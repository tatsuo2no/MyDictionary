package com.mydictionary.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Note {
    private long id;
    private final String uuid;
    private final long bookId;
    private String body;
    private List<Long> tagIds = new ArrayList<>();
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * カラムID(NoteColumn#getId())→そのカラムの値。以前は項目名・読み・英訳という3つの固定フィールド
     * だったが、ブックごとに自由な数・名前のカラムを持てるように変更した（2026-09）。
     * 値が無いカラムはこのMapにキー自体が存在しない（空文字と未入力を区別するため）。
     * tagIdsと同じくコンストラクタ引数には含めず、デフォルト値付きのフィールド+setterで扱う。
     */
    private Map<Long, String> fieldValues = new LinkedHashMap<>();

    // ノート画面の見た目設定（2026-09追加）。既存の呼び出し元を壊さないよう、tagIdsと同じく
    // コンストラクタ引数には含めずデフォルト値付きのフィールド+setterで扱う。
    private BookTheme backgroundTheme = BookTheme.WHITE;
    private String backgroundImageFileName;
    private NoteTextColor textColor = NoteTextColor.BLACK;

    /**
     * uuidはデバイス間同期でノートの同一性を判定するための識別子。
     * 新規作成してinsert()に渡す場合はnullでよい（リポジトリが自動採番する）。
     */
    public Note(long id, String uuid, long bookId, String body, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.uuid = uuid;
        this.bookId = bookId;
        this.body = body;
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

    public long getBookId() {
        return bookId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }

    /** カラムID→値。値が設定されていないカラムはキーごと存在しない。 */
    public Map<Long, String> getFieldValues() {
        return fieldValues;
    }

    public void setFieldValues(Map<Long, String> fieldValues) {
        this.fieldValues = fieldValues;
    }

    /** 指定したカラムの値を返す（未設定なら空文字）。 */
    public String getFieldValue(long columnId) {
        return fieldValues.getOrDefault(columnId, "");
    }

    public void setFieldValue(long columnId, String value) {
        fieldValues.put(columnId, value);
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

    public BookTheme getBackgroundTheme() {
        return backgroundTheme;
    }

    public void setBackgroundTheme(BookTheme backgroundTheme) {
        this.backgroundTheme = backgroundTheme;
    }

    public String getBackgroundImageFileName() {
        return backgroundImageFileName;
    }

    public void setBackgroundImageFileName(String backgroundImageFileName) {
        this.backgroundImageFileName = backgroundImageFileName;
    }

    public NoteTextColor getTextColor() {
        return textColor;
    }

    public void setTextColor(NoteTextColor textColor) {
        this.textColor = textColor;
    }
}
