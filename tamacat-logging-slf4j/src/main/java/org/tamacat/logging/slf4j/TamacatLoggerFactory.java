package org.tamacat.logging.slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * Creates and caches loggers. The cache is bounded so that dynamically generated logger names
 * cannot grow memory without limit; beyond the limit loggers are created uncached.
 */
final class TamacatLoggerFactory implements ILoggerFactory {

    private static final int CACHE_LIMIT = 10_000;

    private final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(String name) {
        String n = name == null ? "" : name;
        Logger logger = loggers.get(n);
        if (logger != null) {
            return logger;
        }
        if (loggers.size() >= CACHE_LIMIT) {
            return new TamacatSlf4jLogger(n);
        }
        return loggers.computeIfAbsent(n, TamacatSlf4jLogger::new);
    }
}
