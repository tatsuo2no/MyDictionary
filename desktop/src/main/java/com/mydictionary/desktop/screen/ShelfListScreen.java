package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.desktop.db.SqliteShelfRepository;
import com.mydictionary.desktop.db.SqliteDatabase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** シェルフ（本棚）一覧画面。アプリのホーム画面で、ブックはこの下のシェルフごとに分類される。 */
public class ShelfListScreen {
    /** カード内のシェルフカバー表示・名前表示の幅（枠1px込みでBookCover.WIDTH_PXより2px大きい）。 */
    private static final double CARD_WIDTH = BookCover.WIDTH_PX + 2;

    private final SceneNavigator navigator;
    private final SqliteDatabase database;
    private final BorderPane view = new BorderPane();
    private final FlowPane grid = new FlowPane(16, 16);
    private final Label countLabel = new Label();
    private List<Shelf> allShelves;

    public ShelfListScreen(SceneNavigator navigator, SqliteDatabase database) {
        this.navigator = navigator;
        this.database = database;
        build();
    }

    public BorderPane getView() {
        return view;
    }

    private void build() {
        allShelves = new SqliteShelfRepository(database).findAll();

        Label title = new Label("シェルフ一覧");
        title.getStyleClass().add("screen-title");

        Button createButton = new Button("シェルフを作成");
        createButton.setOnAction(e -> navigator.showShelfCreate());

        Button settingsButton = new Button("設定");
        settingsButton.setOnAction(e -> navigator.showSettings());

        HBox header = new HBox(16, title, countLabel, createButton, settingsButton);
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

    private void refreshGrid() {
        countLabel.setText(allShelves.size() + " 件"
            + (allShelves.size() > 1 ? "（ドラッグで並び替え）" : ""));
        grid.getChildren().clear();
        for (Shelf shelf : allShelves) {
            grid.getChildren().add(createShelfCard(shelf));
        }
    }

    private VBox createShelfCard(Shelf shelf) {
        Node cover = BookFormWidgets.createBorderedCoverView(shelf.getCover(),
            Shelf.COVER_BACKGROUND_COLOR_CODE, Shelf.COVER_TINT_COLOR_CODE, BookCover.WIDTH_PX, BookCover.HEIGHT_PX);

        Label label = new Label(shelf.getName());
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
                navigator.showBookList(shelf.getId());
            } else if (e.getButton() == MouseButton.SECONDARY) {
                navigator.showShelfOptions(shelf.getId());
            }
        });

        enableDragReorder(card, shelf);

        return card;
    }

    private void enableDragReorder(VBox card, Shelf shelf) {
        card.setOnDragDetected(e -> {
            Dragboard dragboard = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(shelf.getId()));
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
                reorderShelf(Long.parseLong(e.getDragboard().getString()), shelf.getId());
                success = true;
            }
            e.setDropCompleted(success);
            e.consume();
        });
        card.setOnDragDone(e -> card.setOpacity(1.0));
    }

    private void reorderShelf(long sourceId, long targetId) {
        if (sourceId == targetId) {
            return;
        }
        List<Shelf> working = new ArrayList<>(allShelves);
        int srcIdx = indexOfId(working, sourceId);
        int tgtIdx = indexOfId(working, targetId);
        if (srcIdx < 0 || tgtIdx < 0) {
            return;
        }
        Shelf moved = working.remove(srcIdx);
        int newTgtIdx = indexOfId(working, targetId);
        int insertAt = srcIdx < tgtIdx ? newTgtIdx + 1 : newTgtIdx;
        working.add(insertAt, moved);

        allShelves = working;
        new SqliteShelfRepository(database).updateManualOrder(
            working.stream().map(Shelf::getId).collect(Collectors.toList()));
        refreshGrid();
    }

    private static int indexOfId(List<Shelf> shelves, long id) {
        for (int i = 0; i < shelves.size(); i++) {
            if (shelves.get(i).getId() == id) {
                return i;
            }
        }
        return -1;
    }
}
