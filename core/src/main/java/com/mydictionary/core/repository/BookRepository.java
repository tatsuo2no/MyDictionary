package com.mydictionary.core.repository;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookTheme;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookRepository {
    List<Book> findAll();

    Optional<Book> findById(long id);

    Optional<Book> findByUuid(String uuid);

    /** 指定したシェルフに属するブックの一覧（表示順=sort_order, idの昇順）。 */
    List<Book> findByShelfId(long shelfId);

    Book insert(Book book);

    void update(Book book);

    void delete(long id);

    /**
     * 手動並び替え用。与えられたブックIDの順番どおりに sort_order を 0,1,2,... と振り直す。
     * sort_order は端末ローカルの表示順であり、デバイス間同期の対象外（updated_atも更新しない）。
     * 呼び出し側は同一シェルフに属するブックIDのみを渡すこと。
     */
    void updateManualOrder(List<Long> orderedBookIds);

    /**
     * デバイス間同期用。uuidが一致するブックが既にあれば与えられた内容で更新し、
     * なければ与えられたuuid・作成日時・更新日時をそのまま使って新規作成する
     * （通常のinsert()と違い、uuidや日時を自動生成し直さない）。shelfIdはこの端末での
     * ローカルなシェルフID（呼び出し側がシェルフのuuid解決を済ませてから渡す）。
     */
    Book upsertFromSync(String uuid, long shelfId, String title, BookTheme theme, BookFont font, int fontSizePt,
                         BookCover cover, Instant createdAt, Instant updatedAt);
}
