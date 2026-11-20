package org.tamacat.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.tamacat.logging.access.AccessFormat;
import org.tamacat.logging.internal.StatusReporter;

class ConfigLoaderTest {

    @Test
    void parsesAllKeys() throws Exception {
        Map<String, String> file = ConfigLoader.parse("""
                format=json
                level.root=WARN
                level.org.tamacat.httpd=DEBUG
                pattern=%level %msg%n
                ecs.service.name=myapp
                ecs.service.version=1.0
                ecs.service.environment=prod
                accesslog.enabled=false
                accesslog.format=common
                mask.keys=Password, X-Api-Key
                json.asciiOnly=true
                limit.message.maxLength=100
                limit.label.maxLength=50
                limit.label.maxKeys=10
                limit.stack.maxFrames=20
                limit.cause.maxDepth=3
                limit.access.uri.maxLength=300
                limit.access.userAgent.maxLength=200
                limit.access.referer.maxLength=100
                """);
        Configuration c = ConfigLoader.load(file, Map.of(), new Properties());
        assertEquals(Configuration.Format.JSON, c.format());
        assertEquals(Level.WARN, c.rootLevel());
        assertEquals(Map.of("org.tamacat.httpd", Level.DEBUG), c.levels());
        assertEquals("%level %msg%n", c.pattern());
        assertEquals("myapp", c.serviceName());
        assertEquals("1.0", c.serviceVersion());
        assertEquals("prod", c.serviceEnvironment());
        assertEquals(false, c.accessLogEnabled());
        assertEquals(AccessFormat.COMMON, c.accessFormat());
        assertEquals(Set.of("password", "x-api-key"), c.maskKeys());
        assertTrue(c.jsonAsciiOnly());
        assertEquals(100, c.messageMaxLength());
        assertEquals(50, c.labelMaxLength());
        assertEquals(10, c.labelMaxKeys());
        assertEquals(20, c.stackMaxFrames());
        assertEquals(3, c.causeMaxDepth());
        assertEquals(300, c.accessUriMaxLength());
        assertEquals(200, c.accessUserAgentMaxLength());
        assertEquals(100, c.accessRefererMaxLength());
    }

    @Test
    void precedenceFileThenEnvThenSystemProperties() {
        Properties sys = new Properties();
        sys.setProperty("tamacat.logging.level.root", "ERROR");
        sys.setProperty("tamacat.logging.config", "/ignored-as-key");
        Configuration c = ConfigLoader.load(
                Map.of("level.root", "DEBUG", "format", "text", "ecs.service.name", "file"),
                Map.of("TAMACAT_LOG_LEVEL", "WARN", "TAMACAT_LOG_FORMAT", "json",
                        "TAMACAT_LOG_SERVICE_NAME", "env", "UNRELATED", "x"),
                sys);
        assertEquals(Level.ERROR, c.rootLevel());
        assertEquals(Configuration.Format.JSON, c.format());
        assertEquals("env", c.serviceName());
        assertEquals(AccessFormat.JSON, c.accessFormat());
    }

    @Test
    void invalidAndUnknownEntriesFallBackToDefaults() {
        long before = StatusReporter.totalReports();
        Configuration c = ConfigLoader.load(Map.of(
                "level.root", "VERBOSE",
                "pattern", "%d %X{user} ${jndi:ldap://x}",
                "limit.message.maxLength", "-1",
                "ecs.service.name", "bad\nname",
                "appender.file.type", "file",
                "level.bad name", "INFO"), Map.of(), new Properties());
        Configuration d = Configuration.defaults();
        assertEquals(d.rootLevel(), c.rootLevel());
        assertEquals(d.pattern(), c.pattern());
        assertEquals(d.messageMaxLength(), c.messageMaxLength());
        assertNull(c.serviceName());
        assertTrue(c.levels().isEmpty());
        assertEquals(6, StatusReporter.totalReports() - before);
    }

    @Test
    void valuesAreNeverExpanded() {
        Configuration c = ConfigLoader.load(Map.of("ecs.service.name", "${env:HOME}"), Map.of(), new Properties());
        assertEquals("${env:HOME}", c.serviceName());
    }
}
