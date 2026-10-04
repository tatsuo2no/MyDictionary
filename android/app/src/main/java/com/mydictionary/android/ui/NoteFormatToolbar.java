package com.mydictionary.android.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;

import com.google.android.material.button.MaterialButton;
import com.mydictionary.android.R;
import com.mydictionary.core.markdown.CssNamedColors;
import com.mydictionary.core.markdown.MarkdownEditing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * ノート編集画面の本文欄（EditText）の上に置く書式設定ツールバー（デスクトップ版の
 * NoteFormatToolbarと同じ機能・同じ並び）。ボタンを押すと本文欄で選択中の範囲にMarkdown風記法を
 * 自動で挿入/解除する。変換ロジックは両OS共通のcoreの{@link MarkdownEditing}。
 * 画面幅に収まらないため書式ボタンは横スクロールで、先頭の「Markdown編集」ボタンだけは固定表示して
 * 押すと書式ボタンを隠せる（数式のTeX記法などを直接書くとき用）。
 */
final class NoteFormatToolbar {
    private static final String[] FONT_SIZES_PT = {
        "8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "28", "32", "36", "48", "72"};
    private static final int MAX_TABLE_ROWS = 100;
    private static final int MAX_TABLE_COLUMNS = 20;
    private static final int BUTTON_SIZE_DP = 44;

    private final Activity activity;
    private final EditText body;
    private final Supplier<List<String>> fontFamilies;
    private final Runnable onInsertImage;
    private final LinearLayout buttonRow;

    NoteFormatToolbar(Activity activity, LinearLayout container, EditText body,
                      Supplier<List<String>> fontFamilies, Runnable onInsertImage) {
        this.activity = activity;
        this.body = body;
        this.fontFamilies = fontFamilies;
        this.onInsertImage = onInsertImage;

        buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buildButtons();

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(buttonRow);

        Button modeButton = baseButton(activity.getString(R.string.fmt_markdown_mode));
        modeButton.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(BUTTON_SIZE_DP)));
        modeButton.setPadding(dp(8), 0, dp(8), 0);
        modeButton.setOnClickListener(v -> {
            boolean markdownMode = scroll.getVisibility() == View.VISIBLE;
            scroll.setVisibility(markdownMode ? View.GONE : View.VISIBLE);
            modeButton.setText(activity.getString(markdownMode
                ? R.string.fmt_markdown_mode_off : R.string.fmt_markdown_mode));
        });

        container.removeAllViews();
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(modeButton);
        container.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }

    private void buildButtons() {
        add(textButton("B", Typeface.BOLD, R.string.fmt_bold, () -> wrap("**")));
        add(textButton("I", Typeface.ITALIC, R.string.fmt_italic, () -> wrap("*")));

        Button underline = textButton("U", Typeface.NORMAL, R.string.fmt_underline, () -> wrap("__"));
        SpannableString u = new SpannableString("U");
        u.setSpan(new UnderlineSpan(), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        underline.setText(u);
        add(underline);

        Button strike = textButton("S", Typeface.NORMAL, R.string.fmt_strikethrough, () -> wrap("~~"));
        SpannableString s = new SpannableString("S");
        s.setSpan(new StrikethroughSpan(), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        strike.setText(s);
        add(strike);

        Button textColor = textButton("A", Typeface.BOLD, R.string.fmt_text_color, null);
        textColor.setTextColor(Color.parseColor("#FF8A80"));
        textColor.setOnClickListener(v -> showColorDialog(R.string.fmt_text_color, "color"));
        add(textColor);

        Button backgroundColor = textButton("A", Typeface.BOLD, R.string.fmt_background_color, null);
        SpannableString bg = new SpannableString("A");
        bg.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        backgroundColor.setText(bg);
        backgroundColor.setTextColor(Color.BLACK);
        backgroundColor.setOnClickListener(v -> showColorDialog(R.string.fmt_background_color, "background-color"));
        add(backgroundColor);

        add(wideButton(activity.getString(R.string.fmt_font), R.string.fmt_font, this::showFontDialog));
        add(wideButton(activity.getString(R.string.fmt_font_size), R.string.fmt_font_size, this::showSizeDialog));
        add(wideButton(activity.getString(R.string.fmt_ruby), R.string.fmt_ruby, this::onRuby));

        add(iconButton(R.drawable.ic_align_left, R.string.fmt_align_left, () -> align("left")));
        add(iconButton(R.drawable.ic_align_center, R.string.fmt_align_center, () -> align("center")));
        add(iconButton(R.drawable.ic_align_right, R.string.fmt_align_right, () -> align("right")));

        add(textButton("•―", Typeface.BOLD, R.string.fmt_bullet_list,
            () -> edit(t -> MarkdownEditing.toggleBulletList(t, selStart(), selEnd()))));
        add(textButton("1.", Typeface.BOLD, R.string.fmt_numbered_list,
            () -> edit(t -> MarkdownEditing.toggleNumberedList(t, selStart(), selEnd()))));

        add(iconButton(R.drawable.ic_photo, R.string.action_insert_image, onInsertImage));
        add(textButton("田", Typeface.BOLD, R.string.fmt_table, this::onInsertTable));
        add(textButton("―", Typeface.BOLD, R.string.fmt_horizontal_rule,
            () -> edit(t -> MarkdownEditing.insertHorizontalRule(t, selStart(), selEnd()))));
        add(textButton("＞", Typeface.BOLD, R.string.fmt_quote,
            () -> edit(t -> MarkdownEditing.toggleQuote(t, selStart(), selEnd()))));
        add(textButton("”", Typeface.BOLD, R.string.fmt_inline_code, () -> wrap("`")));
        add(textButton("</>", Typeface.BOLD, R.string.fmt_code_block,
            () -> edit(t -> MarkdownEditing.wrapCodeBlock(t, selStart(), selEnd()))));
    }

    private void add(View button) {
        buttonRow.addView(button);
    }

    // ---------------------------------------------------------------- 操作

    /** 選択範囲の開始。一度もフォーカスされておらず選択位置が無い場合は本文末尾を指す。 */
    private int selStart() {
        int s = body.getSelectionStart();
        return s < 0 ? body.getText().length() : s;
    }

    private int selEnd() {
        int e = body.getSelectionEnd();
        return e < 0 ? body.getText().length() : e;
    }

    private void edit(Function<String, MarkdownEditing.Result> transform) {
        applyResult(transform.apply(body.getText().toString()));
    }

    private void wrap(String marker) {
        edit(t -> MarkdownEditing.toggleWrap(t, selStart(), selEnd(), marker));
    }

    private void applyStyle(String property, String value) {
        edit(t -> MarkdownEditing.setSpanStyle(t, selStart(), selEnd(), property, value));
    }

    private void align(String align) {
        edit(t -> MarkdownEditing.setAlignment(t, selStart(), selEnd(), align));
    }

    private void applyResult(MarkdownEditing.Result result) {
        Editable editable = body.getText();
        if (!result.text().contentEquals(editable)) {
            editable.replace(0, editable.length(), result.text());
        }
        body.setSelection(result.selectionStart(), result.selectionEnd());
        body.requestFocus();
    }

    private void showColorDialog(int titleRes, String property) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(12), dp(8), dp(12), 0);

        TextView hint = new TextView(activity);
        hint.setText(R.string.fmt_color_hint);
        layout.addView(hint);

        NamedColorGridView grid = new NamedColorGridView(activity);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = dp(8);
        layout.addView(grid, gridParams);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(layout)
            .setNeutralButton(R.string.fmt_clear_color, (d, which) -> applyStyle(property, null))
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        grid.setListener(new NamedColorGridView.Listener() {
            @Override
            public void onHover(CssNamedColors.NamedColor color) {
                hint.setText(color == null ? activity.getString(R.string.fmt_color_hint)
                    : color.name() + "  " + color.hex());
            }

            @Override
            public void onPick(CssNamedColors.NamedColor color) {
                dialog.dismiss();
                applyStyle(property, color.name());
            }
        });
        dialog.show();
    }

    private void showFontDialog() {
        List<String> families = fontFamilies.get();
        List<String> items = new ArrayList<>();
        items.add(activity.getString(R.string.fmt_reset_font));
        items.addAll(families);
        new AlertDialog.Builder(activity)
            .setTitle(R.string.fmt_font)
            .setItems(items.toArray(new String[0]), (d, which) ->
                applyStyle("font-family", which == 0 ? null : "'" + families.get(which - 1) + "'"))
            .show();
    }

    private void showSizeDialog() {
        List<String> items = new ArrayList<>();
        items.add(activity.getString(R.string.fmt_reset_font_size));
        for (String size : FONT_SIZES_PT) {
            items.add(size + "pt");
        }
        new AlertDialog.Builder(activity)
            .setTitle(R.string.fmt_font_size)
            .setItems(items.toArray(new String[0]), (d, which) ->
                applyStyle("font-size", which == 0 ? null : FONT_SIZES_PT[which - 1] + "pt"))
            .show();
    }

    private void onRuby() {
        int s = Math.min(selStart(), selEnd());
        int e = Math.max(selStart(), selEnd());
        if (s == e) {
            new AlertDialog.Builder(activity)
                .setMessage(R.string.fmt_ruby_select_first)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return;
        }
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        String selected = body.getText().toString().substring(s, e).trim();
        new AlertDialog.Builder(activity)
            .setTitle(R.string.fmt_ruby_title)
            .setMessage(activity.getString(R.string.fmt_ruby_message, selected))
            .setView(wrapWithMargin(input))
            .setPositiveButton(android.R.string.ok, (d, which) -> applyResult(
                MarkdownEditing.applyRuby(body.getText().toString(), s, e, input.getText().toString())))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void onInsertTable() {
        EditText rows = numberField("3");
        EditText columns = numberField("3");

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(8), dp(20), 0);
        layout.addView(label(R.string.fmt_table_rows));
        layout.addView(rows);
        layout.addView(label(R.string.fmt_table_columns));
        layout.addView(columns);
        TextView hint = new TextView(activity);
        hint.setText(activity.getString(R.string.fmt_table_hint, MAX_TABLE_ROWS, MAX_TABLE_COLUMNS));
        hint.setTextColor(Color.GRAY);
        layout.addView(hint);

        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.fmt_table_title)
            .setView(layout)
            .setPositiveButton(R.string.fmt_insert, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
        dialog.setOnShowListener(d -> {
            Button insert = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Runnable validate = () -> insert.setEnabled(
                parseInRange(rows.getText().toString(), MAX_TABLE_ROWS) != null
                    && parseInRange(columns.getText().toString(), MAX_TABLE_COLUMNS) != null);
            android.text.TextWatcher watcher = new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    validate.run();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            };
            rows.addTextChangedListener(watcher);
            columns.addTextChangedListener(watcher);
            validate.run();
            insert.setOnClickListener(v -> {
                Integer r = parseInRange(rows.getText().toString(), MAX_TABLE_ROWS);
                Integer c = parseInRange(columns.getText().toString(), MAX_TABLE_COLUMNS);
                if (r != null && c != null) {
                    dialog.dismiss();
                    edit(t -> MarkdownEditing.insertTable(t, selStart(), selEnd(), r, c));
                }
            });
        });
        dialog.show();
    }

    private static Integer parseInRange(String text, int max) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 1 && value <= max ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 部品

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
            activity.getResources().getDisplayMetrics()));
    }

    private Button baseButton(String text) {
        // 他の画面のボタン（レイアウトXMLのButtonはMaterialButtonに置き換えられて紫になる）と
        // 見た目を揃えるため、コードから作る場合も最初からMaterialButtonにする。
        MaterialButton button = new MaterialButton(activity);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private Button textButton(String text, int style, int tooltipRes, Runnable action) {
        Button button = baseButton(text);
        button.setTypeface(Typeface.DEFAULT, style);
        button.setLayoutParams(buttonParams(dp(BUTTON_SIZE_DP)));
        decorate(button, tooltipRes, action);
        return button;
    }

    private Button wideButton(String text, int tooltipRes, Runnable action) {
        Button button = baseButton(text);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setLayoutParams(buttonParams(LinearLayout.LayoutParams.WRAP_CONTENT));
        decorate(button, tooltipRes, action);
        return button;
    }

    private Button iconButton(int drawableRes, int tooltipRes, Runnable action) {
        MaterialButton button = (MaterialButton) baseButton("");
        button.setIconResource(drawableRes);
        button.setIconTint(null);
        button.setIconPadding(0);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setLayoutParams(buttonParams(dp(BUTTON_SIZE_DP)));
        decorate(button, tooltipRes, action);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(BUTTON_SIZE_DP));
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private void decorate(Button button, int tooltipRes, Runnable action) {
        button.setContentDescription(activity.getString(tooltipRes));
        TooltipCompat.setTooltipText(button, activity.getString(tooltipRes));
        if (action != null) {
            button.setOnClickListener(v -> action.run());
        }
    }

    private EditText numberField(String initial) {
        EditText field = new EditText(activity);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(initial);
        field.setSelectAllOnFocus(true);
        return field;
    }

    private TextView label(int textRes) {
        TextView label = new TextView(activity);
        label.setText(textRes);
        label.setPadding(0, dp(8), 0, 0);
        return label;
    }

    private View wrapWithMargin(View view) {
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setPadding(dp(20), 0, dp(20), 0);
        wrapper.addView(view, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrapper;
    }
}
