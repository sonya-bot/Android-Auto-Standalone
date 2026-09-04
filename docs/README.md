# Android Auto Self Head Unit - 設計・実装・修正計画ドキュメント一覧

本ディレクトリには、Android Auto Self Head Unit アプリケーションの開発初期から現在に至るまでの各フェーズの実装計画および不具合修正計画書が時系列順に個別保存されています。

---

## ドキュメント構成

| ファイル名 | 分類 | 概要 |
| :--- | :--- | :--- |
| [`01_project_foundation_plan.md`](./01_project_foundation_plan.md) | 基本設計 | プロジェクト基盤の構築、Gradle設定、初期アーキテクチャ設計 |
| [`02_aap_protocol_integration_plan.md`](./02_aap_protocol_integration_plan.md) | 機能実装 | AAP（Android Auto Protocol）統合、TCPソケット通信、SSL/TLSハンドシェイク、各チャネル（Control, Video, Audio, Input）設計 |
| [`03_touch_coordinate_mapping_fix_plan.md`](./03_touch_coordinate_mapping_fix_plan.md) | 不具合修正 | 21:9画面クロップに伴うタッチ座標のズレ（129px下方向シフト）解消と画面下部ボタンドックのタップ正常化 |
| [`04_headunit_server_auto_start_plan.md`](./04_headunit_server_auto_start_plan.md) | 機能実装 | ユーザー補助サービス（`AutoStartAccessibilityService`）を用いたAndroid Auto開発者向けヘッドユニットサーバーの自動起動 |
| [`05_auto_start_stop_and_audio_fix_plan.md`](./05_auto_start_stop_and_audio_fix_plan.md) | 機能・修正 | アプリ終了時・タスククリア時のサーバー自動停止、音声ループバック干渉回避（`USAGE_UNKNOWN` 適用） |
| [`06_wired_audio_volume_and_service_stability_plan.md`](./06_wired_audio_volume_and_service_stability_plan.md) | 不具合修正 | 有線デバイス接続時の無音解消（音量0の自動補正）、プラグ抜去時の250msデバウンス、Android 15での強制終了防止（`HeadUnitService` のForeground Service化） |
