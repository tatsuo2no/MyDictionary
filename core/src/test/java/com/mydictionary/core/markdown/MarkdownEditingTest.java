package com.mydictionary.core.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkdownEditingTest {

    private static String selected(MarkdownEditing.Result r) {
        return r.text().substring(r.selectionStart(), r.selectionEnd());
    }

    // ---- 太字・斜体・下線・打ち消し線・インラインコード

    @Test
    void wrapsSelectionWithBoldMarker() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("これは重要です", 3, 5, "**");
        assertEquals("これは**重要**です", r.text());
        assertEquals("**重要**", selected(r));
    }

    @Test
    void unwrapsWhenMarkersAreInsideSelection() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("これは**重要**です", 3, 9, "**");
        assertEquals("これは重要です", r.text());
        assertEquals("重要", selected(r));
    }

    @Test
    void unwrapsWhenMarkersAreJustOutsideSelection() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("これは**重要**です", 5, 7, "**");
        assertEquals("これは重要です", r.text());
        assertEquals("重要", selected(r));
    }

    @Test
    void emptySelectionInsertsMarkerPairWithCaretBetween() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("ab", 1, 1, "**");
        assertEquals("a****b", r.text());
        assertEquals(3, r.selectionStart());
        assertEquals(3, r.selectionEnd());
    }

    @Test
    void surroundingSpacesAreExcludedFromTheWrappedRange() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("a 重要 b", 1, 5, "**");
        assertEquals("a **重要** b", r.text());
    }

    @Test
    void wrapsEachLineSeparatelyForMultiLineSelection() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("一行目\n二行目", 0, 7, "**");
        assertEquals("**一行目**\n**二行目**", r.text());
    }

    @Test
    void italicToggleDoesNotTreatBoldAsItalic() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("**太字**", 0, 6, "*");
        assertEquals("***太字***", r.text());
    }

    @Test
    void boldToggleOffOfBoldItalicLeavesItalic() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("***両方***", 0, 7, "**");
        assertEquals("*両方*", r.text());
    }

    @Test
    void italicToggleOffOfPlainItalic() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("*斜体*", 0, 4, "*");
        assertEquals("斜体", r.text());
    }

    @Test
    void underlineDoesNotUnwrapTwoSeparateUnderlinedWords() {
        String text = "__a__ と __b__";
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap(text, 0, text.length(), "__");
        assertEquals("____a__ と __b____", r.text());
    }

    @Test
    void inlineCodeWrapsWithBackticks() {
        MarkdownEditing.Result r = MarkdownEditing.toggleWrap("x = 1", 0, 5, "`");
        assertEquals("`x = 1`", r.text());
    }

    // ---- span（文字色・背景色・フォント・サイズ）

    @Test
    void wrapsSelectionInSpanWithColor() {
        MarkdownEditing.Result r = MarkdownEditing.setSpanStyle("赤い文字", 0, 2, "color", "red");
        assertEquals("<span style=\"color:red\">赤い</span>文字", r.text());
    }

    @Test
    void addsSecondPropertyToExistingSpanInsideSelection() {
        String text = "<span style=\"color:red\">赤</span>";
        MarkdownEditing.Result r = MarkdownEditing.setSpanStyle(text, 0, text.length(), "background-color", "yellow");
        assertEquals("<span style=\"color:red;background-color:yellow\">赤</span>", r.text());
    }

    @Test
    void replacesSamePropertyInExistingSpanJustOutsideSelection() {
        String text = "<span style=\"color:red\">赤</span>";
        int a = text.indexOf("赤");
        MarkdownEditing.Result r = MarkdownEditing.setSpanStyle(text, a, a + 1, "color", "blue");
        assertEquals("<span style=\"color:blue\">赤</span>", r.text());
        assertEquals("<span style=\"color:blue\">赤</span>", selected(r));
    }

    @Test
    void removingLastPropertyUnwrapsTheSpan() {
        String text = "<span style=\"color:red\">赤</span>";
        MarkdownEditing.Result r = MarkdownEditing.setSpanStyle(text, 0, text.length(), "color", null);
        assertEquals("赤", r.text());
    }

    @Test
    void removingPropertyWhenNoSpanExistsDoesNothing() {
        MarkdownEditing.Result r = MarkdownEditing.setSpanStyle("文字", 0, 2, "color", null);
        assertEquals("文字", r.text());
    }

    @Test
    void spanWithSeparateAdjacentSpansIsNotMergedIntoOne() {
        String text = "<span style=\"color:red\">a</span><span style=\"color:blue\">b</span>";
        MarkdownEditing.Result r = MarkdownEditing.setSpanStyle(text, 0, text.length(), "font-size", "20pt");
        assertEquals("<span style=\"font-size:20pt\">" + text + "</span>", r.text());
    }

    @Test
    void fontFamilyWithQuotesIsKeptAsIs() {
        MarkdownEditing.Result r = MarkdownEditing.setSpanStyle("本文", 0, 2, "font-family", "'Meiryo UI'");
        assertEquals("<span style=\"font-family:'Meiryo UI'\">本文</span>", r.text());
    }

    // ---- ルビ

    @Test
    void appliesRubyToSelection() {
        MarkdownEditing.Result r = MarkdownEditing.applyRuby("漢字です", 0, 2, "かんじ");
        assertEquals("<ruby>漢字<rt>かんじ</rt></ruby>です", r.text());
    }

    @Test
    void replacesExistingRubyReading() {
        String text = "<ruby>漢字<rt>かんじ</rt></ruby>";
        MarkdownEditing.Result r = MarkdownEditing.applyRuby(text, 0, text.length(), "カンジ");
        assertEquals("<ruby>漢字<rt>カンジ</rt></ruby>", r.text());
    }

    @Test
    void emptyReadingRemovesExistingRuby() {
        String text = "<ruby>漢字<rt>かんじ</rt></ruby>";
        MarkdownEditing.Result r = MarkdownEditing.applyRuby(text, 0, text.length(), "");
        assertEquals("漢字", r.text());
    }

    @Test
    void rubyWithoutSelectionDoesNothing() {
        MarkdownEditing.Result r = MarkdownEditing.applyRuby("漢字", 1, 1, "かんじ");
        assertEquals("漢字", r.text());
    }

    // ---- 配置

    @Test
    void insertsCenterDirectiveBeforeParagraphAtCaret() {
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("文章です", 2, 2, "center");
        assertEquals("{: align=\"center\"}\n文章です", r.text());
    }

    @Test
    void alignsOnlyTheCaretLineOfAMultiLineParagraph() {
        // 2行目(カーソル行)だけを右揃えにし、3行目は元の配置(左)に戻す。1行目は段落の前半として左のまま。
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("一行目\n二行目\n三行目", 5, 5, "right");
        assertEquals("一行目\n{: align=\"right\"}\n二行目\n{: align=\"left\"}\n三行目", r.text());
    }

    @Test
    void alignsImageLineAloneNotTheCaptionAbove() {
        String text = "キャプション\n![図](a.png)";
        MarkdownEditing.Result r = MarkdownEditing.setAlignment(text, 10, 10, "center");
        assertEquals("キャプション\n{: align=\"center\"}\n![図](a.png)", r.text());
        String html = new MarkdownRenderer().render(r.text());
        assertTrue(html.contains("<p>キャプション</p>"), html);
        assertTrue(html.contains("margin:0 auto"), html);
    }

    @Test
    void alignsFirstLineOfParagraphAndKeepsRestLeft() {
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("一行目\n二行目", 0, 0, "center");
        assertEquals("{: align=\"center\"}\n一行目\n{: align=\"left\"}\n二行目", r.text());
    }

    @Test
    void alignsSelectedLinesTogetherWithOneDirective() {
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("a\nb\nc\nd", 2, 5, "center");
        assertEquals("a\n{: align=\"center\"}\nb\nc\n{: align=\"left\"}\nd", r.text());
    }

    @Test
    void leftOnLineInsideAlignedParagraphSplitsOnlyThatLine() {
        String text = "{: align=\"center\"}\n一\n二\n三";
        MarkdownEditing.Result r = MarkdownEditing.setAlignment(text, text.indexOf('二'), text.indexOf('二'), "left");
        // 一と三は中央のまま、二だけ左。
        assertEquals("{: align=\"center\"}\n一\n{: align=\"left\"}\n二\n{: align=\"center\"}\n三", r.text());
    }

    @Test
    void leftOnHeadOfAlignedParagraphKeepsTheRestAligned() {
        String text = "{: align=\"center\"}\n一\n二";
        MarkdownEditing.Result r = MarkdownEditing.setAlignment(text, text.indexOf('一'), text.indexOf('一'), "left");
        assertEquals("一\n{: align=\"center\"}\n二", r.text());
    }

    @Test
    void sameAlignmentInsideAlignedParagraphChangesNothing() {
        String text = "{: align=\"center\"}\n一\n二";
        MarkdownEditing.Result r = MarkdownEditing.setAlignment(text, text.indexOf('二'), text.indexOf('二'), "center");
        assertEquals(text, r.text());
    }

    @Test
    void lastLineOfParagraphNeedsNoTrailingDirective() {
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("一\n二", 2, 2, "right");
        assertEquals("一\n{: align=\"right\"}\n二", r.text());
    }

    @Test
    void replacesExistingDirective() {
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("{: align=\"center\"}\n文章", 20, 20, "right");
        assertEquals("{: align=\"right\"}\n文章", r.text());
    }

    @Test
    void leftAlignRemovesDirective() {
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("{: align=\"center\"}\n文章", 20, 20, "left");
        assertEquals("文章", r.text());
    }

    @Test
    void alignsEveryListItemInSelectionSeparately() {
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("- a\n- b", 0, 7, "center");
        assertEquals("{: align=\"center\"}\n- a\n{: align=\"center\"}\n- b", r.text());
    }

    @Test
    void alignsTableWithSingleDirectiveBeforeFirstRow() {
        String table = "|a|b|\n|---|---|\n|1|2|";
        MarkdownEditing.Result r = MarkdownEditing.setAlignment(table, 10, 10, "center");
        assertEquals("{: align=\"center\"}\n" + table, r.text());
    }

    @Test
    void doesNotTouchLinesInsideCodeBlock() {
        String text = "```\n文章\n```";
        MarkdownEditing.Result r = MarkdownEditing.setAlignment(text, 5, 5, "center");
        assertEquals(text, r.text());
    }

    @Test
    void alignmentIsRenderedByMarkdownRenderer() {
        MarkdownEditing.Result r = MarkdownEditing.setAlignment("本文", 0, 0, "right");
        assertTrue(new MarkdownRenderer().render(r.text()).contains("<p style=\"text-align:right;\">本文</p>"));
    }

    // ---- リスト・引用

    @Test
    void addsBulletToEachSelectedLine() {
        MarkdownEditing.Result r = MarkdownEditing.toggleBulletList("a\nb\nc", 0, 5);
        assertEquals("- a\n- b\n- c", r.text());
    }

    @Test
    void removesBulletWhenAllLinesHaveIt() {
        MarkdownEditing.Result r = MarkdownEditing.toggleBulletList("- a\n- b", 0, 7);
        assertEquals("a\nb", r.text());
    }

    @Test
    void mixedLinesGetBulletOnAll() {
        MarkdownEditing.Result r = MarkdownEditing.toggleBulletList("- a\nb", 0, 5);
        assertEquals("- a\n- b", r.text());
    }

    @Test
    void numberedListIsNumberedInOrderAndReplacesBullets() {
        MarkdownEditing.Result r = MarkdownEditing.toggleNumberedList("- a\nb\n\nc", 0, 8);
        assertEquals("1. a\n2. b\n\n3. c", r.text());
    }

    @Test
    void numberedToggleOffRemovesNumbers() {
        MarkdownEditing.Result r = MarkdownEditing.toggleNumberedList("1. a\n2. b", 0, 9);
        assertEquals("a\nb", r.text());
    }

    @Test
    void caretOnlyAffectsItsOwnLine() {
        MarkdownEditing.Result r = MarkdownEditing.toggleBulletList("a\nb", 3, 3);
        assertEquals("a\n- b", r.text());
    }

    @Test
    void quoteTogglesOnAndOff() {
        MarkdownEditing.Result on = MarkdownEditing.toggleQuote("引用文", 0, 3);
        assertEquals("> 引用文", on.text());
        MarkdownEditing.Result off = MarkdownEditing.toggleQuote(on.text(), 0, on.text().length());
        assertEquals("引用文", off.text());
    }

    // ---- 見出し

    @Test
    void turnsCaretLineIntoHeadingOfTheGivenLevel() {
        MarkdownEditing.Result r = MarkdownEditing.setHeading("前\n見出しにする\n後", 4, 4, 3);
        assertEquals("前\n### 見出しにする\n後", r.text());
        assertTrue(new MarkdownRenderer().render(r.text()).contains("<h3>見出しにする</h3>"));
    }

    @Test
    void changingHeadingLevelReplacesTheExistingMarks() {
        assertEquals("## 題", MarkdownEditing.setHeading("##### 題", 0, 0, 2).text());
        assertEquals("# 題", MarkdownEditing.setHeading("- 題", 0, 0, 1).text());
        assertEquals("#### 題", MarkdownEditing.setHeading("> 題", 0, 0, 4).text());
        assertEquals("# 題", MarkdownEditing.setHeading("1. 題", 0, 0, 1).text());
    }

    @Test
    void sameLevelAgainTurnsHeadingBackIntoPlainText() {
        assertEquals("題", MarkdownEditing.setHeading("## 題", 0, 0, 2).text());
    }

    @Test
    void levelZeroRemovesHeading() {
        assertEquals("題", MarkdownEditing.setHeading("### 題", 0, 0, 0).text());
        assertEquals("普通", MarkdownEditing.setHeading("普通", 0, 0, 0).text());
    }

    @Test
    void headingAppliesToEverySelectedNonBlankLine() {
        MarkdownEditing.Result r = MarkdownEditing.setHeading("a\n\nb", 0, 4, 2);
        assertEquals("## a\n\n## b", r.text());
    }

    @Test
    void headingLeavesCodeBlocksTablesAndRulesAlone() {
        String text = "```\n#x\n```\n|a|b|\n---";
        assertEquals(text, MarkdownEditing.setHeading(text, 0, text.length(), 1).text());
    }

    // ---- 挿入

    @Test
    void insertsHorizontalRuleOnItsOwnLine() {
        MarkdownEditing.Result r = MarkdownEditing.insertHorizontalRule("前\n後", 1, 1);
        assertEquals("前\n---\n\n後", r.text());
        assertTrue(new MarkdownRenderer().render(r.text()).contains("<hr/>"));
    }

    @Test
    void wrapsSelectionInCodeBlock() {
        MarkdownEditing.Result r = MarkdownEditing.wrapCodeBlock("int a;", 0, 6);
        assertEquals("```\nint a;\n```", r.text());
        assertEquals("int a;", selected(r));
    }

    @Test
    void emptySelectionInsertsEmptyCodeBlockWithCaretInside() {
        MarkdownEditing.Result r = MarkdownEditing.wrapCodeBlock("", 0, 0);
        assertEquals("```\n\n```", r.text());
        assertEquals(4, r.selectionStart());
    }

    @Test
    void insertsTableWithRequestedRowsAndColumns() {
        MarkdownEditing.Result r = MarkdownEditing.insertTable("", 0, 0, 3, 2);
        assertEquals("|   |   |\n|---|---|\n|   |   |\n|   |   |\n", r.text());
        String html = new MarkdownRenderer().render(r.text());
        assertEquals(3, html.split("<tr>", -1).length - 1);
        assertEquals(2, html.split("<th>", -1).length - 1);
    }

    @Test
    void tableCaretIsPlacedInFirstCell() {
        MarkdownEditing.Result r = MarkdownEditing.insertTable("", 0, 0, 2, 2);
        assertEquals(2, r.selectionStart());
    }

    @Test
    void tableInsertedMidLineStartsOnNewLine() {
        MarkdownEditing.Result r = MarkdownEditing.insertTable("文章", 2, 2, 1, 1);
        assertTrue(r.text().startsWith("文章\n|   |\n|---|\n"));
    }
}
