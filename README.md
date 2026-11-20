# tamacat-logging

English | [日本語](README-ja.md)

A security-first, minimal logging implementation for Java 25.
Designed for web servers running in containers, it writes to **standard output only**.

- SLF4J 2.x provider and `java.lang.System.Logger` backend
- Plain text (restricted pattern) and ECS-compatible JSON Lines
- Access log API (Common / Combined / ECS JSON)
- A single jar with **zero** runtime dependencies (`slf4j-api` is optional; only applications that use SLF4J need it)
- **No** lookups, expression evaluation, XML configuration, JNDI, network/file output, serialization or plugin mechanism
- Line breaks, control characters, ANSI escapes and bidirectional controls are always escaped (log injection protection)

See [docs/DESIGN.md](docs/DESIGN.md) for the design.

## Usage

Published to the tamacat Maven repository (the same one as tamacat-httpd); 1.0.0 is not released yet. It is not published to Maven Central.

```xml
<repositories>
  <repository>
    <id>tamacat.org</id>
    <url>https://raw.github.com/tamacat/tamacat.github.io/mvn-repo/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>org.tamacat</groupId>
    <artifactId>tamacat-logging</artifactId>
    <version>1.0.0</version>
  </dependency>
</dependencies>
```

This single dependency is all you need, whether you use SLF4J or System.Logger.

### With SLF4J

If the application has `slf4j-api` (2.x), tamacat-logging is registered as its provider automatically.

```java
Logger log = LoggerFactory.getLogger(MyServer.class);
MDC.put("reqId", requestId);
log.info("request {} completed", uri);
log.atWarn().addKeyValue("status", 503).log("upstream unavailable");
MDC.clear(); // always clear at the end of a request
```

### With System.Logger (no dependencies)

`slf4j-api` is not needed.

```java
System.Logger log = System.getLogger("org.example.App");
log.log(System.Logger.Level.INFO, "user {0} logged in", user);
```

`{0}` is a plain positional substitution (`MessageFormat` sub-formats are not interpreted).

### Access log

```java
AccessLogger.get().log(AccessLogEntry.builder()
        .remoteAddress(addr).remoteUser(user)
        .method("GET").requestUri(uri).protocol("HTTP/1.1")
        .status(200).bytesSent(length)
        .referer(referer).userAgent(userAgent)
        .elapsed(duration).requestId(requestId)
        .build());
```

## Configuration

`tamacat-logging.properties` on the classpath, or the file named by the system property `tamacat.logging.config`.
Environment variables, then system properties `tamacat.logging.<key>`, override it in that order. `${...}` in values is never expanded.

(`.properties` files do not support trailing comments; put comments on their own lines.)

```properties
# text | json
format=json
level.root=INFO
level.org.tamacat.httpd=DEBUG

# text format only. Tokens: %d %level %thread %logger{n} %msg %labels %labels{key} %ex %ex{oneline} %n %%
pattern=%d %level [%thread] %logger{36} - %msg%labels%n%ex

ecs.service.name=myapp
ecs.service.version=1.0.0
ecs.service.environment=production

accesslog.enabled=true
# common | combined | json (default: follows format)
accesslog.format=json

# Values are replaced with *** (case-insensitive exact match). Setting this replaces the default list.
mask.keys=password,token,authorization,cookie
json.asciiOnly=false

limit.message.maxLength=8192
limit.label.maxLength=1024
limit.label.maxKeys=32
limit.stack.maxFrames=128
limit.cause.maxDepth=16
limit.access.uri.maxLength=2048
limit.access.userAgent.maxLength=512
limit.access.referer.maxLength=2048
```

| Environment variable | Key |
|---|---|
| `TAMACAT_LOG_FORMAT` | `format` |
| `TAMACAT_LOG_LEVEL` | `level.root` |
| `TAMACAT_LOG_ACCESS_FORMAT` | `accesslog.format` |
| `TAMACAT_LOG_SERVICE_NAME` | `ecs.service.name` |
| `TAMACAT_LOG_SERVICE_ENVIRONMENT` | `ecs.service.environment` |

Unknown keys and invalid values are reported to stderr, and the default is used for that entry.

## Output examples

text:
```
2026-11-13T13:52:01.123Z INFO  [http-1] o.t.httpd.Server - request /index.html completed {reqId="abc123"}
```

json:
```json
{"@timestamp":"2026-11-13T13:52:01.123Z","log.level":"INFO","message":"request /index.html completed","ecs.version":"8.11","event.dataset":"app","log.logger":"org.tamacat.httpd.Server","process.thread.name":"http-1","service.name":"myapp","labels":{"reqId":"abc123"}}
```

## Build

The Maven Wrapper (Maven 3.10.1, SHA-256 verified) is included.

```bash
./mvnw install
```

```bash
./mvnw -Psbom package
```

`-Psbom` generates a CycloneDX SBOM (`target/bom.json`).

## Release procedure

1. Set the POM version to the release version (e.g. `1.0.0`), commit and push to `main`
2. Pushing the `v1.0.0` tag runs [release.yml](.github/workflows/release.yml)
   - Only artifacts that pass the build and tests (0 failures, 0 skips) are published
     to the `mvn-repo` branch of `tamacat/tamacat.github.io` and to GitHub Releases
   - Publishing to `mvn-repo` runs after approval in the `release` environment
3. Set the POM back to the next development version (e.g. `1.0.1-SNAPSHOT`)

## License

Apache License 2.0
