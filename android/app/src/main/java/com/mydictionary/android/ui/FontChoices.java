package com.mydictionary.android.ui;

import android.content.Context;

import com.mydictionary.android.CustomFontFiles;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookFont;

import java.util.ArrayList;
import java.util.List;

/**
 * ブックのフォントを選ぶスピナーの選択肢。組み込みのフォント({@link BookFont})に続けて、
 * デスクトップ版で追加されこの端末に同期されたフォントを並べる。
 */
final class FontChoices {
    private final List<String> labels = new ArrayList<>();
    private final List<String> customFamilies = new ArrayList<>();

    /**
     * currentCustomFamilyは、今のブックに設定されている追加フォント（無ければnull）。
     * まだこの端末にファイルが届いていなくても選択肢に加え、保存で設定が消えないようにする。
     */
    FontChoices(Context context, String currentCustomFamily) {
        for (BookFont font : BookFont.values()) {
            labels.add(font.getFamilyName());
        }
        customFamilies.addAll(CustomFontFiles.families(context));
        if (currentCustomFamily != null && !customFamilies.contains(currentCustomFamily)) {
            customFamilies.add(currentCustomFamily);
        }
        labels.addAll(customFamilies);
    }

    List<String> labels() {
        return labels;
    }

    /** 今のブックの設定に対応する選択肢の位置。 */
    int indexOf(Book book) {
        if (book.getCustomFontFamily() != null) {
            return BookFont.values().length + customFamilies.indexOf(book.getCustomFontFamily());
        }
        return book.getFont().ordinal();
    }

    /** 選んだ位置の設定をブックに反映する。組み込みを選んだら追加フォントの指定は解除する。 */
    void applyTo(int position, Book book) {
        int builtinCount = BookFont.values().length;
        if (position < builtinCount) {
            book.setFont(BookFont.values()[position]);
            book.setCustomFontFamily(null);
        } else {
            book.setCustomFontFamily(customFamilies.get(position - builtinCount));
        }
    }

    /** 書式ツールバーの「フォント」で選べるファミリー名（組み込み＋追加フォント）。 */
    static List<String> toolbarFamilies(Context context) {
        List<String> families = new ArrayList<>();
        for (BookFont font : BookFont.values()) {
            families.add(font.getFamilyName());
        }
        families.addAll(CustomFontFiles.families(context));
        return families;
    }
}
