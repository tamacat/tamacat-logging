package org.tamacat.logging.internal;

/**
 * Helpers that make logging robust against hostile or broken objects.
 */
public final class Safe {

    public static final String TRUNCATED = "...[truncated]";

    private Safe() {
    }

    /** Rethrows errors that must never be swallowed (e.g. OutOfMemoryError). */
    public static void rethrowIfFatal(Throwable t) {
        if (t instanceof VirtualMachineError && !(t instanceof StackOverflowError)) {
            throw (VirtualMachineError) t;
        }
    }

    /** {@code toString()} that never throws. */
    public static String toString(Object o) {
        if (o == null) {
            return "null";
        }
        try {
            String s = o.toString();
            return s == null ? "null" : s;
        } catch (Throwable t) {
            rethrowIfFatal(t);
            return "[toString() failed: " + o.getClass().getName() + "]";
        }
    }

    /** Truncates to at most {@code max} chars (never splitting a surrogate pair) and appends a marker. */
    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        int end = max;
        if (end > 0 && Character.isHighSurrogate(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end) + TRUNCATED;
    }

    /** {@code getMessage()} that never throws. */
    public static String message(Throwable t) {
        try {
            return t.getMessage();
        } catch (Throwable e) {
            rethrowIfFatal(e);
            return "[getMessage() failed]";
        }
    }

    /** Thread name; virtual threads (which are unnamed by default) get a synthetic name. */
    public static String threadName(Thread t) {
        String name = t.getName();
        if (name == null || name.isEmpty()) {
            return (t.isVirtual() ? "virtual-" : "thread-") + t.threadId();
        }
        return name;
    }
}
