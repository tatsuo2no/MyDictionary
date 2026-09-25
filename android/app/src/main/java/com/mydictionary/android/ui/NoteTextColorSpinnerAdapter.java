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

import com.mydictionary.core.model.NoteTextColor;

/**
 * 文字色名の横に、その色そのままを表示する□のカラーサンプルを添えるSpinnerアダプター
 * （デスクトップ版のノート作成画面と同じ見た目にするためのAndroid版実装。BookThemeSpinnerAdapterと
 * 同じ考え方）。
 */
public class NoteTextColorSpinnerAdapter extends ArrayAdapter<NoteTextColor> {

    public NoteTextColorSpinnerAdapter(@NonNull Context context, NoteTextColor[] colors) {
        super(context, 0, colors);
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
        NoteTextColor color = getItem(position);

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
        swatchDrawable.setColor(Color.parseColor(color.getColorCode()));
        swatchDrawable.setStroke(Math.max(1, (int) (1 * density)), Color.parseColor("#999999"));
        swatch.setBackground(swatchDrawable);

        TextView label = new TextView(context);
        label.setText(color.getDisplayName());
        label.setTextColor(Color.parseColor("#000000"));

        row.addView(swatch);
        row.addView(label);
        return row;
    }
}
