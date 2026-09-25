package com.mydictionary.desktop.screen;

import com.mydictionary.core.markdown.MarkdownRenderer;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.NoteColumns;
import com.mydictionary.core.model.Tag;
import com.mydictionary.desktop.MainApp;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteNoteColumnRepository;
import com.mydictionary.desktop.db.SqliteNoteRepository;
import com.mydictionary.desktop.db.SqliteTagRepository;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ノート画面。項目名・読み・英訳・タグは上部に固定表示し、本文（Markdown）だけをスクロールさせる。
 * 本文中の注釈は画面下部にせり上がる注釈ウィンドウで、画像はズームオーバーレイで表示する。
 * このJDK環境にはWebViewとJavaを橋渡しするjdk.jsobjectモジュールが存在しないため、
 * MarkdownRendererが出力するURLフラグメントリンク（#footnote:.. / #image:.. / #note:..）を
 * WebEngineのlocationプロパティで検知する方式でクリックを検出している。
 */
public class NoteScreen {
    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final long bookId;
    private final long noteId;

    private final StackPane view = new StackPane();
    private final WebView webView = new WebView();
    private final Label footnoteLabel = new Label();
    private final VBox footnotePane = new VBox(8);
    private final Region scrim = new Region();
    private final StackPane imageOverlay = new StackPane();
    private final ImageView zoomImageView = new ImageView();

    private Book book;
    private Note note;
    private List<NoteColumn> columns;

    public NoteScreen(SceneNavigator navigator, SqliteDatabase database, long bookId, long noteId) {
        this.navigator = navigator;
        this.database = database;
        this.bookId = bookId;
        this.noteId = noteId;
        build();
    }

    public StackPane getView() {
        return view;
    }

    private void build() {
        book = new SqliteBookRepository(database).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));
        note = new SqliteNoteRepository(database).findById(noteId)
            .orElseThrow(() -> new IllegalStateException("ノートが見つかりません: " + noteId));
        columns = new SqliteNoteColumnRepository(database).findByBookId(bookId);

        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-font-family: '" + book.getFont().getFamilyName() + "';");
        mainLayout.setTop(buildHeader());
        mainLayout.setCenter(buildWebView());

        buildScrim();
        buildFootnotePane();
        buildImageOverlay();

        view.getChildren().addAll(mainLayout, scrim, footnotePane, imageOverlay);
        StackPane.setAlignment(footnotePane, Pos.BOTTOM_CENTER);
    }

    private VBox buildHeader() {
        String textColor = book.getTheme().getTextColorCode();

        Label titleLabel = new Label(NoteColumns.primaryValue(note, columns));
        titleLabel.getStyleClass().add("screen-title");
        titleLabel.setStyle("-fx-text-fill: " + textColor + ";");

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showBook(bookId));

        Button editButton = new Button("ノートを編集");
        editButton.setOnAction(e -> navigator.showNoteEdit(bookId, noteId));

        Button deleteButton = new Button("ノートを削除");
        deleteButton.setStyle("-fx-text-fill: red;");
        deleteButton.setOnAction(e -> onDeleteNote());

        HBox topRow = new HBox(16, titleLabel, backButton, editButton, deleteButton);
        topRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        VBox header = new VBox(6, topRow);

        // 先頭（必須・primary）カラムは見出しとして表示済みなので、残りのカラムを
        // 値が入力されているものだけ「項目名: 値」の形で並べる。
        for (int i = 1; i < columns.size(); i++) {
            NoteColumn column = columns.get(i);
            String value = note.getFieldValue(column.getId());
            if (value == null || value.isBlank()) {
                continue;
            }
            Label fieldLabel = new Label(column.getName() + ": " + value);
            fieldLabel.setStyle("-fx-text-fill: " + textColor + ";");
            header.getChildren().add(fieldLabel);
        }

        List<Long> tagIds = note.getTagIds();
        if (!tagIds.isEmpty()) {
            Map<Long, Tag> tagById = new SqliteTagRepository(database).findByBookId(bookId).stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag));
            FlowPane tagsRow = new FlowPane(8, 8);
            for (Long tagId : tagIds) {
                Label chip = new Label(Tag.fullPath(tagId, tagById));
                chip.setPadding(new Insets(2, 8, 2, 8));
                chip.setStyle("-fx-background-color: white; -fx-background-radius: 12;");
                tagsRow.getChildren().add(chip);
            }
            header.getChildren().add(tagsRow);
        }

        header.setPadding(new Insets(16));
        header.setStyle("-fx-background-color: " + book.getTheme().getColorCode() + ";");
        return header;
    }

    private WebView buildWebView() {
        WebEngine engine = webView.getEngine();
        engine.locationProperty().addListener((obs, oldLocation, newLocation) -> handleLocationChange(newLocation));

        String bodyHtml = new MarkdownRenderer().render(note.getBody());
        String document = buildHtmlDocument(bodyHtml);

        try {
            Path previewFile = MainApp.IMAGE_DIR.resolve("_note_preview.html");
            Files.writeString(previewFile, document, StandardCharsets.UTF_8);
            engine.load(previewFile.toUri().toString());
        } catch (IOException e) {
            engine.loadContent(document);
        }

        return webView;
    }

    private String buildHtmlDocument(String bodyHtml) {
        String bodyAppearance = NoteFormWidgets.buildBodyStyleCss(
            note.getBackgroundTheme(), note.getBackgroundImageFileName(), note.getTextColor());
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>"
            + "body{font-family:'" + book.getFont().getFamilyName() + "', sans-serif; font-size:"
            + book.getFontSizePt() + "pt; padding:16px; line-height:1.7;"
            + bodyAppearance + "}"
            + "table{border-collapse:collapse;} td,th{border:1px solid #999;padding:4px 8px;}"
            + "blockquote{border-left:4px solid #999;margin:8px 0;padding:4px 12px;color:#555;}"
            + "pre{background:#f4f4f4;padding:8px;overflow-x:auto;}"
            + "rt{font-size:0.6em;}"
            + "img{max-width:100%;display:block;margin:8px 0;cursor:zoom-in;}"
            + "a.note-link{color:#2a6ebb;text-decoration:underline;}"
            + "a.footnote{color:#c0392b;text-decoration:none;}"
            + "h1,h2,h3,h4,h5,h6{font-weight:bold;margin:0.8em 0 0.3em;}"
            + "h1{font-size:1.8em;} h2{font-size:1.5em;} h3{font-size:1.3em;}"
            + "h4{font-size:1.15em;} h5{font-size:1.05em;} h6{font-size:1em;}"
            + "</style>" + com.mydictionary.desktop.KatexAssets.headHtml()
            + "</head><body>" + bodyHtml + "</body></html>";
    }

    private void handleLocationChange(String location) {
        if (location == null) {
            return;
        }
        int hashIndex = location.indexOf('#');
        if (hashIndex < 0 || hashIndex == location.length() - 1) {
            return;
        }
        String fragment = location.substring(hashIndex + 1);

        if (fragment.startsWith("footnote:")) {
            String text = URLDecoder.decode(fragment.substring("footnote:".length()), StandardCharsets.UTF_8);
            showFootnote(text);
        } else if (fragment.startsWith("image:")) {
            String rawPath = URLDecoder.decode(fragment.substring("image:".length()), StandardCharsets.UTF_8);
            showImageZoom(rawPath);
        } else if (fragment.startsWith("note:")) {
            try {
                long targetNoteId = Long.parseLong(fragment.substring("note:".length()));
                navigator.showNote(bookId, targetNoteId);
                return;
            } catch (NumberFormatException ignored) {
                // 不正なノートIDは無視する
            }
        }

        // 同じ注釈/画像/リンクを連続でクリックしても再度検知できるよう、フラグメントをリセットする
        webView.getEngine().executeScript("history.replaceState(null, '', location.pathname)");
    }

    private void buildScrim() {
        scrim.setVisible(false);
        scrim.setMouseTransparent(true);
        scrim.setOnMouseClicked(e -> hideFootnote());
    }

    private void buildFootnotePane() {
        footnoteLabel.setWrapText(true);

        Label header = new Label("注釈");
        header.setStyle("-fx-font-weight: bold;");

        footnotePane.getChildren().addAll(header, footnoteLabel);
        footnotePane.setPadding(new Insets(16));
        footnotePane.setMaxWidth(Double.MAX_VALUE);
        footnotePane.setMaxHeight(Region.USE_PREF_SIZE);
        footnotePane.setStyle("-fx-background-color: white; -fx-border-color: #999999; -fx-border-width: 1 0 0 0;");
        footnotePane.setTranslateY(300);
        footnotePane.setVisible(false);
    }

    private void showFootnote(String text) {
        footnoteLabel.setText(text);
        scrim.setVisible(true);
        scrim.setMouseTransparent(false);
        footnotePane.setVisible(true);

        TranslateTransition transition = new TranslateTransition(Duration.millis(200), footnotePane);
        transition.setToY(0);
        transition.play();
    }

    private void hideFootnote() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(200), footnotePane);
        transition.setToY(300);
        transition.setOnFinished(e -> footnotePane.setVisible(false));
        transition.play();

        scrim.setVisible(false);
        scrim.setMouseTransparent(true);
    }

    private void buildImageOverlay() {
        imageOverlay.setStyle("-fx-background-color: rgba(0,0,0,0.85);");
        imageOverlay.setVisible(false);
        zoomImageView.setPreserveRatio(true);
        zoomImageView.fitWidthProperty().bind(view.widthProperty().multiply(0.9));
        zoomImageView.fitHeightProperty().bind(view.heightProperty().multiply(0.9));
        imageOverlay.getChildren().add(zoomImageView);
        imageOverlay.setOnMouseClicked(e -> imageOverlay.setVisible(false));
    }

    private void showImageZoom(String rawPath) {
        try {
            zoomImageView.setImage(resolveImage(rawPath));
            imageOverlay.setVisible(true);
        } catch (RuntimeException e) {
            showInfo("画像の読み込みに失敗しました: " + rawPath);
        }
    }

    private Image resolveImage(String rawPath) {
        if (rawPath.startsWith("http://") || rawPath.startsWith("https://") || rawPath.startsWith("file:")) {
            return new Image(rawPath);
        }
        Path resolved = MainApp.IMAGE_DIR.resolve(rawPath);
        return new Image(resolved.toUri().toString());
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void onDeleteNote() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
            "このノートを削除しますか？（一度削除したノートは復元できません）", ButtonType.YES, ButtonType.NO);
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(result -> {
            if (result == ButtonType.YES) {
                new SqliteNoteRepository(database).delete(noteId);
                navigator.showBook(bookId);
            }
        });
    }
}
