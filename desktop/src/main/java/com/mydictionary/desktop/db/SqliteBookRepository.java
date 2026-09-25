package com.mydictionary.desktop.db;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.repository.BookRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SqliteBookRepository implements BookRepository {
    private final SqliteDatabase database;

    public SqliteBookRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public List<Book> findAll() {
        String sql = "SELECT * FROM books ORDER BY sort_order, id";
        List<Book> result = new ArrayList<>();
        try (Statement st = database.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("ブック一覧の取得に失敗しました", e);
        }
        return result;
    }

    @Override
    public Optional<Book> findById(long id) {
        String sql = "SELECT * FROM books WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ブックの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Book> findByUuid(String uuid) {
        String sql = "SELECT * FROM books WHERE uuid = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ブックの取得に失敗しました", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Book> findByShelfId(long shelfId) {
        String sql = "SELECT * FROM books WHERE shelf_id = ? ORDER BY sort_order, id";
        List<Book> result = new ArrayList<>();
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, shelfId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("シェルフ内のブック一覧の取得に失敗しました", e);
        }
        return result;
    }

    @Override
    public Book insert(Book book) {
        String sql = "INSERT INTO books (uuid, shelf_id, title, theme, font, font_size_pt, icon_preset, "
            + "icon_custom_path, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String now = Instant.now().toString();
            ps.setString(1, UUID.randomUUID().toString());
            ps.setLong(2, book.getShelfId());
            ps.setString(3, book.getTitle());
            ps.setString(4, book.getTheme().name());
            ps.setString(5, book.getFont().name());
            ps.setInt(6, book.getFontSizePt());
            ps.setString(7, book.getCover().isCustom() ? null : book.getCover().getPattern().name());
            ps.setString(8, book.getCover().isCustom() ? book.getCover().getCustomImagePath() : null);
            ps.setInt(9, nextSortOrder(book.getShelfId()));
            ps.setString(10, now);
            ps.setString(11, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long newBookId = keys.getLong(1);
                    // ノートのカラム（項目名・読み・英訳に相当）は自由設定制のため、
                    // ブック作成時に既定の3カラムを用意しておく（ユーザーは後から自由に編集できる）。
                    new SqliteNoteColumnRepository(database).seedDefaultColumns(newBookId);
                    return findById(newBookId).orElseThrow();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("ブックの作成に失敗しました", e);
        }
        throw new IllegalStateException("ブックIDの採番に失敗しました");
    }

    @Override
    public void update(Book book) {
        String sql = "UPDATE books SET shelf_id=?, title=?, theme=?, font=?, font_size_pt=?, icon_preset=?, "
            + "icon_custom_path=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, book.getShelfId());
            ps.setString(2, book.getTitle());
            ps.setString(3, book.getTheme().name());
            ps.setString(4, book.getFont().name());
            ps.setInt(5, book.getFontSizePt());
            ps.setString(6, book.getCover().isCustom() ? null : book.getCover().getPattern().name());
            ps.setString(7, book.getCover().isCustom() ? book.getCover().getCustomImagePath() : null);
            ps.setString(8, Instant.now().toString());
            ps.setLong(9, book.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ブックの更新に失敗しました", e);
        }
    }

    @Override
    public void delete(long id) {
        String sql = "DELETE FROM books WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ブックの削除に失敗しました", e);
        }
    }

    @Override
    public void updateManualOrder(List<Long> orderedBookIds) {
        String sql = "UPDATE books SET sort_order = ? WHERE id = ?";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < orderedBookIds.size(); i++) {
                ps.setInt(1, i);
                ps.setLong(2, orderedBookIds.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("ブックの並び順の更新に失敗しました", e);
        }
    }

    /** 同一シェルフ内で新規ブックを末尾に付けるための sort_order 値（そのシェルフ内の既存最大値+1）。 */
    private int nextSortOrder(long shelfId) {
        try (PreparedStatement ps = database.getConnection().prepareStatement(
                "SELECT COALESCE(MAX(sort_order), -1) + 1 AS next FROM books WHERE shelf_id = ?")) {
            ps.setLong(1, shelfId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("next") : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("sort_orderの採番に失敗しました", e);
        }
    }

    @Override
    public Book upsertFromSync(String uuid, long shelfId, String title, BookTheme theme, BookFont font,
                                int fontSizePt, BookCover cover, Instant createdAt, Instant updatedAt) {
        Optional<Book> existing = findByUuid(uuid);
        if (existing.isPresent()) {
            String sql = "UPDATE books SET shelf_id=?, title=?, theme=?, font=?, font_size_pt=?, icon_preset=?, "
                + "icon_custom_path=?, updated_at=? WHERE uuid=?";
            try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                ps.setLong(1, shelfId);
                ps.setString(2, title);
                ps.setString(3, theme.name());
                ps.setString(4, font.name());
                ps.setInt(5, fontSizePt);
                ps.setString(6, cover.isCustom() ? null : cover.getPattern().name());
                ps.setString(7, cover.isCustom() ? cover.getCustomImagePath() : null);
                ps.setString(8, updatedAt.toString());
                ps.setString(9, uuid);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("ブックの同期更新に失敗しました", e);
            }
            return findByUuid(uuid).orElseThrow();
        }

        String sql = "INSERT INTO books (uuid, shelf_id, title, theme, font, font_size_pt, icon_preset, "
            + "icon_custom_path, sort_order, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, shelfId);
            ps.setString(3, title);
            ps.setString(4, theme.name());
            ps.setString(5, font.name());
            ps.setInt(6, fontSizePt);
            ps.setString(7, cover.isCustom() ? null : cover.getPattern().name());
            ps.setString(8, cover.isCustom() ? cover.getCustomImagePath() : null);
            // sort_order（表示順）は同期対象外。同期で入ってきたブックはそのシェルフ内の末尾に付ける。
            ps.setInt(9, nextSortOrder(shelfId));
            ps.setString(10, createdAt.toString());
            ps.setString(11, updatedAt.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("ブックの同期作成に失敗しました", e);
        }
        return findByUuid(uuid).orElseThrow();
    }

    private Book mapRow(ResultSet rs) throws SQLException {
        String coverPatternName = rs.getString("icon_preset");
        String coverCustomPath = rs.getString("icon_custom_path");
        BookCover cover = coverCustomPath != null
            ? BookCover.ofCustom(coverCustomPath)
            : BookCover.ofPattern(resolvePattern(coverPatternName));

        Book book = new Book(
            rs.getLong("id"),
            rs.getString("uuid"),
            rs.getLong("shelf_id"),
            rs.getString("title"),
            BookTheme.valueOf(rs.getString("theme")),
            resolveFont(rs.getString("font")),
            cover,
            Instant.parse(rs.getString("created_at")),
            Instant.parse(rs.getString("updated_at"))
        );
        book.setSortOrder(rs.getInt("sort_order"));
        book.setFontSizePt(resolveFontSizePt(rs.getInt("font_size_pt"), rs.wasNull()));
        return book;
    }

    /** 移行直後などfont_size_ptがまだNULLの既存行は、既定サイズにフォールバックする。 */
    private static int resolveFontSizePt(int value, boolean wasNull) {
        return wasNull || value <= 0 ? Book.DEFAULT_FONT_SIZE_PT : value;
    }

    /**
     * DB列名(icon_preset/icon_custom_path)は、以前の「記号アイコン」機能の名残をそのまま
     * 流用している（スキーマ変更によるデータ破損リスクを避けるため）。旧アイコン機能で保存された
     * 記号名（例: "BOOK"）はPattern.valueOf()で解決できないため、その場合はデフォルトの模様に
     * フォールバックし、既存ブックの読み込みが例外で落ちないようにする。
     */
    private static BookCover.Pattern resolvePattern(String name) {
        try {
            return BookCover.Pattern.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return BookCover.Pattern.STRIPES;
        }
    }

    /**
     * 太字非対応フォント（MS ゴシック等）をBookFontから削除した際、既存ブックが持つ
     * 旧フォント名の値はvalueOf()で解決できなくなる。その場合はデフォルトのMeiryo UIに
     * フォールバックし、既存ブックの読み込みが例外で落ちないようにする。
     */
    private static BookFont resolveFont(String name) {
        try {
            return BookFont.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return BookFont.MEIRYO_UI;
        }
    }
}
