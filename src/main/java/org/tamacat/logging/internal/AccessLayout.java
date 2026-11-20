package org.tamacat.logging.internal;

import java.nio.charset.StandardCharsets;

import org.tamacat.logging.access.AccessFormat;
import org.tamacat.logging.access.AccessLogEntry;

/**
 * Fixed access log formats. Text formats escape like Apache httpd's {@code ap_escape_logitem}:
 * {@code "} and {@code \} are backslash-escaped, control and non-ASCII bytes become {@code \xhh}.
 * Unquoted fields additionally escape spaces so they cannot shift field positions.
 */
public final class AccessLayout {

    private static final int MAX_ADDRESS = 64;
    private static final int MAX_USER = 128;
    private static final int MAX_TOKEN = 32;
    private static final int MAX_REQUEST_ID = 128;

    private final AccessFormat format;
    private final int maxUri;
    private final int maxUserAgent;
    private final int maxReferer;
    private final EcsJsonLayout json;

    /** @param json supplies JSON escaping options and the static {@code service.*} fields */
    public AccessLayout(AccessFormat format, int maxUri, int maxUserAgent, int maxReferer, EcsJsonLayout json) {
        this.format = format;
        this.maxUri = maxUri;
        this.maxUserAgent = maxUserAgent;
        this.maxReferer = maxReferer;
        this.json = json;
    }

    public void format(AccessLogEntry e, StringBuilder out) {
        if (format == AccessFormat.JSON) {
            formatJson(e, out);
            return;
        }
        unquoted(trunc(e.remoteAddress(), MAX_ADDRESS), out);
        out.append(" - ");
        unquoted(trunc(e.remoteUser(), MAX_USER), out);
        out.append(" [").append(Timestamps.clf(e.timestamp())).append("] \"");
        if (e.method() == null && e.requestUri() == null && e.protocol() == null) {
            out.append('-');
        } else {
            quotedOrDash(trunc(e.method(), MAX_TOKEN), out);
            out.append(' ');
            quotedOrDash(trunc(e.requestUri(), maxUri), out);
            out.append(' ');
            quotedOrDash(trunc(e.protocol(), MAX_TOKEN), out);
        }
        out.append("\" ").append(e.status()).append(' ');
        if (e.bytesSent() > 0) {
            out.append(e.bytesSent());
        } else {
            out.append('-');
        }
        if (format == AccessFormat.COMBINED) {
            out.append(" \"");
            quotedOrDash(trunc(e.referer(), maxReferer), out);
            out.append("\" \"");
            quotedOrDash(trunc(e.userAgent(), maxUserAgent), out);
            out.append("\" ");
            if (e.elapsed() != null) {
                out.append(micros(e));
            } else {
                out.append('-');
            }
            out.append(' ');
            unquoted(trunc(e.requestId(), MAX_REQUEST_ID), out);
        }
        out.append('\n');
    }

    private void formatJson(AccessLogEntry e, StringBuilder out) {
        String method = trunc(e.method(), MAX_TOKEN);
        String uri = trunc(e.requestUri(), maxUri);
        String protocol = trunc(e.protocol(), MAX_TOKEN);
        out.append("{\"@timestamp\":\"").append(Timestamps.iso(e.timestamp())).append('"');
        out.append(",\"log.level\":\"INFO\"");
        json.field(out, "message", nz(method) + " " + nz(uri) + " " + nz(protocol) + " " + e.status());
        out.append(",\"ecs.version\":\"").append(EcsJsonLayout.ECS_VERSION).append('"');
        out.append(",\"event.dataset\":\"access\"");
        json.appendService(out);
        optional(out, "source.address", trunc(e.remoteAddress(), MAX_ADDRESS));
        optional(out, "user.name", trunc(e.remoteUser(), MAX_USER));
        optional(out, "http.request.method", method);
        optional(out, "url.original", uri);
        if (protocol != null) {
            json.field(out, "http.version", protocol.startsWith("HTTP/") ? protocol.substring(5) : protocol);
        }
        out.append(",\"http.response.status_code\":").append(e.status());
        if (e.bytesSent() >= 0) {
            out.append(",\"http.response.body.bytes\":").append(e.bytesSent());
        }
        optional(out, "http.request.referrer", trunc(e.referer(), maxReferer));
        optional(out, "user_agent.original", trunc(e.userAgent(), maxUserAgent));
        if (e.elapsed() != null) {
            out.append(",\"event.duration\":").append(nanos(e));
        }
        optional(out, "http.request.id", trunc(e.requestId(), MAX_REQUEST_ID));
        out.append("}\n");
    }

    private void optional(StringBuilder out, String name, String value) {
        if (value != null && !value.isEmpty()) {
            json.field(out, name, value);
        }
    }

    private static long micros(AccessLogEntry e) {
        try {
            return e.elapsed().toNanos() / 1000;
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static long nanos(AccessLogEntry e) {
        try {
            return e.elapsed().toNanos();
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static String nz(String s) {
        return s == null ? "-" : s;
    }

    private static String trunc(String s, int max) {
        return Safe.truncate(s, max);
    }

    private static void quotedOrDash(String s, StringBuilder out) {
        if (s == null || s.isEmpty()) {
            out.append('-');
        } else {
            escape(s, out, false);
        }
    }

    private static void unquoted(String s, StringBuilder out) {
        if (s == null || s.isEmpty()) {
            out.append('-');
        } else {
            escape(s, out, true);
        }
    }

    /** Byte-wise escaping of the UTF-8 encoding (lone surrogates become '?'). */
    static void escape(String s, StringBuilder out, boolean escapeSpace) {
        for (byte raw : s.getBytes(StandardCharsets.UTF_8)) {
            int b = raw & 0xFF;
            switch (b) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case 0x0B -> out.append("\\v");
                default -> {
                    if (b < 0x20 || b >= 0x7F || (escapeSpace && b == ' ')) {
                        Chars.appendHexByte(b, out);
                    } else {
                        out.append((char) b);
                    }
                }
            }
        }
    }
}
