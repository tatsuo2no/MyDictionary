package com.mydictionary.desktop.db;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.model.Tag;
import com.mydictionary.core.sync.DatabaseMerger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Android版の「今すぐ同期」が使うDatabaseMergerを、実際に2つの独立したSQLiteデータベース
 * （それぞれローカルIDの採番系列が異なる）を使って検証する。
 */
class DatabaseMergerIntegrationTest {

    @Test
    void mergesNewShelfBookNoteAndTagFromOtherSideInBothDirections(@TempDir Path tempDir) {
        try (SqliteDatabase dbA = new SqliteDatabase(tempDir.resolve("a.db"));
             SqliteDatabase dbB = new SqliteDatabase(tempDir.resolve("b.db"))) {
            dbA.initSchema();
            dbB.initSchema();

            SqliteShelfRepository shelvesA = new SqliteShelfRepository(dbA);
            SqliteBookRepository booksA = new SqliteBookRepository(dbA);
            SqliteTagRepository tagsA = new SqliteTagRepository(dbA);
            SqliteNoteColumnRepository columnsA = new SqliteNoteColumnRepository(dbA);
            SqliteNoteRepository notesA = new SqliteNoteRepository(dbA);

            SqliteShelfRepository shelvesB = new SqliteShelfRepository(dbB);
            SqliteBookRepository booksB = new SqliteBookRepository(dbB);
            SqliteTagRepository tagsB = new SqliteTagRepository(dbB);
            SqliteNoteColumnRepository columnsB = new SqliteNoteColumnRepository(dbB);
            SqliteNoteRepository notesB = new SqliteNoteRepository(dbB);

            // A側だけにシェルフ・ブック・入れ子タグ・ノートを作成する
            Shelf shelf = shelvesA.insert(new Shelf(0, null, "旅行", BookCover.ofPattern(BookCover.Pattern.WAVES),
                Instant.now(), Instant.now()));
            Book book = booksA.insert(new Book(0, null, shelf.getId(), "旅行メモ", BookTheme.GREEN, BookFont.MEIRYO_UI,
                BookCover.ofPattern(BookCover.Pattern.WAVES), Instant.now(), Instant.now()));
            Tag country = tagsA.insert(new Tag(0, null, book.getId(), "国", null, Instant.now()));
            Tag japan = tagsA.insert(new Tag(0, null, book.getId(), "日本", country.getId(), Instant.now()));

            List<NoteColumn> columnsInA = columnsA.findByBookId(book.getId());
            long titleColumnIdA = columnsInA.get(0).getId();

            Note note = new Note(0, null, book.getId(), "# 京都旅行\n楽しかった", Instant.now(), Instant.now());
            note.setFieldValues(Map.of(titleColumnIdA, "京都", columnsInA.get(1).getId(), "きょうと",
                columnsInA.get(2).getId(), "Kyoto"));
            note.setTagIds(List.of(japan.getId()));
            notesA.insert(note);

            // B側は空の状態でマージ
            DatabaseMerger.merge(shelvesA, booksA, tagsA, columnsA, notesA,
                shelvesB, booksB, tagsB, columnsB, notesB);

            // B側にシェルフ・ブック・タグ階層・カラム定義・ノート・タグ紐付けが複製されていること
            List<Shelf> shelvesInB = shelvesB.findAll();
            assertEquals(1, shelvesInB.size());
            Shelf shelfInB = shelvesInB.get(0);
            assertEquals("旅行", shelfInB.getName());
            assertEquals(shelf.getUuid(), shelfInB.getUuid());

            List<Book> booksInB = booksB.findAll();
            assertEquals(1, booksInB.size());
            Book bookInB = booksInB.get(0);
            assertEquals("旅行メモ", bookInB.getTitle());
            assertEquals(book.getUuid(), bookInB.getUuid());
            assertEquals(shelfInB.getId(), bookInB.getShelfId());

            List<Tag> tagsInB = tagsB.findByBookId(bookInB.getId());
            assertEquals(2, tagsInB.size());
            Tag japanInB = tagsInB.stream().filter(t -> t.getUuid().equals(japan.getUuid())).findFirst().orElseThrow();
            Tag countryInB = tagsInB.stream().filter(t -> t.getUuid().equals(country.getUuid())).findFirst().orElseThrow();
            assertEquals(countryInB.getId(), japanInB.getParentTagId());

            List<NoteColumn> columnsInB = columnsB.findByBookId(bookInB.getId());
            assertEquals(3, columnsInB.size());
            long titleColumnIdB = columnsInB.get(0).getId();

            List<Note> notesInBook = notesB.findByBookId(bookInB.getId());
            assertEquals(1, notesInBook.size());
            Note noteInB = notesInBook.get(0);
            assertEquals("京都", noteInB.getFieldValue(titleColumnIdB));
            assertEquals(List.of(japanInB.getId()), noteInB.getTagIds());
        }
    }

    @Test
    void newerEditWinsWhenBothSidesHaveTheSameNote(@TempDir Path tempDir) {
        try (SqliteDatabase dbA = new SqliteDatabase(tempDir.resolve("a.db"));
             SqliteDatabase dbB = new SqliteDatabase(tempDir.resolve("b.db"))) {
            dbA.initSchema();
            dbB.initSchema();

            SqliteShelfRepository shelvesA = new SqliteShelfRepository(dbA);
            SqliteBookRepository booksA = new SqliteBookRepository(dbA);
            SqliteTagRepository tagsA = new SqliteTagRepository(dbA);
            SqliteNoteColumnRepository columnsA = new SqliteNoteColumnRepository(dbA);
            SqliteNoteRepository notesA = new SqliteNoteRepository(dbA);

            SqliteShelfRepository shelvesB = new SqliteShelfRepository(dbB);
            SqliteBookRepository booksB = new SqliteBookRepository(dbB);
            SqliteTagRepository tagsB = new SqliteTagRepository(dbB);
            SqliteNoteColumnRepository columnsB = new SqliteNoteColumnRepository(dbB);
            SqliteNoteRepository notesB = new SqliteNoteRepository(dbB);

            Shelf shelfA = shelvesA.insert(new Shelf(0, null, "共有シェルフ",
                BookCover.ofPattern(BookCover.Pattern.STRIPES), Instant.now(), Instant.now()));
            Book bookA = booksA.insert(new Book(0, null, shelfA.getId(), "共有ブック", BookTheme.WHITE, BookFont.MEIRYO_UI,
                BookCover.ofPattern(BookCover.Pattern.STRIPES), Instant.now(), Instant.now()));
            List<NoteColumn> columnsInA = columnsA.findByBookId(bookA.getId());
            Note noteA = new Note(0, null, bookA.getId(), "古い本文",
                Instant.now(), Instant.now().minus(1, ChronoUnit.HOURS));
            noteA.setFieldValues(Map.of(columnsInA.get(0).getId(), "単語", columnsInA.get(1).getId(), "たんご"));
            notesA.insert(noteA);

            // 初回同期でB側にも複製する
            DatabaseMerger.merge(shelvesA, booksA, tagsA, columnsA, notesA,
                shelvesB, booksB, tagsB, columnsB, notesB);

            Book bookB = booksB.findAll().get(0);
            Note noteBBeforeEdit = notesB.findByBookId(bookB.getId()).get(0);

            // B側で本文を新しい日時で更新する（B側の変更のほうが新しい）
            noteBBeforeEdit.setBody("新しい本文");
            noteBBeforeEdit.setUpdatedAt(Instant.now().plus(1, ChronoUnit.HOURS));
            notesB.update(noteBBeforeEdit);

            // 再度マージすると、新しい方（B側）の内容がA側にも反映されるはず
            DatabaseMerger.merge(shelvesA, booksA, tagsA, columnsA, notesA,
                shelvesB, booksB, tagsB, columnsB, notesB);

            Note noteAAfterMerge = notesA.findByBookId(bookA.getId()).get(0);
            assertEquals("新しい本文", noteAAfterMerge.getBody());

            Note noteBAfterMerge = notesB.findByBookId(bookB.getId()).get(0);
            assertEquals("新しい本文", noteBAfterMerge.getBody());
            assertTrue(noteAAfterMerge.getUpdatedAt().equals(noteBAfterMerge.getUpdatedAt()));
        }
    }

    @Test
    void movingABookToAnotherShelfSyncsAcrossSides(@TempDir Path tempDir) {
        try (SqliteDatabase dbA = new SqliteDatabase(tempDir.resolve("a.db"));
             SqliteDatabase dbB = new SqliteDatabase(tempDir.resolve("b.db"))) {
            dbA.initSchema();
            dbB.initSchema();

            SqliteShelfRepository shelvesA = new SqliteShelfRepository(dbA);
            SqliteBookRepository booksA = new SqliteBookRepository(dbA);
            SqliteTagRepository tagsA = new SqliteTagRepository(dbA);
            SqliteNoteColumnRepository columnsA = new SqliteNoteColumnRepository(dbA);
            SqliteNoteRepository notesA = new SqliteNoteRepository(dbA);

            SqliteShelfRepository shelvesB = new SqliteShelfRepository(dbB);
            SqliteBookRepository booksB = new SqliteBookRepository(dbB);
            SqliteTagRepository tagsB = new SqliteTagRepository(dbB);
            SqliteNoteColumnRepository columnsB = new SqliteNoteColumnRepository(dbB);
            SqliteNoteRepository notesB = new SqliteNoteRepository(dbB);

            // A側に2つのシェルフとブック1冊(シェルフ1に所属)を作り、初回同期でB側にも複製する。
            Shelf shelf1A = shelvesA.insert(new Shelf(0, null, "シェルフ1",
                BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));
            Shelf shelf2A = shelvesA.insert(new Shelf(0, null, "シェルフ2",
                BookCover.ofPattern(BookCover.Pattern.STRIPES), Instant.now(), Instant.now()));
            Book bookA = booksA.insert(new Book(0, null, shelf1A.getId(), "移動するブック", BookTheme.GREEN,
                BookFont.MEIRYO_UI, BookCover.ofPattern(BookCover.Pattern.PLAIN), Instant.now(), Instant.now()));

            DatabaseMerger.merge(shelvesA, booksA, tagsA, columnsA, notesA,
                shelvesB, booksB, tagsB, columnsB, notesB);

            Shelf shelf2B = shelvesB.findAll().stream()
                .filter(s -> s.getUuid().equals(shelf2A.getUuid())).findFirst().orElseThrow();

            // A側でブックをシェルフ2へ移動してから再同期する。
            bookA.setShelfId(shelf2A.getId());
            bookA.setUpdatedAt(Instant.now().plus(1, ChronoUnit.HOURS));
            booksA.update(bookA);

            DatabaseMerger.merge(shelvesA, booksA, tagsA, columnsA, notesA,
                shelvesB, booksB, tagsB, columnsB, notesB);

            Book bookB = booksB.findByUuid(bookA.getUuid()).orElseThrow();
            assertEquals(shelf2B.getId(), bookB.getShelfId());
        }
    }
}
