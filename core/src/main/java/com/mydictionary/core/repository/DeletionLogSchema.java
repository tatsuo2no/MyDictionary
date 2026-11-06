package com.mydictionary.core.repository;

import java.util.ArrayList;
import java.util.List;

/**
 * 削除の記録（{@link DeletionLog}）用のテーブルとトリガーを作るSQL。デスクトップ版・Android版で共通。
 * トリガーは行の削除（外部キーの連鎖削除を含む）のたびに、そのuuidと削除した日時を{@code deletions}へ残す。
 * 日時は{@link java.time.Instant#toString()}と同じ形式（ISO-8601のUTC）で記録する。
 */
public final class DeletionLogSchema {
    private static final String[][] TRIGGERS = {
        {"shelves", DeletionLog.SHELF},
        {"books", DeletionLog.BOOK},
        {"tags", DeletionLog.TAG},
        {"note_columns", DeletionLog.COLUMN},
        {"notes", DeletionLog.NOTE},
    };

    private DeletionLogSchema() {
    }

    /** どれも{@code IF NOT EXISTS}なので、何度実行してもよい。1文ずつ実行すること。 */
    public static List<String> statements() {
        List<String> statements = new ArrayList<>();
        statements.add("CREATE TABLE IF NOT EXISTS deletions ("
            + "kind TEXT NOT NULL,"
            + "uuid TEXT NOT NULL,"
            + "deleted_at TEXT NOT NULL,"
            + "PRIMARY KEY (kind, uuid))");
        for (String[] trigger : TRIGGERS) {
            statements.add("CREATE TRIGGER IF NOT EXISTS trg_deleted_" + trigger[0]
                + " AFTER DELETE ON " + trigger[0]
                + " WHEN OLD.uuid IS NOT NULL"
                + " BEGIN INSERT OR REPLACE INTO deletions (kind, uuid, deleted_at) VALUES ('" + trigger[1]
                + "', OLD.uuid, strftime('%Y-%m-%dT%H:%M:%fZ', 'now')); END");
        }
        return statements;
    }
}
