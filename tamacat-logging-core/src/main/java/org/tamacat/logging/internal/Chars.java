package org.tamacat.logging.internal;

/**
 * Character classification shared by all escapers.
 */
public final class Chars {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private Chars() {
    }

    /**
     * Characters that must never appear raw in log output: C0 controls, DEL, C1 controls,
     * line/paragraph separators, bidirectional controls (Trojan Source) and BOM.
     */
    public static boolean isUnsafe(char c) {
        return c < 0x20
                || (c >= 0x7F && c <= 0x9F)
                || c == 0x061C
                || c == 0x200E || c == 0x200F
                || c == 0x2028 || c == 0x2029
                || (c >= 0x202A && c <= 0x202E)
                || (c >= 0x2066 && c <= 0x2069)
                || c == 0xFEFF;
    }

    /** Appends {@code \\uXXXX}. */
    public static void appendUnicodeEscape(char c, StringBuilder out) {
        out.append('\\').append('u')
                .append(HEX[(c >> 12) & 0xF])
                .append(HEX[(c >> 8) & 0xF])
                .append(HEX[(c >> 4) & 0xF])
                .append(HEX[c & 0xF]);
    }

    static void appendHexByte(int b, StringBuilder out) {
        out.append('\\').append('x')
                .append(Character.toLowerCase(HEX[(b >> 4) & 0xF]))
                .append(Character.toLowerCase(HEX[b & 0xF]));
    }

    /** Returns true if the text contains unsafe characters or lone surrogates. */
    public static boolean containsUnsafe(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isUnsafe(c)) {
                return true;
            }
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) {
                    return true;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }
}
