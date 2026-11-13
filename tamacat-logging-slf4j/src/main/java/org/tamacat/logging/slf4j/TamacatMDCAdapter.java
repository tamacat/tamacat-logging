package org.tamacat.logging.slf4j;

import java.util.Deque;
import java.util.Map;

import org.slf4j.helpers.ThreadLocalMapOfStacks;
import org.slf4j.spi.MDCAdapter;
import org.tamacat.logging.LogContext;

/**
 * MDC backed by {@link LogContext}: plain {@link ThreadLocal} (not inheritable), so request
 * context cannot leak into threads spawned from a request thread. The same context is used by
 * {@link System.Logger} output.
 */
final class TamacatMDCAdapter implements MDCAdapter {

    private final ThreadLocalMapOfStacks stacks = new ThreadLocalMapOfStacks();

    @Override
    public void put(String key, String val) {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }
        LogContext.put(key, val);
    }

    @Override
    public String get(String key) {
        return LogContext.get(key);
    }

    @Override
    public void remove(String key) {
        LogContext.remove(key);
    }

    @Override
    public void clear() {
        LogContext.clear();
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        Map<String, String> copy = LogContext.copy();
        return copy.isEmpty() ? null : new java.util.HashMap<>(copy);
    }

    @Override
    public void setContextMap(Map<String, String> contextMap) {
        LogContext.replace(contextMap);
    }

    @Override
    public void pushByKey(String key, String value) {
        stacks.pushByKey(key, value);
    }

    @Override
    public String popByKey(String key) {
        return stacks.popByKey(key);
    }

    @Override
    public Deque<String> getCopyOfDequeByKey(String key) {
        return stacks.getCopyOfDequeByKey(key);
    }

    @Override
    public void clearDequeByKey(String key) {
        stacks.clearDequeByKey(key);
    }
}
