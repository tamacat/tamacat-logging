package org.tamacat.logging.internal;

import java.util.List;
import java.util.Map;

/**
 * ECS-compatible JSON Lines layout (dotted flat keys, fixed field order).
 *
 * <p>All caller-supplied key/value data (MDC and SLF4J key-values) is confined to the
 * {@code labels} object, so it can never override or duplicate reserved top-level fields such
 * as {@code log.level} or {@code @timestamp}.
 */
public final class EcsJsonLayout implements Layout {

    public static final String ECS_VERSION = "8.11";

    private final ThrowableRenderer renderer;
    private final String serviceName;
    private final String serviceVersion;
    private final String serviceEnvironment;
    private final boolean asciiOnly;

    public EcsJsonLayout(ThrowableRenderer renderer, String serviceName, String serviceVersion,
                         String serviceEnvironment, boolean asciiOnly) {
        this.renderer = renderer;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.serviceEnvironment = serviceEnvironment;
        this.asciiOnly = asciiOnly;
    }

    @Override
    public void format(LogEvent e, StringBuilder out) {
        out.append("{\"@timestamp\":\"").append(Timestamps.iso(e.timestamp())).append('"');
        out.append(",\"log.level\":\"").append(e.level().name()).append('"');
        field(out, "message", e.message());
        out.append(",\"ecs.version\":\"").append(ECS_VERSION).append('"');
        out.append(",\"event.dataset\":\"app\"");
        field(out, "log.logger", e.loggerName());
        field(out, "process.thread.name", e.threadName());
        appendService(out);
        Map<String, String> labels = e.labels();
        if (!labels.isEmpty()) {
            out.append(",\"labels\":{");
            boolean first = true;
            for (Map.Entry<String, String> en : labels.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                JsonEscaper.quote(en.getKey(), out, asciiOnly);
                out.append(':');
                JsonEscaper.quote(en.getValue(), out, asciiOnly);
            }
            out.append('}');
        }
        Throwable t = e.thrown();
        if (t != null) {
            field(out, "error.type", t.getClass().getName());
            String msg = renderer.message(t);
            if (msg != null) {
                field(out, "error.message", msg);
            }
            List<ThrowableRenderer.Line> lines = renderer.render(t);
            StringBuilder st = new StringBuilder(lines.size() * 64);
            for (ThrowableRenderer.Line line : lines) {
                if (!st.isEmpty()) {
                    st.append('\n');
                }
                st.repeat('\t', line.indent()).append(line.text());
            }
            field(out, "error.stack_trace", st);
        }
        out.append("}\n");
    }

    /** Writes the static {@code service.*} fields. Shared with the access log layout. */
    void appendService(StringBuilder out) {
        if (serviceName != null) {
            field(out, "service.name", serviceName);
        }
        if (serviceVersion != null) {
            field(out, "service.version", serviceVersion);
        }
        if (serviceEnvironment != null) {
            field(out, "service.environment", serviceEnvironment);
        }
    }

    void field(StringBuilder out, String name, CharSequence value) {
        out.append(",\"").append(name).append("\":");
        JsonEscaper.quote(value == null ? "" : value, out, asciiOnly);
    }
}
