package org.tamacat.logging;

import java.util.Locale;

/**
 * Log levels in ascending order of severity. {@link #OFF} is only valid as a threshold.
 */
public enum Level {
    TRACE, DEBUG, INFO, WARN, ERROR, OFF;

    /**
     * Returns true if an event of this level passes the given threshold.
     */
    public boolean isEnabledFor(Level threshold) {
        return this != OFF && threshold != OFF && this.ordinal() >= threshold.ordinal();
    }

    /**
     * Strictly parses a level name (case-insensitive). Unknown names are rejected.
     *
     * @throws IllegalArgumentException if the name is not a known level
     */
    public static Level parse(String name) {
        if (name == null) {
            throw new IllegalArgumentException("level is null");
        }
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "TRACE" -> TRACE;
            case "DEBUG" -> DEBUG;
            case "INFO" -> INFO;
            case "WARN" -> WARN;
            case "ERROR" -> ERROR;
            case "OFF" -> OFF;
            default -> throw new IllegalArgumentException("unknown level");
        };
    }
}
