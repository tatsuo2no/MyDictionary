package com.mydictionary.core.model;

import java.time.Instant;

/** ブックの上位オブジェクトである「シェルフ（本棚）」。ブックは必ずいずれか1つのシェルフに属する。 */
public class Shelf {
    /**
     * シェルフのカバー（BookCoverを流用）を描画する際の固定配色。ブックと違い、シェルフは
     * 色テーマの設定を持たない（名前とカバーのみ設定可能）ため、全シェルフ共通の中間色で統一する。
     */
    public static final String COVER_BACKGROUND_COLOR_CODE = "#E0E0E0";
    public static final String COVER_TINT_COLOR_CODE = "#4A4A4A";

    private long id;
    private final String uuid;
    private String name;
    private BookCover cover;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * シェルフ一覧画面での手動並び替え順。小さいほど先頭。端末ローカルの表示順であり、
     * デバイス間同期の対象外（Bookのsort_orderと同じ設計方針）。
     */
    private int sortOrder;

    public Shelf(long id, String uuid, String name, BookCover cover, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.uuid = uuid;
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
}
