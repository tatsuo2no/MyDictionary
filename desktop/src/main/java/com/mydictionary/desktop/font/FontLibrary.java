package com.mydictionary.desktop.font;

import com.mydictionary.core.font.CustomFonts;
import com.mydictionary.desktop.FileUrls;
import com.mydictionary.desktop.MainApp;
import javafx.scene.text.Font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * デスクトップ版で追加されたフォント（データ保存先フォルダの{@code fonts}フォルダ内の.ttf）の一覧・
 * CSS・JavaFX登録・取り込みをまとめる窓口。フォルダは同期フォルダの中にあるため、Android版と共有される。
 */
public final class FontLibrary {

    private FontLibrary() {
    }

    public static Path directory() {
        return MainApp.APP_DATA_DIR.resolve(CustomFonts.FOLDER_NAME);
    }

    public static List<CustomFonts.Face> faces() {
        return faces(directory());
    }

    static List<CustomFonts.Face> faces(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException e) {
            return List.of();
        }
        return CustomFonts.parseAll(names);
    }

    public static List<String> families() {
        return CustomFonts.families(faces());
    }

    /** ノート本文のHTMLに埋め込む、追加フォント全部の{@code @font-face}規則（追加フォントが無ければ空文字）。 */
    public static String fontFaceCss() {
        Path dir = directory();
        return CustomFonts.fontFaceCss(faces(dir), fileName -> FileUrls.toFileUrl(dir.resolve(fileName)));
    }

    /**
     * 追加フォントをJavaFXの画面部品でも使えるように登録する（{@code -fx-font-family}で指定できるようになる）。
     * 壊れたフォントがあっても他の起動処理を止めないよう、1つずつ失敗を無視する。
     */
    public static void registerWithJavaFx() {
        Path dir = directory();
        for (CustomFonts.Face face : faces(dir)) {
            try {
                Font.loadFont(dir.resolve(face.fileName()).toUri().toString(), 12);
            } catch (RuntimeException ignored) {
                // 読み込めないフォントは画面部品では使えないだけで、ノート本文(WebView)には影響しない。
            }
        }
    }

    /** 選んだフォントファイルを取り込み（ttc/woff/woff2はttfへ変換）、JavaFXにも登録する。 */
    public static List<FontImporter.Imported> importFont(Path source) throws IOException {
        List<FontImporter.Imported> imported = FontImporter.importFile(source, directory());
        registerWithJavaFx();
        return imported;
    }
}
