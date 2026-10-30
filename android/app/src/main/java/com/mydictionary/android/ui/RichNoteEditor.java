package com.mydictionary.android.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import com.mydictionary.core.editor.RichEditorSupport;
import com.mydictionary.core.markdown.MarkdownRenderer;

import org.json.JSONException;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * ノート本文を「Wordのように」記法を見せずに編集するエディタ（WebViewのcontenteditable）。
 * デスクトップ版のRichNoteEditorと同じく、表示は{@link MarkdownRenderer#renderForEditor}のHTML、
 * 書式の適用はcoreの{@code editor.js}（{@code window.MDE}）、編集結果のMarkdownへの変換は
 * {@link RichEditorSupport}が行う。AndroidのJavaScript実行は非同期なので、結果が要る操作は
 * コールバックで受け取る。
 * <p>
 * 一度も編集されていない間は、読み込んだMarkdownをそのまま返す（開いて保存しただけで、
 * 段落内の改行などの書式が変換で変わってしまうのを避けるため）。
 */
final class RichNoteEditor {
    private final WebView webView;
    private final List<Runnable> pending = new ArrayList<>();
    private boolean ready;

    /** 最後にsetMarkdownで読み込んだ本文（無編集のまま返す用）。 */
    private String loadedMarkdown = "";
    /** 本文HTMLには現れない注釈の定義行。保存時に末尾へ付け直す。 */
    private final List<String> footnoteDefinitions = new ArrayList<>();

    /**
     * @param styleCss  ページ全体に適用するCSS
     * @param headHtml  KaTeXの読み込みなど、&lt;head&gt;へ入れるHTML
     * @param baseUrl   画像などの相対パスの基準（{@code assetLoader}が配信する画像フォルダのURL）
     */
    @SuppressLint("SetJavaScriptEnabled")
    RichNoteEditor(Context context, String styleCss, String headHtml, String baseUrl,
                   WebViewAssetLoader assetLoader) {
        webView = new WebView(context);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (ready) {
                    return;
                }
                ready = true;
                List<Runnable> toRun = new ArrayList<>(pending);
                pending.clear();
                toRun.forEach(Runnable::run);
            }
        });
        webView.loadDataWithBaseURL(baseUrl, RichEditorSupport.pageHtml(styleCss, headHtml),
            "text/html", "UTF-8", null);
    }

    View getView() {
        return webView;
    }

    void requestFocus() {
        webView.requestFocus();
    }

    // ---------------------------------------------------------------- 本文

    /** Markdownをエディタへ読み込む（編集済みの状態は破棄される）。 */
    void setMarkdown(String markdown) {
        String md = markdown == null ? "" : markdown;
        loadedMarkdown = md;
        footnoteDefinitions.clear();
        footnoteDefinitions.addAll(RichEditorSupport.footnoteDefinitions(md));
        String html = new MarkdownRenderer().renderForEditor(md);
        whenReady(() -> evaluate("MDE.setBody(" + RichEditorSupport.quote(html) + ")", null));
    }

    /** 現在の本文をMarkdownにして、callbackへ渡す。 */
    void getMarkdown(Consumer<String> callback) {
        if (!ready) {
            callback.accept(loadedMarkdown);
            return;
        }
        evaluate("MDE.isDirty()", dirty -> {
            if (!"true".equals(dirty)) {
                callback.accept(loadedMarkdown);
                return;
            }
            evaluate("MDE.getHtml()", html -> callback.accept(
                RichEditorSupport.toMarkdown(decodeString(html), footnoteDefinitions)));
        });
    }

    /** ノートの背景色・背景画像・文字色（bodyに適用するCSS宣言）を更新する。 */
    void setAppearance(String bodyDeclarations) {
        whenReady(() -> evaluate("MDE.setAppearance("
            + RichEditorSupport.quote("body{" + bodyDeclarations + "}") + ")", null));
    }

    // ---------------------------------------------------------------- 書式の操作（ツールバーから）

    void bold() {
        command("bold");
    }

    void italic() {
        command("italic");
    }

    void underline() {
        command("underline");
    }

    void strikethrough() {
        command("strikeThrough");
    }

    void inlineCode() {
        run("MDE.code()");
    }

    /** property(color/background-color/font-family/font-size)をvalueに。valueがnullなら解除する。 */
    void setStyle(String property, String value) {
        run("MDE.style(" + RichEditorSupport.quote(property) + ","
            + (value == null ? "null" : RichEditorSupport.quote(value)) + ")");
    }

    /** 選択中の文字列（無ければ空文字）をcallbackへ渡す。 */
    void selectedText(Consumer<String> callback) {
        evaluate("MDE.selectionText()", value -> callback.accept(decodeString(value)));
    }

    void setRuby(String reading) {
        run("MDE.ruby(" + RichEditorSupport.quote(reading == null ? "" : reading) + ")");
    }

    void align(String align) {
        run("MDE.align(" + RichEditorSupport.quote(align) + ")");
    }

    void toggleBulletList() {
        run("MDE.block('ul')");
    }

    void toggleNumberedList() {
        run("MDE.block('ol')");
    }

    void toggleQuote() {
        run("MDE.block('quote')");
    }

    void toggleCodeBlock() {
        run("MDE.block('pre')");
    }

    /** level=1〜6で見出しにする（同じレベルをもう一度で解除）。0なら見出しを解除する。 */
    void setHeading(int level) {
        run("MDE.heading(" + level + ")");
    }

    void insertHorizontalRule() {
        run("MDE.hr()");
    }

    void insertTable(int rows, int columns) {
        run("MDE.table(" + rows + "," + columns + ")");
    }

    /** 画像（画像フォルダ内のfileName）をカーソル位置へ挿入する。 */
    void insertImage(String fileName, String altText) {
        run("MDE.image(" + RichEditorSupport.quote(RichEditorSupport.imageHtml(fileName, altText)) + ")");
    }

    private void command(String name) {
        run("MDE.cmd(" + RichEditorSupport.quote(name) + ")");
    }

    private void run(String script) {
        whenReady(() -> evaluate(script, null));
        webView.requestFocus();
    }

    private void whenReady(Runnable action) {
        if (ready) {
            action.run();
        } else {
            pending.add(action);
        }
    }

    private void evaluate(String script, Consumer<String> callback) {
        ValueCallback<String> valueCallback = callback == null ? null : callback::accept;
        webView.evaluateJavascript(script, valueCallback);
    }

    /** evaluateJavascriptの結果（JSON表記）から文字列を取り出す。nullや文字列以外は空文字。 */
    private static String decodeString(String json) {
        if (json == null) {
            return "";
        }
        try {
            Object value = new JSONTokener(json).nextValue();
            return value instanceof String s ? s : "";
        } catch (JSONException e) {
            return "";
        }
    }
}
