package com.mydictionary.core.markdown;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ノート本文で使用するMarkdown風記法をHTMLに変換する。
 * 見出し(h1-h6)/引用/リスト(disc・decimal・checkbox・definition)/水平線/強調(太字・斜体・打ち消し線・下線)/
 * コードブロック/テーブル/画像/注釈/ノート内リンク/行末半角スペース2個や&lt;br&gt;タグによる改行/
 * バックスラッシュによるエスケープ/数式($...$・$$...$$)に対応した基礎実装。CommonMark完全準拠ではない。
 * 数式はここではKaTeXが後で処理できるよう$記号ごと素通りさせるだけで、実際のレンダリングは
 * 呼び出し側（デスクトップ・Android）がHTMLにKaTeXのJS/CSSを埋め込むことで行う。
 */
public class MarkdownRenderer {

    private static final Pattern FOOTNOTE_DEF = Pattern.compile("^\\[\\^(.+?)]:\\s*(.+)$");
    private static final Pattern FOOTNOTE_REF = Pattern.compile("\\[\\^(.+?)]");
    private static final Pattern NOTE_LINK = Pattern.compile("\\[\\[(\\d+)\\|(.+?)]]");
    /**
     * 画像記法。`![alt](path)`の基本形に加え、サイズ指定の拡張構文に対応する。
     * `![alt](path =300x)`（横のみ）・`=x300`（縦のみ）・`=300x300`（縦横）。
     * group: 1=alt / 2=path / 3=幅 / 4=高さ。中央寄せ（{@link #CENTER_DIRECTIVE_LINE}参照）は
     * このパターンでは扱わず、render()側でブロック単位に判定してinline()へ渡す。
     */
    private static final Pattern IMAGE = Pattern.compile("!\\[(.*?)]\\((.+?)(?:\\s+=(\\d+)?x(\\d+)?)?\\)");
    /**
     * 画像・テーブル・本文（段落）を中央寄せにする指定行。対象となるブロック（画像を含む段落、
     * 通常の文章の段落、またはテーブル）の直前の行に単独で書く。行単位でrender()が判定して消費するため、
     * パラグラフの結合や
     * インライン処理には一切現れない。
     * 閉じ中括弧はデスクトップ版JVMの正規表現エンジンは無エスケープでも許容するが、Android(ICU正規表現)
     * は不正な構文としてPatternSyntaxExceptionを投げる（実機で確認済み。static final初期化中の例外の
     * ためMarkdownRendererに触れる全画面がクラッシュする重大な不具合だった）。開き・閉じとも必ず
     * エスケープすること。
     */
    private static final Pattern CENTER_DIRECTIVE_LINE = Pattern.compile("^\\{:\\s*align=\"center\"\\s*\\}$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern BR_TAG = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern MATH_BLOCK = Pattern.compile("\\$\\$([\\s\\S]+?)\\$\\$");
    private static final Pattern MATH_INLINE = Pattern.compile("\\$([^\\n$]+?)\\$");

    /**
     * 文字色・文字サイズの部分変更用に、HTMLの&lt;small&gt;・&lt;big&gt;・
     * &lt;span style="..."&gt;タグをそのまま素通りさせる
     * （例: "&lt;span style=\"color:red;font-size:20px\"&gt;強調&lt;/span&gt;"）。
     * ルビ用に&lt;ruby&gt;・&lt;rt&gt;タグも同様に素通りさせる
     * （例: "&lt;ruby&gt;漢字&lt;rt&gt;かんじ&lt;/rt&gt;&lt;/ruby&gt;"）。
     * style属性値は"（ダブルクォート）・&lt;・&gt;を含められない（属性の外へ抜け出せないようにするため）。
     */
    private static final Pattern SMALL_OPEN = Pattern.compile("(?i)<small>");
    private static final Pattern SMALL_CLOSE = Pattern.compile("(?i)</small>");
    private static final Pattern BIG_OPEN = Pattern.compile("(?i)<big>");
    private static final Pattern BIG_CLOSE = Pattern.compile("(?i)</big>");
    private static final Pattern RUBY_OPEN = Pattern.compile("(?i)<ruby>");
    private static final Pattern RUBY_CLOSE = Pattern.compile("(?i)</ruby>");
    private static final Pattern RT_OPEN = Pattern.compile("(?i)<rt>");
    private static final Pattern RT_CLOSE = Pattern.compile("(?i)</rt>");
    private static final Pattern SPAN_OPEN = Pattern.compile("(?i)<span\\s+style=\"([^\"<>]*)\">");
    private static final Pattern SPAN_CLOSE = Pattern.compile("(?i)</span>");

    /** 上記タグを退避させる際の目印（他の目印とかぶらない専用の制御文字）。 */
    private static final char HTML_TAG_MARKER = '';
    private static final Pattern HTML_TAG_PLACEHOLDER =
        Pattern.compile(HTML_TAG_MARKER + "(\\d+)" + HTML_TAG_MARKER);

    /** 数式($...$)の中身を、他の記法の正規表現に巻き込まれないよう一時的に退避させる目印。 */
    private static final char MATH_MARKER = '';
    private static final Pattern MATH_PLACEHOLDER =
        Pattern.compile(MATH_MARKER + "(\\d+)" + MATH_MARKER);

    /** バックスラッシュでエスケープできる記号（このMarkdownレンダラーが記法として使う文字一式）。 */
    private static final String ESCAPABLE_CHARS = "\\`*_{}[]()#+-.!~<>";

    /** エスケープした記号を、後段の処理に巻き込まれないよう一時的に囲む制御文字（通常の文章には現れない）。 */
    private static final char SENTINEL_START = '';
    private static final char SENTINEL_END = '';
    private static final Pattern ESCAPE_SENTINEL =
        Pattern.compile(SENTINEL_START + "(\\d+)" + SENTINEL_END);

    /** 段落内の行末半角スペース2個や&lt;br&gt;タグによる改行指示を、inline()の途中でエスケープされない
     *  目印として一時的に埋め込む（最終的にinline()の末尾で実際の&lt;br/&gt;に置き換える）。 */
    private static final String HARD_BREAK_MARKER = " HARD_BREAK ";

    public String render(String markdown) {
        if (markdown == null) {
            return "";
        }

        Map<String, String> footnotes = new LinkedHashMap<>();
        List<String> bodyLines = new ArrayList<>();
        for (String line : markdown.split("\n", -1)) {
            Matcher footnoteDef = FOOTNOTE_DEF.matcher(line.trim());
            if (footnoteDef.matches()) {
                footnotes.put(footnoteDef.group(1), footnoteDef.group(2));
            } else {
                bodyLines.add(line);
            }
        }

        StringBuilder html = new StringBuilder();
        List<String> paragraphBuffer = new ArrayList<>();
        List<Boolean> paragraphHardBreaks = new ArrayList<>();
        boolean inCodeBlock = false;
        StringBuilder codeBuffer = new StringBuilder();
        List<String> tableBuffer = new ArrayList<>();
        // 直前の行に中央寄せ指定({: align="center"})があったかどうか。次に来る画像段落・テーブルにだけ
        // 適用し、消費したら必ずfalseへ戻す（他のブロック種別に紛れ込んで居座らないようにするため）。
        boolean centerNextBlock = false;
        boolean currentTableCentered = false;

        for (int i = 0; i < bodyLines.size(); i++) {
            String line = bodyLines.get(i);

            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    html.append("<pre><code>").append(escapeHtml(codeBuffer.toString())).append("</code></pre>\n");
                    codeBuffer.setLength(0);
                    inCodeBlock = false;
                } else {
                    flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                    centerNextBlock = false;
                    inCodeBlock = true;
                }
                continue;
            }
            if (inCodeBlock) {
                codeBuffer.append(line).append("\n");
                continue;
            }

            if (line.trim().startsWith("|")) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                if (tableBuffer.isEmpty()) {
                    currentTableCentered = centerNextBlock;
                }
                centerNextBlock = false;
                tableBuffer.add(line.trim());
                boolean nextIsTable = i + 1 < bodyLines.size() && bodyLines.get(i + 1).trim().startsWith("|");
                if (!nextIsTable) {
                    html.append(renderTable(tableBuffer, currentTableCentered));
                    tableBuffer.clear();
                    currentTableCentered = false;
                }
                continue;
            }

            String trimmed = line.trim();

            if (CENTER_DIRECTIVE_LINE.matcher(trimmed).matches()) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = true;
                continue;
            }

            if (trimmed.matches("^(\\*\\*\\*|---)$")) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = false;
                html.append("<hr/>\n");
                continue;
            }

            Matcher heading = HEADING.matcher(trimmed);
            if (heading.matches()) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = false;
                int level = heading.group(1).length();
                html.append("<h").append(level).append('>')
                    .append(inline(heading.group(2), footnotes))
                    .append("</h").append(level).append(">\n");
                continue;
            }

            if (trimmed.startsWith("> ")) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = false;
                html.append("<blockquote>").append(inline(trimmed.substring(2), footnotes)).append("</blockquote>\n");
                continue;
            }

            if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ")) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = false;
                boolean checked = trimmed.startsWith("- [x] ");
                String text = trimmed.substring(6);
                html.append("<div class=\"checkbox-item\"><input type=\"checkbox\" disabled")
                    .append(checked ? " checked" : "").append("/> ")
                    .append(inline(text, footnotes)).append("</div>\n");
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = false;
                html.append("<li>").append(inline(trimmed.substring(2), footnotes)).append("</li>\n");
                continue;
            }

            if (trimmed.matches("^\\d+\\.\\s.+")) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = false;
                String text = trimmed.replaceFirst("^\\d+\\.\\s", "");
                html.append("<li class=\"ordered\">").append(inline(text, footnotes)).append("</li>\n");
                continue;
            }

            if (trimmed.startsWith(": ")) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = false;
                html.append("<dd>").append(inline(trimmed.substring(2), footnotes)).append("</dd>\n");
                continue;
            }

            if (trimmed.isEmpty()) {
                flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);
                centerNextBlock = false;
                continue;
            }

            paragraphBuffer.add(trimmed);
            paragraphHardBreaks.add(line.endsWith("  "));
        }
        flushParagraph(html, paragraphBuffer, paragraphHardBreaks, footnotes, centerNextBlock);

        return wrapListItems(html.toString());
    }

    private void flushParagraph(StringBuilder html, List<String> buffer, List<Boolean> hardBreaks,
                                 Map<String, String> footnotes, boolean centered) {
        if (buffer.isEmpty()) {
            return;
        }
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < buffer.size(); i++) {
            if (i > 0) {
                joined.append(Boolean.TRUE.equals(hardBreaks.get(i - 1))
                    ? HARD_BREAK_MARKER : " ");
            }
            joined.append(buffer.get(i));
        }
        // text-align:centerで段落内の文章そのものを中央寄せにする。画像はCSSでdisplay:blockに
        // しているためこれだけでは効かないので、inline()側で画像自体にもmargin:0 autoを別途適用する
        // （キャプション文＋画像のような混在段落でも両方中央に揃う）。
        html.append("<p").append(centered ? " style=\"text-align:center;\"" : "").append('>')
            .append(inline(joined.toString(), footnotes, centered)).append("</p>\n");
        buffer.clear();
        hardBreaks.clear();
    }

    private String renderTable(List<String> rows, boolean centered) {
        StringBuilder sb = new StringBuilder("<table");
        if (centered) {
            // tableは既定でshrink-to-fit幅のブロック要素なので、margin:autoで中央寄せできる
            // （画像と違いdisplay:blockへの上書きは不要）。
            sb.append(" style=\"margin:0 auto;\"");
        }
        sb.append(">\n");
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r);
            String stripped = row.replace("|", "").replace("-", "").replace(":", "").trim();
            if (r == 1 && stripped.isEmpty()) {
                continue;
            }
            String inner = row.substring(1, row.length() - (row.endsWith("|") ? 1 : 0));
            String[] cells = inner.split("\\|", -1);
            String tag = r == 0 ? "th" : "td";
            sb.append("<tr>");
            for (String cell : cells) {
                sb.append('<').append(tag).append('>')
                  .append(inline(cell.trim(), Map.of()))
                  .append("</").append(tag).append('>');
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");
        return sb.toString();
    }

    private String wrapListItems(String html) {
        String[] lines = html.split("\n");
        StringBuilder out = new StringBuilder();
        boolean inUl = false;
        boolean inOl = false;
        for (String line : lines) {
            boolean isOrdered = line.contains("class=\"ordered\"");
            boolean isLi = line.trim().startsWith("<li");
            if (isLi && isOrdered) {
                if (inUl) {
                    out.append("</ul>\n");
                    inUl = false;
                }
                if (!inOl) {
                    out.append("<ol>\n");
                    inOl = true;
                }
            } else if (isLi) {
                if (inOl) {
                    out.append("</ol>\n");
                    inOl = false;
                }
                if (!inUl) {
                    out.append("<ul>\n");
                    inUl = true;
                }
            } else {
                if (inUl) {
                    out.append("</ul>\n");
                    inUl = false;
                }
                if (inOl) {
                    out.append("</ol>\n");
                    inOl = false;
                }
            }
            out.append(line).append('\n');
        }
        if (inUl) {
            out.append("</ul>\n");
        }
        if (inOl) {
            out.append("</ol>\n");
        }
        return out.toString();
    }

    private String inline(String text, Map<String, String> footnotes) {
        return inline(text, footnotes, false);
    }

    /**
     * centerImagesがtrueのとき、このinline()呼び出しで出てくる画像すべてに中央寄せスタイルを適用する
     * （{@link #CENTER_DIRECTIVE_LINE}をrender()がブロック単位で判定し、対象の段落にだけ渡す）。
     */
    private String inline(String text, Map<String, String> footnotes, boolean centerImages) {
        // 数式($...$・$$...$$)は中身にMarkdown記法と紛らわしい記号（*, _, `など）を含みうるため、
        // 真っ先に退避させ、他のどの処理にも一切手を加えられないようにする。
        List<String> mathSegments = new ArrayList<>();
        String withMathProtected = extractMath(text, mathSegments);

        // <small>・<span style="...">のようなHTMLタグも、HTMLエスケープでつぶされないよう、
        // 中身は素通しのまま開始/終了タグ自体だけを退避させる（中身には他のMarkdown記法を使える）。
        List<String> htmlTagSegments = new ArrayList<>();
        String withHtmlTagsProtected = protectInlineHtmlTags(withMathProtected, htmlTagSegments);

        // エスケープ（\*のような記法）と<br>タグは、HTMLエスケープや他の記法の正規表現に
        // 巻き込まれないよう、生のテキストの段階で先に目印（プレースホルダー）に変換しておく。
        String withEscapes = applyEscapeSentinels(withHtmlTagsProtected);
        String withBreaks = BR_TAG.matcher(withEscapes).replaceAll(Matcher.quoteReplacement(HARD_BREAK_MARKER));

        String escaped = escapeHtml(withBreaks);

        escaped = IMAGE.matcher(escaped).replaceAll(mr -> {
            String alt = mr.group(1);
            String rawPath = mr.group(2);
            String width = mr.group(3);
            String height = mr.group(4);

            String encodedPath = URLEncoder.encode(rawPath, StandardCharsets.UTF_8);
            // src属性は実際にWebViewがfile://ベースURLに対して解決する本物のURLなので、
            // フォームエンコード特有の空白→"+"ではなく、URLのパスとして正しい"%20"にしておく。
            // 日本語ファイル名（デスクトップ版が元のファイル名をそのままコピー先のファイル名に
            // 含める仕様のため発生する）が、Android版のWebView（Chromium）ではエンコードなしだと
            // 正しく解決できずに画像が表示されないことを実機で確認済み。
            String srcPath = encodedPath.replace("+", "%20");

            StringBuilder img = new StringBuilder("<img alt=\"").append(alt)
                .append("\" src=\"").append(srcPath).append('"');
            if (width != null && !width.isEmpty()) {
                img.append(" width=\"").append(width).append('"');
            }
            if (height != null && !height.isEmpty()) {
                img.append(" height=\"").append(height).append('"');
            }
            if (centerImages) {
                // imgは全体CSSでdisplay:blockにしているため、親のtext-alignでは中央寄せできない。
                // ブロック要素自体をmargin:autoで中央寄せする。
                img.append(" style=\"display:block;margin:0 auto;\"");
            }
            img.append("/>");

            return "<a href=\"#image:" + encodedPath + "\">" + img + "</a>";
        });

        escaped = NOTE_LINK.matcher(escaped).replaceAll(mr ->
            "<a class=\"note-link\" href=\"#note:" + mr.group(1) + "\">" + mr.group(2) + "</a>");

        escaped = escaped.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "<b><i>$1</i></b>");
        escaped = escaped.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        escaped = escaped.replaceAll("\\*(.+?)\\*", "<i>$1</i>");
        escaped = escaped.replaceAll("~~(.+?)~~", "<s>$1</s>");
        escaped = escaped.replaceAll("__(.+?)__", "<u>$1</u>");
        escaped = escaped.replaceAll("`([^`]+?)`", "<code>$1</code>");

        if (!footnotes.isEmpty()) {
            Matcher footnoteRef = FOOTNOTE_REF.matcher(escaped);
            StringBuilder sb = new StringBuilder();
            while (footnoteRef.find()) {
                String key = footnoteRef.group(1);
                String note = footnotes.getOrDefault(key, "");
                String encodedNote = URLEncoder.encode(note, StandardCharsets.UTF_8);
                String replacement = "<sup><a class=\"footnote\" href=\"#footnote:" + encodedNote + "\">["
                    + key + "]</a></sup>";
                footnoteRef.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            footnoteRef.appendTail(sb);
            escaped = sb.toString();
        }

        escaped = restoreEscapeSentinels(escaped);
        escaped = escaped.replace(HARD_BREAK_MARKER, "<br/>\n");
        escaped = restoreInlineHtmlTags(escaped, htmlTagSegments);
        escaped = restoreMath(escaped, mathSegments);

        return escaped;
    }

    /**
     * `$...$`（インライン）・`$$...$$`（ブロック）を丸ごと退避させ、一覧に格納する。
     * デリミタ（$記号）ごと保存し、退避跡には目印を残す。KaTeXのauto-renderが
     * ブラウザ側で$記号を検出して数式に変換する前提のため、ここでは中身を一切解釈しない。
     */
    private String extractMath(String text, List<String> store) {
        text = extractByPattern(text, MATH_BLOCK, "$$", "$$", store);
        text = extractByPattern(text, MATH_INLINE, "$", "$", store);
        return text;
    }

    private String extractByPattern(String text, Pattern pattern, String left, String right,
                                     List<String> store) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            store.add(left + matcher.group(1) + right);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                MATH_MARKER + String.valueOf(store.size() - 1) + MATH_MARKER));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String restoreMath(String text, List<String> store) {
        Matcher matcher = MATH_PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(escapeHtml(store.get(index))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * &lt;small&gt;・&lt;span style="..."&gt;等のタグ自体（中身は含まない）を、HTMLエスケープや
     * 他の記法の正規表現に巻き込まれない目印へ一時的に置き換え、一覧に格納する。
     * 中身のテキストはそのまま残すため、太字**text**のような他のMarkdown記法もタグの中で使える。
     */
    private String protectInlineHtmlTags(String text, List<String> store) {
        text = protectTag(text, SMALL_OPEN, m -> "<small>", store);
        text = protectTag(text, SMALL_CLOSE, m -> "</small>", store);
        text = protectTag(text, BIG_OPEN, m -> "<big>", store);
        text = protectTag(text, BIG_CLOSE, m -> "</big>", store);
        text = protectTag(text, RUBY_OPEN, m -> "<ruby>", store);
        text = protectTag(text, RUBY_CLOSE, m -> "</ruby>", store);
        text = protectTag(text, RT_OPEN, m -> "<rt>", store);
        text = protectTag(text, RT_CLOSE, m -> "</rt>", store);
        text = protectTag(text, SPAN_OPEN, m -> "<span style=\"" + m.group(1) + "\">", store);
        text = protectTag(text, SPAN_CLOSE, m -> "</span>", store);
        return text;
    }

    private String protectTag(String text, Pattern pattern, java.util.function.Function<Matcher, String> literal,
                               List<String> store) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            store.add(literal.apply(matcher));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(
                HTML_TAG_MARKER + String.valueOf(store.size() - 1) + HTML_TAG_MARKER));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 退避させたタグをHTMLエスケープせずそのまま復元する（restoreMathとは異なりエスケープしない）。 */
    private String restoreInlineHtmlTags(String text, List<String> store) {
        Matcher matcher = HTML_TAG_PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(store.get(index)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** `\X`（Xはこのレンダラーが記法として使う記号）を、後段の処理に影響されない目印に置き換える。 */
    private String applyEscapeSentinels(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && ESCAPABLE_CHARS.indexOf(text.charAt(i + 1)) >= 0) {
                sb.append(SENTINEL_START).append((int) text.charAt(i + 1)).append(SENTINEL_END);
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 目印を、エスケープされた記号のHTML表示（必要ならHTMLエスケープ済み）に戻す。 */
    private String restoreEscapeSentinels(String text) {
        Matcher matcher = ESCAPE_SENTINEL.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            char original = (char) Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(escapeHtml(String.valueOf(original))));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }
}
