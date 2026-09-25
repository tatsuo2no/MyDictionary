package com.mydictionary.desktop;

import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.io.PrintWriter;
import java.io.StringWriter;

/** 想定外の例外をユーザーに分かりやすく表示するためのダイアログ。 */
public final class ErrorDialogs {

    private ErrorDialogs() {
    }

    /**
     * 例外の要点（クラス名・メッセージ）だけをまず見せて、
     * 完全なスタックトレースは折りたたみ式のスクロール可能な領域に入れる。
     * 縦に長い1行のラベルで表示すると画面からはみ出して読めなくなるため、この形式にしている。
     */
    public static void show(Throwable throwable) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("エラー");
        alert.setHeaderText("予期しないエラーが発生しました。操作を継続できない場合があります。");
        alert.setContentText(throwable.toString());

        StringWriter stackTraceWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTraceWriter));

        TextArea textArea = new TextArea(stackTraceWriter.toString());
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        textArea.setPrefRowCount(20);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expandableContent = new GridPane();
        expandableContent.setMaxWidth(Double.MAX_VALUE);
        expandableContent.add(new Label("詳細（スタックトレース）:"), 0, 0);
        expandableContent.add(textArea, 0, 1);

        alert.getDialogPane().setExpandableContent(expandableContent);
        alert.getDialogPane().setPrefWidth(640);
        alert.showAndWait();
    }
}
