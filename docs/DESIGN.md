# tamacat-logging Requirements and Design (v1.0)

English | [日本語](DESIGN-ja.md)

> Status: **final** (2026-11-13). The implementation follows this document.

---

## 1. Purpose

A **security-first, minimal-dependency, minimal-feature** logging implementation for Java 25.
Its main use is application and access logging for **web servers running in containers**.
Output goes to **standard output only**; collection, storage and rotation are left to the container platform (Docker / Kubernetes / log collectors).

As an alternative to Logback / Log4j2, the primary design goal is to **have no attack surface by construction**.

### 1.1 Known vulnerability classes and how they are handled

| Case | Feature that caused it | Handling in this implementation |
|---|---|---|
| Log4Shell (CVE-2021-44228) | `${...}` lookups / JNDI in log messages | **No lookups or variable expansion** |
| Log4j CVE-2021-45046/45105 | Recursive evaluation of context lookups | Same as above |
| Logback CVE-2023-6378/6481 | Deserialization in SocketReceiver | **No network I/O, no Java serialization** |
| Logback CVE-2021-42550 | XML configuration using JNDI | **No XML configuration, no JNDI** |
| Logback CVE-2024-12798/12801 | Expression evaluation / SSRF in configuration | **No expression evaluation, no external references** |
| Many Jackson CVEs | Polymorphic typing in a general-purpose (de)serializer | **Own write-only JSON encoder (no parsing)** |
| File appender issues | Following symlinks, path traversal, permissions | **No file output at all** |
| Log injection (CWE-117) | Unescaped line breaks and control characters | **Every output value is escaped per format** |

---

## 2. Requirements

### 2.1 Functional requirements

| ID | Requirement |
|---|---|
| F-1 | Works as an SLF4J 2.x backend (`SLF4JServiceProvider`) |
| F-2 | Works as a `java.lang.System.Logger` backend (`System.LoggerFinder`) |
| F-3 | Provides a dedicated **access log** API and formats for web servers (Common / Combined / ECS JSON) (§4.3) |
| F-4 | Log levels `TRACE / DEBUG / INFO / WARN / ERROR / OFF`, configurable per logger-name hierarchy |
| F-5 | Output formats: **plain text** (restricted pattern) and **JSON Lines** (one JSON object per line, **ECS compatible**) |
| F-6 | Output destination: **standard output only** (no files, rotation or compression) |
| F-7 | MDC (SLF4J `MDC`) support |
| F-8 | Exception output (stack trace, cause chain, suppressed). Text format: multi-line (default) or single-line option |
| F-9 | SLF4J 2.x fluent API key-values (`addKeyValue`) are output as structured fields |
| F-10 | **Masking by key name**: values of the listed MDC / key-value keys are replaced with `***` (no regular expressions) |
| F-11 | Configuration: `tamacat-logging.properties` on the classpath / system properties / environment variables (§5) |

### 2.2 Non-functional requirements (security)

| ID | Requirement |
|---|---|
| S-1 | **Zero runtime dependencies**. `slf4j-api` is optional (only applications that use SLF4J have it) |
| S-2 | Logged data (messages, arguments, MDC, exception messages, ...) is **never evaluated**. SLF4J `{}` substitution happens once and the result is never re-scanned |
| S-3 | The text format **always escapes** CR/LF, control characters (C0/C1, DEL), ESC and bidirectional controls, preventing forged lines and terminal escape attacks |
| S-4 | JSON uses strict RFC 8259 escaping plus handling of U+2028/2029 and invalid surrogates. Keys are escaped too |
| S-5 | Input length, key count and stack depth are capped (truncated) to prevent DoS through logging |
| S-6 | **No** network I/O, file I/O (except reading configuration), JNDI, JMX, scripting, Java serialization or loading arbitrary classes from configuration |
| S-7 | Configuration is `java.util.Properties` only (no XML/YAML). Values are never expanded |
| S-8 | Exceptions during logging never propagate to the caller. Internal errors are reported to stderr with rate limiting |
| S-9 | `toString()` that throws or returns huge strings is handled safely (caught and replaced, length-capped) |
| S-10 | MDC / key-values cannot overwrite reserved top-level fields (`log.level` etc.) (§4.2) |

### 2.3 Non-functional requirements (other)

| ID | Requirement |
|---|---|
| N-1 | Java 25 baseline, JPMS module. No virtual-thread pinning (`ReentrantLock`) |
| N-2 | Small code base (target: 2,000 lines or fewer excluding SLF4J support, blank lines and comments) so that every line can be audited |
| N-3 | Reproducible builds, CycloneDX SBOM, Maven Enforcer bans added dependencies |
| N-4 | JUnit only for tests (test scope) |
| N-5 | **Synchronous output only**, flushed per event (minimizes loss on container stop or OOM kill) |

### 2.4 Out of scope (intentionally not implemented)

- Lookups / variable substitution / expression evaluation / scripting
- XML, YAML or JSON configuration; automatic configuration reload
- File output, rotation, compression, stderr as a destination
- Network appenders (socket, syslog, SMTP, HTTP, DB, ...) and their receivers
- JNDI, JMX, Java serialization, plugin mechanism
- Asynchronous output
- A `java.util.logging` (JUL) handler (use `jul-to-slf4j` if needed)
- Log4j API / Commons Logging bridges
- Marker-based filtering, filter chains
- Colored console output
- Masking by detecting patterns in message text (false positives, ReDoS risk)

---

## 3. Architecture

### 3.1 Artifact and module

There is **one jar, `org.tamacat:tamacat-logging`** (JPMS module `org.tamacat.logging`). Users declare a single dependency.

```java
module org.tamacat.logging {
    requires static org.slf4j;          // optional: may be absent at run time
    exports org.tamacat.logging;
    exports org.tamacat.logging.access;
    provides java.lang.System.LoggerFinder with ...jdk.TamacatLoggerFinder;
    provides org.slf4j.spi.SLF4JServiceProvider with ...slf4j.TamacatServiceProvider;
}
```

- `slf4j-api` is `<optional>true</optional>`. The SLF4J provider is loaded only when SLF4J itself looks it up through ServiceLoader,
  so without slf4j-api (System.Logger only) it is never loaded and there are no transitive dependencies
- Verified on module path and class path, each with and without slf4j-api
- The `internal` package is not exported (used only inside the module)
- License: Apache License 2.0
- Because a `System.LoggerFinder` is provided, System.Logger output from JDK internals (`java.net.http` etc.) is captured too

(It was first split into three artifacts, core / slf4j / parent, and merged before the 1.0.0 release so that users declare only one dependency.)

### 3.2 Processing flow

```
 SLF4J Logger ──┐
 System.Logger ─┴─▶ LoggerContext ─▶ Logger(level check) ─▶ LogEvent ──┐
                                                                       ├─▶ Layout ─▶ StdoutSink
 AccessLogger ─────────────────────────────────────────▶ AccessLogEntry ┘   (byte[])   (lock + write + flush)
```

- **LogEvent** (immutable `record`): `Instant timestamp, Level level, String loggerName, String threadName, String message, Map<String,String> labels, Throwable thrown`
- Messages are formatted only after the level check
- A **Layout** is a pure function from event to bytes. There is no API that bypasses escaping
- **StdoutSink** writes directly to `new FileOutputStream(FileDescriptor.out)`.
  It does not use `System.out`, so an application that redirects `System.out` into a logger with `System.setOut()` cannot cause infinite recursion
- The bytes of one event are built first and then written with a single `write` + `flush` under a lock (records never interleave)

### 3.3 Main classes

The public API is `org.tamacat.logging` and `org.tamacat.logging.access` only.

| Package | Class | Role |
|---|---|---|
| `org.tamacat.logging` (public) | `Level` (enum) | Level definitions |
| | `LoggerContext` | The engine (configuration, level checks, output). `configure()` / `setOutput()` |
| | `Configuration` | Immutable configuration with a validating builder, `fromProperties()` |
| | `LogContext` | Thread-local context (backing store of MDC, not inherited) |
| | `ConfigLoader` (non-public) | Reads properties / environment variables / system properties |
| `org.tamacat.logging.access` (public) | `AccessLogger` / `AccessLogEntry` / `AccessFormat` | Access log API |
| `org.tamacat.logging.internal` | `Layout` (sealed) | Only `TextLayout` and `EcsJsonLayout` |
| | `AccessLayout` | Fixed common / combined / json implementations |
| | `PatternParser` | Parses the restricted pattern (sealed tokens) |
| | `TextEscaper` / `JsonEscaper` / `Chars` | Escaping (most heavily tested) |
| | `LabelBuilder` | Label key validation, masking, count/length limits |
| | `MessageFormatter` | Plain `{}` / `{0}` substitution (no recursion, bounded) |
| | `ThrowableRenderer` | Stack trace rendering (cycle detection, limits) |
| | `StdoutSink` | Synchronous writes to standard output |
| | `StatusReporter` | Reports internal errors to stderr (rate limited) |
| | `Safe` | toString / truncation that never throws |
| `org.tamacat.logging.jdk` (non-public) | `TamacatLoggerFinder` / `TamacatSystemLogger` | `System.LoggerFinder` implementation |
| `org.tamacat.logging.slf4j` (non-public) | `TamacatServiceProvider` and others | SLF4J provider, `LoggingEventAware` logger, MDC adapter |

Extension points (Layout / Token) are sealed or final; there is no path for configuration to load arbitrary classes.

**System.Logger formatting**: `{0}`, `{1}`, ... are replaced by plain substitution. `java.text.MessageFormat` is not used
(its `{0,choice,...}` sub-formats re-evaluate substituted text).

---

## 4. Output formats

### 4.1 Plain text

Default pattern:
```
%d %level [%thread] %logger{36} - %msg%labels%n%ex
```
Example:
```
2026-11-13T13:52:01.123Z INFO  [http-1] o.t.httpd.Server - Request completed {reqId="abc123", status="200"}
```

`%labels` values are quoted with `"`, and `"` and `\` inside values are escaped (a value such as `a", role="admin` cannot forge fields).

**Restricted pattern** (only these fixed tokens; unknown tokens are configuration errors):

| Token | Content |
|---|---|
| `%d` | ISO-8601 UTC timestamp (format not configurable) |
| `%level` | Level (left-aligned, width 5) |
| `%thread` | Thread name |
| `%logger` / `%logger{n}` | Logger name / abbreviated to n characters |
| `%msg` | Message |
| `%labels` / `%labels{key}` | All MDC + key-values / a single key |
| `%ex` / `%ex{oneline}` | Exception (multi-line / single line) |
| `%n` `%%` | Line break / `%` |

- The pattern is parsed once, at configuration time, into a token list. Log data is never interpreted as a pattern
- Control characters are not allowed in the literal parts either (configuration error)

**Escaping rules (applied to every variable field)**:

| Input | Output |
|---|---|
| `\` | `\\` |
| CR / LF / TAB | `\r` / `\n` / `\t` |
| Other C0 (U+0000–001F), DEL, C1 (U+0080–009F) | `\u00XX` (includes ESC, so terminal escape attacks are impossible) |
| U+2028 / U+2029 | `\u2028` / `\u2029` |
| Bidirectional controls (U+202A–202E, U+2066–2069) | `\u202E` etc. (prevents Trojan-Source-style display forgery) |
| Invalid surrogates | `\uFFFD` |

**Exceptions**:
- Default (multi-line): **every stack trace line starts with a TAB**, and line breaks in exception messages are escaped.
  "A line that does not start with a TAB is a new record" is guaranteed, so forged lines cannot be produced
- `%ex{oneline}`: frames are joined on one line with ` | `. Example: `pattern=%d %level [%thread] %logger{36} - %msg%labels%ex{oneline}%n`
- Even if the pattern has no `%n`, every record ends with `\n`

### 4.2 JSON Lines (ECS compatible)

**Dotted flat keys** as in ecs-logging (Elastic's official specification), in a fixed field order.

```json
{"@timestamp":"2026-11-13T13:52:01.123Z","log.level":"ERROR","message":"Request failed","ecs.version":"8.11","event.dataset":"app","log.logger":"org.tamacat.httpd.Server","process.thread.name":"http-1","service.name":"myapp","labels":{"reqId":"abc123","password":"***"},"error.type":"java.io.IOException","error.message":"Broken pipe","error.stack_trace":"java.io.IOException: Broken pipe\n\tat ..."}
```

| ECS field | Content |
|---|---|
| `@timestamp` | ISO-8601 UTC, milliseconds |
| `log.level` | `TRACE` to `ERROR` |
| `message` | Formatted message |
| `ecs.version` | ECS version followed (fixed) |
| `event.dataset` | `app` (application log) / `access` (access log). Distinguishes them on the shared stdout |
| `log.logger` | Logger name |
| `process.thread.name` | Thread name |
| `service.name` / `service.version` / `service.environment` | Configured static strings (optional) |
| `labels` | MDC + key-values (**strings only**; a key-value wins over an MDC entry with the same key) |
| `error.type` / `error.message` / `error.stack_trace` | Only when there is an exception |

- MDC / key-values are confined to `labels` (expanding them at the top level would let attacker-controlled keys forge or duplicate `log.level` etc.)
- `labels` keys are restricted to `[A-Za-z0-9_\-]{1,64}`; other keys are dropped and counted internally
- No duplicate keys are produced. All values are strings (no type inference)
- Non-ASCII stays UTF-8 (optionally `\uXXXX`)

### 4.3 Access log

A dedicated API that web servers call when a request completes. Independent of SLF4J (works without slf4j-api).

```java
AccessLogger.get().log(new AccessLogEntry(
    Instant start, String remoteAddr, String remoteUser,
    String method, String requestUri, String protocol,
    int status, long bytesSent, String referer, String userAgent,
    Duration elapsed, String requestId));
```

**Only three fixed formats**:

| format | Output |
|---|---|
| `common` | Apache Common Log Format: `%h %l %u [%t] "%r" %>s %b` |
| `combined` | `common` + ` "Referer" "User-Agent"` + response time (µs) + request id |
| `json` | ECS: `@timestamp`, `event.dataset=access`, `source.address`, `user.name`, `http.request.method`, `url.original`, `http.version`, `http.response.status_code`, `http.response.body.bytes`, `http.request.referrer`, `user_agent.original`, `event.duration` (ns), `http.request.id` |

- Text formats escape like Apache httpd's `ap_escape_logitem`: `"` → `\"`, `\` → `\\`, control and non-ASCII bytes → `\xHH`
- The request line, User-Agent, Referer and user name are fully attacker-controlled, so there is no API that skips escaping
- Length limits (defaults: URI 2,048 / User-Agent 512 / Referer 2,048 / request id 128)
- The access log has no level; it is switched on and off with `accesslog.enabled`

### 4.4 Masking

- Values of MDC / key-value entries whose key is listed in `mask.keys` (case-insensitive exact match) are replaced with `***`
- Default: `password,passwd,secret,token,access_token,refresh_token,authorization,cookie,set-cookie,api_key,apikey`
- No regular expressions, partial matches or scanning of message text (avoids ReDoS, false positives and slowdowns)
- URI query strings in the access log are not masked (the server should strip them before logging if needed)

### 4.5 Limits (defaults, configurable)

| Item | Default |
|---|---|
| Maximum message length | 8,192 characters (longer messages are truncated + `...[truncated]`) |
| Maximum label value length | 1,024 characters |
| Maximum number of labels | 32 |
| Maximum stack trace frames | 128 (total including causes) |
| Maximum cause chain depth | 16 (with cycle detection) |

---

## 5. Configuration

### 5.1 Load order (later wins)

1. Built-in defaults (`INFO` and above, text format)
2. `tamacat-logging.properties` on the classpath (or the file named by the system property `tamacat.logging.config`)
3. Environment variables (for containers; fixed names only, §5.3)
4. System properties `tamacat.logging.*`

- Loaded once at startup (no reload). Reconfiguration through the programmatic API is possible
- Unknown keys and invalid values are reported to stderr and the default is used for that entry (the application keeps running)
- `${...}` in values is never expanded

### 5.2 Example (properties)

```properties
format=json
level.root=INFO
level.org.tamacat.httpd=DEBUG
level.org.apache=WARN

pattern=%d %level [%thread] %logger{36} - %msg%labels%n%ex

ecs.service.name=myapp
ecs.service.environment=production

accesslog.enabled=true
accesslog.format=json

mask.keys=password,token,authorization,cookie

limit.message.maxLength=8192
```

### 5.3 Environment variables

| Environment variable | Key |
|---|---|
| `TAMACAT_LOG_FORMAT` | `format` (`text` / `json`) |
| `TAMACAT_LOG_LEVEL` | `level.root` |
| `TAMACAT_LOG_ACCESS_FORMAT` | `accesslog.format` |
| `TAMACAT_LOG_SERVICE_NAME` | `ecs.service.name` |
| `TAMACAT_LOG_SERVICE_ENVIRONMENT` | `ecs.service.environment` |

Only this fixed mapping exists; there is no way to reference arbitrary environment variables.

---

## 6. Licensing policy

| Reference | License | Use |
|---|---|---|
| SLF4J API | MIT | **Used as a dependency** (no code copied) |
| `slf4j-simple` | MIT | Reference for a minimal provider. Keep the copyright notice if code is reused |
| Logback | EPL 1.0 / LGPL 2.1 | **No code reused** (design reference only) |
| Log4j2 / tinylog 2 | Apache 2.0 | Design reference only. Mention in NOTICE if code is reused |
| ecs-logging (specification) | Apache 2.0 | Field definitions only |

Policy: **written from scratch**. This project is licensed under the Apache License 2.0.

---

## 7. Supply chain and quality assurance

- Runtime dependencies: 0 (`slf4j-api` is optional)
- Maven Enforcer `bannedDependencies` fails the build if a compile/runtime dependency is added
- CycloneDX SBOM; OSV-Scanner / OWASP Dependency-Check, SpotBugs + FindSecBugs
- Test focus:
  - Property-based tests of escaping (all code points and random input: "no raw line breaks or control characters in the output", "JSON is always parseable", "one event = one line (JSON)")
  - Known attack strings such as `${jndi:...}` are output as plain text
  - Reserved fields cannot be forged (MDC key `log.level` etc.)
  - No interleaved lines with many threads or virtual threads
  - Repository hygiene: Java sources are ASCII-only and no file contains bidirectional or other invisible control characters
  - The JSON parser used by tests is test scope only

---

## 8. Decisions

| # | Topic | Decision |
|---|---|---|
| Q1 | Supported APIs | SLF4J 2.x provider + System.Logger |
| Q2 | Access log | Included (dedicated API, common / combined / ECS JSON) |
| Q3 | Text format | Restricted pattern (fixed tokens only) |
| Q4 | Asynchronous output | Synchronous only |
| Q5 | JSON format | ECS compatible (MDC / key-values under `labels`) |
| Q6 | Destination | **Standard output only** (no files, rotation or compression) |
| Q7 | Masking | Exact key-name match (no regular expressions) |
| Q8 | License / coordinates | Apache 2.0 / `org.tamacat:tamacat-logging` / module `org.tamacat.logging` |
| Q9 | Exceptions in text format | Multi-line (TAB-prefixed lines enforced) + `%ex{oneline}` |
| C1 | Overrides through environment variables | Five fixed variables only (§5.3) |
| C2 | Application and access logs mixed in text format | Access log stays in Common/Combined format. `format=json` is recommended in containers |
| C3 | Separate stderr for ERROR | No (standard output only; stderr is only for internal error reports) |
