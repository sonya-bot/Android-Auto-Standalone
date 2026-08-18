# Android Auto Self Head Unit App
## AIエージェント実装仕様書 v0.2

---

# 1. 文書目的

本書は、AIコーディングエージェントがAndroidアプリケーションを自律的に設計・実装・検証するための実装仕様書である。

AIエージェントは本仕様書を最上位のプロジェクトルールとして扱い、仕様に明記されていない機能を独断で追加してはならない。

本プロジェクトでは、実装速度より以下を優先する。

1. 正しいAndroidアーキテクチャ
2. Kotlinの型安全性
3. 明確な責務分離
4. テスト可能性
5. 再現可能なGit履歴
6. 段階的な実装
7. 安定した`main` branch
8. ビルド可能な状態の維持

---

# 2. プロジェクト概要

## 2.1 目的

Android端末自身をAndroid AutoのHead Unitとして動作させる。

ユーザーが本アプリを起動すると、同一Android端末上で動作しているAndroid Auto Developer Head Unit Serverへ接続し、Android Auto Projection Sessionを開始する。

最終的なUXは以下とする。

```text
アプリアイコンをタップ
        ↓
本アプリ起動
        ↓
Android Auto Head Unit Server確認
        ↓
127.0.0.1:5277へ接続
        ↓
AAP Session確立
        ↓
Video Projection開始
        ↓
Android Auto UI表示
        ↓
Touch / Audio操作
```

Google公式のDesktop Head UnitもAndroid AutoのHead Unitをエミュレートするための開発ツールとして提供されている。

---

# 3. 技術的前提

本アプリはAndroid Autoそのものを通常のAndroid `Intent`で起動するアプリではない。

本アプリ自身がAndroid AutoのHead Unit / Receiverとして機能する。

概念構成:

```text
Android Device
│
├── Android Auto
│
│   └── Developer Head Unit Server
│       └── TCP :5277
│
└── Self Head Unit App
    │
    ├── TCP Connection
    ├── AAP Transport
    ├── TLS
    ├── Control Channel
    ├── Video Channel
    ├── Input Channel
    └── Audio Channel
        │
        ▼
    Android Auto UI
```

Open HeadunitにはAndroid端末自身でAndroid Autoを表示するSelf Modeおよびloopbackを利用する運用実績が存在する。

---

# 4. 開発対象

## 4.1 Platform

```text
Android
```

## 4.2 主言語

```text
Kotlin
```

新規アプリケーションコードをJavaで実装してはならない。

例外:

- 外部OSS
- 自動生成コード
- Protocol Buffers生成コード
- 既存Javaコードとのinteropが不可避な場合

---

# 5. Android SDK

原則:

```text
minSdk = 30
```

すなわちAndroid 11以上を初期ターゲットとする。

`compileSdk`および`targetSdk`は実装開始時点の最新安定版を選択する。

preview / beta SDKを通常ビルドへ採用してはならない。

SDK versionを変更する場合は、変更理由を記録すること。

---

# 6. Build System

以下を使用する。

```text
Gradle
Gradle Kotlin DSL
```

必須形式:

```text
build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml
```

新規Groovy Gradle scriptは禁止する。

Dependency versionはVersion Catalogへ集約する。

禁止:

```text
+
latest
latest.release
1.+
```

すべて明示的なversionで固定する。

---

# 7. UI技術

Projection画面はAndroid標準の、

```text
Activity
SurfaceView
Surface
MediaCodec
```

を基本とする。

Android Auto映像描画そのものをCompose Canvas等で再実装してはならない。

設定画面等には必要に応じてJetpack Composeを使用してよい。

---

# 8. Orientation

Projection Activityは原則、

```text
Landscape
```

固定とする。

Android Auto Projectionのアスペクト比を維持する。

禁止:

```text
映像Stretch
非等方scale
```

必要に応じてletterboxを使用する。

---

# 9. 接続仕様

初期PoCでは接続先を以下へ固定する。

```text
HOST = 127.0.0.1
PORT = 5277
```

GoogleのDHUでもAndroid Auto Head Unit Serverとの通信にTCP 5277が使用される。

マジックナンバーとして各クラスへ分散させてはならない。

例:

```kotlin
internal object HeadUnitEndpoint {
    const val HOST = "127.0.0.1"
    const val PORT = 5277
}
```

---

# 10. 接続状態

最低限以下の状態を表現する。

```text
Idle
 ↓
Connecting
 ↓
TcpConnected
 ↓
Negotiating
 ↓
Handshaking
 ↓
Connected
 ↓
StartingProjection
 ↓
Projecting
```

異常:

```text
任意の状態
 ↓
Error
 ↓
Disconnected
```

Connection StateはUI文字列で管理してはならない。

`sealed interface`または`sealed class`を使用する。

例:

```kotlin
sealed interface ConnectionState {

    data object Idle : ConnectionState

    data object Connecting : ConnectionState

    data object TcpConnected : ConnectionState

    data object Handshaking : ConnectionState

    data object Projecting : ConnectionState

    data class Error(
        val cause: Throwable,
    ) : ConnectionState
}
```

---

# 11. 状態通知

状態管理には原則、

```text
StateFlow
SharedFlow
```

を使用する。

新規LiveData実装は禁止する。

---

# 12. アーキテクチャ

依存方向を以下へ固定する。

```text
UI
 ↓
Application / Service
 ↓
Connection
 ↓
Transport
 ↓
Protocol
 ↓
Media / Input
```

禁止する依存:

```text
Protocol → Activity

Connection → Activity

VideoDecoder → Activity

Socket → SurfaceView

UI → raw Socket
```

---

# 13. 推奨ディレクトリ構造

```text
app/
└── src/
    ├── main/
    │   ├── java/<base-package>/
    │   │
    │   ├── ui/
    │   │   ├── MainActivity.kt
    │   │   └── ProjectionActivity.kt
    │   │
    │   ├── service/
    │   │   └── HeadUnitService.kt
    │   │
    │   ├── connection/
    │   │   ├── HeadUnitConnection.kt
    │   │   ├── SocketHeadUnitConnection.kt
    │   │   ├── ConnectionManager.kt
    │   │   └── ConnectionState.kt
    │   │
    │   ├── transport/
    │   │   ├── AapTransport.kt
    │   │   ├── AapReader.kt
    │   │   └── AapWriter.kt
    │   │
    │   ├── protocol/
    │   │   ├── AapMessage.kt
    │   │   ├── AapMessageParser.kt
    │   │   ├── AapMessageHandler.kt
    │   │   ├── AapControlHandler.kt
    │   │   └── Channel.kt
    │   │
    │   ├── security/
    │   │   └── AapTlsTransport.kt
    │   │
    │   ├── video/
    │   │   ├── VideoChannel.kt
    │   │   ├── VideoFrameAssembler.kt
    │   │   ├── VideoDecoder.kt
    │   │   └── ProjectionSurface.kt
    │   │
    │   ├── input/
    │   │   ├── InputChannel.kt
    │   │   └── TouchEventMapper.kt
    │   │
    │   ├── audio/
    │   │   ├── AudioChannel.kt
    │   │   └── AudioOutput.kt
    │   │
    │   └── common/
    │       ├── AppLogger.kt
    │       └── Constants.kt
    │
    └── res/
```

パッケージとディレクトリ構造を一致させる。

---

# 14. 初期PoC

最初の目標を以下へ限定する。

```text
Android Auto
Developer Head Unit Server
        ↓
127.0.0.1:5277
        ↓
Self Head Unit App
        ↓
AAP Session
        ↓
H.264 Video
        ↓
MediaCodec
        ↓
SurfaceView
```

---

# 15. Phase 0 — Project Foundation

目的:

```text
安定したAndroidプロジェクトを作成する
```

実装:

- Kotlin Android project
- Gradle Kotlin DSL
- Version Catalog
- Android Lint
- ktlint
- detekt
- Unit Test framework
- `.editorconfig`
- `.gitignore`

完了条件:

```text
assembleDebug PASS
test PASS
lint PASS
ktlint PASS
detekt PASS
```

---

# 16. Phase 1 — TCP Connection

目的:

```text
127.0.0.1:5277への接続
```

実装:

```text
Socket
 ↓
Connection Manager
 ↓
Connected
```

Head Unit Server停止中もクラッシュしてはならない。

明確なconnection errorを返す。

---

# 17. Phase 2 — AAP Session

実装:

```text
Version Negotiation
 ↓
TLS Handshake
 ↓
AAP Transport
 ↓
Control Channel
 ↓
Service Discovery
 ↓
Session Established
```

TCP処理とAAP Protocol処理を同一クラスへ実装してはならない。

---

# 18. Phase 3 — Video Projection

処理:

```text
AAP Video Channel
 ↓
AAP Packet
 ↓
Fragment Reconstruction
 ↓
Encoded Video Frame
 ↓
MediaCodec
 ↓
Surface
```

初期実装ではH.264を優先する。

Open Headunitでもdecoder compatibility問題に対するH.264利用が案内されている。

---

# 19. Phase 4 — Touch Input

実装:

```text
Surface Touch
 ↓
TouchEventMapper
 ↓
Projection座標へ変換
 ↓
AAP Input Channel
 ↓
Android Auto
```

最低限:

```text
ACTION_DOWN
ACTION_MOVE
ACTION_UP
```

multi-touchは後続実装とする。

---

# 20. Phase 5 — Audio

使用:

```text
AudioTrack
```

最低限:

```text
Media Audio
Navigation Audio
System Audio
```

Audio処理をActivityに実装してはならない。

---

# 21. Phase 6 — 起動UX

最終的に、

```text
App Launch
 ↓
Head Unit Server確認
 ↓
Connect
 ↓
AAP Session
 ↓
Projection
```

とする。

Head Unit Server停止時にはAndroid Auto Developer Settingsへの誘導を行う。

Android Auto内部設定を非公開API等で強制変更してはならない。

---

# 22. v0.1対象機能

実装対象:

```text
App起動

localhost接続

AAP handshake

Control Channel

Video Channel

H.264 decode

SurfaceView表示

Connection State

Error handling

Logging
```

---

# 23. v0.1対象外

以下は実装しない。

```text
USB

Bluetooth

Wi-Fi Direct

外部端末接続

車両GPS

車速

CAN

Steering Wheel Control

Rotary Controller

Microphone

Voice Assistant

Navigation Metadata

Instrument Cluster

Picture-in-Picture

H.265最適化

自動無限再接続

Boot時自動起動
```

AIエージェントは勝手に実装範囲を拡張してはならない。

---

# 24. Kotlinコーディング規則

Google Android Kotlin Style Guideを最優先規則とする。

Kotlin公式Coding Conventionsを補助規則として使用する。

両者に差異が存在する場合、

```text
Android Kotlin Style Guide
```

を優先する。

---

# 25. Encoding

すべて、

```text
UTF-8
```

とする。

---

# 26. インデント

```text
4 spaces
```

Tabは禁止。

---

# 27. Class Naming

```text
PascalCase
```

例:

```kotlin
ConnectionManager
VideoDecoder
AapTransport
```

---

# 28. Function / Property Naming

```text
lowerCamelCase
```

例:

```kotlin
connectToHeadUnit()
startHandshake()
decodeFrame()
```

---

# 29. Constant Naming

```text
UPPER_SNAKE_CASE
```

例:

```kotlin
private const val CONNECTION_TIMEOUT_MS = 5_000L
```

---

# 30. ファイル命名

ファイル名は主要型または責務と一致させる。

禁止:

```text
Utils.kt
Helper.kt
Stuff.kt
Misc.kt
CommonUtils.kt
ManagerUtils.kt
```

曖昧な`Util`クラスの作成を避ける。

---

# 31. Null Safety

`!!`は禁止を原則とする。

禁止:

```kotlin
connection!!.start()
```

使用:

```kotlin
connection?.start()
```

または:

```kotlin
val activeConnection = requireNotNull(connection)
```

そもそもnullableが必要ない設計ならnullableを導入しない。

---

# 32. lateinit

`lateinit`はAndroid lifecycle上合理的な場合だけ使用する。

優先順位:

```text
constructor dependency
 ↓
lazy
 ↓
nullable
 ↓
lateinit
```

---

# 33. Coroutine

使用:

```text
Kotlin Coroutines
```

禁止:

```kotlin
GlobalScope.launch { }
```

Activity:

```text
lifecycleScope
```

ViewModel:

```text
viewModelScope
```

Service:

```text
Service専用CoroutineScope
```

Service終了時は必ずcancelする。

---

# 34. Thread

Main Thread上で以下を禁止する。

```text
Socket.connect()

blocking read()

blocking write()

TLS handshake

大量ByteArray processing

MediaCodecの重い同期処理
```

I/Oは適切に`Dispatchers.IO`へ分離する。

---

# 35. Exception

空catchは禁止。

禁止:

```kotlin
try {
    operation()
} catch (e: Exception) {
}
```

Exception発生時には最低1つ実施する。

```text
状態更新

ログ

resource cleanup

上位層への通知
```

---

# 36. Logging

禁止:

```kotlin
println()
System.out.println()
```

Android `Log`またはProject Loggerを使用する。

ログへ以下を出してはならない。

```text
秘密鍵

TLS key material

credential

完全な映像frame

完全な音声buffer

個人情報
```

---

# 37. Magic Number

禁止:

```kotlin
connect(5277, 5000)
```

定数へ意味を与える。

---

# 38. Boolean Argument

意味不明なBoolean引数は禁止する。

禁止:

```kotlin
connect(true, false)
```

推奨:

```kotlin
connect(
    retryPolicy = RetryPolicy.Disabled,
)
```

---

# 39. Function Size

新規関数:

```text
原則50行以内
```

超える場合は責務分離を検討する。

機械的に分割するのではなく、意味的な責務を単位とする。

---

# 40. Class Size

新規クラス:

```text
原則500行以内
```

巨大なGod Classを禁止する。

特に巨大Activityを作成してはならない。

---

# 41. Activity責務

Activityで許可:

```text
UI

Lifecycle

Surface取得

ユーザー入力取得

状態表示
```

禁止:

```text
Socket

TLS

AAP parser

AAP state machine

Media protocol

Retry algorithm
```

---

# 42. Service責務

HeadUnitServiceは、

```text
Connection lifecycle

AAP Session lifecycle

Projection lifecycle
```

を管理する。

Activity lifecycleとAAP connection lifecycleを直接結合しない。

---

# 43. Interface設計

外部I/O境界にはinterfaceを使用する。

例:

```kotlin
interface HeadUnitConnection {

    suspend fun connect()

    suspend fun disconnect()

    suspend fun read(): ByteArray

    suspend fun write(data: ByteArray)
}
```

Unit TestでFakeへ置換可能にする。

---

# 44. Resource Lifecycle

必ず解放する。

```text
Socket

InputStream

OutputStream

MediaCodec

AudioTrack

Surface関連resource

CoroutineScope
```

各クラスに必要に応じて、

```text
start()

stop()

close()

release()
```

を持たせる。

GCにresource cleanupを依存してはならない。

---

# 45. MediaCodec

状態順序を守る。

```text
configure
 ↓
start
 ↓
queue / dequeue
 ↓
stop
 ↓
release
```

例外時にもreleaseを保証する。

---

# 46. Protocol Layer

AAP処理は以下へ分離する。

```text
Raw Transport
 ↓
AAP Frame
 ↓
AAP Message
 ↓
Channel
 ↓
Feature Handler
```

禁止:

```text
Socket Reader
 ↓
VideoDecoder直接呼び出し
```

正:

```text
Socket
 ↓
Transport
 ↓
Message Parser
 ↓
Channel Handler
 ↓
Video Handler
 ↓
Video Decoder
```

---

# 47. ByteArray

PoC段階で過剰なzero-copy最適化を行わない。

まず、

```text
Correctness
Readability
Testability
```

を優先する。

性能問題が測定された後に最適化する。

---

# 48. コメント

コメントは「何をしているか」ではなく、

```text
なぜ必要なのか
```

を書く。

禁止:

```kotlin
// 接続する
connect()
```

---

# 49. TODO

禁止:

```kotlin
// TODO later
```

許可:

```kotlin
// TODO(#24): Add multi-touch support after v0.1.
```

追跡可能な理由を記載する。

---

# 50. Dependency追加

依存ライブラリは必要性を説明できる場合のみ追加する。

禁止理由:

```text
便利だから

簡単だから

流行しているから
```

Android/Kotlin標準APIで十分な場合は標準APIを優先する。

---

# 51. Static Analysis

必須:

```text
Android Lint

ktlint

detekt
```

`.editorconfig`にはAndroid向けKotlin styleを適用する。

Kotlin公式ドキュメントでもktlintをAndroid styleへ設定する方法が案内されている。

---

# 52. Quality Gate

以下がPASSしなければbranchを`main`へ統合してはならない。

```text
./gradlew assembleDebug

./gradlew test

./gradlew lint

ktlint check相当

detekt
```

実際のGradle Pluginに応じてTask名を確定する。

---

# 53. Unit Test

最低限:

## Connection

```text
正常接続

connection refused

timeout

disconnect
```

## Protocol

```text
header parse

invalid packet

fragmented packet

unknown channel
```

## Video

```text
single frame

fragmented frame

invalid frame
```

---

# 54. Instrumentation Test

最低限:

```text
Activity launch

Surface lifecycle

Service lifecycle / binding
```

実際のAndroid Auto Head Unit ServerとのEnd-to-End Testは実機試験とする。

---

# 55. Error UI

最低限区別する。

```text
Head Unit Serverが起動していません

Android Autoへ接続できません

AAP handshakeに失敗しました

Video Decoderを初期化できません

Projectionが切断されました
```

Stack TraceをUIへ表示してはならない。

---

# 56. Open Headunit

Open Headunitは技術的参考および必要に応じた実装ベースとして使用できる。

同プロジェクトにはSelf Modeの実装が存在する。

コードを利用する場合はRepositoryのlicenseを確認し、該当ライセンス条件を遵守する。

OSSライセンスを回避する目的でコードを機械的に書き換えてはならない。

---

# 57. 実装戦略

ゼロからAAPを完全再実装することを最初の方針としない。

推奨:

```text
Open Headunit確認
 ↓
Build
 ↓
Self Mode経路確認
 ↓
必要な依存関係を特定
 ↓
PoCに必要な機能を残す
 ↓
段階的に簡略化
```

大量削除を一度に行わない。

---

# 58. Git基本方針

Git操作はAIエージェントによる自動化を許可する。

ただし、本章の原則を厳守すること。

主要branchは、

```text
main
```

とする。

`master`等を主要branchとして新規作成しない。

---

# 59. main Branch原則

`main`は常に、

```text
Build可能

Test可能

既知の重大障害なし
```

の安定状態を維持する。

**`main`で直接機能実装を行ってはならない。**

---

# 60. 初期Repository

初期化順序:

```text
Project作成
 ↓
Build確認
 ↓
.gitignore確認
 ↓
Git初期化
 ↓
main確認
 ↓
初期commit
```

初期commit例:

```text
chore: initialize Android project
```

初期commitはビルド可能な状態で行う。

---

# 61. Branch Strategy

原則:

```text
main
 │
 ├── feature/*
 │
 ├── fix/*
 │
 ├── refactor/*
 │
 ├── test/*
 │
 ├── docs/*
 │
 └── chore/*
```

---

# 62. Branch Naming

使用可能:

```text
feature/<purpose>

fix/<purpose>

refactor/<purpose>

test/<purpose>

docs/<purpose>

chore/<purpose>
```

例:

```text
feature/project-foundation

feature/headunit-connection

feature/aap-session

feature/video-projection

feature/touch-input

feature/audio-output

fix/socket-disconnection

fix/video-decoder-release

refactor/aap-transport
```

禁止:

```text
test

new

work

temp

tmp

update

branch1
```

---

# 63. 1 Branch = 1 Purpose

1つのbranchは原則1つの目的だけを持つ。

禁止:

```text
feature/poc-v01

├ TCP Connection
├ AAP Session
├ Video
├ Touch
└ Audio
```

推奨:

```text
feature/headunit-connection
 ↓ merge

feature/aap-session
 ↓ merge

feature/video-projection
 ↓ merge

feature/touch-input
 ↓ merge

feature/audio-output
```

---

# 64. Branch作業開始

新機能開始時:

```text
mainへ切替
 ↓
main状態確認
 ↓
新規branch作成
 ↓
実装
```

既存feature branchから別feature branchを派生させることは原則禁止する。

必要な場合は理由を明確にする。

---

# 65. Git自動実行許可

AIエージェントは以下を自動実行してよい。

```text
git status

git diff

git log

git branch

git switch

git switch -c

git add

git commit

git merge

git branch -d
```

---

# 66. Commit前検証

commit前に最低限、

```text
git status

git diff
```

を確認する。

意図しない変更をcommitしてはならない。

---

# 67. Commit Message

Conventional Commits形式を採用する。

許可:

```text
feat:

fix:

refactor:

test:

docs:

build:

chore:
```

例:

```text
chore: initialize Android project

feat: add head unit TCP connection

feat: implement AAP session handshake

feat: add H264 projection decoder

feat: add touch input forwarding

fix: release MediaCodec on disconnect

refactor: separate transport from socket layer
```

禁止:

```text
update

fix

changes

working

test

wip

修正

変更
```

---

# 68. Commit Granularity

1 commitには関連する変更だけを含める。

禁止:

```text
TCP connection
+
UI redesign
+
Video decoder
```

を1 commitに混在させる。

一方で過度な1行commitも行わない。

基準:

```text
意味的に独立した最小変更単位
```

---

# 69. Branch内Commit

1 branch = 1 commitである必要はない。

例えば:

```text
feature/video-projection

feat: add video frame assembler

feat: add H264 MediaCodec decoder

test: add video frame assembler tests
```

は許可する。

ただしbranch全体として目的を1つに維持する。

---

# 70. main Merge条件

以下すべてPASSした場合のみmerge可能。

```text
assembleDebug PASS

Unit Test PASS

Android Lint PASS

ktlint PASS

detekt PASS
```

加えて:

```text
Compiler Errorなし

未解決Lint Errorなし

既存Test破壊なし

不要Debug Codeなし

コメントアウトされた旧コードなし

未使用Importなし

意図しない変更なし
```

を確認する。

---

# 71. Merge Flow

標準:

```text
main
 ↓
feature branch作成
 ↓
実装
 ↓
Build
 ↓
Lint
 ↓
Test
 ↓
git diff確認
 ↓
commit
 ↓
mainへ切替
 ↓
merge
 ↓
main上で再Build
 ↓
main上で再Test
 ↓
branch削除
```

---

# 72. Merge後検証

merge自体が成功しても完了ではない。

必ず`main`上で再度、

```text
Build

Lint

Test
```

を行う。

失敗した場合、正常mergeとして扱わない。

---

# 73. Branch削除

mergeおよび検証成功後、

```text
git branch -d <branch>
```

によるローカルbranch削除を自動実行してよい。

---

# 74. Git禁止操作

AIエージェントは原則以下を実行してはならない。

```text
git reset --hard

git clean -fd

git branch -D

git push --force

git push --force-with-lease

mainの履歴を書き換えるrebase
```

必要になった場合は自動実行せず、状況を報告する。

---

# 75. User変更保護

AIエージェントは、自分が作成していない未commit変更を破棄してはならない。

作業開始時に必ず、

```text
git status
```

を確認する。

ユーザー変更と競合する場合は、ユーザーデータを保持する方向を優先する。

---

# 76. Remote Operations

以下はローカルGit運用とは区別する。

```text
git push

Remote branch削除

Pull Request作成

Pull Request merge
```

Remote操作が利用可能であり、Repository運用上問題がない場合は自動化してよい。

ただし、

```text
force push
```

は禁止する。

---

# 77. Git Conflict

merge conflict発生時は、

```text
Conflict確認
 ↓
双方の変更意図確認
 ↓
最小解決
 ↓
Build
 ↓
Test
```

を行う。

機械的に`ours`または`theirs`を全面採用してはならない。

---

# 78. 推奨Branch順序

初期PoC:

```text
main
 │
 ├── feature/project-foundation
 │          ↓
 │        merge
 │
 ├── feature/headunit-connection
 │          ↓
 │        merge
 │
 ├── feature/aap-session
 │          ↓
 │        merge
 │
 ├── feature/video-projection
 │          ↓
 │        merge
 │
 ├── feature/touch-input
 │          ↓
 │        merge
 │
 └── feature/audio-output
            ↓
          merge
```

---

# 79. AIエージェント標準ワークフロー

各機能について必ず、

```text
1. git status

2. main確認

3. Build確認

4. feature branch作成

5. 関連コード調査

6. 実装計画確認

7. 最小実装

8. Build

9. Lint

10. Test

11. git diff

12. Commit

13. mainへmerge

14. main上でBuild

15. main上でTest

16. feature branch削除

17. 次の実装
```

の流れを維持する。

---

# 80. AIエージェント禁止事項

以下を禁止する。

```text
mainへ直接機能実装

Build失敗状態で次機能へ進む

Test失敗を放置

Lint ErrorをSuppressして隠す

@Suppress("ALL")

空catch

!!乱用

GlobalScope

Main Thread blocking I/O

God Activity

God Class

仕様外機能追加

無関係な大規模refactor

不要dependency追加

ユーザー変更破棄

Force Push

未merge branch強制削除
```

---

# 81. リファクタリング

Feature追加と大規模refactorを同時に行わない。

例:

```text
feature/video-projection
```

完了後に、

```text
refactor/video-pipeline
```

を独立branchで実施する。

---

# 82. Bug Fix

Bugは、

```text
fix/<purpose>
```

で修正する。

例:

```text
fix/socket-reconnect-crash

fix/media-codec-release
```

mainへ直接修正してはならない。

---

# 83. PoC v0.1 Acceptance Criteria

以下すべて満たすこと。

```text
[ ] Androidアプリが正常起動する

[ ] 主要branchがmainである

[ ] main上で直接開発されていない

[ ] Android Auto Head Unit Serverへ接続できる

[ ] 127.0.0.1:5277接続が成功する

[ ] AAP Sessionが確立する

[ ] Control Channelが動作する

[ ] Video Channelを受信する

[ ] H.264 Frameを復元できる

[ ] MediaCodecでdecodeできる

[ ] SurfaceViewにAndroid Auto UIが表示される

[ ] Head Unit Server停止時にクラッシュしない

[ ] Socketが正常にcloseされる

[ ] MediaCodecがreleaseされる

[ ] Coroutine leakがない

[ ] assembleDebug PASS

[ ] Unit Test PASS

[ ] Android Lint PASS

[ ] ktlint PASS

[ ] detekt PASS

[ ] feature branch単位で履歴が管理されている

[ ] merge後のmainでもBuild/TestがPASSする
```

---

# 84. v0.2 Acceptance Criteria

v0.1完了後のみ開始する。

```text
[ ] Touch入力

[ ] Android Auto Home操作

[ ] Maps操作

[ ] Media App操作

[ ] Audio Playback
```

---

# 85. 最終アーキテクチャ

```text
User
 │
 │ Tap
 ▼
┌─────────────────────────────────┐
│ Self Head Unit App              │
│                                 │
│ UI                              │
│  │                              │
│  ▼                              │
│ HeadUnitService                 │
│  │                              │
│  ▼                              │
│ ConnectionManager               │
│  │                              │
│  ▼                              │
│ 127.0.0.1:5277                  │
│  │                              │
│  ▼                              │
│ AapTransport                    │
│  │                              │
│  ├──────── Control              │
│  │                              │
│  ├──────── Video                │
│  │          │                   │
│  │          ▼                   │
│  │      MediaCodec              │
│  │          │                   │
│  │          ▼                   │
│  │        Surface               │
│  │                              │
│  ├──────── Input ─────────────┐ │
│  │                           │ │
│  └──────── Audio             │ │
│                              │ │
│         Android Auto UI ◀────┘ │
└─────────────────────────────────┘
```

---

# 86. AIエージェントへの最重要原則

本プロジェクトでは、

> 動くコードを大量に生成することではなく、検証可能で保守可能なコードを、小さな変更単位で安全に積み上げること

を最優先とする。

AIエージェントは、

```text
調査
 ↓
設計
 ↓
Branch作成
 ↓
最小実装
 ↓
Build
 ↓
Lint
 ↓
Test
 ↓
Commit
 ↓
Merge
 ↓
再検証
```

を繰り返す。

---

# 87. 実装開始時の初回タスク

AIエージェントは、最初から大量のコードを書いてはならない。

最初に以下を実行する。

```text
Task 1
Repository状態を確認する

Task 2
Git main branchを確認する

Task 3
未commit変更を確認する

Task 4
Gradle / Android / Kotlin versionを確認する

Task 5
現在のBuildを確認する

Task 6
Open HeadunitのSelf Mode実装を確認する

Task 7
127.0.0.1:5277への接続経路を確認する

Task 8
AAP Transportの依存関係を確認する

Task 9
Video Pipelineの依存関係を確認する

Task 10
PoCに不要な機能を整理する

Task 11
実装計画を提示する

Task 12
feature/project-foundationから実装を開始する
```

---

# 88. 実装優先順位

```text
P0
Project / Git / Quality Gate

P1
TCP Connection

P2
AAP Session

P3
Video Projection

P4
Touch Input

P5
Audio

P6
UX

P7
Reconnect / Automation
```

P0〜P3を最初のPoC完成条件とする。

---

# 89. Definition of Done

機能はコードを書き終えた時点では完成とみなさない。

以下をすべて満たした時点で完成とする。

```text
Implementation complete

Build PASS

Lint PASS

Static Analysis PASS

Unit Test PASS

No unintended diff

Commit complete

main merge complete

main Build PASS

main Test PASS

feature branch cleanup complete
```

このDefinition of Doneを満たさない状態で次の機能へ進んではならない。