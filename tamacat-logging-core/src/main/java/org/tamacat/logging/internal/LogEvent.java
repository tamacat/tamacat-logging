package org.tamacat.logging.internal;

import java.time.Instant;
import java.util.Map;

import org.tamacat.logging.Level;

/**
 * Immutable log event. {@code message} is already formatted and truncated;
 * {@code labels} are already validated, masked and truncated.
 */
public record LogEvent(
        Instant timestamp,
        Level level,
        String loggerName,
        String threadName,
        String message,
        Map<String, String> labels,
        Throwable thrown) {
}
