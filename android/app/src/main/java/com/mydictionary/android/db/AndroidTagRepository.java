package com.mydictionary.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mydictionary.core.model.Tag;
import com.mydictionary.core.repository.TagRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AndroidTagRepository implements TagRepository {
    private final DictionaryDbHelper dbHelper;

    public AndroidTagRepository(DictionaryDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public List<Tag> findByBookId(long bookId) {
        List<Tag> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM tags WHERE book_id = ? ORDER BY id",
                new String[]{String.valueOf(bookId)})) {
            while (cursor.moveToNext()) {
                result.add(mapRow(cursor));
            }
        }
        return result;
    }

    @Override
    public Optional<Tag> findById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM tags WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Tag> findByUuid(long bookId, String uuid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM tags WHERE book_id = ? AND uuid = ?",
                new String[]{String.valueOf(bookId), uuid})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public Tag insert(Tag tag) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uuid", UUID.randomUUID().toString());
        values.put("book_id", tag.getBookId());
        values.put("name", tag.getName());
        if (tag.getParentTagId() != null) {
            values.put("parent_tag_id", tag.getParentTagId());
        } else {
            values.putNull("parent_tag_id");
        }
        values.put("updated_at", Instant.now().toString());
        long id = db.insertOrThrow("tags", null, values);
        return findById(id).orElseThrow(() -> new IllegalStateException("タグの作成に失敗しました"));
    }

    @Override
    public void update(Tag tag) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", tag.getName());
        if (tag.getParentTagId() != null) {
            values.put("parent_tag_id", tag.getParentTagId());
        } else {
            values.putNull("parent_tag_id");
        }
        values.put("updated_at", Instant.now().toString());
        db.update("tags", values, "id = ?", new String[]{String.valueOf(tag.getId())});
    }

    @Override
    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("tags", "id = ?", new String[]{String.valueOf(id)});
    }

    @Override
    public boolean isUsedByAnyNote(long tagId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM note_tags WHERE tag_id = ?",
                new String[]{String.valueOf(tagId)})) {
            cursor.moveToFirst();
            return cursor.getInt(0) > 0;
        }
    }

    @Override
    public Tag upsertFromSync(long bookId, String uuid, String name, Long parentTagId, Instant updatedAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Optional<Tag> existing = findByUuid(bookId, uuid);

        ContentValues values = new ContentValues();
        values.put("name", name);
        if (parentTagId != null) {
            values.put("parent_tag_id", parentTagId);
        } else {
            values.putNull("parent_tag_id");
        }
        values.put("updated_at", updatedAt.toString());

        if (existing.isPresent()) {
            db.update("tags", values, "book_id = ? AND uuid = ?", new String[]{String.valueOf(bookId), uuid});
        } else {
            values.put("uuid", uuid);
            values.put("book_id", bookId);
            db.insertOrThrow("tags", null, values);
        }
        return findByUuid(bookId, uuid).orElseThrow();
    }

    private Tag mapRow(Cursor cursor) {
        int parentIndex = cursor.getColumnIndexOrThrow("parent_tag_id");
        Long parentId = cursor.isNull(parentIndex) ? null : cursor.getLong(parentIndex);
        String updatedAtText = getStringOrNull(cursor, "updated_at");
        Instant updatedAt = updatedAtText != null ? Instant.parse(updatedAtText) : Instant.EPOCH;
        return new Tag(
            cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            cursor.getLong(cursor.getColumnIndexOrThrow("book_id")),
            cursor.getString(cursor.getColumnIndexOrThrow("name")),
            parentId,
            updatedAt
        );
    }

    private String getStringOrNull(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }
}
