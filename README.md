# MyDictionary

Zoho Notebookのようなブック/ノート/タグ管理アプリ。Windows版とAndroid版で「core」モジュールのデータモデル・ロジックを共有する構成。

## モジュール構成

```
MyDictionary/
├── settings.gradle, build.gradle, gradle.properties   … デスクトップ側のGradleルート（core+desktopをEclipseにインポート）
├── core/                … 共有ロジック（純粋Java、Android/JavaFX非依存）
│   └── src/main/java/com/mydictionary/core/
│       ├── model/       … Book, Note, Tag, BookTheme, BookFont, BookCover（旧BookIcon）
│       ├── repository/  … BookRepository, NoteRepository, TagRepository, SortOrder（インターフェースのみ）
│       ├── validation/  … NameValidator（ブックタイトル・タグ名の文字種チェック）
│       └── markdown/    … MarkdownRenderer（ノート本文の独自Markdown記法→HTML変換）
├── desktop/              … Windows版（JavaFX + SQLite、Eclipseでビルド）
│   └── src/main/java/com/mydictionary/desktop/
│       ├── MainApp.java / Launcher.java  … 起動クラス
│       ├── DesktopSettings.java  … データ保存先フォルダなどのアプリ設定（java.util.prefs）
│       ├── db/           … SqliteDatabase, Sqlite*Repository（coreのリポジトリIFのSQLite実装）
│       ├── sample/       … 初回起動時のサンプルブック/ノート/タグ投入
│       └── screen/       … BookListScreen, BookCreateScreen, BookScreen, BookOptionsScreen, TagScreen,
│                            NoteScreen, NoteCreateScreen, SettingsScreen, SceneNavigator
└── android/              … Android版（Android Studio用の別Gradleルート）
    └── app/src/main/java/com/mydictionary/android/
        ├── MyDictionaryApplication.java  … アプリ起動時にDBとサンプルデータを初期化
        ├── AppPaths.java                 … 画像専用ディレクトリ（アプリ内部ストレージ）
        ├── db/    … DictionaryDbHelper, AndroidBook/Tag/NoteRepository（coreリポジトリIFのSQLiteOpenHelper実装）
        ├── sample/ … 初回起動時のサンプルデータ投入（デスクトップ版と同内容）
        ├── sync/  … SyncManager（Google Drive REST API経由でのデバイス間同期）
        └── ui/    … BookListActivity, BookCreateActivity, BookActivity, BookOptionsActivity,
                     TagActivity, NoteActivity, NoteCreateActivity と各種Adapter
```

`core`には`sync/DatabaseMerger.java`（デバイス間同期のマージロジック、後述）も追加されている。

`android/settings.gradle`は`core`ディレクトリを直接参照するため、`core`のソースはデスクトップ版・Android版の両方から共通で使われる。

## インストール方法

このリポジトリはソースコードのみを配布しており（ビルド成果物・署名鍵はGitに含めていない）、
利用するには各自の環境でビルドしてから「インストール」する必要がある。ビルド環境の詳細
（JDKバージョン・Android SDK等）は後述の「Eclipseでのインポート方法」「Android Studioでの
開き方」の各節を参照。

### Windows版（exeのビルドと導入）

1. リポジトリを取得する。
   ```bash
   git clone https://github.com/tatsuo2no/MyDictionary.git
   cd MyDictionary
   ```
2. JDK 26がインストールされていることを確認し、exeをビルドする。
   ```bash
   gradle :desktop:jpackageExe
   ```
3. `desktop/build/jpackage/MyDictionary/` フォルダが生成される。**このフォルダ一式**を、
   使いたい場所（例: `C:\Program Files\MyDictionary` や任意のフォルダ）へコピーする。
   フォルダ内にJavaランタイムが同梱されているため、別途Javaをインストールする必要はない。
4. コピー先の `MyDictionary.exe` を実行する。必要ならデスクトップにショートカットを
   作成しておくと便利（インストーラー形式ではなく、フォルダをコピーするだけの
   「アプリイメージ」形式のため。詳細は後述「exe化について」を参照）。
5. 初回起動時にサンプルデータが自動的に投入される。「設定」画面からデータ保存先フォルダ
   （Google Drive等の同期フォルダ）を指定できる。

### Android版（APKのビルドと導入）

1. リポジトリを取得する（上記と共通）。
2. Android Studioで `MyDictionary/android` フォルダを開くか、コマンドラインでAPKをビルドする
   （要Android SDK・JDK 17。詳細は後述「Android Studioでの開き方」を参照）。
   ```bash
   cd android
   gradle :app:assembleDebug
   ```
   自分用の署名付きAPKが必要な場合は `gradle :app:assembleRelease` を使う（別途、自分自身の
   署名鍵を用意する必要がある。詳細は後述「apk化について」を参照。このリポジトリには
   署名鍵を含めていないため、cloneしただけでは`assembleRelease`は動かない）。
3. 生成された `android/app/build/outputs/apk/debug/app-debug.apk` を端末に転送する
   （USBケーブル、またはGoogle Drive等のクラウドストレージ経由）。
4. 端末側で「設定 > セキュリティ」から、APKを開くアプリ（ファイルアプリ等）に対して
   「提供元不明のアプリ」のインストールを許可する。
5. 転送したAPKファイルを開いてインストールする。
   （Google Playでは公開していないため、この方法＝サイドロードでのみインストール可能）
6. 初回起動時にサンプルデータが自動的に投入される。デスクトップ版とデータを共有したい場合は、
   ホーム画面の「同期」ボタンから設定する（詳細は後述「デスクトップ版⇔Android版のデータ連携」を参照）。

## 今回のセッションで実装した範囲

- プロジェクト全体の構成（core / desktop / androidの分離）
- データモデル（Book, Note, Tag）とリポジトリインターフェース
- SQLiteによるDB層（books, tags, notes, note_tagsテーブル）
- 初回起動時のサンプルデータ（サンプルブック1件、タグ2〜3件、ノート2件）
- **ブックリスト画面**（一覧表示、件数表示、「ブックを作成」ボタン、既存ブックのクリック/右クリック、
  テーマによる絞り込み（「すべて」を含むComboBox/Spinner、選択中のテーマのブックのみ表示・件数も連動））
- **ブック作成画面**（タイトル・テーマ・フォント・ブックカバー・初期タグの設定、文字種バリデーション）
- **ブック画面**（ノート一覧のテーブル表示、ブック名・件数表示、文字列検索、タグ絞り込み（入れ子タグをインデント表示）、
  名前順/読み順（50音順、Collatorによる日本語ソート）、行クリックでのノート画面遷移、「ノートを作成」「ブックオプション」導線、
  ブックのテーマ色・フォントを画面に反映）
- **ブックオプション画面**（タイトル・テーマ・ブックカバーの編集と保存、「タグ」ボタンからタグ画面への導線、
  「ブックを削除」から削除確認ダイアログ（「削除」と入力しないと削除ボタンが有効化されない仕様）を経てブックを完全削除）
  ※仕様書どおりフォントはこの画面では編集不可（ブック作成画面のみで設定）
- **ブックカバー**（2026-09、記号1文字の「アイコン」を廃止し全面刷新。デスクトップ版・Android版共通で、
  幅122×高さ160px（表示枠込み124×162px）の縦長カード状に統一。プリセット模様9種（無地・縞・水玉・市松・
  ダイヤ・波・杉綾・網目・花柄、`BookCover.Pattern`）はテーマ配色（背景=テーマ色、模様=テーマの文字色）で
  自動着色され、Teams風グリッドポップアップ（`CoverPatternPickerButton`）から選ぶ。「参照」ボタンから
  任意の画像を選ぶこともでき、縦横比を保ったまま中央基準で122×160pxに切り抜いてPNG保存する
  （`BookFormWidgets.saveCustomCoverImage`/`BookCoverImages`）。保存先は既存のノート画像と同じ同期対象
  フォルダのため、デスクトップ⇔Android間で自動的に同期される。ブック名はカバー内ではなくカバー下に
  固定幅で表示し、長い名前でもカード幅は変わらず折り返す）
- **タグ画面**（入れ子タグをTreeViewで表示、ルートタグ/子タグの追加、名称編集、削除。既にノートで使用中の
  タグ（子孫タグ含む）を編集・削除する場合は『このタグを編集/削除しますか？』の確認ポップアップを表示。
  削除時のノートとの紐付け解除・子タグの連鎖削除はSQLiteの外部キーカスケードで実装し、統合テストで検証済み。
  **「インポート」ボタン（デスクトップ版のみ）**からMarkdown（インデント付き箇条書き。`-`/`*`/`・`のいずれかで開始）
  またはJSON（`{"name":..., "children":[...]}`の入れ子配列）ファイルを読み込み、入れ子タグをまとめて作成できる
  （`core/.../tagimport/TagImportParser.java`。既存の同名・同じ親のタグは重複作成せず再利用するため、
  同じファイルを再インポートしても安全）
- **ノート画面**（項目名・読み・英訳・タグを上部に固定表示し、本文（Markdown→HTML変換）だけをWebViewで
  スクロール表示。本文中の注釈クリックで画面下部からせり上がる注釈ウィンドウ、画像クリックでのズーム表示、
  ノート内リンククリックでの同一ブック内ノートへの遷移に対応。「戻る」でブック画面へ、
  「ノートを編集」でノート編集画面へ）
- **ノート作成/編集画面**（項目名・読み・英訳（オプション）・タグ（オプション、プルダウンで選んで
  「タグを追加」ボタンで追加、選択済みタグはチップ表示で個別に削除可）・本文を設定。本文は左に
  MarkdownソースのTextArea、右にWebViewのライブプレビューを並べた
  分割画面。「画像を挿入」でファイル選択ダイアログから画像をアプリ専用ディレクトリにコピーし、
  Markdown記法をカーソル位置に自動挿入。「ノートを保存」で新規作成時はブック画面へ、編集時はそのノート画面へ
  遷移。「戻る」では『編集中の内容は破棄されます。戻りますか？』の確認ポップアップを表示し、新規作成中は
  ブック画面へ、既存ノート編集中はそのノート画面へ戻る）
- **ノートの見た目設定**（2026-09、ノートごとに個別設定。既存のBookTheme(14色)を流用した
  「本文の背景色」（デフォルト白）、「参照」ボタンで選ぶ「本文の背景画像」（オプション。原寸のまま
  既存のノート画像と同じ同期対象フォルダへコピーし、CSSのbackground-size:coverで画面いっぱいに表示。
  設定時は背景色より優先）、文字色専用の新規プリセット14色（濃色系8色: 黒・濃灰色・紺色・えんじ色・
  深緑・こげ茶色・紫・焦茶色、明色系6色: 白・黄色・明灰色・クリーム色・桃色・水色。濃色背景でも
  文字が見えるよう明色系も用意、`NoteTextColor`）から選ぶ「本文の標準文字色」（デフォルト黒）。いずれもノート画面・
  作成/編集画面のプレビューの両方に反映される（`NoteFormWidgets`/`NoteAppearance`）
- **本文中の部分的な文字装飾**（MarkdownRendererが`<small>テキスト</small>`・
  `<span style="color:...;font-size:...">テキスト</span>`をそのまま素通しして解釈する。
  タグの中身には他のMarkdown記法（**太字**など）も引き続き使える）
- 終了確認ポップアップ（ウィンドウの✕ボタン）
- Markdownレンダラー（見出しh1〜h6/引用/リスト/水平線/強調(太字`**`・斜体`*`・打ち消し線`~~`・
  下線`__`)/コードブロック/テーブル/画像/注釈/ノート内リンク/行末半角スペース2個や`<br>`タグでの
  強制改行/`\`によるエスケープ/数式`$...$`・`$$...$$`（KaTeX、後述））とテスト。見出しは
  デスクトップ・Androidどちらのプレビュー/表示画面でも同じ太さ・大きさで表示されるよう、
  明示的なCSSを両方に指定している（指定しないと、両OSの内蔵ブラウザエンジンの既定スタイルの
  違いにより見た目が揃わないことがある）。
- **数式（TeX記法）対応**: KaTeX（JavaScript製の数式レンダリングライブラリ、v0.16.11）を
  オフラインで両OSに同梱している（インターネット接続不要）。デスクトップ版は
  `desktop/src/main/resources/com/mydictionary/desktop/katex/`にクラスパスリソースとして
  同梱し、初回利用時に`~/.mydictionary_katex/`へ展開してから`file://`で参照する
  （`KatexAssets.java`）。Android版は`assets/katex/`に配置し、`file:///android_asset/`で
  直接参照する（`KatexAssets.java`、Android側）。KaTeXの利用のため、**Android版もノート
  表示・プレビュー画面のWebViewでJavaScriptを有効化した**（他機能はJS非依存のまま）。
  実装時、生成しているHTMLに`<!DOCTYPE html>`が無く「quirksモード」で描画されていたために
  KaTeXが動作を拒否する不具合があり、両OS・両画面（表示/プレビュー）の生成HTMLの先頭に
  `<!DOCTYPE html>`を追加して解消した。
- **Windows版のexe化**（`gradle :desktop:jpackageExe`タスクで`MyDictionary.exe`を生成。詳細は後述）
- **Android版の画面実装**（7画面すべて。SQLiteOpenHelperによるcoreリポジトリ実装込み。詳細は後述）
- **Android版のapk化**（署名付きリリースAPK。詳細は後述）
- **デスクトップ版⇔Android版のデータ連携**（クラウド共有フォルダ経由。詳細は後述）

## 未実装（次のステップ）

- Google Playへの公開設定（ストア掲載情報、Play App Signingへの鍵登録など）
- データ連携における「削除」の同期（既知の制限。後述の同期機能の説明を参照）

## 環境メモ（重要）

このPCではJavaの既定の証明書ストア（cacerts）に、社内プロキシ/セキュリティソフトのTLSインスペクション用ルート証明書が入っていないため、Maven Central等へのアクセスでSSLエラーが発生しました。
対策として`gradle.properties`に以下を設定済みです（Windowsの証明書ストアを直接信頼させる）。

```
org.gradle.jvmargs=-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT
```

もし別のPCでビルドしてこのエラーが出ない場合は、この設定は無害なので残したままで問題ありません。

## Eclipseでのインポート方法（デスクトップ版）

1. Eclipse IDE for Java Developers（Buildship = Gradleプラグインが標準搭載）を使用する。
2. `File > Import > Gradle > Existing Gradle Project` を選択。
3. プロジェクトルートとして `MyDictionary`（このフォルダ、`android`は含まない）を指定してインポート。
4. `core` と `desktop` の2プロジェクトがインポートされる。

### ビルド・実行方法（コマンドライン）

```bash
# コンパイル + テスト
gradle build

# デスクトップアプリを起動
gradle :desktop:run
```

Eclipse上では `desktop` プロジェクトの `com.mydictionary.desktop.Launcher` クラスを実行することでも起動できる。
（`MainApp`ではなく`Launcher`を実行すること。`MainApp`は`javafx.application.Application`を継承しているため、
クラスパス実行時にJVMが「JavaFX runtime components are missing」と誤検知してしまう既知の問題を避けるため。）

### exe化について（MyDictionary.exeの生成）

JDKに同梱の`jpackage`を使い、`gradle :desktop:jpackageExe`で`MyDictionary.exe`を生成できる。

```bash
gradle :desktop:jpackageExe
```

生成物は `desktop/build/jpackage/MyDictionary/MyDictionary.exe`（同フォルダの `runtime/` にJavaランタイムが
同梱されているため、Javaが入っていないPCでもこのフォルダごとコピーすれば動作する）。

このタスクは `--type app-image` でビルドしている。これはWiX Toolsetが無くても動く形式で、
`MyDictionary.exe` を直接生成する（インストーラーではなく、フォルダごと配布する形式）。
`--type exe` や `--type msi` で本格的なインストーラーを作りたい場合は、別途
[WiX Toolset](https://wixtoolset.org/)（`candle.exe`/`light.exe`）のインストールが必要
（このPCには入っていなかったため今回はapp-image形式を採用した）。

mainクラスは`com.mydictionary.desktop.Launcher`を指定している（`MainApp`を直接指定すると
`gradle :desktop:run`の項で説明した「JavaFX runtime components are missing」の問題が起きるため）。

## Android Studioでの開き方（Android版）

1. Android Studioで `MyDictionary/android` フォルダを開く（`MyDictionary`直下ではなく`android`サブフォルダを指定）。
2. `core` モジュールは自動的に `../core` を参照して取り込まれる。
3. ランチャーアイコンは未設定なので、Android Studioの `New > Image Asset` で `ic_launcher` を生成し、
   `AndroidManifest.xml` に `android:icon="@mipmap/ic_launcher"` を追加すること
   （lintが `MissingApplicationIcon` を警告として出す。ビルド自体は通る）。

**注意:** Eclipse ADTプラグインは2015年に開発終了しており最新のAndroid SDKと組み合わせて使うのは現実的ではないため、
Android版はAndroid Studioでのビルドを前提としている（ユーザー確認済み）。

### このPCでのAndroidビルド環境（今回セットアップ済み）

このセッションでAndroid SDKとビルド検証環境を用意した。

- **Android SDK**: `C:\Users\Amami\Android\Sdk`（コマンドラインツール一式でインストール。platform-tools,
  platforms;android-34, build-tools;34.0.0）。`android/local.properties`の`sdk.dir`がこれを指している。
- **JDK 17（Android専用）**: `C:\Users\Amami\jdks\jdk-17.0.20+8`（Eclipse Temurin）。
  デスクトップ側で使っているJDK 26ではAndroid Gradle Plugin 8.7のjlink処理
  （`compileDebugJavaWithJavac`が依存する`androidJdkImage`変換）が失敗したため、
  Android専用ビルドにはJDK 17を使うよう`android/gradle.properties`の`org.gradle.java.home`で指定している。
  デスクトップ側（`MyDictionary`直下）はJDK 26のままでよく、影響しない。
- 上記の設定により、Android Studioで開いた場合もこのJDK 17設定が使われる
  （Android Studio同梱のJDKを使いたい場合は`org.gradle.java.home`の行を削除・コメントアウトすること）。

### ビルド・確認方法（コマンドライン）

```bash
cd android

# コンパイル + lint + デバッグ/リリースAPK生成
gradle build

# デバッグAPKのみ
gradle :app:assembleDebug
```

生成物: `android/app/build/outputs/apk/debug/app-debug.apk`

### 確認済みの内容（Android版）

- `gradle build`（`core`のビルド → `app`のコンパイル → lint → デバッグ/リリースAPK生成）が成功することを確認済み
- lintでは当初 `MissingSuperCall`（`onBackPressed`のオーバーライド）と `WebViewLayout` の
  エラー3件が検出されたため、`onBackPressed`は非推奨のためAndroidX推奨の`OnBackPressedCallback`方式に
  書き換え、`WebViewLayout`は意図的な固定高さWebViewのため`tools:ignore`で抑制して解消した
- 実機（`adb install`でUSB接続の実機にインストール）での動作確認をユーザーに実施していただき、
  7画面すべての表示・操作、テーマ色/アイコンの見え方の不具合を含め、報告いただいた問題は解消済み

### apk化について（署名付きリリースAPKの生成）

リリース用の署名鍵（キーストア）を作成し、`app/build.gradle`に署名設定を追加した。

```bash
cd android
gradle :app:assembleRelease
```

生成物: `android/app/build/outputs/apk/release/app-release.apk`（約4.7MB、`apksigner verify`で
APK Signature Scheme v2署名済みであることを確認済み）。

**署名鍵の場所と取り扱い（重要）:**

- キーストア本体: `android/keystore/mydictionary-release.jks`
- パスワード等: `android/keystore.properties`（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`）
- 両方とも`android/.gitignore`で除外設定済み（誤ってバージョン管理に含まれないように）
- **このキーストアは今回のセッションでランダムなパスワードを生成して作成した。Google Playなどで一度公開した
  後にアプリを更新する場合、同じ署名鍵が必要になる（紛失すると同じアプリとして更新できなくなる）。
  `android/keystore/`と`android/keystore.properties`は必ずバックアップを取っておくこと。**
- `keystore.properties`が存在しない場合、`buildTypes.release`は署名なしでビルドされる
  （`assembleDebug`は元々デバッグ鍵で自動署名されるため影響しない）。

### Android版の画面実装について

デスクトップ版と同じ7画面をAndroidのActivityとして実装している。設計上のポイント:

- **DB層**: `AndroidBookRepository`/`AndroidTagRepository`/`AndroidNoteRepository`が、
  `android.database.sqlite.SQLiteOpenHelper`（`DictionaryDbHelper`）を使って`core`のリポジトリIFを実装。
  スキーマ（テーブル定義・外部キーカスケード）はデスクトップ版`SqliteDatabase`と同一。
- **java.time対応**: `core`モジュールが`java.time.Instant`を使用しているため、minSdk 24でも動くよう
  `coreLibraryDesugaringEnabled`を有効化し、`desugar_jdk_libs`を依存に追加している。
- **ノート画面のMarkdown表示**: デスクトップ版と同じ理由（JSブリッジ非依存）で、
  `MarkdownRenderer`が出力する`#footnote:..` `#image:..` `#note:..`というURLフラグメントリンクを
  `WebViewClient#shouldOverrideUrlLoading`で検知する方式を採用（Android版はJavaScriptを一切有効化していない）。
- **タグの入れ子表示**: Android標準にTreeView相当のウィジェットが無いため、`RecyclerView`に
  インデント付きのフラットなリストとして表示している（デスクトップのTreeViewと見た目の考え方は同じ）。
- **画像挿入**: `ActivityResultContracts.GetContent`（Storage Access Framework）で画像を選択し、
  アプリ内部ストレージ（`context.getFilesDir()/images`）にコピーする。ランタイムのストレージ権限は不要。

## デスクトップ版⇔Android版のデータ連携（クラウド共有フォルダ + Drive REST API方式）

デスクトップ版とAndroid版で、ブック・ノート・タグのデータをGoogle Drive経由で共有できる。
**Android版で対応しているのはGoogle Driveのみ**（OneDriveはAndroidのフォルダ選択画面に
表示されないことを実機で確認済みのため非対応。デスクトップ版はOSに認識される任意のフォルダを
直接読み書きするだけなので、OneDriveを含めどのクラウドストレージでも問題なく使える）。

### 設計方針（2026-08-10に大幅刷新）

当初はAndroid標準のStorage Access Framework（SAF）経由でGoogle Driveアプリを介して
共有フォルダにアクセスしていたが、**Google Driveアプリ自身がバックグラウンドで保持する
キャッシュが、他端末での変更を検知できず、数時間〜半日単位で古い内容を読み込んで上書きし、
データを消してしまう事故が実機で複数回発生した**。`ContentResolver.refresh()`や
Google Driveアプリを手動で開いて最新化する等の回避策もすべて不十分だったため、
**SAFベースの実装を完全に廃止し、Googleの正式なDrive REST APIを直接使う方式に作り直した**
（Google Sign-In + `com.google.api.services.drive`）。Drive REST APIはGoogleのサーバーに
直接アクセスするため、Google Driveアプリのキャッシュに影響されない。

- **デスクトップ版**: 「設定」画面でデータ保存先フォルダを指定できる。指定したフォルダに
  データベース（`mydictionary.db`）と画像が置かれ、以後はGoogle Driveの同期機能によって
  クラウドと同期される（アプリ自体は同期処理を行わず、常にそのフォルダのファイルを直接
  読み書きするだけ）。**Google Driveの同期方式が「ストリーミング」になっていると、保存した
  はずのデータが実際にはファイルに反映されないことがある（実機で確認済みの重大な不具合）。**
  必ずそのフォルダを「オフラインで使用可能にする」に設定すること。設定変更はアプリの
  再起動後に反映される。
- **Android版**: 初回、ブックリスト画面の「同期」ボタンを押すとGoogleアカウントへのログインを
  求められる（`drive`スコープ＝Drive内の全ファイルへのアクセス許可）。続けて同期フォルダ名を
  入力するダイアログが出るので、デスクトップ版と同じフォルダ名（例:
  `MyDictionarySync`）を入力すると、Drive API経由でそのフォルダを名前検索する。以後は
  ボタンを押すたびに、
  1. 共有フォルダの`mydictionary.db`をDrive APIで端末に一時的にダウンロード
  2. ローカルDBとuuid・更新日時をもとにマージ（`core`の`DatabaseMerger`）
  3. マージ結果を端末のローカルDBと、共有フォルダの`mydictionary.db`の両方に反映（Drive APIで
     アップロードし直す）
  4. 画像もファイル名ベースで両方向にコピー
  という処理を行う（常時の自動同期ではなく、ボタンを押したときだけ動く）。同期先フォルダを
  変更したい場合は、「同期」ボタンを長押しするとフォルダ名を入力し直せる。

### Google Cloud側の事前設定が必要

Drive REST APIを使うため、Google Cloud Consoleでのプロジェクト作成・Drive APIの有効化・
OAuth同意画面の設定（テストユーザー登録、`drive`スコープの追加）・OAuthクライアントID
（Androidアプリのパッケージ名＋署名証明書のSHA-1で登録）の発行が必要。個人利用の範囲
（無料枠内）であれば料金は発生しない。詳細な手順はメンテナ側の記録を参照。

### マージのルール（重要な制限事項）

- ブック・タグ・ノートにはそれぞれ`uuid`という、デバイスをまたいで同じ実体を識別するための
  IDを付与している（各デバイスのSQLiteの自動採番IDはデバイスごとに独立しているため、
  それだけでは「同じノート」かどうか判断できないため）。
- 同じuuidのデータが両方にある場合は、**更新日時が新しい方を採用**し、両方に反映する
  （いわゆるlast-write-wins。同じノートを両方の端末でオフラインのまま編集すると、
  後から同期した方の内容で上書きされる）。
- 片方にしかないuuidのデータは、もう片方に新規追加する。
- **削除は同期されない（既知の制限）。** 片方の端末でブック・ノート・タグを削除しても、
  もう片方にまだ存在していれば、次回の同期で復活してしまう。削除を伴う整理をする場合は、
  同期前に両方の端末で同じ削除操作をするなど、手動での配慮が必要。
- ダウンロードが不完全な場合（ファイルサイズ不一致・整合性エラー）や、同期処理中に共有
  フォルダが変化した場合は、書き戻しを行わずに中断するガードを実装済み（`SyncManager.syncNow`）。

### 【重要】PC側とAndroid側で同じGoogleアカウントを使うこと

Drive REST API方式に切り替えた直後、Android側で「フォルダが見つからない」というエラーが
長時間解消しない不具合が発生した。原因は、**PC側のGoogle Driveアプリのサインインアカウントと、
Android版でログインしたGoogleアカウントが実際には異なっていた**こと（PCに複数のGoogle
アカウントが登録されており、既定のアカウントが意図したものと違っていた）。`about()`や
`files.get(既知のID)`のようなID指定の直接取得は正常に動作するのに、`files.list()`
（一覧取得・検索）だけが常に0件になる、という症状が出た場合は、まずこのアカウント不一致を
疑うこと。PCのタスクトレイのGoogle Driveアイコンで実際のサインインアカウントを確認し、
Android版でログインしているアカウント（Google Cloud ConsoleのOAuth同意画面でテスト
ユーザーに登録したアカウント）と一致させる必要がある。

### 既存データの移行について

この機能の追加にあたり、データベースに`uuid`・`updated_at`列を追加した。既にお使いのデータベースが
壊れないよう、テーブルを作り直すのではなく`ALTER TABLE`でカラムを追加し、既存の行には
新しいuuidを自動生成して補完する方式にしている（デスクトップ版は`SqliteDatabase.initSchema()`、
Android版は`DictionaryDbHelper.onUpgrade()`）。この移行処理は、実際にユーザーの本番データ
（サンプル以外の自作ブック・画像入りノートを含む）に対して実行し、データが一切失われず
uuidが正しく補完されることを確認済み。

### 確認済み・未確認の範囲（正直な報告）

- `DatabaseMergerIntegrationTest`（`desktop/src/test`）で、2つの独立したSQLiteデータベース
  （ローカルIDの採番系列が異なる）を使い、(1)片方にしかないブック/入れ子タグ/ノートがもう片方に
  正しく複製されること、(2)同じノートを両方で編集した場合に更新日時が新しい方が両方に反映されること
  を検証済み。
- Android版はGoogle Drive REST APIを直接叩く実装のため、OneDrive・Dropbox等は対象外
  （デスクトップ版はOSに認識される任意のフォルダを直接読み書きするだけなので、
  Android版と同期しないのであればOneDriveでも問題なく使える）。
- **Google Driveを使った実機での往復同期を、ユーザーの環境で最終的に確認済み。** デスクトップ版で
  Google Driveの同期フォルダ（`MyDictionarySync`）をデータ保存先に指定し、Android版で同じフォルダ名を
  入力して「同期」を実行したところ、双方向にブックが反映されることを確認。ここに至るまでに
  多数の実装バグ・環境要因（SAF方式の廃止に至った経緯、PC/Android間のGoogleアカウント不一致など）を
  発見・修正しており、詳細は上記「デスクトップ版⇔Android版のデータ連携」を参照。

**同期機能の検証中に見つかり、修正済みの不具合（SAF方式時代のものを含む）:**
1. クラウド同期フォルダ（OneDrive）が内部管理用に作る特殊な隠しフォルダにアクセスしようとして、
   データフォルダの変更（既存データのコピー）が失敗する不具合。隠しファイル/フォルダやアクセス
   できないファイルはスキップして処理を続行するよう修正（`SettingsScreen.java`）。
2. デスクトップ版が作成した`mydictionary.db`はSQLite内部の`user_version`が常に0のままのため、
   Android版がこのファイルを同期で開いた際に「新規データベース」と誤認識し、
   既にあるテーブルに対して`CREATE TABLE`を実行してエラーになる不具合。
   `CREATE TABLE IF NOT EXISTS`に変更して解消（`DictionaryDbHelper.java`）。
3. `drive.files().list()`が、共有ドライブ配下のフォルダに対しては`supportsAllDrives`・
   `includeItemsFromAllDrives`・`corpora=allDrives`を明示的に指定しないと検索結果に
   含まれない（Drive APIの既定動作）。`SyncManager`の全`list()`呼び出しに追加して解消。
4. Android版のバックグラウンドスレッドから直接`Toast`を呼び出しクラッシュする不具合
   （`Can't toast on a thread that has not called Looper.prepare()`）。UIスレッドに
   ディスパッチするよう修正（`BookListActivity.searchAndSync`）。

## ノート画面の技術的な注意点（WebViewとJavaの連携方式）

ノート画面の本文はMarkdownをHTMLに変換し、JavaFXのWebViewで表示している。注釈・画像ズーム・ノート内リンクの
クリックをJava側で検知する必要があるが、一般的な方法（`netscape.javascript.JSObject`でJSからJavaを呼び出す）
に必要な`jdk.jsobject`モジュールが、このPCのJDK（`jdk-26.0.1`）には含まれていなかった
（`java --list-modules`で確認済み）。

代わりに、MarkdownRendererが注釈・画像・ノート内リンクをすべて`<a href="#footnote:...">`
`<a href="#image:...">` `<a href="#note:...">`というURLフラグメント付きリンクとして出力し、
デスクトップ側は`WebEngine`の`locationProperty`の変化を監視してフラグメントを解析する方式にしている
（JavaScript実行やJSObjectを一切使わないため、`jdk.jsobject`モジュールの有無に依存しない）。
別のPC・別のJDKで`jdk.jsobject`が利用可能であっても、この方式のまま動作する。

## 動作確認済みの内容

- `gradle clean build` … core/desktopともコンパイル・単体テスト成功（MarkdownRendererのフラグメントリンク
  出力を含む）
- `gradle :desktop:run` … ブックリスト画面 → サンプルブックをクリック → ブック画面（テーマ色反映、件数「2件」、
  犬/桜のノートがテーブル表示）への遷移をスクリーンショットで目視確認済み
- `SqliteRepositoryIntegrationTest`（`desktop/src/test`）… UIを介さず、SQLite実装が
  ブック/入れ子タグ/ノートの保存・一覧取得・タグ検索・キーワード検索・タグのカスケード削除まで
  正しく動作することを確認済み
- **ブックオプション画面・タグ画面・ノート画面・ノート作成/編集画面については、座標クリックによるGUI自動操作を
  行っていない。** 別のセッションでこの方式のスクリーンショット確認中に、実際には別アプリ（Firefoxで開かれていた
  模擬試験サイト）へ誤ってクリック操作をしてしまう事故が発生したため、以降は安全のためコンパイル・単体テストでの
  検証に留めている。そのため、これら4画面については実装コードのレビューとビルド成功以上の動作保証はできていない。
  お手元での実機確認を推奨する。
- 以上で仕様書に記載された7画面（ブックリスト・ブック作成・ブック・ブックオプション・タグ・ノート・
  ノート作成/編集）のデスクトップ版実装が一通り完成した。
- `gradle :desktop:jpackageExe` … `MyDictionary.exe`の生成に成功。生成された`MyDictionary.exe`を単体で
  （`gradle`や`java`コマンドなしに）起動し、ウィンドウが立ち上がることをプロセス確認で確認済み
  （前述の事故を踏まえ、クリック操作は行わずプロセスの存在とウィンドウタイトルの確認のみ実施）。
