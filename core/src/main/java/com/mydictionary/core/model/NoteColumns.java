package com.mydictionary.core.model;

import java.util.List;

/**
 * {@link NoteColumn}のリストとノートのカラム値に関する共通処理。
 * デスクトップ版・Android版の両方から使う（見た目は各画面で作るが、
 * 「先頭カラムが必須カラム」というルールの解釈はここに一本化する）。
 */
public final class NoteColumns {

    private NoteColumns() {
    }

    /**
     * 必須カラム（常に先頭・削除不可）を返す。ブックには常に最低1カラムが存在する前提。
     */
    public static NoteColumn primary(List<NoteColumn> columns) {
        if (columns.isEmpty()) {
            throw new IllegalStateException("ブックにカラムが1つもありません");
        }
        return columns.get(0);
    }

    /** ノート一覧の見出し・ノート内リンクの既定表示に使う、必須カラムの値。 */
    public static String primaryValue(Note note, List<NoteColumn> columns) {
        return note.getFieldValues().getOrDefault(primary(columns).getId(), "");
    }
}
