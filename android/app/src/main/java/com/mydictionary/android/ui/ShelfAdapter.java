package com.mydictionary.android.ui;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mydictionary.android.BookCoverRenderer;
import com.mydictionary.android.R;
import com.mydictionary.core.model.Shelf;

import java.util.List;

public class ShelfAdapter extends RecyclerView.Adapter<ShelfAdapter.ShelfViewHolder> {

    public interface Listener {
        void onShelfClick(Shelf shelf);

        void onShelfLongClick(Shelf shelf);
    }

    /** ドラッグハンドルに触れたときに、ItemTouchHelper へドラッグ開始を伝えるためのコールバック。 */
    public interface DragStarter {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    private final List<Shelf> shelves;
    private final Listener listener;
    private final DragStarter dragStarter;

    public ShelfAdapter(List<Shelf> shelves, Listener listener, DragStarter dragStarter) {
        this.shelves = shelves;
        this.listener = listener;
        this.dragStarter = dragStarter;
    }

    @NonNull
    @Override
    public ShelfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shelf, parent, false);
        return new ShelfViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull ShelfViewHolder holder, int position) {
        Shelf shelf = shelves.get(position);
        holder.nameView.setText(shelf.getName());

        Drawable background = holder.iconView.getBackground().mutate();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background).setColor(Color.parseColor(Shelf.COVER_BACKGROUND_COLOR_CODE));
        }
        holder.iconView.setBackground(background);

        BookCoverRenderer.apply(holder.coverPatternView, shelf.getCover(), Shelf.COVER_TINT_COLOR_CODE);

        holder.itemView.setOnClickListener(v -> listener.onShelfClick(shelf));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onShelfLongClick(shelf);
            return true;
        });

        holder.dragHandleView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragStarter.onStartDrag(holder);
                // DOWNを消費して、カード本体の長押し（＝シェルフオプションを開く）が発火しないようにする。
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return shelves.size();
    }

    /** ドラッグ並び替え中に、表示中のリスト内で1件を移動する。 */
    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= shelves.size() || toPosition >= shelves.size()) {
            return;
        }
        Shelf moved = shelves.remove(fromPosition);
        shelves.add(toPosition, moved);
        notifyItemMoved(fromPosition, toPosition);
    }

    /** ドラッグ終了時に、確定した並び順を取り出すための現在のリスト。 */
    public List<Shelf> currentShelves() {
        return shelves;
    }

    static class ShelfViewHolder extends RecyclerView.ViewHolder {
        final View iconView;
        final ImageView coverPatternView;
        final ImageView dragHandleView;
        final TextView nameView;

        ShelfViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.shelfIconView);
            coverPatternView = itemView.findViewById(R.id.shelfCoverPatternView);
            dragHandleView = itemView.findViewById(R.id.dragHandleView);
            nameView = itemView.findViewById(R.id.shelfNameView);
        }
    }
}
