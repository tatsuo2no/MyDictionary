package com.mydictionary.android.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.mydictionary.core.model.BookTheme;

/**
 * テーマ名の横に、そのテーマの色そのままを表示する□のカラーサンプルを添えるSpinnerアダプター
 * （デスクトップ版のブック作成/オプション画面と同じ見た目にするためのAndroid版実装）。
 */
public class BookThemeSpinnerAdapter extends ArrayAdapter<BookTheme> {

    public BookThemeSpinnerAdapter(@NonNull Context context, BookTheme[] themes) {
        super(context, 0, themes);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        return buildRow(position, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        return buildRow(position, parent);
    }

    private View buildRow(int position, ViewGroup parent) {
        Context context = parent.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        BookTheme theme = getItem(position);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padding = (int) (8 * density);
        row.setPadding(padding, padding, padding, padding);

        View swatch = new View(context);
        int size = (int) (18 * density);
        LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(size, size);
        swatchParams.setMarginEnd((int) (8 * density));
        swatch.setLayoutParams(swatchParams);

        GradientDrawable swatchDrawable = new GradientDrawable();
        swatchDrawable.setColor(Color.parseColor(theme.getColorCode()));
        swatchDrawable.setStroke(Math.max(1, (int) (1 * density)), Color.parseColor("#999999"));
        swatch.setBackground(swatchDrawable);

        TextView label = new TextView(context);
        label.setText(theme.getDisplayName());
        label.setTextColor(Color.parseColor("#000000"));

        row.addView(swatch);
        row.addView(label);
        return row;
    }
}
