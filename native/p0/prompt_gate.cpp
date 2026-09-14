#include "llm/llm.hpp"
#include <iostream>
#include <memory>
#include <string>
// Diagnostic only: same Omni tokenizer/audio expansion as response(), no decode.
int main(int argc,char**argv){
 if(argc!=4)return 2;
 using namespace MNN::Transformer;
 const std::string lang=argv[3];
 if(lang!="English"&&lang!="Chinese"&&lang!="auto")return 2;
 std::unique_ptr<Llm> llm(Llm::createLLM(argv[1]));
 if(!llm)return 3;
 if(!llm->set_config("{\"asr_language\":\""+(lang=="auto"?std::string(""):lang)+"\",\"async\":false}"))return 4;
 if(!llm->load())return 5;
 auto rendered=llm->apply_chat_template(std::string("<audio>")+argv[2]+"</audio>");
 auto ids=llm->tokenizer_encode(rendered);
 std::cout<<"P0_NATIVE_IDS [";
 for(size_t i=0;i<ids.size();++i){if(i)std::cout<<",";std::cout<<ids[i];}
 std::cout<<"]\n";
 return ids.empty()?6:0;
}
