package org.tamacat.logging.internal;

import java.util.Arrays;

/**
 * Minimal message formatting. Arguments are substituted exactly once and the result is
 * never scanned again: there are no lookups, no expressions and no recursion.
 */
public final class MessageFormatter {

    private MessageFormatter() {
    }

    /**
     * SLF4J style: each {@code {}} is replaced by the next argument; {@code \{}} is a literal
     * {@code {}}; {@code \\{}} is a literal backslash followed by a substitution.
     * Output stops growing once it exceeds {@code maxLength}.
     */
    public static String format(String pattern, Object[] args, int maxLength) {
        if (pattern == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        StringBuilder sb = new StringBuilder(pattern.length() + 32);
        int i = 0;
        int a = 0;
        int len = pattern.length();
        while (i < len && sb.length() <= maxLength) {
            int j = pattern.indexOf("{}", i);
            if (j < 0 || a >= args.length) {
                sb.append(pattern, i, len);
                break;
            }
            if (j >= 1 && pattern.charAt(j - 1) == '\\') {
                if (j >= 2 && pattern.charAt(j - 2) == '\\') {
                    sb.append(pattern, i, j - 1);
                    appendArg(sb, args[a++], maxLength);
                } else {
                    sb.append(pattern, i, j - 1).append("{}");
                }
            } else {
                sb.append(pattern, i, j);
                appendArg(sb, args[a++], maxLength);
            }
            i = j + 2;
        }
        return sb.toString();
    }

    /**
     * {@link System.Logger} style: {@code {0}}, {@code {1}}, ... are replaced by the argument at
     * that index. Unlike {@link java.text.MessageFormat}, no sub-formats (e.g. {@code choice})
     * and no quoting rules are supported, so a format can never trigger nested evaluation.
     */
    public static String formatIndexed(String pattern, Object[] args, int maxLength) {
        if (pattern == null) {
            return "null";
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        StringBuilder sb = new StringBuilder(pattern.length() + 32);
        int len = pattern.length();
        int i = 0;
        while (i < len && sb.length() <= maxLength) {
            char c = pattern.charAt(i);
            if (c == '{') {
                int j = i + 1;
                int index = 0;
                while (j < len && j - i <= 3 && Character.isDigit(pattern.charAt(j))) {
                    index = index * 10 + (pattern.charAt(j) - '0');
                    j++;
                }
                if (j > i + 1 && j < len && pattern.charAt(j) == '}' && index < args.length) {
                    appendArg(sb, args[index], maxLength);
                    i = j + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static void appendArg(StringBuilder sb, Object arg, int maxLength) {
        String s;
        if (arg == null) {
            s = "null";
        } else if (arg.getClass().isArray()) {
            s = arrayToString(arg);
        } else {
            s = Safe.toString(arg);
        }
        sb.append(Safe.truncate(s, maxLength));
    }

    private static String arrayToString(Object arr) {
        try {
            return switch (arr) {
                case Object[] o -> Arrays.deepToString(o);
                case int[] v -> Arrays.toString(v);
                case long[] v -> Arrays.toString(v);
                case byte[] v -> Arrays.toString(v);
                case short[] v -> Arrays.toString(v);
                case char[] v -> Arrays.toString(v);
                case boolean[] v -> Arrays.toString(v);
                case float[] v -> Arrays.toString(v);
                case double[] v -> Arrays.toString(v);
                default -> Safe.toString(arr);
            };
        } catch (Throwable t) {
            Safe.rethrowIfFatal(t);
            return "[toString() failed: " + arr.getClass().getName() + "]";
        }
    }
}
