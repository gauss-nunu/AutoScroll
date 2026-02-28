# 開発ナレッジ: AutoScrollデバッグの記録

このドキュメントは、AutoScrollアプリの開発およびデバッグ過程で発生した問題とその解決策を記録し、将来の参照のためにナレッジとして蓄積するものです。

---

## 発生した事象

ユーザーからの報告: 「実機（AQUOS sense 10, API 26）でアプリを起動し、フローティングボタンを表示させようとすると、アプリが固まってクラッシュする」

## デバッグの過程と原因分析

### ❌ [誤] 最初の仮説: `NotificationChannel` のバージョン問題

- **現象:** 静的コードレビューの段階で、`FloatingButtonService` 内の `createNotificationChannel()` メソッドにAndroidバージョンの分岐がないことが確認された。
- **仮説:** `NotificationChannel` はAPIレベル26で導入されたAPIであり、導入初期のOSバージョンでは実装が不安定な可能性があるため、これがクラッシュの原因ではないかと考えた。
- **結果:** この仮説に基づいてバージョンチェックの修正を行ったが、クラッシュは解決しなかった。**この仮説は完全な誤りだった。**

### ❌ [誤] 第二の仮説: `FOREGROUND_SERVICE_SPECIAL_USE` 権限の欠如

- **現象:** `logcat` を確認したところ、ログの前半で `SecurityException: Starting FGS with type specialUse ... requires permissions: ... [android.permission.FOREGROUND_SERVICE_SPECIAL_USE]` という例外が確認された。
- **仮説:** `AndroidManifest.xml` に `FOREGROUND_SERVICE_SPECIAL_USE` 権限が不足していることが原因であると判断した。
- **結果:** この仮説に基づいて権限を追加する修正を行ったが、根本的な解決には至らなかった。`logcat` のログを最後まで注意深く確認しなかったため、本当の原因を見落としていた。

### ✅ [正] 真の根本原因: `Service`コンテキストにおけるテーマの欠如

- **現象:** `logcat` のログを**最後まで**詳細に分析したところ、`SecurityException` の後に、さらに別の致命的な例外 `java.lang.IllegalArgumentException: The style on this component requires your app theme to be Theme.AppCompat (or a descendant).` が発生していた。

- **原因の特定:**
  1. この例外は、`FloatingButtonService` が `overlay_fab.xml` レイアウトを読み込む（inflateする）際に発生していた。
  2. レイアウト内の `FloatingActionButton` (FAB) は、`Theme.AppCompat` またはその派生テーマが適用されていることを前提とするMaterial Designコンポーネントである。
  3. しかし、`Service` は `Activity` と異なりUIのコンテキストを持たず、**デフォルトではテーマが適用されていない**。
  4. テーマがない状態でテーマを必要とするUIコンポーネントを生成しようとしたため、`IllegalArgumentException` が発生し、サービスがクラッシュしていた。

## 最終的な対応策

`FloatingButtonService` の `onCreate` メソッド内において、`LayoutInflater` に**アプリのテーマを明示的に指定した** `Context` を渡すことで問題を解決した。

**修正前のコード:**
```kotlin
// テーマが適用されていないデフォルトのContextでinflateしていた
overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_fab, null)
```

**修正後のコード:**
```kotlin
// 1. アプリのテーマを持つContextThemeWrapperを生成
val context = ContextThemeWrapper(this, R.style.Theme_AutoScroll)

// 2. テーマが適用されたContextを使ってinflateする
overlayView = LayoutInflater.from(context).inflate(R.layout.overlay_fab, null)
```

## 学んだこと・教訓

- **`logcat` は必ず最後まで読む:** ログの最初の例外に飛びつくと、根本原因を見誤る可能性がある。複数の例外が連続して発生している場合、最後に出力されたものが真の原因であることが多い。
- **`Service` からUIを生成する際の注意点:** `Service` のコンテキストはテーマを持たない。`Service` から直接 `Activity` のようなUIコンポーネント（特にMaterial Designコンポーネント）を含むレイアウトをinflateする場合は、`ContextThemeWrapper` を使って明示的にテーマを適用する必要がある。
- **仮説は常に検証する:** 静的コードレビューによる推測はあくまで仮説に過ぎない。実際のデバイスでの動作と `logcat` による動的な証拠こそが、原因特定において最も重要である。

---

## 機能改善の記録

- **事象:** ユーザーからのフィードバック: 「動画視聴時間が1秒〜30秒のようにランダムであってほしいが、現在の設定では最大5秒までしか間隔を設定できない」
- **原因:** `activity_main.xml` 内の `sliderBaseDelay` の `android:valueTo` が `5000` に制限されていた。
- **対応:** `android:valueTo` の値を `30000` に変更。これにより、スクロール間隔の平均値を最大30秒まで設定可能になり、「Jitter Multiplier」と組み合わせることで、より人間らしいランダムな視聴時間を実現できるようになった。

