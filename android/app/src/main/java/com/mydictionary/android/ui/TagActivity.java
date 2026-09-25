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
import com.mydictionary.android.db.AndroidTagRepository;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.Tag;
import com.mydictionary.core.validation.NameValidator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** タグ画面。ブックに紐づく入れ子タグの確認・追加・編集・削除を行う。 */
public class TagActivity extends AppCompatActivity implements TagAdapter.Listener {

    private long bookId;
    private AndroidTagRepository tagRepository;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tag);

        bookId = getIntent().getLongExtra(IntentKeys.EXTRA_BOOK_ID, -1);
        tagRepository = new AndroidTagRepository(MyDictionaryApplication.from(this).getDbHelper());
        Book book = new AndroidBookRepository(MyDictionaryApplication.from(this).getDbHelper()).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));

        TextView titleLabel = findViewById(R.id.tagScreenTitle);
        titleLabel.setText(getString(R.string.tag_screen_title_format, book.getTitle()));

        recyclerView = findViewById(R.id.tagRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        findViewById(R.id.addRootTagButton).setOnClickListener(v -> onAddTag(null));
        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        refresh();
    }

    private void refresh() {
        List<Tag> flatTags = tagRepository.findByBookId(bookId);
        List<Tag> roots = Tag.buildTree(flatTags);
        List<TagAdapter.Row> rows = new ArrayList<>();
        collectRows(roots, 0, rows);
        recyclerView.setAdapter(new TagAdapter(rows, this));
    }

    private void collectRows(List<Tag> nodes, int depth, List<TagAdapter.Row> rows) {
        for (Tag tag : nodes) {
            rows.add(new TagAdapter.Row(tag, depth));
            collectRows(tag.getChildren(), depth + 1, rows);
        }
    }

    @Override
    public void onAddChild(Tag tag) {
        onAddTag(tag);
    }

    private void onAddTag(Tag parent) {
        String title = parent == null
            ? getString(R.string.add_root_tag_title)
            : getString(R.string.add_child_tag_title_format, parent.getName());
        promptTagName(title, "", newName -> {
            tagRepository.insert(new Tag(0, null, bookId, newName, parent == null ? null : parent.getId(),
                Instant.now()));
            refresh();
        });
    }

    @Override
    public void onEdit(Tag tag) {
        Runnable proceed = () -> promptTagName(getString(R.string.edit_tag_title), tag.getName(), newName -> {
            tag.setName(newName);
            tagRepository.update(tag);
            refresh();
        });

        if (tagRepository.isUsedByAnyNote(tag.getId())) {
            confirmEditOrDelete(proceed);
        } else {
            proceed.run();
        }
    }

    @Override
    public void onDelete(Tag tag) {
        Runnable proceed = () -> {
            tagRepository.delete(tag.getId());
            refresh();
        };

        if (isTagOrDescendantsUsed(tag)) {
            confirmEditOrDelete(proceed);
        } else {
            proceed.run();
        }
    }

    private boolean isTagOrDescendantsUsed(Tag tag) {
        if (tagRepository.isUsedByAnyNote(tag.getId())) {
            return true;
        }
        for (Tag child : tag.getChildren()) {
            if (isTagOrDescendantsUsed(child)) {
                return true;
            }
        }
        return false;
    }

    private void confirmEditOrDelete(Runnable onConfirmed) {
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_edit_or_delete_tag)
            .setPositiveButton(R.string.yes, (d, which) -> onConfirmed.run())
            .setNegativeButton(R.string.no, null)
            .show();
    }

    private interface NameCallback {
        void onNameEntered(String name);
    }

    private void promptTagName(String title, String initialValue, NameCallback callback) {
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
                if (!NameValidator.isValid(name)) {
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
