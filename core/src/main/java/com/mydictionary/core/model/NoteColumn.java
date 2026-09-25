package com.mydictionary.core.model;

import java.time.Instant;

/**
 * ブックごとに自由設定できる「ノートの項目（カラム）」の定義。
 * 以前は「項目名・読み・英訳」の3つが固定だったが、ブックごとに自由な名前のカラムを
 * 好きな数だけ定義できるように変更した（2026-09）。
 *
 * 並び順（sortOrder）の先頭（0番目）のカラムは常に必須カラムとして扱われ、削除できない。
 * これは以前の「項目名」に相当し、ノート一覧の見出し表示・ノート内リンクの既定表示・
 * 名前順の並び替えの基準として使われる。先頭以外のカラムは、必須にするか任意にするかを
 * requiredフラグで個別に切り替えられる。
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
