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
import com.mydictionary.android.CustomFontFiles;
import com.mydictionary.android.R;
import com.mydictionary.core.markdown.CssNamedColors;
import com.mydictionary.core.markdown.HeadingStyles;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * ノート編集画面の本文エディタ（{@link RichNoteEditor}）の上に置く書式設定ツールバー（デスクトップ版の
 * NoteFormatToolbarと同じ機能・同じ並び）。ボタンを押すとエディタで選択中の範囲に書式を適用する。
 * 画面が狭いため、ボタンは4つのリボンに分けて1つずつ表示し、左端の切り替えボタンで巡回する。
 * リボン④の「Markdown編集」を押すと書式ボタンを隠し、本文をMarkdownのまま編集する欄に切り替える
 * （数式のTeX記法などを直接書くとき用。切り替えは{@code onMarkdownMode}で画面側へ知らせる）。
 */
final class NoteFormatToolbar {
    private static final String[] FONT_SIZES_PT = {
        "8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "28", "32", "36", "48", "72"};
    private static final int MAX_TABLE_ROWS = 100;
    private static final int MAX_TABLE_COLUMNS = 20;
    private static final int BUTTON_HEIGHT_DP = 44;
    /** 1つのリボンに最大6個並ぶため、画面幅（約360dp）に収まるよう狭めにしている。 */
    private static final int BUTTON_WIDTH_DP = 42;
    private static final int RIBBON_BUTTON_WIDTH_DP = 38;
    private static final int RIBBON_COUNT = 4;
    /** ルビ・テーブルのアイコン画像（PNG）を表示する大きさ。 */
    private static final int ICON_IMAGE_SIZE_DP = 24;
    /** 見出しウィンドウに出す見出しレベルは1〜5（レベル6は本文と同じ大きさなので出さない）。 */
    private static final int MAX_HEADING_LEVEL = 5;

    private final Activity activity;
    private final RichNoteEditor editor;
    private final Supplier<List<String>> fontFamilies;
    private final Runnable onInsertImage;
    private final Consumer<Boolean> onMarkdownMode;
    private final String bookFontFamily;
    private final int baseFontSizePt;
    /**
     * 4つのリボン（ボタンの並び）。①文字の装飾 ②フォント・ルビ・配置 ③見出し・リスト・挿入 ④引用・コード・Markdown編集。
     * 画面には常に1つだけ出し、左端の切り替えボタンで ①→②→③→④→① と巡回する。幅が足りない端末のために横スクロール。
     */
    private final List<HorizontalScrollView> ribbons = new ArrayList<>();
    private final List<LinearLayout> ribbonRows = new ArrayList<>();
    /** リボン④のうち、Markdown編集中は隠す書式ボタン（「Markdown編集」ボタン以外）。 */
    private final List<View> markdownModeHidden = new ArrayList<>();
    private LinearLayout currentRow;
    private int ribbonIndex;
    private boolean markdownMode;

    /**
     * bookFontFamily・baseFontSizePtは、見出しウィンドウで「ノートで実際に表示される大きさ」を
     * サンプル表示するための、このブックの本文フォントと標準サイズ(pt)。
     */
    NoteFormatToolbar(Activity activity, LinearLayout container, RichNoteEditor editor,
                      Supplier<List<String>> fontFamilies, Runnable onInsertImage,
                      Consumer<Boolean> onMarkdownMode, String bookFontFamily, int baseFontSizePt) {
        this.activity = activity;
        this.editor = editor;
        this.fontFamilies = fontFamilies;
        this.onInsertImage = onInsertImage;
        this.onMarkdownMode = onMarkdownMode;
        this.bookFontFamily = bookFontFamily;
        this.baseFontSizePt = baseFontSizePt;

        for (int i = 0; i < RIBBON_COUNT; i++) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            ribbonRows.add(row);
            HorizontalScrollView scroll = new HorizontalScrollView(activity);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(row);
            ribbons.add(scroll);
        }
        buildButtons();

        // 左端に固定表示するリボン切り替えボタン（現在のリボン番号を表示し、押すたびに次のリボンへ）。
        Button ribbonButton = baseButton(ribbonLabel(0));
        ribbonButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        ribbonButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        ribbonButton.setLayoutParams(buttonParams(dp(RIBBON_BUTTON_WIDTH_DP)));
        decorate(ribbonButton, R.string.fmt_ribbon_switch, () -> {
            ribbonIndex = (ribbonIndex + 1) % RIBBON_COUNT;
            ribbonButton.setText(ribbonLabel(ribbonIndex));
            showRibbon(ribbonIndex);
        });
        this.ribbonButton = ribbonButton;

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(ribbonButton);
        for (HorizontalScrollView scroll : ribbons) {
            bar.addView(scroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        showRibbon(0);

        container.removeAllViews();
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(bar);
    }

    private Button ribbonButton;

    private static String ribbonLabel(int index) {
        return String.valueOf((char) ('①' + index));
    }

    private void showRibbon(int index) {
        for (int i = 0; i < ribbons.size(); i++) {
            ribbons.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 「Markdown編集」の切り替え。編集中は書式ボタンを隠し、本文欄へ記法を直接書けるようにする
     * （数式のTeX記法など）。戻すボタンはリボン④にあるため、編集中はそのボタンだけを残してリボン④を出す。
     */
    private void toggleMarkdownMode(Button modeButton) {
        markdownMode = !markdownMode;
        ribbonButton.setVisibility(markdownMode ? View.GONE : View.VISIBLE);
        for (View view : markdownModeHidden) {
            view.setVisibility(markdownMode ? View.GONE : View.VISIBLE);
        }
        showRibbon(markdownMode ? RIBBON_COUNT - 1 : ribbonIndex);
        modeButton.setText(activity.getString(markdownMode
            ? R.string.fmt_markdown_mode_off : R.string.fmt_markdown_mode));
        onMarkdownMode.accept(markdownMode);
    }

    private void buildButtons() {
        // ---- リボン①: 文字の装飾
        currentRow = ribbonRows.get(0);
        add(textButton("B", Typeface.BOLD, R.string.fmt_bold, editor::bold));
        add(textButton("I", Typeface.ITALIC, R.string.fmt_italic, editor::italic));

        Button underline = textButton("U", Typeface.NORMAL, R.string.fmt_underline, editor::underline);
        SpannableString u = new SpannableString("U");
        u.setSpan(new UnderlineSpan(), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        underline.setText(u);
        add(underline);

        Button strike = textButton("S", Typeface.NORMAL, R.string.fmt_strikethrough, editor::strikethrough);
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

        // ---- リボン②: フォント・サイズ・ルビ・配置
        currentRow = ribbonRows.get(1);
        add(wideButton(activity.getString(R.string.fmt_font), R.string.fmt_font, this::showFontDialog));
        add(wideButton(activity.getString(R.string.fmt_font_size), R.string.fmt_font_size, this::showSizeDialog));
        add(iconButton(R.drawable.ic_ruby, ICON_IMAGE_SIZE_DP, R.string.fmt_ruby, this::onRuby));

        add(iconButton(R.drawable.ic_align_left, 0, R.string.fmt_align_left, () -> align("left")));
        add(iconButton(R.drawable.ic_align_center, 0, R.string.fmt_align_center, () -> align("center")));
        add(iconButton(R.drawable.ic_align_right, 0, R.string.fmt_align_right, () -> align("right")));

        // ---- リボン③: 見出し・リスト・挿入
        currentRow = ribbonRows.get(2);
        add(wideButton(activity.getString(R.string.fmt_heading), R.string.fmt_heading_tooltip,
            this::showHeadingDialog));
        add(textButton("•―", Typeface.BOLD, R.string.fmt_bullet_list, editor::toggleBulletList));
        add(textButton("1.", Typeface.BOLD, R.string.fmt_numbered_list, editor::toggleNumberedList));

        add(iconButton(R.drawable.ic_photo, 0, R.string.action_insert_image, onInsertImage));
        add(iconButton(R.drawable.ic_table, ICON_IMAGE_SIZE_DP, R.string.fmt_table, this::onInsertTable));
        add(textButton("―", Typeface.BOLD, R.string.fmt_horizontal_rule, editor::insertHorizontalRule));

        // ---- リボン④: 引用・コード・Markdown編集
        currentRow = ribbonRows.get(3);
        markdownModeHidden.add(add(textButton("＞", Typeface.BOLD, R.string.fmt_quote, editor::toggleQuote)));
        markdownModeHidden.add(add(textButton("”", Typeface.BOLD, R.string.fmt_inline_code, editor::inlineCode)));
        markdownModeHidden.add(add(textButton("</>", Typeface.BOLD, R.string.fmt_code_block,
            editor::toggleCodeBlock)));

        Button modeButton = wideButton(activity.getString(R.string.fmt_markdown_mode),
            R.string.fmt_markdown_mode, null);
        modeButton.setOnClickListener(v -> toggleMarkdownMode(modeButton));
        add(modeButton);
    }

    private View add(View button) {
        currentRow.addView(button);
        return button;
    }

    /**
     * 見出しレベル5→1の順に、このブックの標準フォントサイズを基準に実際に表示される大きさで
     * 1行ずつサンプルを並べたウィンドウ。選んだレベルを本文欄の選択行（無ければカーソル行）に適用する。
     * WebViewではCSSの1pxが1dpに当たるため、ptは「px換算(pt×4/3)」のsp/dpで表示して実際の見た目に合わせる。
     */
    private void showHeadingDialog() {
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(4), dp(8), dp(4));

        Typeface family = CustomFontFiles.typeface(activity, bookFontFamily);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.fmt_heading_title, baseFontSizePt))
            .setView(list)
            .setNeutralButton(R.string.fmt_heading_clear, (d, which) -> editor.setHeading(0))
            .setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();

        for (int level = MAX_HEADING_LEVEL; level >= HeadingStyles.MIN_LEVEL; level--) {
            int chosen = level;
            double pt = HeadingStyles.sizePt(level, baseFontSizePt);
            TextView row = new TextView(activity);
            row.setText(activity.getString(R.string.fmt_heading_row_format, level, HeadingStyles.formatPt(pt)));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float) (pt * 4.0 / 3.0));
            row.setTypeface(family, Typeface.BOLD);
            row.setTextColor(Color.BLACK);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                editor.setHeading(chosen);
            });
            list.addView(row);
        }
        dialog.show();
    }

    // ---------------------------------------------------------------- 操作

    private void applyStyle(String property, String value) {
        editor.setStyle(property, value);
    }

    private void align(String align) {
        editor.align(align);
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
        editor.selectedText(selected -> {
            if (selected.trim().isEmpty()) {
                new AlertDialog.Builder(activity)
                    .setMessage(R.string.fmt_ruby_select_first)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
                return;
            }
            EditText input = new EditText(activity);
            input.setSingleLine(true);
            new AlertDialog.Builder(activity)
                .setTitle(R.string.fmt_ruby_title)
                .setMessage(activity.getString(R.string.fmt_ruby_message, selected.trim()))
                .setView(wrapWithMargin(input))
                .setPositiveButton(android.R.string.ok, (d, which) ->
                    editor.setRuby(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        });
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
                    editor.insertTable(r, c);
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
        button.setLayoutParams(buttonParams(dp(BUTTON_WIDTH_DP)));
        decorate(button, tooltipRes, action);
        return button;
    }

    private Button wideButton(String text, int tooltipRes, Runnable action) {
        Button button = baseButton(text);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setLayoutParams(buttonParams(LinearLayout.LayoutParams.WRAP_CONTENT));
        decorate(button, tooltipRes, action);
        return button;
    }

    /** iconSizeDpが0なら、アイコン自身の大きさ（ベクター画像）で表示する。画像（PNG）は大きさを指定する。 */
    private Button iconButton(int drawableRes, int iconSizeDp, int tooltipRes, Runnable action) {
        MaterialButton button = (MaterialButton) baseButton("");
        button.setIconResource(drawableRes);
        if (iconSizeDp > 0) {
            button.setIconSize(dp(iconSizeDp));
        }
        button.setIconTint(null);
        button.setIconPadding(0);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setPadding(dp(11), 0, dp(11), 0);
        button.setLayoutParams(buttonParams(dp(BUTTON_WIDTH_DP)));
        decorate(button, tooltipRes, action);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, dp(BUTTON_HEIGHT_DP));
        params.setMargins(dp(1), 0, dp(1), 0);
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
