package com.mydictionary.android.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;

import com.mydictionary.android.BookCoverRenderer;
import com.mydictionary.android.R;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookTheme;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ブックカバーのプリセット模様を、Spinner（プルダウン）ではなくTeams等の絵文字ピッカーのような
 * グリッド形式のダイアログから選択できるようにするビュー（デスクトップ版のCoverPatternPickerButton
 * と同じ考え方のAndroid版実装）。「参照」ボタンで選んだカスタム画像も同じ表示領域にそのまま
 * 反映できるよう、扱う値はプリセットではなく{@link BookCover}そのもの。プレビューは常に
 * 「現在選ばれている背景色/模様色」（ブックならテーマの背景色/文字色、シェルフなら固定の中間色）
 * で描くため、その配色が変わったら{@link #refresh()}を呼んで再描画する必要がある。
 */
public class CoverPatternPickerButton extends FrameLayout {
    private static final int COLUMNS = 5;
    private static final float OPTION_SCALE = 0.5f;

    private ImageView coverImageView;
    private Supplier<String> backgroundHexSupplier = () -> BookTheme.WHITE.getColorCode();
    private Supplier<String> tintHexSupplier = () -> BookTheme.WHITE.getTextColorCode();
    private BookCover value = BookCover.ofPattern(BookCover.Pattern.STRIPES);
    private Consumer<BookCover> onChange;

    public CoverPatternPickerButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        coverImageView = new ImageView(context);
        addView(coverImageView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setOnClickListener(v -> showPicker());
        refresh();
    }

    public void setColorSuppliers(Supplier<String> backgroundHexSupplier, Supplier<String> tintHexSupplier) {
        this.backgroundHexSupplier = backgroundHexSupplier;
        this.tintHexSupplier = tintHexSupplier;
        refresh();
    }

    public void setOnChange(Consumer<BookCover> onChange) {
        this.onChange = onChange;
    }

    public BookCover getValue() {
        return value;
    }

    public void setValue(BookCover cover) {
        this.value = cover;
        refresh();
    }

    /** 背景色/模様色が変わったときに呼び、プレビューを合わせ直す。 */
    public void refresh() {
        BookCoverRenderer.apply(coverImageView, value, tintHexSupplier.get());
        setBackground(coverBackground(backgroundHexSupplier.get()));
    }

    private void showPicker() {
        Context context = getContext();
        String backgroundHex = backgroundHexSupplier.get();
        String tintHex = tintHexSupplier.get();

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(COLUMNS);
        int padding = dp(16);
        grid.setPadding(padding, padding, padding, padding);

        AlertDialog dialog = new AlertDialog.Builder(context)
            .setTitle(R.string.dialog_title_cover_pattern_picker)
            .setView(grid)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        int optionWidth = Math.round(dp(BookCover.WIDTH_PX) * OPTION_SCALE);
        int optionHeight = Math.round(dp(BookCover.HEIGHT_PX) * OPTION_SCALE);
        int margin = dp(4);

        for (BookCover.Pattern pattern : BookCover.Pattern.values()) {
            BookCover optionCover = BookCover.ofPattern(pattern);

            ImageView optionView = new ImageView(context);
            BookCoverRenderer.apply(optionView, optionCover, tintHex);

            boolean selected = !value.isCustom() && value.getPattern() == pattern;
            GradientDrawable optionBackground = coverBackgroundDrawable(backgroundHex);
            optionBackground.setStroke(dp(selected ? 3 : 1), Color.parseColor(selected ? "#2A6EBB" : "#999999"));
            optionView.setBackground(optionBackground);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = optionWidth;
            params.height = optionHeight;
            params.setMargins(margin, margin, margin, margin);
            optionView.setLayoutParams(params);

            optionView.setOnClickListener(v -> {
                setValue(optionCover);
                if (onChange != null) {
                    onChange.accept(optionCover);
                }
                dialog.dismiss();
            });
            grid.addView(optionView);
        }

        dialog.show();
    }

    private GradientDrawable coverBackground(String backgroundHex) {
        GradientDrawable drawable = coverBackgroundDrawable(backgroundHex);
        drawable.setStroke(dp(1), Color.parseColor("#999999"));
        return drawable;
    }

    private GradientDrawable coverBackgroundDrawable(String backgroundHex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(backgroundHex));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getContext().getResources().getDisplayMetrics().density);
    }
}
