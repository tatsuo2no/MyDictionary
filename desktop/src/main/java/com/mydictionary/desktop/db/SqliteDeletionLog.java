package com.mydictionary.desktop.db;

import com.mydictionary.core.repository.DeletionLog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** 削除の記録（deletionsテーブル。行の削除時にトリガーが自動で書き込む）をSQLiteで読み書きする。 */
public class SqliteDeletionLog implements DeletionLog {
    private final SqliteDatabase database;

    public SqliteDeletionLog(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public Map<String, Instant> findAll(String kind) {
        Map<String, Instant> result = new HashMap<>();
        try (PreparedStatement ps = database.getConnection().prepareStatement(
                "SELECT uuid, deleted_at FROM deletions WHERE kind = ?")) {
            ps.setString(1, kind);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("uuid"), Instant.parse(rs.getString("deleted_at")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("削除の記録の取得に失敗しました", e);
        }
        return result;
    }

    @Override
    public void record(String kind, String uuid, Instant deletedAt) {
        Instant existing = findAll(kind).get(uuid);
        if (existing != null && !deletedAt.isAfter(existing)) {
            return;
        }
        try (PreparedStatement ps = database.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO deletions (kind, uuid, deleted_at) VALUES (?, ?, ?)")) {
            ps.setString(1, kind);
            ps.setString(2, uuid);
            ps.setString(3, deletedAt.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("削除の記録の保存に失敗しました", e);
        }
    }

    @Override
    public void remove(String kind, String uuid) {
        try (PreparedStatement ps = database.getConnection().prepareStatement(
                "DELETE FROM deletions WHERE kind = ? AND uuid = ?")) {
            ps.setString(1, kind);
            ps.setString(2, uuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("削除の記録の取り消しに失敗しました", e);
        }
    }
}
