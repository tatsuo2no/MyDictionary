package com.mydictionary.desktop.db;

import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.repository.NoteColumnRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqliteNoteColumnRepository implements NoteColumnRepository {
    private final SqliteDatabase database;

    public SqliteNoteColumnRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public List<NoteColumn> findByBookId(long bookId) {
        String sql = "SELECT * FROM note_columns WHERE book_id = ? ORDER BY sort_order, id";
        List<NoteColumn> result = new ArrayList<>();
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, bookId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("カラム一覧の取得に失敗しました", e);
        }
        return result;
    }

    private Optional<NoteColumn> findById(long id) {
        String sql = "SELECT * FROM note_columns WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("カラムの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    private Optional<NoteColumn> findByUuid(long bookId, String uuid) {
        String sql = "SELECT * FROM note_columns WHERE book_id = ? AND uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setString(2, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("カラムの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public NoteColumn insert(NoteColumn column) {
        String sql = "INSERT INTO note_columns (uuid, book_id, name, required, sort_order, is_primary, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setLong(2, column.getBookId());
            ps.setString(3, column.getName());
            ps.setInt(4, column.isRequired() ? 1 : 0);
            ps.setInt(5, column.getSortOrder());
            ps.setInt(6, column.isPrimaryKey() ? 1 : 0);
            ps.setString(7, Instant.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1)).orElseThrow();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("カラムの作成に失敗しました", e);
        }
        throw new IllegalStateException("カラムIDの採番に失敗しました");
    }

    @Override
    public void update(NoteColumn column) {
        String sql = "UPDATE note_columns SET name=?, required=?, sort_order=?, is_primary=?, updated_at=? "
            + "WHERE id=?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, column.getName());
            ps.setInt(2, column.isRequired() ? 1 : 0);
            ps.setInt(3, column.getSortOrder());
            ps.setInt(4, column.isPrimaryKey() ? 1 : 0);
            ps.setString(5, Instant.now().toString());
            ps.setLong(6, column.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("カラムの更新に失敗しました", e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM note_columns WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("カラムの削除に失敗しました", e);
        }
    }

    @Override
    public int countNonEmptyValues(long columnId) {
        String sql = "SELECT COUNT(*) FROM note_field_values WHERE column_id = ? "
            + "AND value IS NOT NULL AND TRIM(value) <> ''";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, columnId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("入力件数の取得に失敗しました", e);
        }
    }

    @Override
    public void setPrimary(long bookId, long columnId) {
        String now = Instant.now().toString();
        String sql = "UPDATE note_columns SET is_primary=?, required=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            for (NoteColumn column : findByBookId(bookId)) {
                if (column.getId() == columnId) {
                    if (column.isPrimaryKey() && column.isRequired()) {
                        continue;
                    }
                    ps.setInt(1, 1);
                    ps.setInt(2, 1);
                } else {
                    if (!column.isPrimaryKey()) {
                        continue;
                    }
                    ps.setInt(1, 0);
                    ps.setInt(2, column.isRequired() ? 1 : 0);
                }
                ps.setString(3, now);
                ps.setLong(4, column.getId());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("主キーの設定に失敗しました", e);
        }
    }

    @Override
    public void seedDefaultColumns(long bookId) {
        NoteColumn primary = new NoteColumn(0, null, bookId, "項目名", true, 0, Instant.now());
        primary.setPrimaryKey(true);
        insert(primary);
        insert(new NoteColumn(0, null, bookId, "読み", true, 1, Instant.now()));
        insert(new NoteColumn(0, null, bookId, "英訳", false, 2, Instant.now()));
    }

    @Override
    public NoteColumn upsertFromSync(long bookId, String uuid, String name, boolean required, int sortOrder,
                                      boolean primaryKey, Instant updatedAt) {
        Optional<NoteColumn> existing = findByUuid(bookId, uuid);
        if (existing.isPresent()) {
            String sql = "UPDATE note_columns SET name=?, required=?, sort_order=?, is_primary=?, updated_at=? "
                + "WHERE book_id=? AND uuid=?";
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setInt(2, required ? 1 : 0);
                ps.setInt(3, sortOrder);
                ps.setInt(4, primaryKey ? 1 : 0);
                ps.setString(5, updatedAt.toString());
                ps.setLong(6, bookId);
                ps.setString(7, uuid);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("カラムの同期更新に失敗しました", e);
            }
            return findByUuid(bookId, uuid).orElseThrow();
        }

        String sql = "INSERT INTO note_columns (uuid, book_id, name, required, sort_order, is_primary, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, bookId);
            ps.setString(3, name);
            ps.setInt(4, required ? 1 : 0);
            ps.setInt(5, sortOrder);
            ps.setInt(6, primaryKey ? 1 : 0);
            ps.setString(7, updatedAt.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("カラムの同期作成に失敗しました", e);
        }
        return findByUuid(bookId, uuid).orElseThrow();
    }

    private NoteColumn mapRow(ResultSet rs) throws SQLException {
        NoteColumn column = new NoteColumn(
            rs.getLong("id"),
            rs.getString("uuid"),
            rs.getLong("book_id"),
            rs.getString("name"),
            rs.getInt("required") != 0,
            rs.getInt("sort_order"),
            rs.getString("updated_at") == null ? null : Instant.parse(rs.getString("updated_at"))
        );
        column.setPrimaryKey(rs.getInt("is_primary") != 0);
        return column;
    }
}
