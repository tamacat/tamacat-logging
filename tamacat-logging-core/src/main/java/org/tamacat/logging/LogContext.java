package org.tamacat.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.tamacat.logging.internal.StatusReporter;

/**
 * Per-thread diagnostic context (the storage behind SLF4J {@code MDC}).
 *
 * <p>Uses a plain (non-inheritable) {@link ThreadLocal} so that request context can never leak
 * into threads created from a request thread (e.g. pooled workers). Callers should
 * {@link #clear()} at the end of each request.
 */
public final class LogContext {

    /** Hard cap on entries per thread to prevent unbounded growth. */
    public static final int MAX_ENTRIES = 128;

    private static final ThreadLocal<Map<String, String>> CONTEXT = new ThreadLocal<>();

    private LogContext() {
    }

    /** Puts a value. A {@code null} value removes the key. */
    public static void put(String key, String value) {
        Objects.requireNonNull(key, "key");
        if (value == null) {
            remove(key);
            return;
        }
        Map<String, String> map = CONTEXT.get();
        if (map == null) {
            map = new LinkedHashMap<>();
            CONTEXT.set(map);
        }
        if (!map.containsKey(key) && map.size() >= MAX_ENTRIES) {
            StatusReporter.report("LogContext entry limit reached; value ignored", null);
            return;
        }
        map.put(key, value);
    }

    public static String get(String key) {
        Map<String, String> map = CONTEXT.get();
        return map == null || key == null ? null : map.get(key);
    }

    public static void remove(String key) {
        Map<String, String> map = CONTEXT.get();
        if (map != null && key != null) {
            map.remove(key);
            if (map.isEmpty()) {
                CONTEXT.remove();
            }
        }
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /** Returns an unmodifiable copy of the current thread's context. */
    public static Map<String, String> copy() {
        Map<String, String> map = CONTEXT.get();
        return map == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    /** Replaces the current thread's context with the given entries. */
    public static void replace(Map<String, String> entries) {
        clear();
        if (entries != null) {
            entries.forEach((k, v) -> {
                if (k != null) {
                    put(k, v);
                }
            });
        }
    }

    /** Read-only view for the current thread; used internally while building an event. */
    static Map<String, String> view() {
        Map<String, String> map = CONTEXT.get();
        return map == null ? Map.of() : map;
    }
}
