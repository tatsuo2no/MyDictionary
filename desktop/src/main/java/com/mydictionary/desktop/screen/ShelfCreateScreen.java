package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.validation.NameValidator;
import com.mydictionary.desktop.db.SqliteShelfRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

/** シェルフ作成画面。名前とカバーを設定する（ブックと違い色テーマは持たない）。 */
public class ShelfCreateScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final VBox view = new VBox(12);

    private TextField nameField;
    private CoverPatternPickerButton coverPicker;
    private Label errorLabel;

    public ShelfCreateScreen(SceneNavigator navigator, SqliteDatabase database) {
        this.navigator = navigator;
        this.database = database;
        build();
    }

    public VBox getView() {
        return view;
    }

    private void build() {
        Label title = new Label("シェルフを作成");
        title.getStyleClass().add("screen-title");

        nameField = new TextField();
        nameField.setPromptText("英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースが使用できます");

        coverPicker = new CoverPatternPickerButton(BookCover.ofPattern(BookCover.Pattern.STRIPES),
            () -> Shelf.COVER_BACKGROUND_COLOR_CODE, () -> Shelf.COVER_TINT_COLOR_CODE);

        Button browseCoverButton = new Button("参照...");
        browseCoverButton.setOnAction(e -> onBrowseCoverImage());
        HBox coverBox = new HBox(8, coverPicker, browseCoverButton);

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        Button saveButton = new Button("作成する");
        saveButton.setOnAction(e -> onSave());

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showShelfList());

        HBox buttons = new HBox(12, saveButton, backButton);

        GridPane form = new GridPane();
        form.setVgap(8);
        form.setHgap(8);
        form.addRow(0, new Label("シェルフ名"), nameField);
        form.addRow(1, new Label("シェルフカバー"), coverBox);

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
        String nameText = nameField.getText() == null ? "" : nameField.getText().trim();
        if (!NameValidator.isValid(nameText)) {
            errorLabel.setText("シェルフ名は英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースのみ使用できます");
            return;
        }

        SqliteShelfRepository shelfRepository = new SqliteShelfRepository(database);
        boolean nameTaken = shelfRepository.findAll().stream().anyMatch(s -> s.getName().equals(nameText));
        if (nameTaken) {
            errorLabel.setText("そのシェルフ名は既に使われています");
            return;
        }

        Shelf shelf = new Shelf(0, null, nameText, coverPicker.getValue(), Instant.now(), Instant.now());
        shelfRepository.insert(shelf);

        navigator.showShelfList();
    }
}
