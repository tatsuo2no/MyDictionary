package com.mydictionary.core.model;

/**
 * ノートの背景画像の表示方法。
 * <ul>
 *   <li>{@link #CENTER}: 画像全体が収まる大きさにして中央に表示する。画像の周りに余白ができる場合は、
 *       そこに背景色を表示する。</li>
 *   <li>{@link #COVER}: 画面いっぱいになるよう拡大して表示する（はみ出す部分は切り取られ、背景色は見えない）。
 *       背景画像の機能を追加した当初からの表示方法で、保存された値が無い既存のノートはこちら。</li>
 * </ul>
 */
public enum BackgroundImageMode {
    CENTER("中央に表示", "contain"),
    COVER("拡大して表示", "cover");

    private final String displayName;
    private final String cssSize;

    BackgroundImageMode(String displayName, String cssSize) {
        this.displayName = displayName;
        this.cssSize = cssSize;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 背景画像を表示するCSS宣言（画像のURLは含まない）。 */
    public String backgroundCss() {
        return "background-size:" + cssSize + ";background-position:center;background-repeat:no-repeat;"
            + "background-attachment:fixed;";
    }

    /** 保存された名前から復元する。未設定や不明な値は{@link #COVER}（従来の表示）。 */
    public static BackgroundImageMode fromName(String name) {
        if (name != null) {
            for (BackgroundImageMode mode : values()) {
                if (mode.name().equals(name)) {
                    return mode;
                }
            }
        }
        return COVER;
    }
}
