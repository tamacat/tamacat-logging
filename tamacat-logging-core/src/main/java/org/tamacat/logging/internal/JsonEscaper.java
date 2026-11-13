package org.tamacat.logging.internal;

/**
 * Write-only JSON string escaping (RFC 8259), stricter than required: also escapes DEL, C1,
 * U+2028/U+2029 and bidirectional controls, and replaces lone surrogates with U+FFFD.
 */
public final class JsonEscaper {

    private JsonEscaper() {
    }

    /** Appends the value as a quoted JSON string. */
    public static void quote(CharSequence s, StringBuilder out, boolean asciiOnly) {
        out.append('"');
        escape(s, out, asciiOnly);
        out.append('"');
    }

    public static void escape(CharSequence s, StringBuilder out, boolean asciiOnly) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < len && Character.isLowSurrogate(s.charAt(i + 1))) {
                    char low = s.charAt(++i);
                    if (asciiOnly) {
                        Chars.appendUnicodeEscape(c, out);
                        Chars.appendUnicodeEscape(low, out);
                    } else {
                        out.append(c).append(low);
                    }
                } else {
                    out.append("\\uFFFD");
                }
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                out.append("\\uFFFD");
                continue;
            }
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (Chars.isUnsafe(c) || (asciiOnly && c > 0x7E)) {
                        Chars.appendUnicodeEscape(c, out);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }
}
