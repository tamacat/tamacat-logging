package org.tamacat.logging.access;

import org.tamacat.logging.LoggerContext;

/**
 * Access log API for web servers. Independent of SLF4J.
 *
 * <pre>{@code
 * AccessLogger.get().log(AccessLogEntry.builder()
 *     .remoteAddress(addr).method("GET").requestUri(uri).protocol("HTTP/1.1")
 *     .status(200).bytesSent(len).userAgent(ua).elapsed(duration).build());
 * }</pre>
 */
public final class AccessLogger {

    private static final AccessLogger INSTANCE = new AccessLogger();

    private AccessLogger() {
    }

    public static AccessLogger get() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return LoggerContext.get().configuration().accessLogEnabled();
    }

    /** Writes one entry. Never throws. */
    public void log(AccessLogEntry entry) {
        LoggerContext.get().logAccess(entry);
    }
}
