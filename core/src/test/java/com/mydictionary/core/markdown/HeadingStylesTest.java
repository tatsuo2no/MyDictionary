package com.mydictionary.core.markdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class HeadingStylesTest {

    @Test
    void cssKeepsTheSizesThatNotesHaveAlwaysBeenShownWith() {
        assertEquals("h1{font-size:1.8em;} h2{font-size:1.5em;} h3{font-size:1.3em;} "
            + "h4{font-size:1.15em;} h5{font-size:1.05em;} h6{font-size:1em;}", HeadingStyles.css());
    }

    @Test
    void sizeIsTheBookFontSizeTimesTheHeadingRatio() {
        assertEquals(21.6, HeadingStyles.sizePt(1, 12));
        assertEquals(12.6, HeadingStyles.sizePt(5, 12));
        assertEquals(13.8, HeadingStyles.sizePt(4, 12));
        assertEquals(36.0, HeadingStyles.sizePt(2, 24));
    }

    @Test
    void formatsPointsWithoutUselessDecimals() {
        assertEquals("12", HeadingStyles.formatPt(12.0));
        assertEquals("12.6", HeadingStyles.formatPt(12.6));
        assertEquals("36", HeadingStyles.formatPt(HeadingStyles.sizePt(2, 24)));
    }

    @Test
    void rejectsLevelsOutsideOneToSix() {
        assertThrows(IllegalArgumentException.class, () -> HeadingStyles.em(0));
        assertThrows(IllegalArgumentException.class, () -> HeadingStyles.em(7));
    }
}
