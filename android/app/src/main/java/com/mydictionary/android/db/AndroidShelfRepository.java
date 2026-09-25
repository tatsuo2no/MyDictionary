package com.mydictionary.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.repository.ShelfRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AndroidShelfRepository implements ShelfRepository {
    private final DictionaryDbHelper dbHelper;

    public AndroidShelfRepository(DictionaryDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public List<Shelf> findAll() {
        List<Shelf> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM shelves ORDER BY sort_order, id", null)) {
            while (cursor.moveToNext()) {
                result.add(mapRow(cursor));
            }
        }
        return result;
    }

    @Override
    public Optional<Shelf> findById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM shelves WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Shelf> findByUuid(String uuid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM shelves WHERE uuid = ?", new String[]{uuid})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public Shelf insert(Shelf shelf) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String now = Instant.now().toString();
        ContentValues values = toContentValues(shelf);
        values.put("uuid", UUID.randomUUID().toString());
        values.put("sort_order", nextSortOrder(db));
        values.put("created_at", now);
        values.put("updated_at", now);
        long id = db.insertOrThrow("shelves", null, values);
        return findById(id).orElseThrow(() -> new IllegalStateException("シェルフの作成に失敗しました"));
    }

    @Override
    public void update(Shelf shelf) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toContentValues(shelf);
        values.put("updated_at", Instant.now().toString());
        db.update("shelves", values, "id = ?", new String[]{String.valueOf(shelf.getId())});
    }

    @Override
    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("shelves", "id = ?", new String[]{String.valueOf(id)});
    }

    @Override
    public void updateManualOrder(List<Long> orderedShelfIds) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < orderedShelfIds.size(); i++) {
                ContentValues values = new ContentValues();
                values.put("sort_order", i);
                db.update("shelves", values, "id = ?", new String[]{String.valueOf(orderedShelfIds.get(i))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 新規シェルフを末尾に付けるための sort_order 値（既存の最大値+1）。 */
    private int nextSortOrder(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM shelves", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @Override
    public Shelf upsertFromSync(String uuid, String name, BookCover cover, Instant createdAt, Instant updatedAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Optional<Shelf> existing = findByUuid(uuid);

        ContentValues values = new ContentValues();
        values.put("name", name);
        if (cover.isCustom()) {
            values.putNull("cover_pattern");
            values.put("cover_custom_path", cover.getCustomImagePath());
        } else {
            values.put("cover_pattern", cover.getPattern().name());
            values.putNull("cover_custom_path");
        }
        values.put("updated_at", updatedAt.toString());

        if (existing.isPresent()) {
            db.update("shelves", values, "uuid = ?", new String[]{uuid});
        } else {
            values.put("uuid", uuid);
            values.put("sort_order", nextSortOrder(db));
            values.put("created_at", createdAt.toString());
            db.insertOrThrow("shelves", null, values);
        }
        return findByUuid(uuid).orElseThrow();
    }

    private ContentValues toContentValues(Shelf shelf) {
        ContentValues values = new ContentValues();
        values.put("name", shelf.getName());
        if (shelf.getCover().isCustom()) {
            values.putNull("cover_pattern");
            values.put("cover_custom_path", shelf.getCover().getCustomImagePath());
        } else {
            values.put("cover_pattern", shelf.getCover().getPattern().name());
            values.putNull("cover_custom_path");
        }
        return values;
    }

    private Shelf mapRow(Cursor cursor) {
        String coverPatternName = getStringOrNull(cursor, "cover_pattern");
        String coverCustomPath = getStringOrNull(cursor, "cover_custom_path");
        BookCover cover = coverCustomPath != null
            ? BookCover.ofCustom(coverCustomPath)
            : BookCover.ofPattern(resolvePattern(coverPatternName));

        Shelf shelf = new Shelf(
            cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            cursor.getString(cursor.getColumnIndexOrThrow("name")),
            cover,
            Instant.parse(cursor.getString(cursor.getColumnIndexOrThrow("created_at"))),
            Instant.parse(cursor.getString(cursor.getColumnIndexOrThrow("updated_at")))
        );
        shelf.setSortOrder(cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")));
        return shelf;
    }

    private static BookCover.Pattern resolvePattern(String name) {
        try {
            return BookCover.Pattern.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return BookCover.Pattern.STRIPES;
        }
    }

    private String getStringOrNull(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }
}
