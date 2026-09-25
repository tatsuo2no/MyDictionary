package com.mydictionary.desktop.screen;

import com.mydictionary.desktop.db.SqliteDatabase;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.net.URL;
import java.util.Optional;

/** 画面遷移とダイアログ表示を一元管理する。 */
public class SceneNavigator {
    private final Stage stage;
    private final SqliteDatabase database;

    public SceneNavigator(Stage stage, SqliteDatabase database) {
        this.stage = stage;
        this.database = database;
    }

    public SqliteDatabase getDatabase() {
        return database;
    }

    public void showShelfList() {
        ShelfListScreen screen = new ShelfListScreen(this, database);
        stage.setScene(createScene(screen.getView()));
    }

    public void showShelfCreate() {
        ShelfCreateScreen screen = new ShelfCreateScreen(this, database);
        stage.setScene(createScene(screen.getView()));
    }

    public void showShelfOptions(long shelfId) {
        ShelfOptionsScreen screen = new ShelfOptionsScreen(this, database, shelfId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showBookList(long shelfId) {
        BookListScreen screen = new BookListScreen(this, database, shelfId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showBookCreate(long shelfId) {
        BookCreateScreen screen = new BookCreateScreen(this, database, shelfId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showBook(long bookId) {
        BookScreen screen = new BookScreen(this, database, bookId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showBookOptions(long bookId) {
        BookOptionsScreen screen = new BookOptionsScreen(this, database, bookId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showTagScreen(long bookId) {
        TagScreen screen = new TagScreen(this, database, bookId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showNoteColumns(long bookId) {
        NoteColumnScreen screen = new NoteColumnScreen(this, database, bookId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showNote(long bookId, long noteId) {
        NoteScreen screen = new NoteScreen(this, database, bookId, noteId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showNoteCreate(long bookId) {
        NoteCreateScreen screen = new NoteCreateScreen(this, database, bookId, null);
        stage.setScene(createScene(screen.getView()));
    }

    public void showNoteEdit(long bookId, long noteId) {
        NoteCreateScreen screen = new NoteCreateScreen(this, database, bookId, noteId);
        stage.setScene(createScene(screen.getView()));
    }

    public void showSettings() {
        SettingsScreen screen = new SettingsScreen(this);
        stage.setScene(createScene(screen.getView()));
    }

    public void confirmExit() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "終了しますか？", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.setTitle("確認");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            stage.close();
            System.exit(0);
        }
    }

    private Scene createScene(Parent root) {
        Scene scene = new Scene(root, 960, 640);
        URL css = getClass().getResource("/com/mydictionary/desktop/style.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }
}
