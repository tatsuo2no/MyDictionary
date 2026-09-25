package com.mydictionary.core.model;

import java.time.Instant;

public class Book {
    private long id;
    private final String uuid;
    private long shelfId;
    private String title;
    private BookTheme theme;
    private BookFont font;
    private BookCover cover;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * ブックリスト画面での手動並び替え順。小さいほど先頭。端末ローカルの表示順であり、
     * デバイス間同期の対象外（同期で入ってきたブックは末尾に付く）。
     * 既存の new Book(...) 呼び出しを壊さないよう、コンストラクタ引数ではなくフィールド+setterで持つ。
     */
    private int sortOrder;

    /** ノート本文の標準フォントサイズ（pt）。既定値はWebViewの標準サイズ（16px相当）に合わせた12。 */
    public static final int DEFAULT_FONT_SIZE_PT = 12;
    public static final int[] AVAILABLE_FONT_SIZES_PT = {10, 11, 12, 14, 16, 18, 20, 24, 28, 32};

    private int fontSizePt = DEFAULT_FONT_SIZE_PT;

    /**
     * uuidはデバイス間同期でブックの同一性を判定するための識別子。
     * 新規作成してinsert()に渡す場合はnullでよい（リポジトリが自動採番する）。
     * shelfIdはブックが必ず1つ属するシェルフのID（他のシェルフへ移動する場合はsetShelfId()で変更する）。
     */
    public Book(long id, String uuid, long shelfId, String title, BookTheme theme, BookFont font, BookCover cover,
                Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.uuid = uuid;
        this.shelfId = shelfId;
        this.title = title;
        this.theme = theme;
        this.font = font;
        this.cover = cover;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public long getShelfId() {
        return shelfId;
    }

    public void setShelfId(long shelfId) {
        this.shelfId = shelfId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BookTheme getTheme() {
        return theme;
    }

    public void setTheme(BookTheme theme) {
        this.theme = theme;
    }

    public BookFont getFont() {
        return font;
    }

    public void setFont(BookFont font) {
        this.font = font;
    }

    public BookCover getCover() {
        return cover;
    }

    public void setCover(BookCover cover) {
        this.cover = cover;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int getFontSizePt() {
        return fontSizePt;
    }

    public void setFontSizePt(int fontSizePt) {
        this.fontSizePt = fontSizePt;
    }
}
