#include <jni.h>
#include "llm/llm.hpp"
#include <chrono>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>

namespace {
// Explicit ABI shared with InferencePhase.code, never Java enum ordinals.
constexpr int PHASE_MODEL_LOAD_STARTED = 1;
constexpr int PHASE_MODEL_LOAD_COMPLETED = 2;
constexpr int PHASE_INFERENCE_STARTED = 3;
constexpr int PHASE_INFERENCE_COMPLETED = 4;

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
    try {
        if (env->ExceptionCheck()) return nullptr;
        std::lock_guard<std::mutex> lock(engine_mutex);
        using namespace MNN::Transformer;
        using Clock=std::chrono::steady_clock;
        const std::string c=utf(env,config), a=utf(env,wav), lang=utf(env,language), tmp=utf(env,cache);
        if (lang!="Chinese" && lang!="English" && lang!="auto") throw std::runtime_error("Invalid language");
        // All paths are constructed from app-private directories by Java, not Intent extras.
        if (tmp.find_first_of("\"\\\n")!=std::string::npos) throw std::runtime_error("Invalid cache path");
        const auto begin=Clock::now();
        std::ostringstream raw; // Must outlive engine retaining stream pointer.
        PhaseNotifier notifier(env, listener);
        notifier.phase(PHASE_MODEL_LOAD_STARTED);
        std::unique_ptr<Llm> llm(Llm::createLLM(c));
        if (!llm) {
            throw std::runtime_error("createLLM failed");
        }
        const std::string settings="{\"async\":false,\"sampler_type\":\"greedy\",\"max_new_tokens\":128,"
            "\"backend_type\":\"cpu\",\"thread_num\":2,\"tmp_path\":\""+tmp+"\",\"asr_language\":\""+
            (lang=="auto"?"":lang)+"\"}";
        if (!llm->set_config(settings)) {
            throw std::runtime_error("Model configuration failed");
        }
        if (!llm->load()) {
            throw std::runtime_error("Model load failed");
        }
        notifier.phase(PHASE_MODEL_LOAD_COMPLETED);
        const auto loaded=Clock::now();
        notifier.phase(PHASE_INFERENCE_STARTED);
        llm->response("<audio>"+a+"</audio>",&raw);
        const auto done=Clock::now();
        const auto* ctx=llm->getContext();
        if (!ctx || ctx->status!=LlmStatus::NORMAL_FINISHED) {
            if (ctx && ctx->status==LlmStatus::MAX_TOKENS_FINISHED)
                throw std::runtime_error("Output truncated at 128 tokens; use a shorter WAV");
            throw std::runtime_error("Inference did not finish normally");
        }
        notifier.phase(PHASE_INFERENCE_COMPLETED);
        std::ostringstream result;
        result << std::chrono::duration<double>(loaded-begin).count() << " "
               << std::chrono::duration<double>(done-loaded).count() << " " << ctx->gen_seq_len << "\n" << raw.str();
        const auto data=result.str();
        jbyteArray out=env->NewByteArray(static_cast<jsize>(data.size()));
        if (!out || env->ExceptionCheck()) return nullptr;
        env->SetByteArrayRegion(out,0,static_cast<jsize>(data.size()),reinterpret_cast<const jbyte*>(data.data()));
        if (env->ExceptionCheck()) { env->DeleteLocalRef(out); return nullptr; }
        return out; // llm is released for every request; not persistent warm inference.
    } catch (const std::exception& e) {
        throwFailure(env, e.what());
    } catch (...) {
        throwFailure(env, "Unknown native failure");
    }
    return nullptr;
}
