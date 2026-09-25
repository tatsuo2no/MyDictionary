package com.mydictionary.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.repository.BookRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AndroidBookRepository implements BookRepository {
    private final DictionaryDbHelper dbHelper;

    public AndroidBookRepository(DictionaryDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @Override
    public List<Book> findAll() {
        List<Book> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM books ORDER BY sort_order, id", null)) {
            while (cursor.moveToNext()) {
                result.add(mapRow(cursor));
            }
        }
        return result;
    }

    @Override
    public Optional<Book> findById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM books WHERE id = ?",
                new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Book> findByUuid(String uuid) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM books WHERE uuid = ?", new String[]{uuid})) {
            if (cursor.moveToFirst()) {
                return Optional.of(mapRow(cursor));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Book> findByShelfId(long shelfId) {
        List<Book> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT * FROM books WHERE shelf_id = ? ORDER BY sort_order, id",
                new String[]{String.valueOf(shelfId)})) {
            while (cursor.moveToNext()) {
                result.add(mapRow(cursor));
            }
        }
        return result;
    }

    @Override
    public Book insert(Book book) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String now = Instant.now().toString();
        ContentValues values = toContentValues(book);
        values.put("uuid", UUID.randomUUID().toString());
        values.put("sort_order", nextSortOrder(db, book.getShelfId()));
        values.put("created_at", now);
        values.put("updated_at", now);
        long id = db.insertOrThrow("books", null, values);
        return findById(id).orElseThrow(() -> new IllegalStateException("ブックの作成に失敗しました"));
    }

    @Override
    public void update(Book book) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = toContentValues(book);
        values.put("updated_at", Instant.now().toString());
        db.update("books", values, "id = ?", new String[]{String.valueOf(book.getId())});
    }

    @Override
    public void delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("books", "id = ?", new String[]{String.valueOf(id)});
    }

    @Override
    public void updateManualOrder(List<Long> orderedBookIds) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int i = 0; i < orderedBookIds.size(); i++) {
                ContentValues values = new ContentValues();
                values.put("sort_order", i);
                db.update("books", values, "id = ?", new String[]{String.valueOf(orderedBookIds.get(i))});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 同一シェルフ内で新規ブックを末尾に付けるための sort_order 値（そのシェルフ内の既存最大値+1）。 */
    private int nextSortOrder(SQLiteDatabase db, long shelfId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM books WHERE shelf_id = ?",
                new String[]{String.valueOf(shelfId)})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    @Override
    public Book upsertFromSync(String uuid, long shelfId, String title, BookTheme theme, BookFont font,
                                int fontSizePt, BookCover cover, Instant createdAt, Instant updatedAt) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Optional<Book> existing = findByUuid(uuid);

        ContentValues values = new ContentValues();
        values.put("shelf_id", shelfId);
        values.put("title", title);
        values.put("theme", theme.name());
        values.put("font", font.name());
        values.put("font_size_pt", fontSizePt);
        if (cover.isCustom()) {
            values.putNull("icon_preset");
            values.put("icon_custom_path", cover.getCustomImagePath());
        } else {
            values.put("icon_preset", cover.getPattern().name());
            values.putNull("icon_custom_path");
        }
        values.put("updated_at", updatedAt.toString());

        if (existing.isPresent()) {
            db.update("books", values, "uuid = ?", new String[]{uuid});
        } else {
            values.put("uuid", uuid);
            // sort_order（表示順）は同期対象外。同期で入ってきたブックはそのシェルフ内の末尾に付ける。
            values.put("sort_order", nextSortOrder(db, shelfId));
            values.put("created_at", createdAt.toString());
            db.insertOrThrow("books", null, values);
        }
        return findByUuid(uuid).orElseThrow();
    }

    private ContentValues toContentValues(Book book) {
        ContentValues values = new ContentValues();
        values.put("shelf_id", book.getShelfId());
        values.put("title", book.getTitle());
        values.put("theme", book.getTheme().name());
        values.put("font", book.getFont().name());
        values.put("font_size_pt", book.getFontSizePt());
        if (book.getCover().isCustom()) {
            values.putNull("icon_preset");
            values.put("icon_custom_path", book.getCover().getCustomImagePath());
        } else {
            values.put("icon_preset", book.getCover().getPattern().name());
            values.putNull("icon_custom_path");
        }
        return values;
    }

    private Book mapRow(Cursor cursor) {
        String coverPatternName = getStringOrNull(cursor, "icon_preset");
        String coverCustomPath = getStringOrNull(cursor, "icon_custom_path");
        BookCover cover = coverCustomPath != null
            ? BookCover.ofCustom(coverCustomPath)
            : BookCover.ofPattern(resolvePattern(coverPatternName));

        Book book = new Book(
            cursor.getLong(cursor.getColumnIndexOrThrow("id")),
            cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
            cursor.getLong(cursor.getColumnIndexOrThrow("shelf_id")),
            cursor.getString(cursor.getColumnIndexOrThrow("title")),
            BookTheme.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("theme"))),
            resolveFont(cursor.getString(cursor.getColumnIndexOrThrow("font"))),
            cover,
            Instant.parse(cursor.getString(cursor.getColumnIndexOrThrow("created_at"))),
            Instant.parse(cursor.getString(cursor.getColumnIndexOrThrow("updated_at")))
        );
        book.setSortOrder(cursor.getInt(cursor.getColumnIndexOrThrow("sort_order")));
        book.setFontSizePt(resolveFontSizePt(cursor));
        return book;
    }

    /** 移行直後などfont_size_ptがまだNULLの既存行は、既定サイズにフォールバックする。 */
    private static int resolveFontSizePt(Cursor cursor) {
        int index = cursor.getColumnIndexOrThrow("font_size_pt");
        int value = cursor.isNull(index) ? 0 : cursor.getInt(index);
        return value <= 0 ? Book.DEFAULT_FONT_SIZE_PT : value;
    }

    /**
     * DB列名(icon_preset/icon_custom_path)は、以前の「記号アイコン」機能の名残をそのまま
     * 流用している（スキーマ変更によるデータ破損リスクを避けるため）。旧アイコン機能で保存された
     * 記号名（例: "BOOK"）はPattern.valueOf()で解決できないため、その場合はデフォルトの模様に
     * フォールバックし、既存ブックの読み込みが例外で落ちないようにする（デスクトップ版と同じ対応）。
     */
    private static BookCover.Pattern resolvePattern(String name) {
        try {
            return BookCover.Pattern.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return BookCover.Pattern.STRIPES;
        }
    }

    /**
     * 太字非対応フォント（MS ゴシック等）をBookFontから削除した際、既存ブックが持つ
     * 旧フォント名の値はvalueOf()で解決できなくなる。その場合はデフォルトのMeiryo UIに
     * フォールバックし、既存ブックの読み込みが例外で落ちないようにする（デスクトップ版と同じ対応）。
     */
    private static BookFont resolveFont(String name) {
        try {
            return BookFont.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return BookFont.MEIRYO_UI;
        }
    }

    private String getStringOrNull(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? null : cursor.getString(index);
    }
}
