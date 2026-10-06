package com.mydictionary.desktop.editor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RichNoteEditorTest {

    @Test
    void quoteEscapesCharactersThatWouldBreakAScriptStringLiteral() {
        assertEquals("\"a\\\"b\\\\c\\nd\\r\\u0001\\u2028\"", RichNoteEditor.quote("a\"b\\c\nd\r\u0001\u2028"));
    }

    @Test
    void quoteKeepsJapaneseAndHtmlAsIs() {
        assertEquals("\"<p>日本語</p>\"", RichNoteEditor.quote("<p>日本語</p>"));
    }
}
