#include <jni.h>
#include "llm/llm.hpp"
#include <chrono>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>

// Test-only injection keeps the actual bridge/JNI/Llm declarations intact.
// Product builds do not define these macros and use the sole real engine.
#ifndef LLM_ASR_ENGINE_FACTORY
#define LLM_ASR_ENGINE_FACTORY MNN::Transformer::Llm::createLLM
#endif
#ifndef LLM_ASR_RESPONSE
#define LLM_ASR_RESPONSE(engine, prompt, stream) (engine)->response((prompt), (stream))
#endif

namespace {
// Explicit ABI shared with InferencePhase.code, never Java enum ordinals.
constexpr int PHASE_MODEL_LOAD_STARTED = 1;
constexpr int PHASE_MODEL_LOAD_COMPLETED = 2;
constexpr int PHASE_INFERENCE_STARTED = 3;
constexpr int PHASE_INFERENCE_COMPLETED = 4;

std::mutex engine_mutex;
thread_local bool native_entry_active = false;
struct NativeEntry {
    bool entered;
    NativeEntry() : entered(!native_entry_active) { if (entered) native_entry_active = true; }
    ~NativeEntry() { if (entered) native_entry_active = false; }
};
std::unique_ptr<MNN::Transformer::Llm> resident;
std::string resident_config, resident_cache;
void evict() {
    resident.reset(); resident_config.clear(); resident_cache.clear();
}
struct EvictUnlessCommitted {
    bool committed = false;
    ~EvictUnlessCommitted() { if (!committed) evict(); }
};
std::string utf(JNIEnv* env, jstring input) {
    if (!input) throw std::runtime_error("Missing native input");
    const char* chars=env->GetStringUTFChars(input,nullptr);
    if (!chars) throw std::runtime_error("Cannot read native input");
    struct Guard { JNIEnv* env; jstring input; const char* chars;
        ~Guard() { env->ReleaseStringUTFChars(input,chars); }
    } guard{env,input,chars};
    return std::string(chars);
}

/** Optional per-request instrumentation. Entry checks distinguish an original
 * pending Java exception from ANY throwable introduced by lookup/invocation.
 * Never perform JNI logging calls or clear an original pending exception.
 */
struct PhaseNotifier {
    JNIEnv* env;
    jobject listener; // borrowed local reference valid for this synchronous call
    jmethodID onPhase = nullptr;
    PhaseNotifier(JNIEnv* e, jobject l) : env(e), listener(l) {
        if (env->ExceptionCheck() || listener == nullptr) return;
        jclass cls = env->GetObjectClass(listener);
        if (env->ExceptionCheck()) {
            env->ExceptionClear(); // only this logging lookup introduced it
            if (cls) env->DeleteLocalRef(cls);
            return;
        }
        if (!cls) return;
        onPhase = env->GetMethodID(cls, "onPhase", "(I)V");
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            onPhase = nullptr;
        }
        env->DeleteLocalRef(cls);
    }
    void phase(int code) {
        if (env->ExceptionCheck() || !onPhase) return;
        env->CallVoidMethod(listener, onPhase, static_cast<jint>(code));
        if (env->ExceptionCheck()) {
            env->ExceptionClear(); // any new Java Throwable, not only RuntimeException
            onPhase = nullptr; // no later callbacks may fabricate a complete sequence
        }
    }
};

void throwFailure(JNIEnv* env, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls) {
        env->ThrowNew(cls, message);
        env->DeleteLocalRef(cls);
    }
}
}
extern "C" JNIEXPORT jdouble JNICALL
Java_org_llmasr_minimal_asr_JniNativeTranscription_prepareNative(JNIEnv* env, jclass,
        jstring config, jstring cache, jobject listener) {
    NativeEntry entry;
    if (!entry.entered) { throwFailure(env,"Reentrant native call rejected"); return -1; }
    try {
        std::lock_guard<std::mutex> lock(engine_mutex);
        EvictUnlessCommitted guard;
        if (env->ExceptionCheck()) return -1;
        const std::string c=utf(env,config), tmp=utf(env,cache);
        if (tmp.find_first_of("\"\\\n")!=std::string::npos) throw std::runtime_error("Invalid cache path");
        if (resident && resident_config==c && resident_cache==tmp) { guard.committed=true; return 0; }
        evict();
        PhaseNotifier notifier(env, listener);
        const auto begin=std::chrono::steady_clock::now();
        notifier.phase(PHASE_MODEL_LOAD_STARTED);
        std::unique_ptr<MNN::Transformer::Llm> fresh(LLM_ASR_ENGINE_FACTORY(c));
        if (!fresh) throw std::runtime_error("createLLM failed");
        const std::string settings="{\"async\":false,\"sampler_type\":\"greedy\",\"max_new_tokens\":128,"
            "\"backend_type\":\"cpu\",\"thread_num\":2,\"tmp_path\":\""+tmp+"\",\"asr_language\":\"\"}";
        if (!fresh->set_config(settings) || !fresh->load()) throw std::runtime_error("Model preparation failed");
        // A successful fresh load is its own empty-state establishment. Never
        // call NORMAL_FINISHED-only cleanup on the fresh RUNNING context.
        const auto* context=fresh->getContext();
        if (!context || context->status!=MNN::Transformer::LlmStatus::RUNNING || context->os ||
            !context->history_tokens.empty() || !context->output_tokens.empty() || !context->generate_str.empty())
            throw std::runtime_error("Fresh model state invalid");
        resident=std::move(fresh); resident_config=c; resident_cache=tmp;
        notifier.phase(PHASE_MODEL_LOAD_COMPLETED);
        if (env->ExceptionCheck()) return -1;
        guard.committed=true;
        return std::chrono::duration<double>(std::chrono::steady_clock::now()-begin).count();
    } catch (const std::exception& e) { throwFailure(env,e.what()); }
      catch (...) { throwFailure(env,"Unknown preparation failure"); }
    return -1;
}
extern "C" JNIEXPORT void JNICALL
Java_org_llmasr_minimal_asr_JniNativeTranscription_releaseNative(JNIEnv* env, jclass) {
    NativeEntry entry;
    if (!entry.entered) { throwFailure(env,"Reentrant native call rejected"); return; }
    try { std::lock_guard<std::mutex> lock(engine_mutex); evict(); }
    catch (...) { throwFailure(env,"Native release failed"); }
}
// Declaration required by the legacy wrapper below; both native symbols remain.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribeWithListener(JNIEnv*, jclass,
        jstring, jstring, jstring, jstring, jobject);
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribe(JNIEnv* env, jclass, jstring config,
                                               jstring wav, jstring language, jstring cache) {
    return Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribeWithListener(env, nullptr, config, wav, language, cache, nullptr);
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_llmasr_minimal_asr_JniNativeTranscription_transcribeWithListener(JNIEnv* env, jclass,
        jstring config, jstring wav, jstring language, jstring cache, jobject listener) {
    NativeEntry entry;
    if (!entry.entered) { throwFailure(env,"Reentrant native call rejected"); return nullptr; }
    try {
        std::lock_guard<std::mutex> lock(engine_mutex);
        std::ostringstream raw; // Outlives default-eviction guard and retained stream.
        EvictUnlessCommitted guard;
        if (env->ExceptionCheck()) return nullptr;
        using namespace MNN::Transformer;
        using Clock=std::chrono::steady_clock;
        const std::string c=utf(env,config), a=utf(env,wav), lang=utf(env,language), tmp=utf(env,cache);
        if (lang!="Chinese" && lang!="English" && lang!="auto") throw std::runtime_error("Invalid language");
        if (!resident || resident_config!=c || resident_cache!=tmp) throw std::runtime_error("Model not explicitly prepared");
        const std::string languageSetting="{\"asr_language\":\""+(lang=="auto"?std::string(""):lang)+"\"}";
        if (!resident->set_config(languageSetting)) throw std::runtime_error("Language configuration failed");
        PhaseNotifier notifier(env,listener);
        notifier.phase(PHASE_INFERENCE_STARTED);
        const auto begin=Clock::now();
        LLM_ASR_RESPONSE(resident.get(),"<audio>"+a+"</audio>",&raw);
        const auto done=Clock::now();
        const auto* ctx=resident->getContext();
        if (!ctx || ctx->status!=LlmStatus::NORMAL_FINISHED) {
            if (ctx && ctx->status==LlmStatus::MAX_TOKENS_FINISHED)
                throw std::runtime_error("Output truncated at 128 tokens; use a shorter WAV");
            throw std::runtime_error("Inference did not finish normally");
        }
        const auto generated=ctx->gen_seq_len;
        const auto text=raw.str();
        if (!resident->clearRequestState()) throw std::runtime_error("Native request cleanup failed");
        notifier.phase(PHASE_INFERENCE_COMPLETED);
        std::ostringstream result;
        result << 0.0 << " " << std::chrono::duration<double>(done-begin).count() << " " << generated << "\n" << text;
        const auto data=result.str();
        jbyteArray out=env->NewByteArray(static_cast<jsize>(data.size()));
        if (!out || env->ExceptionCheck()) return nullptr;
        env->SetByteArrayRegion(out,0,static_cast<jsize>(data.size()),reinterpret_cast<const jbyte*>(data.data()));
        if (env->ExceptionCheck()) { env->DeleteLocalRef(out); return nullptr; }
        guard.committed=true;
        return out;
    } catch (const std::exception& e) { throwFailure(env,e.what()); }
      catch (...) { throwFailure(env,"Unknown native failure"); }
    return nullptr;
}
