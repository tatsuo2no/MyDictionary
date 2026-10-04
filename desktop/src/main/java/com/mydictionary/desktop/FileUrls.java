package com.mydictionary.desktop;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** ファイルパスをWebView/CSSに埋め込めるfile:// URL文字列へ変換する。 */
public final class FileUrls {

    private FileUrls() {
    }

    /**
     * ファイルパスを、CSSの url() に埋め込んでも確実に解決できるfile:// URL文字列に変換する。
     * Path.toUri()は日本語のようなASCII外の文字を含むパス（Googleドライブの「マイドライブ」フォルダ
     * など、ユーザーの同期先フォルダに由来する）を正しくパーセントエンコードしないことがあり、
     * WebViewのページ読み込み（engine.load）はこれを許容して自動補正するが、CSSのbackground-image:
     * url()経由のサブリソース読み込みは補正されず、画像が表示されないことを実機で確認した
     * （ノート本文中の画像<img src>で以前確認したのと同種の問題）。パスの各セグメントを
     * 個別にパーセントエンコードして組み立てることで、確実に有効なURLにする。
     */
    public static String toFileUrl(Path filePath) {
        Path absolute = filePath.toAbsolutePath().normalize();
        String root = absolute.getRoot() == null ? "" : absolute.getRoot().toString()
            .replace("\\", "").replace("/", "");

        StringBuilder url = new StringBuilder("file:///");
        if (!root.isEmpty()) {
            url.append(root).append('/');
        }
        for (int i = 0; i < absolute.getNameCount(); i++) {
            if (i > 0) {
                url.append('/');
            }
            String name = absolute.getName(i).toString();
            url.append(URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return url.toString();
    }
}
