#pragma once
#include <MNN/expr/ExprCreator.hpp>
#include <MNN/expr/Module.hpp>
#include "llm/llm.hpp"
#include "llmconfig.hpp"
#include "speculative_decoding/generate.hpp"
#include "core/KVMeta.hpp"
#include <stdexcept>
#include <iostream>
using namespace MNN;using namespace MNN::Express;using namespace MNN::Transformer;
inline void require(bool v,const char* msg){if(!v)throw std::runtime_error(msg);}
inline std::shared_ptr<LlmConfig> config(){
 auto c=std::make_shared<LlmConfig>();c->config_=ujson::json::parse(R"({"backend_type":"cpu","thread_num":2,"async":false,"sampler_type":"greedy","max_new_tokens":128,"is_audio":true,"audio_type":"qwen3_asr","asr_language":"Chinese","reuse_kv":false,"prompt_cache":false,"jinja":{"chat_template":"{{ asr_language }}","context":{"asr_language":"Chinese"}}})");
 return c;
}
