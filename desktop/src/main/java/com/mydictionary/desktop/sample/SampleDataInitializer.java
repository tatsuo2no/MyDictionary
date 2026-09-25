package com.mydictionary.desktop.sample;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.model.Tag;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteNoteColumnRepository;
import com.mydictionary.desktop.db.SqliteNoteRepository;
import com.mydictionary.desktop.db.SqliteShelfRepository;
import com.mydictionary.desktop.db.SqliteTagRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 初回起動時に、仕様書に記載のとおりサンプルのブック・タグ・ノートを1件ずつ用意する。 */
public final class SampleDataInitializer {

    private SampleDataInitializer() {
    }

    public static void ensureSampleData(SqliteDatabase database) {
        SqliteBookRepository bookRepository = new SqliteBookRepository(database);
        if (!bookRepository.findAll().isEmpty()) {
            return;
        }

        Shelf sampleShelf = new SqliteShelfRepository(database).insert(new Shelf(0, null, "マイシェルフ",
            BookCover.ofPattern(BookCover.Pattern.STRIPES), Instant.now(), Instant.now()));

        Book sampleBook = bookRepository.insert(new Book(0, null, sampleShelf.getId(), "サンプルブック",
            BookTheme.LIGHT_BLUE, BookFont.MEIRYO_UI,
            BookCover.ofPattern(BookCover.Pattern.STRIPES), Instant.now(), Instant.now()));

        // bookRepository.insert()が既定の3カラム（項目名=必須, 読み=必須, 英訳=任意）を
        // 自動的に用意しているので、それを取得してサンプルノートの値を割り当てる。
        List<NoteColumn> columns = new SqliteNoteColumnRepository(database).findByBookId(sampleBook.getId());
        long titleColumnId = columns.get(0).getId();
        long readingColumnId = columns.get(1).getId();
        long englishColumnId = columns.get(2).getId();

        SqliteTagRepository tagRepository = new SqliteTagRepository(database);
        Tag animal = tagRepository.insert(new Tag(0, null, sampleBook.getId(), "動物", null, Instant.now()));
        Tag mammal = tagRepository.insert(new Tag(0, null, sampleBook.getId(), "哺乳類", animal.getId(), Instant.now()));
        Tag plant = tagRepository.insert(new Tag(0, null, sampleBook.getId(), "植物", null, Instant.now()));

        SqliteNoteRepository noteRepository = new SqliteNoteRepository(database);

        Note dog = new Note(0, null, sampleBook.getId(),
            "## 概要\n犬は古くから人と共に暮らしてきた哺乳類です。\n\n> 忠実な相棒として知られています。\n\n"
                + "- 嗅覚が優れている\n- 聴覚が優れている\n",
            Instant.now(), Instant.now());
        dog.setFieldValues(fieldValues(titleColumnId, "犬", readingColumnId, "いぬ", englishColumnId, "dog"));
        dog.setTagIds(List.of(mammal.getId()));
        noteRepository.insert(dog);

        Note sakura = new Note(0, null, sampleBook.getId(),
            "## 概要\n桜は日本を代表する花木です。\n\n1. 春に開花する\n2. 花見の風習がある\n",
            Instant.now(), Instant.now());
        sakura.setFieldValues(fieldValues(titleColumnId, "桜", readingColumnId, "さくら", englishColumnId, "cherry blossom"));
        sakura.setTagIds(List.of(plant.getId()));
        noteRepository.insert(sakura);
    }

    private static Map<Long, String> fieldValues(long titleColumnId, String title,
                                                   long readingColumnId, String reading,
                                                   long englishColumnId, String english) {
        Map<Long, String> values = new LinkedHashMap<>();
        values.put(titleColumnId, title);
        values.put(readingColumnId, reading);
        values.put(englishColumnId, english);
        return values;
    }
}
