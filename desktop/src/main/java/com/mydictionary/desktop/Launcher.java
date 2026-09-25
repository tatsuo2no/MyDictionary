package com.mydictionary.desktop;

/**
 * javafx.application.Applicationを継承したクラスをそのままmainクラスに指定すると、
 * クラスパス実行時にJVMが「JavaFX runtime components are missing」と誤検知するため、
 * 継承しない別クラスを経由して起動する。
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
