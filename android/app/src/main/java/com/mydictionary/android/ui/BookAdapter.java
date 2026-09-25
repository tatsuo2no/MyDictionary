package com.mydictionary.android.ui;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Typeface;
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
import com.mydictionary.core.model.Book;

import java.util.List;

public class BookAdapter extends RecyclerView.Adapter<BookAdapter.BookViewHolder> {

    public interface Listener {
        void onBookClick(Book book);

        void onBookLongClick(Book book);
    }

    /** ドラッグハンドルに触れたときに、ItemTouchHelper へドラッグ開始を伝えるためのコールバック。 */
    public interface DragStarter {
        void onStartDrag(RecyclerView.ViewHolder viewHolder);
    }

    private final List<Book> books;
    private final Listener listener;
    private final boolean showDragHandle;
    private final DragStarter dragStarter;

    public BookAdapter(List<Book> books, Listener listener) {
        this(books, listener, false, null);
    }

    public BookAdapter(List<Book> books, Listener listener, boolean showDragHandle, DragStarter dragStarter) {
        this.books = books;
        this.listener = listener;
        this.showDragHandle = showDragHandle;
        this.dragStarter = dragStarter;
    }

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(view);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = books.get(position);
        holder.titleView.setText(book.getTitle());
        holder.titleView.setTypeface(Typeface.create(book.getFont().getFamilyName(), Typeface.NORMAL));

        Drawable background = holder.iconView.getBackground().mutate();
        if (background instanceof GradientDrawable) {
            ((GradientDrawable) background).setColor(Color.parseColor(book.getTheme().getColorCode()));
        }
        holder.iconView.setBackground(background);

        BookCoverRenderer.apply(holder.coverPatternView, book.getCover(), book.getTheme().getTextColorCode());

        holder.itemView.setOnClickListener(v -> listener.onBookClick(book));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onBookLongClick(book);
            return true;
        });

        if (showDragHandle && dragStarter != null) {
            holder.dragHandleView.setVisibility(View.VISIBLE);
            holder.dragHandleView.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragStarter.onStartDrag(holder);
                    // DOWNを消費して、カード本体の長押し（＝ブックオプションを開く）が発火しないようにする。
                    return true;
                }
                return false;
            });
        } else {
            holder.dragHandleView.setVisibility(View.GONE);
            holder.dragHandleView.setOnTouchListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return books.size();
    }

    /** ドラッグ並び替え中に、表示中のリスト内で1件を移動する。 */
    public void moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= books.size() || toPosition >= books.size()) {
            return;
        }
        Book moved = books.remove(fromPosition);
        books.add(toPosition, moved);
        notifyItemMoved(fromPosition, toPosition);
    }

    /** ドラッグ終了時に、確定した並び順を取り出すための現在のリスト。 */
    public List<Book> currentBooks() {
        return books;
    }

    static class BookViewHolder extends RecyclerView.ViewHolder {
        final View iconView;
        final ImageView coverPatternView;
        final ImageView dragHandleView;
        final TextView titleView;

        BookViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.bookIconView);
            coverPatternView = itemView.findViewById(R.id.bookCoverPatternView);
            dragHandleView = itemView.findViewById(R.id.dragHandleView);
            titleView = itemView.findViewById(R.id.bookTitleView);
        }
    }
}
