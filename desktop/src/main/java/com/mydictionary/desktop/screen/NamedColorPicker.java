package com.mydictionary.desktop.screen;

import com.mydictionary.core.markdown.CssNamedColors;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.List;
import java.util.function.Consumer;

/**
 * ブラウザ定義済みの140色（色相順、7行×20列）から色を選ぶポップアップ。
 * ブックカバーの模様選択（{@link CoverPatternPickerButton}）と同じく、ボタンの下にグリッド状のポップアップを出す。
 * 各色のセルにマウスを乗せると英語の色名とHEX値をツールチップで表示する。
 */
final class NamedColorPicker {
    private static final double SWATCH_SIZE = 22;

    private NamedColorPicker() {
    }

    /** onPickには選んだ色名（例: "crimson"）が渡る。「色を解除」を押した場合はnullが渡る。 */
    static void show(Node anchor, String title, Consumer<String> onPick) {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);

        List<CssNamedColors.NamedColor> colors = CssNamedColors.all();
        for (int i = 0; i < colors.size(); i++) {
            CssNamedColors.NamedColor color = colors.get(i);
            Button swatch = new Button();
            swatch.setMinSize(SWATCH_SIZE, SWATCH_SIZE);
            swatch.setPrefSize(SWATCH_SIZE, SWATCH_SIZE);
            swatch.setMaxSize(SWATCH_SIZE, SWATCH_SIZE);
            swatch.setPadding(Insets.EMPTY);
            swatch.setFocusTraversable(false);
            swatch.setStyle("-fx-background-color: " + color.hex() + "; -fx-background-radius: 0;"
                + " -fx-border-color: #888888; -fx-border-width: 1; -fx-border-radius: 0;");
            swatch.setTooltip(new Tooltip(color.name() + "  " + color.hex()));
            swatch.setOnAction(e -> {
                popup.hide();
                onPick.accept(color.name());
            });
            grid.add(swatch, i % CssNamedColors.COLUMNS, i / CssNamedColors.COLUMNS);
        }

        Button clearButton = new Button("色を解除");
        clearButton.setFocusTraversable(false);
        clearButton.setOnAction(e -> {
            popup.hide();
            onPick.accept(null);
        });

        VBox content = new VBox(6, new Label(title), grid, clearButton);
        content.setPadding(new Insets(8));
        content.setStyle("-fx-background-color: white; -fx-border-color: #999999; -fx-border-width: 1;");

        popup.getContent().add(content);
        Point2D anchorPoint = anchor.localToScreen(0, anchor.getLayoutBounds().getHeight());
        popup.show(anchor, anchorPoint.getX(), anchorPoint.getY());
    }
}
