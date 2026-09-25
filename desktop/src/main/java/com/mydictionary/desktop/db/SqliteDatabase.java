package com.mydictionary.desktop.db;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SqliteDatabase implements AutoCloseable {
    private final Path dbPath;
    private Connection connection;

    public SqliteDatabase(Path dbPath) {
        this.dbPath = dbPath;
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA foreign_keys = ON");
                }
            }
            return connection;
        } catch (SQLException e) {
            throw new RuntimeException("データベース接続に失敗しました", e);
        }
    }

    public void initSchema() {
        // ブックの上位オブジェクトである「シェルフ（本棚）」（2026-09追加）。ブックは必ず1つの
        // シェルフに属する。カバーはブックと同じBookCoverの仕組み（模様/カスタム画像）を流用するが、
        // シェルフ自体は色テーマを持たないため、既存のicon_preset/icon_custom_pathとは別の列名にする。
        String createShelves = """
            CREATE TABLE IF NOT EXISTS shelves (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                name TEXT NOT NULL,
                cover_pattern TEXT,
                cover_custom_path TEXT,
                sort_order INTEGER,
                created_at TEXT NOT NULL,
                updated_at TEXT
            )
            """;
        String createBooks = """
            CREATE TABLE IF NOT EXISTS books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                shelf_id INTEGER REFERENCES shelves(id),
                title TEXT NOT NULL,
                theme TEXT NOT NULL,
                font TEXT NOT NULL,
                font_size_pt INTEGER,
                icon_preset TEXT,
                icon_custom_path TEXT,
                sort_order INTEGER,
                created_at TEXT NOT NULL,
                updated_at TEXT
            )
            """;
        String createTags = """
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                book_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                parent_tag_id INTEGER,
                updated_at TEXT,
                FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE,
                FOREIGN KEY (parent_tag_id) REFERENCES tags(id) ON DELETE CASCADE
            )
            """;
        String createNotes = """
            CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                book_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                reading TEXT NOT NULL,
                english_translation TEXT,
                body TEXT NOT NULL,
                background_theme TEXT,
                background_image TEXT,
                text_color TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
            )
            """;
        String createNoteTags = """
            CREATE TABLE IF NOT EXISTS note_tags (
                note_id INTEGER NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY (note_id, tag_id),
                FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,
                FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE
            )
            """;
        // ノートの項目（カラム）をブックごとに自由設定できるようにするための2テーブル（2026-09追加）。
        // 以前の固定の項目名・読み・英訳は、既存のnotesテーブルの列としてはそのまま残し
        // （データ破損リスクを避けるため）、値自体はnote_field_valuesへ移す。
        // sort_orderが最小のカラム（=0番目）が常に必須カラムとして扱われる。
        String createNoteColumns = """
            CREATE TABLE IF NOT EXISTS note_columns (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                book_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                required INTEGER NOT NULL DEFAULT 0,
                sort_order INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT,
                FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
            )
            """;
        String createNoteFieldValues = """
            CREATE TABLE IF NOT EXISTS note_field_values (
                note_id INTEGER NOT NULL,
                column_id INTEGER NOT NULL,
                value TEXT,
                PRIMARY KEY (note_id, column_id),
                FOREIGN KEY (note_id) REFERENCES notes(id) ON DELETE CASCADE,
                FOREIGN KEY (column_id) REFERENCES note_columns(id) ON DELETE CASCADE
            )
            """;

        try (Statement st = getConnection().createStatement()) {
            st.execute(createShelves);
            st.execute(createBooks);
            st.execute(createTags);
            st.execute(createNotes);
            st.execute(createNoteTags);
            st.execute(createNoteColumns);
            st.execute(createNoteFieldValues);
        } catch (SQLException e) {
            throw new RuntimeException("スキーマ初期化に失敗しました", e);
        }

        migrateExistingRows();
    }

    /**
     * デバイス間同期機能の追加(uuid/updated_atカラム)より前に作成されたデータベースに対して、
     * 既存の行を壊さずにカラムを補完する。新規作成したデータベースでは何もしない。
     */
    private void migrateExistingRows() {
        addColumnIfMissing("shelves", "uuid", "TEXT");
        addColumnIfMissing("shelves", "updated_at", "TEXT");
        addColumnIfMissing("shelves", "sort_order", "INTEGER");
        addColumnIfMissing("books", "uuid", "TEXT");
        addColumnIfMissing("books", "updated_at", "TEXT");
        addColumnIfMissing("books", "sort_order", "INTEGER");
        addColumnIfMissing("books", "shelf_id", "INTEGER");
        addColumnIfMissing("books", "font_size_pt", "INTEGER");
        addColumnIfMissing("tags", "uuid", "TEXT");
        addColumnIfMissing("tags", "updated_at", "TEXT");
        addColumnIfMissing("notes", "uuid", "TEXT");
        addColumnIfMissing("notes", "background_theme", "TEXT");
        addColumnIfMissing("notes", "background_image", "TEXT");
        addColumnIfMissing("notes", "text_color", "TEXT");

        backfillUuids("shelves");
        backfillUuids("books");
        backfillUuids("tags");
        backfillUuids("notes");
        backfillUpdatedAtFromCreatedAt("books");
        backfillUpdatedAtWithNow("shelves");
        backfillUpdatedAtWithNow("tags");
        backfillBookSortOrder();
        backfillShelfSortOrder();
        migrateBookShelves();
        migrateNoteColumns();
        deduplicateNoteColumns();

        try (Statement st = getConnection().createStatement()) {
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_shelves_uuid ON shelves(uuid)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_books_uuid ON books(uuid)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_uuid ON tags(uuid)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_notes_uuid ON notes(uuid)");
        } catch (SQLException e) {
            throw new RuntimeException("uuidインデックスの作成に失敗しました", e);
        }
    }

    /** sort_order未設定のシェルフに、既存の並び（id昇順）を保ったままの初期値を振る。 */
    private void backfillShelfSortOrder() {
        try (Statement st = getConnection().createStatement()) {
            st.execute("UPDATE shelves SET sort_order = id WHERE sort_order IS NULL");
        } catch (SQLException e) {
            throw new RuntimeException("シェルフのsort_orderの補完に失敗しました", e);
        }
    }

    /**
     * まだシェルフに属していないブック（＝シェルフ機能追加より前から存在するブック）を、
     * 新規に作る既定のシェルフ「マイシェルフ」へまとめて割り当てる。新規に作るブックは
     * SceneNavigator側で必ず所属シェルフを指定してinsert()するため、このメソッドの対象にならない。
     */
    private void migrateBookShelves() {
        List<Long> bookIdsWithoutShelf = new ArrayList<>();
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM books WHERE shelf_id IS NULL")) {
            while (rs.next()) {
                bookIdsWithoutShelf.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("シェルフ未設定ブックの取得に失敗しました", e);
        }
        if (bookIdsWithoutShelf.isEmpty()) {
            return;
        }

        long defaultShelfId = insertDefaultShelf();
        try (PreparedStatement ps = getConnection().prepareStatement("UPDATE books SET shelf_id = ? WHERE id = ?")) {
            for (Long bookId : bookIdsWithoutShelf) {
                ps.setLong(1, defaultShelfId);
                ps.setLong(2, bookId);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("ブックへのシェルフ割り当てに失敗しました", e);
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

    private long insertDefaultShelf() {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO shelves (uuid, name, cover_pattern, cover_custom_path, sort_order, "
                    + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            String now = java.time.Instant.now().toString();
            ps.setString(1, DEFAULT_SHELF_UUID);
            ps.setString(2, "マイシェルフ");
            ps.setString(3, "STRIPES");
            ps.setString(4, null);
            ps.setInt(5, 0);
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("既定シェルフの作成に失敗しました", e);
        }
        throw new IllegalStateException("既定シェルフIDの採番に失敗しました");
    }

    /**
     * まだ1つもカラムが定義されていないブック（＝この機能追加より前から存在するブック）に、
     * 既定の3カラム（項目名=必須, 読み=必須, 英訳=任意）を補完し、既存ノートのtitle/reading/
     * english_translationの値をnote_field_valuesへ複製する。新規に作るブックはこのメソッドの
     * 対象にならないが、SqliteBookRepository.insert()が同じ既定カラムを作成する。
     */
    private void migrateNoteColumns() {
        List<Long> bookIdsWithoutColumns = new ArrayList<>();
        Map<Long, String> bookUuidById = new LinkedHashMap<>();
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, uuid FROM books WHERE id NOT IN (SELECT DISTINCT book_id FROM note_columns)")) {
            while (rs.next()) {
                long bookId = rs.getLong("id");
                bookIdsWithoutColumns.add(bookId);
                bookUuidById.put(bookId, rs.getString("uuid"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("カラム未設定ブックの取得に失敗しました", e);
        }

        for (Long bookId : bookIdsWithoutColumns) {
            String bookUuid = bookUuidById.get(bookId);
            long titleColumnId = insertNoteColumn(bookId, defaultColumnUuid(bookUuid, "項目名"), "項目名", true, 0);
            long readingColumnId = insertNoteColumn(bookId, defaultColumnUuid(bookUuid, "読み"), "読み", true, 1);
            long englishColumnId = insertNoteColumn(bookId, defaultColumnUuid(bookUuid, "英訳"), "英訳", false, 2);

            try (PreparedStatement ps = getConnection().prepareStatement(
                    "SELECT id, title, reading, english_translation FROM notes WHERE book_id = ?")) {
                ps.setLong(1, bookId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long noteId = rs.getLong("id");
                        insertFieldValueIfAbsent(noteId, titleColumnId, rs.getString("title"));
                        insertFieldValueIfAbsent(noteId, readingColumnId, rs.getString("reading"));
                        String english = rs.getString("english_translation");
                        if (english != null) {
                            insertFieldValueIfAbsent(noteId, englishColumnId, english);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("ノートのカラム値移行に失敗しました", e);
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

    private long insertNoteColumn(long bookId, String uuid, String name, boolean required, int sortOrder) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO note_columns (uuid, book_id, name, required, sort_order, updated_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid);
            ps.setLong(2, bookId);
            ps.setString(3, name);
            ps.setInt(4, required ? 1 : 0);
            ps.setInt(5, sortOrder);
            ps.setString(6, java.time.Instant.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("既定カラムの作成に失敗しました: " + name, e);
        }
        throw new IllegalStateException("既定カラムIDの採番に失敗しました");
    }

    /**
     * 過去のバグ（上記defaultColumnUuid導入前は既定カラムのuuidがランダムだったため、
     * デスクトップ・Android双方で独立に移行が走った後に同期すると、同じ役割のカラムが
     * uuid違いで重複して残ってしまっていた）で既に重複してしまったカラムを統合する。
     * 同じブック・同じ名前のカラムが複数あれば、sort_order（次点でid）が最小のものを残し、
     * 他のカラムに入力されていた値を残す方が空の場合にだけ複製してから、他のカラムを削除する
     * （note_field_valuesはON DELETE CASCADEなので、カラム削除で自動的に消える）。
     */
    private void deduplicateNoteColumns() {
        Map<String, List<long[]>> groups = new LinkedHashMap<>();
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT id, book_id, name, sort_order, required FROM note_columns "
                     + "ORDER BY book_id, name, sort_order, id")) {
            while (rs.next()) {
                String key = rs.getLong("book_id") + "|" + rs.getString("name");
                groups.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new long[]{rs.getLong("id"), rs.getInt("required")});
            }
        } catch (SQLException e) {
            throw new RuntimeException("重複カラムの検出に失敗しました", e);
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
                mergeFieldValuesInto(canonicalId, duplicateId);
                deleteNoteColumn(duplicateId);
            }
            if (anyRequired) {
                setColumnRequired(canonicalId, true);
            }
        }
    }

    private void mergeFieldValuesInto(long canonicalColumnId, long duplicateColumnId) {
        try (PreparedStatement selectDup = getConnection().prepareStatement(
                "SELECT note_id, value FROM note_field_values WHERE column_id = ?")) {
            selectDup.setLong(1, duplicateColumnId);
            try (ResultSet rs = selectDup.executeQuery()) {
                while (rs.next()) {
                    long noteId = rs.getLong("note_id");
                    String duplicateValue = rs.getString("value");
                    if (duplicateValue == null || duplicateValue.isEmpty()) {
                        continue;
                    }
                    String canonicalValue = getFieldValue(canonicalColumnId, noteId);
                    if (canonicalValue == null || canonicalValue.isEmpty()) {
                        upsertFieldValue(canonicalColumnId, noteId, duplicateValue);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("重複カラムの値統合に失敗しました", e);
        }
    }

    private String getFieldValue(long columnId, long noteId) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "SELECT value FROM note_field_values WHERE column_id = ? AND note_id = ?")) {
            ps.setLong(1, columnId);
            ps.setLong(2, noteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        }
    }

    private void upsertFieldValue(long columnId, long noteId, String value) throws SQLException {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT INTO note_field_values (note_id, column_id, value) VALUES (?, ?, ?) "
                    + "ON CONFLICT(note_id, column_id) DO UPDATE SET value = excluded.value")) {
            ps.setLong(1, noteId);
            ps.setLong(2, columnId);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    private void deleteNoteColumn(long columnId) {
        try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM note_columns WHERE id = ?")) {
            ps.setLong(1, columnId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("重複カラムの削除に失敗しました", e);
        }
    }

    private void setColumnRequired(long columnId, boolean required) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE note_columns SET required = ? WHERE id = ?")) {
            ps.setInt(1, required ? 1 : 0);
            ps.setLong(2, columnId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("カラムのrequired更新に失敗しました", e);
        }
    }

    private void insertFieldValueIfAbsent(long noteId, long columnId, String value) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT OR IGNORE INTO note_field_values (note_id, column_id, value) VALUES (?, ?, ?)")) {
            ps.setLong(1, noteId);
            ps.setLong(2, columnId);
            ps.setString(3, value == null ? "" : value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ノートのカラム値移行に失敗しました", e);
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addColumnIfMissing(String table, String column, String type) {
        try {
            if (!columnExists(table, column)) {
                getConnection().createStatement().execute(
                    "ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            }
        } catch (SQLException e) {
            throw new RuntimeException("カラムの追加に失敗しました: " + table + "." + column, e);
        }
    }

    private void backfillUuids(String table) {
        List<Long> idsNeedingUuid = new ArrayList<>();
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM " + table + " WHERE uuid IS NULL")) {
            while (rs.next()) {
                idsNeedingUuid.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("uuid未設定行の取得に失敗しました: " + table, e);
        }

        for (Long id : idsNeedingUuid) {
            try (PreparedStatement ps = getConnection().prepareStatement(
                    "UPDATE " + table + " SET uuid = ? WHERE id = ?")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setLong(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("uuidの補完に失敗しました: " + table, e);
            }
        }
    }

    private void backfillUpdatedAtFromCreatedAt(String table) {
        try (Statement st = getConnection().createStatement()) {
            st.execute("UPDATE " + table + " SET updated_at = created_at WHERE updated_at IS NULL");
        } catch (SQLException e) {
            throw new RuntimeException("updated_atの補完に失敗しました: " + table, e);
        }
    }

    /** sort_order未設定のブックに、既存の並び（id昇順）を保ったままの初期値を振る。 */
    private void backfillBookSortOrder() {
        try (Statement st = getConnection().createStatement()) {
            st.execute("UPDATE books SET sort_order = id WHERE sort_order IS NULL");
        } catch (SQLException e) {
            throw new RuntimeException("sort_orderの補完に失敗しました", e);
        }
    }

    /** created_at列を持たないテーブル（tags）向けに、現在時刻でupdated_atを補完する。 */
    private void backfillUpdatedAtWithNow(String table) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "UPDATE " + table + " SET updated_at = ? WHERE updated_at IS NULL")) {
            ps.setString(1, java.time.Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updated_atの補完に失敗しました: " + table, e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("データベースのクローズに失敗しました", e);
        }
    }
}
