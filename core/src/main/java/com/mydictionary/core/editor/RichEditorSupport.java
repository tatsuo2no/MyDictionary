package com.mydictionary.core.editor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 「Wordのように」記法を見せずにノート本文を編集するWebViewのエディタ（{@code editor.js}の{@code window.MDE}）を、
 * デスクトップ版・Android版のどちらからも同じように使うための共通部分。
 * WebViewの種類（JavaFX/Android）に依らない、ページHTMLの組み立て・JavaScript文字列の作成・
 * HTMLからMarkdownへ戻すときの注釈の扱いをまとめている。
 */
public final class RichEditorSupport {
    private static final Pattern FOOTNOTE_DEF = Pattern.compile("^\\[\\^(.+?)]:\\s*(.+)$");
    private static final String SCRIPT_RESOURCE = "/com/mydictionary/core/editor/editor.js";
    private static final char LINE_SEPARATOR = (char) 0x2028;
    private static final char PARAGRAPH_SEPARATOR = (char) 0x2029;

    private RichEditorSupport() {
    }

    /**
     * エディタのページ全体のHTML。
     *
     * @param styleCss ページ全体に適用するCSS（フォント・本文の体裁・見出しなど）
     * @param headHtml KaTeXの読み込みなど、&lt;head&gt;へ入れるHTML
     */
    public static String pageHtml(String styleCss, String headHtml) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/><style>" + styleCss
            + "html,body{height:100%;margin:0;box-sizing:border-box;}"
            + "#ed{outline:none;min-height:calc(100% - 4px);}"
            + ".md-atom{display:inline-block;cursor:default;}"
            + "</style><style id=\"appearance\"></style>" + headHtml
            + "</head><body><div id=\"ed\" contenteditable=\"true\" spellcheck=\"false\"></div>"
            + "<script>" + loadScript() + "</script></body></html>";
    }

    /** 本文HTMLには現れない注釈の定義行（{@code [^1]: ...}）。保存時に末尾へ付け直す。 */
    public static List<String> footnoteDefinitions(String markdown) {
        List<String> definitions = new ArrayList<>();
        for (String line : markdown.split("\n", -1)) {
            if (FOOTNOTE_DEF.matcher(line.trim()).matches()) {
                definitions.add(line.trim());
            }
        }
        return definitions;
    }

    /** エディタのHTMLをMarkdownへ戻し、注釈の定義行を末尾へ付け直す。 */
    public static String toMarkdown(String editorHtml, List<String> footnoteDefinitions) {
        String body = HtmlToMarkdown.convert(editorHtml == null ? "" : editorHtml);
        if (footnoteDefinitions.isEmpty()) {
            return body;
        }
        String defs = String.join("\n", footnoteDefinitions);
        return body.isEmpty() ? defs : body + "\n\n" + defs;
    }

    /** 画像（画像フォルダ内のfileName）をエディタへ挿入するときのHTML。 */
    public static String imageHtml(String fileName, String altText) {
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        String src = encoded.replace("+", "%20");
        return "<a href=\"#image:" + encoded + "\" contenteditable=\"false\"><img alt=\""
            + escapeAttribute(altText) + "\" src=\"" + src + "\"/></a>";
    }

    /** JavaScriptの文字列リテラル（ダブルクォート付き）にする。 */
    public static String quote(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
            } else if (c == '"') {
                sb.append("\\\"");
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == LINE_SEPARATOR) {
                sb.append("\\u2028");
            } else if (c == PARAGRAPH_SEPARATOR) {
                sb.append("\\u2029");
            } else if (c < 0x20) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String loadScript() {
        try (InputStream in = RichEditorSupport.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("エディタのスクリプトが見つかりません: " + SCRIPT_RESOURCE);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8).replace("</script", "<\\/script");
        } catch (IOException e) {
            throw new IllegalStateException("エディタのスクリプトを読み込めません", e);
        }
    }

    private static String escapeAttribute(String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
