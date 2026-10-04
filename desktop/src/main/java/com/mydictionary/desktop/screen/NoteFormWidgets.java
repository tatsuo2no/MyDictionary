package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.NoteTextColor;
import com.mydictionary.desktop.FileUrls;
import com.mydictionary.desktop.MainApp;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.nio.file.Path;

/**
 * ノート作成画面・ノート画面で共通して使う、本文の背景色/背景画像/文字色に関するUI部品と
 * レンダリング（CSS生成）処理。編集中のプレビューと実際の表示で見た目がずれないよう一本化する。
 */
final class NoteFormWidgets {

    private static final Color BORDER_COLOR = Color.web("#999999");

    private NoteFormWidgets() {
    }

    /** 文字色名の横にカラーサンプルを添えたComboBoxを作る（テーマ選択と同じ見た目・操作感）。 */
    static ComboBox<NoteTextColor> createTextColorCombo() {
        ComboBox<NoteTextColor> combo = new ComboBox<>();
        combo.getItems().addAll(NoteTextColor.values());
        combo.setCellFactory(lv -> textColorCell());
        combo.setButtonCell(textColorCell());
        return combo;
    }

    private static ListCell<NoteTextColor> textColorCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(NoteTextColor color, boolean empty) {
                super.updateItem(color, empty);
                if (empty || color == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Rectangle swatch = new Rectangle(14, 14);
                swatch.setFill(Color.web(color.getColorCode()));
                swatch.setStroke(BORDER_COLOR);
                setText(color.getDisplayName());
                setGraphic(swatch);
            }
        };
    }

    /**
     * ノート本文（WebViewのbody要素）に適用するCSSを組み立てる。背景画像が設定されていれば
     * 背景色の上に画面いっぱいに敷き詰めて表示し（cover）、背景色は画像読み込み失敗時の
     * フォールバックとしても機能する。
     */
    static String buildBodyStyleCss(BookTheme backgroundTheme, String backgroundImageFileName,
                                     NoteTextColor textColor) {
        StringBuilder css = new StringBuilder();
        css.append("background-color:").append(backgroundTheme.getColorCode()).append(';');
        css.append("color:").append(textColor.getColorCode()).append(';');
        if (backgroundImageFileName != null && !backgroundImageFileName.isBlank()) {
            String imageUrl = FileUrls.toFileUrl(MainApp.IMAGE_DIR.resolve(backgroundImageFileName));
            css.append("background-image:url('").append(imageUrl).append("');")
                .append("background-size:cover;background-position:center;background-repeat:no-repeat;")
                .append("background-attachment:fixed;");
        }
        return css.toString();
    }
}
