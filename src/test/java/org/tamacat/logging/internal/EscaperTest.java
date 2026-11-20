package org.tamacat.logging.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.tamacat.logging.JsonTestParser;

class EscaperTest {

    private static void assertSafe(String out) {
        assertFalse(Chars.containsUnsafe(out), () -> "unsafe output: " + out);
    }

    @Test
    void textEscapesEveryBmpCodePoint() {
        for (int c = 0; c <= 0xFFFF; c++) {
            String out = TextEscaper.escape(String.valueOf((char) c));
            assertSafe(out);
        }
    }

    @Test
    void textEscapesLineBreaksAndAnsi() {
        assertEquals("a\\r\\nb", TextEscaper.escape("a\r\nb"));
        assertEquals("\\u001B[31mred", TextEscaper.escape("\u001B[31mred"));
        assertEquals("x\\u202Ey", TextEscaper.escape("x\u202Ey"));
        assertEquals("back\\\\slash", TextEscaper.escape("back\\slash"));
        assertEquals("\\uFFFD", TextEscaper.escape("\uD800"));
        assertEquals("\uD83D\uDE00", TextEscaper.escape("\uD83D\uDE00"));
    }

    @Test
    void jsonEscapesEveryBmpCodePointAndRoundTrips() {
        for (int c = 0; c <= 0xFFFF; c++) {
            String in = String.valueOf((char) c);
            for (boolean ascii : new boolean[] {false, true}) {
                StringBuilder sb = new StringBuilder();
                JsonEscaper.quote(in, sb, ascii);
                String out = sb.toString();
                assertSafe(out);
                Object parsed = JsonTestParser.parse(out);
                if (!Character.isSurrogate((char) c)) {
                    assertEquals(in, parsed);
                } else {
                    assertEquals("\uFFFD", parsed);
                }
                if (ascii) {
                    assertFalse(out.chars().anyMatch(ch -> ch > 0x7E));
                }
            }
        }
    }

    @Test
    void randomStringsAlwaysProduceSafeOutput() {
        Random r = new Random(42);
        for (int n = 0; n < 20_000; n++) {
            char[] cs = new char[r.nextInt(40)];
            for (int k = 0; k < cs.length; k++) {
                cs[k] = r.nextInt(4) == 0 ? (char) r.nextInt(0x100) : (char) r.nextInt(0x10000);
            }
            String in = new String(cs);
            assertSafe(TextEscaper.escape(in));
            StringBuilder sb = new StringBuilder();
            JsonEscaper.quote(in, sb, false);
            assertSafe(sb.toString());
            JsonTestParser.parse(sb.toString());
        }
    }

    @Test
    void accessEscapingMatchesApacheStyle() {
        StringBuilder sb = new StringBuilder();
        AccessLayout.escape("a\"b\\c\r\n\u0001 \u3042", sb, false);
        assertEquals("a\\\"b\\\\c\\r\\n\\x01 \\xe3\\x81\\x82", sb.toString());
        sb.setLength(0);
        AccessLayout.escape("evil user", sb, true);
        assertEquals("evil\\x20user", sb.toString());
    }

    @Test
    void truncateDoesNotSplitSurrogatePairs() {
        String s = "ab\uD83D\uDE00cd";
        assertEquals("ab" + Safe.TRUNCATED, Safe.truncate(s, 3));
        assertEquals(s, Safe.truncate(s, 100));
    }
}
