package com.mydictionary.core.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void rendersHeadingAndEmphasis() {
        String html = renderer.render("# タイトル\n\nこれは**太字**と*イタリック*です。");
        assertTrue(html.contains("<h1>タイトル</h1>"));
        assertTrue(html.contains("<b>太字</b>"));
        assertTrue(html.contains("<i>イタリック</i>"));
    }

    @Test
    void rendersHeadingsUpToLevelSix() {
        String html = renderer.render("#### 見出し4\n\n##### 見出し5\n\n###### 見出し6");
        assertTrue(html.contains("<h4>見出し4</h4>"));
        assertTrue(html.contains("<h5>見出し5</h5>"));
        assertTrue(html.contains("<h6>見出し6</h6>"));
    }

    @Test
    void rendersTrailingDoubleSpaceAsHardLineBreak() {
        String html = renderer.render("1行目  \n2行目");
        assertTrue(html.contains("1行目<br/>"));
        assertTrue(html.contains("2行目"));
    }

    @Test
    void rendersBrTagAsLineBreak() {
        String html = renderer.render("1行目<br>2行目");
        assertTrue(html.contains("1行目<br/>"));
        assertTrue(html.contains("2行目"));
    }

    @Test
    void rendersStrikethroughAndUnderline() {
        String html = renderer.render("~~打ち消し~~と__下線__です。");
        assertTrue(html.contains("<s>打ち消し</s>"));
        assertTrue(html.contains("<u>下線</u>"));
    }

    @Test
    void escapedSpecialCharactersAreLiteralAndNotInterpreted() {
        String html = renderer.render("\\*\\*強調\\*\\*と\\<br\\>タグ");
        assertTrue(html.contains("**強調**と&lt;br&gt;タグ"));
        assertTrue(!html.contains("<b>"));
        assertTrue(!html.contains("<br/>"));
    }

    @Test
    void passesMathThroughUnmodifiedForClientSideRendering() {
        String html = renderer.render("式は$a_1*b^2$と$$\\frac{1}{2}$$です。");
        assertTrue(html.contains("$a_1*b^2$"));
        assertTrue(html.contains("$$\\frac{1}{2}$$"));
        assertTrue(!html.contains("<i>"));
    }

    @Test
    void rendersParagraphBeforeTableWhenTableFollowsText() {
        String html = renderer.render("説明文です。\n|要素|説明|\n|---|---|\n|a|b|");
        int paragraphIndex = html.indexOf("<p>説明文です。</p>");
        int tableIndex = html.indexOf("<table>");
        assertTrue(paragraphIndex >= 0);
        assertTrue(tableIndex >= 0);
        assertTrue(paragraphIndex < tableIndex);
    }

    @Test
    void centersTableWhenPrecededByAlignCenterDirective() {
        String html = renderer.render("{: align=\"center\"}\n|列1|列2|\n|---|---|\n|a|b|");
        assertTrue(html.contains("<table style=\"margin:0 auto;\">"));
        assertTrue(!html.contains("{: align"));
    }

    @Test
    void doesNotCenterTableWithoutDirective() {
        String html = renderer.render("|列1|列2|\n|---|---|\n|a|b|");
        assertTrue(html.contains("<table>"));
        assertTrue(!html.contains("margin:0 auto"));
    }

    @Test
    void centerDirectiveDoesNotLeakPastAnUnrelatedBlock() {
        // 中央寄せ指定の直後が画像でもテーブルでもない場合（見出し）、指定は消費されず捨てられ、
        // その後に出てくる無関係なテーブルには影響しない。
        String html = renderer.render(
            "{: align=\"center\"}\n# 見出し\n|列1|列2|\n|---|---|\n|a|b|");
        assertTrue(html.contains("<table>"));
        assertTrue(!html.contains("margin:0 auto"));
    }

    @Test
    void centersPlainTextParagraphWhenPrecededByAlignCenterDirective() {
        String html = renderer.render("{: align=\"center\"}\nこれはキャプションです。");
        assertTrue(html.contains("<p style=\"text-align:center;\">これはキャプションです。</p>"));
    }

    @Test
    void centersCaptionParagraphFollowingCenteredImage() {
        // 画像とキャプション文それぞれの直前に指定を書けば、両方中央寄せになる。
        String html = renderer.render(
            "{: align=\"center\"}\n![写真](photo.png)\n\n{: align=\"center\"}\nキャプション文");
        assertTrue(html.contains("style=\"display:block;margin:0 auto;\""));
        assertTrue(html.contains("<p style=\"text-align:center;\">キャプション文</p>"));
    }

    @Test
    void doesNotCenterPlainParagraphWithoutDirective() {
        String html = renderer.render("通常の文章です。");
        assertTrue(html.contains("<p>通常の文章です。</p>"));
        assertTrue(!html.contains("text-align"));
    }

    @Test
    void rendersNoteLink() {
        String html = renderer.render("[[42|関連ノート]]を参照");
        assertTrue(html.contains("href=\"#note:42\""));
    }

    @Test
    void rendersCodeBlock() {
        String html = renderer.render("```\nSystem.out.println(1);\n```");
        assertTrue(html.contains("<pre><code>"));
    }

    @Test
    void rendersFootnoteAsClickableFragmentLink() {
        String html = renderer.render("本文[^1]です。\n\n[^1]: 補足説明");
        assertTrue(html.contains("href=\"#footnote:"));
        assertTrue(html.contains("[1]"));
    }

    @Test
    void rendersImageAsClickableFragmentLink() {
        String html = renderer.render("![説明](cat.png)");
        assertTrue(html.contains("href=\"#image:cat.png\""));
        assertTrue(html.contains("<img alt=\"説明\" src=\"cat.png\"/>"));
    }

    @Test
    void encodesNonAsciiImagePathForSrcAttribute() {
        String html = renderer.render("![説明](1_赤牌 写真.png)");
        assertTrue(html.contains("src=\"1_%E8%B5%A4%E7%89%8C%20%E5%86%99%E7%9C%9F.png\""));
        assertTrue(!html.contains("src=\"1_赤牌"));
        assertTrue(!html.contains("%2B"));
    }

    @Test
    void rendersImageWithWidthOnly() {
        String html = renderer.render("![my image](/img/myImage.jpg =300x)");
        assertTrue(html.contains("width=\"300\""));
        assertTrue(!html.contains("height=\""));
        // "/"はURLEncoderで"%2F"になる（既存の実装方針どおり、pathは丸ごとURLEncodeする）。
        assertTrue(html.contains("src=\"%2Fimg%2FmyImage.jpg\""));
    }

    @Test
    void rendersImageWithHeightOnly() {
        String html = renderer.render("![my image](/img/myImage.jpg =x300)");
        assertTrue(html.contains("height=\"300\""));
        assertTrue(!html.contains("width=\""));
    }

    @Test
    void rendersImageWithWidthAndHeight() {
        String html = renderer.render("![my image](/img/myImage.jpg =300x300)");
        assertTrue(html.contains("width=\"300\""));
        assertTrue(html.contains("height=\"300\""));
    }

    @Test
    void doesNotTreatOrdinaryPathContainingEqualsAndXAsSizeSpec() {
        String html = renderer.render("![説明](1_赤牌 写真.png)");
        assertTrue(!html.contains("width="));
        assertTrue(!html.contains("height="));
    }

    @Test
    void centersImageWhenPrecededByAlignCenterDirective() {
        String html = renderer.render("{: align=\"center\"}\n![my image](/img/myImage.jpg)");
        assertTrue(html.contains("style=\"display:block;margin:0 auto;\""));
        assertTrue(!html.contains("{: align"));
    }

    @Test
    void combinesCenterDirectiveWithSizeSpec() {
        String html = renderer.render("{: align=\"center\"}\n![my image](/img/myImage.jpg =300x300)");
        assertTrue(html.contains("width=\"300\""));
        assertTrue(html.contains("height=\"300\""));
        assertTrue(html.contains("style=\"display:block;margin:0 auto;\""));
    }

    @Test
    void doesNotCenterImageWithoutDirective() {
        String html = renderer.render("![my image](/img/myImage.jpg)");
        assertTrue(!html.contains("text-align"));
        assertTrue(!html.contains("margin:0 auto"));
    }

    @Test
    void rendersSmallTagAsIs() {
        String html = renderer.render("通常の文字と<small>小さい文字</small>です。");
        assertTrue(html.contains("<small>小さい文字</small>"));
    }

    @Test
    void rendersBigTagAsIs() {
        String html = renderer.render("通常の文字と<big>大きい文字</big>です。");
        assertTrue(html.contains("<big>大きい文字</big>"));
    }

    @Test
    void rendersRubyAndRtTagsAsIs() {
        String html = renderer.render("<ruby>漢字<rt>かんじ</rt></ruby>を読む。");
        assertTrue(html.contains("<ruby>漢字<rt>かんじ</rt></ruby>"));
    }

    @Test
    void rendersSpanStyleTagAsIsForColorAndSize() {
        String html = renderer.render("<span style=\"color:red;font-size:20px\">強調部分</span>です。");
        assertTrue(html.contains("<span style=\"color:red;font-size:20px\">強調部分</span>"));
    }

    @Test
    void allowsMarkdownEmphasisInsideInlineHtmlTags() {
        String html = renderer.render("<small>**太字の小文字**</small>");
        assertTrue(html.contains("<small><b>太字の小文字</b></small>"));
    }

    @Test
    void doesNotAllowBreakingOutOfSpanStyleAttribute() {
        String html = renderer.render("<span style=\"color:red\"onclick=\"x\">text</span>");
        // style属性値に"（ダブルクォート）を含められないため、SPAN_OPENパターンにマッチせず、
        // 通常のHTMLエスケープ対象としてそのまま無害化される。
        assertTrue(!html.contains("onclick=\"x\">"));
    }
}
