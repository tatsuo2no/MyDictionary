package com.mydictionary.android.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mydictionary.android.R;
import com.mydictionary.core.model.Tag;

import java.util.List;

public class TagAdapter extends RecyclerView.Adapter<TagAdapter.TagViewHolder> {

    public interface Listener {
        void onAddChild(Tag tag);

        void onEdit(Tag tag);

        void onDelete(Tag tag);
    }

    public static final class Row {
        final Tag tag;
        final int depth;

        public Row(Tag tag, int depth) {
            this.tag = tag;
            this.depth = depth;
        }
    }

    private final List<Row> rows;
    private final Listener listener;

    public TagAdapter(List<Row> rows, Listener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tag_row, parent, false);
        return new TagViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
        Row row = rows.get(position);
        String indent = "　".repeat(row.depth);
        holder.nameView.setText(indent + row.tag.getName());
        holder.addChildButton.setOnClickListener(v -> listener.onAddChild(row.tag));
        holder.editButton.setOnClickListener(v -> listener.onEdit(row.tag));
        holder.deleteButton.setOnClickListener(v -> listener.onDelete(row.tag));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class TagViewHolder extends RecyclerView.ViewHolder {
        final TextView nameView;
        final Button addChildButton;
        final Button editButton;
        final Button deleteButton;

        TagViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.tagNameView);
            addChildButton = itemView.findViewById(R.id.addChildButton);
            editButton = itemView.findViewById(R.id.editTagButton);
            deleteButton = itemView.findViewById(R.id.deleteTagButton);
        }
    }
}
