package org.tamacat.logging.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FormatterAndPatternTest {

    private static String fmt(String p, Object... args) {
        return MessageFormatter.format(p, args, 8192);
    }

    @Test
    void slf4jPlaceholders() {
        assertEquals("a 1 b 2", fmt("a {} b {}", 1, 2));
        assertEquals("a {} b 1", fmt("a \\{} b {}", 1));
        assertEquals("a \\1", fmt("a \\\\{}", 1));
        assertEquals("only 1 {}", fmt("only {} {}", 1));
        assertEquals("x", fmt("x", 1, 2));
        assertEquals("[1, 2] [a, [b]]", fmt("{} {}", new int[] {1, 2}, new Object[] {"a", new Object[] {"b"}}));
        assertEquals("null", MessageFormatter.format(null, null, 10));
    }

    @Test
    void argumentsAreNeverReinterpreted() {
        // Substituted text containing {} or ${...} is output literally, never re-scanned.
        assertEquals("v={} w=2", fmt("v={} w={}", "{}", 2));
        assertEquals("user=${jndi:ldap://evil/x}", fmt("user={}", "${jndi:ldap://evil/x}"));
        assertEquals("${jndi:ldap://evil/x}", fmt("${jndi:ldap://evil/x}"));
    }

    @Test
    void hostileToStringIsContained() {
        Object bad = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("boom");
            }
        };
        assertTrue(fmt("x={}", bad).startsWith("x=[toString() failed: "));
    }

    @Test
    void outputIsBounded() {
        String big = "y".repeat(100_000);
        String out = MessageFormatter.format("{}{}{}", new Object[] {big, big, big}, 1000);
        assertTrue(out.length() < 1100, "length=" + out.length());
    }

    @Test
    void indexedPlaceholdersHaveNoSubFormats() {
        assertEquals("a b a", MessageFormatter.formatIndexed("{0} {1} {0}", new Object[] {"a", "b"}, 100));
        assertEquals("{0,choice,0#x|1#{1}} {5}",
                MessageFormatter.formatIndexed("{0,choice,0#x|1#{1}} {5}", new Object[] {"a"}, 100));
        // no MessageFormat quoting rules either
        assertEquals("'a' is a", MessageFormatter.formatIndexed("'{0}' is {0}", new Object[] {"a"}, 100));
    }

    @Test
    void patternAcceptsOnlyKnownTokens() {
        List<PatternParser.Token> t = PatternParser.parse("%d %level [%thread] %logger{20} %% %msg%labels{reqId}%n%ex{oneline}");
        assertEquals(12, t.size(), t.toString());
        assertThrows(IllegalArgumentException.class, () -> PatternParser.parse("%d %X{user}"));
        assertThrows(IllegalArgumentException.class, () -> PatternParser.parse("${jndi:x} %msg %"));
        assertThrows(IllegalArgumentException.class, () -> PatternParser.parse("%logger{abc}"));
        assertThrows(IllegalArgumentException.class, () -> PatternParser.parse("%ex{full}"));
        assertThrows(IllegalArgumentException.class, () -> PatternParser.parse("%msg{x}"));
        assertThrows(IllegalArgumentException.class, () -> PatternParser.parse("bad\u001B%msg"));
        assertThrows(IllegalArgumentException.class, () -> PatternParser.parse("%labels{a.b}"));
    }

    @Test
    void loggerAbbreviation() {
        assertEquals("o.t.h.Server", TextLayout.abbreviate("org.tamacat.httpd.Server", 12));
        assertEquals("o.t.httpd.Server", TextLayout.abbreviate("org.tamacat.httpd.Server", 16));
        assertEquals("org.tamacat.httpd.Server", TextLayout.abbreviate("org.tamacat.httpd.Server", 100));
        assertEquals("o.t.h.VeryLongClassName", TextLayout.abbreviate("org.tamacat.httpd.VeryLongClassName", 5));
    }
}
