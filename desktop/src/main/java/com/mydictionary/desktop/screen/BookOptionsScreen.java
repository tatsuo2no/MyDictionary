package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.validation.NameValidator;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteShelfRepository;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * ブックオプション画面。
 * タイトル・テーマ・フォント・アイコン・タグへの導線・ブック削除を提供する。
 */
public class BookOptionsScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final SqliteBookRepository bookRepository;
    private final long bookId;
    private final VBox view = new VBox(12);

    private Book book;
    private TextField titleField;
    private ComboBox<BookTheme> themeCombo;
    private ComboBox<BookFont> fontCombo;
    private ComboBox<Integer> fontSizeCombo;
    private ComboBox<Shelf> shelfCombo;
    private CoverPatternPickerButton coverPicker;
    private Label errorLabel;

    public BookOptionsScreen(SceneNavigator navigator, SqliteDatabase database, long bookId) {
        this.navigator = navigator;
        this.database = database;
        this.bookId = bookId;
        this.bookRepository = new SqliteBookRepository(database);
        build();
    }

    public VBox getView() {
        return view;
    }

    private void build() {
        book = bookRepository.findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));

        view.setStyle("-fx-font-family: '" + book.getFont().getFamilyName() + "';");
        view.setPadding(new Insets(16));

        Label title = new Label("ブックオプション");
        title.getStyleClass().add("screen-title");

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showBookList(book.getShelfId()));

        HBox header = new HBox(16, title, backButton);

        titleField = new TextField(book.getTitle());

        themeCombo = BookFormWidgets.createThemeCombo();
        themeCombo.setValue(book.getTheme());
        themeCombo.valueProperty().addListener((obs, oldValue, newValue) -> coverPicker.refresh());

        fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll(BookFont.values());
        fontCombo.setValue(book.getFont());
        fontCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(BookFont f) {
                return f == null ? "" : f.getFamilyName();
            }

            @Override
            public BookFont fromString(String s) {
                return null;
            }
        });

        fontSizeCombo = BookFormWidgets.createFontSizeCombo();
        fontSizeCombo.setValue(book.getFontSizePt());

        coverPicker = new CoverPatternPickerButton(book.getCover(),
            () -> themeCombo.getValue().getColorCode(), () -> themeCombo.getValue().getTextColorCode());

        Button browseCoverButton = new Button("参照...");
        browseCoverButton.setOnAction(e -> onBrowseCoverImage());
        HBox coverBox = new HBox(8, coverPicker, browseCoverButton);

        List<Shelf> allShelves = new SqliteShelfRepository(database).findAll();
        shelfCombo = new ComboBox<>();
        shelfCombo.getItems().addAll(allShelves);
        shelfCombo.getItems().stream().filter(s -> s.getId() == book.getShelfId()).findFirst()
            .ifPresent(shelfCombo::setValue);
        shelfCombo.setCellFactory(lv -> shelfCell());
        shelfCombo.setButtonCell(shelfCell());

        Button tagButton = new Button("タグ");
        tagButton.setOnAction(e -> navigator.showTagScreen(bookId));

        Button noteColumnsButton = new Button("ノート項目");
        noteColumnsButton.setOnAction(e -> navigator.showNoteColumns(bookId));

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        Button saveButton = new Button("保存する");
        saveButton.setOnAction(e -> onSave());

        GridPane form = new GridPane();
        form.setVgap(8);
        form.setHgap(8);
        form.addRow(0, new Label("ブックタイトル"), titleField);
        form.addRow(1, new Label("所属シェルフ"), shelfCombo);
        form.addRow(2, new Label("ブックテーマ"), themeCombo);
        form.addRow(3, new Label("ブックフォント"), fontCombo);
        form.addRow(4, new Label("フォントサイズ"), fontSizeCombo);
        form.addRow(5, new Label("ブックカバー"), coverBox);
        form.addRow(6, new Label("タグ"), tagButton);
        form.addRow(7, new Label("ノート項目"), noteColumnsButton);

        Button deleteButton = new Button("ブックを削除");
        deleteButton.setStyle("-fx-text-fill: red;");
        deleteButton.setOnAction(e -> onDeleteBook());

        view.getChildren().addAll(header, form, errorLabel, saveButton, deleteButton);
    }

    private void onSave() {
        String titleText = titleField.getText() == null ? "" : titleField.getText().trim();
        if (!NameValidator.isValid(titleText)) {
            errorLabel.setText("ブックタイトルは英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースのみ使用できます");
            return;
        }

        book.setTitle(titleText);
        book.setTheme(themeCombo.getValue());
        book.setFont(fontCombo.getValue());
        book.setFontSizePt(fontSizeCombo.getValue());
        book.setCover(coverPicker.getValue());
        if (shelfCombo.getValue() != null) {
            book.setShelfId(shelfCombo.getValue().getId());
        }
        bookRepository.update(book);

        navigator.showBookOptions(bookId);
    }

    private static ListCell<Shelf> shelfCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Shelf shelf, boolean empty) {
                super.updateItem(shelf, empty);
                setText(empty || shelf == null ? null : shelf.getName());
            }
        };
    }

    private void onBrowseCoverImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("カバー画像を選択");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("画像ファイル", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = fileChooser.showOpenDialog(view.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            String savedPath = BookFormWidgets.saveCustomCoverImage(file);
            coverPicker.setValue(BookCover.ofCustom(savedPath));
        } catch (IOException ex) {
            errorLabel.setText("画像の読み込みに失敗しました: " + ex.getMessage());
        }
    }

    private void onDeleteBook() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("ブックの削除");
        dialog.setHeaderText(null);

        ButtonType deleteButtonType = new ButtonType("削除", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteButtonType, ButtonType.CANCEL);

        Label message = new Label("このブックを削除しますか？（一度削除したブックは復元できません）");
        message.setWrapText(true);
        message.setMaxWidth(320);

        TextField confirmField = new TextField();
        confirmField.setPromptText("「削除」と入力してください");

        VBox content = new VBox(12, message, confirmField);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        Node deleteNode = dialog.getDialogPane().lookupButton(deleteButtonType);
        deleteNode.setDisable(true);
        confirmField.textProperty().addListener((obs, oldValue, newValue) ->
            deleteNode.setDisable(!"削除".equals(newValue)));

        long shelfIdForReturn = book.getShelfId();
        dialog.showAndWait().ifPresent(result -> {
            if (result == deleteButtonType) {
                bookRepository.delete(bookId);
                navigator.showBookList(shelfIdForReturn);
            }
        });
    }
}
