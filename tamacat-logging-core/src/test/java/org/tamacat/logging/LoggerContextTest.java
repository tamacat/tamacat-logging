package org.tamacat.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoggerContextTest {

    private static final Pattern RECORD_START = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z .*");

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final LoggerContext ctx = LoggerContext.get();

    @BeforeEach
    void setUp() {
        ctx.setOutput(out);
    }

    @AfterEach
    void tearDown() {
        ctx.setOutput(null);
        ctx.configure(Configuration.defaults());
        LogContext.clear();
    }

    private List<String> lines() {
        String s = out.toString(StandardCharsets.UTF_8);
        assertTrue(s.isEmpty() || s.endsWith("\n"), "record must end with newline");
        return s.isEmpty() ? List.of() : List.of(s.split("\n", -1)).subList(0, s.split("\n", -1).length - 1);
    }

    private void json() {
        ctx.configure(Configuration.builder().format(Configuration.Format.JSON).rootLevel(Level.TRACE).build());
    }

    @Test
    void textLayoutPreventsLineInjection() {
        ctx.configure(Configuration.defaults());
        LogContext.put("reqId", "a\", forged=\"x");
        ctx.log("app.Login", Level.INFO, "login failed for admin\n2026-01-01T00:00:00.000Z INFO  fake record\u001B[2J",
                Map.of(), new IllegalStateException("bad\r\ninput"));
        List<String> lines = lines();
        assertTrue(RECORD_START.matcher(lines.getFirst()).matches(), lines.getFirst());
        for (String l : lines.subList(1, lines.size())) {
            assertTrue(l.startsWith("\t"), "continuation line must start with TAB: " + l);
        }
        String all = String.join("\n", lines);
        assertTrue(all.contains("admin\\n2026"));
        assertTrue(all.contains("\\u001B[2J"));
        assertTrue(all.contains("reqId=\"a\\\", forged=\\\"x\""), all);
        assertTrue(all.contains("bad\\r\\ninput"));
    }

    @Test
    void onelineException() {
        ctx.configure(Configuration.builder().pattern("%level %msg%ex{oneline}%n").build());
        ctx.log("x", Level.ERROR, "failed", null, new RuntimeException("outer", new java.io.IOException("inner")));
        List<String> lines = lines();
        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.getFirst().startsWith("ERROR failed java.lang.RuntimeException: outer | at "));
        assertTrue(lines.getFirst().contains("| Caused by: java.io.IOException: inner"));
    }

    @Test
    void jsonIsEcsAndConfinesLabels() {
        ctx.configure(Configuration.builder().format(Configuration.Format.JSON).serviceName("svc").build());
        LogContext.put("log.level", "FATAL");          // invalid key (dot) -> dropped
        LogContext.put("message", "spoofed");          // valid key -> stays under labels
        LogContext.put("password", "hunter2");         // masked
        LogContext.put("reqId", "r1");
        ctx.log("org.example.App", Level.WARN, "hello \"world\"\n", Map.of("reqId", "kv-wins", "Token", "t"), null);
        Map<String, Object> m = JsonTestParser.parseObject(lines().getFirst());
        assertEquals("WARN", m.get("log.level"));
        assertEquals("hello \"world\"\n", m.get("message"));
        assertEquals("8.11", m.get("ecs.version"));
        assertEquals("app", m.get("event.dataset"));
        assertEquals("org.example.App", m.get("log.logger"));
        assertEquals("svc", m.get("service.name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> labels = (Map<String, Object>) m.get("labels");
        assertEquals(Map.of("message", "spoofed", "password", "***", "reqId", "kv-wins", "Token", "***"), labels);
    }

    @Test
    void jsonExceptionWithCycleAndLimits() {
        json();
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        a.addSuppressed(new IllegalStateException("s"));
        ctx.log("x", Level.ERROR, "boom", null, a);
        Map<String, Object> m = JsonTestParser.parseObject(lines().getFirst());
        assertEquals("java.lang.RuntimeException", m.get("error.type"));
        assertEquals("a", m.get("error.message"));
        String st = (String) m.get("error.stack_trace");
        assertTrue(st.contains("[CIRCULAR REFERENCE: java.lang.RuntimeException: a]"), st);
        assertTrue(st.contains("\tSuppressed: java.lang.IllegalStateException: s"), st);

        out.reset();
        ctx.configure(Configuration.builder().format(Configuration.Format.JSON).stackMaxFrames(3).causeMaxDepth(2).build());
        Throwable deep = new Exception("root");
        for (int i = 0; i < 50; i++) {
            deep = new Exception("level" + i, deep);
        }
        ctx.log("x", Level.ERROR, "deep", null, deep);
        String st2 = (String) JsonTestParser.parseObject(lines().getFirst()).get("error.stack_trace");
        assertTrue(st2.split("\n").length <= 3 + 2 + 2, st2);
        assertTrue(st2.contains("truncated"), st2);
    }

    @Test
    void hostileThrowableDoesNotBreakLogging() {
        json();
        Throwable evil = new RuntimeException() {
            @Override
            public String getMessage() {
                throw new IllegalStateException("no");
            }

            @Override
            public synchronized Throwable getCause() {
                throw new IllegalStateException("no");
            }
        };
        ctx.log("x", Level.ERROR, "evil", null, evil);
        Map<String, Object> m = JsonTestParser.parseObject(lines().getFirst());
        assertEquals("[getMessage() failed]", m.get("error.message"));
    }

    @Test
    void levelsByPrefix() {
        ctx.configure(Configuration.builder().rootLevel(Level.WARN).level("org.tamacat", Level.DEBUG)
                .level("org.tamacat.noisy", Level.OFF).build());
        assertTrue(ctx.isEnabled("org.tamacat.httpd.Server", Level.DEBUG));
        assertFalse(ctx.isEnabled("org.tamacat.httpd.Server", Level.TRACE));
        assertFalse(ctx.isEnabled("org.tamacat.noisy.X", Level.ERROR));
        assertFalse(ctx.isEnabled("org.tamacatx.Y", Level.INFO));
        assertTrue(ctx.isEnabled("com.example", Level.WARN));
        ctx.log("com.example", Level.INFO, "dropped", null, null);
        assertEquals(List.of(), lines());
    }

    @Test
    void messageAndLabelLimits() {
        ctx.configure(Configuration.builder().format(Configuration.Format.JSON).messageMaxLength(10)
                .labelMaxLength(5).labelMaxKeys(2).build());
        LogContext.put("a", "1234567890");
        LogContext.put("b", "x");
        LogContext.put("c", "y");
        ctx.log("x", Level.INFO, "0123456789ABCDEF", null, null);
        Map<String, Object> m = JsonTestParser.parseObject(lines().getFirst());
        assertEquals("0123456789...[truncated]", m.get("message"));
        assertEquals(Map.of("a", "12345...[truncated]", "b", "x"), m.get("labels"));
    }

    @Test
    void concurrentVirtualThreadsNeverInterleave() throws Exception {
        json();
        int n = 2000;
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                int id = i;
                ex.submit(() -> {
                    LogContext.put("id", String.valueOf(id));
                    ctx.log("conc", Level.INFO, "msg-" + id + "\n" + "x".repeat(id % 300), null, null);
                    LogContext.clear();
                });
            }
        }
        List<String> lines = lines();
        assertEquals(n, lines.size());
        Set<String> ids = new HashSet<>();
        for (String l : lines) {
            Map<String, Object> m = JsonTestParser.parseObject(l);
            @SuppressWarnings("unchecked")
            String id = ((Map<String, String>) m.get("labels")).get("id");
            assertTrue(((String) m.get("message")).startsWith("msg-" + id + "\n"));
            assertTrue(((String) m.get("process.thread.name")).startsWith("virtual-"));
            ids.add(id);
        }
        assertEquals(n, ids.size());
    }

    @Test
    void contextIsNotInheritedByChildThreads() throws Exception {
        LogContext.put("reqId", "parent");
        String[] seen = new String[1];
        Thread t = new Thread(() -> seen[0] = LogContext.get("reqId"));
        t.start();
        t.join();
        assertNull(seen[0]);
    }

    @Test
    void systemLoggerIsRoutedHere() {
        ctx.configure(Configuration.builder().format(Configuration.Format.JSON).build());
        System.Logger logger = System.getLogger("sys.test");
        assertEquals("org.tamacat.logging.jdk.TamacatSystemLogger", logger.getClass().getName());
        logger.log(System.Logger.Level.WARNING, "user {0} from {1} {0,choice,0#x}", "${jndi:ldap://e/}", "10.0.0.1");
        logger.log(System.Logger.Level.ERROR, "failed", new RuntimeException("x"));
        logger.log(System.Logger.Level.DEBUG, "debug");
        List<String> lines = lines();
        assertEquals(2, lines.size());
        Map<String, Object> m = JsonTestParser.parseObject(lines.getFirst());
        assertEquals("WARN", m.get("log.level"));
        assertEquals("user ${jndi:ldap://e/} from 10.0.0.1 {0,choice,0#x}", m.get("message"));
        assertEquals("java.lang.RuntimeException", JsonTestParser.parseObject(lines.get(1)).get("error.type"));
        ctx.configure(Configuration.builder().rootLevel(Level.ERROR).build());
        assertFalse(logger.isLoggable(System.Logger.Level.WARNING));
        assertFalse(logger.isLoggable(System.Logger.Level.OFF));
    }
}
