package com.mydictionary.android;

import com.mydictionary.core.model.BookCover;

/**
 * ブックカバーの模様プリセットに対応するdrawableリソースIDを返す。実際の画像は白の半透明マスクPNG
 * （res/drawable/pattern_*.png）で、テーマの文字色でティントして使う（デスクトップ版と同じ考え方）。
 * PLAIN（無地）は模様画像を持たないため、{@link #drawableRes}は呼ばず{@link #hasImage}で判定すること。
 * ブック一覧（{@link com.mydictionary.android.ui.BookAdapter}）と模様選択ダイアログ
 * （{@link com.mydictionary.android.ui.CoverPatternPickerButton}）の両方で共通して使う。
 */
public final class BookCoverPatterns {

    private BookCoverPatterns() {
    }

    public static boolean hasImage(BookCover.Pattern pattern) {
        return pattern != BookCover.Pattern.PLAIN;
    }

    public static int drawableRes(BookCover.Pattern pattern) {
        switch (pattern) {
            case STRIPES:
                return R.drawable.pattern_stripes;
            case POLKA_DOTS:
                return R.drawable.pattern_polka_dots;
            case CHECKERS:
                return R.drawable.pattern_checkers;
            case DIAMONDS:
                return R.drawable.pattern_diamonds;
            case WAVES:
                return R.drawable.pattern_waves;
            case HERRINGBONE:
                return R.drawable.pattern_herringbone;
            case CROSSHATCH:
                return R.drawable.pattern_crosshatch;
            case FLORAL:
                return R.drawable.pattern_floral;
            case HONEYCOMB:
                return R.drawable.pattern_honeycomb;
            case PLAIN:
            default:
                throw new IllegalArgumentException("PLAINには画像がありません。hasImage()で先に判定してください: " + pattern);
        }
    }
}
