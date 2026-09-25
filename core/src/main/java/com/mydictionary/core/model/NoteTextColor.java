package com.mydictionary.core.model;

/**
 * ノート本文の標準の文字色。BookThemeとは別に、文字色として読みやすい色のみを集めた
 * プリセット（BookThemeの背景色をそのまま文字色に使うと視認性が悪いものがあるため）。
 * 切替UIはBookTheme選択と同じ「色名+カラーサンプル」のComboBox/Spinner形式にする。
 * 濃色系（明るい背景向け）と明色系（紺色・黒などの濃色背景向け）の両方を用意し、
 * 背景色の明暗どちらでも視認性を確保できるようにしている（2026-09、明色系を追加）。
 */
public enum NoteTextColor {
    BLACK("黒", "#000000"),
    DARK_GRAY("濃灰色", "#4A4A4A"),
    NAVY("紺色", "#1F3A5F"),
    MAROON("えんじ色", "#7B241C"),
    DARK_GREEN("深緑", "#1B4D3E"),
    DARK_BROWN("こげ茶色", "#4E342E"),
    PURPLE("紫", "#4A235A"),
    DARK_ORANGE("焦茶色", "#B75B00"),
    WHITE("白", "#FFFFFF"),
    YELLOW("黄色", "#FFEB3B"),
    LIGHT_GRAY("明灰色", "#E0E0E0"),
    CREAM("クリーム色", "#FFF8DC"),
    LIGHT_PINK("桃色", "#FFC1E3"),
    LIGHT_BLUE("水色", "#87CEEB");

    private final String displayName;
    private final String colorCode;

    NoteTextColor(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }
}
