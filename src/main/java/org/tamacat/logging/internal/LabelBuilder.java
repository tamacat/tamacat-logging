package org.tamacat.logging.internal;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the label map of an event from the thread context and per-event key-values:
 * validates key names, applies masking, and enforces key-count and value-length limits.
 */
public final class LabelBuilder {

    public static final int MAX_KEY_LENGTH = 64;
    public static final String MASK = "***";

    private final int maxKeys;
    private final int maxValueLength;
    private final Set<String> maskKeys;

    /** @param maskKeys lower-case key names whose values are replaced by {@value #MASK} */
    public LabelBuilder(int maxKeys, int maxValueLength, Set<String> maskKeys) {
        this.maxKeys = maxKeys;
        this.maxValueLength = maxValueLength;
        this.maskKeys = Set.copyOf(maskKeys);
    }

    /** Key-values take precedence over context entries with the same key. */
    public Map<String, String> build(Map<String, String> context, Map<String, String> keyValues) {
        boolean noCtx = context == null || context.isEmpty();
        boolean noKv = keyValues == null || keyValues.isEmpty();
        if (noCtx && noKv) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        int rejected = 0;
        if (!noCtx) {
            rejected += addAll(context, out);
        }
        if (!noKv) {
            rejected += addAll(keyValues, out);
        }
        if (rejected > 0) {
            StatusReporter.report(rejected + " label(s) dropped (invalid key name or too many keys)", null);
        }
        return out;
    }

    private int addAll(Map<String, String> src, Map<String, String> out) {
        int rejected = 0;
        for (Map.Entry<String, String> en : src.entrySet()) {
            String k = en.getKey();
            String v = en.getValue();
            if (v == null) {
                continue;
            }
            if (!isValidKey(k) || (!out.containsKey(k) && out.size() >= maxKeys)) {
                rejected++;
                continue;
            }
            out.put(k, maskKeys.contains(k.toLowerCase(Locale.ROOT)) ? MASK : Safe.truncate(v, maxValueLength));
        }
        return rejected;
    }

    /** {@code [A-Za-z0-9_-]{1,64}} - no dots (ECS labels must not contain them), no spaces. */
    public static boolean isValidKey(String k) {
        if (k == null || k.isEmpty() || k.length() > MAX_KEY_LENGTH) {
            return false;
        }
        for (int i = 0; i < k.length(); i++) {
            char c = k.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
