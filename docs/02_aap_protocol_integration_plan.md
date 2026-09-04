# AAPプロトコル本実装（モック解除）と映像投影の確立

前仕様書（v0.2）における仮実装（モック）を外し、実機の Android Auto サーバーと正式に暗号化通信を行い、プロトコル仕様に沿って映像のプロジェクションを開始させるための実装計画です。

## User Review Required

> [!WARNING]
> このフェーズはアーキテクチャの根幹に関わる大規模なアップデートとなります。
> Protobufの導入、SSL暗号化/復号の本格稼働、チャネル確立の完全シーケンスを実装します。

## 変更内容（Proposed Changes）

### 1. Protobuf (Protocol Buffers) の導入
AAPのメッセージ形式はProtobufで定義されています。依存関係を追加し、生成済みのJavaクラス群をプロジェクトに組み込みます。

#### [MODIFY] app/build.gradle.kts
- `com.google.protobuf:protobuf-javalite:3.21.12` を追加

#### [NEW] app/src/main/java/com/example/androidautoselfheadunit/aap/protocol/proto/*
- Open Headunit プロジェクトから AAP プロトコル定義のJavaクラス（Common, Control, Media, Sensors 等）をコピー配置。

### 2. AAP メッセージクラスと暗号化（SSL）の統合
生データではなく、正式な AAP ヘッダー（Channel ID, Flags, Length）をパースし、SSLEngine でペイロードの暗号化・復号を行う機構を実装します。

#### [NEW] app/src/main/java/com/example/androidautoselfheadunit/aap/AapMessage.kt
- AAP ヘッダーとペイロードを保持するデータクラス

#### [MODIFY] app/src/main/java/com/example/androidautoselfheadunit/aap/security/AapSslContext.kt
- `encrypt()` および `decrypt()` メソッドを追加し、SSLEngine を用いてパケット単位の暗号化・復号を処理

#### [MODIFY] app/src/main/java/com/example/androidautoselfheadunit/aap/AapTransport.kt
- `receiveEncrypted()`: ソケットから4バイトのヘッダーを読み取り、ペイロードサイズを特定した上で暗号化データを読み込み、`AapSslContext` で復号して `AapMessage` を返すように変更。
- `sendEncrypted()`: Protobuf メッセージを受け取り、AAPヘッダーを付与して暗号化したのちソケットに書き込むように変更。

### 3. コントロールチャネルとチャネル確立シーケンス
スマートフォン側の Android Auto からの要求に応答し、各種チャネル（映像・音声など）をネゴシエーションするシーケンスを実装します。

#### [MODIFY] app/src/main/java/com/example/androidautoselfheadunit/aap/ControlChannel.kt
- **Service Discovery**: スマートフォンからの `ServiceDiscoveryRequest` を受信後、本アプリがサポートする機能（H.264 映像、オーディオ、タッチ入力など）を定義した `ServiceDiscoveryResponse` を構築して返信。
- **Channel Open**: 各チャネルの `ChannelOpenRequest` を処理。
- **Video Focus**: 映像送信を開始させるための `VideoFocusRequest` を送信。

#### [MODIFY] app/src/main/java/com/example/androidautoselfheadunit/ui/MainActivity.kt
- モック用のダミーループを廃止し、正式な `AapTransport` ループからメッセージを各チャネル（`ControlChannel`, `VideoChannel`, `AudioChannel`）にルーティングする処理を組み込み。

## Verification Plan

### Automated Tests
- 新規追加したProtobufメッセージのビルドが通るか確認
- ktlint, detekt によるコードフォーマット・静的解析の検査

### Manual Verification
1. アプリを実機にインストールし、Android Autoの「ヘッドユニットサーバーを起動」を有効にした状態で起動する。
2. TCP接続完了後、エラーダイアログが出ず、**Android Autoのホーム画面（またはマップ等）が実機の画面上に表示されること** を確認する。
