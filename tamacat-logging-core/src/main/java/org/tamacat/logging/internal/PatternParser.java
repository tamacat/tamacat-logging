package org.tamacat.logging.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the restricted text pattern into a fixed token list. Only the documented tokens are
 * accepted; anything else is a configuration error. The pattern comes from configuration only
 * and is parsed once; log data is never interpreted as a pattern.
 */
public final class PatternParser {

    public static final int MAX_PATTERN_LENGTH = 1024;

    public sealed interface Token permits Literal, Date, LevelTok, ThreadTok, LoggerTok, Msg, Labels, Ex, NewLine {
    }

    public record Literal(String text) implements Token {
    }

    public record Date() implements Token {
    }

    public record LevelTok() implements Token {
    }

    public record ThreadTok() implements Token {
    }

    /** @param maxLength abbreviation target, or -1 for the full name */
    public record LoggerTok(int maxLength) implements Token {
    }

    public record Msg() implements Token {
    }

    /** @param key a single label key, or null for all labels */
    public record Labels(String key) implements Token {
    }

    public record Ex(boolean oneline) implements Token {
    }

    public record NewLine() implements Token {
    }

    private PatternParser() {
    }

    /** @throws IllegalArgumentException on any unknown or malformed token */
    public static List<Token> parse(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern is empty");
        }
        if (pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("pattern too long");
        }
        if (Chars.containsUnsafe(pattern)) {
            throw new IllegalArgumentException("pattern contains control characters");
        }
        List<Token> tokens = new ArrayList<>();
        StringBuilder lit = new StringBuilder();
        int i = 0;
        int len = pattern.length();
        while (i < len) {
            char c = pattern.charAt(i);
            if (c != '%') {
                lit.append(c);
                i++;
                continue;
            }
            i++;
            if (i >= len) {
                throw new IllegalArgumentException("dangling '%' in pattern");
            }
            if (pattern.charAt(i) == '%') {
                lit.append('%');
                i++;
                continue;
            }
            int start = i;
            while (i < len && pattern.charAt(i) >= 'a' && pattern.charAt(i) <= 'z') {
                i++;
            }
            String name = pattern.substring(start, i);
            String arg = null;
            if (i < len && pattern.charAt(i) == '{') {
                int close = pattern.indexOf('}', i);
                if (close < 0) {
                    throw new IllegalArgumentException("unclosed '{' in pattern");
                }
                arg = pattern.substring(i + 1, close);
                if (!LabelBuilder.isValidKey(arg)) {
                    throw new IllegalArgumentException("invalid token option");
                }
                i = close + 1;
            }
            if (!lit.isEmpty()) {
                tokens.add(new Literal(lit.toString()));
                lit.setLength(0);
            }
            tokens.add(token(name, arg));
        }
        if (!lit.isEmpty()) {
            tokens.add(new Literal(lit.toString()));
        }
        return List.copyOf(tokens);
    }

    private static Token token(String name, String arg) {
        return switch (name) {
            case "d" -> noArg(new Date(), arg);
            case "level" -> noArg(new LevelTok(), arg);
            case "thread" -> noArg(new ThreadTok(), arg);
            case "msg" -> noArg(new Msg(), arg);
            case "n" -> noArg(new NewLine(), arg);
            case "logger" -> new LoggerTok(arg == null ? -1 : loggerLength(arg));
            case "labels" -> new Labels(arg);
            case "ex" -> {
                if (arg == null) {
                    yield new Ex(false);
                }
                if (arg.equals("oneline")) {
                    yield new Ex(true);
                }
                throw new IllegalArgumentException("unknown %ex option");
            }
            default -> throw new IllegalArgumentException("unknown pattern token");
        };
    }

    private static Token noArg(Token t, String arg) {
        if (arg != null) {
            throw new IllegalArgumentException("token does not take an option");
        }
        return t;
    }

    private static int loggerLength(String arg) {
        try {
            int n = Integer.parseInt(arg);
            if (n < 1 || n > 1024) {
                throw new IllegalArgumentException("%logger length out of range");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("%logger option must be a number");
        }
    }
}
