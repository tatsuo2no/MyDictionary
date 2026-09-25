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
        String sql = "INSERT INTO note_columns (uuid, book_id, name, required, sort_order, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setLong(2, column.getBookId());
            ps.setString(3, column.getName());
            ps.setInt(4, column.isRequired() ? 1 : 0);
            ps.setInt(5, column.getSortOrder());
            ps.setString(6, Instant.now().toString());
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
        String sql = "UPDATE note_columns SET name=?, required=?, sort_order=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, column.getName());
            ps.setInt(2, column.isRequired() ? 1 : 0);
            ps.setInt(3, column.getSortOrder());
            ps.setString(4, Instant.now().toString());
            ps.setLong(5, column.getId());
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
    public void seedDefaultColumns(long bookId) {
        insert(new NoteColumn(0, null, bookId, "項目名", true, 0, Instant.now()));
        insert(new NoteColumn(0, null, bookId, "読み", true, 1, Instant.now()));
        insert(new NoteColumn(0, null, bookId, "英訳", false, 2, Instant.now()));
    }

    @Override
    public NoteColumn upsertFromSync(long bookId, String uuid, String name, boolean required, int sortOrder,
                                      Instant updatedAt) {
        Optional<NoteColumn> existing = findByUuid(bookId, uuid);
        if (existing.isPresent()) {
            String sql = "UPDATE note_columns SET name=?, required=?, sort_order=?, updated_at=? "
                + "WHERE book_id=? AND uuid=?";
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setInt(2, required ? 1 : 0);
                ps.setInt(3, sortOrder);
                ps.setString(4, updatedAt.toString());
                ps.setLong(5, bookId);
                ps.setString(6, uuid);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("カラムの同期更新に失敗しました", e);
            }
            return findByUuid(bookId, uuid).orElseThrow();
        }

        String sql = "INSERT INTO note_columns (uuid, book_id, name, required, sort_order, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, bookId);
            ps.setString(3, name);
            ps.setInt(4, required ? 1 : 0);
            ps.setInt(5, sortOrder);
            ps.setString(6, updatedAt.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("カラムの同期作成に失敗しました", e);
        }
        return findByUuid(bookId, uuid).orElseThrow();
    }

    private NoteColumn mapRow(ResultSet rs) throws SQLException {
        return new NoteColumn(
            rs.getLong("id"),
            rs.getString("uuid"),
            rs.getLong("book_id"),
            rs.getString("name"),
            rs.getInt("required") != 0,
            rs.getInt("sort_order"),
            rs.getString("updated_at") == null ? null : Instant.parse(rs.getString("updated_at"))
        );
    }
}
