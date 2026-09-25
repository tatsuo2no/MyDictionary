package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Tag;
import com.mydictionary.core.validation.NameValidator;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteTagRepository;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** ブック作成画面。タイトル・テーマ・フォント・アイコン・初期タグ(任意)を設定する。 */
public class BookCreateScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final long shelfId;
    private final VBox view = new VBox(12);

    private TextField titleField;
    private ComboBox<BookTheme> themeCombo;
    private ComboBox<BookFont> fontCombo;
    private ComboBox<Integer> fontSizeCombo;
    private CoverPatternPickerButton coverPicker;
    private TextField tagField;
    private Label errorLabel;

    public BookCreateScreen(SceneNavigator navigator, SqliteDatabase database, long shelfId) {
        this.navigator = navigator;
        this.database = database;
        this.shelfId = shelfId;
        build();
    }

    public VBox getView() {
        return view;
    }

    private void build() {
        Label title = new Label("ブックを作成");
        title.getStyleClass().add("screen-title");

        titleField = new TextField();
        titleField.setPromptText("英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースが使用できます");

        themeCombo = BookFormWidgets.createThemeCombo();
        themeCombo.setValue(BookTheme.WHITE);
        themeCombo.valueProperty().addListener((obs, oldValue, newValue) -> coverPicker.refresh());

        fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll(BookFont.values());
        fontCombo.setValue(BookFont.MEIRYO_UI);
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
        fontSizeCombo.setValue(Book.DEFAULT_FONT_SIZE_PT);

        coverPicker = new CoverPatternPickerButton(BookCover.ofPattern(BookCover.Pattern.STRIPES),
            () -> themeCombo.getValue().getColorCode(), () -> themeCombo.getValue().getTextColorCode());

        Button browseCoverButton = new Button("参照...");
        browseCoverButton.setOnAction(e -> onBrowseCoverImage());
        HBox coverBox = new HBox(8, coverPicker, browseCoverButton);

        tagField = new TextField();
        tagField.setPromptText("カンマ区切りで初期タグを入力（オプション）");

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        Button saveButton = new Button("作成する");
        saveButton.setOnAction(e -> onSave());

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showBookList(shelfId));

        HBox buttons = new HBox(12, saveButton, backButton);

        GridPane form = new GridPane();
        form.setVgap(8);
        form.setHgap(8);
        form.addRow(0, new Label("ブックタイトル"), titleField);
        form.addRow(1, new Label("ブックテーマ"), themeCombo);
        form.addRow(2, new Label("ブックフォント"), fontCombo);
        form.addRow(3, new Label("フォントサイズ"), fontSizeCombo);
        form.addRow(4, new Label("ブックカバー"), coverBox);
        form.addRow(5, new Label("タグ（オプション）"), tagField);

        view.setPadding(new Insets(16));
        view.getChildren().addAll(title, form, errorLabel, buttons);
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

    private void onSave() {
        String titleText = titleField.getText() == null ? "" : titleField.getText().trim();
        if (!NameValidator.isValid(titleText)) {
            errorLabel.setText("ブックタイトルは英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースのみ使用できます");
            return;
        }

        List<String> tagNames = new ArrayList<>();
        String tagsText = tagField.getText();
        if (tagsText != null && !tagsText.isBlank()) {
            for (String tagName : tagsText.split(",")) {
                String trimmed = tagName.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!NameValidator.isValid(trimmed)) {
                    errorLabel.setText("タグ名は英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースのみ使用できます: " + trimmed);
                    return;
                }
                tagNames.add(trimmed);
            }
        }

        SqliteBookRepository bookRepository = new SqliteBookRepository(database);
        Book book = new Book(0, null, shelfId, titleText, themeCombo.getValue(), fontCombo.getValue(),
            coverPicker.getValue(), Instant.now(), Instant.now());
        book.setFontSizePt(fontSizeCombo.getValue());
        Book saved = bookRepository.insert(book);

        if (!tagNames.isEmpty()) {
            SqliteTagRepository tagRepository = new SqliteTagRepository(database);
            for (String name : tagNames) {
                tagRepository.insert(new Tag(0, null, saved.getId(), name, null, Instant.now()));
            }
        }

        navigator.showBookList(shelfId);
    }
}
