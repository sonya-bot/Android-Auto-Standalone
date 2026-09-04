# Android Auto ヘッドユニットサーバー自動起動 実装計画

アプリアイコン押下時に、Android Autoのデベロッパー／ヘッドユニットサーバーが停止していた場合でも自動的にサーバーを起動し、ユーザーが手動操作なしでそのまま利用できるようにします。

## ユーザー確認事項
- **ユーザー補助（AccessibilityService）の有効化**:
  - Android OSのセキュリティ制限により、他社アプリ（Gearhead）の非公開サービスを直接バックグラウンドから起動することは禁止されているため、AccessibilityServiceを活用して設定画面の起動操作を自動実行します。
  - 実機検証環境においては、ADBコマンド（`settings put secure enabled_accessibility_services`）により自動でユーザー補助権限を有効化します。
  - 通常利用時も、初回のみ案内ダイアログから「設定」を開いて一度有効化すれば、以後は一切の手動操作が不要となります。

## 変更内容

### 1. アクセシビリティサービスの実装
#### [NEW] [`AutoStartAccessibilityService.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/service/AutoStartAccessibilityService.kt)
- `AccessibilityService` を継承。
- `com.google.android.projection.gearhead` の画面遷移イベント（`TYPE_WINDOW_STATE_CHANGED`, `TYPE_WINDOW_CONTENT_CHANGED`）を監視。
- 自動起動トリガーフラグが有効な場合：
  1. 「その他のオプション（More options）」ボタン（オーバーフローメニュー）を検索し、クリック（`ACTION_CLICK`）。
  2. ポップアップメニュー内の「ヘッドユニット サーバーを開始（Start head unit server）」を検索し、クリック。
  3. クリック成功後、自動的に本アプリ（`MainActivity`）を最前面へ呼び戻す Intent を発行し、トリガーフラグをクリア。

#### [NEW] [`accessibility_service_config.xml`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/res/xml/accessibility_service_config.xml)
- パッケージ対象: `com.google.android.projection.gearhead`
- イベントタイプ: `typeWindowStateChanged|typeWindowContentChanged`
- フラグ: `flagRetrieveInteractiveWindows|flagIncludeNotImportantViews`

### 2. マニフェストおよびリソースの更新
#### [MODIFY] [`AndroidManifest.xml`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/AndroidManifest.xml)
- `AutoStartAccessibilityService` の登録（`android.permission.BIND_ACCESSIBILITY_SERVICE`、メタデータ定義）。
- Android Auto 設定画面へ Intent を発行するための `<queries>` 定義。

#### [MODIFY] [`strings.xml`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/res/values/strings.xml)
- サーバー自動起動中や、初回ユーザー補助案内用の文言を追加。

### 3. アプリ起動フローの統合
#### [MODIFY] [`MainActivity.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/ui/MainActivity.kt)
- 接続試行時にポート 5277 への接続が失敗（またはタイムアウト）した場合：
  1. アクセシビリティサービスが有効かチェック。
  2. 有効な場合:
     - `AutoStartAccessibilityService.armAutoStart()` を呼び出し。
     - Android Auto 設定画面（`DefaultSettingsActivity`）を起動。
     - ステータス表示に「Android Auto サーバーを自動起動中…」と表示し、フォールバックタイマー（数秒後に自動リトライ）を設定。
  3. 未有効な場合（初回等）:
     - ユーザー補助設定の許可を促すガイダンスダイアログを表示し、設定画面へワンタップで遷移可能にする。

---

## 検証手順

### 自動テスト
- `./gradlew test` を実行し、既存テストにデグレがないことを確認。

### 実機検証
1. Xperia実機にてビルド・インストールを実施。
2. ADBでAccessibilityServiceを有効化:
   `adb shell settings put secure enabled_accessibility_services com.example.androidautoselfheadunit/.service.AutoStartAccessibilityService:...`
3. テストのため、Android Autoのヘッドユニットサーバーを一旦停止。
4. ホーム画面から本アプリ（`MainActivity`）を起動。
5. 設定画面が一瞬表示され、自動でメニュー操作が行われた後、本アプリへ自動復帰し、正常にAndroid Auto投影画面が表示されることを確認。
