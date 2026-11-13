package org.tamacat.logging;

import java.io.OutputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.tamacat.logging.access.AccessLogEntry;
import org.tamacat.logging.internal.AccessLayout;
import org.tamacat.logging.internal.EcsJsonLayout;
import org.tamacat.logging.internal.LabelBuilder;
import org.tamacat.logging.internal.Layout;
import org.tamacat.logging.internal.LogEvent;
import org.tamacat.logging.internal.PatternParser;
import org.tamacat.logging.internal.Safe;
import org.tamacat.logging.internal.StatusReporter;
import org.tamacat.logging.internal.StdoutSink;
import org.tamacat.logging.internal.TextLayout;
import org.tamacat.logging.internal.ThrowableRenderer;

/**
 * The logging engine: holds the active configuration and writes events to stdout.
 * All logging methods are safe to call from any thread and never throw.
 */
public final class LoggerContext {

    private static final int MAX_LOGGER_NAME = 256;
    private static final int LEVEL_CACHE_LIMIT = 10_000;

    private static final class Holder {
        static final LoggerContext INSTANCE = new LoggerContext();
    }

    private final StdoutSink sink = new StdoutSink();
    private final ReentrantLock configLock = new ReentrantLock();
    private volatile State state;

    private LoggerContext() {
        state = new State(ConfigLoader.load());
    }

    /**
     * Returns the singleton. May return {@code null} only if called re-entrantly while the context
     * itself is being initialized (e.g. by JDK code logging during configuration loading).
     */
    public static LoggerContext get() {
        return Holder.INSTANCE;
    }

    /** Replaces the active configuration. */
    public void configure(Configuration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        State s = new State(configuration);
        configLock.lock();
        try {
            state = s;
        } finally {
            configLock.unlock();
        }
    }

    public Configuration configuration() {
        return state.config;
    }

    /** Redirects output (for tests or embedding). {@code null} restores stdout. */
    public void setOutput(OutputStream out) {
        sink.setOutput(out);
    }

    public boolean isEnabled(String loggerName, Level level) {
        return level != null && level.isEnabledFor(state.levelFor(loggerName == null ? "" : loggerName));
    }

    /**
     * Logs an already formatted message.
     *
     * @param keyValues per-event key-values (may be null); merged with {@link LogContext}
     */
    public void log(String loggerName, Level level, String message, Map<String, String> keyValues,
                    Throwable thrown) {
        try {
            State s = state;
            String name = loggerName == null ? "" : loggerName;
            if (level == null || !level.isEnabledFor(s.levelFor(name))) {
                return;
            }
            Configuration c = s.config;
            LogEvent event = new LogEvent(
                    Instant.now(),
                    level,
                    Safe.truncate(name, MAX_LOGGER_NAME),
                    Safe.truncate(Safe.threadName(Thread.currentThread()), MAX_LOGGER_NAME),
                    Safe.truncate(message == null ? "null" : message, c.messageMaxLength()),
                    s.labels.build(LogContext.view(), keyValues),
                    thrown);
            StringBuilder sb = new StringBuilder(256);
            s.layout.format(event, sb);
            sink.write(sb);
        } catch (Throwable t) {
            Safe.rethrowIfFatal(t);
            StatusReporter.report("failed to log event", t);
        }
    }

    /** Writes an access log entry if access logging is enabled. Never throws. */
    public void logAccess(AccessLogEntry entry) {
        if (entry == null) {
            return;
        }
        try {
            State s = state;
            if (!s.config.accessLogEnabled()) {
                return;
            }
            StringBuilder sb = new StringBuilder(256);
            s.access.format(entry, sb);
            sink.write(sb);
        } catch (Throwable t) {
            Safe.rethrowIfFatal(t);
            StatusReporter.report("failed to write access log", t);
        }
    }

    /** Compiled, immutable view of a configuration. */
    private static final class State {
        final Configuration config;
        final Layout layout;
        final AccessLayout access;
        final LabelBuilder labels;
        final Map<String, Level> levelCache = new ConcurrentHashMap<>();

        State(Configuration c) {
            config = c;
            ThrowableRenderer renderer = new ThrowableRenderer(c.stackMaxFrames(), c.causeMaxDepth(),
                    c.messageMaxLength());
            EcsJsonLayout json = new EcsJsonLayout(renderer, c.serviceName(), c.serviceVersion(),
                    c.serviceEnvironment(), c.jsonAsciiOnly());
            layout = c.format() == Configuration.Format.JSON
                    ? json
                    : new TextLayout(PatternParser.parse(c.pattern()), renderer);
            access = new AccessLayout(c.accessFormat(), c.accessUriMaxLength(), c.accessUserAgentMaxLength(),
                    c.accessRefererMaxLength(), json);
            labels = new LabelBuilder(c.labelMaxKeys(), c.labelMaxLength(), c.maskKeys());
        }

        /** Longest matching dotted prefix wins; falls back to root. Cache is bounded. */
        Level levelFor(String name) {
            Level cached = levelCache.get(name);
            if (cached != null) {
                return cached;
            }
            Level level = resolve(name);
            if (levelCache.size() < LEVEL_CACHE_LIMIT) {
                levelCache.put(name, level);
            }
            return level;
        }

        private Level resolve(String name) {
            Map<String, Level> levels = config.levels();
            if (!levels.isEmpty()) {
                String n = name;
                while (!n.isEmpty()) {
                    Level l = levels.get(n);
                    if (l != null) {
                        return l;
                    }
                    int dot = n.lastIndexOf('.');
                    if (dot < 0) {
                        break;
                    }
                    n = n.substring(0, dot);
                }
            }
            return config.rootLevel();
        }
    }
}
