package com.mydictionary.core.repository;

import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteTextColor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface NoteRepository {
    /**
     * ブック内の全ノート。並び順は特に保証しない（呼び出し側が{@link com.mydictionary.core.model.NoteColumn}
     * の値をもとに並び替える。並び替え自体はUI層の責務）。
     */
    List<Note> findByBookId(long bookId);

    Optional<Note> findById(long id);

    Optional<Note> findByUuid(long bookId, String uuid);

    /** 全カラムの値・本文のいずれかにキーワードが部分一致するノートを返す。 */
    List<Note> search(long bookId, String keyword);

    List<Note> findByTag(long bookId, long tagId);

    Note insert(Note note);

    void update(Note note);

    void delete(long id);

    /**
     * デバイス間同期用。uuidが一致するノートが既にあれば与えられた内容で更新し、
     * なければ与えられたuuid・作成日時・更新日時をそのまま使って新規作成する。
     * tagIds・fieldValuesのキーは、いずれもこのリポジトリ（同期先）でのローカルなID
     * （タグID・カラムID）を指定すること。
     */
    Note upsertFromSync(long bookId, String uuid, Map<Long, String> fieldValues, String body, List<Long> tagIds,
                         BookTheme backgroundTheme, String backgroundImageFileName, NoteTextColor textColor,
                         Instant createdAt, Instant updatedAt);
}
