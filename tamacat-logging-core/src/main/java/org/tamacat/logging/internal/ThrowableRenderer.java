package org.tamacat.logging.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Renders a throwable into raw (unescaped) lines, JDK style, with hard limits on
 * frames, nesting depth and total lines, and with cycle detection.
 */
public final class ThrowableRenderer {

    /** A raw line. {@code indent} 0 = header, 1 = frame of a top-level throwable, and so on. */
    public record Line(int indent, String text) {
    }

    private final int maxFrames;
    private final int maxDepth;
    private final int maxMessageLength;

    public ThrowableRenderer(int maxFrames, int maxDepth, int maxMessageLength) {
        this.maxFrames = maxFrames;
        this.maxDepth = maxDepth;
        this.maxMessageLength = maxMessageLength;
    }

    public List<Line> render(Throwable t) {
        List<Line> lines = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        State st = new State(maxFrames + maxDepth + 1);
        renderOne(t, null, "", 0, 0, lines, seen, st);
        return lines;
    }

    public String header(Throwable t) {
        String msg = Safe.message(t);
        String name = t.getClass().getName();
        return msg == null ? name : name + ": " + Safe.truncate(msg, maxMessageLength);
    }

    public String message(Throwable t) {
        String msg = Safe.message(t);
        return msg == null ? null : Safe.truncate(msg, maxMessageLength);
    }

    private static final class State {
        int linesLeft;
        boolean truncated;

        State(int linesLeft) {
            this.linesLeft = linesLeft;
        }
    }

    private void renderOne(Throwable t, StackTraceElement[] enclosing, String caption, int indent, int depth,
                           List<Line> lines, Set<Throwable> seen, State st) {
        if (st.truncated) {
            return;
        }
        if (!seen.add(t)) {
            add(lines, st, indent, caption + "[CIRCULAR REFERENCE: " + header(t) + "]");
            return;
        }
        if (depth > maxDepth) {
            add(lines, st, indent, caption + "[cause chain truncated]");
            return;
        }
        if (!add(lines, st, indent, caption + header(t))) {
            return;
        }

        StackTraceElement[] trace = stackTrace(t);
        int m = trace.length - 1;
        if (enclosing != null) {
            int n = enclosing.length - 1;
            while (m >= 0 && n >= 0 && trace[m].equals(enclosing[n])) {
                m--;
                n--;
            }
        }
        int inCommon = trace.length - 1 - m;
        for (int i = 0; i <= m; i++) {
            if (!add(lines, st, indent + 1, "at " + trace[i])) {
                return;
            }
        }
        if (inCommon != 0 && !add(lines, st, indent + 1, "... " + inCommon + " more")) {
            return;
        }
        for (Throwable s : suppressed(t)) {
            renderOne(s, trace, "Suppressed: ", indent + 1, depth + 1, lines, seen, st);
        }
        Throwable cause = cause(t);
        if (cause != null) {
            renderOne(cause, trace, "Caused by: ", indent, depth + 1, lines, seen, st);
        }
    }

    private static boolean add(List<Line> lines, State st, int indent, String text) {
        if (st.linesLeft <= 0) {
            if (!st.truncated) {
                st.truncated = true;
                lines.add(new Line(Math.max(indent, 1), "...[stack trace truncated]"));
            }
            return false;
        }
        st.linesLeft--;
        lines.add(new Line(indent, text));
        return true;
    }

    private static StackTraceElement[] stackTrace(Throwable t) {
        try {
            StackTraceElement[] st = t.getStackTrace();
            return st == null ? new StackTraceElement[0] : st;
        } catch (Throwable e) {
            Safe.rethrowIfFatal(e);
            return new StackTraceElement[0];
        }
    }

    private static Throwable[] suppressed(Throwable t) {
        try {
            return t.getSuppressed();
        } catch (Throwable e) {
            Safe.rethrowIfFatal(e);
            return new Throwable[0];
        }
    }

    private static Throwable cause(Throwable t) {
        try {
            Throwable c = t.getCause();
            return c == t ? null : c;
        } catch (Throwable e) {
            Safe.rethrowIfFatal(e);
            return null;
        }
    }
}
