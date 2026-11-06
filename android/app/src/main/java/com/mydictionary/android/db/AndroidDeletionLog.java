package com.mydictionary.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.mydictionary.core.repository.DeletionLog;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 削除の記録（deletionsテーブル。行の削除時にトリガーが自動で書き込む）をAndroidのSQLiteで読み書きする。 */
public class AndroidDeletionLog implements DeletionLog {
    private final DictionaryDbHelper helper;

    public AndroidDeletionLog(DictionaryDbHelper helper) {
        this.helper = helper;
    }

    @Override
    public Map<String, Instant> findAll(String kind) {
        Map<String, Instant> result = new HashMap<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor cursor = db.rawQuery("SELECT uuid, deleted_at FROM deletions WHERE kind = ?",
                new String[]{kind})) {
            while (cursor.moveToNext()) {
                result.put(cursor.getString(0), Instant.parse(cursor.getString(1)));
            }
        }
        return result;
    }

    @Override
    public void record(String kind, String uuid, Instant deletedAt) {
        Instant existing = findAll(kind).get(uuid);
        if (existing != null && !deletedAt.isAfter(existing)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("kind", kind);
        values.put("uuid", uuid);
        values.put("deleted_at", deletedAt.toString());
        helper.getWritableDatabase().insertWithOnConflict("deletions", null, values,
            SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public void remove(String kind, String uuid) {
        helper.getWritableDatabase().delete("deletions", "kind = ? AND uuid = ?", new String[]{kind, uuid});
    }
}
