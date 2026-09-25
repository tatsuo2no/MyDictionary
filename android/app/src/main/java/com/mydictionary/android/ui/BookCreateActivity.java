package com.mydictionary.android.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.mydictionary.android.BookCoverImages;
import com.mydictionary.android.IntentKeys;
import com.mydictionary.android.MyDictionaryApplication;
import com.mydictionary.android.R;
import com.mydictionary.android.db.AndroidBookRepository;
import com.mydictionary.android.db.AndroidTagRepository;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.BookFont;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Tag;
import com.mydictionary.core.validation.NameValidator;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** ブック作成画面。 */
public class BookCreateActivity extends AppCompatActivity {

    private long shelfId;
    private EditText titleField;
    private Spinner themeSpinner;
    private Spinner fontSpinner;
    private Spinner fontSizeSpinner;
    private CoverPatternPickerButton coverPicker;
    private EditText tagField;
    private TextView errorLabel;

    private AndroidBookRepository bookRepository;
    private AndroidTagRepository tagRepository;

    private final ActivityResultLauncher<String> pickCoverImageLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::onCoverImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_create);

        shelfId = getIntent().getLongExtra(IntentKeys.EXTRA_SHELF_ID, -1);
        bookRepository = new AndroidBookRepository(MyDictionaryApplication.from(this).getDbHelper());
        tagRepository = new AndroidTagRepository(MyDictionaryApplication.from(this).getDbHelper());

        titleField = findViewById(R.id.titleField);
        themeSpinner = findViewById(R.id.themeSpinner);
        fontSpinner = findViewById(R.id.fontSpinner);
        fontSizeSpinner = findViewById(R.id.fontSizeSpinner);
        coverPicker = findViewById(R.id.coverPicker);
        tagField = findViewById(R.id.tagField);
        errorLabel = findViewById(R.id.errorLabel);

        themeSpinner.setAdapter(new BookThemeSpinnerAdapter(this, BookTheme.values()));
        fontSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            fontLabels()));
        fontSizeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
            fontSizeLabels()));
        fontSizeSpinner.setSelection(defaultFontSizeIndex());
        coverPicker.setColorSuppliers(
            () -> BookTheme.values()[themeSpinner.getSelectedItemPosition()].getColorCode(),
            () -> BookTheme.values()[themeSpinner.getSelectedItemPosition()].getTextColorCode());
        coverPicker.setValue(BookCover.ofPattern(BookCover.Pattern.STRIPES));
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

        Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> onSave());

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());
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

    private List<String> fontLabels() {
        List<String> labels = new ArrayList<>();
        for (BookFont font : BookFont.values()) {
            labels.add(font.getFamilyName());
        }
        return labels;
    }

    private List<String> fontSizeLabels() {
        List<String> labels = new ArrayList<>();
        for (int size : Book.AVAILABLE_FONT_SIZES_PT) {
            labels.add(size + "pt");
        }
        return labels;
    }

    private int defaultFontSizeIndex() {
        int[] sizes = Book.AVAILABLE_FONT_SIZES_PT;
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] == Book.DEFAULT_FONT_SIZE_PT) {
                return i;
            }
        }
        return 0;
    }

    private void onSave() {
        String titleText = titleField.getText() == null ? "" : titleField.getText().toString().trim();
        if (!NameValidator.isValid(titleText)) {
            errorLabel.setText(R.string.error_invalid_name_chars);
            return;
        }

        List<String> tagNames = new ArrayList<>();
        String tagsText = tagField.getText() == null ? "" : tagField.getText().toString();
        if (!tagsText.isBlank()) {
            for (String tagName : tagsText.split(",")) {
                String trimmed = tagName.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (!NameValidator.isValid(trimmed)) {
                    errorLabel.setText(getString(R.string.error_invalid_tag_name, trimmed));
                    return;
                }
                tagNames.add(trimmed);
            }
        }

        BookTheme theme = BookTheme.values()[themeSpinner.getSelectedItemPosition()];
        BookFont font = BookFont.values()[fontSpinner.getSelectedItemPosition()];
        int fontSizePt = Book.AVAILABLE_FONT_SIZES_PT[fontSizeSpinner.getSelectedItemPosition()];

        Book book = new Book(0, null, shelfId, titleText, theme, font, coverPicker.getValue(),
            Instant.now(), Instant.now());
        book.setFontSizePt(fontSizePt);
        Book saved = bookRepository.insert(book);

        for (String name : tagNames) {
            tagRepository.insert(new Tag(0, null, saved.getId(), name, null, Instant.now()));
        }

        finish();
    }
}
