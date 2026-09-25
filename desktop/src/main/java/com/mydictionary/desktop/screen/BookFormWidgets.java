package com.mydictionary.desktop.screen;

import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.desktop.MainApp;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.effect.Blend;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.ColorInput;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * ブック作成画面・ブックオプション画面で共通して使う、テーマ選択・カバー選択のUI部品。
 * 両画面で見た目・挙動がずれないよう、ここに一本化する。
 */
final class BookFormWidgets {

    private static final String COVER_PATTERN_RESOURCE_BASE = "/com/mydictionary/desktop/coverpatterns/";
    private static final Map<BookCover.Pattern, Image> PATTERN_IMAGE_CACHE = new EnumMap<>(BookCover.Pattern.class);
    private static final Color BORDER_COLOR = Color.web("#999999");

    private BookFormWidgets() {
    }

    /** テーマ名の横に、そのテーマの色そのままを表示する□のカラーサンプルを添えたComboBoxを作る。 */
    static ComboBox<BookTheme> createThemeCombo() {
        ComboBox<BookTheme> combo = new ComboBox<>();
        combo.getItems().addAll(BookTheme.values());
        combo.setCellFactory(lv -> themeCell());
        combo.setButtonCell(themeCell());
        return combo;
    }

    private static ListCell<BookTheme> themeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(BookTheme theme, boolean empty) {
                super.updateItem(theme, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                if (theme == null) {
                    setText("すべて");
                    setGraphic(null);
                    return;
                }
                Rectangle swatch = new Rectangle(14, 14);
                swatch.setFill(Color.web(theme.getColorCode()));
                swatch.setStroke(BORDER_COLOR);
                setText(theme.getDisplayName());
                setGraphic(swatch);
            }
        };
    }

    /** ノート本文の標準フォントサイズ（pt）を選ぶComboBox。 */
    static ComboBox<Integer> createFontSizeCombo() {
        ComboBox<Integer> combo = new ComboBox<>();
        for (int size : com.mydictionary.core.model.Book.AVAILABLE_FONT_SIZES_PT) {
            combo.getItems().add(size);
        }
        combo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer size) {
                return size == null ? "" : size + "pt";
            }

            @Override
            public Integer fromString(String s) {
                return null;
            }
        });
        return combo;
    }

    private static String patternFileName(BookCover.Pattern pattern) {
        return switch (pattern) {
            case PLAIN -> null;
            case STRIPES -> "stripes.png";
            case POLKA_DOTS -> "polka_dots.png";
            case CHECKERS -> "checkers.png";
            case DIAMONDS -> "diamonds.png";
            case WAVES -> "waves.png";
            case HERRINGBONE -> "herringbone.png";
            case CROSSHATCH -> "crosshatch.png";
            case FLORAL -> "floral.png";
            case HONEYCOMB -> "honeycomb.png";
        };
    }

    private static Image patternImage(BookCover.Pattern pattern) {
        return PATTERN_IMAGE_CACHE.computeIfAbsent(pattern, p ->
            new Image(BookFormWidgets.class.getResourceAsStream(COVER_PATTERN_RESOURCE_BASE + patternFileName(p))));
    }

    /**
     * カバー模様の画像（白の半透明マスクPNG）を、指定した色でティントしたImageViewを作る。
     * 模様画像自体は色を持たず、アルファチャンネルだけを使った型紙のようなものなので、
     * Blend(SRC_ATOP)でtintColorを重ねることで「その色の模様」として描画する。
     * pattern が PLAIN の場合は模様を持たないため呼び出さないこと（{@link #createPatternCoverView}参照）。
     */
    static ImageView createTintedPatternView(BookCover.Pattern pattern, String tintColorHex, double width, double height) {
        ImageView imageView = new ImageView(patternImage(pattern));
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        Blend blend = new Blend(BlendMode.SRC_ATOP);
        blend.setTopInput(new ColorInput(0, 0, width, height, Color.web(tintColorHex)));
        imageView.setEffect(blend);
        return imageView;
    }

    /**
     * 背景色の上に模様を乗せたカバー表示を作る（PLAINの場合は模様なしの単色背景のみ）。
     * ブックはテーマの背景色/文字色を、シェルフは固定の中間色（{@link com.mydictionary.core.model.Shelf}参照）を
     * それぞれbackgroundHex/tintHexとして渡す。
     */
    static Node createPatternCoverView(BookCover.Pattern pattern, String backgroundHex, String tintHex,
                                        double width, double height) {
        Rectangle background = new Rectangle(width, height, Color.web(backgroundHex));
        if (pattern == BookCover.Pattern.PLAIN) {
            return background;
        }
        ImageView patternView = createTintedPatternView(pattern, tintHex, width, height);
        return new StackPane(background, patternView);
    }

    /**
     * ブックカバーの表示を作る。カスタム画像が設定されていればそれをそのまま表示し、
     * 読み込みに失敗した場合や未設定の場合はプリセット模様の表示にフォールバックする。
     */
    static Node createCoverPreview(BookCover cover, String backgroundHex, String tintHex, double width, double height) {
        if (cover.isCustom() && cover.getCustomImagePath() != null) {
            File file = resolveCoverImageFile(cover.getCustomImagePath());
            if (file.isFile()) {
                try {
                    ImageView imageView = new ImageView(new Image(file.toURI().toString(), width, height, false, true));
                    imageView.setFitWidth(width);
                    imageView.setFitHeight(height);
                    return imageView;
                } catch (RuntimeException ignored) {
                    // 画像の読み込みに失敗した場合は模様のPLAIN（無地）にフォールバックする
                }
            }
            return createPatternCoverView(BookCover.Pattern.PLAIN, backgroundHex, tintHex, width, height);
        }
        return createPatternCoverView(cover.getPattern(), backgroundHex, tintHex, width, height);
    }

    /** カバー表示の周囲に1pxの枠を付けた表示を作る（ブック一覧・作成/オプション画面のプレビューで共通）。 */
    static Node createBorderedCoverView(BookCover cover, String backgroundHex, String tintHex,
                                         double innerWidth, double innerHeight) {
        Node content = createCoverPreview(cover, backgroundHex, tintHex, innerWidth, innerHeight);
        StackPane frame = new StackPane(content);
        frame.setStyle("-fx-border-color: #999999; -fx-border-width: 1;");
        double outerWidth = innerWidth + 2;
        double outerHeight = innerHeight + 2;
        frame.setMinSize(outerWidth, outerHeight);
        frame.setPrefSize(outerWidth, outerHeight);
        frame.setMaxSize(outerWidth, outerHeight);
        return frame;
    }

    /**
     * 「参照」ボタンで選んだ画像ファイルを、ブックカバーの表示サイズ（{@link BookCover#WIDTH_PX}×
     * {@link BookCover#HEIGHT_PX}）にアスペクト比を保ったまま拡大縮小し、はみ出た部分を中央基準で
     * 切り抜いてPNGとして保存する。保存先は既存のノート画像と同じ同期対象フォルダ
     * （{@link MainApp#IMAGE_DIR}）とし、デスクトップ⇔Android間で自動的に同期されるようにする。
     * 戻り値は絶対パスではなく**ファイル名のみ**（ノート内画像と同じ方式）。絶対パスをDBに保存すると
     * デスクトップ⇔Android間で同期した際に相手側のOSでは存在しないパスになり、カスタム画像が
     * 表示できなくなる不具合があったため、ファイル名だけを保存し表示時に{@link #resolveCoverImageFile}
     * で都度その端末の画像フォルダから解決する方式に修正した（2026-09）。
     */
    static String saveCustomCoverImage(File sourceFile) throws IOException {
        BufferedImage source = ImageIO.read(sourceFile);
        if (source == null) {
            throw new IOException("画像として読み込めませんでした: " + sourceFile.getName());
        }
        BufferedImage resized = cropToCover(source, BookCover.WIDTH_PX, BookCover.HEIGHT_PX);

        Files.createDirectories(MainApp.IMAGE_DIR);
        String fileName = System.currentTimeMillis() + "_cover.png";
        Path targetFile = MainApp.IMAGE_DIR.resolve(fileName);
        ImageIO.write(resized, "png", targetFile.toFile());
        return fileName;
    }

    /**
     * カスタムカバー画像のDB保存値をファイルとして解決する。新形式（ファイル名のみ）はこの端末の
     * 画像フォルダ内を見る。修正前の形式（絶対パス）で保存された既存データとの後方互換のため、
     * 値がこの端末上で実在する絶対パスの場合はそれをそのまま使う（同期元と同じ端末で見る場合のみ有効）。
     */
    static File resolveCoverImageFile(String storedValue) {
        File direct = new File(storedValue);
        if (direct.isAbsolute() && direct.isFile()) {
            return direct;
        }
        return MainApp.IMAGE_DIR.resolve(storedValue).toFile();
    }

    private static BufferedImage cropToCover(BufferedImage source, int targetWidth, int targetHeight) {
        double scale = Math.max((double) targetWidth / source.getWidth(), (double) targetHeight / source.getHeight());
        int scaledWidth = Math.max(targetWidth, (int) Math.ceil(source.getWidth() * scale));
        int scaledHeight = Math.max(targetHeight, (int) Math.ceil(source.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
        g.dispose();

        int x = (scaledWidth - targetWidth) / 2;
        int y = (scaledHeight - targetHeight) / 2;
        return scaled.getSubimage(x, y, targetWidth, targetHeight);
    }
}
