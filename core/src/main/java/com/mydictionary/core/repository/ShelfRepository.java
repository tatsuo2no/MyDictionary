package com.mydictionary.core.repository;

import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShelfRepository {
    List<Shelf> findAll();

    Optional<Shelf> findById(long id);

    Optional<Shelf> findByUuid(String uuid);

    Shelf insert(Shelf shelf);

    void update(Shelf shelf);

    void delete(long id);

    /**
     * 手動並び替え用。与えられたシェルフIDの順番どおりに sort_order を 0,1,2,... と振り直す。
     * sort_order は端末ローカルの表示順であり、デバイス間同期の対象外（updated_atも更新しない）。
     */
    void updateManualOrder(List<Long> orderedShelfIds);

    /**
     * デバイス間同期用。uuidが一致するシェルフが既にあれば与えられた内容で更新し、
     * なければ与えられたuuid・作成日時・更新日時をそのまま使って新規作成する。
     */
    Shelf upsertFromSync(String uuid, String name, BookCover cover, Instant createdAt, Instant updatedAt);
}
