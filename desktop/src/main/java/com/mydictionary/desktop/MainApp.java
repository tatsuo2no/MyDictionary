package com.mydictionary.desktop;

import com.mydictionary.desktop.db.SqliteDatabase;
import com.mydictionary.desktop.sample.SampleDataInitializer;
import com.mydictionary.desktop.screen.SceneNavigator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainApp extends Application {

    public static final Path APP_DATA_DIR = resolveDataDir();
    public static final Path IMAGE_DIR = APP_DATA_DIR.resolve("images");

    /**
     * データ保存先フォルダ。設定でクラウド同期フォルダ（Google Drive/OneDrive/Dropboxなど）が
     * 指定されていればそちらを使い、未設定ならこれまで通りユーザーホーム配下の隠しフォルダを使う。
     * 設定変更はアプリ再起動後に反映される。
     */
    private static Path resolveDataDir() {
        return DesktopSettings.getDataDirectory()
            .map(Paths::get)
            .orElseGet(() -> Paths.get(System.getProperty("user.home"), ".mydictionary"));
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        // 画面遷移中の例外はJavaFXの既定動作だとコンソールに出るだけで、
        // jpackage版（コンソール非表示）では気付けないため、必ずダイアログで見えるようにする。
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            throwable.printStackTrace();
            Platform.runLater(() -> ErrorDialogs.show(throwable));
        });

        Files.createDirectories(IMAGE_DIR);

        SqliteDatabase database = new SqliteDatabase(APP_DATA_DIR.resolve("mydictionary.db"));
        database.initSchema();
        SampleDataInitializer.ensureSampleData(database);

        SceneNavigator navigator = new SceneNavigator(primaryStage, database);
        navigator.showShelfList();

        primaryStage.setTitle("MyDictionary");
        // ノート作成画面など、左右にパネルを並べる画面がウィンドウを縮小しすぎると
        // 使い物にならなくなるため、デスクトップ版では最小サイズを設けておく。
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);
        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            navigator.confirmExit();
        });
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
