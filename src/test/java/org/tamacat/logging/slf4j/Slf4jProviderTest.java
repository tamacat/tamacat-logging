package org.tamacat.logging.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.tamacat.logging.Configuration;
import org.tamacat.logging.Level;
import org.tamacat.logging.LoggerContext;

class Slf4jProviderTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final Logger log = LoggerFactory.getLogger("org.example.Web");

    @BeforeEach
    void setUp() {
        LoggerContext.get().setOutput(out);
        LoggerContext.get().configure(Configuration.builder().format(Configuration.Format.JSON).build());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        LoggerContext.get().setOutput(null);
        LoggerContext.get().configure(Configuration.defaults());
    }

    private String output() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void providerIsBound() {
        assertEquals(TamacatSlf4jLogger.class, log.getClass());
        assertEquals("org.example.Web", log.getName());
        assertTrue(MDC.getMDCAdapter() instanceof TamacatMDCAdapter);
    }

    @Test
    void placeholdersAndTrailingThrowable() {
        log.info("user {} logged in from {}", "${jndi:ldap://evil/a}", "10.0.0.1", new IllegalStateException("x"));
        String s = output();
        assertTrue(s.contains("\"message\":\"user ${jndi:ldap://evil/a} logged in from 10.0.0.1\""), s);
        assertTrue(s.contains("\"error.type\":\"java.lang.IllegalStateException\""), s);
        assertEquals(1, s.split("\n").length);
    }

    @Test
    void singleThrowableArgumentIsTheException() {
        log.error("failed", new RuntimeException("boom"));
        assertTrue(output().contains("\"error.message\":\"boom\""), output());
    }

    @Test
    void mdcAndFluentKeyValues() {
        MDC.put("reqId", "r-1");
        MDC.put("session", "s-1");
        log.atWarn().addKeyValue("session", "kv").addKeyValue("authorization", "Bearer abc")
                .setMessage("hello {}").addArgument("world").log();
        String s = output();
        assertTrue(s.contains("\"log.level\":\"WARN\""), s);
        assertTrue(s.contains("\"message\":\"hello world\""), s);
        assertTrue(s.contains("\"labels\":{\"reqId\":\"r-1\",\"session\":\"kv\",\"authorization\":\"***\"}"), s);
    }

    @Test
    void levelsAreHonoured() {
        LoggerContext.get().configure(Configuration.builder().format(Configuration.Format.JSON)
                .rootLevel(Level.WARN).build());
        assertFalse(log.isInfoEnabled());
        assertTrue(log.isWarnEnabled());
        log.info("dropped");
        log.atDebug().log("dropped too");
        assertEquals("", output());
    }

    @Test
    void textFormatEscapesNewlines() {
        LoggerContext.get().configure(Configuration.defaults());
        log.info("line1\nline2 {}", "\r\nINJECTED");
        String s = output();
        assertEquals(1, s.split("\n").length, s);
        assertTrue(s.contains("line1\\nline2 \\r\\nINJECTED"), s);
    }
}
