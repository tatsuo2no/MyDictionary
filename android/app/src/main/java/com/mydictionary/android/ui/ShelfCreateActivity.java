package com.mydictionary.android.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.mydictionary.android.BookCoverImages;
import com.mydictionary.android.MyDictionaryApplication;
import com.mydictionary.android.R;
import com.mydictionary.android.db.AndroidShelfRepository;
import com.mydictionary.core.model.BookCover;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.validation.NameValidator;

import java.io.IOException;
import java.time.Instant;

/** シェルフ作成画面。名前とカバーを設定する（ブックと違い色テーマは持たない）。 */
public class ShelfCreateActivity extends AppCompatActivity {

    private EditText nameField;
    private CoverPatternPickerButton coverPicker;
    private TextView errorLabel;

    private AndroidShelfRepository shelfRepository;

    private final ActivityResultLauncher<String> pickCoverImageLauncher =
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::onCoverImagePicked);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shelf_create);

        shelfRepository = new AndroidShelfRepository(MyDictionaryApplication.from(this).getDbHelper());

        nameField = findViewById(R.id.nameField);
        coverPicker = findViewById(R.id.coverPicker);
        errorLabel = findViewById(R.id.errorLabel);

        coverPicker.setColorSuppliers(() -> Shelf.COVER_BACKGROUND_COLOR_CODE, () -> Shelf.COVER_TINT_COLOR_CODE);
        coverPicker.setValue(BookCover.ofPattern(BookCover.Pattern.STRIPES));

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

    private void onSave() {
        String nameText = nameField.getText() == null ? "" : nameField.getText().toString().trim();
        if (!NameValidator.isValid(nameText)) {
            errorLabel.setText(R.string.error_invalid_name_chars);
            return;
        }

        boolean nameTaken = shelfRepository.findAll().stream().anyMatch(s -> s.getName().equals(nameText));
        if (nameTaken) {
            errorLabel.setText(R.string.error_shelf_name_taken);
            return;
        }

        Shelf shelf = new Shelf(0, null, nameText, coverPicker.getValue(), Instant.now(), Instant.now());
        shelfRepository.insert(shelf);

        finish();
    }
}
