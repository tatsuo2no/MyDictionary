package com.mydictionary.desktop.editor;

import com.mydictionary.core.editor.RichEditorSupport;
import com.mydictionary.core.markdown.MarkdownRenderer;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ノート本文を「Wordのように」記法を見せずに編集するエディタ（WebViewのcontenteditable）。
 * 表示は{@link MarkdownRenderer#renderForEditor}が作るHTMLで、編集結果は{@link HtmlToMarkdown}で
 * Markdownへ戻す。書式の適用やブロックの変換はクラスパス上の{@code editor.js}（{@code window.MDE}、coreに置いて
 * Android版と共用）が行い、
 * このクラスはそれを{@link WebEngine#executeScript}で呼び出す。
 * <p>
 * 一度も編集されていない間は、読み込んだMarkdownをそのまま返す（開いて保存しただけで、
 * 段落内の改行などの書式が変換で変わってしまうのを避けるため）。
 */
public final class RichNoteEditor {
    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private final List<Runnable> pending = new ArrayList<>();
    private boolean ready;

    /** 最後にsetMarkdownで読み込んだ本文（無編集のまま返す用）。 */
    private String loadedMarkdown = "";
    /** 本文HTMLには現れない注釈の定義行。保存時に末尾へ付け直す。 */
    private final List<String> footnoteDefinitions = new ArrayList<>();

    /**
     * @param styleCss  ページ全体に適用するCSS（フォント・本文の体裁・見出しなどのプレビューと共通のもの）
     * @param headHtml  KaTeXの読み込みなど、&lt;head&gt;へ入れるHTML
     * @param pageFile  ページ(HTML)を書き出すファイル。画像などの相対パスの基準になるため、画像フォルダ内に置く
     */
    public RichNoteEditor(String styleCss, String headHtml, Path pageFile) {
        webView.setContextMenuEnabled(true);
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                ready = true;
                List<Runnable> toRun = new ArrayList<>(pending);
                pending.clear();
                toRun.forEach(Runnable::run);
            }
        });
        String page = RichEditorSupport.pageHtml(styleCss, headHtml);
        try {
            Files.createDirectories(pageFile.getParent());
            Files.writeString(pageFile, page, StandardCharsets.UTF_8);
            engine.load(pageFile.toUri().toString());
        } catch (IOException e) {
            engine.loadContent(page);
        }
    }

    public Node getNode() {
        return webView;
    }

    public void requestFocus() {
        webView.requestFocus();
    }

    // ---------------------------------------------------------------- 本文

    /** Markdownをエディタへ読み込む（編集済みの状態は破棄される）。 */
    public void setMarkdown(String markdown) {
        String md = markdown == null ? "" : markdown;
        loadedMarkdown = md;
        footnoteDefinitions.clear();
        footnoteDefinitions.addAll(RichEditorSupport.footnoteDefinitions(md));
        String html = new MarkdownRenderer().renderForEditor(md);
        whenReady(() -> call("MDE.setBody(" + quote(html) + ")"));
    }

    /** 現在の本文をMarkdownで返す。 */
    public String getMarkdown() {
        if (!ready || !isDirty()) {
            return loadedMarkdown;
        }
        Object html = call("MDE.getHtml()");
        return RichEditorSupport.toMarkdown(html instanceof String s ? s : "", footnoteDefinitions);
    }

    public boolean isDirty() {
        return ready && Boolean.TRUE.equals(call("MDE.isDirty()"));
    }

    /** ノートの背景色・背景画像・文字色（bodyに適用するCSS宣言）を更新する。 */
    public void setAppearance(String bodyDeclarations) {
        whenReady(() -> call("MDE.setAppearance(" + quote("body{" + bodyDeclarations + "}") + ")"));
    }

    // ---------------------------------------------------------------- 書式の操作（ツールバーから）

    public void bold() {
        command("bold");
    }

    public void italic() {
        command("italic");
    }

    public void underline() {
        command("underline");
    }

    public void strikethrough() {
        command("strikeThrough");
    }

    public void inlineCode() {
        run("MDE.code()");
    }

    /** property(color/background-color/font-family/font-size)をvalueに。valueがnullなら解除する。 */
    public void setStyle(String property, String value) {
        run("MDE.style(" + quote(property) + "," + (value == null ? "null" : quote(value)) + ")");
    }

    /** 選択中の文字列（無ければ空文字）。 */
    public String selectedText() {
        Object text = call("MDE.selectionText()");
        return text instanceof String s ? s : "";
    }

    public void setRuby(String reading) {
        run("MDE.ruby(" + quote(reading == null ? "" : reading) + ")");
    }

    public void align(String align) {
        run("MDE.align(" + quote(align) + ")");
    }

    public void toggleBulletList() {
        run("MDE.block('ul')");
    }

    public void toggleNumberedList() {
        run("MDE.block('ol')");
    }

    public void toggleQuote() {
        run("MDE.block('quote')");
    }

    public void toggleCodeBlock() {
        run("MDE.block('pre')");
    }

    /** level=1〜6で見出しにする（同じレベルをもう一度で解除）。0なら見出しを解除する。 */
    public void setHeading(int level) {
        run("MDE.heading(" + level + ")");
    }

    public void insertHorizontalRule() {
        run("MDE.hr()");
    }

    public void insertTable(int rows, int columns) {
        run("MDE.table(" + rows + "," + columns + ")");
    }

    /** 画像（画像フォルダ内のfileName）をカーソル位置へ挿入する。 */
    public void insertImage(String fileName, String altText) {
        run("MDE.image(" + quote(RichEditorSupport.imageHtml(fileName, altText)) + ")");
    }

    private void command(String name) {
        run("MDE.cmd(" + quote(name) + ")");
    }

    private void run(String script) {
        if (ready) {
            call(script);
        } else {
            whenReady(() -> call(script));
        }
        webView.requestFocus();
    }

    private void whenReady(Runnable action) {
        if (ready) {
            action.run();
        } else {
            pending.add(action);
        }
    }

    /** スクリプトを実行して結果を返す。失敗しても編集画面全体は止めず、標準エラーへ記録する。 */
    public Object call(String script) {
        try {
            return engine.executeScript(script);
        } catch (RuntimeException e) {
            System.err.println("エディタのスクリプト実行に失敗しました: " + script + " : " + e);
            return null;
        }
    }

    // ---------------------------------------------------------------- 補助

    private static String quote(String text) {
        return RichEditorSupport.quote(text);
    }
}
