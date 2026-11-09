#include "model_graph_policy.h"
#include <iostream>
#include <limits>
#include <cstring>

namespace {
void check(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
}
std::vector<uint8_t> graph(const std::string& kind) {
    MNN::NetT net;
    net.usage = MNN::Usage_INFERENCE;
    std::unique_ptr<MNN::OpT> op(new MNN::OpT);
    op->type = MNN::OpType_Const;
    MNN::BlobT blob;
    blob.dataType = MNN::DataType_DT_FLOAT;
    blob.dims = {1}; blob.float32s = {1.0f};
    if (kind == "range" || kind == "overflow") blob.external = {8, kind == "range" ? 4 : std::numeric_limits<int64_t>::max()};
    if (kind == "blob-capacity") blob.external = {0, 65536};
    if (kind == "int64-storage") { blob.dataType = MNN::DataType_DT_INT64; blob.external = {0, 8}; }
    op->main.Set(std::move(blob));
    if (kind == "where" || kind == "where-custom") {
        op->type = MNN::OpType_Where;
        MNN::ExtraT marker;
        if (kind == "where-custom") marker.engine = "untrusted";
        op->main.Set(std::move(marker));
    }
    if (kind == "path" || kind == "subpath") op->externalPath = "/must-not-open/outside.weight";
    if (kind == "custom") op->type = MNN::OpType_Extra;
    if (kind == "union") op->type = MNN::OpType_Convolution;
    if (kind == "int8-union") op->type = MNN::OpType_ConvInt8;
    if (kind == "depthwise-union") op->type = MNN::OpType_DepthwiseConvInt8;
    if (kind == "scale-capacity" || kind == "scale-good") {
        MNN::ScaleT scale; scale.channels = 1; scale.external = {0, 4, kind == "scale-capacity" ? 8 : 4};
        op->type = MNN::OpType_Scale; op->main.Set(std::move(scale));
    }
    if (kind == "ptq-alpha" || kind == "fused-ptq") {
        std::unique_ptr<MNN::Convolution2DT> c(new MNN::Convolution2DT);
        c->common.reset(new MNN::Convolution2DCommonT); c->common->outputCount = 1;
        c->quanParameter.reset(new MNN::IDSTQuanT); c->quanParameter->scaleIn = 1.0f;
        c->quanParameter->scaleStorage = MNN::ScaleStorageType_FP16; c->external = {0, 4, 2};
        if (kind == "fused-ptq") { MNN::FusedLinearParamT fused; fused.convs.push_back(std::move(c)); op->type = MNN::OpType_FusedLinear; op->main.Set(std::move(fused)); }
        else { op->type = MNN::OpType_Convolution; op->main.Set(std::move(*c)); }
    }
    if (kind == "rnn") { MNN::RNNParamT value; op->type = MNN::OpType_Identity; op->main.Set(std::move(value)); }
    if (kind == "tensor-blob" || kind == "tensor-int64-storage") {
        std::unique_ptr<MNN::TensorDescribeT> tensor(new MNN::TensorDescribeT);
        tensor->blob.reset(new MNN::BlobT); tensor->blob->dataType = MNN::DataType_DT_FLOAT;
        tensor->blob->dims = {1}; tensor->blob->external = {0, 65536};
        if (kind == "tensor-int64-storage") { tensor->blob->dataType = MNN::DataType_DT_INT64; tensor->blob->external = {0, 8}; }
        net.extraTensorDescribe.push_back(std::move(tensor));
    }
    if (kind == "empty-norm" || kind == "empty-rope-norm") {
        // Direct builder: object Pack deliberately omits empty vectors.
        flatbuffers::FlatBufferBuilder builder;
        auto ext = builder.CreateVector(std::vector<int64_t>{});
        auto axis = builder.CreateVector(std::vector<int32_t>{1});
        auto norm = MNN::CreateLayerNorm(builder, axis, 0.00001f, 0, 0, 1, ext, false);
        flatbuffers::Offset<void> param = norm.Union(); auto paramType = MNN::OpParameter_LayerNorm; auto opType = MNN::OpType_LayerNorm;
        if (kind == "empty-rope-norm") { param = MNN::CreateRoPEParam(builder, 0, 0, 0, 0, norm, 0).Union(); paramType = MNN::OpParameter_RoPEParam; opType = MNN::OpType_RoPE; }
        auto node = MNN::CreateOp(builder, 0, paramType, param, 0, 0, opType);
        auto nodes = builder.CreateVector(std::vector<flatbuffers::Offset<MNN::Op>>{node});
        auto direct = MNN::CreateNet(builder, 0, 0, 0, nodes); builder.Finish(direct);
        return std::vector<uint8_t>(builder.GetBufferPointer(), builder.GetBufferPointer() + builder.GetSize());
    }
    if (kind == "relative-path") op->externalPath = "../outside.weight";
    if (kind == "gpu") net.preferForwardType = MNN::ForwardType_OPENCL;
    if (kind == "subpath") {
        std::unique_ptr<MNN::SubGraphProtoT> sub(new MNN::SubGraphProtoT);
        sub->nodes.push_back(std::move(op)); net.subgraphs.push_back(std::move(sub));
    } else net.oplists.push_back(std::move(op));
    flatbuffers::FlatBufferBuilder builder;
    builder.Finish(MNN::Net::Pack(builder, &net));
    std::vector<uint8_t> result(builder.GetBufferPointer(), builder.GetBufferPointer() + builder.GetSize());
    if (kind == "truncated") result.resize(result.size() / 2);
    return result;
}
}
int main(int argc, char** argv) {
    try {
        if (argc == 4 && std::string(argv[1]) == "--audit") {
            llm_asr_graph::Fd directory(::open(argv[2], O_RDONLY | O_DIRECTORY | O_NOFOLLOW));
            const std::string leaf(argv[3]);
            check(leaf == "audio.mnn" || leaf == "llm.mnn", "fixed graph leaf only");
            llm_asr_graph::Fd input(::openat(directory.get(), leaf.c_str(), O_RDONLY | O_NOFOLLOW | O_NONBLOCK));
            auto info = llm_asr_graph::regular(input.get());
            check(info.st_size > 0 && static_cast<uint64_t>(info.st_size) <= llm_asr_graph::GRAPH_LIMIT, "audit size");
            std::vector<uint8_t> bytes(static_cast<size_t>(info.st_size)); size_t offset = 0;
            while (offset < bytes.size()) {
                auto n = ::read(input.get(), bytes.data() + offset, bytes.size() - offset);
                check(n > 0, "audit read"); offset += static_cast<size_t>(n);
            }
            input.close(); directory.close();
            flatbuffers::Verifier verifier(bytes.data(), bytes.size(), 64, 1000000);
            check(MNN::VerifyNetBuffer(verifier), "audit schema");
            auto inspect = [](const MNN::Op* op) {
                if (op->main_type() != MNN::OpParameter_Extra && op->type() != MNN::OpType_Extra && op->type() != MNN::OpType_Plugin) return;
                auto* extra = op->main_as_Extra();
                std::cout << "AUDIT type=" << MNN::EnumNameOpType(op->type()) << " union=" << MNN::EnumNameOpParameter(op->main_type());
                if (extra) {
                    std::cout << " engine=" << (extra->engine() ? extra->engine()->str() : "<null>")
                              << " kind=" << (extra->type() ? extra->type()->str() : "<null>")
                              << " attrs=" << (extra->attr() ? extra->attr()->size() : 0)
                              << " infoBytes=" << (extra->info() ? extra->info()->size() : 0);
                }
                std::cout << '\n';
            };
            const auto* net = MNN::GetNet(bytes.data());
            if (net->oplists()) for (const auto* op : *net->oplists()) inspect(op);
            if (net->subgraphs()) for (const auto* sub : *net->subgraphs())
                if (sub->nodes()) for (const auto* op : *sub->nodes()) inspect(op);
            return 0;
        }
        if (argc == 4 && std::string(argv[1]) == "--admit") {
            llm_asr_graph::Fd directory(::open(argv[2], O_RDONLY | O_DIRECTORY | O_NOFOLLOW));
            const std::string name(argv[3]);
            check(name == "audio.mnn" || name == "llm.mnn", "fixed graph leaf only");
            auto count = llm_asr_graph::inspectAt(directory.get(), name.c_str(), (name + ".weight").c_str());
            directory.close();
            std::cout << "ADMITTED " << name << " ops=" << count << " weights=STAT_ONLY\n"; return 0;
        }
        const std::vector<std::string> cases = {"normal", "range", "scale-good", "where", "where-custom", "path", "relative-path", "subpath", "custom", "union", "gpu", "overflow", "truncated", "scale-capacity", "blob-capacity", "ptq-alpha", "fused-ptq", "int8-union", "depthwise-union", "empty-norm", "empty-rope-norm", "rnn", "tensor-blob", "int64-storage", "tensor-int64-storage"};
        for (const auto& item : cases) {
            const char* name = item.c_str();
            if (argc == 3 && std::string(argv[1]) == "--case" && item != argv[2]) continue;
            auto bytes = graph(name); bool accepted = false; std::string diagnostic;
            try { llm_asr_graph::Profile(65536).inspect(bytes.data(), bytes.size()); accepted = true; }
            catch (const std::runtime_error& error) { diagnostic = error.what(); }
            const bool expected = std::strcmp(name, "normal") == 0 || std::strcmp(name, "range") == 0 || std::strcmp(name, "where") == 0 || std::strcmp(name, "scale-good") == 0;
            check(accepted == expected, name);
            if (item == "int64-storage" || item == "tensor-int64-storage")
                check(diagnostic == "Unsupported external Blob INT64 storage", "INT64 must fail for runtime storage contract");
            std::cout << "CASE " << name << " " << (accepted ? "ACCEPT" : "REFUSE " + diagnostic) << '\n';
        }
        std::cout << "PASS production graph resource guard tiny fixtures; not inference\n";
        return 0;
    } catch (const std::exception& error) { std::cerr << "FAIL " << error.what() << '\n'; return 1; }
}
