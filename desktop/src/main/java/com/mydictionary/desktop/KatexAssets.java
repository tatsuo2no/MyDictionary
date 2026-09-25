package com.mydictionary.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ノート本文の数式表示（KaTeX）に使うCSS/JS/フォントファイルは、JAR内のクラスパスリソースとして
 * 同梱している。JavaFXのWebEngineでは、CSSの@font-face等が相対パスで正しく解決されるよう、
 * 実際のディスク上のフォルダとして展開しておく必要があるため、初回利用時にユーザーホーム配下の
 * 固定キャッシュフォルダへコピーする（同期対象のデータ保存先フォルダとは無関係の場所）。
 */
public final class KatexAssets {

    private static final Path CACHE_DIR = Paths.get(System.getProperty("user.home"), ".mydictionary_katex");
    private static final String RESOURCE_BASE = "/com/mydictionary/desktop/katex/";

    private static final String[] FONT_FILES = {
        "KaTeX_AMS-Regular.woff2",
        "KaTeX_Caligraphic-Bold.woff2",
        "KaTeX_Caligraphic-Regular.woff2",
        "KaTeX_Fraktur-Bold.woff2",
        "KaTeX_Fraktur-Regular.woff2",
        "KaTeX_Main-Bold.woff2",
        "KaTeX_Main-BoldItalic.woff2",
        "KaTeX_Main-Italic.woff2",
        "KaTeX_Main-Regular.woff2",
        "KaTeX_Math-BoldItalic.woff2",
        "KaTeX_Math-Italic.woff2",
        "KaTeX_SansSerif-Bold.woff2",
        "KaTeX_SansSerif-Italic.woff2",
        "KaTeX_SansSerif-Regular.woff2",
        "KaTeX_Script-Regular.woff2",
        "KaTeX_Size1-Regular.woff2",
        "KaTeX_Size2-Regular.woff2",
        "KaTeX_Size3-Regular.woff2",
        "KaTeX_Size4-Regular.woff2",
        "KaTeX_Typewriter-Regular.woff2",
    };

    private static volatile Path extractedDir;

    private KatexAssets() {
    }

    /** 展開済みのKaTeXアセットフォルダを返す（未展開なら先に展開する）。 */
    public static synchronized Path ensureExtracted() {
        if (extractedDir != null) {
            return extractedDir;
        }
        try {
            Files.createDirectories(CACHE_DIR.resolve("fonts"));
            copyResource("katex.min.css", CACHE_DIR.resolve("katex.min.css"));
            copyResource("katex.min.js", CACHE_DIR.resolve("katex.min.js"));
            copyResource("auto-render.min.js", CACHE_DIR.resolve("auto-render.min.js"));
            for (String font : FONT_FILES) {
                copyResource("fonts/" + font, CACHE_DIR.resolve("fonts").resolve(font));
            }
        } catch (IOException e) {
            throw new RuntimeException("数式表示用ファイル(KaTeX)の展開に失敗しました", e);
        }
        extractedDir = CACHE_DIR;
        return extractedDir;
    }

    /**
     * KaTeXが分数・上線・下線の罫線に使うborder-bottom-widthは通常"0.04em"前後（本文サイズでは1px未満）
     * だが、JavaFX WebViewが内蔵する（比較的古い）WebKitは1px未満のborderをアンチエイリアスできず、
     * 罫線が完全に非表示になってしまう不具合が実機で確認された（Android版のWebView(Chromium)では
     * 発生しないため、このCSS補正はデスクトップ版のみに適用する）。罫線幅を確実に1px以上に補正する。
     */
    private static final String SUBPIXEL_BORDER_FIX_CSS =
        "<style>.katex .frac-line, .katex .overline .overline-line,"
            + " .katex .underline .underline-line { border-bottom-width: 1px !important; }</style>";

    /** ノートプレビューのHTML &lt;head&gt;に埋め込む、KaTeXの読み込み＋自動描画スクリプト。 */
    public static String headHtml() {
        String base = ensureExtracted().toUri().toString();
        return "<link rel=\"stylesheet\" href=\"" + base + "katex.min.css\"/>"
            + SUBPIXEL_BORDER_FIX_CSS
            + "<script src=\"" + base + "katex.min.js\"></script>"
            + "<script src=\"" + base + "auto-render.min.js\"></script>"
            + "<script>document.addEventListener('DOMContentLoaded', function() {"
            + "renderMathInElement(document.body, {delimiters: ["
            + "{left: '$$', right: '$$', display: true},"
            + "{left: '$', right: '$', display: false}"
            + "], throwOnError: false});});</script>";
    }

    private static void copyResource(String relativeName, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = KatexAssets.class.getResourceAsStream(RESOURCE_BASE + relativeName)) {
            if (in == null) {
                throw new IOException("リソースが見つかりません: " + relativeName);
            }
            Files.copy(in, target);
        }
    }
}
