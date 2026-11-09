#ifndef LLM_ASR_MODEL_GRAPH_POLICY_H
#define LLM_ASR_MODEL_GRAPH_POLICY_H

#include "MNN_generated.h"
#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <fcntl.h>
#include <stdexcept>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>

/** Closed resource profile for the pinned Qwen3-ASR deployment, not a native sandbox.
 * No graph-selected file is opened. All external ranges refer to the paired weight.
 * Schema verification precedes every accessor, including recursively nested ops. */
namespace llm_asr_graph {
constexpr size_t GRAPH_LIMIT = 64U * 1024U * 1024U;
constexpr uint64_t WEIGHT_LIMIT = 1536ULL * 1024ULL * 1024ULL;
inline void require(bool good, const char* reason) {
    if (!good) throw std::runtime_error(reason);
}

class Profile {
    uint64_t weightBytes;
    size_t ops = 0;
    void floatRange(const flatbuffers::Vector<int64_t>* values, size_t minimum, size_t maximum) {
        range(values, minimum, maximum);
        if (values) for (size_t i = 1; i < values->size(); ++i)
            require(values->Get(static_cast<flatbuffers::uoffset_t>(i)) % 4 == 0, "Unaligned graph external float range");
    }
    void range(const flatbuffers::Vector<int64_t>* values, size_t minimum, size_t maximum) {
        if (!values) return;
        require(values->size() >= minimum && values->size() <= maximum, "Unsupported graph external range");
        uint64_t end = 0;
        for (auto value : *values) {
            require(value >= 0 && static_cast<uint64_t>(value) <= weightBytes - end,
                    "Graph external range exceeds paired weight");
            end += static_cast<uint64_t>(value);
        }
    }
    void norm(const MNN::LayerNorm* value) {
        require(value != nullptr, "Missing graph norm");
        floatRange(value->external(), 3, 3);
        if (value->external() && value->external()->size())
            require(value->external()->Get(1) == value->external()->Get(2), "Graph norm gamma/beta size mismatch");
    }
    void conv(const MNN::Convolution2D* value) {
        require(value != nullptr, "Missing graph convolution");
        // Sparse attributes can carry recursive Attribute.tensor/func values.
        require(!value->sparseParameter(), "Unsupported sparse graph convolution");
        const auto* e = value->external();
        range(e, 3, value->quanParameter() ? 5 : 3);
        if (e && e->size()) {
            // Native decoding consumes alpha/bias/index arrays with these alignments.
            if (value->quanParameter()) {
                const auto* q = value->quanParameter();
                require(q->scaleStorage() == MNN::ScaleStorageType_FP32 || q->scaleStorage() == MNN::ScaleStorageType_FP16, "Unknown graph alpha storage");
                // _RebuildExternalOp PTQ path always resizes/reads float alpha.
                require(q->scaleIn() == 0 || q->scaleStorage() == MNN::ScaleStorageType_FP32, "Unsupported PTQ FP16 external alpha");
                const uint64_t scaleBytes = q->scaleStorage() == MNN::ScaleStorageType_FP16 ? 2 : 4;
                require(e->Get(2) % scaleBytes == 0, "Unaligned graph external alpha");
                if (e->size() > 3) require(e->Get(3) % 4 == 0, "Unaligned graph external bias");
                if (e->size() > 4) require(e->Get(4) % 4 == 0, "Unaligned graph external index");
            } else require(e->Get(1) % 4 == 0 && e->Get(2) % 4 == 0, "Unaligned graph external float");
        }
    }
    void blob(const MNN::Blob* value) {
        require(value != nullptr, "Missing graph blob");
        const auto* e = value->external();
        range(e, 2, 2);
        if (!e) return;
        uint64_t width = 0;
        switch (value->dataType()) {
            case MNN::DataType_DT_FLOAT: case MNN::DataType_DT_INT32: width = 4; break;
            // Pinned Tensor::setType maps DT_INT64 to int32 storage. An external
            // eight-byte payload would exceed that allocation; keep it unsupported.
            case MNN::DataType_DT_INT64:
                throw std::runtime_error("Unsupported external Blob INT64 storage");
            case MNN::DataType_DT_UINT8: case MNN::DataType_DT_INT8: width = 1; break;
            default: throw std::runtime_error("Unsupported external Blob dtype");
        }
        uint64_t bytes = width;
        if (value->dims()) {
            require(value->dims()->size() <= 8, "External Blob rank exceeds limit");
            for (int dim : *value->dims()) {
                require(dim > 0 && bytes <= WEIGHT_LIMIT / static_cast<uint64_t>(dim), "External Blob dimensions overflow");
                bytes *= static_cast<uint64_t>(dim);
            }
        }
        require(static_cast<uint64_t>(e->Get(1)) == bytes, "External Blob bytes exceed destination");
        require(static_cast<uint64_t>(e->Get(0)) % width == 0, "Unaligned external Blob offset");
    }
    void tensor(const MNN::TensorDescribe* value) {
        require(value != nullptr, "Missing graph tensor description");
        if (value->blob()) blob(value->blob());
    }
    template<class T> void tensors(const T* values) {
        if (!values) return;
        require(values->size() <= 100000, "Graph tensor inventory exceeds limit");
        for (const auto* value : *values) tensor(value);
    }
    template<class T> void commands(const T* values, size_t depth) {
        if (!values) return;
        require(values->size() <= 100000, "Graph command inventory exceeds limit");
        for (const auto* value : *values) {
            require(value != nullptr, "Missing graph command");
            if (value->op()) op(value->op(), depth + 1);
        }
    }
    void op(const MNN::Op* value, size_t depth) {
        require(value && depth <= 32 && ++ops <= 100000, "Graph operation inventory exceeds limit");
        // Even an empty externalPath is forbidden: upstream checks pointer presence.
        require(value->externalPath() == nullptr, "Graph-selected external path forbidden");
        const auto type = value->type();
        // Resource-bearing execution paths must agree with their schema union;
        // do not validate one representation then execute it as another.
        auto parameter = value->main_type();
        if (type == MNN::OpType_Convolution || type == MNN::OpType_ConvolutionDepthwise ||
            type == MNN::OpType_Deconvolution || type == MNN::OpType_DeconvolutionDepthwise ||
            type == MNN::OpType_ConvInt8 || type == MNN::OpType_DepthwiseConvInt8)
            require(parameter == MNN::OpParameter_Convolution2D, "Graph convolution union mismatch");
        if (type == MNN::OpType_Const || type == MNN::OpType_TrainableParam)
            require(parameter == MNN::OpParameter_Blob, "Graph blob union mismatch");
        if (type == MNN::OpType_Scale) require(parameter == MNN::OpParameter_Scale, "Graph scale union mismatch");
        if (type == MNN::OpType_LayerNorm) require(parameter == MNN::OpParameter_LayerNorm, "Graph norm union mismatch");
        if (type == MNN::OpType_FusedLinear) require(parameter == MNN::OpParameter_FusedLinearParam, "Graph fused union mismatch");
        if (type == MNN::OpType_RoPE) require(parameter == MNN::OpParameter_RoPEParam, "Graph rope union mismatch");
        if (type == MNN::OpType_Convolution3D) require(parameter == MNN::OpParameter_Convolution3D, "Graph Conv3D union mismatch");
        if (type == MNN::OpType_GroupNorm) require(parameter == MNN::OpParameter_GroupNorm, "Graph GroupNorm union mismatch");
        const auto& types = MNN::EnumValuesOpType();
        require(std::find(std::begin(types), std::end(types), type) != std::end(types), "Unknown graph operation");
        require(type != MNN::OpType_Plugin && type != MNN::OpType_Extra, "Custom graph operation forbidden");
        require(value->main_type() >= MNN::OpParameter_NONE && value->main_type() <= MNN::OpParameter_FusedLinearParam,
                "Unknown graph parameter");
        switch (value->main_type()) {
            case MNN::OpParameter_Plugin:
                throw std::runtime_error("Custom graph parameter forbidden");
            case MNN::OpParameter_Extra: {
                // The pinned built-in Where uses an EMPTY Extra table as a shape
                // version marker (ShapeWhere.cpp), not custom-op dispatch.
                const auto* p = value->main_as_Extra();
                require(type == MNN::OpType_Where && p && !p->engine() && !p->type() &&
                        !p->info() && !p->attr() && !p->vector(), "Custom graph parameter forbidden");
                break;
            }
            case MNN::OpParameter_LSTM: case MNN::OpParameter_Proposal:
            case MNN::OpParameter_SpaceBatch: case MNN::OpParameter_LSTMBlockCell: case MNN::OpParameter_RNNParam:
                // These non-ASR representations embed additional Blob tables.
                throw std::runtime_error("Unsupported nested graph resource representation");
            case MNN::OpParameter_Convolution2D: conv(value->main_as_Convolution2D()); break;
            case MNN::OpParameter_Convolution3D: {
                const auto* p = value->main_as_Convolution3D();
                require(p && (!p->external() || !p->external()->size()), "Unsupported Conv3D external representation"); break;
            }
            case MNN::OpParameter_Scale: {
                const auto* p = value->main_as_Scale(); require(p, "Missing graph scale");
                floatRange(p->external(), 2, 3);
                if (p->external() && p->external()->size() == 3)
                    require(p->external()->Get(2) <= p->external()->Get(1), "Scale bias exceeds destination");
                break;
            }
            case MNN::OpParameter_Blob: {
                blob(value->main_as_Blob()); break;
            }
            case MNN::OpParameter_LayerNorm: norm(value->main_as_LayerNorm()); break;
            case MNN::OpParameter_GroupNorm: {
                const auto* p = value->main_as_GroupNorm();
                require(p && (!p->external() || !p->external()->size()), "Unsupported GroupNorm external representation"); break;
            }
            case MNN::OpParameter_RoPEParam: {
                const auto* p = value->main_as_RoPEParam(); require(p, "Missing graph rope");
                if (p->q_norm()) norm(p->q_norm());
                if (p->k_norm()) norm(p->k_norm());
                break;
            }
            case MNN::OpParameter_FusedLinearParam: {
                const auto* p = value->main_as_FusedLinearParam(); require(p, "Missing graph fused projection");
                if (p->convs()) {
                    require(p->convs()->size() <= 4, "Unsupported graph fused projection count");
                    for (const auto* c : *p->convs()) conv(c);
                }
                if (p->ln()) norm(p->ln());
                break;
            }
            case MNN::OpParameter_LoopParam: {
                const auto* p = value->main_as_LoopParam(); require(p, "Missing graph loop");
                tensors(p->extraTensorInfos()); commands(p->commands(), depth); commands(p->initCommand(), depth); break;
            }
            default: break; // Remaining pinned schema unions have no file/range fields.
        }
    }
public:
    explicit Profile(uint64_t bytes) : weightBytes(bytes) {
        require(bytes > 0 && bytes <= WEIGHT_LIMIT, "Invalid paired graph weight size");
    }
    size_t inspect(const uint8_t* data, size_t bytes) {
        require(bytes > 0 && bytes <= GRAPH_LIMIT, "Graph size exceeds limit");
        flatbuffers::Verifier verifier(data, bytes, 64, 1000000);
        require(MNN::VerifyNetBuffer(verifier), "Invalid graph schema");
        const auto* net = MNN::GetNet(data);
        require(net->usage() == MNN::Usage_INFERENCE || net->usage() == MNN::Usage_INFERENCE_STATIC,
                "Unsupported graph usage");
        require(net->preferForwardType() == MNN::ForwardType_CPU, "Non-CPU graph forbidden");
        tensors(net->extraTensorDescribe());
        if (net->oplists()) for (const auto* value : *net->oplists()) op(value, 0);
        if (net->subgraphs()) {
            require(net->subgraphs()->size() <= 1024, "Graph subgraph inventory exceeds limit");
            for (const auto* sub : *net->subgraphs()) {
                require(sub, "Missing graph subgraph");
                tensors(sub->extraTensorDescribe());
                if (sub->nodes()) for (const auto* value : *sub->nodes()) op(value, 0);
            }
        }
        require(ops > 0, "Empty graph forbidden");
        return ops;
    }
};

class Fd {
    int value;
public:
    explicit Fd(int fd) : value(fd) { require(fd >= 0, "Cannot open private graph resource"); }
    ~Fd() { if (value >= 0) ::close(value); }
    Fd(const Fd&) = delete;
    Fd& operator=(const Fd&) = delete;
    int get() const { return value; }
    void close() { int fd = value; value = -1; require(::close(fd) == 0, "Cannot close private graph resource"); }
};
inline struct stat regular(int fd) {
    struct stat info {};
    require(::fstat(fd, &info) == 0 && S_ISREG(info.st_mode), "Graph resource must be regular");
    return info;
}
inline size_t inspectAt(int directory, const char* graph, const char* weight) {
    struct stat weightInfo {};
    require(::fstatat(directory, weight, &weightInfo, AT_SYMLINK_NOFOLLOW) == 0 && S_ISREG(weightInfo.st_mode) &&
            weightInfo.st_size > 0, "Paired graph weight must be regular");
    Fd input(::openat(directory, graph, O_RDONLY | O_NOFOLLOW | O_NONBLOCK | O_CLOEXEC));
    const auto before = regular(input.get());
    require(before.st_size > 0 && static_cast<uint64_t>(before.st_size) <= GRAPH_LIMIT, "Graph size exceeds limit");
    std::vector<uint8_t> bytes(static_cast<size_t>(before.st_size));
    size_t offset = 0;
    while (offset < bytes.size()) {
        auto n = ::read(input.get(), bytes.data() + offset, bytes.size() - offset);
        if (n < 0 && errno == EINTR) continue;
        require(n > 0, "Graph read did not complete"); offset += static_cast<size_t>(n);
    }
    const auto after = regular(input.get());
    require(before.st_dev == after.st_dev && before.st_ino == after.st_ino && before.st_size == after.st_size &&
            before.st_mtim.tv_sec == after.st_mtim.tv_sec && before.st_mtim.tv_nsec == after.st_mtim.tv_nsec &&
            before.st_ctim.tv_sec == after.st_ctim.tv_sec && before.st_ctim.tv_nsec == after.st_ctim.tv_nsec,
            "Graph identity changed while reading");
    input.close(); // Successful admission includes the actual close result.
    return Profile(static_cast<uint64_t>(weightInfo.st_size)).inspect(bytes.data(), bytes.size());
}
inline void inspectDeployment(const std::string& config) {
    const auto slash = config.find_last_of('/');
    require(slash != std::string::npos && config.substr(slash + 1) == "config.json", "Invalid deployment config leaf");
    Fd directory(::open(config.substr(0, slash).c_str(), O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC));
    inspectAt(directory.get(), "audio.mnn", "audio.mnn.weight");
    inspectAt(directory.get(), "llm.mnn", "llm.mnn.weight");
    directory.close();
}
} // namespace llm_asr_graph
#endif
