package org.tamacat.logging.internal;

import java.util.List;
import java.util.Map;

import org.tamacat.logging.internal.PatternParser.Date;
import org.tamacat.logging.internal.PatternParser.Ex;
import org.tamacat.logging.internal.PatternParser.Labels;
import org.tamacat.logging.internal.PatternParser.LevelTok;
import org.tamacat.logging.internal.PatternParser.Literal;
import org.tamacat.logging.internal.PatternParser.LoggerTok;
import org.tamacat.logging.internal.PatternParser.Msg;
import org.tamacat.logging.internal.PatternParser.NewLine;
import org.tamacat.logging.internal.PatternParser.ThreadTok;
import org.tamacat.logging.internal.PatternParser.Token;

/**
 * Plain-text layout driven by the restricted pattern.
 *
 * <p>Line-integrity guarantee: event data can never introduce a line break. The only line breaks
 * come from {@code %n} in the (trusted) pattern and from multi-line {@code %ex}, where every
 * stack trace line starts with a TAB. A record always ends with {@code '\n'}.
 */
public final class TextLayout implements Layout {

    private final List<Token> tokens;
    private final ThrowableRenderer renderer;

    public TextLayout(List<Token> tokens, ThrowableRenderer renderer) {
        this.tokens = List.copyOf(tokens);
        this.renderer = renderer;
    }

    @Override
    public void format(LogEvent e, StringBuilder out) {
        for (Token t : tokens) {
            switch (t) {
                case Literal l -> out.append(l.text());
                case Date d -> out.append(Timestamps.iso(e.timestamp()));
                case LevelTok l -> appendLevel(e, out);
                case ThreadTok th -> TextEscaper.escape(e.threadName(), out);
                case LoggerTok lg -> TextEscaper.escape(abbreviate(e.loggerName(), lg.maxLength()), out);
                case Msg m -> TextEscaper.escape(e.message(), out);
                case Labels lb -> appendLabels(e.labels(), lb.key(), out);
                case Ex ex -> appendThrowable(e.thrown(), ex.oneline(), out);
                case NewLine n -> out.append('\n');
            }
        }
        if (out.isEmpty() || out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
    }

    private static void appendLevel(LogEvent e, StringBuilder out) {
        String name = e.level().name();
        out.append(name);
        for (int i = name.length(); i < 5; i++) {
            out.append(' ');
        }
    }

    /** {@code k="v"} pairs: quoting makes values containing {@code , = }} unambiguous. */
    private static void appendLabels(Map<String, String> labels, String key, StringBuilder out) {
        if (key != null) {
            String v = labels.get(key);
            if (v != null) {
                TextEscaper.escape(v, out);
            }
            return;
        }
        if (labels.isEmpty()) {
            return;
        }
        out.append(" {");
        boolean first = true;
        for (Map.Entry<String, String> en : labels.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(en.getKey()).append("=\"");
            TextEscaper.escapeQuoted(en.getValue(), out);
            out.append('"');
        }
        out.append('}');
    }

    private void appendThrowable(Throwable t, boolean oneline, StringBuilder out) {
        if (t == null) {
            return;
        }
        List<ThrowableRenderer.Line> lines = renderer.render(t);
        if (oneline) {
            boolean first = true;
            for (ThrowableRenderer.Line line : lines) {
                out.append(first ? " " : " | ");
                first = false;
                TextEscaper.escape(line.text(), out);
            }
            return;
        }
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        for (ThrowableRenderer.Line line : lines) {
            out.repeat('\t', Math.max(line.indent(), 1));
            TextEscaper.escape(line.text(), out);
            out.append('\n');
        }
    }

    /** Logback-style abbreviation: shortens leading package segments to one char until it fits. */
    static String abbreviate(String name, int max) {
        if (max < 0 || name.length() <= max) {
            return name;
        }
        String[] parts = name.split("\\.", -1);
        int length = name.length();
        StringBuilder sb = new StringBuilder(max + 16);
        for (int i = 0; i < parts.length - 1; i++) {
            String p = parts[i];
            if (length > max && p.length() > 1) {
                length -= p.length() - 1;
                sb.append(p.charAt(0));
            } else {
                sb.append(p);
            }
            sb.append('.');
        }
        return sb.append(parts[parts.length - 1]).toString();
    }
}
