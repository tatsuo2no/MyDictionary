package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.validation.NameValidator;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteNoteColumnRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * ノートのカラム（項目）設定画面。以前は「項目名・読み・英訳」の3つが固定だったが、
 * ブックごとに自由な名前・数のカラムを設定できるようにした（2026-09）。
 * 先頭（並び順が最小）のカラムは常に必須カラムで、削除できない
 * （ノート一覧の見出し・ノート内リンクの既定表示・名前順の並び替えに使われる）。
 * それ以外のカラムは、必須/任意をチェックボックスで切り替えられ、削除もできる。
 * タグ画面（{@link TagScreen}）と同じく、追加・変更・削除は都度即座にDBへ反映する。
 */
public class NoteColumnScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final long bookId;
    private final SqliteNoteColumnRepository columnRepository;

    private final BorderPane view = new BorderPane();
    private final VBox listBox = new VBox(8);

    private Book book;

    public NoteColumnScreen(SceneNavigator navigator, SqliteDatabase database, long bookId) {
        this.navigator = navigator;
        this.database = database;
        this.bookId = bookId;
        this.columnRepository = new SqliteNoteColumnRepository(database);
        build();
    }

    public BorderPane getView() {
        return view;
    }

    private void build() {
        book = new SqliteBookRepository(database).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));

        view.setStyle("-fx-font-family: '" + book.getFont().getFamilyName() + "';");

        Label title = new Label(book.getTitle() + " のノート項目設定");
        title.getStyleClass().add("screen-title");
        title.setStyle("-fx-text-fill: " + book.getTheme().getTextColorCode() + ";");

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showBookOptions(bookId));

        Button addButton = new Button("項目を追加");
        addButton.setOnAction(e -> onAddColumn());

        HBox header = new HBox(16, title, backButton, addButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16));
        header.setStyle("-fx-background-color: " + book.getTheme().getColorCode() + ";");
        HBox.setHgrow(title, Priority.ALWAYS);

        Label hint = new Label(
            "先頭の項目は常に必須で、ノート一覧の見出しや名前順の並び替えに使われます（削除できません）。"
                + "それ以外の項目は必須・任意を切り替えたり、削除したりできます。");
        hint.setWrapText(true);

        VBox center = new VBox(12, hint, listBox);
        center.setPadding(new Insets(16));

        view.setTop(header);
        view.setCenter(center);

        refreshList();
    }

    private void refreshList() {
        List<NoteColumn> columns = columnRepository.findByBookId(bookId);
        listBox.getChildren().clear();
        for (int i = 0; i < columns.size(); i++) {
            listBox.getChildren().add(buildColumnRow(columns.get(i), i == 0));
        }
    }

    private HBox buildColumnRow(NoteColumn column, boolean isPrimary) {
        Label nameLabel = new Label(column.getName());
        nameLabel.setPrefWidth(160);
        if (isPrimary) {
            nameLabel.setStyle("-fx-font-weight: bold;");
        }

        CheckBox requiredCheck = new CheckBox("必須");
        requiredCheck.setSelected(isPrimary || column.isRequired());
        requiredCheck.setDisable(isPrimary);
        requiredCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
            column.setRequired(newValue);
            columnRepository.update(column);
        });

        Button renameButton = new Button("名前を変更");
        renameButton.setOnAction(e -> onRenameColumn(column));

        Button deleteButton = new Button("削除");
        deleteButton.setStyle("-fx-text-fill: red;");
        deleteButton.setDisable(isPrimary);
        deleteButton.setOnAction(e -> onDeleteColumn(column));

        HBox row = new HBox(12, nameLabel, requiredCheck, renameButton, deleteButton);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    private void onAddColumn() {
        promptValidColumnName("項目を追加", "").ifPresent(name -> {
            List<NoteColumn> existing = columnRepository.findByBookId(bookId);
            int nextSortOrder = existing.isEmpty() ? 0
                : existing.get(existing.size() - 1).getSortOrder() + 1;
            columnRepository.insert(new NoteColumn(0, null, bookId, name, false, nextSortOrder, null));
            refreshList();
        });
    }

    private void onRenameColumn(NoteColumn column) {
        promptValidColumnName("名前を変更", column.getName()).ifPresent(newName -> {
            column.setName(newName);
            columnRepository.update(column);
            refreshList();
        });
    }

    private void onDeleteColumn(NoteColumn column) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
            "「" + column.getName() + "」を削除しますか？（この項目に入力されていた内容も削除されます）",
            ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.YES) {
                columnRepository.delete(column.getId());
                refreshList();
            }
        });
    }

    private Optional<String> promptValidColumnName(String dialogTitle, String initialValue) {
        String current = initialValue;
        while (true) {
            TextInputDialog dialog = new TextInputDialog(current);
            dialog.setTitle(dialogTitle);
            dialog.setHeaderText(null);
            dialog.setContentText("項目名:");
            Optional<String> result = dialog.showAndWait();
            if (result.isEmpty()) {
                return Optional.empty();
            }
            String trimmed = result.get().trim();
            if (NameValidator.isValid(trimmed) && !trimmed.isEmpty()) {
                return Optional.of(trimmed);
            }
            current = result.get();
            showError("項目名は英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースのみ使用でき、"
                + "空にはできません");
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
