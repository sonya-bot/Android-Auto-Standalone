# 有線オーディオ出力・抜去時安定性・自動起動/終了の修正計画

## 問題の概要と原因分析

1. **有線デバイス接続時に音声が再生されない問題**
   - **原因**: `dumpsys audio` の確認により、`STREAM_MUSIC` において `headphone (device 8)` の音量インデックスが **0**（消音状態）になっていました。Android では出力デバイスごとに音量が独立して保持されるため、有線イヤホン/AUXを挿入してルーティングが切り替わっても音量が 0 のままで無音となっていました。
2. **有線デバイス取り外し時に本体スピーカーからの音声出力が不安定になる問題**
   - **原因**: プラグ抜去時に `AudioDeviceCallback` と `headsetReceiver` から約100msの間に5回以上のイベントが連続して発火していました。`handleDeviceRoutingChange()` が呼び出されるたびに `stop()` と `start()`（AudioTrack の破棄と再生成）が激しく実行され、並行して実行中の音声データ書き込み（`AudioTrack.write`）と衝突して `-6` (`ERROR_DEAD_OBJECT`) や AudioFlinger のクラッシュ・音途切れ・不安定化を引き起こしていました。
3. **自動起動/終了エラーの再発問題**
   - **原因1 (サービス停止)**: Android 15 ではバックグラウンドサービスが「app idle」により約60秒で OS によって強制停止されます（`ActivityManager: Stopping service due to app idle: ... HeadUnitService`）。`HeadUnitService` が Foreground Service 化されていないため、接続維持やタスク終了時のクリーンアップ前に強制終了されていました。
   - **原因2 (自動停止のタイミング)**: タスクキル時（`onTaskRemoved`）に `stopSelf()` が即座に同期実行されるため、ユーザー補助サービスが Gearhead の停止処理を完了する前にプロセスが破棄されることがありました。
   - **原因3 (自動起動のリトライ上限)**: Gearhead 設定画面でサーバー起動を押した後、MainActivity 復帰時にリトライカウントがリセットされず、サーバーのポートバインド完了前にエラーダイアログが表示されるケースがありました。

---

## 修正内容

### 1. 音声ルーティングと音量管理の刷新 (`AudioTrackWrapper.kt`)
- **デバウンス処理の導入**:
  - ハードウェアの抜き差し時に連続発火するイベントを、Main Handler を用いて 250ms のデバウンスを行います。
- **同一デバイスへの不要な再生成の防止**:
  - `currentTargetDeviceId` を保持し、接続デバイスが変わっていない場合は `stop()` / `start()` をスキップします。
- **有線/外部デバイス接続時の音量自動補正**:
  - デバイス切り替え時に `am.getStreamVolume(STREAM_MUSIC)` を確認し、音量が 0 またはミュート状態であれば、安全かつ十分な音量（最大音量の 50% 程度）に自動設定し、ミュートを解除します。
- **シームレスなルーティング切り替え**:
  - 原則として `audioTrack?.setPreferredDevice(target)` を利用し、再生中のストリームを不要に破棄しないことで抜去時の音途切れやノイズ・クラッシュを防ぎます。

### 2. `HeadUnitService` のフォアグラウンドサービス化 (`HeadUnitService.kt`, `AndroidManifest.xml`)
- `AndroidManifest.xml` に `FOREGROUND_SERVICE` および `FOREGROUND_SERVICE_CONNECTED_DEVICE` パーミッションを追加。
- `HeadUnitService` に通知チャンネルを作成し、常駐通知（"Android Auto Head Unit 実行中"）とともに `startForeground()` を呼び出すことで、Android 15 による「app idle」強制終了を完全に防ぎます。
- `onTaskRemoved` で `AutoStartAccessibilityService.stopServerAutomatically { stopSelf() }` とし、サーバー停止処理が完了（またはタイムアウト）してからサービスを破棄します。

### 3. 自動起動・終了処理の安定化 (`AutoStartAccessibilityService.kt`, `MainActivity.kt`)
- **`MainActivity.kt`**:
  - `onNewIntent`（Gearhead から戻った際）で `retryCount = 0` にリセットし、リトライ上限を 10 回（約15秒）に拡張して、サーバー起動待機中の誤ったエラーダイアログ表示を防止。
  - `onDestroy` 時に `unbindService` を適切に呼び出してサービスコネクションのリークを解消。
- **`AutoStartAccessibilityService.kt`**:
  - `stopServerAutomatically` に 4 秒のフォールバックタイマーを設置し、Gearhead 画面の応答に関わらず必ずコールバックが実行されリソースが解放されるように改善。

---

## 変更対象ファイル

- [MODIFY] `app/src/main/AndroidManifest.xml`
- [MODIFY] `app/src/main/java/com/example/androidautoselfheadunit/audio/AudioTrackWrapper.kt`
- [MODIFY] `app/src/main/java/com/example/androidautoselfheadunit/service/HeadUnitService.kt`
- [MODIFY] `app/src/main/java/com/example/androidautoselfheadunit/service/AutoStartAccessibilityService.kt`
- [MODIFY] `app/src/main/java/com/example/androidautoselfheadunit/ui/MainActivity.kt`

---

## 検証手順

1. **ビルドとユニットテスト**:
   - `./gradlew testDebugUnitTest assembleDebug` でコンパイルとテスト通過を確認。
2. **実機デプロイと動作確認**:
   - `adb install -r app/build/outputs/apk/debug/app-debug.apk` で Xperia SOG10 にデプロイ。
   - アプリ起動時の自動サーバー起動がエラーなく動作することを確認。
3. **有線オーディオ接続・抜去テスト**:
   - 有線デバイス（イヤホン/AUX）を接続し、音量が自動復帰して有線から正常に出力されることを確認。
   - 有線デバイスを抜去した際、デバウンスにより本体スピーカーへ安定して切り替わり、音飛びやクラッシュが発生しないことを確認。
4. **アプリ終了・タスククリアテスト**:
   - Back キーおよびタスク画面（Recents）からのスワイプ終了を実行し、Gearhead サーバーが正常に自動停止されることを確認。
