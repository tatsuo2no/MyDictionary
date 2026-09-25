package com.mydictionary.desktop.screen;

import com.mydictionary.desktop.DesktopSettings;
import com.mydictionary.desktop.MainApp;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * データ保存先フォルダを設定する画面。ここでGoogle Drive/OneDrive/Dropboxなど
 * 任意の同期フォルダを指定すると、そのフォルダにデータベース・画像を置くようになり、
 * デスクトップ間でデータを共有できる（クラウド側の同期任せであり、アプリが同期処理を行うわけではない）。
 * 特定のクラウドサービスに依存した実装ではなく、単に指定されたフォルダを読み書きするだけなので、
 * OSがフォルダとして認識できるクラウド同期サービスであればどれでも利用できる。
 */
public class SettingsScreen {
    private final SceneNavigator navigator;
    private final VBox view = new VBox(16);
    private Label currentPathLabel;

    public SettingsScreen(SceneNavigator navigator) {
        this.navigator = navigator;
        build();
    }

    public VBox getView() {
        return view;
    }

    private void build() {
        Label title = new Label("設定");
        title.getStyleClass().add("screen-title");

        Button backButton = new Button("戻る");
        backButton.setOnAction(e -> navigator.showShelfList());

        HBox header = new HBox(16, title, backButton);
        header.setAlignment(Pos.CENTER_LEFT);

        Label description = new Label(
            "ブック・ノート・タグのデータ（データベースと画像）を保存するフォルダを指定できます。\n"
                + "Google Drive・OneDrive・Dropboxなど、お使いの同期フォルダを指定すると、\n"
                + "そのフォルダの同期機能によって他のPCと共有できます（このアプリ自体が同期を行うわけではありません）。\n"
                + "変更はアプリの再起動後に反映されます。\n\n"
                + "【重要】Google Driveをお使いの場合、同期方法が「ストリーミング」になっていると、\n"
                + "保存したはずのデータが実際にはファイルに反映されないことがあります。このフォルダは\n"
                + "必ず「オフラインで使用可能にする」に設定してください（Google Driveアプリでフォルダを\n"
                + "右クリック→オフラインアクセス）。\n\n"
                + "【注意】このフォルダをAndroid版との同期にも使っている場合、こちらで変更した内容が\n"
                + "クラウドへのアップロードが完了する前にAndroid版で「同期」を押すと、直前の変更が\n"
                + "失われることがあります。タスクトレイのクラウドアプリのアイコンが同期完了の表示に\n"
                + "なったことを確認し、さらにAndroid版のGoogle Driveアプリでもフォルダを直接開いて\n"
                + "最新化してから、Android版で同期してください。");
        description.setWrapText(true);

        currentPathLabel = new Label("現在のフォルダ: " + MainApp.APP_DATA_DIR.toAbsolutePath());
        currentPathLabel.setWrapText(true);

        Button changeButton = new Button("フォルダを変更...");
        changeButton.setOnAction(e -> onChangeFolder());

        view.setPadding(new Insets(16));
        view.getChildren().addAll(header, description, currentPathLabel, changeButton);
    }

    private void onChangeFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("データ保存先フォルダを選択");
        Path initial = MainApp.APP_DATA_DIR.toAbsolutePath();
        if (Files.isDirectory(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        }

        var selected = chooser.showDialog(view.getScene() != null ? view.getScene().getWindow() : null);
        if (selected == null) {
            return;
        }

        Path newDir = selected.toPath().toAbsolutePath();
        Path currentDir = MainApp.APP_DATA_DIR.toAbsolutePath();
        if (newDir.equals(currentDir)) {
            return;
        }

        boolean newDirHasExistingData = Files.exists(newDir.resolve("mydictionary.db"));

        String message = newDirHasExistingData
            ? "選択したフォルダには既にデータ（mydictionary.db）があります。\n"
                + "今のデータの代わりに、そのデータを使うように切り替えます。よろしいですか？\n"
                + "（アプリの再起動が必要です）"
            : "データベースと画像を選択したフォルダにコピーして、以後そこを使うようにします。よろしいですか？\n"
                + "（アプリの再起動が必要です）";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }

        try {
            List<Path> skipped = List.of();
            if (!newDirHasExistingData) {
                Files.createDirectories(newDir);
                skipped = copyDirectoryContents(currentDir, newDir);
            }
            DesktopSettings.setDataDirectory(newDir.toString());
            currentPathLabel.setText("現在のフォルダ: " + newDir + "\n（反映にはアプリの再起動が必要です）");

            String doneMessage = "設定を保存しました。変更を反映するにはアプリを再起動してください。";
            if (!skipped.isEmpty()) {
                doneMessage += "\n\n（" + skipped.size() + "件のファイル/フォルダはコピーできなかったためスキップしました。"
                    + "OneDrive等が内部管理用に作る特殊なフォルダである可能性が高く、通常は問題ありません。）";
            }
            Alert done = new Alert(Alert.AlertType.INFORMATION, doneMessage);
            done.setHeaderText(null);
            done.showAndWait();
        } catch (IOException e) {
            Alert error = new Alert(Alert.AlertType.ERROR, "フォルダの変更に失敗しました: " + e.getMessage());
            error.setHeaderText(null);
            error.showAndWait();
        }
    }

    /**
     * フォルダの中身をコピーする。OneDriveなどのクラウド同期フォルダには、アプリからは
     * 読み書きできない内部管理用の特殊なフォルダ（隠し属性やGUID名のフォルダなど）が
     * 混ざっていることがあるため、そういったファイル/フォルダは無視して処理を続行する
     * （1件アクセスできないだけでコピー全体が失敗しないようにする）。
     * 戻り値はスキップしたファイル/フォルダの一覧。
     */
    private List<Path> copyDirectoryContents(Path source, Path target) throws IOException {
        List<Path> skipped = new ArrayList<>();
        if (!Files.isDirectory(source)) {
            return skipped;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (isHiddenOrSystem(dir)) {
                    skipped.add(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                try {
                    Files.createDirectories(target.resolve(source.relativize(dir)));
                } catch (IOException e) {
                    skipped.add(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (isHiddenOrSystem(file)) {
                    skipped.add(file);
                    return FileVisitResult.CONTINUE;
                }
                try {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    skipped.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // アクセスできないファイル/フォルダ（OneDriveの内部管理用フォルダなど）は
                // 無視して処理を継続する。ここでexcを再throwすると全体が失敗してしまう。
                skipped.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return skipped;
    }

    private boolean isHiddenOrSystem(Path path) {
        Path fileName = path.getFileName();
        if (fileName != null && fileName.toString().startsWith(".")) {
            return true;
        }
        try {
            return Files.isHidden(path);
        } catch (IOException e) {
            return false;
        }
    }
}
