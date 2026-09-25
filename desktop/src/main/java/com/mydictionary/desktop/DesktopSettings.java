package com.mydictionary.desktop;

import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * アプリ全体の設定（データ保存先フォルダなど）。OSのユーザー設定領域
 * （Windowsではレジストリ）に保存されるため、データフォルダ自体を移動しても消えない。
 */
public final class DesktopSettings {
    private static final String KEY_DATA_DIRECTORY = "dataDirectory";
    private static final String KEY_BOOK_SORT_MODE = "bookSortMode";

    private DesktopSettings() {
    }

    public static Optional<String> getDataDirectory() {
        return Optional.ofNullable(preferences().get(KEY_DATA_DIRECTORY, null));
    }

    public static void setDataDirectory(String path) {
        preferences().put(KEY_DATA_DIRECTORY, path);
    }

    /** ブックリスト画面で最後に選ばれていた並び順（列挙名で保存。未設定・不正値ならnull）。 */
    public static Optional<String> getBookSortMode() {
        return Optional.ofNullable(preferences().get(KEY_BOOK_SORT_MODE, null));
    }

    public static void setBookSortMode(String modeName) {
        preferences().put(KEY_BOOK_SORT_MODE, modeName);
    }

    private static Preferences preferences() {
        return Preferences.userNodeForPackage(DesktopSettings.class);
    }
}
