# Android Auto Self Head Unit App
## 次期実装仕様書 v0.3〜v1.0

---

# 1. 文書目的

本仕様書は、v0.2までに完成したAndroid Auto Self Head Unit Appを、PoCから実用可能な安定版へ移行するための次期実装仕様を定義する。

前工程で以下がすべて完成していることを前提とする。

```text
Android App起動

127.0.0.1:5277接続

AAP Session確立

Control Channel

Video Projection

H.264 Decode

Surface表示

Touch Input

Audio Playback

基本Error Handling

Unit Test

Android Lint

ktlint

detekt

feature branchによるGit運用
```

本工程では既存機能を再実装しない。

重点を以下へ移す。

```text
PoC
 ↓
Connection Reliability
 ↓
Lifecycle Reliability
 ↓
Compatibility
 ↓
Performance
 ↓
Diagnostics
 ↓
Operational UX
 ↓
Release Quality
 ↓
v1.0
```

---

# 2. 次期目標

最終的にユーザーが以下の操作だけでAndroid Autoを継続利用できる状態を目指す。

```text
App起動
   ↓
環境確認
   ↓
Head Unit Server確認
   ↓
接続
   ↓
Android Auto表示
   ↓
通常使用
   ↓
一時切断
   ↓
自動復旧
   ↓
Android Auto継続
```

日常利用時にユーザーが通信状態やAAP内部状態を意識する必要がないことを目標とする。

---

# 3. バージョン構成

次工程を以下に分割する。

```text
v0.3
Connection Reliability

v0.4
Lifecycle / Recovery

v0.5
Device Compatibility

v0.6
Performance / Stability

v0.7
Operational UX

v0.8
Diagnostics / Test Infrastructure

v0.9
Release Candidate

v1.0
Stable Release
```

一度にv1.0まで実装してはならない。

各バージョンのDefinition of Doneを満たした後に次へ進む。

---

# 4. 最重要原則

次工程では、

> 新機能数ではなく、既存機能が壊れず継続動作すること

を最優先とする。

優先順位:

```text
Reliability
>
Correctness
>
Compatibility
>
Observability
>
Performance
>
UX
>
Additional Features
```

---

# 5. Android Auto互換性方針

Android Autoの特定versionの内部挙動を永久仕様として仮定してはならない。

接続処理は以下のように分離する。

```text
Android Auto
     ↓
Compatibility Layer
     ↓
AAP Session
     ↓
Application
```

Android Auto version依存コードを、

```text
MainActivity
ConnectionManager
VideoDecoder
```

へ散在させてはならない。

---

# 6. Compatibility Layer

新規に以下の責務を導入する。

```text
compatibility/
├── AndroidAutoEnvironment.kt
├── AndroidAutoVersion.kt
├── CompatibilityPolicy.kt
└── HeadUnitServerCapability.kt
```

目的:

```text
Android Auto有無確認

Version取得

Head Unit Server利用可否判定

既知の互換性問題判定

接続方式決定
```

---

# 7. Android Auto 17.4以降

現在のSelf ModeではHead Unit Serverを利用する構成を基本とする。

標準接続:

```text
Android Auto
Developer Head Unit Server

127.0.0.1:5277
        │
        ▼
Self Head Unit App
```

接続方式を別の非公開Wireless Projection起動方式へ勝手に切り替えてはならない。

---

# 8. v0.3 — Connection Reliability

## 8.1 目的

TCP/AAP接続を一度成功させるだけでなく、

```text
切断
再接続
失敗
復旧
```

を安全に処理できるようにする。

---

# 9. Connection State Machine

状態を以下に再構成する。

```text
Idle

CheckingEnvironment

WaitingForHeadUnitServer

Connecting

TcpConnected

Negotiating

TlsHandshaking

StartingTransport

StartingProjection

Projecting

Recovering

Disconnecting

Disconnected

FatalError
```

状態遷移を明示的に定義する。

---

# 10. 不正状態遷移禁止

例えば、

```text
Idle
 ↓
Projecting
```

のような遷移を許可してはならない。

Connection State Machine自身が不正遷移を検出する。

---

# 11. Single Session原則

同時に存在できるAAP Sessionは、

```text
最大1
```

とする。

禁止:

```text
connect()
connect()
connect()

→ Socket 3個
```

必ず既存sessionの状態を確認する。

---

# 12. Idempotent API

以下は可能な限り冪等にする。

```text
start()

stop()

connect()

disconnect()

release()
```

例:

```text
disconnect()
 ↓
disconnect()
```

でもクラッシュしてはならない。

---

# 13. 再接続

一時的な切断に対して自動再接続を実装する。

対象:

```text
Socket切断

EOF

Head Unit Server一時停止

Transport停止

TLS Session切断
```

---

# 14. Retry Policy

無限高速再接続は禁止する。

以下のようなbackoffを使用する。

```text
1回目   1 sec

2回目   2 sec

3回目   4 sec

4回目   8 sec

5回目   15 sec
```

上限:

```text
15 sec
```

程度とする。

実際の値は定数・Policyとして定義する。

---

# 15. Retry分類

すべてのErrorをretryしてはならない。

```text
Retryable

NonRetryable

Fatal
```

へ分類する。

例:

```text
Connection refused
→ Retryable

Temporary EOF
→ Retryable

Protocol incompatible
→ NonRetryable

TLS initialization failure
→ Fatal
```

---

# 16. Connection Generation

古い接続処理が新しい接続を破壊しないよう、

```text
Session ID
または
Connection Generation
```

を導入する。

例:

```text
Session #10
 disconnect callback

Session #11
 already connected
```

の場合、

Session #10のcallbackがSession #11をcloseしてはならない。

---

# 17. Timeout

最低限以下へtimeoutを設定する。

```text
TCP connect

Version negotiation

TLS handshake

Service discovery

Projection start
```

無期限waitは禁止する。

---

# 18. v0.3 Git Branch

推奨:

```text
feature/connection-state-machine

feature/reconnect-policy

feature/session-generation

feature/protocol-timeouts

test/reconnect-scenarios
```

---

# 19. v0.3 完成条件

```text
[ ] 連続10回 connect/disconnect可能

[ ] 二重connectが発生しない

[ ] 一時Socket切断から復旧する

[ ] Head Unit Server再起動後に復旧する

[ ] Retryが暴走しない

[ ] 古いcallbackが新sessionへ干渉しない

[ ] 全timeoutが定義されている

[ ] Resource leakなし

[ ] 全Quality Gate PASS
```

---

# 20. v0.4 — Android Lifecycle

## 20.1 目的

Android lifecycleによってProjection Sessionが不正状態にならないようにする。

対象:

```text
Activity pause

Activity stop

Activity recreate

画面回転要求

画面OFF

App background

App foreground

Process recreation
```

---

# 21. UIとSessionの分離

Projection SessionのownerをActivityにしてはならない。

```text
Activity
    │
    │ bind
    ▼
HeadUnitService
    │
    ▼
AAP Session
```

Activity破棄時もSession lifecycleを明確に制御する。

---

# 22. Surface Lifecycle

Surface状態:

```text
Unavailable
 ↓
Created
 ↓
Ready
 ↓
Destroyed
```

を管理する。

Surface消失時にVideoDecoderへinvalid Surfaceを渡してはならない。

---

# 23. Decoder Recovery

Surface再生成時:

```text
Surface destroyed
 ↓
Decoder output停止
 ↓
新Surface生成
 ↓
Decoder再設定
 ↓
必要ならKey Frame要求
 ↓
Projection復旧
```

を安全に実行する。

---

# 24. Foreground Service監査

`targetSdk >= 34`ではForeground Serviceを使用する場合、Android側が要求する適切なservice typeをManifestへ明示する必要がある。

AIエージェントは、

```text
とりあえずdataSync

とりあえずmediaProjection
```

のように不適切なservice typeを割り当ててはならない。

実際に使用している機能に対応するtypeとpermissionを選定する。

---

# 25. v0.4 Git Branch

```text
feature/session-lifecycle

feature/surface-recovery

feature/service-lifecycle

test/activity-recreation

test/surface-recreation
```

---

# 26. v0.4 完成条件

```text
[ ] Activity recreateでクラッシュしない

[ ] background→foreground復帰可能

[ ] Surface再生成可能

[ ] Decoder leakなし

[ ] Service leakなし

[ ] Coroutine leakなし

[ ] Socket leakなし

[ ] AudioTrack leakなし
```

---

# 27. v0.5 — Device Compatibility

## 27.1 目的

特定の1台だけで動作するアプリから脱却する。

---

# 28. Compatibility Matrix

少なくとも以下を記録する。

```text
Device

Manufacturer

Android Version

API Level

Android Auto Version

CPU Architecture

Screen Resolution

Refresh Rate

H.264 Decoder

Audio Output

Result
```

---

# 29. 実機試験

最低限、

```text
Android 11系

Android 13系

Android 14系

Android 15以降
```

の複数世代を可能な範囲で検証する。

実機を保有していないversionについて、動作保証を捏造してはならない。

---

# 30. MediaCodec Capability

起動時またはProjection開始前にdecoder capabilityを確認する。

確認:

```text
video/avc

required resolution

required profile

required level
```

---

# 31. Decoder Selection

decoder名を固定してはならない。

禁止:

```text
OMX.vendor.decoder
```

固定。

Androidのcodec discovery APIから利用可能decoderを選定する。

---

# 32. Resolution

最低限以下を想定する。

```text
800x480

1280x720

1920x1080
```

実際に使用可能なresolutionはAAP negotiationと端末性能に基づいて決定する。

---

# 33. Display Metrics

以下を考慮する。

```text
physical pixels

density

Surface size

projection size

aspect ratio

display cutout
```

Touch座標変換とVideo描画で同一Transformを利用する。

---

# 34. Touch Transform

以下を単一コンポーネントへ統合する。

```text
View Coordinate
       ↓
ProjectionTransform
       ↓
Android Auto Coordinate
```

VideoとTouchで別々のscale計算を実装してはならない。

---

# 35. v0.5 Git Branch

```text
feature/device-capability

feature/video-capability-detection

feature/projection-transform

test/resolution-matrix
```

---

# 36. v0.6 — Performance / Stability

## 36.1 目的

長時間利用可能なProjection Sessionを実現する。

---

# 37. 長時間試験

最低限、

```text
30分

1時間

3時間
```

の連続Projection試験を行う。

測定:

```text
Crash

ANR

Memory

CPU

Frame Drop

Audio Drop

Reconnect Count
```

---

# 38. Memory

監視対象:

```text
Java Heap

Native Heap

MediaCodec buffers

ByteArray allocation
```

時間経過に伴う単調増加を検出する。

---

# 39. ByteArray Allocation

AAP Video処理でframeごとの不要な巨大allocationを減らす。

ただし、

```text
計測前の過剰最適化
```

は禁止する。

Profilerで問題を確認した場合のみ最適化する。

---

# 40. Video Backpressure

decodeが受信速度へ追いつかない場合、

```text
無制限queue
```

を作成してはならない。

リアルタイム表示では古いframeを無制限に保持しない。

---

# 41. Audio Backpressure

Audioについてもbounded bufferを使用する。

Buffer underflow / overflowを記録する。

---

# 42. ANR

Main Thread上で以下を実行しない。

```text
Socket

TLS

AAP parse loop

Video processing

Audio processing

大量ログ

大容量file I/O
```

---

# 43. Thermal

長時間運用時に、

```text
CPU usage

device temperature

thermal throttling
```

を可能な範囲で確認する。

---

# 44. Performance Metrics

Debug buildでは最低限以下を取得可能にする。

```text
FPS

Video frames received

Video frames decoded

Dropped frames

Audio underruns

Reconnect count

Session duration

Protocol errors
```

Release UIへ常時表示する必要はない。

---

# 45. v0.6 Git Branch

```text
feature/performance-metrics

fix/video-backpressure

fix/audio-backpressure

refactor/buffer-management

test/long-running-session
```

---

# 46. v0.7 — Operational UX

## 46.1 目的

技術知識を持たないユーザーでも起動できる状態にする。

---

# 47. First Launch

初回起動時に環境チェックを行う。

```text
Android Auto installed
        ↓
Developer mode available
        ↓
Head Unit Server status
        ↓
Required app permissions
        ↓
Ready
```

---

# 48. Setup Screen

状態を明示する。

例:

```text
Android Auto
✓ Installed

Head Unit Server
✕ Not running

Decoder
✓ H.264 supported

Audio
✓ Available
```

---

# 49. Head Unit Server停止時

ユーザーに、

```text
Head Unit Serverを起動してください
```

と表示する。

可能であれば適切なAndroid Auto設定画面への遷移を提供する。

非公開APIを利用して設定を強制変更してはならない。

---

# 50. Connection UI

状態表示:

```text
接続準備中

Android Autoへ接続中

Android Autoを起動中

接続済み

再接続中

接続できません
```

AAP内部用語を一般ユーザーへ露出しない。

---

# 51. Retry UI

自動再接続中:

```text
Android Autoへ再接続しています…
```

手動:

```text
[再接続]
```

を提供する。

---

# 52. Settings

必要最低限:

```text
Auto Connect

Keep Screen On

Audio Output

Debug Logging

Projection Resolution
```

PoC由来の技術設定を大量に露出しない。

---

# 53. Fullscreen

Projection中はImmersive Fullscreenを基本とする。

ただしAndroid system gestureを完全に妨害する設計は禁止する。

---

# 54. Screen On

Projection中のみ、

```text
FLAG_KEEP_SCREEN_ON
```

等の適切な仕組みを利用して画面スリープを防止する。

Projection終了後は解除する。

---

# 55. v0.7 Git Branch

```text
feature/onboarding

feature/environment-check

feature/connection-ui

feature/settings

feature/projection-fullscreen
```

---

# 56. v0.8 — Diagnostics

## 56.1 目的

「動かない」場合に原因を追跡可能にする。

---

# 57. Structured Logging

ログcategory:

```text
APP

CONNECTION

TLS

AAP

VIDEO

AUDIO

INPUT

LIFECYCLE

PERFORMANCE
```

---

# 58. Log Level

```text
VERBOSE

DEBUG

INFO

WARN

ERROR
```

Releaseでは不要なVerbose loggingを無効化する。

---

# 59. Session ID

全ログに可能な範囲で、

```text
sessionId
```

を付与する。

例:

```text
session=42
connection state CONNECTING -> TCP_CONNECTED
```

---

# 60. Diagnostic Report

ユーザー操作で診断情報を生成できるようにする。

含めてよい:

```text
App Version

Android Version

Android Auto Version

Device Model

Connection State History

Protocol Error Summary

Decoder Name

Projection Resolution
```

含めてはならない:

```text
Audio Content

Video Content

TLS Secret

Credential

個人情報
```

---

# 61. Crash原因分類

最低限:

```text
Connection

Protocol

Decoder

Audio

Lifecycle

Unknown
```

へ分類可能にする。

---

# 62. v0.8 Git Branch

```text
feature/structured-logging

feature/session-diagnostics

feature/diagnostic-report

test/error-classification
```

---

# 63. Test Infrastructure強化

Unit TestだけでなくIntegration Testを追加する。

---

# 64. Fake Transport

実際のAndroid Autoがなくても、

```text
TCP connect

disconnect

fragmented message

invalid message

timeout
```

を再現可能にする。

---

# 65. Fake AAP Server

可能であればTest用の、

```text
FakeHeadUnitServer
```

または同等fixtureを作成する。

Production sourceへTest専用コードを混入しない。

---

# 66. Fault Injection

以下を再現できるようにする。

```text
Socket disconnect

TLS failure

Malformed packet

Slow connection

Video frame loss

Surface destruction

Audio buffer starvation
```

---

# 67. Regression Test

修正したbugには可能な限り再現テストを追加する。

原則:

```text
Bug
 ↓
Reproduction test
 ↓
Fix
 ↓
Test PASS
```

---

# 68. v0.9 — Release Candidate

機能追加を原則停止する。

対象:

```text
Bug Fix

Compatibility Fix

Performance Fix

Documentation

Test
```

のみ。

---

# 69. Release Build

最低限、

```text
Debug

Release
```

を分離する。

Releaseでは、

```text
Debug UI

Verbose metrics

Test endpoint

Fault injection
```

を無効化する。

---

# 70. R8 / Shrinker

Release buildでR8を使用する場合、AAP reflectionやgenerated protocol classesへの影響を検証する。

単にwarningを消す目的で広範囲の、

```text
-keep class ** { *; }
```

を追加してはならない。

---

# 71. Versioning

Semantic Versioning相当を使用する。

```text
0.x
開発版

1.0.0
初回安定版

1.0.1
Bug Fix

1.1.0
Backward-compatible Feature
```

---

# 72. Version情報

最低限:

```text
versionName

versionCode

Git commit hash
```

を診断情報から確認可能にする。

---

# 73. Security Review

Release前に確認する。

```text
exported component

Intent filter

FileProvider

Network usage

Log data

Stored data

Permissions

Foreground Service
```

---

# 74. Permission最小化

使用していないpermissionはManifestから削除する。

禁止:

```text
将来必要になるかもしれない
```

という理由だけでpermissionを保持する。

---

# 75. Exported Components

外部アプリから呼び出す必要のない、

```text
Activity

Service

Receiver
```

は、

```text
android:exported="false"
```

を基本とする。

---

# 76. Dependency Review

Release前に全dependencyについて確認する。

```text
目的

Version

License

実際に使用されているか
```

不要dependencyを削除する。

---

# 77. OSS License

Open Headunit等からコードを利用している場合、該当license要件をRelease時にも確実に維持する。

LICENSEやNOTICE等を必要に応じて含める。

---

# 78. v0.9 Git運用

Release candidate作業もmainへ直接行わない。

例:

```text
fix/rc-reconnect

fix/rc-surface-recovery

docs/release-notes
```

---

# 79. v1.0 Release Criteria

以下をすべて満たすこと。

## 起動

```text
[ ] App単体で起動

[ ] 環境確認可能

[ ] Head Unit Serverへ接続可能

[ ] Projection自動開始
```

## Projection

```text
[ ] Video安定表示

[ ] Touch操作可能

[ ] Audio再生可能

[ ] Aspect Ratio正常
```

## Reliability

```text
[ ] 一時切断から復旧

[ ] Head Unit Server再起動から復旧

[ ] Activity再生成に耐える

[ ] Surface再生成に耐える

[ ] 連続connect/disconnectに耐える
```

## Performance

```text
[ ] 3時間連続使用でCrashなし

[ ] ANRなし

[ ] Memory Leakなし

[ ] 無制限Buffer増加なし

[ ] 実用上重大なAudio dropなし

[ ] 実用上重大なVideo freezeなし
```

## Quality

```text
[ ] assembleDebug PASS

[ ] assembleRelease PASS

[ ] Unit Test PASS

[ ] Integration Test PASS

[ ] Android Lint PASS

[ ] ktlint PASS

[ ] detekt PASS
```

## Git

```text
[ ] main安定

[ ] main直接実装なし

[ ] Feature/Fix branch運用

[ ] merge後再検証済み

[ ] 不要branch削除済み
```

---

# 80. 次工程のBranchロードマップ

推奨順序:

```text
main
 │
 ├─ feature/connection-state-machine
 │
 ├─ feature/reconnect-policy
 │
 ├─ feature/session-generation
 │
 ├─ feature/session-lifecycle
 │
 ├─ feature/surface-recovery
 │
 ├─ feature/device-capability
 │
 ├─ feature/projection-transform
 │
 ├─ feature/performance-metrics
 │
 ├─ feature/onboarding
 │
 ├─ feature/environment-check
 │
 ├─ feature/structured-logging
 │
 ├─ feature/diagnostic-report
 │
 └─ test/long-running-session
```

各branchは前仕様書と同じQuality Gateを満たしてからmainへmergeする。

---

# 81. AIエージェント標準工程

各branchについて必ず以下を実行する。

```text
git status
 ↓
main確認
 ↓
main Build/Test
 ↓
feature/fix branch作成
 ↓
既存実装調査
 ↓
変更範囲決定
 ↓
Test追加
 ↓
最小実装
 ↓
Build
 ↓
Lint
 ↓
ktlint
 ↓
detekt
 ↓
Test
 ↓
git diff確認
 ↓
Commit
 ↓
main merge
 ↓
main上で全検証
 ↓
branch削除
```

---

# 82. 既存コード保護

前工程で完成済みの、

```text
AAP Session

Video

Touch

Audio
```

を理由なく大規模に書き換えてはならない。

次工程は既存機能の安定化が目的である。

---

# 83. Refactoring条件

大規模refactorは、

```text
明確な問題

Testによる保護

具体的な改善目的
```

がある場合のみ実施する。

「きれいにしたい」という理由だけの大規模refactorは禁止する。

---

# 84. Compatibility Fix規則

特定端末向けfixを、

```text
if (manufacturer == ...)
```

として無秩序に追加してはならない。

必要な場合:

```text
Capability
 ↓
CompatibilityPolicy
 ↓
Behavior
```

として抽象化する。

---

# 85. No Silent Failure

以下は禁止する。

```text
接続失敗
→ 何も表示しない

Video停止
→ 黙って黒画面

Audio停止
→ 状態を保持したまま
```

内部状態とUI状態を一致させる。

---

# 86. No Infinite Recovery

Recoveryが永遠に続く状態を作らない。

一定条件で、

```text
Recovering
 ↓
User Action Required
```

へ遷移可能にする。

---

# 87. DebugとProductionの分離

Debug機能は、

```text
BuildConfig.DEBUG
```

または専用build variant等で制御する。

Production behaviorにDebug hackを残さない。

---

# 88. 次工程で実装しないもの

本仕様書では以下を対象外とする。

```text
USB Android Auto Receiver

他Android端末とのWireless Projection

CarPlay

Android Automotive OS化

CAN接続

OBD-II

車速連携

ステアリングリモコン

HUD

外部GPS

root専用機能

system app化
```

これらはv1.0以降の独立仕様とする。

---

# 89. v1.0 Definition of Done

v1.0は、

> Android Auto画面が表示された

だけでは完成としない。

以下を満たして初めて完成とする。

```text
起動可能

接続可能

操作可能

音声再生可能

切断から復旧可能

Lifecycleに耐える

複数端末で検証済み

長時間動作可能

原因追跡可能

Release Build可能

Test可能

mainが安定状態
```

---

# 90. AIエージェントへの最終指示

次工程で最も避けるべきものは、

```text
Feature Creep

巨大Refactor

端末固有Hackの乱立

無限Retry

Lifecycle無視

Resource Leak

Silent Failure

Testなし修正
```

である。

AIエージェントは、

```text
Observe
 ↓
Reproduce
 ↓
Test
 ↓
Fix
 ↓
Verify
```

を基本原則とする。

本工程の目的は、

**「Android Autoが動くアプリ」から「Android Autoを安定して使い続けられるアプリ」へ移行することである。**