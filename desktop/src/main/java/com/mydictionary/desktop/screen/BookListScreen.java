package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.repository.BookSortMode;
import com.mydictionary.desktop.DesktopSettings;
import com.mydictionary.desktop.db.SqliteBookRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.db.SqliteShelfRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** ブック一覧画面。1つのシェルフ（本棚）に属するブックを表示する。 */
public class BookListScreen {
    /** カード内のブックカバー表示・タイトル表示の幅（枠1px込みでBookCover.WIDTH_PXより2px大きい）。 */
    private static final double CARD_WIDTH = BookCover.WIDTH_PX + 2;

    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final long shelfId;
    private final BorderPane view = new BorderPane();
    private final FlowPane grid = new FlowPane(16, 16);
    private final Label countLabel = new Label();
    private Shelf shelf;
    private List<Book> allBooks;
    private BookSortMode sortMode;

    public BookListScreen(SceneNavigator navigator, SqliteDatabase database, long shelfId) {
        this.navigator = navigator;
        this.database = database;
        this.shelfId = shelfId;
        build();
    }

    public BorderPane getView() {
        return view;
    }

    private void build() {
        shelf = new SqliteShelfRepository(database).findById(shelfId)
            .orElseThrow(() -> new IllegalStateException("シェルフが見つかりません: " + shelfId));
        allBooks = new SqliteBookRepository(database).findByShelfId(shelfId);
        sortMode = loadSortMode();

        Label title = new Label(shelf.getName());
        title.getStyleClass().add("screen-title");

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showShelfList());

        ComboBox<BookSortMode> sortModeCombo = new ComboBox<>();
        sortModeCombo.getItems().addAll(BookSortMode.MANUAL, BookSortMode.TITLE, BookSortMode.CREATED_AT);
        sortModeCombo.setValue(sortMode);
        sortModeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(BookSortMode mode) {
                if (mode == null) {
                    return "";
                }
                return switch (mode) {
                    case MANUAL -> "手動";
                    case TITLE -> "名前順";
                    case CREATED_AT -> "作成日順";
                };
            }

            @Override
            public BookSortMode fromString(String s) {
                return null;
            }
        });
        sortModeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            sortMode = newValue == null ? BookSortMode.MANUAL : newValue;
            DesktopSettings.setBookSortMode(sortMode.name());
            refreshGrid();
        });

        Button createButton = new Button("ブックを作成");
        createButton.setOnAction(e -> navigator.showBookCreate(shelfId));

        Button settingsButton = new Button("設定");
        settingsButton.setOnAction(e -> navigator.showSettings());

        HBox header = new HBox(16, backButton, title, countLabel,
            new Label("並び順:"), sortModeCombo, createButton, settingsButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16));
        HBox.setHgrow(countLabel, Priority.ALWAYS);

        grid.setPadding(new Insets(16));
        refreshGrid();

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);

        view.setTop(header);
        view.setCenter(scrollPane);
    }

    private BookSortMode loadSortMode() {
        return DesktopSettings.getBookSortMode().map(name -> {
            try {
                return BookSortMode.valueOf(name);
            } catch (IllegalArgumentException e) {
                return BookSortMode.MANUAL;
            }
        }).orElse(BookSortMode.MANUAL);
    }

    /**
     * 並び順を反映してカード一覧を再構築し、件数表示も更新する。
     * ドラッグ&ドロップでの並び替えは「手動」のときだけ有効にする。
     */
    private void refreshGrid() {
        List<Book> shown = new ArrayList<>(allBooks);

        if (sortMode == BookSortMode.TITLE) {
            Collator collator = Collator.getInstance(Locale.JAPANESE);
            shown.sort(Comparator.comparing(Book::getTitle, collator));
        } else if (sortMode == BookSortMode.CREATED_AT) {
            shown.sort(Comparator.comparing(Book::getCreatedAt));
        }
        // MANUAL は findByShelfId() が sort_order 順で返しているのでそのまま。

        boolean dragEnabled = sortMode == BookSortMode.MANUAL;

        countLabel.setText(shown.size() + " 件"
            + (dragEnabled && shown.size() > 1 ? "（ドラッグで並び替え）" : ""));
        grid.getChildren().clear();
        for (Book book : shown) {
            grid.getChildren().add(createBookCard(book, dragEnabled));
        }
    }

    private VBox createBookCard(Book book, boolean dragEnabled) {
        Node cover = BookFormWidgets.createBorderedCoverView(book.getCover(),
            book.getTheme().getColorCode(), book.getTheme().getTextColorCode(),
            BookCover.WIDTH_PX, BookCover.HEIGHT_PX);

        // ブック名はカバーの中ではなく下に表示し、名前の長さでカードの横幅が変わらないよう、
        // カバーと同じ幅に固定して折り返す（長い場合は複数行になり、カードの高さだけが伸びる）。
        Label label = new Label(book.getTitle());
        label.setStyle("-fx-font-family: '" + book.getFont().getFamilyName() + "';");
        label.setWrapText(true);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.CENTER);
        label.setMinWidth(CARD_WIDTH);
        label.setPrefWidth(CARD_WIDTH);
        label.setMaxWidth(CARD_WIDTH);

        VBox card = new VBox(8, cover, label);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12));
        card.getStyleClass().add("book-card");

        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                navigator.showBook(book.getId());
            } else if (e.getButton() == MouseButton.SECONDARY) {
                navigator.showBookOptions(book.getId());
            }
        });

        if (dragEnabled) {
            enableDragReorder(card, book);
        }

        return card;
    }

    private void enableDragReorder(VBox card, Book book) {
        card.setOnDragDetected(e -> {
            Dragboard dragboard = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(book.getId()));
            dragboard.setContent(content);
            card.setOpacity(0.4);
            e.consume();
        });
        card.setOnDragOver(e -> {
            if (e.getGestureSource() != card && e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        card.setOnDragEntered(e -> {
            if (e.getGestureSource() != card && e.getDragboard().hasString()) {
                card.setStyle("-fx-border-color: #2a6ebb; -fx-border-width: 2;");
            }
        });
        card.setOnDragExited(e -> card.setStyle(""));
        card.setOnDragDropped(e -> {
            boolean success = false;
            if (e.getDragboard().hasString()) {
                reorderBook(Long.parseLong(e.getDragboard().getString()), book.getId());
                success = true;
            }
            e.setDropCompleted(success);
            e.consume();
        });
        card.setOnDragDone(e -> card.setOpacity(1.0));
    }

    private void reorderBook(long sourceId, long targetId) {
        if (sourceId == targetId) {
            return;
        }
        List<Book> working = new ArrayList<>(allBooks);
        int srcIdx = indexOfId(working, sourceId);
        int tgtIdx = indexOfId(working, targetId);
        if (srcIdx < 0 || tgtIdx < 0) {
            return;
        }
        Book moved = working.remove(srcIdx);
        int newTgtIdx = indexOfId(working, targetId);
        int insertAt = srcIdx < tgtIdx ? newTgtIdx + 1 : newTgtIdx;
        working.add(insertAt, moved);

        allBooks = working;
        new SqliteBookRepository(database).updateManualOrder(
            working.stream().map(Book::getId).collect(Collectors.toList()));
        refreshGrid();
    }

    private static int indexOfId(List<Book> books, long id) {
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }
}
