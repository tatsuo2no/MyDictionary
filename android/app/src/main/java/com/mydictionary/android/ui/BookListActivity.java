package com.mydictionary.android.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.mydictionary.android.IntentKeys;
import com.mydictionary.android.MyDictionaryApplication;
import com.mydictionary.android.R;
import com.mydictionary.android.db.AndroidBookRepository;
import com.mydictionary.android.db.AndroidShelfRepository;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.repository.BookSortMode;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** ブックリスト画面。1つのシェルフ（本棚）に属するブックを表示する。 */
public class BookListActivity extends AppCompatActivity implements BookAdapter.Listener {

    private static final String PREFS_NAME = "sync_prefs";
    private static final String KEY_BOOK_SORT_MODE = "book_sort_mode";

    private long shelfId;
    private AndroidBookRepository bookRepository;
    private Spinner sortModeSpinner;
    private BookSortMode sortMode = BookSortMode.MANUAL;
    private ItemTouchHelper itemTouchHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_list);

        shelfId = getIntent().getLongExtra(IntentKeys.EXTRA_SHELF_ID, -1);
        bookRepository = new AndroidBookRepository(MyDictionaryApplication.from(this).getDbHelper());
        Shelf shelf = new AndroidShelfRepository(MyDictionaryApplication.from(this).getDbHelper()).findById(shelfId)
            .orElseThrow(() -> new IllegalStateException("シェルフが見つかりません: " + shelfId));

        TextView shelfTitleLabel = findViewById(R.id.shelfTitleLabel);
        shelfTitleLabel.setText(shelf.getName());

        findViewById(R.id.backButton).setOnClickListener(v -> finish());

        Button createBookButton = findViewById(R.id.createBookButton);
        createBookButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, BookCreateActivity.class);
            intent.putExtra(IntentKeys.EXTRA_SHELF_ID, shelfId);
            startActivity(intent);
        });

        sortMode = loadSortMode();
        sortModeSpinner = findViewById(R.id.sortModeSpinner);
        sortModeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            new String[]{getString(R.string.sort_mode_manual), getString(R.string.sort_mode_title),
                getString(R.string.sort_mode_created)}));
        sortModeSpinner.setSelection(sortMode.ordinal());
        sortModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sortMode = BookSortMode.values()[position];
                preferences().edit().putString(KEY_BOOK_SORT_MODE, sortMode.name()).apply();
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private BookSortMode loadSortMode() {
        String name = preferences().getString(KEY_BOOK_SORT_MODE, null);
        if (name == null) {
            return BookSortMode.MANUAL;
        }
        try {
            return BookSortMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return BookSortMode.MANUAL;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ブックカバーが124dp幅の縦長カードになったため、画面幅によっては固定3列だと1列分の幅に
    // 収まりきらず、タイトルが左右で切れてしまう（実機で確認済みの不具合）。カード1枚に必要な
    // 最小幅（カバー124dp＋左右のpadding/margin計32dp）を基準に、画面幅から列数を都度計算する。
    private static final int CARD_MIN_WIDTH_DP = 156;

    private void refresh() {
        List<Book> books = new ArrayList<>(bookRepository.findByShelfId(shelfId));
        if (sortMode == BookSortMode.TITLE) {
            Collator collator = Collator.getInstance(Locale.JAPANESE);
            books.sort(Comparator.comparing(Book::getTitle, collator));
        } else if (sortMode == BookSortMode.CREATED_AT) {
            books.sort(Comparator.comparing(Book::getCreatedAt));
        }
        // MANUAL は findByShelfId() が sort_order 順で返しているのでそのまま。

        boolean dragEnabled = sortMode == BookSortMode.MANUAL;

        TextView countLabel = findViewById(R.id.bookCountLabel);
        countLabel.setText(getString(R.string.book_count_format, books.size()));

        RecyclerView recyclerView = findViewById(R.id.bookRecyclerView);
        float density = getResources().getDisplayMetrics().density;
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels / density;
        int spanCount = Math.max(1, (int) (screenWidthDp / CARD_MIN_WIDTH_DP));
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));

        // ドラッグは「手動」のときだけ。長押しはブックオプションを開く操作に使っているため、
        // ドラッグの開始はカード左上のハンドルに触れたときだけにする（長押しドラッグは無効）。
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
            itemTouchHelper = null;
        }
        BookAdapter[] adapterRef = new BookAdapter[1];
        if (dragEnabled) {
            itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
                @Override
                public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder viewHolder,
                                      RecyclerView.ViewHolder target) {
                    adapterRef[0].moveItem(viewHolder.getBindingAdapterPosition(),
                        target.getBindingAdapterPosition());
                    return true;
                }

                @Override
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                }

                @Override
                public boolean isLongPressDragEnabled() {
                    return false;
                }

                @Override
                public void clearView(RecyclerView rv, RecyclerView.ViewHolder viewHolder) {
                    super.clearView(rv, viewHolder);
                    List<Long> orderedIds = new ArrayList<>();
                    for (Book book : adapterRef[0].currentBooks()) {
                        orderedIds.add(book.getId());
                    }
                    bookRepository.updateManualOrder(orderedIds);
                }
            });
        }
        ItemTouchHelper helper = itemTouchHelper;
        BookAdapter adapter = new BookAdapter(books, this, dragEnabled,
            helper == null ? null : helper::startDrag);
        adapterRef[0] = adapter;
        recyclerView.setAdapter(adapter);
        if (helper != null) {
            helper.attachToRecyclerView(recyclerView);
        }
    }

    @Override
    public void onBookClick(Book book) {
        Intent intent = new Intent(this, BookActivity.class);
        intent.putExtra(IntentKeys.EXTRA_BOOK_ID, book.getId());
        startActivity(intent);
    }

    @Override
    public void onBookLongClick(Book book) {
        Intent intent = new Intent(this, BookOptionsActivity.class);
        intent.putExtra(IntentKeys.EXTRA_BOOK_ID, book.getId());
        startActivity(intent);
    }
}
