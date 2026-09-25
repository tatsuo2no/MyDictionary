package com.mydictionary.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteTextColor;
import com.mydictionary.core.repository.NoteRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AndroidNoteRepository implements NoteRepository {
    private final DictionaryDbHelper dbHelper;

    public AndroidNoteRepository(DictionaryDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public List<Note> findByBookId(long bookId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Note> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT * FROM notes WHERE book_id = ? ORDER BY id",
                new String[]{String.valueOf(bookId)})) {
            while (cursor.moveToNext()) {
                result.add(mapRow(db, cursor));
            }
        }
        return result;
    }

    @Override
    public Optional<Note> findById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM notes WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(db, cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Note> findByUuid(long bookId, String uuid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM notes WHERE book_id = ? AND uuid = ?",
                new String[]{String.valueOf(bookId), uuid})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(db, cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Note> search(long bookId, String keyword) {
        String lowerKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        List<Note> result = new ArrayList<>();
        for (Note note : findByBookId(bookId)) {
            if (matchesKeyword(note, lowerKeyword)) {
                result.add(note);
            }
        }
        return result;
    }

    private boolean matchesKeyword(Note note, String lowerKeyword) {
        if (containsIgnoreCase(note.getBody(), lowerKeyword)) {
            return true;
        }
        for (String value : note.getFieldValues().values()) {
            if (containsIgnoreCase(value, lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String lowerKeyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    @Override
    public List<Note> findByTag(long bookId, long tagId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        List<Note> result = new ArrayList<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT n.* FROM notes n JOIN note_tags nt ON n.id = nt.note_id "
                    + "WHERE n.book_id = ? AND nt.tag_id = ? ORDER BY n.id",
                new String[]{String.valueOf(bookId), String.valueOf(tagId)})) {
            while (cursor.moveToNext()) {
                result.add(mapRow(db, cursor));
            }
        }
        return result;
    }

    @Override
    public Note insert(Note note) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String now = Instant.now().toString();
        ContentValues values = new ContentValues();
        values.put("uuid", UUID.randomUUID().toString());
        values.put("book_id", note.getBookId());
        // title/readingは以前の固定項目の名残の列（NOT NULL制約があるため空文字を入れて満たす）。
        // 実際の値はカラム自由設定の仕組み（note_field_values）に持たせる。
        values.put("title", "");
        values.put("reading", "");
        values.put("body", note.getBody());
        values.put("background_theme", note.getBackgroundTheme().name());
        values.put("background_image", note.getBackgroundImageFileName());
        values.put("text_color", note.getTextColor().name());
        values.put("created_at", now);
        values.put("updated_at", now);
        long id = db.insertOrThrow("notes", null, values);
        saveTagLinks(db, id, note.getTagIds());
        saveFieldValues(db, id, note.getFieldValues());
        return findById(id).orElseThrow(() -> new IllegalStateException("ノートの作成に失敗しました"));
    }

    @Override
    public void update(Note note) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("body", note.getBody());
        values.put("background_theme", note.getBackgroundTheme().name());
        values.put("background_image", note.getBackgroundImageFileName());
        values.put("text_color", note.getTextColor().name());
        values.put("updated_at", Instant.now().toString());
        db.update("notes", values, "id = ?", new String[]{String.valueOf(note.getId())});
        saveTagLinks(db, note.getId(), note.getTagIds());
        saveFieldValues(db, note.getId(), note.getFieldValues());
    }

    @Override
    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("notes", "id = ?", new String[]{String.valueOf(id)});
    }

    @Override
    public Note upsertFromSync(long bookId, String uuid, Map<Long, String> fieldValues, String body,
                                List<Long> tagIds, BookTheme backgroundTheme, String backgroundImageFileName,
                                NoteTextColor textColor, Instant createdAt, Instant updatedAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Optional<Note> existing = findByUuid(bookId, uuid);

        ContentValues values = new ContentValues();
        values.put("body", body);
        values.put("background_theme", backgroundTheme == null ? null : backgroundTheme.name());
        values.put("background_image", backgroundImageFileName);
        values.put("text_color", textColor == null ? null : textColor.name());
        values.put("updated_at", updatedAt.toString());

        long noteId;
        if (existing.isPresent()) {
            noteId = existing.get().getId();
            db.update("notes", values, "book_id = ? AND uuid = ?", new String[]{String.valueOf(bookId), uuid});
        } else {
            values.put("uuid", uuid);
            values.put("book_id", bookId);
            values.put("title", "");
            values.put("reading", "");
            values.put("created_at", createdAt.toString());
            noteId = db.insertOrThrow("notes", null, values);
        }
        saveTagLinks(db, noteId, tagIds);
        saveFieldValues(db, noteId, fieldValues);
        return findByUuid(bookId, uuid).orElseThrow();
    }

    private void saveTagLinks(SQLiteDatabase db, long noteId, List<Long> tagIds) {
        db.delete("note_tags", "note_id = ?", new String[]{String.valueOf(noteId)});
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            ContentValues values = new ContentValues();
            values.put("note_id", noteId);
            values.put("tag_id", tagId);
            db.insertOrThrow("note_tags", null, values);
        }
    }

    private void saveFieldValues(SQLiteDatabase db, long noteId, Map<Long, String> fieldValues) {
        db.delete("note_field_values", "note_id = ?", new String[]{String.valueOf(noteId)});
        if (fieldValues == null) {
            return;
        }
        for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
            ContentValues values = new ContentValues();
            values.put("note_id", noteId);
            values.put("column_id", entry.getKey());
            values.put("value", entry.getValue());
            db.insertOrThrow("note_field_values", null, values);
        }
    }

    private Note mapRow(SQLiteDatabase db, Cursor cursor) {
        long id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        Note note = new Note(
            id,
            cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            cursor.getLong(cursor.getColumnIndexOrThrow("book_id")),
            cursor.getString(cursor.getColumnIndexOrThrow("body")),
            Instant.parse(cursor.getString(cursor.getColumnIndexOrThrow("created_at"))),
            Instant.parse(cursor.getString(cursor.getColumnIndexOrThrow("updated_at")))
        );
        note.setTagIds(loadTagIds(db, id));
        note.setFieldValues(loadFieldValues(db, id));

        // 3列とも既存データではNULL（未設定）のことがあるため、Noteのデフォルト値
        // （白背景・画像なし・黒文字）にフォールバックする。
        String backgroundThemeName = getStringOrNull(cursor, "background_theme");
        if (backgroundThemeName != null) {
            try {
                note.setBackgroundTheme(BookTheme.valueOf(backgroundThemeName));
            } catch (IllegalArgumentException ignored) {
                // 不明な値は既定値のまま
            }
        }
        note.setBackgroundImageFileName(getStringOrNull(cursor, "background_image"));
        String textColorName = getStringOrNull(cursor, "text_color");
        if (textColorName != null) {
            try {
                note.setTextColor(NoteTextColor.valueOf(textColorName));
            } catch (IllegalArgumentException ignored) {
                // 不明な値は既定値のまま
            }
        }
        return note;
    }

    private List<Long> loadTagIds(SQLiteDatabase db, long noteId) {
        List<Long> ids = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT tag_id FROM note_tags WHERE note_id = ?",
                new String[]{String.valueOf(noteId)})) {
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
            }
        }
        return ids;
    }

    private Map<Long, String> loadFieldValues(SQLiteDatabase db, long noteId) {
        Map<Long, String> values = new HashMap<>();
        try (Cursor cursor = db.rawQuery("SELECT column_id, value FROM note_field_values WHERE note_id = ?",
                new String[]{String.valueOf(noteId)})) {
            while (cursor.moveToNext()) {
                values.put(cursor.getLong(0), cursor.getString(1));
            }
        }
        return values;
    }

    private String getStringOrNull(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }
}
