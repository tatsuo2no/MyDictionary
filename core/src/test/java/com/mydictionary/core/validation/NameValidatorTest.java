package com.mydictionary.core.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameValidatorTest {

    @Test
    void allowsAlphanumericHiraganaKatakanaAndSymbols() {
        assertTrue(NameValidator.isValid("Book-1_あいうえおアイウエオ()[]"));
    }

    @Test
    void allowsKanjiAndFullWidthSymbols() {
        assertTrue(NameValidator.isValid("日本語辞書"));
        assertTrue(NameValidator.isValid("英単語・熟語集"));
        assertTrue(NameValidator.isValid("旅行「フランス編」"));
    }

    @Test
    void rejectsDisallowedCharacters() {
        assertFalse(NameValidator.isValid("ブック@"));
        assertFalse(NameValidator.isValid("ブック　"));
    }

    @Test
    void allowsHalfWidthSpace() {
        assertTrue(NameValidator.isValid("Word A"));
        assertTrue(NameValidator.isValid("単語 その1"));
    }

    @Test
    void rejectsEmptyOrNull() {
        assertFalse(NameValidator.isValid(""));
        assertFalse(NameValidator.isValid(null));
    }
}
