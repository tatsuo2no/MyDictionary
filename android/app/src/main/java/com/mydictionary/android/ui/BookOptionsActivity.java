package com.mydictionary.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.mydictionary.android.BookCoverImages;
import com.mydictionary.android.IntentKeys;
import com.mydictionary.android.MyDictionaryApplication;
import com.mydictionary.android.R;
import com.mydictionary.android.db.AndroidBookRepository;
import com.mydictionary.android.db.AndroidShelfRepository;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.validation.NameValidator;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ブックオプション画面。タイトル・テーマ・フォント・アイコンの編集、タグ画面への導線、ブック削除を提供する。
 */
public class BookOptionsActivity extends AppCompatActivity {

    private long bookId;
    private Book book;
    private AndroidBookRepository bookRepository;
    private List<Shelf> allShelves;

    private EditText titleField;
    private Spinner shelfSpinner;
    private Spinner themeSpinner;
    private Spinner fontSpinner;
    private Spinner fontSizeSpinner;
    private CoverPatternPickerButton coverPicker;
    private TextView errorLabel;

    private final ActivityResultLauncher<String> pickCoverImageLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::onCoverImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_options);

        bookId = getIntent().getLongExtra(IntentKeys.EXTRA_BOOK_ID, -1);
        bookRepository = new AndroidBookRepository(MyDictionaryApplication.from(this).getDbHelper());
        book = bookRepository.findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));

        titleField = findViewById(R.id.titleField);
        shelfSpinner = findViewById(R.id.shelfSpinner);
        themeSpinner = findViewById(R.id.themeSpinner);
        fontSpinner = findViewById(R.id.fontSpinner);
        fontSizeSpinner = findViewById(R.id.fontSizeSpinner);
        coverPicker = findViewById(R.id.coverPicker);
        errorLabel = findViewById(R.id.errorLabel);

        titleField.setText(book.getTitle());

        allShelves = new AndroidShelfRepository(MyDictionaryApplication.from(this).getDbHelper()).findAll();
        int shelfSelectedIndex = 0;
        for (int i = 0; i < allShelves.size(); i++) {
            if (allShelves.get(i).getId() == book.getShelfId()) {
                shelfSelectedIndex = i;
            }
        }
        shelfSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            allShelves.stream().map(Shelf::getName).collect(Collectors.toList())));
        shelfSpinner.setSelection(shelfSelectedIndex);

        int themeSelectedIndex = 0;
        for (int i = 0; i < BookTheme.values().length; i++) {
            if (BookTheme.values()[i] == book.getTheme()) {
                themeSelectedIndex = i;
            }
        }
        themeSpinner.setAdapter(new BookThemeSpinnerAdapter(this, BookTheme.values()));
        themeSpinner.setSelection(themeSelectedIndex);

        int fontSelectedIndex = 0;
        for (int i = 0; i < BookFont.values().length; i++) {
            if (BookFont.values()[i] == book.getFont()) {
                fontSelectedIndex = i;
            }
        }
        fontSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            fontLabels()));
        fontSpinner.setSelection(fontSelectedIndex);

        int fontSizeSelectedIndex = 0;
        int[] fontSizes = Book.AVAILABLE_FONT_SIZES_PT;
        for (int i = 0; i < fontSizes.length; i++) {
            if (fontSizes[i] == book.getFontSizePt()) {
                fontSizeSelectedIndex = i;
            }
        }
        List<String> fontSizeLabels = new java.util.ArrayList<>();
        for (int size : fontSizes) {
            fontSizeLabels.add(size + "pt");
        }
        fontSizeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            fontSizeLabels));
        fontSizeSpinner.setSelection(fontSizeSelectedIndex);

        coverPicker.setColorSuppliers(
            () -> BookTheme.values()[themeSpinner.getSelectedItemPosition()].getColorCode(),
            () -> BookTheme.values()[themeSpinner.getSelectedItemPosition()].getTextColorCode());
        coverPicker.setValue(book.getCover());
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                coverPicker.refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.browseCoverButton).setOnClickListener(v -> pickCoverImageLauncher.launch("image/*"));

        findViewById(R.id.tagButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, TagActivity.class);
            intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
            startActivity(intent);
        });

        findViewById(R.id.noteColumnButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, NoteColumnActivity.class);
            intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
            startActivity(intent);
        });

        findViewById(R.id.saveButton).setOnClickListener(v -> onSave());
        findViewById(R.id.deleteBookButton).setOnClickListener(v -> onDeleteBook());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
    }

    private List<String> fontLabels() {
        List<String> labels = new java.util.ArrayList<>();
        for (BookFont font : BookFont.values()) {
            labels.add(font.getFamilyName());
        }
        return labels;
    }

    private void onSave() {
        String titleText = titleField.getText() == null ? "" : titleField.getText().toString().trim();
        if (!NameValidator.isValid(titleText)) {
            errorLabel.setText(R.string.error_invalid_name_chars);
            return;
        }

        book.setTitle(titleText);
        book.setShelfId(allShelves.get(shelfSpinner.getSelectedItemPosition()).getId());
        book.setTheme(BookTheme.values()[themeSpinner.getSelectedItemPosition()]);
        book.setFont(BookFont.values()[fontSpinner.getSelectedItemPosition()]);
        book.setFontSizePt(Book.AVAILABLE_FONT_SIZES_PT[fontSizeSpinner.getSelectedItemPosition()]);
        book.setCover(coverPicker.getValue());
        bookRepository.update(book);

        finish();
    }

    private void onCoverImagePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            String savedPath = BookCoverImages.saveCustomCoverImage(this, uri);
            coverPicker.setValue(BookCover.ofCustom(savedPath));
        } catch (IOException e) {
            errorLabel.setText(getString(R.string.error_cover_image_save_failed, e.getMessage()));
        }
    }

    private void onDeleteBook() {
        EditText input = new EditText(this);
        input.setHint(R.string.delete_confirm_hint);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.delete_book_title)
            .setMessage(R.string.delete_book_message)
            .setView(input)
            .setPositiveButton(R.string.action_delete, (d, which) -> {
                long shelfIdForReturn = book.getShelfId();
                bookRepository.delete(bookId);
                Intent intent = new Intent(this, BookListActivity.class);
                intent.putExtra(IntentKeys.EXTRA_SHELF_ID, shelfIdForReturn);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    positive.setEnabled(getString(R.string.delete_keyword).contentEquals(s));
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        });

        dialog.show();
    }
}
