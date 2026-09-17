package org.llmasr.minimal.audio;

/** Protocol display only, NOT the official repetition-normalizing scoring parser. */
public final class AsrText {
    public static String display(String raw) {
        int marker=raw.indexOf("<asr_text>");
        String text=marker>=0 ? raw.substring(marker+10) : raw;
        return text.replace("<|im_end|>", "").trim();
    }
}
