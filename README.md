# tamacat-logging

Java 25 向けの、セキュリティ最優先・依存最小のロギング実装です。
コンテナ上の Web サーバーを想定し、**標準出力のみ**に出力します。

- SLF4J 2.x プロバイダ / `java.lang.System.Logger` バックエンド
- テキスト形式（限定パターン）と ECS 互換 JSON Lines
- アクセスログ API（Common / Combined / ECS JSON）
- ランタイム依存: core = **0**、slf4j モジュール = `slf4j-api` のみ
- Lookup・式評価・XML 設定・JNDI・ネットワーク/ファイル出力・シリアライズ・プラグイン機構を**持ちません**
- 改行・制御文字・ANSI エスケープ・双方向制御文字は必ずエスケープされます（ログインジェクション対策）

設計の詳細は [docs/DESIGN.md](docs/DESIGN.md) を参照してください。

## 使い方

### SLF4J で使う

```xml
<dependency>
  <groupId>org.tamacat</groupId>
  <artifactId>tamacat-logging-slf4j</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
Logger log = LoggerFactory.getLogger(MyServer.class);
MDC.put("reqId", requestId);
log.info("request {} completed", uri);
log.atWarn().addKeyValue("status", 503).log("upstream unavailable");
MDC.clear(); // リクエスト終了時に必ずクリア
```

### System.Logger だけで使う（依存ゼロ）

```xml
<dependency>
  <groupId>org.tamacat</groupId>
  <artifactId>tamacat-logging-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
System.Logger log = System.getLogger("org.example.App");
log.log(System.Logger.Level.INFO, "user {0} logged in", user);
```

`{0}` は単純な位置置換です（`MessageFormat` のサブフォーマットは解釈しません）。

### アクセスログ

```java
AccessLogger.get().log(AccessLogEntry.builder()
        .remoteAddress(addr).remoteUser(user)
        .method("GET").requestUri(uri).protocol("HTTP/1.1")
        .status(200).bytesSent(length)
        .referer(referer).userAgent(userAgent)
        .elapsed(duration).requestId(requestId)
        .build());
```

## 設定

classpath の `tamacat-logging.properties`（またはシステムプロパティ `tamacat.logging.config` で指定したファイル）。
環境変数 → システムプロパティ `tamacat.logging.<key>` の順に上書きされます。値の `${...}` 展開は行いません。

（`.properties` は行末コメントを書けないため、コメントは独立した行に書いてください）

```properties
# text | json
format=json
level.root=INFO
level.org.tamacat.httpd=DEBUG

# text 形式のみ。使えるトークン: %d %level %thread %logger{n} %msg %labels %labels{key} %ex %ex{oneline} %n %%
pattern=%d %level [%thread] %logger{36} - %msg%labels%n%ex

ecs.service.name=myapp
ecs.service.version=1.0.0
ecs.service.environment=production

accesslog.enabled=true
# common | combined | json（既定: format に連動）
accesslog.format=json

# 値を *** に置換（大文字小文字無視の完全一致）。指定すると既定リストを置き換える
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

| 環境変数 | 設定キー |
|---|---|
| `TAMACAT_LOG_FORMAT` | `format` |
| `TAMACAT_LOG_LEVEL` | `level.root` |
| `TAMACAT_LOG_ACCESS_FORMAT` | `accesslog.format` |
| `TAMACAT_LOG_SERVICE_NAME` | `ecs.service.name` |
| `TAMACAT_LOG_SERVICE_ENVIRONMENT` | `ecs.service.environment` |

未知のキーや不正な値は stderr に報告され、その項目だけ既定値が使われます。

## 出力例

text:
```
2026-11-13T13:52:01.123Z INFO  [http-1] o.t.httpd.Server - request /index.html completed {reqId="abc123"}
```

json:
```json
{"@timestamp":"2026-11-13T13:52:01.123Z","log.level":"INFO","message":"request /index.html completed","ecs.version":"8.11","event.dataset":"app","log.logger":"org.tamacat.httpd.Server","process.thread.name":"http-1","service.name":"myapp","labels":{"reqId":"abc123"}}
```

## ビルド

```bash
mvn install
```

```bash
mvn -Psbom package
```

`-Psbom` で CycloneDX SBOM（`target/bom.json`）を生成します。

## ライセンス

Apache License 2.0
