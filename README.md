# Android Auto Standalone

Android端末単体で完結するスタンドアロン型 Android Auto レシーバーアプリケーション。

かつて公式に提供されていた「Android Auto for phone screens」のように、車載ディスプレイや外部ハードウェアを介さず、Android端末の画面上で直接 Android Auto の車載UI・ナビゲーション・メディア操作を行うことができます。

---

## 概要

本アプリは、同一Android端末内で動作する「Android Auto 開発者向けヘッドユニットサーバー」（`127.0.0.1:5277`）とローカルループバック接続し、端末自身を Android Auto の Head Unit（受信機・ディスプレイ）として動作させます。

```text
+--------------------------------------------------------+
| Android 端末 (スマホ / タブレット)                       |
|                                                        |
|  +--------------------------+                          |
|  | Android Auto (Google公式) |                          |
|  | Head Unit Server         |                          |
|  | (127.0.0.1:5277)         |                          |
|  +------------+-------------+                          |
|               | AAP (ローカルソケット通信)               |
|  +------------v-------------+                          |
|  | Android Auto Standalone  | (本アプリ)               |
|  | - Video (H.264 デコード)  |                          |
|  | - Audio (動的ルーティング)|                          |
|  | - Touch (入力マッピング)  |                          |
|  +--------------------------+                          |
+--------------------------------------------------------+
```

---

## 主な機能と特徴

- 📱 **完全スタンドアロン動作**
  - 車載ディスプレイ、外部中継ドングル、PC、USBケーブルなどの接続は一切不要です。
- ⚡ **全自動起動・サーバー管理**
  - ユーザー補助サービス（`AutoStartAccessibilityService`）を搭載。
  - アプリアイコンをタップするだけで、バックグラウンドのヘッドユニットサーバーの起動確認・自動立ち上げを行い、数秒でAndroid Auto画面を展開します。
  - アプリ終了時・タスククリア時には、サーバーを自動停止して無駄なバッテリー消費を防ぎます。
- 🎯 **ウルトラワイド・高精度タッチマッピング**
  - 21:9画面（Xperia等）をはじめとする多様なアスペクト比に対応。
  - アスペクト比補正に伴う画面下部ボタンドックのタッチ座標ズレを自動補正し、快適なタッチ操作を実現。
- 🔊 **高度なオーディオルーティング**
  - スマホ内蔵スピーカー、有線イヤホン/AUX、Bluetoothオーディオ、USB DACへの動的切り替えに対応。
  - Android Autoループバック干渉による無音化を回避（`USAGE_UNKNOWN`適用）し、音量自動補正・デバウンス制御を実装。
- 🛡️ **モダンAndroid対応と安定性**
  - Android 14 / 15 の最新バックグラウンド制約に対応。`HeadUnitService` を Foreground Service（`connectedDevice`）として稼働させ、OSによるプロセス停止を防止。

---

## 動作要件

- **OS**: Android 11（API Level 30）以上（Android 15 動作確認済み）
- **必須アプリ**: Google公式「Android Auto」が端末にインストールされていること

---

## セットアップ手順

### 1. Android Auto 開発者向けオプションの有効化（初回のみ）
1. 端末の「設定」>「接続済みのデバイス」>「接続の設定」>「Android Auto」を開きます。
2. 画面最下部までスクロールし、「バージョン」を **10回連続タップ** します。
3. 「開発者向けの設定を有効にしますか？」で「OK」を選択します。

### 2. ユーザー補助の許可（全自動起動用・初回のみ）
1. 本アプリ（Android Auto Standalone）を起動します。
2. 初回起動時に「ユーザー補助の許可」ダイアログが表示されます。
3. 画面の案内に従って設定画面を開き、「Android Auto Standalone」のユーザー補助を **ON** にします。

> [!TIP]
> ユーザー補助をONにすることで、次回以降アプリアイコンをタップするだけでヘッドユニットサーバーが自動起動し、完全ハンズフリーでAndroid Autoが立ち上がります。

---

## 使い方

1. ホーム画面から **「Android Auto Standalone」** アイコンをタップします。
2. 自動的にヘッドユニットサーバーが立ち上がり、Android Autoの画面が表示されます。
3. 終了する際は、戻るボタンまたはホームに戻り、タスク一覧からアプリを閉じることでサーバーも自動停止します。

---

## プロジェクト構造 & 設計ドキュメント

詳細な設計思想、各フェーズの実装計画、不具合修正の経緯は [`docs/`](./docs) ディレクトリにすべて保管されています。

- 📂 [設計・実装計画ドキュメント一覧 (`docs/README.md`)](./docs/README.md)
  - [`01_project_foundation_plan.md`](./docs/01_project_foundation_plan.md) - プロジェクト基盤・ビルド設定
  - [`02_aap_protocol_integration_plan.md`](./docs/02_aap_protocol_integration_plan.md) - AAP（Android Auto Protocol）通信・各チャネル設計
  - [`03_touch_coordinate_mapping_fix_plan.md`](./docs/03_touch_coordinate_mapping_fix_plan.md) - 21:9画面クロップ時のタッチ座標補正
  - [`04_headunit_server_auto_start_plan.md`](./docs/04_headunit_server_auto_start_plan.md) - ユーザー補助サービスによるサーバー自動起動
  - [`05_auto_start_stop_and_audio_fix_plan.md`](./docs/05_auto_start_stop_and_audio_fix_plan.md) - サーバー自動停止・音声干渉回避
  - [`06_wired_audio_volume_and_service_stability_plan.md`](./docs/06_wired_audio_volume_and_service_stability_plan.md) - 有線音量補正・Foreground Service安定化

---

## 免責事項 / 商標について

- Android および Android Auto は Google LLC の商標です。
- 本プロジェクトはオープンソースの個人開発プロジェクトであり、Google LLC との直接の提携・公認・後援関係はありません。