package com.mydictionary.desktop.screen;

import com.mydictionary.core.markdown.MarkdownRenderer;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.NoteTextColor;
import com.mydictionary.core.model.Tag;
import com.mydictionary.desktop.MainApp;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteNoteColumnRepository;
import com.mydictionary.desktop.db.SqliteNoteRepository;
import com.mydictionary.desktop.db.SqliteTagRepository;
import com.mydictionary.desktop.editor.RichNoteEditor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ノート作成画面。noteIdがnullなら新規作成、値があれば既存ノートの編集
 * （仕様上、編集画面は作成画面に既存データを入力した状態と同じ）。
 */
public class NoteCreateScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final long bookId;
    private final Long noteId;

    private final SqliteNoteRepository noteRepository;
    private final SqliteTagRepository tagRepository;
    private final SqliteNoteColumnRepository columnRepository;

    private final VBox view = new VBox(12);
    private final List<Tag> orderedTags = new ArrayList<>();
    private final Map<Long, Integer> tagDepthById = new HashMap<>();
    private final Map<Long, Tag> tagById = new HashMap<>();
    private final List<Long> selectedTagIds = new ArrayList<>();

    private Book book;
    private Note existingNote;
    private List<NoteColumn> columns;
    private final Map<Long, TextField> columnFields = new LinkedHashMap<>();

    private RichNoteEditor richEditor;
    /** 「Markdown編集」中の本文欄。通常は隠れており、Markdown編集中だけ表示して本文の原本になる。 */
    private TextArea bodyArea;
    private boolean markdownMode;
    private WebView previewWebView;
    private Label errorLabel;
    private FlowPane selectedTagsPane;
    private ComboBox<Tag> tagComboBox;
    private Button addTagButton;
    private ComboBox<BookTheme> backgroundThemeCombo;
    private ComboBox<NoteTextColor> textColorCombo;
    private Label backgroundImageLabel;
    private String backgroundImageFileName;
    private VBox editRoot;
    private VBox previewRoot;

    public NoteCreateScreen(SceneNavigator navigator, SqliteDatabase database, long bookId, Long noteId) {
        this.navigator = navigator;
        this.database = database;
        this.bookId = bookId;
        this.noteId = noteId;
        this.noteRepository = new SqliteNoteRepository(database);
        this.tagRepository = new SqliteTagRepository(database);
        this.columnRepository = new SqliteNoteColumnRepository(database);
        build();
    }

    public VBox getView() {
        return view;
    }

    private void build() {
        book = new SqliteBookRepository(database).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));
        columns = columnRepository.findByBookId(bookId);
        if (noteId != null) {
            existingNote = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalStateException("ノートが見つかりません: " + noteId));
        }

        view.setStyle("-fx-font-family: '" + book.getEffectiveFontFamily() + "';");
        view.setPadding(new Insets(16));

        editRoot = buildEditRoot();
        previewRoot = buildPreviewRoot();
        previewRoot.setVisible(false);
        previewRoot.setManaged(false);

        StackPane screens = new StackPane(editRoot, previewRoot);
        VBox.setVgrow(screens, Priority.ALWAYS);

        view.getChildren().add(screens);
    }

    /**
     * 編集画面（左: 項目名・読み・タグ・背景設定などのフォーム、右: 本文入力欄）を組み立てる。
     * 各種フィールドを増やすたびに左右どちらも手狭になってきたため、左をフォーム専用の
     * スクロール可能な縦並びパネル、右を本文入力欄専用の広い領域に分けている。
     */
    private VBox buildEditRoot() {
        Label title = new Label(existingNote == null ? "ノートを作成" : "ノートを編集");
        title.getStyleClass().add("screen-title");
        title.setStyle("-fx-text-fill: " + book.getTheme().getTextColorCode() + ";");

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> onBack());

        HBox header = new HBox(16, title, backButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8));
        header.setStyle("-fx-background-color: " + book.getTheme().getColorCode() + ";");

        List<VBox> columnFieldGroups = new ArrayList<>();
        for (NoteColumn column : columns) {
            String initialValue = existingNote != null ? existingNote.getFieldValue(column.getId()) : "";
            TextField field = new TextField(initialValue);
            columnFields.put(column.getId(), field);
            String label = column.getName() + (column.isRequired() ? "" : "（オプション）");
            columnFieldGroups.add(fieldGroup(label, field));
        }

        VBox tagsPane = buildTagSelector();

        backgroundThemeCombo = BookFormWidgets.createThemeCombo();
        backgroundThemeCombo.setValue(existingNote != null ? existingNote.getBackgroundTheme() : BookTheme.WHITE);

        backgroundImageFileName = existingNote != null ? existingNote.getBackgroundImageFileName() : null;
        backgroundImageLabel = new Label();
        updateBackgroundImageLabel();

        Button browseBackgroundImageButton = new Button("参照...");
        browseBackgroundImageButton.setOnAction(e -> onBrowseBackgroundImage());
        Button clearBackgroundImageButton = new Button("画像をクリア");
        clearBackgroundImageButton.setOnAction(e -> onClearBackgroundImage());
        HBox backgroundImageRow = new HBox(8, browseBackgroundImageButton, clearBackgroundImageButton,
            backgroundImageLabel);
        backgroundImageRow.setAlignment(Pos.CENTER_LEFT);

        textColorCombo = NoteFormWidgets.createTextColorCombo();
        textColorCombo.setValue(existingNote != null ? existingNote.getTextColor() : NoteTextColor.BLACK);

        VBox leftPanel = new VBox(16);
        leftPanel.getChildren().addAll(columnFieldGroups);
        leftPanel.getChildren().addAll(
            fieldGroup("タグ（オプション）", tagsPane),
            fieldGroup("本文の背景色", backgroundThemeCombo),
            fieldGroup("本文の背景画像（オプション）", backgroundImageRow),
            fieldGroup("本文の標準文字色", textColorCombo));
        leftPanel.setPadding(new Insets(4, 16, 4, 0));

        ScrollPane leftScroll = new ScrollPane(leftPanel);
        leftScroll.setFitToWidth(true);
        leftScroll.setPrefWidth(320);
        leftScroll.setMinWidth(280);
        leftScroll.setStyle("-fx-background-color: transparent;");

        String initialBody = existingNote != null ? existingNote.getBody() : "";

        // 通常は、記法を見せずに仕上がりの見た目のまま編集するエディタを表示する。
        // 「Markdown編集」を押している間だけ、同じ本文をMarkdownのまま編集するテキスト欄に切り替える。
        richEditor = new RichNoteEditor(
            NoteFormWidgets.editorDocumentCss(book, ""),
            com.mydictionary.desktop.KatexAssets.headHtml(),
            MainApp.IMAGE_DIR.resolve("_note_editor.html"));
        richEditor.setMarkdown(initialBody);
        applyEditorAppearance();
        backgroundThemeCombo.valueProperty().addListener((obs, o, n) -> applyEditorAppearance());
        textColorCombo.valueProperty().addListener((obs, o, n) -> applyEditorAppearance());

        bodyArea = new TextArea(initialBody);
        bodyArea.setWrapText(true);
        bodyArea.setStyle("-fx-font-family: monospace;");
        bodyArea.setVisible(false);
        bodyArea.setManaged(false);

        StackPane bodyStack = new StackPane(richEditor.getNode(), bodyArea);
        bodyStack.setStyle("-fx-border-color: #b0b0b0; -fx-border-width: 1;");
        VBox.setVgrow(bodyStack, Priority.ALWAYS);

        NoteFormatToolbar formatToolbar = new NoteFormatToolbar(richEditor,
            () -> {
                List<String> families = new ArrayList<>(java.util.Arrays.stream(
                    com.mydictionary.core.model.BookFont.values())
                    .map(com.mydictionary.core.model.BookFont::getFamilyName).toList());
                families.addAll(com.mydictionary.desktop.font.FontLibrary.families());
                return families;
            },
            this::onInsertImage, this::showPreview, this::onMarkdownModeChanged,
            book.getEffectiveFontFamily(), book.getFontSizePt());

        VBox rightPanel = new VBox(8,
            new Label("本文（ツールバーで書式を設定できます。「Markdown編集」でMarkdownの記法を直接編集できます）"),
            formatToolbar.getNode(), bodyStack);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        HBox splitRow = new HBox(16, leftScroll, rightPanel);
        VBox.setVgrow(splitRow, Priority.ALWAYS);

        errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        Button saveButton = new Button("ノートを保存");
        saveButton.setOnAction(e -> onSave());

        VBox root = new VBox(12, header, splitRow, errorLabel, saveButton);
        VBox.setVgrow(splitRow, Priority.ALWAYS);
        return root;
    }

    /** ラベルを上、入力コントロールを下に積んだ縦長のフォーム項目を作る（左パネルが狭いため）。 */
    private VBox fieldGroup(String labelText, Node control) {
        Label label = new Label(labelText);
        label.setStyle("-fx-font-weight: bold;");
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return new VBox(4, label, control);
    }

    /**
     * プレビュー画面（編集不可、本文の最終的な見た目をWebViewでそのまま確認する）を組み立てる。
     * 上部には「戻る」（編集画面に戻る）と「ノートを保存」のみを置く。
     */
    private VBox buildPreviewRoot() {
        Button previewBackButton = new Button("戻る");
        previewBackButton.setOnAction(e -> hidePreview());

        Button previewSaveButton = new Button("ノートを保存");
        previewSaveButton.setOnAction(e -> onSave());

        HBox previewToolbar = new HBox(16, previewBackButton, previewSaveButton);
        previewToolbar.setAlignment(Pos.CENTER_LEFT);
        previewToolbar.setPadding(new Insets(8));
        previewToolbar.setStyle("-fx-background-color: " + book.getTheme().getColorCode() + ";");

        previewWebView = new WebView();
        VBox.setVgrow(previewWebView, Priority.ALWAYS);

        return new VBox(previewToolbar, previewWebView);
    }

    private void showPreview() {
        updatePreview();
        editRoot.setVisible(false);
        editRoot.setManaged(false);
        previewRoot.setVisible(true);
        previewRoot.setManaged(true);
    }

    private void hidePreview() {
        previewRoot.setVisible(false);
        previewRoot.setManaged(false);
        editRoot.setVisible(true);
        editRoot.setManaged(true);
    }

    private VBox buildTagSelector() {
        List<Tag> flatTags = tagRepository.findByBookId(bookId);
        if (flatTags.isEmpty()) {
            return new VBox(new Label("（このブックにはタグがありません）"));
        }

        collectOrdered(Tag.buildTree(flatTags), 0);
        if (existingNote != null) {
            selectedTagIds.addAll(existingNote.getTagIds());
        }

        selectedTagsPane = new FlowPane(8, 8);

        tagComboBox = new ComboBox<>();
        tagComboBox.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(Tag tag, boolean empty) {
                super.updateItem(tag, empty);
                setText(empty || tag == null ? null : tagLabel(tag));
            }
        });
        tagComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Tag tag, boolean empty) {
                super.updateItem(tag, empty);
                setText(empty || tag == null ? "タグを選択" : tagLabel(tag));
            }
        });

        addTagButton = new Button("タグを追加");
        addTagButton.setOnAction(e -> onAddTag());

        refreshTagSelectorState();

        HBox addRow = new HBox(8, tagComboBox, addTagButton);
        addRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(8, selectedTagsPane, addRow);
    }

    private void collectOrdered(List<Tag> nodes, int depth) {
        for (Tag tag : nodes) {
            orderedTags.add(tag);
            tagDepthById.put(tag.getId(), depth);
            tagById.put(tag.getId(), tag);
            collectOrdered(tag.getChildren(), depth + 1);
        }
    }

    private String tagLabel(Tag tag) {
        return "　".repeat(tagDepthById.getOrDefault(tag.getId(), 0)) + tag.getName();
    }

    private void onAddTag() {
        Tag selected = tagComboBox.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        selectedTagIds.add(selected.getId());
        refreshTagSelectorState();
    }

    private void refreshTagSelectorState() {
        List<Tag> available = new ArrayList<>();
        for (Tag tag : orderedTags) {
            if (!selectedTagIds.contains(tag.getId())) {
                available.add(tag);
            }
        }
        tagComboBox.getItems().setAll(available);
        tagComboBox.getSelectionModel().clearSelection();
        boolean hasAvailable = !available.isEmpty();
        tagComboBox.setDisable(!hasAvailable);
        addTagButton.setDisable(!hasAvailable);

        selectedTagsPane.getChildren().clear();
        for (Long tagId : selectedTagIds) {
            Tag tag = tagById.get(tagId);
            if (tag != null) {
                selectedTagsPane.getChildren().add(buildTagChip(tag));
            }
        }
    }

    private HBox buildTagChip(Tag tag) {
        Label label = new Label(Tag.fullPath(tag.getId(), tagById));
        Button removeButton = new Button("×");
        removeButton.setOnAction(e -> {
            selectedTagIds.remove(tag.getId());
            refreshTagSelectorState();
        });
        HBox chip = new HBox(4, label, removeButton);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setStyle("-fx-background-color: #e0e0e0; -fx-background-radius: 12; -fx-padding: 4 8 4 8;");
        return chip;
    }

    private void onInsertImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("挿入する画像を選択");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "画像ファイル", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File selected = chooser.showOpenDialog(view.getScene() != null ? view.getScene().getWindow() : null);
        if (selected == null) {
            return;
        }

        try {
            Files.createDirectories(MainApp.IMAGE_DIR);
            String fileName = System.currentTimeMillis() + "_" + selected.getName();
            Path targetFile = MainApp.IMAGE_DIR.resolve(fileName);
            Files.copy(selected.toPath(), targetFile);

            if (markdownMode) {
                int caretPosition = bodyArea.getCaretPosition();
                String snippet = "![" + selected.getName() + "](" + fileName + ")";
                bodyArea.insertText(caretPosition, snippet);
            } else {
                richEditor.insertImage(fileName, selected.getName());
            }
        } catch (IOException e) {
            showError("画像の保存に失敗しました: " + e.getMessage());
        }
    }

    private void onBrowseBackgroundImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("背景画像を選択");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            "画像ファイル", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"));
        File selected = chooser.showOpenDialog(view.getScene() != null ? view.getScene().getWindow() : null);
        if (selected == null) {
            return;
        }
        try {
            // 拡張子だけでは実体の画像形式が分からない（例: 実体はWebPなのに.png拡張子のファイルが
            // 存在する）ため、ファイルをそのままコピーせず、必ずImageIOでデコードしてから
            // PNGとして保存し直す。これにより保存後のファイルは常に本物のPNGになり、
            // デスクトップ版WebView（WebP非対応）でも確実に表示できる。
            BufferedImage image = ImageIO.read(selected);
            if (image == null) {
                showError("この画像ファイルは読み込めませんでした（対応していない形式の可能性があります）");
                return;
            }
            Files.createDirectories(MainApp.IMAGE_DIR);
            String fileName = System.currentTimeMillis() + "_" + stripExtension(selected.getName()) + ".png";
            Path targetFile = MainApp.IMAGE_DIR.resolve(fileName);
            ImageIO.write(image, "png", targetFile.toFile());
            backgroundImageFileName = fileName;
            updateBackgroundImageLabel();
            applyEditorAppearance();
        } catch (IOException e) {
            showError("背景画像の保存に失敗しました: " + e.getMessage());
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private void onClearBackgroundImage() {
        backgroundImageFileName = null;
        updateBackgroundImageLabel();
        applyEditorAppearance();
    }

    private void updateBackgroundImageLabel() {
        backgroundImageLabel.setText(backgroundImageFileName == null ? "（未設定）" : backgroundImageFileName);
    }

    /** 現在の本文（Markdown）。Markdown編集中はテキスト欄、通常はWYSIWYGエディタから得る。 */
    private String currentMarkdown() {
        return markdownMode ? bodyArea.getText() : richEditor.getMarkdown();
    }

    /** 「Markdown編集」の切替。切り替える側へ、いまの本文を引き継ぐ。 */
    private void onMarkdownModeChanged(boolean markdown) {
        if (markdown == markdownMode) {
            return;
        }
        if (markdown) {
            bodyArea.setText(richEditor.getMarkdown());
        } else {
            richEditor.setMarkdown(bodyArea.getText());
        }
        markdownMode = markdown;
        bodyArea.setVisible(markdown);
        bodyArea.setManaged(markdown);
        richEditor.getNode().setVisible(!markdown);
        richEditor.getNode().setManaged(!markdown);
        if (markdown) {
            bodyArea.requestFocus();
        } else {
            richEditor.requestFocus();
        }
    }

    /** 本文の背景色・背景画像・文字色の設定を、編集欄の見た目にも反映する。 */
    private void applyEditorAppearance() {
        if (richEditor == null || backgroundThemeCombo.getValue() == null || textColorCombo.getValue() == null) {
            return;
        }
        richEditor.setAppearance(NoteFormWidgets.buildBodyStyleCss(
            backgroundThemeCombo.getValue(), backgroundImageFileName, textColorCombo.getValue()));
    }

    private void updatePreview() {
        String bodyHtml = new MarkdownRenderer().render(currentMarkdown());
        String bodyAppearance = NoteFormWidgets.buildBodyStyleCss(
            backgroundThemeCombo.getValue(), backgroundImageFileName, textColorCombo.getValue());
        String document = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>"
            + NoteFormWidgets.editorDocumentCss(book, bodyAppearance)
            + "</style>" + com.mydictionary.desktop.KatexAssets.headHtml()
            + "</head><body>" + bodyHtml + "</body></html>";

        try {
            Path previewFile = MainApp.IMAGE_DIR.resolve("_note_edit_preview.html");
            Files.writeString(previewFile, document, StandardCharsets.UTF_8);
            previewWebView.getEngine().load(previewFile.toUri().toString());
        } catch (IOException e) {
            previewWebView.getEngine().loadContent(document);
        }
    }

    private void onSave() {
        Map<Long, String> fieldValues = new LinkedHashMap<>();
        List<String> missingRequiredNames = new ArrayList<>();
        for (NoteColumn column : columns) {
            String value = columnFields.get(column.getId()).getText();
            String trimmed = value == null ? "" : value.trim();
            if (column.isRequired() && trimmed.isEmpty()) {
                missingRequiredNames.add(column.getName());
            }
            fieldValues.put(column.getId(), trimmed);
        }
        if (!missingRequiredNames.isEmpty()) {
            // プレビュー画面にはエラー表示欄が無いため、保存に失敗したら編集画面に戻してから
            // エラーを表示する（プレビュー画面の「ノートを保存」から呼ばれた場合も想定）。
            hidePreview();
            errorLabel.setText(String.join("・", missingRequiredNames) + "は必須です");
            return;
        }

        List<Long> tagIdsToSave = new ArrayList<>(selectedTagIds);
        String body = currentMarkdown();

        if (existingNote != null) {
            existingNote.setFieldValues(fieldValues);
            existingNote.setBody(body);
            existingNote.setTagIds(tagIdsToSave);
            existingNote.setBackgroundTheme(backgroundThemeCombo.getValue());
            existingNote.setBackgroundImageFileName(backgroundImageFileName);
            existingNote.setTextColor(textColorCombo.getValue());
            noteRepository.update(existingNote);
            navigator.showNote(bookId, existingNote.getId());
        } else {
            Note newNote = new Note(0, null, bookId, body, Instant.now(), Instant.now());
            newNote.setFieldValues(fieldValues);
            newNote.setTagIds(tagIdsToSave);
            newNote.setBackgroundTheme(backgroundThemeCombo.getValue());
            newNote.setBackgroundImageFileName(backgroundImageFileName);
            newNote.setTextColor(textColorCombo.getValue());
            noteRepository.insert(newNote);
            navigator.showBook(bookId);
        }
    }

    private void onBack() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
            "編集中の内容は破棄されます。戻りますか？", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            if (existingNote != null) {
                navigator.showNote(bookId, existingNote.getId());
            } else {
                navigator.showBook(bookId);
            }
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
