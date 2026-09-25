package com.mydictionary.android;

import android.content.Context;

import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.NoteTextColor;

import java.io.File;

/**
 * ノート本文（WebViewのbody要素）の背景色/背景画像/文字色に関するCSS・パス解決処理。
 * ノート作成画面のプレビューとノート画面の実際の表示で見た目がずれないよう一本化する
 * （デスクトップ版のNoteFormWidgetsと同じ役割）。
 */
public final class NoteAppearance {

    private NoteAppearance() {
    }

    /**
     * bodyタグに適用するCSSを組み立てる。背景画像が設定されていれば背景色の上に画面いっぱいに
     * 敷き詰めて表示し（cover）、背景色は画像読み込み失敗時のフォールバックとしても機能する。
     * imageUrl（WebViewAssetLoaderの仮想ドメイン経由のURL、null可）は呼び出し側で解決すること。
     */
    public static String buildBodyStyleCss(BookTheme backgroundTheme, String imageUrl, NoteTextColor textColor) {
        StringBuilder css = new StringBuilder();
        css.append("background-color:").append(backgroundTheme.getColorCode()).append(';');
        css.append("color:").append(textColor.getColorCode()).append(';');
        if (imageUrl != null && !imageUrl.isBlank()) {
            css.append("background-image:url('").append(imageUrl).append("');")
                .append("background-size:cover;background-position:center;background-repeat:no-repeat;")
                .append("background-attachment:fixed;");
        }
        return css.toString();
    }

    /** 背景画像ファイルが実在するかどうか（削除・未同期などで参照切れの場合はfalse）。 */
    public static boolean backgroundImageExists(Context context, String backgroundImageFileName) {
        if (backgroundImageFileName == null || backgroundImageFileName.isBlank()) {
            return false;
        }
        return new File(AppPaths.getImageDir(context), backgroundImageFileName).isFile();
    }
}
