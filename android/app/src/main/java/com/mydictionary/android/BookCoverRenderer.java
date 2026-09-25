package com.mydictionary.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.widget.ImageView;

import com.mydictionary.core.model.BookCover;

import java.io.File;

/**
 * ブックカバー（模様 or カスタム画像）をImageView1つに反映する共通処理。ブック一覧
 * （{@link com.mydictionary.android.ui.BookAdapter}）とカバー選択ダイアログ
 * （{@link com.mydictionary.android.ui.CoverPatternPickerButton}）の両方で同じ見た目になるよう、
 * ここに一本化する。模様・カスタム画像とも余白なくカバー全体に敷き詰めて表示する
 * （デスクトップ版のBookFormWidgetsと同じ見た目。模様画像は正方形素材のため縦横比を保たず
 * ぴったり引き伸ばす＝FIT_XY。PLAINなら模様なし）。tintHexはブックならテーマの文字色、
 * シェルフなら固定の中間色（{@link com.mydictionary.core.model.Shelf}参照）を渡す。
 */
public final class BookCoverRenderer {

    private BookCoverRenderer() {
    }

    public static void apply(ImageView imageView, BookCover cover, String tintHex) {
        imageView.setPadding(0, 0, 0, 0);

        if (cover.isCustom() && cover.getCustomImagePath() != null) {
            File file = BookCoverImages.resolveCoverImageFile(imageView.getContext(), cover.getCustomImagePath());
            if (file.isFile()) {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap != null) {
                    imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    imageView.setColorFilter(null);
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            }
        }

        BookCover.Pattern pattern = cover.isCustom() ? BookCover.Pattern.PLAIN : cover.getPattern();
        if (BookCoverPatterns.hasImage(pattern)) {
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setImageResource(BookCoverPatterns.drawableRes(pattern));
            imageView.setColorFilter(Color.parseColor(tintHex), PorterDuff.Mode.SRC_IN);
        } else {
            imageView.setImageDrawable(null);
            imageView.setColorFilter(null);
        }
    }
}
