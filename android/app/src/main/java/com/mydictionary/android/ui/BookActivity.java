package com.mydictionary.android.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mydictionary.android.IntentKeys;
import com.mydictionary.android.MyDictionaryApplication;
import com.mydictionary.android.R;
import com.mydictionary.android.db.AndroidBookRepository;
import com.mydictionary.android.db.AndroidNoteColumnRepository;
import com.mydictionary.android.db.AndroidNoteRepository;
import com.mydictionary.android.db.AndroidTagRepository;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.Tag;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** ブック画面。ブック内のノート一覧を表示し、検索・タグ絞り込み・並び替えを行う。 */
public class BookActivity extends AppCompatActivity {

    private long bookId;

    private AndroidNoteRepository noteRepository;
    private AndroidTagRepository tagRepository;
    private AndroidNoteColumnRepository columnRepository;
    private List<NoteColumn> columns;

    private EditText searchField;
    private Spinner tagFilterSpinner;
    private Spinner sortSpinner;
    private TextView noteCountLabel;
    private RecyclerView noteRecyclerView;
    private TextView noteEmptyLabel;
    private Integer listRowColorLight;
    private Integer listRowColorDark;
    private boolean themeIsDark;

    private final List<TagOption> tagOptions = new ArrayList<>();
    private Map<Long, Tag> tagById;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book);

        bookId = getIntent().getLongExtra(IntentKeys.EXTRA_BOOK_ID, -1);

        AndroidBookRepository bookRepository = new AndroidBookRepository(
            MyDictionaryApplication.from(this).getDbHelper());
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));

        noteRepository = new AndroidNoteRepository(MyDictionaryApplication.from(this).getDbHelper());
        tagRepository = new AndroidTagRepository(MyDictionaryApplication.from(this).getDbHelper());
        columnRepository = new AndroidNoteColumnRepository(MyDictionaryApplication.from(this).getDbHelper());
        columns = columnRepository.findByBookId(bookId);

        int textColor = Color.parseColor(book.getTheme().getTextColorCode());
        themeIsDark = book.getTheme().isDark();
        String lightCode = book.getTheme().getListRowColorLightCode();
        String darkCode = book.getTheme().getListRowColorDarkCode();
        listRowColorLight = lightCode == null ? null : Color.parseColor(lightCode);
        listRowColorDark = darkCode == null ? null : Color.parseColor(darkCode);

        TextView bookTitleLabel = findViewById(R.id.bookTitleLabel);
        bookTitleLabel.setText(book.getTitle());
        bookTitleLabel.setTextColor(textColor);

        findViewById(R.id.headerLayout).setBackgroundColor(Color.parseColor(book.getTheme().getColorCode()));

        noteCountLabel = findViewById(R.id.noteCountLabel);
        noteCountLabel.setTextColor(textColor);
        searchField = findViewById(R.id.searchField);
        tagFilterSpinner = findViewById(R.id.tagFilterSpinner);
        sortSpinner = findViewById(R.id.sortSpinner);
        noteRecyclerView = findViewById(R.id.noteRecyclerView);
        noteRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        noteEmptyLabel = findViewById(R.id.noteEmptyLabel);
        // ノート未作成部分・0件時の下地を、行色の暗い方で塗る。0件時は「ノートがありません」を出し、
        // 濃色テーマなら白文字にする。
        if (listRowColorDark != null) {
            findViewById(R.id.noteListContainer).setBackgroundColor(listRowColorDark);
            noteEmptyLabel.setTextColor(themeIsDark ? Color.WHITE : Color.BLACK);
        }

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        findViewById(R.id.createNoteButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, NoteCreateActivity.class);
            intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
            startActivity(intent);
        });

        findViewById(R.id.bookOptionsButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, BookOptionsActivity.class);
            intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
            startActivity(intent);
        });

        setupSortSpinner();
        setupSearchField();
    }

    @Override
    protected void onResume() {
        super.onResume();
        columns = columnRepository.findByBookId(bookId);
        setupTagFilterSpinner();
        refresh();
    }

    private void setupSortSpinner() {
        List<String> labels = new ArrayList<>();
        for (NoteColumn column : columns) {
            labels.add(getString(R.string.sort_by_column_format, column.getName()));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            labels);
        sortSpinner.setAdapter(adapter);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupSearchField() {
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupTagFilterSpinner() {
        List<Tag> flatTags = tagRepository.findByBookId(bookId);
        tagById = flatTags.stream().collect(Collectors.toMap(Tag::getId, tag -> tag));

        tagOptions.clear();
        tagOptions.add(new TagOption(null, 0));
        List<Tag> roots = Tag.buildTree(flatTags);
        collectPreOrder(roots, 0);

        List<String> labels = new ArrayList<>();
        for (TagOption option : tagOptions) {
            String indent = "　".repeat(option.depth);
            labels.add(indent + (option.tag == null ? getString(R.string.tag_filter_all) : option.tag.getName()));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            labels);
        tagFilterSpinner.setAdapter(adapter);
        tagFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void collectPreOrder(List<Tag> nodes, int depth) {
        for (Tag node : nodes) {
            tagOptions.add(new TagOption(node, depth));
            collectPreOrder(node.getChildren(), depth + 1);
        }
    }

    private void refresh() {
        int tagIndex = tagFilterSpinner.getSelectedItemPosition();
        TagOption selected = (tagIndex >= 0 && tagIndex < tagOptions.size()) ? tagOptions.get(tagIndex) : null;

        List<Note> base = (selected != null && selected.tag != null)
            ? noteRepository.findByTag(bookId, selected.tag.getId())
            : noteRepository.findByBookId(bookId);

        String keyword = searchField.getText() == null ? "" : searchField.getText().toString().trim()
            .toLowerCase(Locale.ROOT);
        List<Note> filtered = keyword.isEmpty() ? base : base.stream()
            .filter(n -> matchesKeyword(n, keyword))
            .collect(Collectors.toList());

        int sortIndex = sortSpinner.getSelectedItemPosition();
        NoteColumn sortColumn = (sortIndex >= 0 && sortIndex < columns.size()) ? columns.get(sortIndex)
            : columns.get(0);
        Collator collator = Collator.getInstance(Locale.JAPANESE);
        filtered.sort(Comparator.comparing(n -> n.getFieldValue(sortColumn.getId()), collator));

        noteCountLabel.setText(getString(R.string.book_count_format, filtered.size()));
        noteEmptyLabel.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        noteRecyclerView.setAdapter(new NoteAdapter(filtered, columns, tagById, listRowColorLight, listRowColorDark,
            note -> {
                Intent intent = new Intent(this, NoteActivity.class);
                intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
                intent.putExtra(IntentKeys.EXTRA_NOTE_ID, note.getId());
                startActivity(intent);
            }));
    }

    private boolean matchesKeyword(Note note, String keywordLowerCase) {
        if (containsIgnoreCase(note.getBody(), keywordLowerCase)) {
            return true;
        }
        for (String value : note.getFieldValues().values()) {
            if (containsIgnoreCase(value, keywordLowerCase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String keywordLowerCase) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keywordLowerCase);
    }

    private static final class TagOption {
        final Tag tag;
        final int depth;

        TagOption(Tag tag, int depth) {
            this.tag = tag;
            this.depth = depth;
        }
    }
}
