package org.llmasr.minimal.audio

/** Protocol display only, NOT the official repetition-normalizing scoring parser. */
class AsrText {
    companion object {
        @JvmStatic
        fun display(raw: String): String {
            val marker = raw.indexOf("<asr_text>")
            val text = if (marker >= 0) raw.substring(marker + 10) else raw
            // java.lang.String.trim(), not Kotlin's broader Unicode whitespace trim.
            return text.replace("<|im_end|>", "").trim { it <= '\u0020' }
        }
    }
}
