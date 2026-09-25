package com.mydictionary.desktop.db;

import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteTextColor;
import com.mydictionary.core.repository.NoteRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SqliteNoteRepository implements NoteRepository {
    private final SqliteDatabase database;

    public SqliteNoteRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public List<Note> findByBookId(long bookId) {
        String sql = "SELECT * FROM notes WHERE book_id = ? ORDER BY id";
        return query(sql, bookId);
    }

    @Override
    public Optional<Note> findById(long id) {
        String sql = "SELECT * FROM notes WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ノートの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Note> findByUuid(long bookId, String uuid) {
        String sql = "SELECT * FROM notes WHERE book_id = ? AND uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, bookId);
            ps.setString(2, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ノートの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Note> search(long bookId, String keyword) {
        String lowerKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        List<Note> result = new ArrayList<>();
        for (Note note : findByBookId(bookId)) {
            if (matchesKeyword(note, lowerKeyword)) {
                result.add(note);
            }
        }
        return result;
    }

    private boolean matchesKeyword(Note note, String lowerKeyword) {
        if (containsIgnoreCase(note.getBody(), lowerKeyword)) {
            return true;
        }
        for (String value : note.getFieldValues().values()) {
            if (containsIgnoreCase(value, lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String lowerKeyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    @Override
    public List<Note> findByTag(long bookId, long tagId) {
        String sql = "SELECT n.* FROM notes n JOIN note_tags nt ON n.id = nt.note_id "
            + "WHERE n.book_id = ? AND nt.tag_id = ? ORDER BY n.id";
        return query(sql, bookId, tagId);
    }

    @Override
    public Note insert(Note note) {
        // title/readingは以前の固定項目の名残の列（NOT NULL制約があるため空文字を入れて満たす）。
        // 実際の値はカラム自由設定の仕組み（note_field_values）に持たせる。
        String sql = "INSERT INTO notes (uuid, book_id, title, reading, body, "
            + "background_theme, background_image, text_color, created_at, updated_at) "
            + "VALUES (?, ?, '', '', ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String now = Instant.now().toString();
            ps.setString(1, UUID.randomUUID().toString());
            ps.setLong(2, note.getBookId());
            ps.setString(3, note.getBody());
            ps.setString(4, note.getBackgroundTheme().name());
            ps.setString(5, note.getBackgroundImageFileName());
            ps.setString(6, note.getTextColor().name());
            ps.setString(7, now);
            ps.setString(8, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    saveTagLinks(id, note.getTagIds());
                    saveFieldValues(id, note.getFieldValues());
                    return findById(id).orElseThrow();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ノートの作成に失敗しました", e);
        }
        throw new IllegalStateException("ノートIDの採番に失敗しました");
    }

    @Override
    public void update(Note note) {
        String sql = "UPDATE notes SET body=?, "
            + "background_theme=?, background_image=?, text_color=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, note.getBody());
            ps.setString(2, note.getBackgroundTheme().name());
            ps.setString(3, note.getBackgroundImageFileName());
            ps.setString(4, note.getTextColor().name());
            ps.setString(5, Instant.now().toString());
            ps.setLong(6, note.getId());
            ps.executeUpdate();
            saveTagLinks(note.getId(), note.getTagIds());
            saveFieldValues(note.getId(), note.getFieldValues());
        } catch (SQLException e) {
            throw new RuntimeException("ノートの更新に失敗しました", e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM notes WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ノートの削除に失敗しました", e);
        }
    }

    @Override
    public Note upsertFromSync(long bookId, String uuid, Map<Long, String> fieldValues, String body,
                                List<Long> tagIds, BookTheme backgroundTheme, String backgroundImageFileName,
                                NoteTextColor textColor, Instant createdAt, Instant updatedAt) {
        Optional<Note> existing = findByUuid(bookId, uuid);
        if (existing.isPresent()) {
            String sql = "UPDATE notes SET body=?, "
                + "background_theme=?, background_image=?, text_color=?, updated_at=? "
                + "WHERE book_id=? AND uuid=?";
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                ps.setString(1, body);
                ps.setString(2, backgroundTheme == null ? null : backgroundTheme.name());
                ps.setString(3, backgroundImageFileName);
                ps.setString(4, textColor == null ? null : textColor.name());
                ps.setString(5, updatedAt.toString());
                ps.setLong(6, bookId);
                ps.setString(7, uuid);
                ps.executeUpdate();
                saveTagLinks(existing.get().getId(), tagIds);
                saveFieldValues(existing.get().getId(), fieldValues);
            } catch (SQLException e) {
                throw new RuntimeException("ノートの同期更新に失敗しました", e);
            }
            return findByUuid(bookId, uuid).orElseThrow();
        }

        String sql = "INSERT INTO notes (uuid, book_id, title, reading, body, "
            + "background_theme, background_image, text_color, created_at, updated_at) "
            + "VALUES (?, ?, '', '', ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid);
            ps.setLong(2, bookId);
            ps.setString(3, body);
            ps.setString(4, backgroundTheme == null ? null : backgroundTheme.name());
            ps.setString(5, backgroundImageFileName);
            ps.setString(6, textColor == null ? null : textColor.name());
            ps.setString(7, createdAt.toString());
            ps.setString(8, updatedAt.toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    saveTagLinks(keys.getLong(1), tagIds);
                    saveFieldValues(keys.getLong(1), fieldValues);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ノートの同期作成に失敗しました", e);
        }
        return findByUuid(bookId, uuid).orElseThrow();
    }

    private void saveTagLinks(long noteId, List<Long> tagIds) throws SQLException {
        try (PreparedStatement delete = database.getConnection().prepareStatement("DELETE FROM note_tags WHERE note_id = ?")) {
            delete.setLong(1, noteId);
            delete.executeUpdate();
        }
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = database.getConnection()
                .prepareStatement("INSERT INTO note_tags (note_id, tag_id) VALUES (?, ?)")) {
            for (Long tagId : tagIds) {
                insert.setLong(1, noteId);
                insert.setLong(2, tagId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private void saveFieldValues(long noteId, Map<Long, String> fieldValues) throws SQLException {
        try (PreparedStatement delete = database.getConnection()
                .prepareStatement("DELETE FROM note_field_values WHERE note_id = ?")) {
            delete.setLong(1, noteId);
            delete.executeUpdate();
        }
        if (fieldValues == null || fieldValues.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = database.getConnection()
                .prepareStatement("INSERT INTO note_field_values (note_id, column_id, value) VALUES (?, ?, ?)")) {
            for (Map.Entry<Long, String> entry : fieldValues.entrySet()) {
                insert.setLong(1, noteId);
                insert.setLong(2, entry.getKey());
                insert.setString(3, entry.getValue());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private List<Note> query(String sql, Object... params) {
        List<Note> result = new ArrayList<>();
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ノート一覧の取得に失敗しました", e);
        }
        return result;
    }

    private Note mapRow(ResultSet rs) throws SQLException {
        Note note = new Note(
            rs.getLong("id"),
            rs.getString("uuid"),
            rs.getLong("book_id"),
            rs.getString("body"),
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
        note.setTagIds(loadTagIds(note.getId()));
        note.setFieldValues(loadFieldValues(note.getId()));

        // 3列とも既存データではNULL（未設定）のことがあるため、Noteのデフォルト値
        // （白背景・画像なし・黒文字）にフォールバックする。
        String backgroundThemeName = rs.getString("background_theme");
        if (backgroundThemeName != null) {
            try {
                note.setBackgroundTheme(BookTheme.valueOf(backgroundThemeName));
            } catch (IllegalArgumentException ignored) {
                // 不明な値は既定値のまま
            }
        }
        note.setBackgroundImageFileName(rs.getString("background_image"));
        String textColorName = rs.getString("text_color");
        if (textColorName != null) {
            try {
                note.setTextColor(NoteTextColor.valueOf(textColorName));
            } catch (IllegalArgumentException ignored) {
                // 不明な値は既定値のまま
            }
        }
        return note;
    }

    private List<Long> loadTagIds(long noteId) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = database.getConnection().prepareStatement("SELECT tag_id FROM note_tags WHERE note_id = ?")) {
            ps.setLong(1, noteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    private Map<Long, String> loadFieldValues(long noteId) throws SQLException {
        Map<Long, String> values = new HashMap<>();
        try (PreparedStatement ps = database.getConnection()
                .prepareStatement("SELECT column_id, value FROM note_field_values WHERE note_id = ?")) {
            ps.setLong(1, noteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.put(rs.getLong("column_id"), rs.getString("value"));
                }
            }
        }
        return values;
    }
}
