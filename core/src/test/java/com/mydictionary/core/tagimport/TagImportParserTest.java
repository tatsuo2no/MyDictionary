package com.mydictionary.core.tagimport;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TagImportParserTest {

    @Test
    void parsesNestedMarkdownBulletList() throws TagImportParser.ParseException {
        String markdown = """
            - 動物
              - 哺乳類
                - 犬
                - 猫
              - 鳥類
            - 植物
            """;
        List<TagImportParser.ImportedTag> roots = TagImportParser.parseMarkdown(markdown);

        assertEquals(2, roots.size());
        TagImportParser.ImportedTag animal = roots.get(0);
        assertEquals("動物", animal.getName());
        assertEquals(2, animal.getChildren().size());
        TagImportParser.ImportedTag mammal = animal.getChildren().get(0);
        assertEquals("哺乳類", mammal.getName());
        assertEquals(List.of("犬", "猫"), mammal.getChildren().stream().map(TagImportParser.ImportedTag::getName).toList());
        assertEquals("鳥類", animal.getChildren().get(1).getName());
        assertEquals("植物", roots.get(1).getName());
        assertTrue(roots.get(1).getChildren().isEmpty());
    }

    @Test
    void allowsAsteriskAndFullWidthBulletMarkers() throws TagImportParser.ParseException {
        String markdown = """
            * 食材
            ・調味料
            """;
        List<TagImportParser.ImportedTag> roots = TagImportParser.parseMarkdown(markdown);
        assertEquals(List.of("食材", "調味料"), roots.stream().map(TagImportParser.ImportedTag::getName).toList());
    }

    @Test
    void rejectsMarkdownLineWithoutBulletMarker() {
        var exception = assertThrows(TagImportParser.ParseException.class,
            () -> TagImportParser.parseMarkdown("動物\n- 植物"));
        assertTrue(exception.getMessage().contains("1行目"));
    }

    @Test
    void rejectsMarkdownWithDisallowedCharacters() {
        var exception = assertThrows(TagImportParser.ParseException.class,
            () -> TagImportParser.parseMarkdown("- タグ@"));
        assertTrue(exception.getMessage().contains("タグ@"));
    }

    @Test
    void rejectsEmptyMarkdown() {
        assertThrows(TagImportParser.ParseException.class, () -> TagImportParser.parseMarkdown("   \n\n"));
    }

    @Test
    void parsesNestedJsonArray() throws TagImportParser.ParseException {
        String json = """
            [
              { "name": "動物", "children": [
                { "name": "哺乳類", "children": [ { "name": "犬" }, { "name": "猫" } ] },
                { "name": "鳥類" }
              ] },
              { "name": "植物" }
            ]
            """;
        List<TagImportParser.ImportedTag> roots = TagImportParser.parseJson(json);

        assertEquals(2, roots.size());
        TagImportParser.ImportedTag animal = roots.get(0);
        assertEquals("動物", animal.getName());
        TagImportParser.ImportedTag mammal = animal.getChildren().get(0);
        assertEquals(List.of("犬", "猫"), mammal.getChildren().stream().map(TagImportParser.ImportedTag::getName).toList());
        assertEquals("植物", roots.get(1).getName());
    }

    @Test
    void rejectsJsonWithoutTopLevelArray() {
        assertThrows(TagImportParser.ParseException.class, () -> TagImportParser.parseJson("{\"name\":\"動物\"}"));
    }

    @Test
    void rejectsJsonTagWithoutName() {
        assertThrows(TagImportParser.ParseException.class, () -> TagImportParser.parseJson("[{\"children\":[]}]"));
    }

    @Test
    void rejectsJsonWithDisallowedCharacters() {
        var exception = assertThrows(TagImportParser.ParseException.class,
            () -> TagImportParser.parseJson("[{\"name\":\"タグ@\"}]"));
        assertTrue(exception.getMessage().contains("タグ@"));
    }

    @Test
    void decodesJsonStringEscapes() throws TagImportParser.ParseException {
        List<TagImportParser.ImportedTag> roots = TagImportParser.parseJson("[{\"name\":\"改行\\u30bf\\u30b0\"}]");
        assertEquals("改行タグ", roots.get(0).getName());
    }
}
