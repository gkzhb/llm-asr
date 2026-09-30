package org.llmasr.minimal.ime

/** Conservative Android InputType/EditorInfo policy; never reads field text. */
class ImeFieldPolicy private constructor() {
    companion object {
        @JvmStatic
        fun isSensitive(inputType: Int, imeOptions: Int): Boolean {
            val type = inputType and 15
            val variation = inputType and 0xff0
            if (type != 1 && type != 2 && type != 3 && type != 4) return true
            if ((imeOptions and 0x01000000) != 0) return true
            return (type == 1 && (variation == 0x80 || variation == 0x90 || variation == 0xe0)) ||
                (type == 2 && variation == 0x10)
        }
    }
}
