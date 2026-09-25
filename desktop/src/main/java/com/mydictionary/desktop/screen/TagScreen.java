package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.Tag;
import com.mydictionary.core.tagimport.TagImportParser;
import com.mydictionary.core.validation.NameValidator;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteTagRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** タグ画面。ブックに紐づく入れ子タグの確認・追加・編集・削除を行う。 */
public class TagScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final long bookId;
    private final SqliteTagRepository tagRepository;

    private final BorderPane view = new BorderPane();
    private final TreeView<Tag> treeView = new TreeView<>();

    private Book book;

    public TagScreen(SceneNavigator navigator, SqliteDatabase database, long bookId) {
        this.navigator = navigator;
        this.database = database;
        this.bookId = bookId;
        this.tagRepository = new SqliteTagRepository(database);
        build();
    }

    public BorderPane getView() {
        return view;
    }

    private void build() {
        book = new SqliteBookRepository(database).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));

        view.setStyle("-fx-font-family: '" + book.getFont().getFamilyName() + "';");

        Label title = new Label(book.getTitle() + " のタグ");
        title.getStyleClass().add("screen-title");
        title.setStyle("-fx-text-fill: " + book.getTheme().getTextColorCode() + ";");

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showBookOptions(bookId));

        Button addRootTagButton = new Button("タグを追加");
        addRootTagButton.setOnAction(e -> onAddTag(null));

        Button importButton = new Button("インポート");
        importButton.setOnAction(e -> onImportTags());

        HBox header = new HBox(16, title, backButton, addRootTagButton, importButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16));
        header.setStyle("-fx-background-color: " + book.getTheme().getColorCode() + ";");
        HBox.setHgrow(title, Priority.ALWAYS);

        treeView.setShowRoot(false);
        treeView.setCellFactory(tv -> new TagTreeCell());

        VBox center = new VBox(treeView);
        VBox.setVgrow(treeView, Priority.ALWAYS);
        center.setPadding(new Insets(16));

        view.setTop(header);
        view.setCenter(center);

        refreshTree();
    }

    private void refreshTree() {
        List<Tag> flatTags = tagRepository.findByBookId(bookId);
        List<Tag> roots = Tag.buildTree(flatTags);

        TreeItem<Tag> invisibleRoot = new TreeItem<>();
        for (Tag root : roots) {
            invisibleRoot.getChildren().add(buildTreeItem(root));
        }
        treeView.setRoot(invisibleRoot);
    }

    private TreeItem<Tag> buildTreeItem(Tag tag) {
        TreeItem<Tag> item = new TreeItem<>(tag);
        item.setExpanded(true);
        for (Tag child : tag.getChildren()) {
            item.getChildren().add(buildTreeItem(child));
        }
        return item;
    }

    private void onAddTag(Tag parent) {
        String dialogTitle = parent == null ? "タグを追加" : "「" + parent.getName() + "」の子タグを追加";
        promptValidTagName(dialogTitle, "").ifPresent(name -> {
            tagRepository.insert(new Tag(0, null, bookId, name, parent == null ? null : parent.getId(),
                Instant.now()));
            refreshTree();
        });
    }

    /**
     * MarkdownファイルまたはJSONファイルから入れ子タグ構造を一括インポートする（デスクトップ版のみの機能）。
     * 拡張子が".json"のファイルはJSONとして、それ以外はMarkdown（インデント付き箇条書き）として解析する。
     * 既存タグと同名・同じ親のタグは重複作成せず既存のものを使うため、同じファイルを再度取り込んでも安全。
     */
    private void onImportTags() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("タグをインポート");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Markdown/JSONファイル", "*.md", "*.markdown", "*.json"),
            new FileChooser.ExtensionFilter("すべてのファイル", "*.*"));
        File file = fileChooser.showOpenDialog(view.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            boolean isJson = file.getName().toLowerCase(Locale.ROOT).endsWith(".json");
            List<TagImportParser.ImportedTag> roots = isJson
                ? TagImportParser.parseJson(content)
                : TagImportParser.parseMarkdown(content);

            List<Tag> existingFlat = tagRepository.findByBookId(bookId);
            int importedCount = 0;
            for (TagImportParser.ImportedTag root : roots) {
                importedCount += mergeImportedTag(root, null, existingFlat);
            }
            refreshTree();
            showInfo(importedCount + "件のタグを新規作成しました（既存の同名タグは重複作成していません）。");
        } catch (IOException ex) {
            showError("ファイルの読み込みに失敗しました: " + ex.getMessage());
        } catch (TagImportParser.ParseException ex) {
            showError("ファイルの解析に失敗しました。\n" + ex.getMessage());
        }
    }

    /**
     * インポートされたタグ1件を、同名・同じ親の既存タグがあればそれを再利用し、なければ新規作成しながら
     * 子タグへ再帰する。existingFlatは新規作成したタグも都度追加し、以降の重複判定に使う。
     * 戻り値は新規作成したタグ数（既存タグを再利用した分は含まない）。
     */
    private int mergeImportedTag(TagImportParser.ImportedTag imported, Long parentTagId, List<Tag> existingFlat) {
        Tag existing = findExistingTag(existingFlat, imported.getName(), parentTagId);
        long tagId;
        int createdCount;
        if (existing != null) {
            tagId = existing.getId();
            createdCount = 0;
        } else {
            Tag created = tagRepository.insert(new Tag(0, null, bookId, imported.getName(), parentTagId, Instant.now()));
            existingFlat.add(created);
            tagId = created.getId();
            createdCount = 1;
        }
        for (TagImportParser.ImportedTag child : imported.getChildren()) {
            createdCount += mergeImportedTag(child, tagId, existingFlat);
        }
        return createdCount;
    }

    private Tag findExistingTag(List<Tag> flatTags, String name, Long parentTagId) {
        for (Tag tag : flatTags) {
            boolean sameParent = parentTagId == null
                ? tag.getParentTagId() == null
                : parentTagId.equals(tag.getParentTagId());
            if (sameParent && tag.getName().equals(name)) {
                return tag;
            }
        }
        return null;
    }

    private void onRenameTag(Tag tag) {
        if (tagRepository.isUsedByAnyNote(tag.getId()) && !confirmEditOrDelete()) {
            return;
        }
        promptValidTagName("タグ名を編集", tag.getName()).ifPresent(newName -> {
            tag.setName(newName);
            tagRepository.update(tag);
            refreshTree();
        });
    }

    private void onDeleteTag(Tag tag) {
        if (isTagOrDescendantsUsed(tag) && !confirmEditOrDelete()) {
            return;
        }
        tagRepository.delete(tag.getId());
        refreshTree();
    }

    private boolean isTagOrDescendantsUsed(Tag tag) {
        if (tagRepository.isUsedByAnyNote(tag.getId())) {
            return true;
        }
        for (Tag child : tag.getChildren()) {
            if (isTagOrDescendantsUsed(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean confirmEditOrDelete() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "このタグを編集/削除しますか？", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    private Optional<String> promptValidTagName(String dialogTitle, String initialValue) {
        String current = initialValue;
        while (true) {
            TextInputDialog dialog = new TextInputDialog(current);
            dialog.setTitle(dialogTitle);
            dialog.setHeaderText(null);
            dialog.setContentText("タグ名:");
            Optional<String> result = dialog.showAndWait();
            if (result.isEmpty()) {
                return Optional.empty();
            }
            String trimmed = result.get().trim();
            if (NameValidator.isValid(trimmed)) {
                return Optional.of(trimmed);
            }
            current = result.get();
            showError("タグ名は英数字・平仮名・カタカナ・漢字・記号(-.+/*_!?()[]・など)・半角スペースのみ使用できます");
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    /** タグ1件の行を「名称 + 子タグ追加/編集/削除ボタン」で表示するセル。 */
    private final class TagTreeCell extends TreeCell<Tag> {
        @Override
        protected void updateItem(Tag tag, boolean empty) {
            super.updateItem(tag, empty);
            if (empty || tag == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label nameLabel = new Label(tag.getName());

            Button addChildButton = new Button("＋子タグ");
            addChildButton.setOnAction(e -> onAddTag(tag));

            Button renameButton = new Button("編集");
            renameButton.setOnAction(e -> onRenameTag(tag));

            Button deleteButton = new Button("削除");
            deleteButton.setOnAction(e -> onDeleteTag(tag));

            HBox row = new HBox(8, nameLabel, addChildButton, renameButton, deleteButton);
            row.setAlignment(Pos.CENTER_LEFT);

            setText(null);
            setGraphic(row);
        }
    }
}
