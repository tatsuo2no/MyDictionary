package com.mydictionary.core.model;

/** ブックのテーマカラー。ブック画面・ノート画面・ノート作成画面・タグ画面の配色に使用する。 */
public enum BookTheme {
    WHITE("白", "#FFFFFF", false),
    SKIN("肌色", "#FDE0C6", false),
    YELLOW("黄色", "#FFF6A5", false),
    ORANGE("橙色", "#FFC97A", false),
    RED("赤色", "#F28B82", false),
    PINK("桃色", "#FBC8D4", false),
    PURPLE("紫色", "#D7BCE8", false),
    BROWN("茶色", "#C9A27A", false),
    GREEN("緑色", "#A8D5A2", false),
    YELLOW_GREEN("黄緑色", "#D4E8A0", false),
    LIGHT_BLUE("水色", "#A7E0F2", false),
    BLUE("青", "#7FB3F5", false),
    NAVY("紺色", "#3B4B78", true),
    BLACK("黒", "#2B2B2B", true),
    CRIMSON("紅色", "#9E1B32", true);

    private final String displayName;
    private final String colorCode;
    private final boolean dark;

    BookTheme(String displayName, String colorCode, boolean dark) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.dark = dark;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    /** テーマ背景が濃色で、白文字にしないと読みにくいかどうか。 */
    public boolean isDark() {
        return dark;
    }

    /** テーマ背景色の上に文字を置くときに読みやすい文字色。 */
    public String getTextColorCode() {
        return dark ? "#FFFFFF" : "#000000";
    }

    /**
     * ブック画面のノート一覧の行に敷く背景色（1行ごとに明・暗で交互に切り替える用の2色）。
     * どちらもテーマ色を白側へ寄せて明るくした色で、暗い方もテーマ色より明るい。
     * 白テーマの場合は明るくする余地がなく通常の白背景と区別できないため、nullを返す（＝行に色を敷かない）。
     * 一覧の文字は黒で表示されるため、濃色テーマ（紺・黒）も同様に明るくして黒文字が読める色にする。
     */
    public String getListRowColorLightCode() {
        if (this == WHITE) {
            return null;
        }
        return lighten(colorCode, 0.63);
    }

    public String getListRowColorDarkCode() {
        if (this == WHITE) {
            return null;
        }
        return lighten(colorCode, 0.47);
    }

    private static String lighten(String hex, double amount) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        r = (int) Math.round(r + (255 - r) * amount);
        g = (int) Math.round(g + (255 - g) * amount);
        b = (int) Math.round(b + (255 - b) * amount);
        return String.format("#%02X%02X%02X", r, g, b);
    }
}
