package com.mydictionary.desktop.editor;

import com.mydictionary.core.markdown.MarkdownRenderer;
import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ノート本文を「Wordのように」記法を見せずに編集するエディタ（WebViewのcontenteditable）。
 * 表示は{@link MarkdownRenderer#renderForEditor}が作るHTMLで、編集結果は{@link HtmlToMarkdown}で
 * Markdownへ戻す。書式の適用やブロックの変換はクラスパス上の{@code editor.js}（{@code window.MDE}）が行い、
 * このクラスはそれを{@link WebEngine#executeScript}で呼び出す。
 * <p>
 * 一度も編集されていない間は、読み込んだMarkdownをそのまま返す（開いて保存しただけで、
 * 段落内の改行などの書式が変換で変わってしまうのを避けるため）。
 */
public final class RichNoteEditor {
    private static final Pattern FOOTNOTE_DEF = Pattern.compile("^\\[\\^(.+?)]:\\s*(.+)$");
    private static final String SCRIPT_RESOURCE = "/com/mydictionary/desktop/editor/editor.js";

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
        String page = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>" + styleCss
            + "html,body{height:100%;margin:0;box-sizing:border-box;}"
            + "#ed{outline:none;min-height:calc(100% - 4px);}"
            + ".md-atom{display:inline-block;cursor:default;}"
            + "</style><style id=\"appearance\"></style>" + headHtml
            + "</head><body><div id=\"ed\" contenteditable=\"true\" spellcheck=\"false\"></div>"
            + "<script>" + loadScript() + "</script></body></html>";
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
        for (String line : md.split("\n", -1)) {
            if (FOOTNOTE_DEF.matcher(line.trim()).matches()) {
                footnoteDefinitions.add(line.trim());
            }
        }
        String html = new MarkdownRenderer().renderForEditor(md);
        whenReady(() -> call("MDE.setBody(" + quote(html) + ")"));
    }

    /** 現在の本文をMarkdownで返す。 */
    public String getMarkdown() {
        if (!ready || !isDirty()) {
            return loadedMarkdown;
        }
        Object html = call("MDE.getHtml()");
        String body = HtmlToMarkdown.convert(html instanceof String s ? s : "");
        if (footnoteDefinitions.isEmpty()) {
            return body;
        }
        String defs = String.join("\n", footnoteDefinitions);
        return body.isEmpty() ? defs : body + "\n\n" + defs;
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
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        String src = encoded.replace("+", "%20");
        String html = "<a href=\"#image:" + encoded + "\" contenteditable=\"false\"><img alt=\""
            + escapeAttribute(altText) + "\" src=\"" + src + "\"/></a>";
        run("MDE.image(" + quote(html) + ")");
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

    private static String loadScript() {
        try (InputStream in = RichNoteEditor.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("エディタのスクリプトが見つかりません: " + SCRIPT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("</script", "<\\/script");
        } catch (IOException e) {
            throw new IllegalStateException("エディタのスクリプトを読み込めません", e);
        }
    }

    /** JavaScriptの文字列リテラル（ダブルクォート付き）にする。 */
    static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case ' ' -> sb.append("\\u2028");
                case ' ' -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String escapeAttribute(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
