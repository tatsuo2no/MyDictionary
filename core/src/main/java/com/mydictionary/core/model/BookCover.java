package com.mydictionary.core.model;

/**
 * ブックカバーの見た目。プリセットの模様か、ユーザー指定の画像パスのいずれかを保持する。
 * 以前は記号1文字の「アイコン」だったが、使える記号の種類に限りがあるため、テーマ色でティントした
 * 模様（縞・水玉など）を表示する方式に変更した（2026-09、BookIconから改称）。
 */
public final class BookCover {

    /**
     * ブックカバーの表示・保存サイズ（幅×高さ、ピクセル）。デスクトップ版・Android版で共通の値として
     * 参照する。「参照」ボタンでカスタム画像を選んだ場合、このサイズにリサイズしてPNG保存する。
     */
    public static final int WIDTH_PX = 122;
    public static final int HEIGHT_PX = 160;

    /** カバーに敷く模様のプリセット。実際の画像は各プラットフォームのcoverpatterns素材を参照する。 */
    public enum Pattern {
        PLAIN, STRIPES, POLKA_DOTS, CHECKERS, DIAMONDS, WAVES, HERRINGBONE, CROSSHATCH, FLORAL, HONEYCOMB
    }

    private final Pattern pattern;
    private final String customImagePath;

    private BookCover(Pattern pattern, String customImagePath) {
        this.pattern = pattern;
        this.customImagePath = customImagePath;
    }

    public static BookCover ofPattern(Pattern pattern) {
        return new BookCover(pattern, null);
    }

    public static BookCover ofCustom(String imagePath) {
        return new BookCover(null, imagePath);
    }

    public boolean isCustom() {
        return customImagePath != null;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getCustomImagePath() {
        return customImagePath;
    }
}
