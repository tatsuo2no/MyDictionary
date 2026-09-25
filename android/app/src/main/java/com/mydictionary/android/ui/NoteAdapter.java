package com.mydictionary.android.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mydictionary.android.R;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.NoteColumns;
import com.mydictionary.core.model.Tag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    public interface Listener {
        void onNoteClick(Note note);
    }

    private final List<Note> notes;
    private final List<NoteColumn> columns;
    private final Map<Long, Tag> tagById;
    private final Listener listener;
    private final Integer rowColorLight;
    private final Integer rowColorDark;

    public NoteAdapter(List<Note> notes, List<NoteColumn> columns, Map<Long, Tag> tagById, Listener listener) {
        this(notes, columns, tagById, null, null, listener);
    }

    /**
     * rowColorLight/rowColorDark は1行ごとに交互に敷く背景色（どちらもテーマ色を少し明るくした色）。
     * nullなら既定の背景のまま。
     */
    public NoteAdapter(List<Note> notes, List<NoteColumn> columns, Map<Long, Tag> tagById, Integer rowColorLight,
                        Integer rowColorDark, Listener listener) {
        this.notes = notes;
        this.columns = columns;
        this.tagById = tagById;
        this.rowColorLight = rowColorLight;
        this.rowColorDark = rowColorDark;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note_row, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.titleView.setText(NoteColumns.primaryValue(note, columns));

        holder.otherFieldsContainer.removeAllViews();
        for (int i = 1; i < columns.size(); i++) {
            NoteColumn column = columns.get(i);
            String value = note.getFieldValue(column.getId());
            if (value == null || value.isBlank()) {
                continue;
            }
            TextView fieldView = new TextView(holder.itemView.getContext());
            fieldView.setText(column.getName() + ": " + value);
            holder.otherFieldsContainer.addView(fieldView);
        }

        String tagsText = note.getTagIds().stream()
            .map(id -> Tag.fullPath(id, tagById))
            .collect(Collectors.joining("\n"));
        holder.tagsView.setText(tagsText);
        if (rowColorLight != null && rowColorDark != null) {
            holder.itemView.setBackgroundColor(position % 2 == 0 ? rowColorLight : rowColorDark);
        }
        holder.itemView.setOnClickListener(v -> listener.onNoteClick(note));
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final LinearLayout otherFieldsContainer;
        final TextView tagsView;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.noteTitleView);
            otherFieldsContainer = itemView.findViewById(R.id.noteOtherFieldsContainer);
            tagsView = itemView.findViewById(R.id.noteTagsView);
        }
    }
}
