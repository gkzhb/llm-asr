package org.llmasr.minimal.ime;

/** Conservative Android InputType/EditorInfo policy; never reads field text. */
public final class ImeFieldPolicy {
    private ImeFieldPolicy() {}
    public static boolean isSensitive(int inputType, int imeOptions) {
        int type = inputType & 15, variation = inputType & 0xff0;
        if (type != 1 && type != 2 && type != 3 && type != 4) return true;
        if ((imeOptions & 0x01000000) != 0) return true;
        return (type == 1 && (variation == 0x80 || variation == 0x90 || variation == 0xe0))
            || (type == 2 && variation == 0x10);
    }
}
