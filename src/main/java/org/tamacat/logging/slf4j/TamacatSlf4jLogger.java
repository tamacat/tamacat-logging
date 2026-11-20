package org.tamacat.logging.slf4j;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.spi.LoggingEventAware;
import org.tamacat.logging.Level;
import org.tamacat.logging.LoggerContext;
import org.tamacat.logging.internal.MessageFormatter;
import org.tamacat.logging.internal.Safe;

/**
 * SLF4J logger. Implements {@link LoggingEventAware} so that fluent-API key-values become
 * structured labels instead of being concatenated into the message. Markers are ignored.
 */
final class TamacatSlf4jLogger extends LegacyAbstractLogger implements LoggingEventAware {

    @Serial
    private static final long serialVersionUID = 1L;

    TamacatSlf4jLogger(String name) {
        this.name = name;
    }

    @Override
    public boolean isTraceEnabled() {
        return enabled(Level.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return enabled(Level.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return enabled(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return enabled(Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return enabled(Level.ERROR);
    }

    private boolean enabled(Level level) {
        return LoggerContext.get().isEnabled(name, level);
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(org.slf4j.event.Level level, Marker marker, String msg,
                                               Object[] args, Throwable throwable) {
        emit(map(level), msg, args, throwable, null);
    }

    @Override
    public void log(LoggingEvent event) {
        Level level = map(event.getLevel());
        if (!enabled(level)) {
            return;
        }
        Map<String, String> kv = null;
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs != null && !pairs.isEmpty()) {
            kv = new LinkedHashMap<>();
            for (KeyValuePair p : pairs) {
                if (p != null && p.key != null && p.value != null) {
                    kv.put(p.key, Safe.toString(p.value));
                }
            }
        }
        emit(level, event.getMessage(), event.getArgumentArray(), event.getThrowable(), kv);
    }

    private void emit(Level level, String msg, Object[] args, Throwable throwable, Map<String, String> kv) {
        LoggerContext ctx = LoggerContext.get();
        if (!ctx.isEnabled(name, level)) {
            return;
        }
        Object[] a = args;
        Throwable t = throwable;
        // SLF4J convention: a trailing Throwable argument is the exception, not a format argument.
        if (t == null && a != null && a.length > 0 && a[a.length - 1] instanceof Throwable last) {
            t = last;
            a = java.util.Arrays.copyOf(a, a.length - 1);
        }
        String message = MessageFormatter.format(msg, a, ctx.configuration().messageMaxLength());
        ctx.log(name, level, message, kv, t);
    }

    private static Level map(org.slf4j.event.Level level) {
        return switch (level) {
            case TRACE -> Level.TRACE;
            case DEBUG -> Level.DEBUG;
            case INFO -> Level.INFO;
            case WARN -> Level.WARN;
            case ERROR -> Level.ERROR;
        };
    }
}
