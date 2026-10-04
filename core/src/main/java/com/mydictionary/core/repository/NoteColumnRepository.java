package com.mydictionary.core.repository;

import com.mydictionary.core.model.NoteColumn;

import java.time.Instant;
import java.util.List;

/**
 * ブックごとのノートカラム定義（{@link NoteColumn}）を扱うリポジトリ。
 * Tagと同じく、ブックに属する子エンティティとして扱う。
 */
public interface NoteColumnRepository {
    /** sortOrder順で返す。 */
    List<NoteColumn> findByBookId(long bookId);

    NoteColumn insert(NoteColumn column);

    void update(NoteColumn column);

    void delete(long id);

    /** この項目に、空でない値が入力されているノートの数（削除の確認で「失われる件数」を示すために使う）。 */
    int countNonEmptyValues(long columnId);

    /**
     * 指定したカラムをブックの主キーにする。同じブックの他のカラムの主キーの印を外し、
     * 新しい主キーのカラムは必須にする（主キーは常に必須のため）。実際に値が変わったカラムだけ
     * updated_atを更新するので、デバイス間同期でこの変更が反映される。
     */
    void setPrimary(long bookId, long columnId);

    /**
     * ブック作成時に呼び、既定の3カラム（項目名=必須・主キー, 読み=必須, 英訳=任意）を作成する。
     * 新規ブック・および列未移行の既存ブックの両方から使う共通の初期化処理。
     */
    void seedDefaultColumns(long bookId);

    /**
     * デバイス間同期用。uuidが一致するカラムが既にあれば与えられた内容で更新し、
     * なければ与えられたuuid・更新日時をそのまま使って新規作成する。
     */
    NoteColumn upsertFromSync(long bookId, String uuid, String name, boolean required, int sortOrder,
                               boolean primaryKey, Instant updatedAt);
}
