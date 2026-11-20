package org.tamacat.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tamacat.logging.access.AccessFormat;
import org.tamacat.logging.access.AccessLogEntry;
import org.tamacat.logging.access.AccessLogger;

class AccessLogTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private static final AccessLogEntry ENTRY = AccessLogEntry.builder()
            .timestamp(Instant.parse("2026-11-13T13:52:01.123Z"))
            .remoteAddress("192.0.2.1")
            .remoteUser("alice bob")
            .method("GET")
            .requestUri("/search?q=\"x\"\r\n127.0.0.1 - - [fake] \"GET / HTTP/1.1\" 200 1")
            .protocol("HTTP/1.1")
            .status(200)
            .bytesSent(1234)
            .referer(null)
            .userAgent("Mozilla/5.0 \u001B[31m\u3042")
            .elapsed(Duration.ofMillis(15))
            .requestId("req-1")
            .build();

    @BeforeEach
    void setUp() {
        LoggerContext.get().setOutput(out);
    }

    @AfterEach
    void tearDown() {
        LoggerContext.get().setOutput(null);
        LoggerContext.get().configure(Configuration.defaults());
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void common() {
        LoggerContext.get().configure(Configuration.builder().accessFormat(AccessFormat.COMMON).build());
        AccessLogger.get().log(ENTRY);
        assertEquals("192.0.2.1 - alice\\x20bob [13/Nov/2026:13:52:01 +0000] "
                + "\"GET /search?q=\\\"x\\\"\\r\\n127.0.0.1 - - [fake] \\\"GET / HTTP/1.1\\\" 200 1 HTTP/1.1\" 200 1234\n",
                output());
    }

    @Test
    void combinedIsDefaultForText() {
        AccessLogger.get().log(ENTRY);
        String s = output();
        assertEquals(1, s.split("\n", -1).length - 1, s);
        assertTrue(s.endsWith(" 200 1234 \"-\" \"Mozilla/5.0 \\x1b[31m\\xe3\\x81\\x82\" 15000 req-1\n"), s);
    }

    @Test
    void jsonIsDefaultForJsonFormat() {
        LoggerContext.get().configure(Configuration.builder().format(Configuration.Format.JSON)
                .serviceName("web").build());
        AccessLogger.get().log(ENTRY);
        Map<String, Object> m = JsonTestParser.parseObject(output().stripTrailing());
        assertEquals("access", m.get("event.dataset"));
        assertEquals("web", m.get("service.name"));
        assertEquals("192.0.2.1", m.get("source.address"));
        assertEquals("GET", m.get("http.request.method"));
        assertEquals(ENTRY.requestUri(), m.get("url.original"));
        assertEquals("1.1", m.get("http.version"));
        assertEquals(200L, m.get("http.response.status_code"));
        assertEquals(1234L, m.get("http.response.body.bytes"));
        assertEquals(15_000_000L, m.get("event.duration"));
        assertEquals("Mozilla/5.0 \u001B[31m\u3042", m.get("user_agent.original"));
        assertEquals(null, m.get("http.request.referrer"));
    }

    @Test
    void limitsAndDisable() {
        LoggerContext.get().configure(Configuration.builder().accessUriMaxLength(8).build());
        AccessLogger.get().log(AccessLogEntry.builder().requestUri("/" + "a".repeat(100)).status(404).build());
        assertTrue(output().contains("\"- /aaaaaaa...[truncated] -\" 404 -"), output());
        out.reset();
        LoggerContext.get().configure(Configuration.builder().accessLogEnabled(false).build());
        AccessLogger.get().log(ENTRY);
        assertEquals("", output());
    }
}
