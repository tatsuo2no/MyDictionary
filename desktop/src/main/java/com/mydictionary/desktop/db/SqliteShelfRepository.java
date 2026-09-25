package com.mydictionary.desktop.db;

import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.repository.ShelfRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqliteShelfRepository implements ShelfRepository {
    private final SqliteDatabase database;

    public SqliteShelfRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public List<Shelf> findAll() {
        String sql = "SELECT * FROM shelves ORDER BY sort_order, id";
        List<Shelf> result = new ArrayList<>();
        try (Statement st = database.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("シェルフ一覧の取得に失敗しました", e);
        }
        return result;
    }

    @Override
    public Optional<Shelf> findById(long id) {
        String sql = "SELECT * FROM shelves WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("シェルフの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Shelf> findByUuid(String uuid) {
        String sql = "SELECT * FROM shelves WHERE uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("シェルフの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public Shelf insert(Shelf shelf) {
        String sql = "INSERT INTO shelves (uuid, name, cover_pattern, cover_custom_path, sort_order, "
            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String now = Instant.now().toString();
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, shelf.getName());
            ps.setString(3, shelf.getCover().isCustom() ? null : shelf.getCover().getPattern().name());
            ps.setString(4, shelf.getCover().isCustom() ? shelf.getCover().getCustomImagePath() : null);
            ps.setInt(5, nextSortOrder());
            ps.setString(6, now);
            ps.setString(7, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1)).orElseThrow();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("シェルフの作成に失敗しました", e);
        }
        throw new IllegalStateException("シェルフIDの採番に失敗しました");
    }

    @Override
    public void update(Shelf shelf) {
        String sql = "UPDATE shelves SET name=?, cover_pattern=?, cover_custom_path=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, shelf.getName());
            ps.setString(2, shelf.getCover().isCustom() ? null : shelf.getCover().getPattern().name());
            ps.setString(3, shelf.getCover().isCustom() ? shelf.getCover().getCustomImagePath() : null);
            ps.setString(4, Instant.now().toString());
            ps.setLong(5, shelf.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("シェルフの更新に失敗しました", e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM shelves WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("シェルフの削除に失敗しました", e);
        }
    }

    @Override
    public void updateManualOrder(List<Long> orderedShelfIds) {
        String sql = "UPDATE shelves SET sort_order = ? WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < orderedShelfIds.size(); i++) {
                ps.setInt(1, i);
                ps.setLong(2, orderedShelfIds.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("シェルフの並び順の更新に失敗しました", e);
        }
    }

    /** 新規シェルフを末尾に付けるための sort_order 値（既存の最大値+1）。 */
    private int nextSortOrder() {
        try (Statement st = database.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COALESCE(MAX(sort_order), -1) + 1 AS next FROM shelves")) {
            return rs.next() ? rs.getInt("next") : 0;
        } catch (SQLException e) {
            throw new RuntimeException("sort_orderの採番に失敗しました", e);
        }
    }

    @Override
    public Shelf upsertFromSync(String uuid, String name, BookCover cover, Instant createdAt, Instant updatedAt) {
        Optional<Shelf> existing = findByUuid(uuid);
        if (existing.isPresent()) {
            String sql = "UPDATE shelves SET name=?, cover_pattern=?, cover_custom_path=?, updated_at=? WHERE uuid=?";
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, cover.isCustom() ? null : cover.getPattern().name());
                ps.setString(3, cover.isCustom() ? cover.getCustomImagePath() : null);
                ps.setString(4, updatedAt.toString());
                ps.setString(5, uuid);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("シェルフの同期更新に失敗しました", e);
            }
            return findByUuid(uuid).orElseThrow();
        }

        String sql = "INSERT INTO shelves (uuid, name, cover_pattern, cover_custom_path, sort_order, "
            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setString(3, cover.isCustom() ? null : cover.getPattern().name());
            ps.setString(4, cover.isCustom() ? cover.getCustomImagePath() : null);
            // sort_order（表示順）は同期対象外。同期で入ってきたシェルフは末尾に付ける。
            ps.setInt(5, nextSortOrder());
            ps.setString(6, createdAt.toString());
            ps.setString(7, updatedAt.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("シェルフの同期作成に失敗しました", e);
        }
        return findByUuid(uuid).orElseThrow();
    }

    private Shelf mapRow(ResultSet rs) throws SQLException {
        String coverPatternName = rs.getString("cover_pattern");
        String coverCustomPath = rs.getString("cover_custom_path");
        BookCover cover = coverCustomPath != null
            ? BookCover.ofCustom(coverCustomPath)
            : BookCover.ofPattern(resolvePattern(coverPatternName));

        Shelf shelf = new Shelf(
            rs.getLong("id"),
            rs.getString("uuid"),
            rs.getString("name"),
            cover,
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
        shelf.setSortOrder(rs.getInt("sort_order"));
        return shelf;
    }

    private static BookCover.Pattern resolvePattern(String name) {
        try {
            return BookCover.Pattern.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return BookCover.Pattern.STRIPES;
        }
    }
}
