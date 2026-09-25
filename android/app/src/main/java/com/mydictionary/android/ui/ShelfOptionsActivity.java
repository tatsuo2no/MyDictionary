package com.mydictionary.android.ui;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
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
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.validation.NameValidator;

import java.io.IOException;

/**
 * シェルフオプション画面。名前・カバーの編集とシェルフの削除を提供する。
 * ブックは必ずいずれか1つのシェルフに属する必要があるため、中にブックが1冊でも残っている
 * シェルフは削除できない（先に他のシェルフへ移動するか、ブック自体を削除してもらう）。
 */
public class ShelfOptionsActivity extends AppCompatActivity {

    private long shelfId;
    private Shelf shelf;
    private AndroidShelfRepository shelfRepository;
    private AndroidBookRepository bookRepository;

    private EditText nameField;
    private CoverPatternPickerButton coverPicker;
    private TextView errorLabel;

    private final ActivityResultLauncher<String> pickCoverImageLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::onCoverImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shelf_options);

        shelfId = getIntent().getLongExtra(IntentKeys.EXTRA_SHELF_ID, -1);
        shelfRepository = new AndroidShelfRepository(MyDictionaryApplication.from(this).getDbHelper());
        bookRepository = new AndroidBookRepository(MyDictionaryApplication.from(this).getDbHelper());
        shelf = shelfRepository.findById(shelfId)
            .orElseThrow(() -> new IllegalStateException("シェルフが見つかりません: " + shelfId));

        nameField = findViewById(R.id.nameField);
        coverPicker = findViewById(R.id.coverPicker);
        errorLabel = findViewById(R.id.errorLabel);

        nameField.setText(shelf.getName());
        coverPicker.setColorSuppliers(() -> Shelf.COVER_BACKGROUND_COLOR_CODE, () -> Shelf.COVER_TINT_COLOR_CODE);
        coverPicker.setValue(shelf.getCover());

        findViewById(R.id.browseCoverButton).setOnClickListener(v -> pickCoverImageLauncher.launch("image/*"));
        findViewById(R.id.saveButton).setOnClickListener(v -> onSave());
        findViewById(R.id.deleteShelfButton).setOnClickListener(v -> onDeleteShelf());
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
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

    private void onSave() {
        String nameText = nameField.getText() == null ? "" : nameField.getText().toString().trim();
        if (!NameValidator.isValid(nameText)) {
            errorLabel.setText(R.string.error_invalid_name_chars);
            return;
        }

        boolean nameTaken = shelfRepository.findAll().stream()
            .anyMatch(s -> s.getId() != shelfId && s.getName().equals(nameText));
        if (nameTaken) {
            errorLabel.setText(R.string.error_shelf_name_taken);
            return;
        }

        shelf.setName(nameText);
        shelf.setCover(coverPicker.getValue());
        shelfRepository.update(shelf);

        finish();
    }

    private void onDeleteShelf() {
        if (!bookRepository.findByShelfId(shelfId).isEmpty()) {
            new AlertDialog.Builder(this)
                .setMessage(R.string.delete_shelf_blocked_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
            return;
        }

        EditText input = new EditText(this);
        input.setHint(R.string.delete_confirm_hint);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.delete_shelf_title)
            .setMessage(R.string.delete_shelf_message)
            .setView(input)
            .setPositiveButton(R.string.action_delete, (d, which) -> {
                shelfRepository.delete(shelfId);
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
