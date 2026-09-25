package com.mydictionary.android.ui;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mydictionary.android.R;
import com.mydictionary.core.model.NoteColumn;

import java.util.List;

public class NoteColumnAdapter extends RecyclerView.Adapter<NoteColumnAdapter.NoteColumnViewHolder> {

    public interface Listener {
        void onRequiredChanged(NoteColumn column, boolean required);

        void onRename(NoteColumn column);

        void onDelete(NoteColumn column);
    }

    private final List<NoteColumn> columns;
    private final Listener listener;

    public NoteColumnAdapter(List<NoteColumn> columns, Listener listener) {
        this.columns = columns;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoteColumnViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_column_row, parent, false);
        return new NoteColumnViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteColumnViewHolder holder, int position) {
        NoteColumn column = columns.get(position);
        boolean isPrimary = position == 0;

        holder.nameView.setText(column.getName());
        holder.nameView.setTypeface(null, isPrimary ? Typeface.BOLD : Typeface.NORMAL);

        // リスナーを外してからselectedを設定しないと、行の使い回し(リサイクル)時に
        // 直前の行の状態変化としてリスナーが誤発火してしまう。
        holder.requiredCheck.setOnCheckedChangeListener(null);
        holder.requiredCheck.setChecked(isPrimary || column.isRequired());
        holder.requiredCheck.setEnabled(!isPrimary);
        holder.requiredCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                listener.onRequiredChanged(column, isChecked);
            }
        });

        holder.renameButton.setOnClickListener(v -> listener.onRename(column));
        holder.deleteButton.setEnabled(!isPrimary);
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(column));
    }

    @Override
    public int getItemCount() {
        return columns.size();
    }

    static class NoteColumnViewHolder extends RecyclerView.ViewHolder {
        final TextView nameView;
        final CheckBox requiredCheck;
        final Button renameButton;
        final Button deleteButton;

        NoteColumnViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.columnNameView);
            requiredCheck = itemView.findViewById(R.id.columnRequiredCheck);
            renameButton = itemView.findViewById(R.id.renameColumnButton);
            deleteButton = itemView.findViewById(R.id.deleteColumnButton);
        }
    }
}
