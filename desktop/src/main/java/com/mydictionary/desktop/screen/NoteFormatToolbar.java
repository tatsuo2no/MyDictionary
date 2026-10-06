package com.mydictionary.desktop.screen;

import com.mydictionary.core.markdown.HeadingStyles;
import com.mydictionary.desktop.editor.RichNoteEditor;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ノート編集画面の本文欄の上に置く書式設定ツールバー。ボタンを押すと、WYSIWYGエディタ
 * （{@link RichNoteEditor}）で選択中の範囲・カーソル位置に書式を適用する。
 * 数式（TeX記法）などGUIでは却って編集しにくい記法を手で書くための「Markdown編集」切替ボタンで、
 * 書式ボタンを隠してMarkdownのソース表示へ切り替えることもできる（切替の実処理は呼び出し側）。
 */
final class NoteFormatToolbar {
    private static final String[] FONT_SIZES_PT = {
        "8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "28", "32", "36", "48", "72"};
    private static final int MAX_TABLE_ROWS = 100;
    private static final int MAX_TABLE_COLUMNS = 20;

    private final RichNoteEditor editor;
    private final Supplier<List<String>> fontFamilies;
    private final Runnable onInsertImage;
    /** 見出しウィンドウに出す見出しレベルは1〜5（レベル6は本文と同じ大きさなので出さない）。 */
    private static final int MAX_HEADING_LEVEL = 5;

    private final String bookFontFamily;
    private final int baseFontSizePt;
    // 1行目: 文字の装飾・配置、2行目: 見出し・リスト・挿入・モード切替。幅が狭いときは各行がさらに折り返す。
    private final VBox root = new VBox(4);
    private final FlowPane firstRow = new FlowPane(4, 4);
    private final FlowPane secondRow = new FlowPane(4, 4);
    private FlowPane currentRow = firstRow;
    private final List<Node> formatNodes = new ArrayList<>();

    /**
     * bookFontFamily・baseFontSizePtは、見出しウィンドウで「ノートで実際に表示される大きさ」を
     * サンプル表示するための、このブックの本文フォントと標準サイズ(pt)。
     */
    NoteFormatToolbar(RichNoteEditor editor, Supplier<List<String>> fontFamilies, Runnable onInsertImage,
                      Runnable onShowPreview, Consumer<Boolean> onMarkdownMode, String bookFontFamily,
                      int baseFontSizePt) {
        this.editor = editor;
        this.fontFamilies = fontFamilies;
        this.onInsertImage = onInsertImage;
        this.bookFontFamily = bookFontFamily;
        this.baseFontSizePt = baseFontSizePt;
        root.getChildren().addAll(firstRow, secondRow);
        build(onShowPreview, onMarkdownMode);
    }

    Node getNode() {
        return root;
    }

    private void build(Runnable onShowPreview, Consumer<Boolean> onMarkdownMode) {
        // フォント
        addFormat(textButton(styledText("B", FontWeight.BOLD, FontPosture.REGULAR, false, false), "太字",
            editor::bold));
        addFormat(textButton(styledText("I", FontWeight.NORMAL, FontPosture.ITALIC, false, false), "斜体",
            editor::italic));
        addFormat(textButton(styledText("U", FontWeight.NORMAL, FontPosture.REGULAR, true, false), "下線",
            editor::underline));
        addFormat(textButton(styledText("S", FontWeight.NORMAL, FontPosture.REGULAR, false, true), "打ち消し線",
            editor::strikethrough));

        Button textColorButton = textButton(colorGraphic("A", Color.RED, false), "文字色", null);
        textColorButton.setOnAction(e -> NamedColorPicker.show(textColorButton, "文字色",
            name -> applyStyle("color", name)));
        addFormat(textColorButton);

        Button backgroundColorButton = textButton(colorGraphic("A", Color.YELLOW, true), "文字背景色", null);
        backgroundColorButton.setOnAction(e -> NamedColorPicker.show(backgroundColorButton, "文字背景色",
            name -> applyStyle("background-color", name)));
        addFormat(backgroundColorButton);

        Button fontButton = textButton(new Label("フォント ▾"), "フォント選択", null);
        fontButton.setOnAction(e -> showFontMenu(fontButton));
        addFormat(fontButton);

        Button sizeButton = textButton(new Label("サイズ ▾"), "フォントサイズ選択", null);
        sizeButton.setOnAction(e -> showSizeMenu(sizeButton));
        addFormat(sizeButton);

        addFormat(textButton(new Label("ルビ"), "ルビ（選択した文字にふりがなを付ける）", this::onRuby));
        addFormat(separator());

        // 配置
        addFormat(iconButton(alignIcon("M1 2h14v2H1z M1 6h9v2H1z M1 10h14v2H1z M1 14h9v2H1z"), "左揃え",
            () -> align("left")));
        addFormat(iconButton(alignIcon("M1 2h14v2H1z M3.5 6h9v2h-9z M1 10h14v2H1z M3.5 14h9v2h-9z"), "中央揃え",
            () -> align("center")));
        addFormat(iconButton(alignIcon("M1 2h14v2H1z M6 6h9v2H6z M1 10h14v2H1z M6 14h9v2H6z"), "右揃え",
            () -> align("right")));

        // ---- 2行目の先頭から: 見出し・リスト・挿入
        currentRow = secondRow;
        Button headingButton = textButton(new Label("見出し ▾"), "見出し（大きさを確認して選べます）", null);
        headingButton.setOnAction(e -> showHeadingPopup(headingButton));
        addFormat(headingButton);
        addFormat(separator());

        // リスト
        addFormat(textButton(listIcon("•―\n•―\n•―"), "番号なしリスト", editor::toggleBulletList));
        addFormat(textButton(listIcon("1―\n2―\n3―"), "番号付きリスト", editor::toggleNumberedList));
        addFormat(separator());

        // 挿入
        addFormat(iconButton(photoIcon(), "画像挿入", onInsertImage));
        addFormat(textButton(boldText("田", 16), "テーブル挿入", this::onInsertTable));
        addFormat(textButton(boldText("―", 14), "水平線挿入", editor::insertHorizontalRule));
        addFormat(textButton(boldText("＞", 14), "引用", editor::toggleQuote));
        addFormat(textButton(boldText("”", 16), "インラインコード", editor::inlineCode));
        addFormat(textButton(boldText("</>", 12), "コードブロック", editor::toggleCodeBlock));

        ToggleButton markdownModeButton = new ToggleButton("Markdown編集");
        markdownModeButton.setFocusTraversable(false);
        markdownModeButton.setTooltip(new Tooltip(
            "書式ボタンを隠して、Markdown（数式のTeX記法など）を直接編集します"));
        markdownModeButton.selectedProperty().addListener((obs, was, selected) -> {
            for (Node node : formatNodes) {
                node.setVisible(!selected);
                node.setManaged(!selected);
            }
            onMarkdownMode.accept(selected);
        });

        Button previewButton = new Button("プレビューを表示");
        previewButton.setFocusTraversable(false);
        previewButton.setOnAction(e -> onShowPreview.run());

        secondRow.getChildren().addAll(markdownModeButton, previewButton);
    }

    private void addFormat(Node node) {
        formatNodes.add(node);
        currentRow.getChildren().add(node);
    }

    /**
     * 見出しレベル5→1の順に、このブックの標準フォントサイズを基準に実際に表示される大きさで
     * 1行ずつサンプルを並べたウィンドウ。選んだレベルを本文欄の選択行（無ければカーソル行）に適用する。
     */
    private void showHeadingPopup(Node anchor) {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        VBox box = new VBox(2);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: white; -fx-border-color: #999999; -fx-border-width: 1;");
        Label title = new Label("見出し（本文の標準 " + baseFontSizePt + "pt を基準にした表示サイズ）");
        title.setStyle("-fx-text-fill: #555555;");
        box.getChildren().add(title);

        String family = "'" + bookFontFamily + "'";
        for (int level = MAX_HEADING_LEVEL; level >= HeadingStyles.MIN_LEVEL; level--) {
            int chosen = level;
            String size = HeadingStyles.formatPt(HeadingStyles.sizePt(level, baseFontSizePt));
            Button row = new Button("見出し" + level + "　サンプルの文字　（" + size + "pt）");
            row.setFocusTraversable(false);
            row.setMaxWidth(Double.MAX_VALUE);
            String normal = "-fx-background-color: transparent; -fx-alignment: center-left; -fx-font-weight: bold;"
                + " -fx-font-family: " + family + "; -fx-font-size: " + size + "pt;";
            row.setStyle(normal);
            row.setOnMouseEntered(e -> row.setStyle(normal + " -fx-background-color: #e8f0fb;"));
            row.setOnMouseExited(e -> row.setStyle(normal));
            row.setOnAction(e -> {
                popup.hide();
                editor.setHeading(chosen);
            });
            box.getChildren().add(row);
        }

        Button clear = new Button("見出しを解除（通常の文字に戻す）");
        clear.setFocusTraversable(false);
        clear.setOnAction(e -> {
            popup.hide();
            editor.setHeading(0);
        });
        box.getChildren().add(clear);
        VBox.setMargin(clear, new Insets(6, 0, 0, 0));

        popup.getContent().add(box);
        Point2D at = anchor.localToScreen(0, anchor.getLayoutBounds().getHeight());
        popup.show(anchor, at.getX(), at.getY());
    }

    // ---------------------------------------------------------------- 操作

    private void applyStyle(String property, String value) {
        editor.setStyle(property, value);
    }

    private void align(String align) {
        editor.align(align);
    }

    private void showFontMenu(Node anchor) {
        ContextMenu menu = new ContextMenu();
        MenuItem reset = new MenuItem("既定のフォントに戻す");
        reset.setOnAction(e -> applyStyle("font-family", null));
        menu.getItems().add(reset);
        for (String family : fontFamilies.get()) {
            MenuItem item = new MenuItem(family);
            item.setStyle("-fx-font-family: '" + family + "';");
            item.setOnAction(e -> applyStyle("font-family", "'" + family + "'"));
            menu.getItems().add(item);
        }
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void showSizeMenu(Node anchor) {
        ContextMenu menu = new ContextMenu();
        MenuItem reset = new MenuItem("既定のサイズに戻す");
        reset.setOnAction(e -> applyStyle("font-size", null));
        menu.getItems().add(reset);
        for (String size : FONT_SIZES_PT) {
            MenuItem item = new MenuItem(size + "pt");
            item.setOnAction(e -> applyStyle("font-size", size + "pt"));
            menu.getItems().add(item);
        }
        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void onRuby() {
        String selected = editor.selectedText();
        if (selected.isBlank()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                "ふりがなを付ける文字を本文欄で選択してから押してください。", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("ルビ");
        dialog.setHeaderText("「" + selected.trim() + "」のふりがなを入力してください\n"
            + "（空欄で決定すると、既に付いているルビを外します）");
        dialog.setContentText("ふりがな");
        Optional<String> reading = dialog.showAndWait();
        reading.ifPresent(editor::setRuby);
    }

    private void onInsertTable() {
        Dialog<int[]> dialog = new Dialog<>();
        dialog.setTitle("テーブルの挿入");
        dialog.setHeaderText(null);
        ButtonType insertType = new ButtonType("挿入", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(insertType, ButtonType.CANCEL);

        TextField rowsField = new TextField("3");
        TextField columnsField = new TextField("3");
        Label hint = new Label("行数は見出し行を含みます（行数 1〜" + MAX_TABLE_ROWS
            + "、列数 1〜" + MAX_TABLE_COLUMNS + "）");
        hint.setStyle("-fx-text-fill: #666666;");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("行数"), rowsField);
        grid.addRow(1, new Label("列数"), columnsField);
        grid.add(hint, 0, 2, 2, 1);
        dialog.getDialogPane().setContent(grid);

        Node insertButton = dialog.getDialogPane().lookupButton(insertType);
        Runnable validate = () -> insertButton.setDisable(
            parseInRange(rowsField.getText(), MAX_TABLE_ROWS) == null
                || parseInRange(columnsField.getText(), MAX_TABLE_COLUMNS) == null);
        rowsField.textProperty().addListener((obs, o, n) -> validate.run());
        columnsField.textProperty().addListener((obs, o, n) -> validate.run());
        validate.run();

        dialog.setResultConverter(button -> button == insertType
            ? new int[]{parseInRange(rowsField.getText(), MAX_TABLE_ROWS),
                parseInRange(columnsField.getText(), MAX_TABLE_COLUMNS)}
            : null);

        dialog.showAndWait().ifPresent(size -> editor.insertTable(size[0], size[1]));
    }

    private static Integer parseInRange(String text, int max) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 1 && value <= max ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 部品

    private Button textButton(Node graphic, String tooltip, Runnable action) {
        Button button = new Button();
        button.setGraphic(graphic);
        button.setFocusTraversable(false);
        button.setMinHeight(30);
        button.setTooltip(new Tooltip(tooltip));
        if (action != null) {
            button.setOnAction(e -> action.run());
        }
        return button;
    }

    private Button iconButton(Node icon, String tooltip, Runnable action) {
        return textButton(icon, tooltip, action);
    }

    private static Separator separator() {
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.setPadding(new Insets(0, 2, 0, 2));
        return separator;
    }

    private static Text styledText(String value, FontWeight weight, FontPosture posture, boolean underline,
                                    boolean strikethrough) {
        Text text = new Text(value);
        text.setFont(Font.font("Serif", weight, posture, 16));
        text.setUnderline(underline);
        text.setStrikethrough(strikethrough);
        return text;
    }

    private static Text boldText(String value, double size) {
        Text text = new Text(value);
        text.setFont(Font.font(null, FontWeight.BOLD, size));
        return text;
    }

    /** 文字「A」の下に色の帯を引いたアイコン（文字色）、または色の背景を敷いたアイコン（文字背景色）。 */
    private static Node colorGraphic(String letter, Color color, boolean asBackground) {
        Text text = new Text(letter);
        text.setFont(Font.font(null, FontWeight.BOLD, 15));
        Rectangle swatch = new Rectangle(asBackground ? 20 : 18, asBackground ? 20 : 4, color);
        swatch.setStroke(Color.web("#888888"));
        swatch.setStrokeWidth(0.5);
        if (asBackground) {
            return new StackPane(swatch, text);
        }
        VBox box = new VBox(0, text, swatch);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        return box;
    }

    private static Node alignIcon(String pathData) {
        SVGPath path = new SVGPath();
        path.setContent(pathData);
        path.setFill(Color.web("#333333"));
        return path;
    }

    private static Node listIcon(String value) {
        Label label = new Label(value);
        label.setStyle("-fx-font-size: 7px; -fx-line-spacing: -4; -fx-text-fill: #333333;");
        label.setMaxHeight(22);
        label.setMinHeight(22);
        return label;
    }

    /** 写真マーク（額縁・山・太陽）。 */
    private static Node photoIcon() {
        SVGPath frame = new SVGPath();
        frame.setContent("M1.5 2.5h15v13h-15z");
        frame.setFill(Color.TRANSPARENT);
        frame.setStroke(Color.web("#333333"));
        frame.setStrokeWidth(1.4);
        SVGPath mountain = new SVGPath();
        mountain.setContent("M3 14.5l4-5 3 3 2-2 3.5 4z");
        mountain.setFill(Color.web("#333333"));
        SVGPath sun = new SVGPath();
        sun.setContent("M11.5 5a1.6 1.6 0 1 0 0.01 0z");
        sun.setFill(Color.web("#333333"));
        return new javafx.scene.Group(frame, mountain, sun);
    }
}
