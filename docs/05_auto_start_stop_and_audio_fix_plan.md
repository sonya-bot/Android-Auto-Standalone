# 起動・終了時ヘッドユニットサーバー自動処理および音声出力エラーの修正計画

## 概要
直前のコミット（オープニング動画追加）以降に発生している以下の不具合について、実機ログおよびソースコード解析により根本原因を特定しました。それぞれの原因を解消し、安定した自動起動・終了および途切れのない高音質音声出力を実現します。

1. **起動時の自動処理失敗・「ユーザー補助の許可が必要です」ダイアログ誤表示**
2. **終了時のヘッドユニットサーバー自動停止の失敗**
3. **音声出力エラー（MediaPlayer による AudioTrack/AudioFlinger 競合および `dead IAudioTrack`）**

---

## 原因の特定

### 1. サーバー自動起動・終了処理の失敗原因
- **`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` の読み取り制限:**
  [`AutoStartAccessibilityService.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/service/AutoStartAccessibilityService.kt) の `isServiceEnabled()` で `Settings.Secure.getString(...)` を使用していますが、Android 13/14/15 では一般アプリからのこのキーの読み取りが `null` を返すか制限されます。
- **インスタンス未バインド時の即時エラー判定:**
  アプリアイコンタップ直後は `AutoStartAccessibilityService` の `onServiceConnected()` が非同期でバインドされるため、起動直後の一瞬 `instance == null` になります。その際 `isServiceEnabled()` が誤って `false` を返し、MainActivity が「ユーザー補助の許可が必要です」ダイアログを表示して自動起動シーケンスをブロックしていました。
- **停止処理のスキップ:**
  終了時（戻る操作、タスク画面クリア時）にも `isServiceEnabled()` が呼ばれますが、これが `false` を返すため `stopServerAutomatically()` 内で `Log.w("Accessibility service instance not available...")` となり、サーバー停止処理がスキップされていました。

### 2. 音声出力エラーの原因
- **オープニング動画の音声トラックと `AudioTrack` の競合:**
  `opening_copen.mp4` に無音の AAC 音声トラック（48kHz stereo）が含まれており、`MainActivity` の `MediaPlayer` がシステム音楽ストリーム（`USAGE_MEDIA`）を開いていました。
- **`MediaPlayer.release()` による `dead IAudioTrack`:**
  Android Auto 接続時に `HeadUnitService` が 48kHz stereo の `AudioTrack` を初期化しますが、オープニング動画終了時に `MediaPlayer.release()` が実行されると、AudioFlinger 内のオーディオセッション破棄に伴い `dead IAudioTrack`（ログ確認済み: `restoreTrack_l(276): dead IAudioTrack`）が発生し、その後のストリーミング音声出力が遮断・停止する事象を引き起こしていました。
- **`AudioTrackWrapper` の復帰耐性:**
  `AudioTrack.write()` で `ERROR_DEAD_OBJECT` 等のエラーが発生した際、トラックの自動再生成・復帰ロジックが存在しないため、一度死んだオーディオトラックから音が再生されなくなっていました。

---

## 提案する変更内容

### [サービス・権限判定]
#### [MODIFY] [`AutoStartAccessibilityService.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/service/AutoStartAccessibilityService.kt)
- `isServiceEnabled(context)` を標準 API である `AccessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)` を使用するように改修。
- サービス自身（`this`）が動作中の場合は常に有効と判定。
- `stopServerAutomatically()` での二重判定を緩和し、`instance` が存在すれば確実に停止シーケンスを実行。

### [ライフサイクル & UI]
#### [MODIFY] [`MainActivity.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/ui/MainActivity.kt)
- 起動時の自動起動フロー開始時、アクセシビリティサービスがバインド完了するまでのわずかな待機（リトライ）を導入し、「許可ダイアログ」が誤表示されるのを防止。
- `MediaPlayer` に `setVolume(0f, 0f)` を設定し、オーディオ出力を一切行わないよう明示。
- オープニング動画完了と Android Auto 投影開始のフェード遷移をより確実に連携。

### [音声パイプライン]
#### [MODIFY] [`AudioTrackWrapper.kt`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/java/com/example/androidautoselfheadunit/audio/AudioTrackWrapper.kt)
- `write()` でのエラー検知時（`ERROR_DEAD_OBJECT`, `ERROR_INVALID_OPERATION` 等、負値返却時）に、自動的にトラックを再初期化（リスタート）して書き込みを再試行する自己回復ロジックを実装。
- 音声再生中にシステムや別アプリによるオーディオルーティング変更や破棄が発生しても、瞬時に復旧して音が途切れないように強化。

#### [MODIFY] [`opening_copen.mp4`](file:///Users/Souma/Develop/Private%20Project/Android%20Auto/app/src/main/res/raw/opening_copen.mp4)
- `ffmpeg -an` を用いて音声トラックを完全除去した純粋なビデオストリーム（映像のみ）に更新。システムオーディオセッションへの干渉を物理的にゼロにします。

---

## 検証計画

### 実機検証（Sony Xperia SOG10, Android 15）
1. **起動テスト:**
   - アプリアイコンからコールドスタート。
   - オープニング動画がスムーズに再生され、途中で「ユーザー補助許可ダイアログ」が表示されることなく、自動的にヘッドユニットサーバーが起動してフルスクリーン投影に遷移することを確認。
2. **音声出力テスト:**
   - Spotify 再生を開始し、端末スピーカーからクリアに音声が出力され続けることを確認。
   - 数十秒間連続再生し、途中で一時停止や `dead IAudioTrack` による無音化が発生しないことを確認。
3. **終了テスト:**
   - 戻るボタン/ジェスチャー、およびタスク画面（Recents）からのスワイプ/「すべてクリア」を実行。
   - バックグラウンドでヘッドユニットサーバーが自動停止し、ポート 5277 が解放され、通知バーの常駐通知が消えることを確認。
