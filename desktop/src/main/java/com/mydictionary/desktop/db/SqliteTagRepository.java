package com.mydictionary.desktop.db;

import com.mydictionary.core.model.Tag;
import com.mydictionary.core.repository.TagRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqliteTagRepository implements TagRepository {
    private final SqliteDatabase database;

    public SqliteTagRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public List<Tag> findByBookId(long bookId) {
        String sql = "SELECT * FROM tags WHERE book_id = ? ORDER BY id";
        List<Tag> result = new ArrayList<>();
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("タグ一覧の取得に失敗しました", e);
        }
        return result;
    }

    @Override
    public Optional<Tag> findById(long id) {
        String sql = "SELECT * FROM tags WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("タグの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Tag> findByUuid(long bookId, String uuid) {
        String sql = "SELECT * FROM tags WHERE book_id = ? AND uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setString(2, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("タグの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public Tag insert(Tag tag) {
        String sql = "INSERT INTO tags (uuid, book_id, name, parent_tag_id, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setLong(2, tag.getBookId());
            ps.setString(3, tag.getName());
            if (tag.getParentTagId() != null) {
                ps.setLong(4, tag.getParentTagId());
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1)).orElseThrow();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("タグの作成に失敗しました", e);
        }
        throw new IllegalStateException("タグIDの採番に失敗しました");
    }

    @Override
    public void update(Tag tag) {
        String sql = "UPDATE tags SET name=?, parent_tag_id=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, tag.getName());
            if (tag.getParentTagId() != null) {
                ps.setLong(2, tag.getParentTagId());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, Instant.now().toString());
            ps.setLong(4, tag.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("タグの更新に失敗しました", e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM tags WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("タグの削除に失敗しました", e);
        }
    }

    @Override
    public boolean isUsedByAnyNote(long tagId) {
        String sql = "SELECT COUNT(*) FROM note_tags WHERE tag_id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("タグ使用状況の確認に失敗しました", e);
        }
    }

    @Override
    public Tag upsertFromSync(long bookId, String uuid, String name, Long parentTagId, Instant updatedAt) {
        Optional<Tag> existing = findByUuid(bookId, uuid);
        if (existing.isPresent()) {
            String sql = "UPDATE tags SET name=?, parent_tag_id=?, updated_at=? WHERE book_id=? AND uuid=?";
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                ps.setString(1, name);
                if (parentTagId != null) {
                    ps.setLong(2, parentTagId);
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                ps.setString(3, updatedAt.toString());
                ps.setLong(4, bookId);
                ps.setString(5, uuid);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("タグの同期更新に失敗しました", e);
            }
            return findByUuid(bookId, uuid).orElseThrow();
        }

        String sql = "INSERT INTO tags (uuid, book_id, name, parent_tag_id, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, bookId);
            ps.setString(3, name);
            if (parentTagId != null) {
                ps.setLong(4, parentTagId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setString(5, updatedAt.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("タグの同期作成に失敗しました", e);
        }
        return findByUuid(bookId, uuid).orElseThrow();
    }

    private Tag mapRow(ResultSet rs) throws SQLException {
        long parentId = rs.getLong("parent_tag_id");
        Long parent = rs.wasNull() ? null : parentId;
        String updatedAtText = rs.getString("updated_at");
        Instant updatedAt = updatedAtText != null ? Instant.parse(updatedAtText) : Instant.EPOCH;
        return new Tag(rs.getLong("id"), rs.getString("uuid"), rs.getLong("book_id"), rs.getString("name"),
            parent, updatedAt);
    }
}
