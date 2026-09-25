package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.BookCover;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Popup;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ブックカバーのプリセット模様を、プルダウン(ComboBox)ではなくTeams等の絵文字ピッカーのような
 * グリッド形式のポップアップから選択できるようにするボタン。「参照」ボタンで選んだカスタム画像も
 * 同じ表示領域にそのまま反映できるよう、扱う値はプリセットではなく{@link BookCover}そのもの。
 * プレビューは常に「現在選ばれている背景色/模様色」で描く（ブックならテーマの背景色/文字色、
 * シェルフなら固定の中間色）ため、その配色が変わったら{@link #refresh()}を呼んで再描画する必要がある。
 */
final class CoverPatternPickerButton extends Button {
    private static final double PREVIEW_WIDTH = BookCover.WIDTH_PX;
    private static final double PREVIEW_HEIGHT = BookCover.HEIGHT_PX;
    private static final double OPTION_SCALE = 0.5;
    private static final int COLUMNS = 5;

    private final Supplier<String> backgroundHexSupplier;
    private final Supplier<String> tintHexSupplier;
    private BookCover value;
    private Consumer<BookCover> onChange;

    CoverPatternPickerButton(BookCover initial, Supplier<String> backgroundHexSupplier,
                              Supplier<String> tintHexSupplier) {
        this.value = initial;
        this.backgroundHexSupplier = backgroundHexSupplier;
        this.tintHexSupplier = tintHexSupplier;
        setPadding(Insets.EMPTY);
        setStyle("-fx-background-color: transparent;");
        refresh();
        setOnAction(e -> showPicker());
    }

    void setOnChange(Consumer<BookCover> onChange) {
        this.onChange = onChange;
    }

    BookCover getValue() {
        return value;
    }

    void setValue(BookCover cover) {
        this.value = cover;
        refresh();
    }

    /** 背景色/模様色（テーマまたはシェルフの配色）が変わったときに呼び、プレビューを合わせ直す。 */
    void refresh() {
        setGraphic(BookFormWidgets.createBorderedCoverView(value, backgroundHexSupplier.get(), tintHexSupplier.get(),
            PREVIEW_WIDTH, PREVIEW_HEIGHT));
    }

    private void showPicker() {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setPadding(new Insets(8));
        grid.setStyle("-fx-background-color: white; -fx-border-color: #999999; -fx-border-width: 1;");

        double optionWidth = PREVIEW_WIDTH * OPTION_SCALE;
        double optionHeight = PREVIEW_HEIGHT * OPTION_SCALE;
        String backgroundHex = backgroundHexSupplier.get();
        String tintHex = tintHexSupplier.get();

        for (BookCover.Pattern pattern : BookCover.Pattern.values()) {
            BookCover optionCover = BookCover.ofPattern(pattern);
            Node preview = BookFormWidgets.createBorderedCoverView(optionCover, backgroundHex, tintHex,
                optionWidth, optionHeight);

            Button optionButton = new Button();
            optionButton.setGraphic(preview);
            optionButton.setPadding(Insets.EMPTY);
            // ポップアップを開いた際に最初のボタンへ既定のキーボードフォーカスリングが付き、
            // 選択中を示す青枠と紛らわしくなるため、フォーカス移動の対象から外す。
            optionButton.setFocusTraversable(false);
            boolean selected = !value.isCustom() && value.getPattern() == pattern;
            optionButton.setStyle(selected
                ? "-fx-border-color: #2a6ebb; -fx-border-width: 2;"
                : "-fx-border-color: transparent; -fx-border-width: 2;");
            optionButton.setOnAction(e -> {
                setValue(optionCover);
                popup.hide();
                if (onChange != null) {
                    onChange.accept(optionCover);
                }
            });
            int index = pattern.ordinal();
            grid.add(optionButton, index % COLUMNS, index / COLUMNS);
        }

        popup.getContent().add(grid);
        Point2D anchor = localToScreen(0, getHeight());
        popup.show(this, anchor.getX(), anchor.getY());
    }
}
