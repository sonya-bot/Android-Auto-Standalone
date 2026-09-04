# タッチ入力の座標ズレ解消と画面上ボタンの正常化

## 概要
画面上の一部のボタン（ドック内のアプリ一覧ボタンやメディア操作ボタンなど）が反応しない、または意図と異なる動作をする不具合を解消します。

---

## 根本原因の特定

1. **Android Autoの仮想ディスプレイ解像度とプロトコル仕様**
   - 1080p（1920x1080）の投影において、端末の21:9アスペクト比に合わせてマージン（`marginHeightPx = 258`）を設定しているため、Android Auto（Gearhead）が生成する投影用仮想ディスプレイの有効領域は **1920 x 822**（`contentWidthPx x contentHeightPx`）です。
   - Android Autoの仮想ディスプレイ上のウィンドウ配置やタッチ受付領域（TouchableRegion）は **(0, 0) 〜 (1920, 822)** となっています。

2. **TouchConfig と TouchEventMapper の不整合**
   - `ControlChannel.kt` の `TouchConfig` で解像度を `1920 x 1080`（`widthPx x heightPx`）として報告していました。
   - `TouchEventMapper` も `projectionWidth = 1920, projectionHeight = 1080` で初期化されていました。

3. **SurfaceView のクロップオフセットによる +129px の下方向シフト**
   - `MainActivity.kt` では `SurfaceView`（2560x1440）が `FrameLayout`（2560x1096）の中央に配置され、上下172px（動画内マージン相当：129px）が画面外へクロップされています。
   - `surfaceView.setOnTouchListener` が受け取る座標は `SurfaceView` ローカル（画面上端タップ時 `y = 172`）であり、これをそのまま 1440 で割って 1080 に変換していたため、送信される Y 座標は **129 〜 951** となっていました。
   - **影響:**
     - 全てのタップが **129px 下側にズレて認識** されていました（ボタンを押したつもりがその下の要素が反応）。
     - 画面下部（Y > 924）をタップすると送信 Y 座標が **822（仮想ディスプレイの上限）を超えて画面外となり、Android Auto 側で完全に破棄** されていました（ドックのアプリ一覧ボタンや一時停止ボタン等が無反応となっていた原因）。

---

## 変更内容（Proposed Changes）

### 1. タッチ座標マッパーの修正

#### [MODIFY] [`TouchEventMapper.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/input/TouchEventMapper.kt)
- 投影先の基準寸法を `contentWidthPx` / `contentHeightPx` に変更。
- ビューポート寸法（`viewportWidth`, `viewportHeight`）および `SurfaceView` の配置オフセット（`surfaceLeft`, `surfaceTop`）を考慮した幾何変換メソッド `updateGeometry(...)` を追加。
- 計算式:
  - `viewportX = x + surfaceLeft`
  - `mappedX = (viewportX * contentWidthPx / viewportWidth).coerceIn(0, contentWidthPx - 1)`
  - `viewportY = y + surfaceTop`
  - `mappedY = (viewportY * contentHeightPx / viewportHeight).coerceIn(0, contentHeightPx - 1)`

### 2. プロトコル設定とサービス層の連携

#### [MODIFY] [`ControlChannel.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/aap/ControlChannel.kt)
- `TouchConfig` の `width`, `height` を `displayProfile.contentWidthPx`, `displayProfile.contentHeightPx` に設定。

#### [MODIFY] [`HeadUnitService.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/service/HeadUnitService.kt)
- `touchMapper` 初期化時に `profile.contentWidthPx` と `profile.contentHeightPx` を渡す。
- `sendTouchEvent` に `viewportWidth`, `viewportHeight`, `surfaceLeft`, `surfaceTop` を受け渡すシグネチャを追加。

### 3. UI層からの正確な配置情報の受け渡し

#### [MODIFY] [`MainActivity.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/ui/MainActivity.kt)
- `SurfaceView` のタッチリスナーから親コンテナ（`FrameLayout`）のビューポート寸法、および `surfaceView.left`, `surfaceView.top` を取得してサービスへ渡す。

### 4. 単体テストの更新・拡充

#### [MODIFY] [`TouchEventMapperTest.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/test/java/com/example/androidautoselfheadunit/input/TouchEventMapperTest.kt)
- クロップオフセットがある場合でも `0 〜 contentWidthPx - 1` および `0 〜 contentHeightPx - 1` に正確に正規化されることを検証するテストケースを追加。

---

## 検証手順（Verification Plan）

### 自動テスト
- `./gradlew test` を実行し、全テスト（`TouchEventMapperTest` を含む）が成功することを確認。

### 実機検証
1. `./gradlew assembleDebug` でビルドし、実機にインストール。
2. アプリを起動し Android Auto の画面を投影。
3. `adb shell input tap` および画面のタッチ操作により以下を検証:
   - **画面右下ドックのアプリランチャー（9個の点）アイコンタップ** → アプリ一覧ドロワーが正常に開くこと。
   - **Spotify の再生/一時停止ボタンタップ** → 曲の再生/停止がトグルすること。
   - **ドックの各アプリアイコンタップ** → 押した通りのアプリに切り替わること。
   - **Google Maps の検索バー・操作ボタン** → タップした位置が正確に反応すること。
4. スクリーンショットを取得し、状態遷移を視覚的に確認。
