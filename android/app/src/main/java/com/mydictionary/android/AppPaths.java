package com.mydictionary.android;

import android.content.Context;

import java.io.File;

/** 画像・追加フォントの専用ディレクトリ（アプリ内部ストレージ）を扱う。 */
public final class AppPaths {
    private AppPaths() {
    }

    public static File getImageDir(Context context) {
        File dir = new File(context.getFilesDir(), "images");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** デスクトップ版で追加され、同期で届いたフォント(.ttf)を置くフォルダ。 */
    public static File getFontDir(Context context) {
        File dir = new File(context.getFilesDir(), "fonts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}
