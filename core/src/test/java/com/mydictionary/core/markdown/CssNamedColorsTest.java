package com.mydictionary.core.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CssNamedColorsTest {

    @Test
    void has140UniqueColorsFillingTheSevenByTwentyGrid() {
        List<CssNamedColors.NamedColor> colors = CssNamedColors.all();
        assertEquals(140, colors.size());
        assertEquals(CssNamedColors.ROWS * CssNamedColors.COLUMNS, colors.size());
        Set<String> names = new HashSet<>();
        for (CssNamedColors.NamedColor c : colors) {
            assertTrue(names.add(c.name()), "重複: " + c.name());
            assertTrue(c.hex().matches("#[0-9A-F]{6}"), c.name());
        }
    }

    @Test
    void doesNotContainGreyAliasSpellings() {
        for (CssNamedColors.NamedColor c : CssNamedColors.all()) {
            assertFalse(c.name().contains("grey"), c.name());
        }
    }

    @Test
    void containsCommonBasicColors() {
        Set<String> names = new HashSet<>();
        CssNamedColors.all().forEach(c -> names.add(c.name()));
        for (String basic : List.of("red", "green", "blue", "yellow", "black", "white", "orange", "purple")) {
            assertTrue(names.contains(basic), basic);
        }
    }

    @Test
    void startsWithRedHueAndEndsWithAchromaticColors() {
        List<CssNamedColors.NamedColor> colors = CssNamedColors.all();
        CssNamedColors.NamedColor first = colors.get(0);
        assertTrue(first.red() > first.green() && first.red() > first.blue(), first.name());
        CssNamedColors.NamedColor last = colors.get(colors.size() - 1);
        assertEquals(last.red(), last.green());
        assertEquals(last.green(), last.blue());
    }
}
