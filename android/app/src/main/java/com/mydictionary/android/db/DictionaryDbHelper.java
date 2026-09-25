package com.mydictionary.android.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** デスクトップ版SqliteDatabaseと同じスキーマをAndroidのSQLiteOpenHelperで再現する。 */
public class DictionaryDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "mydictionary.db";
    private static final int DATABASE_VERSION = 8;

    public DictionaryDbHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * 同期機能用に、アプリ標準のDB名ではなく任意のファイルパスを直接開くコンストラクタ。
     * pathは絶対パスであること（SQLiteOpenHelperは名前にスラッシュを含む場合、絶対パスとして扱う）。
     */
    public DictionaryDbHelper(Context context, String absoluteDbPath) {
        super(context.getApplicationContext(), absoluteDbPath, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // IF NOT EXISTSにしているのは、同期機能でデスクトップ版が作ったmydictionary.dbを
        // このヘルパーで開いた場合に対応するため。デスクトップ側(sqlite-jdbc)はSQLiteの
        // user_versionプラグマを一切更新しないため、テーブルが既に存在していても
        // Androidからは「バージョン0＝新規」に見えてonCreate()が呼ばれてしまう。
        // IF NOT EXISTSなしだと、その時点で「table books already exists」エラーになっていた。
        // ブックの上位オブジェクトである「シェルフ（本棚）」（2026-09追加）。ブックは必ず1つの
        // シェルフに属する。カバーはブックと同じBookCoverの仕組み（模様/カスタム画像）を流用するが、
        // シェルフ自体は色テーマを持たないため、既存のicon_preset/icon_custom_pathとは別の列名にする。
        db.execSQL("CREATE TABLE IF NOT EXISTS shelves ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "uuid TEXT,"
            + "name TEXT NOT NULL,"
            + "cover_pattern TEXT,"
            + "cover_custom_path TEXT,"
            + "sort_order INTEGER,"
            + "created_at TEXT NOT NULL,"
            + "updated_at TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS books ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "uuid TEXT,"
            + "shelf_id INTEGER REFERENCES shelves(id),"
            + "title TEXT NOT NULL,"
            + "theme TEXT NOT NULL,"
            + "font TEXT NOT NULL,"
            + "font_size_pt INTEGER,"
            + "icon_preset TEXT,"
            + "icon_custom_path TEXT,"
            + "sort_order INTEGER,"
            + "created_at TEXT NOT NULL,"
            + "updated_at TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS tags ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "uuid TEXT,"
            + "book_id INTEGER NOT NULL,"
            + "name TEXT NOT NULL,"
            + "parent_tag_id INTEGER,"
            + "updated_at TEXT,"
            + "FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,"
            + "FOREIGN KEY (parent_tag_id) REFERENCES tags(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS notes ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "uuid TEXT,"
            + "book_id INTEGER NOT NULL,"
            + "title TEXT NOT NULL,"
            + "reading TEXT NOT NULL,"
            + "english_translation TEXT,"
            + "body TEXT NOT NULL,"
            + "background_theme TEXT,"
            + "background_image TEXT,"
            + "text_color TEXT,"
            + "created_at TEXT NOT NULL,"
            + "updated_at TEXT NOT NULL,"
            + "FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS note_tags ("
            + "note_id INTEGER NOT NULL,"
            + "tag_id INTEGER NOT NULL,"
            + "PRIMARY KEY (note_id, tag_id),"
            + "FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,"
            + "FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE)");

        // ノートの項目（カラム）をブックごとに自由設定できるようにするための2テーブル
        // （デスクトップ版SqliteDatabaseと同じ設計、2026-09追加）。以前の固定の項目名・読み・英訳は
        // notesテーブルの列としてそのまま残し（データ破損リスクを避けるため）、値自体は
        // note_field_valuesへ移す。sort_orderが最小のカラム（=0番目）が常に必須カラムとして扱われる。
        db.execSQL("CREATE TABLE IF NOT EXISTS note_columns ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "uuid TEXT,"
            + "book_id INTEGER NOT NULL,"
            + "name TEXT NOT NULL,"
            + "required INTEGER NOT NULL DEFAULT 0,"
            + "sort_order INTEGER NOT NULL DEFAULT 0,"
            + "updated_at TEXT,"
            + "FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS note_field_values ("
            + "note_id INTEGER NOT NULL,"
            + "column_id INTEGER NOT NULL,"
            + "value TEXT,"
            + "PRIMARY KEY (note_id, column_id),"
            + "FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,"
            + "FOREIGN KEY (column_id) REFERENCES note_columns(id) ON DELETE CASCADE)");

        migrateExistingRows(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 端末に既に保存されているブック/ノート/タグを消さないよう、
        // テーブルを作り直すのではなくカラムを追加してデータを補完する。
        if (oldVersion < 5) {
            // note_columns/note_field_valuesはonCreate()と同じCREATE TABLE IF NOT EXISTSで
            // 作る必要があるため、onUpgrade側でも明示的に作成しておく
            // （onCreate()はDBが存在しない場合にしか呼ばれない）。
            db.execSQL("CREATE TABLE IF NOT EXISTS note_columns ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "uuid TEXT,"
                + "book_id INTEGER NOT NULL,"
                + "name TEXT NOT NULL,"
                + "required INTEGER NOT NULL DEFAULT 0,"
                + "sort_order INTEGER NOT NULL DEFAULT 0,"
                + "updated_at TEXT,"
                + "FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE)");
            db.execSQL("CREATE TABLE IF NOT EXISTS note_field_values ("
                + "note_id INTEGER NOT NULL,"
                + "column_id INTEGER NOT NULL,"
                + "value TEXT,"
                + "PRIMARY KEY (note_id, column_id),"
                + "FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,"
                + "FOREIGN KEY (column_id) REFERENCES note_columns(id) ON DELETE CASCADE)");
        }
        if (oldVersion < 6) {
            // shelvesはonCreate()と同じCREATE TABLE IF NOT EXISTSで作る必要があるため、
            // onUpgrade側でも明示的に作成しておく（onCreate()はDBが存在しない場合にしか呼ばれない）。
            db.execSQL("CREATE TABLE IF NOT EXISTS shelves ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "uuid TEXT,"
                + "name TEXT NOT NULL,"
                + "cover_pattern TEXT,"
                + "cover_custom_path TEXT,"
                + "sort_order INTEGER,"
                + "created_at TEXT NOT NULL,"
                + "updated_at TEXT)");
        }
        // onUpgrade()自体がoldVersion < newVersionのときしか呼ばれないため、ここは常に
        // migrateExistingRows()を実行してよい（＝version 6→7のように新しいテーブル追加を
        // 伴わない移行だけの更新でも、確実に新しいマイグレーション処理が走るようにするため）。
        migrateExistingRows(db);
    }

    /** uuid同期機能の追加やノートの見た目設定追加より前に作成されたDBに、既存の行を壊さずカラムを補完する。 */
    private void migrateExistingRows(SQLiteDatabase db) {
        addColumnIfMissing(db, "shelves", "uuid", "TEXT");
        addColumnIfMissing(db, "shelves", "updated_at", "TEXT");
        addColumnIfMissing(db, "shelves", "sort_order", "INTEGER");
        addColumnIfMissing(db, "books", "uuid", "TEXT");
        addColumnIfMissing(db, "books", "updated_at", "TEXT");
        addColumnIfMissing(db, "books", "sort_order", "INTEGER");
        addColumnIfMissing(db, "books", "shelf_id", "INTEGER");
        addColumnIfMissing(db, "books", "font_size_pt", "INTEGER");
        addColumnIfMissing(db, "tags", "uuid", "TEXT");
        addColumnIfMissing(db, "tags", "updated_at", "TEXT");
        addColumnIfMissing(db, "notes", "uuid", "TEXT");
        addColumnIfMissing(db, "notes", "background_theme", "TEXT");
        addColumnIfMissing(db, "notes", "background_image", "TEXT");
        addColumnIfMissing(db, "notes", "text_color", "TEXT");

        backfillUuids(db, "shelves");
        backfillUuids(db, "books");
        backfillUuids(db, "tags");
        backfillUuids(db, "notes");
        db.execSQL("UPDATE shelves SET updated_at = ? WHERE updated_at IS NULL",
            new Object[]{java.time.Instant.now().toString()});
        db.execSQL("UPDATE shelves SET sort_order = id WHERE sort_order IS NULL");
        db.execSQL("UPDATE books SET updated_at = created_at WHERE updated_at IS NULL");
        db.execSQL("UPDATE books SET sort_order = id WHERE sort_order IS NULL");
        db.execSQL("UPDATE tags SET updated_at = ? WHERE updated_at IS NULL",
            new Object[]{java.time.Instant.now().toString()});
        migrateBookShelves(db);
        migrateNoteColumns(db);
        deduplicateNoteColumns(db);

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_shelves_uuid ON shelves(uuid)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_books_uuid ON books(uuid)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_uuid ON tags(uuid)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_notes_uuid ON notes(uuid)");
    }

    /**
     * まだシェルフに属していないブック（＝シェルフ機能追加より前から存在するブック）を、
     * 新規に作る既定のシェルフ「マイシェルフ」へまとめて割り当てる。新規に作るブックは
     * AndroidBookRepository.insert()が必ず所属シェルフを指定するため、このメソッドの対象にならない。
     */
    private void migrateBookShelves(SQLiteDatabase db) {
        List<Long> bookIdsWithoutShelf = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT id FROM books WHERE shelf_id IS NULL", null)) {
            while (cursor.moveToNext()) {
                bookIdsWithoutShelf.add(cursor.getLong(0));
            }
        }
        if (bookIdsWithoutShelf.isEmpty()) {
            return;
        }

        long defaultShelfId = insertDefaultShelf(db);
        for (Long bookId : bookIdsWithoutShelf) {
            ContentValues values = new ContentValues();
            values.put("shelf_id", defaultShelfId);
            db.update("books", values, "id = ?", new String[]{String.valueOf(bookId)});
        }
    }

    /**
     * 既定シェルフのuuidは、ランダムではなく固定の名前空間UUIDにする。デスクトップ・Android
     * 双方がこのマイグレーションを独立に実行しても同じuuidになるようにするためで、
     * これが無いと（2026-09に実際に発生した不具合のように）両OSでそれぞれ別uuidの
     * 「マイシェルフ」が作られ、後で同期した際に同じ名前のシェルフが2つ残ってしまう。
     */
    private static final String DEFAULT_SHELF_UUID =
        UUID.nameUUIDFromBytes("com.mydictionary.migration.default-shelf".getBytes(StandardCharsets.UTF_8))
            .toString();

    private long insertDefaultShelf(SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put("uuid", DEFAULT_SHELF_UUID);
        values.put("name", "マイシェルフ");
        values.put("cover_pattern", "STRIPES");
        values.put("sort_order", 0);
        String now = java.time.Instant.now().toString();
        values.put("created_at", now);
        values.put("updated_at", now);
        return db.insertOrThrow("shelves", null, values);
    }

    /**
     * まだ1つもカラムが定義されていないブック（＝この機能追加より前から存在するブック）に、
     * 既定の3カラム（項目名=必須, 読み=必須, 英訳=任意）を補完し、既存ノートのtitle/reading/
     * english_translationの値をnote_field_valuesへ複製する。新規に作るブックはこのメソッドの
     * 対象にならないが、AndroidBookRepository.insert()が同じ既定カラムを作成する。
     */
    private void migrateNoteColumns(SQLiteDatabase db) {
        List<Long> bookIdsWithoutColumns = new ArrayList<>();
        Map<Long, String> bookUuidById = new LinkedHashMap<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, uuid FROM books WHERE id NOT IN (SELECT DISTINCT book_id FROM note_columns)", null)) {
            while (cursor.moveToNext()) {
                long bookId = cursor.getLong(0);
                bookIdsWithoutColumns.add(bookId);
                bookUuidById.put(bookId, cursor.getString(1));
            }
        }

        for (Long bookId : bookIdsWithoutColumns) {
            String bookUuid = bookUuidById.get(bookId);
            long titleColumnId = insertNoteColumn(db, bookId, defaultColumnUuid(bookUuid, "項目名"), "項目名", true, 0);
            long readingColumnId = insertNoteColumn(db, bookId, defaultColumnUuid(bookUuid, "読み"), "読み", true, 1);
            long englishColumnId = insertNoteColumn(db, bookId, defaultColumnUuid(bookUuid, "英訳"), "英訳", false, 2);

            try (Cursor cursor = db.rawQuery(
                    "SELECT id, title, reading, english_translation FROM notes WHERE book_id = ?",
                    new String[]{String.valueOf(bookId)})) {
                while (cursor.moveToNext()) {
                    long noteId = cursor.getLong(0);
                    insertFieldValueIfAbsent(db, noteId, titleColumnId, cursor.getString(1));
                    insertFieldValueIfAbsent(db, noteId, readingColumnId, cursor.getString(2));
                    String english = cursor.getString(3);
                    if (english != null) {
                        insertFieldValueIfAbsent(db, noteId, englishColumnId, english);
                    }
                }
            }
        }
    }

    /**
     * 移行で自動生成する既定カラムのuuidは、ランダムではなく「ブックのuuid＋カラム名」から
     * 決定的に導出する。デスクトップ・Android双方がこの移行を独立に実行しても同じuuidになる
     * ようにするためで、これが無いと（2026-09に実際に発生した不具合のように）両OSでそれぞれ
     * 別uuidのカラムが作られ、後で同期した際に同じ役割のカラムが重複して残ってしまう。
     */
    private static String defaultColumnUuid(String bookUuid, String columnName) {
        String seed = "note-column-migration:" + bookUuid + ":" + columnName;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private long insertNoteColumn(SQLiteDatabase db, long bookId, String uuid, String name, boolean required,
                                   int sortOrder) {
        ContentValues values = new ContentValues();
        values.put("uuid", uuid);
        values.put("book_id", bookId);
        values.put("name", name);
        values.put("required", required ? 1 : 0);
        values.put("sort_order", sortOrder);
        values.put("updated_at", java.time.Instant.now().toString());
        return db.insertOrThrow("note_columns", null, values);
    }

    /**
     * 過去のバグ（上記defaultColumnUuid導入前は既定カラムのuuidがランダムだったため、
     * デスクトップ・Android双方で独立に移行が走った後に同期すると、同じ役割のカラムが
     * uuid違いで重複して残ってしまっていた）で既に重複してしまったカラムを統合する。
     * 同じブック・同じ名前のカラムが複数あれば、sort_order（次点でid）が最小のものを残し、
     * 他のカラムに入力されていた値を残す方が空の場合にだけ複製してから、他のカラムを削除する
     * （note_field_valuesはON DELETE CASCADEなので、カラム削除で自動的に消える）。
     */
    private void deduplicateNoteColumns(SQLiteDatabase db) {
        Map<String, List<long[]>> groups = new LinkedHashMap<>();
        try (Cursor cursor = db.rawQuery(
                "SELECT id, book_id, name, sort_order, required FROM note_columns "
                    + "ORDER BY book_id, name, sort_order, id", null)) {
            while (cursor.moveToNext()) {
                String key = cursor.getLong(1) + "|" + cursor.getString(2);
                groups.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new long[]{cursor.getLong(0), cursor.getInt(4)});
            }
        }

        for (List<long[]> group : groups.values()) {
            if (group.size() <= 1) {
                continue;
            }
            long canonicalId = group.get(0)[0];
            boolean anyRequired = group.get(0)[1] != 0;
            for (int i = 1; i < group.size(); i++) {
                long duplicateId = group.get(i)[0];
                anyRequired = anyRequired || group.get(i)[1] != 0;
                mergeFieldValuesInto(db, canonicalId, duplicateId);
                db.delete("note_columns", "id = ?", new String[]{String.valueOf(duplicateId)});
            }
            if (anyRequired) {
                ContentValues values = new ContentValues();
                values.put("required", 1);
                db.update("note_columns", values, "id = ?", new String[]{String.valueOf(canonicalId)});
            }
        }
    }

    private void mergeFieldValuesInto(SQLiteDatabase db, long canonicalColumnId, long duplicateColumnId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT note_id, value FROM note_field_values WHERE column_id = ?",
                new String[]{String.valueOf(duplicateColumnId)})) {
            while (cursor.moveToNext()) {
                long noteId = cursor.getLong(0);
                String duplicateValue = cursor.getString(1);
                if (duplicateValue == null || duplicateValue.isEmpty()) {
                    continue;
                }
                String canonicalValue = getFieldValue(db, canonicalColumnId, noteId);
                if (canonicalValue == null || canonicalValue.isEmpty()) {
                    ContentValues values = new ContentValues();
                    values.put("note_id", noteId);
                    values.put("column_id", canonicalColumnId);
                    values.put("value", duplicateValue);
                    db.insertWithOnConflict("note_field_values", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
        }
    }

    private String getFieldValue(SQLiteDatabase db, long columnId, long noteId) {
        try (Cursor cursor = db.rawQuery(
                "SELECT value FROM note_field_values WHERE column_id = ? AND note_id = ?",
                new String[]{String.valueOf(columnId), String.valueOf(noteId)})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private void insertFieldValueIfAbsent(SQLiteDatabase db, long noteId, long columnId, String value) {
        ContentValues values = new ContentValues();
        values.put("note_id", noteId);
        values.put("column_id", columnId);
        values.put("value", value == null ? "" : value);
        db.insertWithOnConflict("note_field_values", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private boolean columnExists(SQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                if (column.equalsIgnoreCase(cursor.getString(nameIndex))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String type) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    private void backfillUuids(SQLiteDatabase db, String table) {
        List<Long> idsNeedingUuid = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT id FROM " + table + " WHERE uuid IS NULL", null)) {
            while (cursor.moveToNext()) {
                idsNeedingUuid.add(cursor.getLong(0));
            }
        }
        for (Long id : idsNeedingUuid) {
            db.execSQL("UPDATE " + table + " SET uuid = ? WHERE id = ?",
                new Object[]{UUID.randomUUID().toString(), id});
        }
    }
}
