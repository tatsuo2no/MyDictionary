package com.mydictionary.android;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;

import androidx.webkit.WebViewAssetLoader;

import com.mydictionary.core.font.CustomFonts;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * デスクトップ版で追加され、同期でこの端末に届いたフォント（アプリ内部の{@code fonts}フォルダの.ttf）を
 * 扱う。ファイル名にファミリー名・太さ・斜体が入っているので（{@link CustomFonts}）、
 * フォントファイルの中身は解析せず、ファイル名だけで一覧とCSSを作る。
 */
public final class CustomFontFiles {

    /** WebViewAssetLoaderで、フォントフォルダを仮想ドメイン上に公開するパス。 */
    public static final String ASSET_PATH = "/" + CustomFonts.FOLDER_NAME + "/";
    private static final String ASSET_BASE_URL = "https://appassets.androidplatform.net" + ASSET_PATH;

    private static final Map<String, Typeface> TYPEFACE_CACHE = new HashMap<>();

    private CustomFontFiles() {
    }

    public static List<CustomFonts.Face> faces(Context context) {
        File[] files = AppPaths.getFontDir(context).listFiles();
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    names.add(file.getName());
                }
            }
        }
        return CustomFonts.parseAll(names);
    }

    public static List<String> families(Context context) {
        return CustomFonts.families(faces(context));
    }

    /** ノート本文のHTMLに埋め込む、追加フォント全部の{@code @font-face}規則（無ければ空文字）。 */
    public static String fontFaceCss(Context context) {
        return CustomFonts.fontFaceCss(faces(context), fileName -> ASSET_BASE_URL + Uri.encode(fileName));
    }

    /** WebViewAssetLoaderにフォントフォルダを登録するためのハンドラ。 */
    public static WebViewAssetLoader.PathHandler pathHandler(Context context) {
        return new WebViewAssetLoader.InternalStoragePathHandler(context, AppPaths.getFontDir(context));
    }

    /**
     * 画面部品(TextView等)で使うTypeface。標準(400)・非斜体に最も近いファイルを選ぶ。
     * 該当するフォントが無い、または読み込めない場合はnull（呼び出し側で組み込みフォントにする）。
     */
    public static synchronized Typeface typeface(Context context, String family) {
        CustomFonts.Face best = null;
        for (CustomFonts.Face face : faces(context)) {
            if (!face.family().equals(family)) {
                continue;
            }
            if (best == null || score(face) < score(best)) {
                best = face;
            }
        }
        if (best == null) {
            return null;
        }
        File file = new File(AppPaths.getFontDir(context), best.fileName());
        String key = file.getAbsolutePath() + ":" + file.lastModified();
        Typeface cached = TYPEFACE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Typeface typeface = Typeface.createFromFile(file);
            TYPEFACE_CACHE.put(key, typeface);
            return typeface;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int score(CustomFonts.Face face) {
        return Math.abs(face.weight() - 400) + (face.italic() ? 1000 : 0);
    }
}
