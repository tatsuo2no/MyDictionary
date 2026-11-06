package com.mydictionary.desktop.db;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.model.Tag;
import com.mydictionary.core.repository.DeletionLog;
import com.mydictionary.core.sync.DatabaseMerger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 片方の端末で削除したもの（項目・タグ・ノート・ブック）が、同期でもう片方からも削除され、
 * その後の同期で復活しないことを、2つの独立したSQLiteデータベースで検証する。
 */
class DeletionSyncIntegrationTest {
    @TempDir
    Path tempDir;

    private Side a;
    private Side b;

    /** 片方の端末のデータベースと、そのリポジトリ一式。 */
    private static final class Side implements AutoCloseable {
        final SqliteDatabase db;
        final SqliteShelfRepository shelves;
        final SqliteBookRepository books;
        final SqliteTagRepository tags;
        final SqliteNoteColumnRepository columns;
        final SqliteNoteRepository notes;
        final SqliteDeletionLog deletions;

        Side(Path file) {
            db = new SqliteDatabase(file);
            db.initSchema();
            shelves = new SqliteShelfRepository(db);
            books = new SqliteBookRepository(db);
            tags = new SqliteTagRepository(db);
            columns = new SqliteNoteColumnRepository(db);
            notes = new SqliteNoteRepository(db);
            deletions = new SqliteDeletionLog(db);
        }

        @Override
        public void close() {
            db.close();
        }
    }

    @BeforeEach
    void openDatabases() {
        a = new Side(tempDir.resolve("a.db"));
        b = new Side(tempDir.resolve("b.db"));
    }

    @AfterEach
    void closeDatabases() {
        a.close();
        b.close();
    }

    private void sync() {
        DatabaseMerger.merge(a.shelves, a.books, a.tags, a.columns, a.notes, a.deletions,
            b.shelves, b.books, b.tags, b.columns, b.notes, b.deletions);
    }

    /** 時刻の分解能を超えて、確実に「後」になるように少し待つ。 */
    private static void later() throws InterruptedException {
        Thread.sleep(30);
    }

    /** A側に「項目名」「読み」「英訳」の3項目を持つブックを作り、両側へ同期した状態にする。 */
    private Book createSyncedBook() {
        Shelf shelf = a.shelves.insert(new Shelf(0, null, "棚", BookCover.ofPattern(BookCover.Pattern.WAVES),
            Instant.now(), Instant.now()));
        Book book = a.books.insert(new Book(0, null, shelf.getId(), "辞書", BookTheme.GREEN, BookFont.MEIRYO_UI,
            BookCover.ofPattern(BookCover.Pattern.WAVES), Instant.now(), Instant.now()));
        a.columns.insert(new NoteColumn(0, null, book.getId(), "読み", true, 1, Instant.now()));
        a.columns.insert(new NoteColumn(0, null, book.getId(), "英訳", false, 2, Instant.now()));
        sync();
        return book;
    }

    private long bookIdIn(Side side, Book book) {
        return side.books.findAll().stream().filter(x -> x.getUuid().equals(book.getUuid()))
            .findFirst().orElseThrow().getId();
    }

    private static NoteColumn column(List<NoteColumn> columns, String name) {
        return columns.stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void columnDeletedOnOneSideIsDeletedOnTheOtherAndStaysDeleted() throws Exception {
        Book book = createSyncedBook();
        assertEquals(3, b.columns.findByBookId(bookIdIn(b, book)).size());

        later();
        a.columns.delete(column(a.columns.findByBookId(book.getId()), "英訳").getId());
        sync();

        assertEquals(List.of("項目名", "読み"),
            b.columns.findByBookId(bookIdIn(b, book)).stream().map(NoteColumn::getName).toList());
        // 何度同期しても復活しない
        sync();
        sync();
        assertEquals(2, a.columns.findByBookId(book.getId()).size());
        assertEquals(2, b.columns.findByBookId(bookIdIn(b, book)).size());

        // 逆向き（B側で削除してA側へ）
        later();
        b.columns.delete(column(b.columns.findByBookId(bookIdIn(b, book)), "読み").getId());
        sync();
        assertEquals(List.of("項目名"),
            a.columns.findByBookId(book.getId()).stream().map(NoteColumn::getName).toList());
    }

    @Test
    void deletionIsCancelledWhenTheOtherSideEditedTheItemAfterwards() throws Exception {
        Book book = createSyncedBook();

        later();
        a.columns.delete(column(a.columns.findByBookId(book.getId()), "英訳").getId());
        later();
        NoteColumn edited = column(b.columns.findByBookId(bookIdIn(b, book)), "英訳");
        edited.setName("英語");
        b.columns.update(edited);
        sync();

        assertEquals("英語", column(a.columns.findByBookId(book.getId()), "英語").getName());
        assertEquals("英語", column(b.columns.findByBookId(bookIdIn(b, book)), "英語").getName());
        sync();
        assertEquals(3, a.columns.findByBookId(book.getId()).size());
    }

    @Test
    void tagWithChildrenAndNoteDeletedOnOneSideAreDeletedOnTheOther() throws Exception {
        Book book = createSyncedBook();
        Tag parent = a.tags.insert(new Tag(0, null, book.getId(), "国", null, Instant.now()));
        a.tags.insert(new Tag(0, null, book.getId(), "日本", parent.getId(), Instant.now()));
        Tag other = a.tags.insert(new Tag(0, null, book.getId(), "地名", null, Instant.now()));
        long titleColumnId = a.columns.findByBookId(book.getId()).get(0).getId();
        Note note = new Note(0, null, book.getId(), "本文", Instant.now(), Instant.now());
        note.setFieldValues(Map.of(titleColumnId, "京都"));
        note.setTagIds(List.of(other.getId()));
        Note saved = a.notes.insert(note);
        sync();
        long bookIdInB = bookIdIn(b, book);
        assertEquals(3, b.tags.findByBookId(bookIdInB).size());
        assertEquals(1, b.notes.findByBookId(bookIdInB).size());

        later();
        a.tags.delete(parent.getId());
        a.notes.delete(saved.getId());
        sync();
        sync();

        assertEquals(List.of("地名"), b.tags.findByBookId(bookIdInB).stream().map(Tag::getName).toList());
        assertEquals(List.of("地名"), a.tags.findByBookId(book.getId()).stream().map(Tag::getName).toList());
        assertTrue(b.notes.findByBookId(bookIdInB).isEmpty());
    }

    @Test
    void bookDeletedOnOneSideIsDeletedOnTheOtherButTheShelfStays() throws Exception {
        Book book = createSyncedBook();

        later();
        b.books.delete(bookIdIn(b, book));
        sync();
        sync();

        assertTrue(a.books.findAll().isEmpty());
        assertTrue(b.books.findAll().isEmpty());
        assertEquals(1, a.shelves.findAll().size());
        assertEquals(1, b.shelves.findAll().size());
    }

    @Test
    void shelfDeletedOnOneSideIsDeletedOnTheOther() throws Exception {
        Book book = createSyncedBook();
        later();
        a.books.delete(book.getId());
        a.shelves.delete(a.shelves.findAll().get(0).getId());
        sync();
        sync();

        assertTrue(a.shelves.findAll().isEmpty());
        assertTrue(b.shelves.findAll().isEmpty());
        assertTrue(b.books.findAll().isEmpty());
    }

    @Test
    void withoutDeletionLogsDeletedItemsStillComeBackAsBefore() throws Exception {
        Book book = createSyncedBook();
        later();
        a.columns.delete(column(a.columns.findByBookId(book.getId()), "英訳").getId());

        DatabaseMerger.merge(a.shelves, a.books, a.tags, a.columns, a.notes,
            b.shelves, b.books, b.tags, b.columns, b.notes);

        assertEquals(3, a.columns.findByBookId(book.getId()).size());
    }

    @Test
    void deletingARowRecordsItInTheLog() throws Exception {
        Book book = createSyncedBook();
        NoteColumn english = column(a.columns.findByBookId(book.getId()), "英訳");
        a.columns.delete(english.getId());

        assertTrue(a.deletions.findAll(DeletionLog.COLUMN).containsKey(english.getUuid()));
        // ブックの削除では、連鎖して消える項目の記録も残る
        a.books.delete(book.getId());
        assertTrue(a.deletions.findAll(DeletionLog.BOOK).containsKey(book.getUuid()));
        assertTrue(a.deletions.findAll(DeletionLog.COLUMN).size() >= 3);
    }
}
