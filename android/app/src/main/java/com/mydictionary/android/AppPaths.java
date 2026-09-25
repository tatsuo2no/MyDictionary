package com.mydictionary.android;

import android.content.Context;

import java.io.File;

/** 画像の専用ディレクトリ（アプリ内部ストレージ）を扱う。 */
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
}
