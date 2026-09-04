# Phase 0: Project Foundation Implementation Plan

## Goal
Android Auto Self Head Unit App の初期プロジェクトを作成し、安定したビルド基盤（Project Foundation）を構築します。仕様書に記載されている「Phase 0 — Project Foundation」に該当します。

## User Review Required
> [!IMPORTANT]
> 仕様書にある Task 1 〜 Task 10 の確認作業は完了しており、Task 11 として本計画を提示しています。
> 内容をご確認いただき、承認いただければ Task 12（`feature/project-foundation`からの実装開始）へ進みます。
> Open Headunit のリファレンス実装も確認済みであり、段階的に必要な機能を移植していく方針です。

## Open Questions
> [!NOTE]
> 特にございません。仕様書に従って進めます。

## Proposed Changes

### Project Structure
以下のファイルと設定を含む新規 Android プロジェクトを作成します。

#### [NEW] `settings.gradle.kts`
プロジェクトの設定、および `gradle/libs.versions.toml` の読み込み設定を行います。

#### [NEW] `gradle/libs.versions.toml`
Version Catalog として、以下を管理します。
- `minSdk = 30`
- `compileSdk = 34` (最新安定版)
- `targetSdk = 34` (最新安定版)
- Kotlin / Android Gradle Plugin / ktlint / detekt などの依存ライブラリとバージョン。

#### [NEW] `build.gradle.kts` (Project level)
プロジェクト全体のビルド設定および Plugin 定義（ktlint, detekt など）を行います。

#### [NEW] `app/build.gradle.kts` (App level)
Android アプリケーション固有の設定（namespace, SdkVersion, dependencies）を行います。

#### [NEW] `.editorconfig`
Android Kotlin Style Guide に準拠したインデント等のフォーマット設定を行います。

#### [NEW] `.gitignore`
Android 開発標準の無視ファイルを定義します。

#### [NEW] `app/src/main/AndroidManifest.xml`
ベースとなるマニフェストファイルです。

#### [NEW] `app/src/main/java/com/example/androidautoselfheadunit/ui/MainActivity.kt`
動作確認用の空の Activity です。（パッケージ名は仮です。適宜設定します）

## Verification Plan

### Automated Tests
- `./gradlew assembleDebug` が成功すること
- `./gradlew test` (もしあれば) が成功すること
- `./gradlew lint` が成功すること
- `ktlint` のチェックが成功すること
- `detekt` のチェックが成功すること

### Manual Verification
- 新規リポジトリで初期コミットとして `main` に安定した基盤が提供されていることを確認します。