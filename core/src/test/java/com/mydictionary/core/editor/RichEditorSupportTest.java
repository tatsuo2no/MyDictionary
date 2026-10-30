package com.mydictionary.core.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RichEditorSupportTest {

    @Test
    void quoteEscapesCharactersThatWouldBreakAScriptStringLiteral() {
        assertEquals("\"a\\\"b\\\\c\\nd\\r\\u0001\\u2028\"", RichEditorSupport.quote("a\"b\\c\nd\r\u0001\u2028"));
    }

    @Test
    void quoteKeepsJapaneseAndHtmlAsIs() {
        assertEquals("\"<p>日本語</p>\"", RichEditorSupport.quote("<p>日本語</p>"));
    }

    @Test
    void pageEmbedsTheEditorScript() {
        String page = RichEditorSupport.pageHtml("body{}", "<!-- head -->");
        assertTrue(page.contains("window.MDE"));
        assertTrue(page.contains("<!-- head -->"));
    }

    @Test
    void footnoteDefinitionsAreKeptAndAppendedAfterTheBody() {
        var defs = RichEditorSupport.footnoteDefinitions("本文[^1]\n\n[^1]: 注釈");
        assertEquals(java.util.List.of("[^1]: 注釈"), defs);
        assertEquals("本文\n\n[^1]: 注釈", RichEditorSupport.toMarkdown("<p>本文</p>", defs));
    }
}
