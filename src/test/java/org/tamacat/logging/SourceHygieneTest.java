package org.tamacat.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.tamacat.logging.internal.Chars;

/**
 * Guards against "Trojan Source" style attacks and invisible characters in the repository:
 * Java sources must be pure ASCII (use \\uXXXX escapes), and no text file may contain
 * control, bidirectional or other unsafe characters (see {@link Chars#isUnsafe(char)}).
 */
class SourceHygieneTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    private static List<Path> files(String dir, String suffix) throws IOException {
        Path d = ROOT.resolve(dir);
        if (!Files.isDirectory(d)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(d)) {
            return s.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(suffix)).toList();
        }
    }

    @Test
    void javaSourcesAreAscii() throws IOException {
        List<String> bad = new ArrayList<>();
        for (Path p : files("src", ".java")) {
            String s = Files.readString(p, StandardCharsets.UTF_8);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c > 0x7E || (c < 0x20 && c != '\n' && c != '\r' && c != '\t')) {
                    bad.add(ROOT.relativize(p) + " offset " + i);
                    break;
                }
            }
        }
        assertEquals(List.of(), bad);
    }

    @Test
    void textFilesHaveNoUnsafeCharacters() throws IOException {
        List<Path> all = new ArrayList<>();
        all.addAll(files("src", ""));
        all.addAll(files("docs", ".md"));
        all.addAll(files(".github", ".yml"));
        try (Stream<Path> s = Files.list(ROOT)) {
            s.filter(p -> p.getFileName().toString().matches(".*\\.(md|xml|properties)|LICENSE|mvnw.*"))
                    .forEach(all::add);
        }
        List<String> bad = new ArrayList<>();
        for (Path p : all) {
            String s = Files.readString(p, StandardCharsets.UTF_8);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                boolean unsafe = Chars.isUnsafe(c) && c != '\n' && c != '\r' && c != '\t';
                if (unsafe || (Character.isSurrogate(c) && !isPaired(s, i))) {
                    bad.add(ROOT.relativize(p) + " offset " + i + " U+" + Integer.toHexString(c));
                    break;
                }
            }
        }
        assertEquals(List.of(), bad);
    }

    private static boolean isPaired(String s, int i) {
        char c = s.charAt(i);
        return Character.isHighSurrogate(c) ? i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))
                : i > 0 && Character.isHighSurrogate(s.charAt(i - 1));
    }
}
