# tamacat-logging 要件定義・設計書 (v1.0)

[English](DESIGN.md) | 日本語

> ステータス: **確定**（2026-11-13）。本書に基づき実装済み。

---

## 1. 目的

Java 25 向けの、**セキュリティ最優先・依存最小・機能最小**のロギング実装。
主用途は**コンテナ上で動く Web サーバー**のアプリケーションログ・アクセスログ出力。
出力先は**標準出力のみ**とし、収集・保存・ローテーションはコンテナ基盤（Docker / Kubernetes / ログコレクタ）に委ねる。

Logback / Log4j2 の代替として、「攻撃面（attack surface）を構造的に持たない」ことを最大の設計目標とする。

### 1.1 既知の脆弱性クラスと本実装での扱い

| 事例 | 原因となった機能 | 本実装での扱い |
|---|---|---|
| Log4Shell (CVE-2021-44228) | ログメッセージ内の `${...}` Lookup / JNDI | **Lookup・変数展開機能を持たない** |
| Log4j CVE-2021-45046/45105 | Context Lookup の再帰評価 | 同上 |
| Logback CVE-2023-6378/6481 | SocketReceiver のデシリアライズ | **ネットワーク送受信・Java シリアライズを持たない** |
| Logback CVE-2021-42550 | JNDI を使う XML 設定 | **XML 設定・JNDI を持たない** |
| Logback CVE-2024-12798/12801 | 設定ファイル内の式評価 / SSRF | **式評価・外部参照を持たない** |
| Jackson の多数の CVE | 汎用 (デ)シリアライザのポリモーフィック型処理 | **JSON は自前の書き出し専用エンコーダ（パース無し）** |
| ファイル系 Appender の問題 | symlink 追従・パストラバーサル・パーミッション | **ファイル出力自体を持たない** |
| ログインジェクション (CWE-117) | 改行・制御文字の未エスケープ | **全出力値を形式ごとに強制エスケープ** |

---

## 2. 要件

### 2.1 機能要件

| ID | 要件 |
|---|---|
| F-1 | SLF4J 2.x API のバックエンド（`SLF4JServiceProvider`）として動作する |
| F-2 | `java.lang.System.Logger`（`System.LoggerFinder`）のバックエンドとして動作する |
| F-3 | Web サーバーの**アクセスログ**専用 API と出力形式（Common / Combined / ECS JSON）を提供する（§4.3） |
| F-4 | ログレベル: `TRACE / DEBUG / INFO / WARN / ERROR / OFF`、ロガー名の階層単位で設定可能 |
| F-5 | 出力形式: **テキスト形式**（限定パターン）と **JSON Lines 形式**（1行1 JSON、**ECS 互換**） |
| F-6 | 出力先: **標準出力のみ**（ファイル・ローテーション・圧縮は持たない） |
| F-7 | MDC（SLF4J の `MDC`）対応 |
| F-8 | 例外（スタックトレース、cause チェーン、suppressed）の出力。テキスト形式は複数行（既定）/ 1行化オプション |
| F-9 | SLF4J 2.x Fluent API の key-value（`addKeyValue`）を構造化フィールドとして出力 |
| F-10 | **キー名指定による伏字**: MDC / key-value のうち指定キーの値を `***` に置換（正規表現は使わない） |
| F-11 | 設定: classpath の `tamacat-logging.properties` / システムプロパティ / 環境変数（§5） |

### 2.2 非機能要件（セキュリティ）

| ID | 要件 |
|---|---|
| S-1 | **ランタイム依存ゼロ**。`slf4j-api` は optional（SLF4J を使うアプリだけが持つ） |
| S-2 | ログ対象データ（メッセージ・引数・MDC・例外メッセージ等）を**一切評価しない**。SLF4J の `{}` 置換は一回のみで、置換結果を再走査しない |
| S-3 | テキスト形式で CR/LF・制御文字（C0/C1, DEL）・ESC・双方向制御文字を**必ずエスケープ**し、行偽装・端末エスケープ攻撃を防ぐ |
| S-4 | JSON は RFC 8259 準拠の厳格エスケープ + U+2028/2029・不正サロゲート処理。キー名も同様 |
| S-5 | 入力長・キー数・スタック深さの上限で切り詰め、ログ経由の DoS を抑止 |
| S-6 | ネットワーク I/O、ファイル I/O（設定読込を除く）、JNDI、JMX、スクリプト、Java シリアライズ、設定からの任意クラスロードを**実装しない** |
| S-7 | 設定は `java.util.Properties` 形式のみ（XML/YAML 不可）。設定値の変数展開もしない |
| S-8 | ロギング処理中の例外を呼び出し元へ伝播させない。内部エラーは stderr にレート制限付きで報告 |
| S-9 | `toString()` が例外を投げる/巨大でも安全に処理（捕捉して代替文字列、長さ上限） |
| S-10 | MDC/kv がトップレベルの予約フィールド（`log.level` 等）を上書きできない構造にする（§4.2） |

### 2.3 非機能要件（その他）

| ID | 要件 |
|---|---|
| N-1 | Java 25 ベースライン、JPMS 対応。仮想スレッドでピン留めを起こさない（`ReentrantLock`） |
| N-2 | 小さなコードベース（目標: SLF4J 対応を除き 2,000 行以下 ※空行・コメント除く）で全行監査可能 |
| N-3 | 再現可能ビルド、CycloneDX SBOM、Maven Enforcer による依存追加禁止 |
| N-4 | テストのみ JUnit（test スコープ） |
| N-5 | **同期出力のみ**。1イベントごとに flush（コンテナ停止・OOM Kill 時の欠落を最小化） |

### 2.4 対象外（意図的に実装しない）

- Lookup / 変数置換 / 式評価 / スクリプト
- XML・YAML・JSON 形式の設定、設定の自動リロード
- ファイル出力、ローテーション、圧縮、stderr 出力先
- ネットワーク Appender（Socket, Syslog, SMTP, HTTP, DB 等）と受信側
- JNDI, JMX, Java シリアライズ、プラグイン機構
- 非同期出力
- `java.util.logging`（JUL）Handler（必要なら利用者側で `jul-to-slf4j`）
- Log4j API / Commons Logging ブリッジ
- Marker によるフィルタリング、フィルタチェーン
- 色付きコンソール出力
- メッセージ本文のパターン検出による伏字（誤検知・ReDoS リスク）

---

## 3. アーキテクチャ

### 3.1 成果物とモジュール構成

成果物は **`org.tamacat:tamacat-logging` の jar 1つ**（JPMS モジュール `org.tamacat.logging`）。利用者の依存記述は1つで済む。

```java
module org.tamacat.logging {
    requires static org.slf4j;          // optional: 実行時に無くてよい
    exports org.tamacat.logging;
    exports org.tamacat.logging.access;
    provides java.lang.System.LoggerFinder with ...jdk.TamacatLoggerFinder;
    provides org.slf4j.spi.SLF4JServiceProvider with ...slf4j.TamacatServiceProvider;
}
```

- `slf4j-api` は `<optional>true</optional>`。SLF4J プロバイダは SLF4J 自身が ServiceLoader で探したときだけロードされるため、
  slf4j-api が無い環境（System.Logger のみ利用）では一切ロードされず、推移的依存も 0
- モジュールパス / クラスパス × slf4j-api 有り / 無しの4通りで動作確認済み
- `internal` パッケージは export しない（同一モジュール内でのみ使用）
- ライセンス: Apache License 2.0
- `System.LoggerFinder` を提供するため、JDK 内部（`java.net.http` 等）の System.Logger 出力も集約される

（当初は core / slf4j / parent の3成果物に分けていたが、利用者の記述を1つにするため 1.0.0 リリース前に統合した）

### 3.2 処理フロー

```
 SLF4J Logger ──┐
 System.Logger ─┴─▶ LoggerContext ─▶ Logger(level判定) ─▶ LogEvent ──┐
                                                                     ├─▶ Layout ─▶ StdoutSink
 AccessLogger ───────────────────────────────────────▶ AccessLogEntry ┘   (byte[])   (lock + write + flush)
```

- **LogEvent** (`record`, 不変): `Instant timestamp, Level level, String loggerName, String threadName, String message, Map<String,String> labels, Throwable thrown`
- メッセージ整形はレベル判定後のみ実施
- **Layout** はイベント → `byte[]` の純関数。エスケープを迂回する API を持たない
- **StdoutSink**: `new FileOutputStream(FileDescriptor.out)` に直接書き込む。
  `System.out` を経由しないため、アプリが `System.setOut()` でロガーへリダイレクトしても無限再帰にならない
- 1イベント分のバイト列を組み立ててから、ロック内で1回の `write` + `flush`（行の混線を防ぐ）

### 3.3 主要クラス

公開 API は `org.tamacat.logging` と `org.tamacat.logging.access` のみ。

| パッケージ | クラス | 役割 |
|---|---|---|
| `org.tamacat.logging`（公開） | `Level` (enum) | レベル定義 |
| | `LoggerContext` | エンジン本体（設定保持・レベル判定・出力）。`configure()` / `setOutput()` |
| | `Configuration` | 不変設定 + 検証付き Builder、`fromProperties()` |
| | `LogContext` | スレッドローカルの文脈（MDC の実体、非継承） |
| | `ConfigLoader`（非公開） | Properties / 環境変数 / システムプロパティの読込 |
| `org.tamacat.logging.access`（公開） | `AccessLogger` / `AccessLogEntry` / `AccessFormat` | アクセスログ API |
| `org.tamacat.logging.internal` | `Layout` (sealed) | `TextLayout`, `EcsJsonLayout` のみ |
| | `AccessLayout` | common / combined / json の固定実装 |
| | `PatternParser` | 限定パターンの解析（sealed Token） |
| | `TextEscaper` / `JsonEscaper` / `Chars` | エスケープ（テスト最重点） |
| | `LabelBuilder` | ラベルのキー検証・伏字・件数/長さ制限 |
| | `MessageFormatter` | `{}` / `{0}` の単純置換（再帰なし、上限付き） |
| | `ThrowableRenderer` | スタックトレース整形（循環検出・上限付き） |
| | `StdoutSink` | 標準出力への同期書き込み |
| | `StatusReporter` | 内部エラーの stderr 報告（レート制限） |
| | `Safe` | 例外を投げない toString / 切り詰め |
| `org.tamacat.logging.jdk`（非公開） | `TamacatLoggerFinder` / `TamacatSystemLogger` | `System.LoggerFinder` 実装 |
| `org.tamacat.logging.slf4j`（非公開） | `TamacatServiceProvider` ほか | SLF4J プロバイダ、`LoggingEventAware` 対応ロガー、MDC アダプタ |

拡張点（Layout / Token）は sealed / final とし、設定から任意クラスを読み込む経路を持たない。

**System.Logger の書式**: `{0}`, `{1}`… を引数で単純置換する。`java.text.MessageFormat` は使わない
（`{0,choice,...}` のサブフォーマットが置換結果を再評価するため）。

---

## 4. 出力形式

### 4.1 テキスト形式

既定パターン:
```
%d %level [%thread] %logger{36} - %msg%labels%n%ex
```
出力例:
```
2026-11-13T13:52:01.123Z INFO  [http-1] o.t.httpd.Server - Request completed {reqId="abc123", status="200"}
```

`%labels` の値は `"` で囲み、値中の `"` と `\` をエスケープする（`a", role="admin` のような値で項目を偽装できない）。

**限定パターン**（以下の固定トークンのみ。未知トークンは設定エラー）:

| トークン | 内容 |
|---|---|
| `%d` | ISO-8601 UTC タイムスタンプ（書式指定不可） |
| `%level` | レベル（5桁左詰め） |
| `%thread` | スレッド名 |
| `%logger` / `%logger{n}` | ロガー名 / n 文字に省略 |
| `%msg` | メッセージ |
| `%labels` / `%labels{key}` | MDC+kv 全体 / 指定キー |
| `%ex` / `%ex{oneline}` | 例外（複数行 / 1行化） |
| `%n` `%%` | 改行 / `%` |

- パターンは設定時に一度だけ解析してトークン列にする。ログデータがパターンとして解釈されることはない
- 固定文字列部分にも制御文字は許可しない（設定エラー）

**エスケープ規則（全ての可変フィールドに適用）**:

| 入力 | 出力 |
|---|---|
| `\` | `\\` |
| CR / LF / TAB | `\r` / `\n` / `\t` |
| その他 C0 (U+0000–001F), DEL, C1 (U+0080–009F) | `\u00XX`（ESC も含むため端末エスケープ攻撃不可） |
| U+2028 / U+2029 | `\u2028` / `\u2029` |
| 双方向制御 (U+202A–202E, U+2066–2069) | `\u202E` 等（Trojan Source 型の表示偽装対策） |
| 不正サロゲート | `\uFFFD` |

**例外出力**:
- 既定（複数行）: スタックトレース各行の**先頭を必ず TAB** とし、例外メッセージ内の改行はエスケープ。
  「TAB で始まらない行 = 新レコード」が保証され、偽装行を作れない
- `%ex{oneline}`: フレームを ` | ` 区切りで1行に連結。例: `pattern=%d %level [%thread] %logger{36} - %msg%labels%ex{oneline}%n`
- パターンに `%n` が無くても、1レコードは必ず `\n` で終わる

### 4.2 JSON Lines 形式（ECS 互換）

ecs-logging（Elastic 公式仕様）と同じ**ドット区切りフラットキー**、フィールド順固定。

```json
{"@timestamp":"2026-11-13T13:52:01.123Z","log.level":"ERROR","message":"Request failed","ecs.version":"8.11","event.dataset":"app","log.logger":"org.tamacat.httpd.Server","process.thread.name":"http-1","service.name":"myapp","labels":{"reqId":"abc123","password":"***"},"error.type":"java.io.IOException","error.message":"Broken pipe","error.stack_trace":"java.io.IOException: Broken pipe\n\tat ..."}
```

| ECS フィールド | 内容 |
|---|---|
| `@timestamp` | ISO-8601 UTC、ミリ秒 |
| `log.level` | `TRACE`〜`ERROR` |
| `message` | 置換済みメッセージ |
| `ecs.version` | 準拠 ECS バージョン（固定値） |
| `event.dataset` | `app`（アプリログ）/ `access`（アクセスログ）。標準出力に混在するため識別に使う |
| `log.logger` | ロガー名 |
| `process.thread.name` | スレッド名 |
| `service.name` / `service.version` / `service.environment` | 設定値（任意、静的文字列） |
| `labels` | MDC + key-value（**文字列のみ**、同名キーは key-value 優先） |
| `error.type` / `error.message` / `error.stack_trace` | 例外がある場合のみ |

- MDC/kv は `labels` 配下に隔離（トップレベル展開すると攻撃者制御のキーで `log.level` 等を偽装・重複できるため）
- `labels` のキー名は `[A-Za-z0-9_\-]{1,64}` に制限、違反キーは破棄して内部カウンタに計上
- 重複キーを生成しない。値は全て文字列（型推論しない）
- 非 ASCII は UTF-8 のまま（オプションで `\uXXXX` 化）

### 4.3 アクセスログ

Web サーバーがリクエスト完了時に呼び出す専用 API。SLF4J 非依存（slf4j-api 無しで利用可）。

```java
AccessLogger.get().log(new AccessLogEntry(
    Instant start, String remoteAddr, String remoteUser,
    String method, String requestUri, String protocol,
    int status, long bytesSent, String referer, String userAgent,
    Duration elapsed, String requestId));
```

書式は**固定3種のみ**:

| format | 出力 |
|---|---|
| `common` | Apache Common Log Format: `%h %l %u [%t] "%r" %>s %b` |
| `combined` | `common` + ` "Referer" "User-Agent"` + 応答時間(µs) + リクエストID |
| `json` | ECS: `@timestamp`, `event.dataset=access`, `source.address`, `user.name`, `http.request.method`, `url.original`, `http.version`, `http.response.status_code`, `http.response.body.bytes`, `http.request.referrer`, `user_agent.original`, `event.duration`(ns), `http.request.id` |

- テキスト系のエスケープは Apache httpd の `ap_escape_logitem` と同じ方針: `"` → `\"`、`\` → `\\`、制御文字・非 ASCII バイト → `\xHH`
- リクエストライン / User-Agent / Referer / ユーザー名は攻撃者が完全に制御できる値のため、エスケープを省略する API は提供しない
- 長さ上限（既定: URI 2,048 / User-Agent 512 / Referer 2,048 / requestId 128）
- アクセスログはレベルを持たず `accesslog.enabled` で ON/OFF

### 4.4 伏字（マスキング）

- 設定 `mask.keys` に列挙したキー名（大文字小文字無視、完全一致）に該当する MDC / key-value の値を `***` に置換
- 既定: `password,passwd,secret,token,access_token,refresh_token,authorization,cookie,set-cookie,api_key,apikey`
- 正規表現・部分一致・メッセージ本文の走査は行わない（ReDoS・誤検知・性能劣化を避ける）
- アクセスログの `remoteUser` 以外の URI クエリ文字列は伏字対象外（必要ならサーバー側で除去して渡す）

### 4.5 上限値（既定、設定で変更可）

| 項目 | 既定 |
|---|---|
| メッセージ最大長 | 8,192 文字（超過は切り詰め + `...[truncated]`） |
| label 値の最大長 | 1,024 文字 |
| label の最大キー数 | 32 |
| スタックトレース最大フレーム数 | 128（cause 合計） |
| cause チェーン最大深さ | 16（循環参照検出あり） |

---

## 5. 設定

### 5.1 読み込み順（後勝ち）

1. 組込み既定値（`INFO` 以上、テキスト形式）
2. classpath の `tamacat-logging.properties`（またはシステムプロパティ `tamacat.logging.config` で指定したファイル）
3. 環境変数（コンテナ向け。固定の名前のみ、§5.3）
4. システムプロパティ `tamacat.logging.*`

- 起動時に1回だけ読込（リロード無し）。プログラム API による再設定は可
- 未知キー・不正値は stderr に報告し、その項目は既定値を使う（アプリは停止しない）
- 値の `${...}` 展開は行わない

### 5.2 設定例（properties）

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

### 5.3 環境変数

| 環境変数 | 対応キー |
|---|---|
| `TAMACAT_LOG_FORMAT` | `format` (`text` / `json`) |
| `TAMACAT_LOG_LEVEL` | `level.root` |
| `TAMACAT_LOG_ACCESS_FORMAT` | `accesslog.format` |
| `TAMACAT_LOG_SERVICE_NAME` | `ecs.service.name` |
| `TAMACAT_LOG_SERVICE_ENVIRONMENT` | `ecs.service.environment` |

固定の対応表のみで、任意の環境変数を参照する手段は提供しない。

---

## 6. ライセンス方針

| 参考元 | ライセンス | 扱い |
|---|---|---|
| SLF4J API | MIT | **依存として利用**（コードコピー無し） |
| `slf4j-simple` | MIT | Provider 実装の最小例として参考。流用時は著作権表示を保持 |
| Logback | EPL 1.0 / LGPL 2.1 | **コード流用しない**（設計参考のみ） |
| Log4j2 / tinylog 2 | Apache 2.0 | 設計参考のみ。流用する場合は NOTICE に明記 |
| ecs-logging (仕様) | Apache 2.0 | フィールド定義の参照のみ |

方針: **原則スクラッチ実装**。本プロジェクトは Apache License 2.0。

---

## 7. サプライチェーン・品質保証

- ランタイム依存: 0（`slf4j-api` は optional）
- Maven Enforcer `bannedDependencies` で compile/runtime 依存の追加をビルド失敗にする
- CycloneDX SBOM 生成、OSV-Scanner / OWASP Dependency-Check、SpotBugs + FindSecBugs
- テスト重点:
  - エスケープのプロパティベーステスト（全コードポイント・ランダム入力で「出力に生の改行/制御文字が残らない」「JSON が常にパース可能」「1イベント=1行（JSON）」）
  - `${jndi:...}` 等の既知攻撃文字列がそのまま文字列として出力されること
  - 予約フィールド偽装（MDC キー `log.level` 等）が不可能なこと
  - マルチスレッド・仮想スレッドでの行混線がないこと
  - リポジトリの衛生: Java ソースは ASCII のみ、全ファイルに双方向制御文字などの不可視制御文字を含まない
  - テスト用 JSON パーサは test スコープのみ

---

## 8. 決定事項一覧

| # | 事項 | 決定 |
|---|---|---|
| Q1 | 対応 API | SLF4J 2.x プロバイダ + System.Logger |
| Q2 | アクセスログ | 含める（専用 API、common / combined / ECS JSON） |
| Q3 | テキスト書式 | 限定パターン（固定トークンのみ） |
| Q4 | 非同期出力 | 同期のみ |
| Q5 | JSON 形式 | ECS 互換（MDC/kv は `labels` 配下） |
| Q6 | 出力先 | **標準出力のみ**（ファイル・ローテーション・圧縮なし） |
| Q7 | マスキング | キー名完全一致で伏字（正規表現なし） |
| Q8 | ライセンス / 座標 | Apache 2.0 / `org.tamacat:tamacat-logging` / モジュール `org.tamacat.logging` |
| Q9 | 例外のテキスト出力 | 複数行（行頭 TAB 強制）+ `%ex{oneline}` |
| C1 | 環境変数による設定上書き | 固定の5変数のみ（§5.3） |
| C2 | テキスト形式でのアプリログ/アクセスログ混在 | アクセスログは Common/Combined のまま出力。コンテナでは `format=json` を推奨 |
| C3 | ERROR の stderr 分離 | しない（標準出力のみ。stderr は内部エラー報告専用） |
