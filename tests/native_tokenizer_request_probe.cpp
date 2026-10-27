// Compile the exact production tokenizer implementation in this test TU so
// its actual BPE model can be observed without adding a test export to llm.so.
#include "tokenizer/tokenizer.cpp"
#include <stdexcept>
#include <iostream>
namespace MNN { namespace Transformer {
struct RequestCleanupProbe {
 static void run(const std::string& file){
  auto require=[](bool v,const char*m){if(!v)throw std::runtime_error(m);};
  std::unique_ptr<Tokenizer> token(Tokenizer::createTokenizer(file));require(token!=nullptr,"TOKENIZER_MISSING");
  auto pipeline=dynamic_cast<PipelineTokenizer*>(token.get());require(pipeline!=nullptr,"NOT_REAL_PIPELINE_TOKENIZER");
  auto model=dynamic_cast<BPEModel*>(pipeline->model_.get());require(model!=nullptr,"NOT_REAL_BPE_MODEL");
  auto binary=pipeline->binary_buf_;auto modelptr=pipeline->model_.get();auto vocab=model->vocab_size();
  auto first=token->encode("ab");auto second=token->encode("ba");require(!first.empty()&&!second.empty(),"VOCAB_NOT_EXERCISED");
  require(model->cache_.size()>=2,"BPE_CACHE_NOT_POPULATED");
  const auto decoded=token->decode(first);require(decoded=="ab","ENCODE_DECODE_WRONG");
  token->clearRequestCache();require(model->cache_.empty(),"BPE_CACHE_RETAINED");
  require(model->vocab_size()==vocab&&pipeline->binary_buf_==binary&&pipeline->model_.get()==modelptr,"VOCAB_RESOURCE_CHANGED");
  require(token->encode("ab")==first&&token->encode("ba")==second&&token->decode(first)==decoded,"BPE_POST_CLEAR_CHANGED");
  token->clearRequestCache();require(model->cache_.empty(),"BPE_SECOND_CLEAR_RETAINED");
 }
};}}
int main(int argc,char**argv){try{if(argc!=2)throw std::runtime_error("ARGS");MNN::Transformer::RequestCleanupProbe::run(argv[1]);std::cout<<"PASS real PipelineTokenizer BPE encode/cache/clear/resources/re-encode"<<std::endl;return 0;}catch(const std::exception&e){std::cerr<<"ASSERTION: "<<e.what()<<std::endl;return 2;}}
