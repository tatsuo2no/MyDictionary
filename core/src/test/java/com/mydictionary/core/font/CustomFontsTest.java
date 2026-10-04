package com.mydictionary.core.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class CustomFontsTest {

    @Test
    void fileNameRoundTrips() {
        String name = CustomFonts.fileName("BIZ UDPGothic", 700, false);
        assertEquals("BIZ UDPGothic.700n.ttf", name);
        CustomFonts.Face face = CustomFonts.parse(name).orElseThrow();
        assertEquals("BIZ UDPGothic", face.family());
        assertEquals(700, face.weight());
        assertFalse(face.italic());
    }

    @Test
    void familyNameWithDotsAndJapaneseIsPreserved() {
        String name = CustomFonts.fileName("Noto Sans CJK JP v2.0 游ゴシック", 400, true);
        CustomFonts.Face face = CustomFonts.parse(name).orElseThrow();
        assertEquals("Noto Sans CJK JP v2.0 游ゴシック", face.family());
        assertTrue(face.italic());
    }

    @Test
    void sanitizeRemovesCharactersThatBreakFilesOrCss() {
        String family = CustomFonts.sanitizeFamily("A'B\"C;D/E<F>G:H(I)");
        assertEquals("A_B_C_D_E_F_G_H_I_", family);
        assertEquals("Font", CustomFonts.sanitizeFamily("   "));
    }

    @Test
    void parseRejectsFilesThatAreNotOurFonts() {
        assertTrue(CustomFonts.parse("readme.txt").isEmpty());
        assertTrue(CustomFonts.parse("plain.ttf").isEmpty());
        assertTrue(CustomFonts.parse("Foo.abc.ttf").isEmpty());
        assertTrue(CustomFonts.parse("Foo.0n.ttf").isEmpty());
        assertTrue(CustomFonts.parse("Foo.400x.ttf").isEmpty());
        assertTrue(CustomFonts.parse(".400n.ttf").isEmpty());
        assertTrue(CustomFonts.parse("A'B.400n.ttf").isEmpty());
    }

    @Test
    void parseAllSortsByFamilyThenWeightAndFamiliesAreUnique() {
        List<CustomFonts.Face> faces = CustomFonts.parseAll(List.of(
            "b.700n.ttf", "A.400n.ttf", "b.400n.ttf", "ignored.txt", "A.400i.ttf"));
        assertEquals(List.of("A.400n.ttf", "A.400i.ttf", "b.400n.ttf", "b.700n.ttf"),
            faces.stream().map(CustomFonts.Face::fileName).toList());
        assertEquals(List.of("A", "b"), CustomFonts.families(faces));
    }

    @Test
    void fontFaceCssRegistersWeightAndStyleAndEscapesQuotesInUrl() {
        List<CustomFonts.Face> faces = CustomFonts.parseAll(List.of("F.700i.ttf"));
        String css = CustomFonts.fontFaceCss(faces, f -> "file:///x/it's/" + f);
        assertEquals("@font-face{font-family:'F';font-weight:700;font-style:italic;"
            + "src:url('file:///x/it%27s/F.700i.ttf') format('truetype');}", css);
    }
}
