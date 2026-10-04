package com.mydictionary.core.model;

import java.time.Instant;

/**
 * ブックごとに自由設定できる「ノートの項目（カラム）」の定義。
 * 以前は「項目名・読み・英訳」の3つが固定だったが、ブックごとに自由な名前のカラムを
 * 好きな数だけ定義できるように変更した（2026-09）。
 *
 * ブックごとに1つ、「主キー」のカラムを設定できる（{@link NoteColumns#primary}）。主キーのカラムは
 * 常に必須で削除できず、ノート一覧の見出し表示・ノート内リンクの既定表示・名前順の並び替えの
 * 既定の基準として使われる。主キーを一度も設定していないブックでは、並び順（sortOrder）の
 * 先頭のカラムが主キーとして扱われる（以前の「先頭カラムが必須」という仕様との互換）。
 * 主キー以外のカラムは、必須にするか任意にするかをrequiredフラグで個別に切り替えられる。
 */
public class NoteColumn {
    private long id;
    private final String uuid;
    private final long bookId;
    private String name;
    private boolean required;
    private int sortOrder;
    private Instant updatedAt;

    /**
     * 主キーに設定されているか。既定値を持つ任意の設定なので、コンストラクタ引数ではなく
     * フィールド+setterで持つ（既存のnew NoteColumn(...)呼び出しを壊さないため）。
     * 設定は{@code NoteColumnRepository#setPrimary}で行い、同じブックの他カラムの印を外す。
     */
    private boolean primaryKey;

    /**
     * uuidはデバイス間同期でカラムの同一性を判定するための識別子。
     * 新規作成してinsert()に渡す場合はnullでよい（リポジトリが自動採番する）。
     */
    public NoteColumn(long id, String uuid, long bookId, String name, boolean required, int sortOrder,
                       Instant updatedAt) {
        this.id = id;
        this.uuid = uuid;
        this.bookId = bookId;
        this.name = name;
        this.required = required;
        this.sortOrder = sortOrder;
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

    public long getBookId() {
        return bookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
