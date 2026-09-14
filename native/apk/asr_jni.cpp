#include <jni.h>
#include "llm/llm.hpp"
#include <chrono>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>

namespace {
std::mutex engine_mutex;
std::string utf(JNIEnv* env, jstring input) {
    if (!input) throw std::runtime_error("Missing native input");
    const char* chars=env->GetStringUTFChars(input,nullptr);
    if (!chars) throw std::runtime_error("Cannot read native input");
    struct Guard { JNIEnv* env; jstring input; const char* chars;
        ~Guard() { env->ReleaseStringUTFChars(input,chars); }
    } guard{env,input,chars};
    return std::string(chars);
}
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_llmasr_minimal_MainActivity_transcribe(JNIEnv* env, jclass, jstring config,
                                               jstring wav, jstring language, jstring cache) {
    try {
        std::lock_guard<std::mutex> lock(engine_mutex);
        using namespace MNN::Transformer;
        using Clock=std::chrono::steady_clock;
        const std::string c=utf(env,config), a=utf(env,wav), lang=utf(env,language), tmp=utf(env,cache);
        if (lang!="Chinese" && lang!="English" && lang!="auto") throw std::runtime_error("Invalid language");
        // All paths are constructed from app-private directories by Java, not Intent extras.
        if (tmp.find_first_of("\"\\\n")!=std::string::npos) throw std::runtime_error("Invalid cache path");
        const auto begin=Clock::now();
        std::ostringstream raw; // Must outlive engine retaining stream pointer.
        std::unique_ptr<Llm> llm(Llm::createLLM(c));
        if (!llm) throw std::runtime_error("createLLM failed");
        const std::string settings="{\"async\":false,\"sampler_type\":\"greedy\",\"max_new_tokens\":128,"
            "\"backend_type\":\"cpu\",\"thread_num\":2,\"tmp_path\":\""+tmp+"\",\"asr_language\":\""+
            (lang=="auto"?"":lang)+"\"}";
        if (!llm->set_config(settings) || !llm->load()) throw std::runtime_error("Model configuration/load failed");
        const auto loaded=Clock::now();
        llm->response("<audio>"+a+"</audio>",&raw);
        const auto done=Clock::now();
        const auto* ctx=llm->getContext();
        if (!ctx || ctx->status!=LlmStatus::NORMAL_FINISHED) {
            if (ctx && ctx->status==LlmStatus::MAX_TOKENS_FINISHED)
                throw std::runtime_error("Output truncated at 128 tokens; use a shorter WAV");
            throw std::runtime_error("Inference did not finish normally");
        }
        std::ostringstream result;
        result << std::chrono::duration<double>(loaded-begin).count() << " "
               << std::chrono::duration<double>(done-loaded).count() << " " << ctx->gen_seq_len << "\n" << raw.str();
        const auto data=result.str();
        jbyteArray out=env->NewByteArray(static_cast<jsize>(data.size()));
        if (out) env->SetByteArrayRegion(out,0,static_cast<jsize>(data.size()),reinterpret_cast<const jbyte*>(data.data()));
        return out; // llm is released for every request; not persistent warm inference.
    } catch (const std::exception& e) {
        if (!env->ExceptionCheck()) { jclass cls=env->FindClass("java/lang/RuntimeException"); if(cls)env->ThrowNew(cls,e.what()); }
    } catch (...) {
        if (!env->ExceptionCheck()) { jclass cls=env->FindClass("java/lang/RuntimeException"); if(cls)env->ThrowNew(cls,"Unknown native failure"); }
    }
    return nullptr;
}
