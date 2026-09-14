#include "llm/llm.hpp"
#include <chrono>
#include <fstream>
#include <iostream>
#include <memory>
#include <sstream>
#include <string>

// Single request, deterministic config supplied by caller; no tuning or microphone.
int main(int argc, char** argv) {
    if (argc != 3 && argc != 4) { std::cerr << "usage: p0_asr config.json audio.wav [Chinese|English|auto]\n"; return 2; }
    using namespace MNN::Transformer;
    using Clock = std::chrono::steady_clock;
    auto begin = Clock::now();
    std::unique_ptr<Llm> llm(Llm::createLLM(argv[1]));
    if (!llm) { std::cerr << "P0_ERROR createLLM\n"; return 3; }
    if (!llm->set_config("{\"async\":false,\"sampler_type\":\"greedy\",\"max_new_tokens\":128}")) {
        std::cerr << "P0_ERROR config\n"; return 3;
    }
    // Apply per-request language AFTER createLLM merges model metadata.
    if (argc == 4) {
        const std::string language = argv[3];
        if (language != "Chinese" && language != "English" && language != "auto") return 2;
        const std::string value = language == "auto" ? "" : language;
        if (!llm->set_config("{\"asr_language\":\"" + value + "\"}")) return 3;
    }
    std::cout << "P0_EFFECTIVE_CONFIG " << llm->dump_config() << std::endl;
    if (!llm->load()) { std::cerr << "P0_ERROR load\n"; return 4; }
    auto loaded = Clock::now();
    std::ostringstream text;
    std::string prompt = std::string("<audio>") + argv[2] + "</audio>";
    llm->response(prompt, &text);
    auto finished = Clock::now();
    const auto* context = llm->getContext();
    if (context->status != LlmStatus::NORMAL_FINISHED &&
        context->status != LlmStatus::MAX_TOKENS_FINISHED) {
        std::cerr << "P0_ERROR inference status=" << static_cast<int>(context->status) << "\n";
        return 5;
    }
    std::cout << "\nP0_TEXT_BEGIN\n" << text.str() << "\nP0_TEXT_END\n";
    std::cout << "P0_METRICS {\"load_s\":"
        << std::chrono::duration<double>(loaded-begin).count()
        << ",\"inference_s\":" << std::chrono::duration<double>(finished-loaded).count()
        << ",\"audio_us\":" << context->audio_us
        << ",\"prefill_us\":" << context->prefill_us
        << ",\"decode_us\":" << context->decode_us
        << ",\"prompt_tokens\":" << context->prompt_len
        << ",\"generated_tokens\":" << context->gen_seq_len
        << ",\"status\":" << static_cast<int>(context->status)
        << ",\"truncated\":" << (context->status == LlmStatus::MAX_TOKENS_FINISHED ? "true" : "false") << "}\n";
    return context->status == LlmStatus::NORMAL_FINISHED ? 0 : 6;
}
