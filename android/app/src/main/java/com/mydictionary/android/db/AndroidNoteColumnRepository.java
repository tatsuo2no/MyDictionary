package com.mydictionary.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.repository.NoteColumnRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AndroidNoteColumnRepository implements NoteColumnRepository {
    private final DictionaryDbHelper dbHelper;

    public AndroidNoteColumnRepository(DictionaryDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public List<NoteColumn> findByBookId(long bookId) {
        List<NoteColumn> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM note_columns WHERE book_id = ? ORDER BY sort_order, id",
                new String[]{String.valueOf(bookId)})) {
            while (cursor.moveToNext()) {
                result.add(mapRow(cursor));
            }
        }
        return result;
    }

    private Optional<NoteColumn> findById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM note_columns WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(cursor));
            }
        }
        return Optional.empty();
    }

    private Optional<NoteColumn> findByUuid(long bookId, String uuid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM note_columns WHERE book_id = ? AND uuid = ?",
                new String[]{String.valueOf(bookId), uuid})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public NoteColumn insert(NoteColumn column) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("uuid", UUID.randomUUID().toString());
        values.put("book_id", column.getBookId());
        values.put("name", column.getName());
        values.put("required", column.isRequired() ? 1 : 0);
        values.put("sort_order", column.getSortOrder());
        values.put("updated_at", Instant.now().toString());
        long id = db.insertOrThrow("note_columns", null, values);
        return findById(id).orElseThrow(() -> new IllegalStateException("カラムの作成に失敗しました"));
    }

    @Override
    public void update(NoteColumn column) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", column.getName());
        values.put("required", column.isRequired() ? 1 : 0);
        values.put("sort_order", column.getSortOrder());
        values.put("updated_at", Instant.now().toString());
        db.update("note_columns", values, "id = ?", new String[]{String.valueOf(column.getId())});
    }

    @Override
    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("note_columns", "id = ?", new String[]{String.valueOf(id)});
    }

    @Override
    public void seedDefaultColumns(long bookId) {
        insert(new NoteColumn(0, null, bookId, "項目名", true, 0, Instant.now()));
        insert(new NoteColumn(0, null, bookId, "読み", true, 1, Instant.now()));
        insert(new NoteColumn(0, null, bookId, "英訳", false, 2, Instant.now()));
    }

    @Override
    public NoteColumn upsertFromSync(long bookId, String uuid, String name, boolean required, int sortOrder,
                                      Instant updatedAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Optional<NoteColumn> existing = findByUuid(bookId, uuid);

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("required", required ? 1 : 0);
        values.put("sort_order", sortOrder);
        values.put("updated_at", updatedAt.toString());

        if (existing.isPresent()) {
            db.update("note_columns", values, "book_id = ? AND uuid = ?", new String[]{String.valueOf(bookId), uuid});
        } else {
            values.put("uuid", uuid);
            values.put("book_id", bookId);
            db.insertOrThrow("note_columns", null, values);
        }
        return findByUuid(bookId, uuid).orElseThrow();
    }

    private NoteColumn mapRow(Cursor cursor) {
        String updatedAtText = getStringOrNull(cursor, "updated_at");
        Instant updatedAt = updatedAtText != null ? Instant.parse(updatedAtText) : Instant.EPOCH;
        return new NoteColumn(
            cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            cursor.getLong(cursor.getColumnIndexOrThrow("book_id")),
            cursor.getString(cursor.getColumnIndexOrThrow("name")),
            cursor.getInt(cursor.getColumnIndexOrThrow("required")) != 0,
            cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")),
            updatedAt
        );
    }

    private String getStringOrNull(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }
}
