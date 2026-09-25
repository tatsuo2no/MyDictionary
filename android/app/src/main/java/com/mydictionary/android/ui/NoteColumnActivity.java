package com.mydictionary.android.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mydictionary.android.IntentKeys;
import com.mydictionary.android.MyDictionaryApplication;
import com.mydictionary.android.R;
import com.mydictionary.android.db.AndroidBookRepository;
import com.mydictionary.android.db.AndroidNoteColumnRepository;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.validation.NameValidator;

import java.util.List;

/**
 * ノート項目（カラム）設定画面。以前は「項目名・読み・英訳」の3つが固定だったが、
 * ブックごとに自由な名前・数のカラムを設定できるようにした（2026-09）。
 * 先頭（並び順が最小）の項目は常に必須で削除できない（ノート一覧の見出し・
 * ノート内リンクの既定表示・名前順の並び替えに使われる）。それ以外は必須/任意を
 * 切り替えたり削除したりできる。タグ画面と同じく、追加・変更・削除は都度即座にDBへ反映する。
 */
public class NoteColumnActivity extends AppCompatActivity implements NoteColumnAdapter.Listener {

    private long bookId;
    private AndroidNoteColumnRepository columnRepository;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_column);

        bookId = getIntent().getLongExtra(IntentKeys.EXTRA_BOOK_ID, -1);
        columnRepository = new AndroidNoteColumnRepository(MyDictionaryApplication.from(this).getDbHelper());
        Book book = new AndroidBookRepository(MyDictionaryApplication.from(this).getDbHelper()).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));

        TextView titleLabel = findViewById(R.id.noteColumnScreenTitle);
        titleLabel.setText(getString(R.string.note_column_screen_title_format, book.getTitle()));

        recyclerView = findViewById(R.id.noteColumnRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.addColumnButton).setOnClickListener(v -> onAddColumn());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        refresh();
    }

    private void refresh() {
        List<NoteColumn> columns = columnRepository.findByBookId(bookId);
        recyclerView.setAdapter(new NoteColumnAdapter(columns, this));
    }

    private void onAddColumn() {
        promptColumnName(getString(R.string.add_note_column_title), "", name -> {
            List<NoteColumn> existing = columnRepository.findByBookId(bookId);
            int nextSortOrder = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1;
            columnRepository.insert(new NoteColumn(0, null, bookId, name, false, nextSortOrder, null));
            refresh();
        });
    }

    @Override
    public void onRequiredChanged(NoteColumn column, boolean required) {
        column.setRequired(required);
        columnRepository.update(column);
    }

    @Override
    public void onRename(NoteColumn column) {
        promptColumnName(getString(R.string.rename_note_column_title), column.getName(), newName -> {
            column.setName(newName);
            columnRepository.update(column);
            refresh();
        });
    }

    @Override
    public void onDelete(NoteColumn column) {
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_note_column_format, column.getName()))
            .setPositiveButton(R.string.yes, (d, which) -> {
                columnRepository.delete(column.getId());
                refresh();
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }

    private interface NameCallback {
        void onNameEntered(String name);
    }

    private void promptColumnName(String title, String initialValue, NameCallback callback) {
        EditText input = new EditText(this);
        input.setText(initialValue);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setOnClickListener(v -> {
                String name = input.getText() == null ? "" : input.getText().toString().trim();
                if (!NameValidator.isValid(name) || name.isEmpty()) {
                    input.setError(getString(R.string.error_invalid_name_chars));
                    return;
                }
                callback.onNameEntered(name);
                dialog.dismiss();
            });
        });

        dialog.show();
    }
}
