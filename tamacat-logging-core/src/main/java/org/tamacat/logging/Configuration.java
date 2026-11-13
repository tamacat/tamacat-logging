package org.tamacat.logging;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.tamacat.logging.access.AccessFormat;
import org.tamacat.logging.internal.Chars;
import org.tamacat.logging.internal.PatternParser;

/**
 * Immutable configuration. Build with {@link #builder()}; every setter validates its input and
 * throws {@link IllegalArgumentException} on invalid values.
 */
public final class Configuration {

    /** Output format of application logs. */
    public enum Format { TEXT, JSON }

    public static final String DEFAULT_PATTERN = "%d %level [%thread] %logger{36} - %msg%labels%n%ex";

    public static final Set<String> DEFAULT_MASK_KEYS = Set.of(
            "password", "passwd", "secret", "token", "access_token", "refresh_token",
            "authorization", "cookie", "set-cookie", "api_key", "apikey");

    static final int MAX_LIMIT = 1 << 20;
    static final int MAX_STATIC_VALUE = 256;

    private final Format format;
    private final Level rootLevel;
    private final Map<String, Level> levels;
    private final String pattern;
    private final String serviceName;
    private final String serviceVersion;
    private final String serviceEnvironment;
    private final boolean accessLogEnabled;
    private final AccessFormat accessFormat;
    private final Set<String> maskKeys;
    private final boolean jsonAsciiOnly;
    private final int messageMaxLength;
    private final int labelMaxLength;
    private final int labelMaxKeys;
    private final int stackMaxFrames;
    private final int causeMaxDepth;
    private final int accessUriMaxLength;
    private final int accessUserAgentMaxLength;
    private final int accessRefererMaxLength;

    private Configuration(Builder b) {
        format = b.format;
        rootLevel = b.rootLevel;
        levels = Map.copyOf(b.levels);
        pattern = b.pattern;
        serviceName = b.serviceName;
        serviceVersion = b.serviceVersion;
        serviceEnvironment = b.serviceEnvironment;
        accessLogEnabled = b.accessLogEnabled;
        accessFormat = b.accessFormat != null ? b.accessFormat
                : (b.format == Format.JSON ? AccessFormat.JSON : AccessFormat.COMBINED);
        maskKeys = Set.copyOf(b.maskKeys);
        jsonAsciiOnly = b.jsonAsciiOnly;
        messageMaxLength = b.messageMaxLength;
        labelMaxLength = b.labelMaxLength;
        labelMaxKeys = b.labelMaxKeys;
        stackMaxFrames = b.stackMaxFrames;
        causeMaxDepth = b.causeMaxDepth;
        accessUriMaxLength = b.accessUriMaxLength;
        accessUserAgentMaxLength = b.accessUserAgentMaxLength;
        accessRefererMaxLength = b.accessRefererMaxLength;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Configuration defaults() {
        return new Builder().build();
    }

    /**
     * Builds a configuration from properties using the same keys as {@code tamacat-logging.properties}.
     * Invalid entries are reported to stderr and ignored.
     */
    public static Configuration fromProperties(java.util.Properties props) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : props.stringPropertyNames()) {
            map.put(name, props.getProperty(name));
        }
        return ConfigLoader.apply(map);
    }

    public Format format() {
        return format;
    }

    public Level rootLevel() {
        return rootLevel;
    }

    /** Per-logger-prefix levels (excluding root). */
    public Map<String, Level> levels() {
        return levels;
    }

    public String pattern() {
        return pattern;
    }

    public String serviceName() {
        return serviceName;
    }

    public String serviceVersion() {
        return serviceVersion;
    }

    public String serviceEnvironment() {
        return serviceEnvironment;
    }

    public boolean accessLogEnabled() {
        return accessLogEnabled;
    }

    /** Access log format; defaults to JSON when {@link #format()} is JSON, otherwise COMBINED. */
    public AccessFormat accessFormat() {
        return accessFormat;
    }

    /** Lower-case label keys whose values are masked. */
    public Set<String> maskKeys() {
        return maskKeys;
    }

    public boolean jsonAsciiOnly() {
        return jsonAsciiOnly;
    }

    public int messageMaxLength() {
        return messageMaxLength;
    }

    public int labelMaxLength() {
        return labelMaxLength;
    }

    public int labelMaxKeys() {
        return labelMaxKeys;
    }

    public int stackMaxFrames() {
        return stackMaxFrames;
    }

    public int causeMaxDepth() {
        return causeMaxDepth;
    }

    public int accessUriMaxLength() {
        return accessUriMaxLength;
    }

    public int accessUserAgentMaxLength() {
        return accessUserAgentMaxLength;
    }

    public int accessRefererMaxLength() {
        return accessRefererMaxLength;
    }

    /** Builder with validating setters. */
    public static final class Builder {
        private Format format = Format.TEXT;
        private Level rootLevel = Level.INFO;
        private final Map<String, Level> levels = new LinkedHashMap<>();
        private String pattern = DEFAULT_PATTERN;
        private String serviceName;
        private String serviceVersion;
        private String serviceEnvironment;
        private boolean accessLogEnabled = true;
        private AccessFormat accessFormat;
        private Set<String> maskKeys = DEFAULT_MASK_KEYS;
        private boolean jsonAsciiOnly;
        private int messageMaxLength = 8192;
        private int labelMaxLength = 1024;
        private int labelMaxKeys = 32;
        private int stackMaxFrames = 128;
        private int causeMaxDepth = 16;
        private int accessUriMaxLength = 2048;
        private int accessUserAgentMaxLength = 512;
        private int accessRefererMaxLength = 2048;

        private Builder() {
        }

        public Builder format(Format v) {
            format = require(v, "format");
            return this;
        }

        public Builder rootLevel(Level v) {
            rootLevel = require(v, "rootLevel");
            return this;
        }

        /** Sets the level for a logger name prefix ({@code a.b} also covers {@code a.b.C}). */
        public Builder level(String loggerPrefix, Level v) {
            if (!isValidLoggerName(loggerPrefix)) {
                throw new IllegalArgumentException("invalid logger name");
            }
            levels.put(loggerPrefix, require(v, "level"));
            return this;
        }

        public Builder pattern(String v) {
            PatternParser.parse(v);
            pattern = v;
            return this;
        }

        public Builder serviceName(String v) {
            serviceName = staticValue(v);
            return this;
        }

        public Builder serviceVersion(String v) {
            serviceVersion = staticValue(v);
            return this;
        }

        public Builder serviceEnvironment(String v) {
            serviceEnvironment = staticValue(v);
            return this;
        }

        public Builder accessLogEnabled(boolean v) {
            accessLogEnabled = v;
            return this;
        }

        /** {@code null} = follow {@link #format(Format)}. */
        public Builder accessFormat(AccessFormat v) {
            accessFormat = v;
            return this;
        }

        /** Replaces the masked key set (case-insensitive exact match). */
        public Builder maskKeys(Set<String> keys) {
            require(keys, "maskKeys");
            Set<String> lower = new java.util.HashSet<>();
            for (String k : keys) {
                if (k == null || k.isBlank() || k.length() > 64) {
                    throw new IllegalArgumentException("invalid mask key");
                }
                lower.add(k.trim().toLowerCase(Locale.ROOT));
            }
            maskKeys = lower;
            return this;
        }

        public Builder jsonAsciiOnly(boolean v) {
            jsonAsciiOnly = v;
            return this;
        }

        public Builder messageMaxLength(int v) {
            messageMaxLength = limit(v);
            return this;
        }

        public Builder labelMaxLength(int v) {
            labelMaxLength = limit(v);
            return this;
        }

        public Builder labelMaxKeys(int v) {
            labelMaxKeys = Math.min(limit(v), LogContext.MAX_ENTRIES);
            return this;
        }

        public Builder stackMaxFrames(int v) {
            stackMaxFrames = limit(v);
            return this;
        }

        public Builder causeMaxDepth(int v) {
            causeMaxDepth = Math.min(limit(v), 1024);
            return this;
        }

        public Builder accessUriMaxLength(int v) {
            accessUriMaxLength = limit(v);
            return this;
        }

        public Builder accessUserAgentMaxLength(int v) {
            accessUserAgentMaxLength = limit(v);
            return this;
        }

        public Builder accessRefererMaxLength(int v) {
            accessRefererMaxLength = limit(v);
            return this;
        }

        public Configuration build() {
            return new Configuration(this);
        }

        private static <T> T require(T v, String name) {
            if (v == null) {
                throw new IllegalArgumentException(name + " is null");
            }
            return v;
        }

        private static int limit(int v) {
            if (v < 1 || v > MAX_LIMIT) {
                throw new IllegalArgumentException("limit out of range (1.." + MAX_LIMIT + ")");
            }
            return v;
        }

        private static String staticValue(String v) {
            if (v == null) {
                return null;
            }
            if (v.length() > MAX_STATIC_VALUE || Chars.containsUnsafe(v)) {
                throw new IllegalArgumentException("invalid value (too long or contains control characters)");
            }
            return v.isEmpty() ? null : v;
        }
    }

    /** {@code [A-Za-z0-9_$.-]{1,256}} */
    static boolean isValidLoggerName(String n) {
        if (n == null || n.isEmpty() || n.length() > 256) {
            return false;
        }
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '$' || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
