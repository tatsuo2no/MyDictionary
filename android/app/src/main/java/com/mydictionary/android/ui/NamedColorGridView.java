package com.mydictionary.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import com.mydictionary.core.markdown.CssNamedColors;

import java.util.List;

/**
 * ブラウザ定義済みの140色（色相順、7行×20列）を並べた色選択グリッド。
 * 画面幅に対して1マスが小さいため、タップの一発決定ではなく「指を置く/なぞると該当の色をハイライトして
 * 色名を通知し、指を離した位置の色で決定する」方式にして、押し間違いを防ぐ。
 */
final class NamedColorGridView extends View {

    interface Listener {
        /** 指が乗っている色（グリッドの外に出たらnull）。 */
        void onHover(CssNamedColors.NamedColor color);

        /** 指を離した位置の色で決定。 */
        void onPick(CssNamedColors.NamedColor color);
    }

    private final List<CssNamedColors.NamedColor> colors = CssNamedColors.all();
    private final Paint fillPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint highlightPaint = new Paint();
    private final int[] parsedColors;
    private int hoverIndex = -1;
    private Listener listener;

    NamedColorGridView(Context context) {
        super(context);
        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.parseColor("#888888"));
        borderPaint.setStrokeWidth(1f);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setColor(Color.BLACK);
        highlightPaint.setStrokeWidth(4f);
        parsedColors = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
            parsedColors[i] = Color.parseColor(colors.get(i).hex());
        }
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    private float cellSize() {
        return getWidth() / (float) CssNamedColors.COLUMNS;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = Math.round(width / (float) CssNamedColors.COLUMNS * CssNamedColors.ROWS);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = cellSize();
        for (int i = 0; i < colors.size(); i++) {
            float left = (i % CssNamedColors.COLUMNS) * cell;
            float top = (i / CssNamedColors.COLUMNS) * cell;
            fillPaint.setColor(parsedColors[i]);
            canvas.drawRect(left, top, left + cell, top + cell, fillPaint);
            canvas.drawRect(left, top, left + cell, top + cell, borderPaint);
        }
        if (hoverIndex >= 0) {
            float left = (hoverIndex % CssNamedColors.COLUMNS) * cell;
            float top = (hoverIndex / CssNamedColors.COLUMNS) * cell;
            canvas.drawRect(left, top, left + cell, top + cell, highlightPaint);
        }
    }

    private int indexAt(float x, float y) {
        float cell = cellSize();
        if (x < 0 || y < 0 || x >= getWidth() || cell <= 0) {
            return -1;
        }
        int column = (int) (x / cell);
        int row = (int) (y / cell);
        if (column >= CssNamedColors.COLUMNS || row >= CssNamedColors.ROWS) {
            return -1;
        }
        int index = row * CssNamedColors.COLUMNS + column;
        return index < colors.size() ? index : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                updateHover(indexAt(event.getX(), event.getY()));
                return true;
            case MotionEvent.ACTION_UP:
                int picked = indexAt(event.getX(), event.getY());
                updateHover(-1);
                if (picked >= 0 && listener != null) {
                    listener.onPick(colors.get(picked));
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                updateHover(-1);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void updateHover(int index) {
        if (index == hoverIndex) {
            return;
        }
        hoverIndex = index;
        invalidate();
        if (listener != null) {
            listener.onHover(index >= 0 ? colors.get(index) : null);
        }
    }
}
