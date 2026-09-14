#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#include <fstream>
#include <iostream>
#include <memory>
#include <vector>
#include <string>

int main(int argc, char** argv) {
    if (argc != 4) { std::cerr << "usage: audio_gate model.mnn data-dir frames-csv\n"; return 2; }
    std::unique_ptr<MNN::Interpreter> net(MNN::Interpreter::createFromFile(argv[1]));
    if (!net) return 3;
    std::string weight = std::string(argv[1])+".weight";
    net->setExternalFile(weight.c_str());
    MNN::ScheduleConfig cfg; cfg.type=MNN_FORWARD_CPU; cfg.numThread=2;
    MNN::BackendConfig backend; backend.precision=MNN::BackendConfig::Precision_High;
    cfg.backendConfig=&backend;
    auto session=net->createSession(cfg); if (!session) return 4;
    std::string lengths=argv[3]; size_t start=0;
    while (start < lengths.size()) {
        size_t end=lengths.find(',',start);
        int n=std::stoi(lengths.substr(start,end-start));
        if (n<=0 || n>3000) return 5;
        auto input=net->getSessionInput(session,"input_features"); if(!input) return 6;
        net->resizeTensor(input,{128,n}); net->resizeSession(session);
        MNN::Tensor host(input,MNN::Tensor::CAFFE);
        std::ifstream file(std::string(argv[2])+"/input-"+std::to_string(n)+".bin",std::ios::binary);
        file.read(reinterpret_cast<char*>(host.host<float>()),128*n*sizeof(float));
        if(file.gcount()!=128*n*sizeof(float)) return 7;
        input->copyFromHostTensor(&host);
        if(net->runSession(session)!=MNN::NO_ERROR) return 8;
        auto output=net->getSessionOutput(session,"audio_embeds"); if(!output) return 9;
        MNN::Tensor result(output,MNN::Tensor::CAFFE);
        if(!output->copyToHostTensor(&result)) return 10;
        std::ofstream out(std::string(argv[2])+"/actual-"+std::to_string(n)+".bin",std::ios::binary);
        out.write(reinterpret_cast<const char*>(result.host<float>()),result.elementSize()*sizeof(float));
        if(!out) return 11;
        std::cout << "frames=" << n << " shape=";
        for(int v:result.shape()) std::cout<<v<<",";
        std::cout<<" elements="<<result.elementSize()<<std::endl;
        if(end==std::string::npos) break; start=end+1;
    }
    return 0;
}
