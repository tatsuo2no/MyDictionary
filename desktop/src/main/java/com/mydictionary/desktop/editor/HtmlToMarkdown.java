package com.mydictionary.desktop.editor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WYSIWYG編集画面（WebViewのcontenteditable）のHTMLを、このアプリのMarkdown風記法へ戻す。
 * <p>
 * 入力として想定するのは、{@code MarkdownRenderer#renderForEditor}が出すHTMLを、ブラウザの編集機能
 * （文字入力・書式コマンド・エディタ側スクリプトによるブロック操作）で変更したもの。レンダラーが出す
 * 形（見出し・段落・引用・リスト項目・チェックボックス・表・コードブロック・画像・注釈・ノート内リンク・
 * 数式の原文つきspan）を正しく戻すことに加え、ブラウザが勝手に付ける形（{@code <strong>}・{@code <strike>}・
 * {@code <div>}での段落・{@code font}要素・{@code font-weight}等のCSS指定）も同じ記法へ寄せる。
 * <p>
 * エンターキーによる改行は段落内の{@code <br>}で表され、Markdownでも{@code <br>}として出力する
 * （レンダラーは{@code <br>}をそのまま改行として描画するため、見た目が変わらない）。
 * 注釈の定義行（{@code [^1]: ...}）は本文HTMLに現れないので、呼び出し側が末尾へ付け直す。
 */
public final class HtmlToMarkdown {

    /** レンダラーがバックスラッシュで打ち消せる記号（MarkdownRenderer.ESCAPABLE_CHARSと同じ）。 */
    private static final String ESCAPABLE = "\\`*_{}[]()#+-.!~<>";

    /** 段落内の改行（&lt;br&gt;）を、後処理しやすいよう一時的に表す目印。 */
    private static final char BR = '\u0001';

    private static final Set<String> BLOCK_TAGS = Set.of(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "pre", "table",
        "hr", "dl", "dt", "dd", "section", "article", "header", "footer", "center");
    private static final Set<String> CONTAINER_TAGS = Set.of(
        "div", "section", "article", "header", "footer", "center", "dl");
    private static final Pattern RGB = Pattern.compile(
        "rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*[\\d.]+\\s*)?\\)");
    private static final Pattern BLOCK_START_HEADING = Pattern.compile("^(#{1,6})(\\s)");
    private static final Pattern BLOCK_START_ORDERED = Pattern.compile("^(\\d+)\\.(\\s)");

    private enum Kind { PARAGRAPH, BULLET, ORDERED, CHECK, QUOTE, DEFINITION, HEADING, HR, CODE, TABLE }

    private record Chunk(Kind kind, String text) {
    }

    private HtmlToMarkdown() {
    }

    /** エディタのルート要素のinnerHTMLをMarkdownへ変換する。 */
    public static String convert(String html) {
        Document document = Jsoup.parseBodyFragment(html == null ? "" : html);
        document.outputSettings().prettyPrint(false);
        List<Chunk> chunks = new ArrayList<>();
        collectBlocks(document.body(), chunks, null);
        return join(chunks);
    }

    private static String join(List<Chunk> chunks) {
        StringBuilder sb = new StringBuilder();
        Chunk previous = null;
        for (Chunk chunk : chunks) {
            if (previous != null) {
                sb.append(previous.kind() == chunk.kind() && isLineGroup(chunk.kind()) ? "\n" : "\n\n");
            }
            sb.append(chunk.text());
            previous = chunk;
        }
        return sb.toString();
    }

    /** 隣り合っていれば空行を挟まずに1行ずつ並べるブロック種別（リスト・引用・定義）。 */
    private static boolean isLineGroup(Kind kind) {
        return kind == Kind.BULLET || kind == Kind.ORDERED || kind == Kind.CHECK
            || kind == Kind.QUOTE || kind == Kind.DEFINITION;
    }

    // ---------------------------------------------------------------- ブロック

    private static void collectBlocks(Element container, List<Chunk> chunks, String inheritedAlign) {
        List<Node> pendingInline = new ArrayList<>();
        for (Node child : container.childNodes()) {
            if (child instanceof Element element && isBlock(element)) {
                flushInline(pendingInline, chunks, inheritedAlign);
                processBlock(element, chunks, inheritedAlign);
            } else {
                pendingInline.add(child);
            }
        }
        flushInline(pendingInline, chunks, inheritedAlign);
    }

    /** ブロックの外に直接置かれた文字・インライン要素の並びを、1つの段落として出力する。 */
    private static void flushInline(List<Node> nodes, List<Chunk> chunks, String align) {
        if (nodes.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Node node : nodes) {
            inline(node, sb);
        }
        nodes.clear();
        String text = finishLine(sb);
        if (!text.isEmpty()) {
            chunks.add(new Chunk(Kind.PARAGRAPH, directive(align) + escapeBlockStart(text)));
        }
    }

    private static boolean isBlock(Element element) {
        return BLOCK_TAGS.contains(element.normalName());
    }

    private static boolean hasBlockChild(Element element) {
        for (Element child : element.children()) {
            if (isBlock(child)) {
                return true;
            }
        }
        return false;
    }

    private static void processBlock(Element el, List<Chunk> chunks, String inheritedAlign) {
        String tag = el.normalName();
        String own = blockAlign(el);
        if (own == null && tag.equals("center")) {
            own = "center";
        }
        String align = own != null ? own : inheritedAlign;

        switch (tag) {
            case "p", "div", "section", "article", "header", "footer", "center", "dt" -> {
                if (el.hasClass("checkbox-item")) {
                    addCheckItem(el, chunks, align);
                } else if (CONTAINER_TAGS.contains(tag) && hasBlockChild(el)) {
                    collectBlocks(el, chunks, align);
                } else if (hasBlockChild(el)) {
                    collectBlocks(el, chunks, align);
                } else {
                    String text = inlineLine(el);
                    if (!text.isEmpty()) {
                        chunks.add(new Chunk(Kind.PARAGRAPH, directive(align) + escapeBlockStart(text)));
                    }
                }
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                String text = inlineLine(el);
                if (!text.isEmpty()) {
                    int level = tag.charAt(1) - '0';
                    chunks.add(new Chunk(Kind.HEADING, directive(align) + "#".repeat(level) + " " + text));
                }
            }
            case "blockquote" -> {
                List<String> lines = new ArrayList<>();
                leafLines(el, lines);
                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(directive(align)).append("> ").append(line);
                }
                if (sb.length() > 0) {
                    chunks.add(new Chunk(Kind.QUOTE, sb.toString()));
                }
            }
            case "ul", "ol" -> {
                int[] counter = {0};
                collectListItems(el, "ol".equals(tag), counter, chunks, align);
            }
            case "li" -> addListItem(el, el.hasClass("ordered"), 1, chunks, align);
            case "hr" -> chunks.add(new Chunk(Kind.HR, "---"));
            case "pre" -> addCodeBlock(el, chunks);
            case "table" -> addTable(el, chunks, align);
            case "dd" -> {
                String text = inlineLine(el);
                if (!text.isEmpty()) {
                    chunks.add(new Chunk(Kind.DEFINITION, ": " + text));
                }
            }
            default -> collectBlocks(el, chunks, align);
        }
    }

    /** 引用の中身を行ごとに集める（中に段落などのブロックがあれば、それぞれを1行とする）。 */
    private static void leafLines(Element el, List<String> out) {
        if (!hasBlockChild(el)) {
            String text = inlineLine(el);
            if (!text.isEmpty()) {
                out.add(text);
            }
            return;
        }
        List<Node> pending = new ArrayList<>();
        for (Node child : el.childNodes()) {
            if (child instanceof Element element && isBlock(element)) {
                flushLeafInline(pending, out);
                leafLines(element, out);
            } else {
                pending.add(child);
            }
        }
        flushLeafInline(pending, out);
    }

    private static void flushLeafInline(List<Node> pending, List<String> out) {
        StringBuilder sb = new StringBuilder();
        for (Node node : pending) {
            inline(node, sb);
        }
        pending.clear();
        String text = finishLine(sb);
        if (!text.isEmpty()) {
            out.add(text);
        }
    }

    private static void collectListItems(Element list, boolean ordered, int[] counter, List<Chunk> chunks,
                                         String listAlign) {
        for (Element child : list.children()) {
            if (child.normalName().equals("li")) {
                counter[0]++;
                String own = blockAlign(child);
                addListItem(child, ordered, counter[0], chunks, own != null ? own : listAlign);
                for (Element nested : child.children()) {
                    if (nested.normalName().equals("ul") || nested.normalName().equals("ol")) {
                        collectListItems(nested, nested.normalName().equals("ol"), counter, chunks, listAlign);
                    }
                }
            } else if (child.normalName().equals("ul") || child.normalName().equals("ol")) {
                collectListItems(child, child.normalName().equals("ol"), counter, chunks, listAlign);
            }
        }
    }

    private static void addListItem(Element li, boolean ordered, int number, List<Chunk> chunks, String align) {
        StringBuilder sb = new StringBuilder();
        for (Node child : li.childNodes()) {
            if (child instanceof Element element
                && (element.normalName().equals("ul") || element.normalName().equals("ol"))) {
                continue;
            }
            inline(child, sb);
        }
        String text = finishLine(sb);
        if (text.isEmpty()) {
            return;
        }
        String prefix = ordered ? number + ". " : "- ";
        chunks.add(new Chunk(ordered ? Kind.ORDERED : Kind.BULLET, directive(align) + prefix + text));
    }

    private static void addCheckItem(Element item, List<Chunk> chunks, String align) {
        boolean checked = false;
        StringBuilder sb = new StringBuilder();
        for (Node child : item.childNodes()) {
            if (child instanceof Element element && element.normalName().equals("input")) {
                checked = element.hasAttr("checked");
            } else {
                inline(child, sb);
            }
        }
        String text = finishLine(sb);
        chunks.add(new Chunk(Kind.CHECK, directive(align) + (checked ? "- [x] " : "- [ ] ") + text));
    }

    private static void addCodeBlock(Element pre, List<Chunk> chunks) {
        StringBuilder sb = new StringBuilder();
        appendCodeText(pre, sb);
        String code = sb.toString().replace("\r", "");
        while (code.endsWith("\n")) {
            code = code.substring(0, code.length() - 1);
        }
        chunks.add(new Chunk(Kind.CODE, "```\n" + code + "\n```"));
    }

    private static void appendCodeText(Node node, StringBuilder sb) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode text) {
                sb.append(text.getWholeText().replace(' ', ' '));
            } else if (child instanceof Element element) {
                if (element.normalName().equals("br")) {
                    sb.append('\n');
                } else {
                    appendCodeText(element, sb);
                }
            }
        }
    }

    private static void addTable(Element table, List<Chunk> chunks, String align) {
        String tableAlign = tableAlign(table);
        if (tableAlign == null && align != null && !"left".equals(align)) {
            tableAlign = align;
        }
        StringBuilder sb = new StringBuilder();
        int rowIndex = 0;
        for (Element row : table.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element cell : row.children()) {
                if (cell.normalName().equals("td") || cell.normalName().equals("th")) {
                    StringBuilder cellText = new StringBuilder();
                    for (Node n : cell.childNodes()) {
                        inline(n, cellText);
                    }
                    cells.add(finishLine(cellText));
                }
            }
            if (cells.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(tableRow(cells));
            if (rowIndex == 0) {
                List<String> separators = new ArrayList<>();
                for (int i = 0; i < cells.size(); i++) {
                    separators.add("---");
                }
                sb.append('\n').append(tableRow(separators));
            }
            rowIndex++;
        }
        if (sb.length() > 0) {
            chunks.add(new Chunk(Kind.TABLE, directive(tableAlign) + sb));
        }
    }

    private static String tableRow(List<String> cells) {
        StringBuilder sb = new StringBuilder("|");
        for (String cell : cells) {
            sb.append(' ').append(cell).append(" |");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- 配置

    /** 指定行（"{: align=\"center\"}"+改行）。left・指定なしは空文字（指定行は不要）。 */
    private static String directive(String align) {
        if ("center".equals(align) || "right".equals(align)) {
            return "{: align=\"" + align + "\"}\n";
        }
        return "";
    }

    /** ブロック要素に指定された文字揃え（center/right/left）。無ければnull。 */
    private static String blockAlign(Element el) {
        String data = el.attr("data-align");
        if (!data.isEmpty()) {
            return normalizeAlign(data);
        }
        String fromStyle = normalizeAlign(parseStyle(el.attr("style")).get("text-align"));
        if (fromStyle != null) {
            return fromStyle;
        }
        return normalizeAlign(el.attr("align"));
    }

    private static String normalizeAlign(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("center") || v.equals("right") || v.equals("left") ? v : null;
    }

    /** 表の配置。エディタ側の印（data-align）を優先し、無ければレンダラーが付けるmargin指定から判定する。 */
    private static String tableAlign(Element table) {
        String data = normalizeAlign(table.attr("data-align"));
        if (data != null) {
            return data;
        }
        Map<String, String> style = parseStyle(table.attr("style"));
        String left = style.get("margin-left");
        String right = style.get("margin-right");
        String shorthand = style.get("margin");
        if (shorthand != null) {
            String[] parts = shorthand.trim().split("\\s+");
            if (parts.length == 2) {
                left = parts[1];
                right = parts[1];
            } else if (parts.length == 4) {
                right = parts[1];
                left = parts[3];
            } else if (parts.length == 3) {
                left = parts[1];
                right = parts[1];
            }
        }
        boolean leftAuto = "auto".equals(left);
        boolean rightAuto = "auto".equals(right);
        if (leftAuto && rightAuto) {
            return "center";
        }
        if (leftAuto) {
            return "right";
        }
        return null;
    }

    // ---------------------------------------------------------------- インライン

    /** 要素の中身を1行のMarkdown（段落内の改行は&lt;br&gt;）にする。 */
    private static String inlineLine(Element el) {
        StringBuilder sb = new StringBuilder();
        for (Node child : el.childNodes()) {
            inline(child, sb);
        }
        return finishLine(sb);
    }

    /** 目印の改行を&lt;br&gt;へ戻し、前後の空白と末尾の改行（キャレット位置の目印にすぎない）を取り除く。 */
    private static String finishLine(StringBuilder sb) {
        String s = sb.toString();
        s = s.replaceAll(" *" + BR + " *", String.valueOf(BR));
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == BR || s.charAt(end - 1) == ' ')) {
            end--;
        }
        int begin = 0;
        while (begin < end && s.charAt(begin) == ' ') {
            begin++;
        }
        return s.substring(begin, end).replace(String.valueOf(BR), "<br>");
    }

    private static void inline(Node node, StringBuilder out) {
        if (node instanceof TextNode text) {
            out.append(escapeText(collapse(text.getWholeText())));
            return;
        }
        if (!(node instanceof Element el)) {
            return;
        }
        String tag = el.normalName();

        if (el.hasAttr("data-md")) {
            out.append(el.attr("data-md"));
            return;
        }

        switch (tag) {
            case "br" -> out.append(BR);
            case "script", "style", "input", "rp" -> {
            }
            case "b", "strong" -> wrapInline(el, out, "**", "**");
            case "i", "em" -> wrapInline(el, out, "*", "*");
            case "u", "ins" -> wrapInline(el, out, "__", "__");
            case "s", "strike", "del" -> wrapInline(el, out, "~~", "~~");
            case "small" -> wrapInline(el, out, "<small>", "</small>");
            case "big" -> wrapInline(el, out, "<big>", "</big>");
            case "ruby" -> wrapInline(el, out, "<ruby>", "</ruby>");
            case "rt" -> wrapInline(el, out, "<rt>", "</rt>");
            case "code" -> inlineCode(el, out);
            case "span" -> styledSpan(el, out);
            case "font" -> fontElement(el, out);
            case "img" -> out.append(imageMarkdown(el, el.attr("src")));
            case "a" -> anchor(el, out);
            case "sup" -> {
                Element footnote = el.selectFirst("a.footnote");
                if (footnote != null) {
                    String key = footnote.text().trim();
                    if (key.startsWith("[") && key.endsWith("]") && key.length() >= 2) {
                        key = key.substring(1, key.length() - 1);
                    }
                    out.append("[^").append(key).append(']');
                } else {
                    children(el, out);
                }
            }
            default -> {
                if (isBlock(el) && !tag.equals("ul") && !tag.equals("ol")) {
                    // 段落などのブロックがインラインの文脈に入り込んだ場合は、前後を改行として扱う。
                    if (out.length() > 0 && out.charAt(out.length() - 1) != BR) {
                        out.append(BR);
                    }
                    children(el, out);
                    out.append(BR);
                } else {
                    children(el, out);
                }
            }
        }
    }

    private static void children(Element el, StringBuilder out) {
        for (Node child : el.childNodes()) {
            inline(child, out);
        }
    }

    private static void wrapInline(Element el, StringBuilder out, String open, String close) {
        StringBuilder inner = new StringBuilder();
        children(el, inner);
        if (inner.length() == 0) {
            return;
        }
        if (inner.toString().isBlank()) {
            out.append(inner);
            return;
        }
        out.append(open).append(inner).append(close);
    }

    private static void inlineCode(Element el, StringBuilder out) {
        String text = collapse(el.wholeText());
        if (text.isEmpty()) {
            return;
        }
        if (text.indexOf('`') >= 0) {
            out.append(escapeText(text));
        } else {
            out.append('`').append(escapeText(text)).append('`');
        }
    }

    private static void anchor(Element el, StringBuilder out) {
        String href = el.attr("href");
        if (href.startsWith("#image:")) {
            Element img = el.selectFirst("img");
            String path = URLDecoder.decode(href.substring("#image:".length()), StandardCharsets.UTF_8);
            out.append(imageMarkdown(img, path));
        } else if (href.startsWith("#note:")) {
            out.append("[[").append(href.substring("#note:".length())).append('|').append(el.text()).append("]]");
        } else {
            children(el, out);
        }
    }

    private static String imageMarkdown(Element img, String path) {
        String alt = img == null ? "" : img.attr("alt");
        if (img != null && path != null && !path.isEmpty() && img.attr("src").equals(path)) {
            // <a>に包まれていない画像は、srcのファイル名部分を保存名として使う。
            int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            path = URLDecoder.decode(path.substring(slash + 1), StandardCharsets.UTF_8);
        }
        StringBuilder md = new StringBuilder("![").append(alt).append("](").append(path);
        if (img != null) {
            String width = img.attr("width");
            String height = img.attr("height");
            if (!width.isEmpty() || !height.isEmpty()) {
                md.append(" =").append(width).append('x').append(height);
            }
        }
        return md.append(')').toString();
    }

    private static void styledSpan(Element el, StringBuilder out) {
        Map<String, String> style = parseStyle(el.attr("style"));
        StringBuilder inner = new StringBuilder();
        children(el, inner);
        if (inner.length() == 0) {
            return;
        }
        if (inner.toString().isBlank()) {
            out.append(inner);
            return;
        }

        // ブラウザの書式コマンドがCSSで付ける太字・斜体・下線・打ち消し線は、対応するMarkdown記法へ戻す。
        String weight = style.getOrDefault("font-weight", "");
        boolean bold = weight.equals("bold") || weight.equals("bolder")
            || (weight.matches("\\d+") && Integer.parseInt(weight) >= 600);
        boolean italic = style.getOrDefault("font-style", "").equals("italic");
        String decoration = style.getOrDefault("text-decoration", "") + " "
            + style.getOrDefault("text-decoration-line", "");
        boolean underline = decoration.contains("underline");
        boolean strike = decoration.contains("line-through");

        StringBuilder css = new StringBuilder();
        appendDeclaration(css, "color", cleanColor(style.get("color")));
        appendDeclaration(css, "background-color", cleanColor(style.get("background-color")));
        appendDeclaration(css, "font-size", cleanValue(style.get("font-size")));
        String family = style.get("font-family");
        appendDeclaration(css, "font-family", family == null ? null : cleanValue(family.replace('"', '\'')));

        String result = inner.toString();
        if (css.length() > 0) {
            result = "<span style=\"" + css + "\">" + result + "</span>";
        }
        if (strike) {
            result = "~~" + result + "~~";
        }
        if (underline) {
            result = "__" + result + "__";
        }
        if (italic) {
            result = "*" + result + "*";
        }
        if (bold) {
            result = "**" + result + "**";
        }
        out.append(result);
    }

    private static void fontElement(Element el, StringBuilder out) {
        StringBuilder inner = new StringBuilder();
        children(el, inner);
        if (inner.length() == 0) {
            return;
        }
        StringBuilder css = new StringBuilder();
        appendDeclaration(css, "color", cleanColor(el.attr("color")));
        String face = el.attr("face");
        appendDeclaration(css, "font-family", face.isEmpty() ? null : cleanValue(face.replace('"', '\'')));
        if (css.length() == 0 || inner.toString().isBlank()) {
            out.append(inner);
        } else {
            out.append("<span style=\"").append(css).append("\">").append(inner).append("</span>");
        }
    }

    private static void appendDeclaration(StringBuilder css, String property, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (css.length() > 0) {
            css.append(';');
        }
        css.append(property).append(':').append(value);
    }

    private static String cleanColor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        Matcher m = RGB.matcher(v);
        if (m.matches()) {
            return String.format("#%02x%02x%02x",
                Math.min(255, Integer.parseInt(m.group(1))),
                Math.min(255, Integer.parseInt(m.group(2))),
                Math.min(255, Integer.parseInt(m.group(3))));
        }
        return cleanValue(v);
    }

    /** style属性値の中に入れられない文字（"・&lt;・&gt;・;）と!importantを取り除く。 */
    private static String cleanValue(String value) {
        if (value == null) {
            return null;
        }
        String v = value.replace("!important", "").replaceAll("[\"<>;]", "").trim();
        return v.isEmpty() ? null : v;
    }

    /** style属性を「プロパティ(小文字)→値」に分解する。値は比較しやすいよう小文字にする（font-familyのみ元のまま）。 */
    private static Map<String, String> parseStyle(String style) {
        Map<String, String> map = new LinkedHashMap<>();
        if (style == null || style.isBlank()) {
            return map;
        }
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0) {
                String key = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = declaration.substring(colon + 1).trim();
                map.put(key, key.equals("font-family") ? value : value.toLowerCase(Locale.ROOT));
            }
        }
        return map;
    }

    // ---------------------------------------------------------------- 文字の扱い

    /** 連続する空白（改行・タブ・ノーブレークスペース含む）を半角スペース1つにまとめる。 */
    private static String collapse(String text) {
        return text.replace("​", "").replace(String.valueOf(BR), "")
            .replaceAll("[\\s ]+", " ");
    }

    /** 文章中の、このレンダラーの記法として解釈されうる文字を、バックスラッシュで打ち消す。 */
    static String escapeText(String text) {
        StringBuilder sb = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            char prev = i > 0 ? text.charAt(i - 1) : 0;
            char next = i + 1 < length ? text.charAt(i + 1) : 0;
            switch (c) {
                case '\\' -> sb.append(next != 0 && ESCAPABLE.indexOf(next) >= 0 ? "\\\\" : "\\");
                case '*', '`' -> sb.append('\\').append(c);
                case '_', '~' -> sb.append(prev == c || next == c ? "\\" : "").append(c);
                case '[' -> sb.append(next == '[' || next == '^' || prev == '!' ? "\\[" : "[");
                case '<' -> sb.append(Character.isLetter(next) || next == '/' ? "\\<" : "<");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 行頭に来ると見出し・引用・リスト等として解釈されてしまう文章を、先頭の記号の打ち消しで防ぐ。 */
    private static String escapeBlockStart(String line) {
        if (BLOCK_START_HEADING.matcher(line).find()) {
            return "\\" + line;
        }
        if (line.startsWith("> ")) {
            return "\\" + line;
        }
        if (line.startsWith("- ") || line.equals("---")) {
            return "\\" + line;
        }
        Matcher ordered = BLOCK_START_ORDERED.matcher(line);
        if (ordered.find()) {
            return ordered.group(1) + "\\." + line.substring(ordered.end(1) + 1);
        }
        return line;
    }
}
