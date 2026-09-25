package com.mydictionary.desktop.db;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.NoteTextColor;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.model.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BookCreateScreen等のUI操作を経由せず、SQLite実装が実際に保存・取得できることを検証する。
 */
class SqliteRepositoryIntegrationTest {

    private static long insertShelf(SqliteDatabase database) {
        return new SqliteShelfRepository(database).insert(new Shelf(0, null, "シェルフ",
            BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now())).getId();
    }

    @Test
    void createsBookWithTagAndNoteThenReadsThemBack(@TempDir Path tempDir) {
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();

            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            Book book = bookRepository.insert(new Book(0, null, insertShelf(database), "テストブック", BookTheme.GREEN,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.CHECKERS), Instant.now(), Instant.now()));
            assertTrue(book.getId() > 0);

            SqliteTagRepository tagRepository = new SqliteTagRepository(database);
            Tag parentTag = tagRepository.insert(new Tag(0, null, book.getId(), "分類", null, Instant.now()));
            Tag childTag = tagRepository.insert(new Tag(0, null, book.getId(), "動物", parentTag.getId(), Instant.now()));
            assertEquals(parentTag.getId(), childTag.getParentTagId());

            List<NoteColumn> columns = new SqliteNoteColumnRepository(database).findByBookId(book.getId());
            long titleColumnId = columns.get(0).getId();

            SqliteNoteRepository noteRepository = new SqliteNoteRepository(database);
            Note note = new Note(0, null, book.getId(), "# 犬について", Instant.now(), Instant.now());
            note.setFieldValues(Map.of(titleColumnId, "犬", columns.get(1).getId(), "いぬ", columns.get(2).getId(), "dog"));
            note.setTagIds(List.of(childTag.getId()));
            Note savedNote = noteRepository.insert(note);

            List<Book> allBooks = bookRepository.findAll();
            assertEquals(1, allBooks.size());

            List<Note> notesInBook = noteRepository.findByBookId(book.getId());
            assertEquals(1, notesInBook.size());
            assertEquals("犬", notesInBook.get(0).getFieldValue(titleColumnId));
            assertEquals(List.of(childTag.getId()), notesInBook.get(0).getTagIds());

            List<Note> foundByTag = noteRepository.findByTag(book.getId(), childTag.getId());
            assertEquals(savedNote.getId(), foundByTag.get(0).getId());

            List<Note> searchResult = noteRepository.search(book.getId(), "犬");
            assertEquals(1, searchResult.size());
        }
    }

    @Test
    void deletingParentTagCascadesToChildTagAndNoteAssociation(@TempDir Path tempDir) {
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();

            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            Book book = bookRepository.insert(new Book(0, null, insertShelf(database), "テストブック", BookTheme.GREEN,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.CHECKERS), Instant.now(), Instant.now()));

            SqliteTagRepository tagRepository = new SqliteTagRepository(database);
            Tag parentTag = tagRepository.insert(new Tag(0, null, book.getId(), "分類", null, Instant.now()));
            Tag childTag = tagRepository.insert(new Tag(0, null, book.getId(), "動物", parentTag.getId(), Instant.now()));

            SqliteNoteRepository noteRepository = new SqliteNoteRepository(database);
            Note note = new Note(0, null, book.getId(), "# 犬について", Instant.now(), Instant.now());
            note.setTagIds(List.of(childTag.getId()));
            Note savedNote = noteRepository.insert(note);

            assertTrue(tagRepository.isUsedByAnyNote(childTag.getId()));

            // TagScreenは「親タグ＋子孫タグのいずれかが使用中か」を確認してから親タグを削除するため、
            // DBの外部キーカスケードが子タグ・ノートとの紐付けまで正しく消すことを検証する。
            tagRepository.delete(parentTag.getId());

            assertTrue(tagRepository.findById(parentTag.getId()).isEmpty());
            assertTrue(tagRepository.findById(childTag.getId()).isEmpty());

            Note reloadedNote = noteRepository.findById(savedNote.getId()).orElseThrow();
            assertTrue(reloadedNote.getTagIds().isEmpty());

            assertFalse(tagRepository.isUsedByAnyNote(childTag.getId()));
        }
    }

    @Test
    void fallsBackToDefaultPatternForLegacyIconPresetValue(@TempDir Path tempDir) throws java.sql.SQLException {
        // 旧「記号アイコン」機能で保存されたicon_preset値（例: "BOOK"）は、模様プリセットへの
        // 移行後はBookCover.Pattern.valueOf()で解決できない。実データを壊さないための
        // フォールバック（SqliteBookRepository.resolvePattern）が機能することを検証する。
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();

            try (var statement = database.getConnection().createStatement()) {
                statement.executeUpdate(
                    "INSERT INTO books (uuid, title, theme, font, icon_preset, icon_custom_path, created_at, updated_at) "
                        + "VALUES ('legacy-uuid', '旧データのブック', 'WHITE', 'MEIRYO_UI', 'BOOK', NULL, "
                        + "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
            }

            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            Book book = bookRepository.findByUuid("legacy-uuid").orElseThrow();

            assertFalse(book.getCover().isCustom());
            assertEquals(BookCover.Pattern.STRIPES, book.getCover().getPattern());
        }
    }

    @Test
    void persistsNoteAppearanceSettings(@TempDir Path tempDir) {
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();

            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            Book book = bookRepository.insert(new Book(0, null, insertShelf(database), "テストブック", BookTheme.GREEN,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.CHECKERS), Instant.now(), Instant.now()));

            SqliteNoteRepository noteRepository = new SqliteNoteRepository(database);
            Note note = new Note(0, null, book.getId(), "本文", Instant.now(), Instant.now());
            note.setBackgroundTheme(BookTheme.NAVY);
            note.setBackgroundImageFileName("12345_bg.png");
            note.setTextColor(NoteTextColor.MAROON);
            Note saved = noteRepository.insert(note);

            Note reloaded = noteRepository.findById(saved.getId()).orElseThrow();
            assertEquals(BookTheme.NAVY, reloaded.getBackgroundTheme());
            assertEquals("12345_bg.png", reloaded.getBackgroundImageFileName());
            assertEquals(NoteTextColor.MAROON, reloaded.getTextColor());
        }
    }

    @Test
    void defaultsNoteAppearanceSettingsForLegacyNullValues(@TempDir Path tempDir) throws java.sql.SQLException {
        // uuid同期機能追加より前のように、見た目カラムがNULLのままの既存ノートでも
        // 例外にならず、Noteのデフォルト値（白背景・画像なし・黒文字）にフォールバックすることを検証する。
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();

            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            Book book = bookRepository.insert(new Book(0, null, insertShelf(database), "テストブック", BookTheme.GREEN,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.CHECKERS), Instant.now(), Instant.now()));

            try (var statement = database.getConnection().createStatement()) {
                statement.executeUpdate(
                    "INSERT INTO notes (uuid, book_id, title, reading, body, created_at, updated_at) "
                        + "VALUES ('legacy-note-uuid', " + book.getId() + ", '猫', 'ねこ', '本文', "
                        + "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
            }

            SqliteNoteRepository noteRepository = new SqliteNoteRepository(database);
            Note reloaded = noteRepository.findByUuid(book.getId(), "legacy-note-uuid").orElseThrow();

            assertEquals(BookTheme.WHITE, reloaded.getBackgroundTheme());
            assertEquals(null, reloaded.getBackgroundImageFileName());
            assertEquals(NoteTextColor.BLACK, reloaded.getTextColor());
        }
    }

    @Test
    void assignsSortOrderOnInsertAndAppliesManualReorder(@TempDir Path tempDir) {
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();
            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            long shelfId = insertShelf(database);

            Book a = bookRepository.insert(new Book(0, null, shelfId, "A", BookTheme.GREEN, BookFont.MEIRYO_UI,
                BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));
            Book b = bookRepository.insert(new Book(0, null, shelfId, "B", BookTheme.GREEN, BookFont.MEIRYO_UI,
                BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));
            Book c = bookRepository.insert(new Book(0, null, shelfId, "C", BookTheme.GREEN, BookFont.MEIRYO_UI,
                BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));

            // 挿入順どおりに並んでいる
            assertEquals(List.of("A", "B", "C"),
                bookRepository.findAll().stream().map(Book::getTitle).toList());

            // Cを先頭へ移動
            bookRepository.updateManualOrder(List.of(c.getId(), a.getId(), b.getId()));
            assertEquals(List.of("C", "A", "B"),
                bookRepository.findAll().stream().map(Book::getTitle).toList());

            // 並び替え後に追加したブックは末尾に付く
            bookRepository.insert(new Book(0, null, shelfId, "D", BookTheme.GREEN, BookFont.MEIRYO_UI,
                BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));
            assertEquals(List.of("C", "A", "B", "D"),
                bookRepository.findAll().stream().map(Book::getTitle).toList());
        }
    }

    @Test
    void honeycombPatternRoundTrips(@TempDir Path tempDir) {
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();
            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            Book saved = bookRepository.insert(new Book(0, null, insertShelf(database), "ハニカム", BookTheme.YELLOW,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.HONEYCOMB), Instant.now(), Instant.now()));
            Book reloaded = bookRepository.findById(saved.getId()).orElseThrow();
            assertEquals(BookCover.Pattern.HONEYCOMB, reloaded.getCover().getPattern());
        }
    }

    @Test
    void seedsDefaultThreeColumnsWhenCreatingANewBook(@TempDir Path tempDir) {
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();
            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            Book book = bookRepository.insert(new Book(0, null, insertShelf(database), "新規ブック", BookTheme.GREEN,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));

            List<NoteColumn> columns = new SqliteNoteColumnRepository(database).findByBookId(book.getId());
            assertEquals(3, columns.size());
            assertEquals("項目名", columns.get(0).getName());
            assertTrue(columns.get(0).isRequired());
            assertEquals("読み", columns.get(1).getName());
            assertTrue(columns.get(1).isRequired());
            assertEquals("英訳", columns.get(2).getName());
            assertFalse(columns.get(2).isRequired());
        }
    }

    @Test
    void canAddRenameAndDeleteNoteColumns(@TempDir Path tempDir) {
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();
            SqliteBookRepository bookRepository = new SqliteBookRepository(database);
            Book book = bookRepository.insert(new Book(0, null, insertShelf(database), "ブック", BookTheme.GREEN,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));
            SqliteNoteColumnRepository columnRepository = new SqliteNoteColumnRepository(database);

            NoteColumn added = columnRepository.insert(new NoteColumn(0, null, book.getId(), "備考", false, 3, null));
            assertEquals(4, columnRepository.findByBookId(book.getId()).size());

            added.setName("メモ");
            added.setRequired(true);
            columnRepository.update(added);
            NoteColumn reloaded = columnRepository.findByBookId(book.getId()).get(3);
            assertEquals("メモ", reloaded.getName());
            assertTrue(reloaded.isRequired());

            columnRepository.delete(added.getId());
            assertEquals(3, columnRepository.findByBookId(book.getId()).size());
        }
    }

    @Test
    void migratesLegacyTitleReadingEnglishIntoNoteFieldValues(@TempDir Path tempDir) throws java.sql.SQLException {
        // カラム自由設定・シェルフ機能より前に作られたデータベース（notesのtitle/reading/
        // english_translation列に直接値が入っており、booksのshelf_idも未設定の状態）を開いたときに、
        // 既定の3カラムが自動生成されて既存ノートの値がnote_field_valuesへ正しく複製されること、
        // また既定のシェルフ「マイシェルフ」が自動生成されて既存ブックがそこへ割り当てられることを検証する。
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();

            // カラム自由設定の実装より前の状態を模した行を、bookRepository.insert()を使わず
            // 直接SQLで作る（insert()経由だと既定カラムが自動生成されてしまうため）。
            try (var statement = database.getConnection().createStatement()) {
                statement.executeUpdate(
                    "INSERT INTO books (id, uuid, title, theme, font, icon_preset, sort_order, created_at, updated_at) "
                        + "VALUES (100, 'legacy-book-uuid', '旧ブック', 'WHITE', 'MEIRYO_UI', 'STRIPES', 0, "
                        + "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
                statement.executeUpdate(
                    "INSERT INTO notes (uuid, book_id, title, reading, english_translation, body, "
                        + "created_at, updated_at) VALUES ('legacy-note-uuid', 100, '猫', 'ねこ', 'cat', '本文', "
                        + "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
            }

            // 新しい接続で開き直し、migrateExistingRows()を再度走らせる（実際の起動シーケンスと同じ）。
            database.initSchema();

            List<NoteColumn> columns = new SqliteNoteColumnRepository(database).findByBookId(100);
            assertEquals(3, columns.size());
            assertEquals("項目名", columns.get(0).getName());

            Note reloaded = new SqliteNoteRepository(database).findByUuid(100, "legacy-note-uuid").orElseThrow();
            assertEquals("猫", reloaded.getFieldValue(columns.get(0).getId()));
            assertEquals("ねこ", reloaded.getFieldValue(columns.get(1).getId()));
            assertEquals("cat", reloaded.getFieldValue(columns.get(2).getId()));

            Book migratedBook = new SqliteBookRepository(database).findById(100).orElseThrow();
            Shelf defaultShelf = new SqliteShelfRepository(database).findById(migratedBook.getShelfId())
                .orElseThrow();
            assertEquals("マイシェルフ", defaultShelf.getName());
        }
    }

    @Test
    void deduplicatesNoteColumnsLeftOverFromIndependentMigrationsOnBothPlatforms(@TempDir Path tempDir)
            throws java.sql.SQLException {
        // 実際に発生した不具合の再現: note_columns機能導入前から存在したブックについて、
        // デスクトップ・Android双方が旧バージョンのコード（既定カラムのuuidをランダムに生成していた
        // 頃のもの）でそれぞれ独立にmigrateNoteColumns()を実行し、その後に同期すると、
        // 同じ役割（項目名/読み/英訳）のカラムがuuid違いで重複して残ってしまっていた。
        // 現在のコードはこの状態を開き直した時点で自動的に統合することを検証する。
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();

            try (var statement = database.getConnection().createStatement()) {
                statement.executeUpdate(
                    "INSERT INTO books (id, uuid, title, theme, font, icon_preset, sort_order, "
                        + "created_at, updated_at) VALUES (200, 'dup-book-uuid', '重複ブック', 'WHITE', "
                        + "'MEIRYO_UI', 'STRIPES', 0, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");
                statement.executeUpdate(
                    "INSERT INTO notes (id, uuid, book_id, title, reading, body, created_at, updated_at) "
                        + "VALUES (300, 'dup-note-uuid', 200, '猫', 'ねこ', '本文', "
                        + "'2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')");

                // デスクトップ側で独立生成されたと想定するカラム（id若番）
                statement.executeUpdate(
                    "INSERT INTO note_columns (id, uuid, book_id, name, required, sort_order, updated_at) VALUES "
                        + "(1001, 'desktop-title', 200, '項目名', 1, 0, '2026-01-01T00:00:00Z'),"
                        + "(1002, 'desktop-reading', 200, '読み', 1, 1, '2026-01-01T00:00:00Z'),"
                        + "(1003, 'desktop-english', 200, '英訳', 0, 2, '2026-01-01T00:00:00Z')");
                // Android側で独立生成されたと想定するカラム（別uuid、同じ名前・sort_order）
                statement.executeUpdate(
                    "INSERT INTO note_columns (id, uuid, book_id, name, required, sort_order, updated_at) VALUES "
                        + "(2001, 'android-title', 200, '項目名', 1, 0, '2026-01-01T00:00:00Z'),"
                        + "(2002, 'android-reading', 200, '読み', 1, 1, '2026-01-01T00:00:00Z'),"
                        + "(2003, 'android-english', 200, '英訳', 0, 2, '2026-01-01T00:00:00Z')");

                // デスクトップ側カラムに項目名・読みの値が入っており、英訳だけAndroid側カラムに
                // 値が入っている状態を再現する（統合後、英訳の値が生き残ることを検証するため）。
                statement.executeUpdate(
                    "INSERT INTO note_field_values (note_id, column_id, value) VALUES "
                        + "(300, 1001, '猫'), (300, 1002, 'ねこ'), (300, 2003, 'cat')");
            }

            // 新しい接続で開き直し、deduplicateNoteColumns()を含むmigrateExistingRows()を再度走らせる。
            database.initSchema();

            List<NoteColumn> columns = new SqliteNoteColumnRepository(database).findByBookId(200);
            assertEquals(3, columns.size());
            assertEquals("項目名", columns.get(0).getName());
            assertTrue(columns.get(0).isRequired());
            assertEquals("読み", columns.get(1).getName());
            assertTrue(columns.get(1).isRequired());
            assertEquals("英訳", columns.get(2).getName());
            assertFalse(columns.get(2).isRequired());

            Note note = new SqliteNoteRepository(database).findByUuid(200, "dup-note-uuid").orElseThrow();
            assertEquals("猫", note.getFieldValue(columns.get(0).getId()));
            assertEquals("ねこ", note.getFieldValue(columns.get(1).getId()));
            assertEquals("cat", note.getFieldValue(columns.get(2).getId()));
        }
    }

    @Test
    void booksAlwaysBelongToExactlyOneShelfAndCanBeMovedBetweenShelves(@TempDir Path tempDir) {
        try (SqliteDatabase database = new SqliteDatabase(tempDir.resolve("test.db"))) {
            database.initSchema();
            SqliteShelfRepository shelfRepository = new SqliteShelfRepository(database);
            SqliteBookRepository bookRepository = new SqliteBookRepository(database);

            Shelf shelfA = shelfRepository.insert(new Shelf(0, null, "シェルフA",
                BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));
            Shelf shelfB = shelfRepository.insert(new Shelf(0, null, "シェルフB",
                BookCover.ofPattern(BookCover.Pattern.STRIPES), Instant.now(), Instant.now()));

            Book book = bookRepository.insert(new Book(0, null, shelfA.getId(), "移動するブック", BookTheme.GREEN,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));

            assertEquals(1, bookRepository.findByShelfId(shelfA.getId()).size());
            assertEquals(0, bookRepository.findByShelfId(shelfB.getId()).size());

            book.setShelfId(shelfB.getId());
            bookRepository.update(book);

            assertEquals(0, bookRepository.findByShelfId(shelfA.getId()).size());
            assertEquals(1, bookRepository.findByShelfId(shelfB.getId()).size());
            assertEquals(shelfB.getId(), bookRepository.findById(book.getId()).orElseThrow().getShelfId());
        }
    }
}
