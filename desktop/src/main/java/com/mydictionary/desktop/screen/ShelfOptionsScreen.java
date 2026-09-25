package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.validation.NameValidator;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteShelfRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;

/**
 * シェルフオプション画面。名前・カバーの編集とシェルフの削除を提供する。
 * ブックは必ずいずれか1つのシェルフに属する必要があるため、中にブックが1冊でも残っている
 * シェルフは削除できない（先に他のシェルフへ移動するか、ブック自体を削除してもらう）。
 */
public class ShelfOptionsScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final SqliteShelfRepository shelfRepository;
    private final SqliteBookRepository bookRepository;
    private final long shelfId;
    private final VBox view = new VBox(12);

    private Shelf shelf;
    private TextField nameField;
    private CoverPatternPickerButton coverPicker;
    private Label errorLabel;

    public ShelfOptionsScreen(SceneNavigator navigator, SqliteDatabase database, long shelfId) {
        this.navigator = navigator;
        this.database = database;
        this.shelfId = shelfId;
        this.shelfRepository = new SqliteShelfRepository(database);
        this.bookRepository = new SqliteBookRepository(database);
        build();
    }

    public VBox getView() {
        return view;
    }

    private void build() {
        shelf = shelfRepository.findById(shelfId)
            .orElseThrow(() -> new IllegalStateException("シェルフが見つかりません: " + shelfId));

        view.setPadding(new Insets(16));

        Label title = new Label("シェルフオプション");
        title.getStyleClass().add("screen-title");

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showShelfList());

        HBox header = new HBox(16, title, backButton);

        nameField = new TextField(shelf.getName());

        coverPicker = new CoverPatternPickerButton(shelf.getCover(),
            () -> Shelf.COVER_BACKGROUND_COLOR_CODE, () -> Shelf.COVER_TINT_COLOR_CODE);

        Button browseCoverButton = new Button("参照...");
        browseCoverButton.setOnAction(e -> onBrowseCoverImage());
        HBox coverBox = new HBox(8, coverPicker, browseCoverButton);

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        Button saveButton = new Button("保存する");
        saveButton.setOnAction(e -> onSave());

        GridPane form = new GridPane();
        form.setVgap(8);
        form.setHgap(8);
        form.addRow(0, new Label("シェルフ名"), nameField);
        form.addRow(1, new Label("シェルフカバー"), coverBox);

        Button deleteButton = new Button("シェルフを削除");
        deleteButton.setStyle("-fx-text-fill: red;");
        deleteButton.setOnAction(e -> onDeleteShelf());

        view.getChildren().addAll(header, form, errorLabel, saveButton, deleteButton);
    }

    private void onSave() {
        String nameText = nameField.getText() == null ? "" : nameField.getText().trim();
        if (!NameValidator.isValid(nameText)) {
            errorLabel.setText("シェルフ名は英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースのみ使用できます");
            return;
        }

        boolean nameTaken = shelfRepository.findAll().stream()
            .anyMatch(s -> s.getId() != shelfId && s.getName().equals(nameText));
        if (nameTaken) {
            errorLabel.setText("そのシェルフ名は既に使われています");
            return;
        }

        shelf.setName(nameText);
        shelf.setCover(coverPicker.getValue());
        shelfRepository.update(shelf);

        navigator.showShelfOptions(shelfId);
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

    private void onDeleteShelf() {
        if (!bookRepository.findByShelfId(shelfId).isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                "このシェルフにはブックが残っているため削除できません。"
                    + "先にブックを他のシェルフへ移動するか、ブック自体を削除してください。");
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("シェルフの削除");
        dialog.setHeaderText(null);

        ButtonType deleteButtonType = new ButtonType("削除", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteButtonType, ButtonType.CANCEL);

        Label message = new Label("このシェルフを削除しますか？（一度削除したシェルフは復元できません）");
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

        dialog.showAndWait().ifPresent(result -> {
            if (result == deleteButtonType) {
                shelfRepository.delete(shelfId);
                navigator.showShelfList();
            }
        });
    }
}
