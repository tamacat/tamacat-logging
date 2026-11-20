package org.tamacat.logging.access;

import java.util.Locale;

/** Fixed access log formats. No custom patterns are supported. */
public enum AccessFormat {
    /** Apache Common Log Format. */
    COMMON,
    /** Common + Referer, User-Agent, response time (microseconds) and request id. */
    COMBINED,
    /** ECS JSON Lines. */
    JSON;

    /** @throws IllegalArgumentException for unknown names */
    public static AccessFormat parse(String name) {
        if (name == null) {
            throw new IllegalArgumentException("format is null");
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "common" -> COMMON;
            case "combined" -> COMBINED;
            case "json" -> JSON;
            default -> throw new IllegalArgumentException("unknown access log format");
        };
    }
}
