package com.mydictionary.core.model;

/**
 * ブックのフォント。ブック画面・ノート画面・ノート作成画面・タグ画面の文字表示に使用する。
 * 太字（**で囲む／見出し）に対応したフォントのみを列挙する。MS ゴシック/MS Pゴシック/MS 明朝/
 * MS P明朝/Yu Minchoはデスクトップ版のWebViewで太字が全く反映されない（実測でnormalと
 * boldのレンダリング結果がピクセル単位で完全一致）ため除外している。
 */
public enum BookFont {
    MEIRYO("Meiryo"),
    MEIRYO_UI("Meiryo UI"),
    YU_GOTHIC("Yu Gothic");

    private final String familyName;

    BookFont(String familyName) {
        this.familyName = familyName;
    }

    public String getFamilyName() {
        return familyName;
    }
}
