package com.mydictionary.core.editor;

import com.mydictionary.core.markdown.MarkdownRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlToMarkdownTest {

    /** Markdown → エディタ用HTML → Markdown と一周させる。 */
    private static String roundTrip(String markdown) {
        return HtmlToMarkdown.convert(new MarkdownRenderer().renderForEditor(markdown));
    }

    private static void assertStable(String markdown) {
        assertEquals(markdown, roundTrip(markdown));
    }

    // ---- レンダラーの出力を、そのまま元のMarkdownへ戻せる

    @Test
    void plainTextAndParagraphs() {
        assertStable("こんにちは");
        assertStable("一段落目\n\n二段落目");
    }

    @Test
    void softLineBreaksInsideParagraphBecomeSpaces() {
        assertEquals("一行目 二行目", roundTrip("一行目\n二行目"));
    }

    @Test
    void enterKeyLineBreaksAreWrittenAsBrTags() {
        assertStable("一行目<br>二行目<br>三行目");
    }

    @Test
    void headings() {
        assertStable("# 大見出し");
        assertStable("### 小見出し\n\n本文");
    }

    @Test
    void inlineStyles() {
        assertStable("**太字** と *斜体* と __下線__ と ~~消し線~~ と `code`");
        assertStable("***太字斜体***");
    }

    @Test
    void spansSmallBigRuby() {
        assertStable("<span style=\"color:red;font-size:20pt\">赤</span>");
        assertStable("<small>小</small>と<big>大</big>");
        assertStable("<ruby>漢字<rt>かんじ</rt></ruby>");
        assertStable("**<span style=\"background-color:yellow\">強調</span>**");
    }

    @Test
    void lists() {
        assertStable("- a\n- b");
        assertStable("1. a\n2. b\n3. c");
        assertStable("- [x] 済\n- [ ] 未");
        assertStable("- a\n- b\n\n1. c\n2. d");
    }

    @Test
    void quoteRuleAndCodeBlock() {
        assertStable("> 引用1\n> 引用2");
        assertStable("上\n\n---\n\n下");
        assertStable("```\nif (a < b && c > d) {\n  x();\n}\n```");
    }

    @Test
    void table() {
        assertStable("| a | b |\n| --- | --- |\n| 1 | 2 |");
        assertStable("| 項目 | 値 |\n| --- | --- |\n| **太** | x<br>y |");
    }

    @Test
    void imagesIncludingSizeAndJapaneseFileNames() {
        assertStable("![図](a.png)");
        assertStable("![図](a.png =300x)");
        assertStable("![図](a.png =x200)");
        assertStable("![図](a.png =300x200)");
        assertStable("![写真](1700_日本語 ファイル.png)");
    }

    @Test
    void mathStaysAsOriginalSource() {
        assertStable("面積は $x^2 + y_1$ です");
        assertStable("$$\\int_0^1 f(x)\\,dx$$");
        assertStable("$a * b$ と *斜体*");
    }

    @Test
    void noteLinks() {
        assertStable("[[12|別のノート]] を参照");
    }

    @Test
    void footnoteReferencesKeptAndDefinitionsLeftToCaller() {
        assertEquals("本文[^1]です", roundTrip("本文[^1]です\n\n[^1]: 注の内容"));
    }

    @Test
    void alignmentDirectives() {
        assertStable("{: align=\"center\"}\n中央の文章");
        assertStable("{: align=\"right\"}\n![図](a.png)");
        assertStable("{: align=\"center\"}\n# 中央見出し");
        assertStable("{: align=\"center\"}\n- 項目");
        assertStable("{: align=\"center\"}\n| a | b |\n| --- | --- |\n| 1 | 2 |");
        assertStable("左の文章\n\n{: align=\"right\"}\n右の文章\n\n左に戻る");
    }

    @Test
    void imageLineAlignedAloneKeepsCaptionUnaligned() {
        String md = "キャプション\n{: align=\"center\"}\n![図](a.png)";
        assertEquals("キャプション\n\n{: align=\"center\"}\n![図](a.png)", roundTrip(md));
    }

    @Test
    void markdownSpecialCharactersInPlainTextSurviveARoundTrip() {
        assertStable("\\*星\\*は斜体にならない");
        assertStable("snake_case_name と a\\_\\_b");
        assertStable("\\# 見出しではない");
        assertStable("\\> 引用ではない");
        assertStable("1\\. 番号ではない");
        assertStable("\\- 箇条書きではない");
        // 「\U」のように記法ではない並びの\は、そのままで表示が変わらないので打ち消しを付け直さない。
        assertEquals("C:\\Users\\x", roundTrip("C:\\\\Users\\\\x"));
        assertStable("\\[[12|リンクではない]]");
    }

    // ---- ブラウザの編集機能が作る形

    @Test
    void browserBoldItalicUnderlineStrike() {
        assertEquals("**a***b*__c__~~d~~",
            HtmlToMarkdown.convert("<b>a</b><i>b</i><u>c</u><strike>d</strike>"));
        assertEquals("**a**", HtmlToMarkdown.convert("<strong>a</strong>"));
    }

    @Test
    void divsBecomeParagraphsAndBareTextIsAParagraph() {
        assertEquals("a\n\nb", HtmlToMarkdown.convert("<div>a</div><div>b</div>"));
        assertEquals("裸の文字<br>改行", HtmlToMarkdown.convert("裸の文字<br>改行"));
    }

    @Test
    void singleTrailingLineBreakIsJustACaretMarkerAndDropped() {
        assertEquals("A", HtmlToMarkdown.convert("<p>A<br></p>"));
        assertEquals("A<br>B", HtmlToMarkdown.convert("<p>A <br> B<br></p>"));
        assertEquals("", HtmlToMarkdown.convert("<p><br></p>"));
    }

    @Test
    void lineBreaksAddedAtTheEndOfALineToOpenSpaceAreKept() {
        // 行末でエンターを押すと「文章+br+br」になる。2つ目のbrは空行として見えているので残す。
        assertEquals("A<br><br>", HtmlToMarkdown.convert("<p>A<br><br></p>"));
        assertEquals("A<br><br><br>", HtmlToMarkdown.convert("<p>A<br><br><br></p>"));
        // brが色などのspanの中にあっても数える。
        assertEquals("<span style=\"color:red\">A</span><br><br>",
            HtmlToMarkdown.convert("<p><span style=\"color: red;\">A<br></span><br></p>"));
        assertEquals("A<br><br>\n\n# 見出し", HtmlToMarkdown.convert("<p>A<br><br></p><h1>見出し</h1>"));
    }

    @Test
    void spaceBeforeAHeadingSurvivesRepeatedSaves() {
        String md = "前の文<br><br>\n\n# 見出し";
        MarkdownRenderer renderer = new MarkdownRenderer();
        String once = HtmlToMarkdown.convert(renderer.renderForEditor(md).replace("<br/>\n", "<br>"));
        assertEquals(md, once);
        assertEquals(md, HtmlToMarkdown.convert(renderer.renderForEditor(once).replace("<br/>\n", "<br>")));
    }

    @Test
    void emptyBlocksBetweenContentAreKeptAsBlankLinesButNotAtTheEnds() {
        assertEquals("a\n\n<br>\n\nb", HtmlToMarkdown.convert("<p>a</p><div><br></div><p>b</p>"));
        assertEquals("a\n\n<br><br>\n\nb", HtmlToMarkdown.convert("<p>a</p><div><br><br></div><p>b</p>"));
        assertEquals("a", HtmlToMarkdown.convert("<p><br></p><p>a</p><div><br></div>"));
    }

    @Test
    void colorPutDirectlyOnAnInlineFormattingElementIsKept() {
        // ブラウザは、書式のかかった範囲ちょうどを選んで色を付けると、b・i等へ直接styleを付ける。
        assertEquals("<span style=\"color:red\">*斜体*</span>",
            HtmlToMarkdown.convert("<p><i style=\"color: red;\">斜体</i></p>"));
        assertEquals("<span style=\"color:red\">**太字**</span>",
            HtmlToMarkdown.convert("<p><b style=\"color: red;\">太字</b></p>"));
        assertEquals("<span style=\"color:red\"><big>大</big></span>",
            HtmlToMarkdown.convert("<p><big style=\"color: red;\">大</big></p>"));
        assertEquals("<ruby>漢字<rt><span style=\"color:red\">かんじ</span></rt></ruby>",
            HtmlToMarkdown.convert("<p><ruby>漢字<rt style=\"color: red;\">かんじ</rt></ruby></p>"));
        assertEquals("<span style=\"color:red\">`コード`</span>",
            HtmlToMarkdown.convert("<p><code style=\"color: red;\">コード</code></p>"));
    }

    @Test
    void colorPutOnABlockElementIsKept() {
        assertEquals("# <span style=\"color:red\">見出し</span>",
            HtmlToMarkdown.convert("<h1 style=\"color: red;\">見出し</h1>"));
        assertEquals("- <span style=\"color:blue\">項目</span>",
            HtmlToMarkdown.convert("<ul><li style=\"color: blue;\">項目</li></ul>"));
        assertEquals("<span style=\"color:red\">本文</span>",
            HtmlToMarkdown.convert("<p style=\"color: red;\">本文</p>"));
        assertEquals("| <span style=\"color:red\">a</span> |\n| --- |",
            HtmlToMarkdown.convert("<table><tr><th style=\"color: red;\">a</th></tr></table>"));
    }

    @Test
    void nonBreakingSpacesBecomePlainSpaces() {
        assertEquals("a b", HtmlToMarkdown.convert("<p>a&nbsp;b</p>"));
    }

    @Test
    void cssStylesFromBrowserCommandsAreNormalized() {
        assertEquals("<span style=\"color:#ff0000\">赤</span>",
            HtmlToMarkdown.convert("<p><span style=\"color: rgb(255, 0, 0);\">赤</span></p>"));
        assertEquals("<span style=\"font-family:'Noto Sans JP'\">字</span>",
            HtmlToMarkdown.convert("<p><span style=\"font-family: &quot;Noto Sans JP&quot;;\">字</span></p>"));
        assertEquals("**太**", HtmlToMarkdown.convert("<p><span style=\"font-weight: bold;\">太</span></p>"));
        assertEquals("<span style=\"color:blue\">青</span>",
            HtmlToMarkdown.convert("<p><font color=\"blue\">青</font></p>"));
        assertEquals("プレーン", HtmlToMarkdown.convert("<p><span style=\"line-height: 2;\">プレーン</span></p>"));
    }

    @Test
    void blockAlignmentFromTextAlignStyle() {
        assertEquals("{: align=\"center\"}\n中央",
            HtmlToMarkdown.convert("<p style=\"text-align: center;\">中央</p>"));
        assertEquals("左", HtmlToMarkdown.convert("<p style=\"text-align: left;\">左</p>"));
        assertEquals("{: align=\"right\"}\n![a](b.png)", HtmlToMarkdown.convert(
            "<p style=\"text-align: right;\"><a href=\"#image:b.png\"><img alt=\"a\" src=\"b.png\" "
                + "style=\"display: block; margin: 0px 0px 0px auto;\"></a></p>"));
    }

    @Test
    void tableAlignmentFromBrowserSerializedMargin() {
        String table = "<tr><th>a</th></tr><tr><td>1</td></tr>";
        assertEquals("{: align=\"center\"}\n| a |\n| --- |\n| 1 |",
            HtmlToMarkdown.convert("<table style=\"margin: 0px auto;\">" + table + "</table>"));
        assertEquals("{: align=\"right\"}\n| a |\n| --- |\n| 1 |",
            HtmlToMarkdown.convert("<table style=\"margin-left: auto; margin-right: 0px;\">" + table + "</table>"));
        assertEquals("{: align=\"center\"}\n| a |\n| --- |\n| 1 |",
            HtmlToMarkdown.convert("<table data-align=\"center\">" + table + "</table>"));
    }

    @Test
    void emptyBlocksProduceNothing() {
        assertEquals("", HtmlToMarkdown.convert(""));
        assertEquals("", HtmlToMarkdown.convert(null));
        assertEquals("a", HtmlToMarkdown.convert("<p><br></p><p>a</p><div><br></div>"));
    }

    @Test
    void convertedMarkdownRendersTheSameHtmlAsTheOriginal() {
        String md = "# 題\n\n**太** と <span style=\"color:red\">赤</span><br>改行\n\n{: align=\"center\"}\n![図](a.png =100x)\n\n- a\n- b\n\n$x^2$";
        MarkdownRenderer renderer = new MarkdownRenderer();
        assertEquals(renderer.render(md), renderer.render(roundTrip(md)));
    }
}
