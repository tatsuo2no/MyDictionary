package com.mydictionary.core.repository;

import com.mydictionary.core.model.Tag;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TagRepository {
    List<Tag> findByBookId(long bookId);

    Optional<Tag> findById(long id);

    Optional<Tag> findByUuid(long bookId, String uuid);

    Tag insert(Tag tag);

    void update(Tag tag);

    void delete(long id);

    boolean isUsedByAnyNote(long tagId);

    /**
     * デバイス間同期用。uuidが一致するタグが既にあれば与えられた内容で更新し、
     * なければ与えられたuuid・更新日時をそのまま使って新規作成する。
     * parentTagIdはこのリポジトリ（同期先）でのローカルなタグIDを指定すること
     * （呼び出し側が親タグを先にupsertFromSyncしてIDを解決しておく）。
     */
    Tag upsertFromSync(long bookId, String uuid, String name, Long parentTagId, Instant updatedAt);
}
