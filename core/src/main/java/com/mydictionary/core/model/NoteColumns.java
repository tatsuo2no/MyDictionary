package com.mydictionary.core.model;

import java.util.List;

/**
 * {@link NoteColumn}のリストとノートのカラム値に関する共通処理。
 * デスクトップ版・Android版の両方から使う（見た目は各画面で作るが、
 * 「どのカラムが主キーか」というルールの解釈はここに一本化する）。
 */
public final class NoteColumns {

    private NoteColumns() {
    }

    /**
     * ブックの主キーのカラムを返す。主キーに設定されたカラムのうち並び順が最小のもの。
     * 一度も設定されていない場合は並び順の先頭のカラム（以前の「先頭カラムが必須」仕様との互換）。
     * 端末ごとの設定変更が同期で食い違い、複数のカラムに印が付いてしまっても、必ず1つに決まる。
     * columnsはsortOrder順（NoteColumnRepository#findByBookIdの戻り値）であること。
     * ブックには常に最低1カラムが存在する前提。
     */
    public static NoteColumn primary(List<NoteColumn> columns) {
        if (columns.isEmpty()) {
            throw new IllegalStateException("ブックにカラムが1つもありません");
        }
        for (NoteColumn column : columns) {
            if (column.isPrimaryKey()) {
                return column;
            }
        }
        return columns.get(0);
    }

    /** このカラムが、ブックの主キーとして扱われるか。 */
    public static boolean isPrimary(NoteColumn column, List<NoteColumn> columns) {
        return primary(columns).getId() == column.getId();
    }

    /** ノート一覧の見出し・ノート内リンクの既定表示に使う、主キーのカラムの値。 */
    public static String primaryValue(Note note, List<NoteColumn> columns) {
        return note.getFieldValues().getOrDefault(primary(columns).getId(), "");
    }
}
