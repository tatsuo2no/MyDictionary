package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookFont;

/**
 * ブックのフォントとして選べるもの。組み込みのフォント({@link BookFont})か、
 * デスクトップ版で追加したフォント（ファミリー名）のどちらか一方。
 */
record FontChoice(BookFont builtin, String custom) {

    static FontChoice builtin(BookFont font) {
        return new FontChoice(font, null);
    }

    static FontChoice custom(String family) {
        return new FontChoice(null, family);
    }

    /** ブックに今設定されているフォントに対応する選択肢。 */
    static FontChoice of(Book book) {
        return book.getCustomFontFamily() != null ? custom(book.getCustomFontFamily()) : builtin(book.getFont());
    }

    String label() {
        return builtin != null ? builtin.getFamilyName() : custom;
    }

    /** この選択をブックに反映する。組み込みを選んだら追加フォントの指定は解除する。 */
    void applyTo(Book book) {
        if (builtin != null) {
            book.setFont(builtin);
            book.setCustomFontFamily(null);
        } else {
            book.setCustomFontFamily(custom);
        }
    }
}
