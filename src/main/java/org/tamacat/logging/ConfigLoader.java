package org.tamacat.logging;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.tamacat.logging.access.AccessFormat;
import org.tamacat.logging.internal.StatusReporter;

/**
 * Loads configuration once at startup. Sources, later ones override earlier ones:
 * <ol>
 * <li>built-in defaults</li>
 * <li>{@code tamacat-logging.properties} on the classpath, or the file named by the system
 *     property {@code tamacat.logging.config}</li>
 * <li>a fixed set of environment variables (see {@link #ENV})</li>
 * <li>system properties {@code tamacat.logging.<key>}</li>
 * </ol>
 * No value is ever expanded ({@code ${...}} is taken literally). Unknown keys and invalid values
 * are reported to stderr and ignored; loading never throws.
 */
final class ConfigLoader {

    static final String RESOURCE = "tamacat-logging.properties";
    static final String CONFIG_PROPERTY = "tamacat.logging.config";
    static final String SYS_PREFIX = "tamacat.logging.";
    static final int MAX_FILE_SIZE = 1 << 20;

    /** Fixed environment variable mapping. No other environment variables are read. */
    static final Map<String, String> ENV = Map.of(
            "TAMACAT_LOG_FORMAT", "format",
            "TAMACAT_LOG_LEVEL", "level.root",
            "TAMACAT_LOG_ACCESS_FORMAT", "accesslog.format",
            "TAMACAT_LOG_SERVICE_NAME", "ecs.service.name",
            "TAMACAT_LOG_SERVICE_ENVIRONMENT", "ecs.service.environment");

    private ConfigLoader() {
    }

    static Configuration load() {
        try {
            Properties sys = System.getProperties();
            return load(readFile(sys.getProperty(CONFIG_PROPERTY)), System.getenv(), sys);
        } catch (RuntimeException e) {
            StatusReporter.report("failed to load configuration; using defaults", e);
            return Configuration.defaults();
        }
    }

    static Configuration load(Map<String, String> file, Map<String, String> env, Properties sys) {
        Map<String, String> merged = new LinkedHashMap<>(file);
        ENV.forEach((var, key) -> {
            String v = env.get(var);
            if (v != null) {
                merged.put(key, v);
            }
        });
        for (String name : sys.stringPropertyNames()) {
            if (name.startsWith(SYS_PREFIX) && !name.equals(CONFIG_PROPERTY)) {
                merged.put(name.substring(SYS_PREFIX.length()), sys.getProperty(name));
            }
        }
        return apply(merged);
    }

    static Configuration apply(Map<String, String> entries) {
        Configuration.Builder b = Configuration.builder();
        for (Map.Entry<String, String> en : entries.entrySet()) {
            String key = en.getKey().trim();
            String value = en.getValue() == null ? "" : en.getValue().trim();
            try {
                if (!applyOne(b, key, value)) {
                    StatusReporter.report("unknown configuration key ignored: " + key, null);
                }
            } catch (IllegalArgumentException e) {
                StatusReporter.report("invalid configuration value ignored for key: " + key, e);
            }
        }
        return b.build();
    }

    private static boolean applyOne(Configuration.Builder b, String key, String v) {
        if (key.startsWith("level.")) {
            String name = key.substring("level.".length());
            if (name.equals("root")) {
                b.rootLevel(Level.parse(v));
            } else {
                b.level(name, Level.parse(v));
            }
            return true;
        }
        switch (key) {
            case "format" -> b.format(parseFormat(v));
            case "pattern" -> b.pattern(v);
            case "ecs.service.name" -> b.serviceName(v);
            case "ecs.service.version" -> b.serviceVersion(v);
            case "ecs.service.environment" -> b.serviceEnvironment(v);
            case "accesslog.enabled" -> b.accessLogEnabled(parseBoolean(v));
            case "accesslog.format" -> b.accessFormat(AccessFormat.parse(v));
            case "mask.keys" -> b.maskKeys(parseList(v));
            case "json.asciiOnly" -> b.jsonAsciiOnly(parseBoolean(v));
            case "limit.message.maxLength" -> b.messageMaxLength(parseInt(v));
            case "limit.label.maxLength" -> b.labelMaxLength(parseInt(v));
            case "limit.label.maxKeys" -> b.labelMaxKeys(parseInt(v));
            case "limit.stack.maxFrames" -> b.stackMaxFrames(parseInt(v));
            case "limit.cause.maxDepth" -> b.causeMaxDepth(parseInt(v));
            case "limit.access.uri.maxLength" -> b.accessUriMaxLength(parseInt(v));
            case "limit.access.userAgent.maxLength" -> b.accessUserAgentMaxLength(parseInt(v));
            case "limit.access.referer.maxLength" -> b.accessRefererMaxLength(parseInt(v));
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Configuration.Format parseFormat(String v) {
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "text" -> Configuration.Format.TEXT;
            case "json" -> Configuration.Format.JSON;
            default -> throw new IllegalArgumentException("format must be text or json");
        };
    }

    private static boolean parseBoolean(String v) {
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("must be true or false");
        };
    }

    private static int parseInt(String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not an integer");
        }
    }

    private static Set<String> parseList(String v) {
        Set<String> set = new LinkedHashSet<>();
        Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(set::add);
        return set;
    }

    /** Reads the explicit file if given, otherwise the classpath resource. Size-limited, UTF-8. */
    static Map<String, String> readFile(String explicitPath) {
        try {
            byte[] bytes;
            if (explicitPath != null && !explicitPath.isBlank()) {
                Path p = Path.of(explicitPath);
                if (Files.size(p) > MAX_FILE_SIZE) {
                    StatusReporter.report("configuration file too large; ignored", null);
                    return Map.of();
                }
                bytes = Files.readAllBytes(p);
            } else {
                bytes = readResource();
                if (bytes == null) {
                    return Map.of();
                }
            }
            return parse(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            StatusReporter.report("failed to read configuration file; using defaults", e);
            return Map.of();
        }
    }

    static Map<String, String> parse(String text) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(text));
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : p.stringPropertyNames()) {
            map.put(name, p.getProperty(name));
        }
        return map;
    }

    private static byte[] readResource() throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return null;
            }
            byte[] bytes = in.readNBytes(MAX_FILE_SIZE + 1);
            if (bytes.length > MAX_FILE_SIZE) {
                StatusReporter.report("configuration resource too large; ignored", null);
                return null;
            }
            return bytes;
        }
    }
}
