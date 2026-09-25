package com.mydictionary.android;

/**
 * ノート本文の数式表示（KaTeX）に使うCSS/JS/フォントは`assets/katex/`に同梱している。
 * WebViewからは`file:///android_asset/`スキームで直接参照できるため、デスクトップ版のような
 * ディスクへの展開は不要。
 */
public final class KatexAssets {

    private static final String BASE = "file:///android_asset/katex/";

    private KatexAssets() {
    }

    /** ノートプレビューのHTML &lt;head&gt;に埋め込む、KaTeXの読み込み＋自動描画スクリプト。 */
    public static String headHtml() {
        return "<link rel=\"stylesheet\" href=\"" + BASE + "katex.min.css\"/>"
            + "<script src=\"" + BASE + "katex.min.js\"></script>"
            + "<script src=\"" + BASE + "auto-render.min.js\"></script>"
            + "<script>document.addEventListener('DOMContentLoaded', function() {"
            + "renderMathInElement(document.body, {delimiters: ["
            + "{left: '$$', right: '$$', display: true},"
            + "{left: '$', right: '$', display: false}"
            + "], throwOnError: false});});</script>";
    }
}
