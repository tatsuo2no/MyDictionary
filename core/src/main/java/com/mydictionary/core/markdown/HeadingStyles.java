package com.mydictionary.core.markdown;

/**
 * 見出し(h1〜h6)の文字サイズ。ノートを表示するHTML(CSS)と、書式ツールバーの見出しウィンドウで
 * 「実際にこのサイズで表示される」というサンプルを出すために、同じ値を共有する。
 * サイズは本文の標準サイズ(ブックのフォントサイズ)に対する倍率(em)。
 */
public final class HeadingStyles {

    /** 見出しレベル1〜6の倍率（CSSのemの表記そのまま）。 */
    private static final String[] EM = {"1.8", "1.5", "1.3", "1.15", "1.05", "1"};

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = EM.length;

    private HeadingStyles() {
    }

    public static double em(int level) {
        return Double.parseDouble(EM[checked(level) - 1]);
    }

    /** 本文の標準サイズ(pt)がbasePtのとき、この見出しレベルが表示されるサイズ(pt)。 */
    public static double sizePt(int level, int basePt) {
        return Math.round(basePt * em(level) * 100.0) / 100.0;
    }

    /** サイズ(pt)を「12」「12.6」のように、不要な小数を付けずに表示用の文字列にする。 */
    public static String formatPt(double pt) {
        return pt == Math.rint(pt) ? String.valueOf((long) pt) : String.valueOf(pt);
    }

    /** ノートのHTMLに埋め込む、見出しのフォントサイズ規則。 */
    public static String css() {
        StringBuilder css = new StringBuilder();
        for (int level = MIN_LEVEL; level <= MAX_LEVEL; level++) {
            css.append('h').append(level).append("{font-size:").append(EM[level - 1]).append("em;}");
            if (level != MAX_LEVEL) {
                css.append(' ');
            }
        }
        return css.toString();
    }

    private static int checked(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("見出しレベルは1〜" + MAX_LEVEL + "です: " + level);
        }
        return level;
    }
}
