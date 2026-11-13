package org.tamacat.logging.internal;

/**
 * Escaping for the plain-text layout. Guarantees the output contains no line breaks,
 * no control characters (including ESC) and no bidirectional controls.
 */
public final class TextEscaper {

    private TextEscaper() {
    }

    public static void escape(CharSequence s, StringBuilder out) {
        escape(s, out, false);
    }

    /** Same as {@link #escape(CharSequence, StringBuilder)} but also escapes {@code "} (for quoted values). */
    public static void escapeQuoted(CharSequence s, StringBuilder out) {
        escape(s, out, true);
    }

    public static String escape(CharSequence s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        escape(s, sb, false);
        return sb.toString();
    }

    private static void escape(CharSequence s, StringBuilder out, boolean quote) {
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < len && Character.isLowSurrogate(s.charAt(i + 1))) {
                    out.append(c).append(s.charAt(++i));
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
                case '\\' -> out.append("\\\\");
                case '\r' -> out.append("\\r");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
                case '"' -> {
                    if (quote) {
                        out.append("\\\"");
                    } else {
                        out.append(c);
                    }
                }
                default -> {
                    if (Chars.isUnsafe(c)) {
                        Chars.appendUnicodeEscape(c, out);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }
}
