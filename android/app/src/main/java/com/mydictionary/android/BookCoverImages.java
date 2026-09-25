package com.mydictionary.android;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.mydictionary.core.model.BookCover;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 「参照」ボタンで選んだ画像を、ブックカバーの表示サイズ（{@link BookCover#WIDTH_PX}×
 * {@link BookCover#HEIGHT_PX}）にアスペクト比を保ったまま拡大縮小し、はみ出た部分を中央基準で
 * 切り抜いてPNGとして保存する（デスクトップ版のBookFormWidgets.saveCustomCoverImageと同じ処理）。
 * 保存先は既存のノート画像と同じ同期対象フォルダ（{@link AppPaths#getImageDir}）とし、
 * デスクトップ⇔Android間で自動的に同期されるようにする。
 * 戻り値は絶対パスではなく**ファイル名のみ**（ノート内画像と同じ方式）。絶対パスをDBに保存すると
 * デスクトップ⇔Android間で同期した際に相手側のOSでは存在しないパスになり、カスタム画像が
 * 表示できなくなる不具合があったため、ファイル名だけを保存し表示時に{@link #resolveCoverImageFile}
 * で都度その端末の画像フォルダから解決する方式に修正した（2026-09）。
 */
public final class BookCoverImages {

    private BookCoverImages() {
    }

    /**
     * カスタムカバー画像のDB保存値をファイルとして解決する。新形式（ファイル名のみ）はこの端末の
     * 画像フォルダ内を見る。修正前の形式（絶対パス）で保存された既存データとの後方互換のため、
     * 値がこの端末上で実在する絶対パスの場合はそれをそのまま使う（同期元と同じ端末で見る場合のみ有効）。
     */
    public static File resolveCoverImageFile(Context context, String storedValue) {
        File direct = new File(storedValue);
        if (direct.isAbsolute() && direct.isFile()) {
            return direct;
        }
        return new File(AppPaths.getImageDir(context), storedValue);
    }

    public static String saveCustomCoverImage(Context context, Uri sourceUri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        Bitmap source;
        try (InputStream in = resolver.openInputStream(sourceUri)) {
            if (in == null) {
                throw new IOException("画像を開けませんでした");
            }
            source = BitmapFactory.decodeStream(in);
        }
        if (source == null) {
            throw new IOException("画像として読み込めませんでした");
        }
        Bitmap resized = cropToCover(source, BookCover.WIDTH_PX, BookCover.HEIGHT_PX);

        File imageDir = AppPaths.getImageDir(context);
        if (!imageDir.exists() && !imageDir.mkdirs()) {
            throw new IOException("画像保存先フォルダを作成できませんでした");
        }
        String fileName = System.currentTimeMillis() + "_cover.png";
        File targetFile = new File(imageDir, fileName);
        try (FileOutputStream out = new FileOutputStream(targetFile)) {
            resized.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return fileName;
    }

    private static Bitmap cropToCover(Bitmap source, int targetWidth, int targetHeight) {
        float scale = Math.max((float) targetWidth / source.getWidth(), (float) targetHeight / source.getHeight());
        int scaledWidth = Math.max(targetWidth, Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(targetHeight, Math.round(source.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);

        int x = (scaledWidth - targetWidth) / 2;
        int y = (scaledHeight - targetHeight) / 2;
        return Bitmap.createBitmap(scaled, x, y, targetWidth, targetHeight);
    }
}
