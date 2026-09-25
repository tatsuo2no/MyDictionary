package com.mydictionary.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.mydictionary.android.AppPaths;
import com.mydictionary.android.IntentKeys;
import com.mydictionary.android.MyDictionaryApplication;
import com.mydictionary.android.NoteAppearance;
import com.mydictionary.android.R;
import com.mydictionary.android.db.AndroidBookRepository;
import com.mydictionary.android.db.AndroidNoteColumnRepository;
import com.mydictionary.android.db.AndroidNoteRepository;
import com.mydictionary.android.db.AndroidTagRepository;
import com.mydictionary.core.markdown.MarkdownRenderer;
import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.BookTheme;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.NoteTextColor;
import com.mydictionary.core.model.Tag;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ノート作成/編集画面。noteIdがIntentに含まれていなければ新規作成、含まれていれば既存ノートの編集
 * （仕様上、編集画面は作成画面に既存データを入力した状態と同じ）。
 */
public class NoteCreateActivity extends AppCompatActivity {

    /** ノート本文中の画像を仮想ドメイン経由で配信する（file://直接参照はWebViewのセキュリティ制限で
     *  ERR_ACCESS_DENIEDになることを実機で確認済みのため、androidx.webkitの正式な回避策を使う）。 */
    private static final String IMAGE_ASSET_PATH = "/images/";

    private long bookId;
    private Long noteId;
    private Note existingNote;

    private AndroidNoteRepository noteRepository;
    private AndroidTagRepository tagRepository;
    private AndroidNoteColumnRepository columnRepository;

    private List<NoteColumn> columns;
    private int bookFontSizePt;
    private final Map<Long, EditText> columnFields = new LinkedHashMap<>();

    private EditText bodyField;
    private WebView previewWebView;
    private TextView errorLabel;

    private final List<Tag> orderedTags = new ArrayList<>();
    private final Map<Long, Integer> tagDepthById = new HashMap<>();
    private final Map<Long, Tag> tagById = new HashMap<>();
    private final List<Long> selectedTagIds = new ArrayList<>();
    private List<Tag> spinnerTags = new ArrayList<>();

    private ChipGroup selectedTagsChipGroup;
    private Spinner tagSpinner;
    private Button addTagButton;
    private ArrayAdapter<String> tagSpinnerAdapter;

    private Spinner backgroundThemeSpinner;
    private Spinner textColorSpinner;
    private TextView backgroundImageLabel;
    private String backgroundImageFileName;

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String> pickBackgroundImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_create);

        bookId = getIntent().getLongExtra(IntentKeys.EXTRA_BOOK_ID, -1);
        long noteIdExtra = getIntent().getLongExtra(IntentKeys.EXTRA_NOTE_ID, -1);
        noteId = noteIdExtra == -1 ? null : noteIdExtra;

        noteRepository = new AndroidNoteRepository(MyDictionaryApplication.from(this).getDbHelper());
        tagRepository = new AndroidTagRepository(MyDictionaryApplication.from(this).getDbHelper());
        columnRepository = new AndroidNoteColumnRepository(MyDictionaryApplication.from(this).getDbHelper());
        Book book = new AndroidBookRepository(MyDictionaryApplication.from(this).getDbHelper()).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));
        bookFontSizePt = book.getFontSizePt();
        columns = columnRepository.findByBookId(bookId);

        if (noteId != null) {
            existingNote = noteRepository.findById(noteId)
                .orElseThrow(() -> new IllegalStateException("ノートが見つかりません: " + noteId));
        }

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            this::onImagePicked);
        pickBackgroundImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            this::onBackgroundImagePicked);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBack();
            }
        });

        bindViews();
    }

    private void bindViews() {
        TextView screenTitle = findViewById(R.id.screenTitle);
        screenTitle.setText(existingNote == null ? R.string.note_create_title : R.string.note_edit_title);

        buildColumnFields();
        bodyField = findViewById(R.id.bodyField);
        previewWebView = findViewById(R.id.previewWebView);
        previewWebView.getSettings().setJavaScriptEnabled(true);
        WebViewAssetLoader previewAssetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler(IMAGE_ASSET_PATH,
                new WebViewAssetLoader.InternalStoragePathHandler(this, AppPaths.getImageDir(this)))
            .build();
        previewWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return previewAssetLoader.shouldInterceptRequest(request.getUrl());
            }
        });
        errorLabel = findViewById(R.id.errorLabel);

        if (existingNote != null) {
            bodyField.setText(existingNote.getBody());
        }

        buildTagSelector();
        buildAppearanceControls();

        findViewById(R.id.backButton).setOnClickListener(v -> onBack());
        findViewById(R.id.insertImageButton).setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        findViewById(R.id.previewButton).setOnClickListener(v -> updatePreview());
        findViewById(R.id.saveButton).setOnClickListener(v -> onSave());

        updatePreview();
    }

    /** ブックごとに自由設定できるノート項目（カラム）1つにつき、ラベル+入力欄を1組動的に生成する。 */
    private void buildColumnFields() {
        LinearLayout container = findViewById(R.id.columnFieldsContainer);
        int density = (int) (getResources().getDisplayMetrics().density);
        for (NoteColumn column : columns) {
            TextView label = new TextView(this);
            label.setText(column.isRequired() ? column.getName() : getString(R.string.optional_field_label_format,
                column.getName()));
            label.setPadding(0, density * 8, 0, 0);
            container.addView(label);

            EditText field = new EditText(this);
            if (existingNote != null) {
                field.setText(existingNote.getFieldValue(column.getId()));
            }
            columnFields.put(column.getId(), field);
            container.addView(field);
        }
    }

    private void buildAppearanceControls() {
        backgroundThemeSpinner = findViewById(R.id.backgroundThemeSpinner);
        backgroundThemeSpinner.setAdapter(new BookThemeSpinnerAdapter(this, BookTheme.values()));
        BookTheme initialBackgroundTheme = existingNote != null ? existingNote.getBackgroundTheme() : BookTheme.WHITE;
        backgroundThemeSpinner.setSelection(indexOf(BookTheme.values(), initialBackgroundTheme));
        backgroundThemeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        textColorSpinner = findViewById(R.id.textColorSpinner);
        textColorSpinner.setAdapter(new NoteTextColorSpinnerAdapter(this, NoteTextColor.values()));
        NoteTextColor initialTextColor = existingNote != null ? existingNote.getTextColor() : NoteTextColor.BLACK;
        textColorSpinner.setSelection(indexOf(NoteTextColor.values(), initialTextColor));
        textColorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreview();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        backgroundImageFileName = existingNote != null ? existingNote.getBackgroundImageFileName() : null;
        backgroundImageLabel = findViewById(R.id.backgroundImageLabel);
        updateBackgroundImageLabel();

        findViewById(R.id.browseBackgroundImageButton).setOnClickListener(
            v -> pickBackgroundImageLauncher.launch("image/*"));
        findViewById(R.id.clearBackgroundImageButton).setOnClickListener(v -> {
            backgroundImageFileName = null;
            updateBackgroundImageLabel();
            updatePreview();
        });
    }

    private <T> int indexOf(T[] values, T target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return 0;
    }

    private void updateBackgroundImageLabel() {
        backgroundImageLabel.setText(backgroundImageFileName == null
            ? getString(R.string.background_image_not_set) : backgroundImageFileName);
    }

    private void buildTagSelector() {
        TextView noTagsLabel = findViewById(R.id.noTagsLabel);
        selectedTagsChipGroup = findViewById(R.id.selectedTagsChipGroup);
        LinearLayout tagAddRow = findViewById(R.id.tagAddRow);
        tagSpinner = findViewById(R.id.tagSpinner);
        addTagButton = findViewById(R.id.addTagButton);

        List<Tag> flatTags = tagRepository.findByBookId(bookId);
        if (flatTags.isEmpty()) {
            noTagsLabel.setVisibility(View.VISIBLE);
            selectedTagsChipGroup.setVisibility(View.GONE);
            tagAddRow.setVisibility(View.GONE);
            return;
        }

        collectOrdered(Tag.buildTree(flatTags), 0);
        if (existingNote != null) {
            selectedTagIds.addAll(existingNote.getTagIds());
        }

        tagSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        tagSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tagSpinner.setAdapter(tagSpinnerAdapter);

        addTagButton.setOnClickListener(v -> onAddTag());

        refreshTagSelectorState();
    }

    private void collectOrdered(List<Tag> nodes, int depth) {
        for (Tag tag : nodes) {
            orderedTags.add(tag);
            tagDepthById.put(tag.getId(), depth);
            tagById.put(tag.getId(), tag);
            collectOrdered(tag.getChildren(), depth + 1);
        }
    }

    private String tagLabel(Tag tag) {
        return "　".repeat(tagDepthById.getOrDefault(tag.getId(), 0)) + tag.getName();
    }

    private void onAddTag() {
        int position = tagSpinner.getSelectedItemPosition();
        if (position < 0 || position >= spinnerTags.size()) {
            return;
        }
        selectedTagIds.add(spinnerTags.get(position).getId());
        refreshTagSelectorState();
    }

    private void refreshTagSelectorState() {
        spinnerTags = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Tag tag : orderedTags) {
            if (!selectedTagIds.contains(tag.getId())) {
                spinnerTags.add(tag);
                labels.add(tagLabel(tag));
            }
        }
        tagSpinnerAdapter.clear();
        tagSpinnerAdapter.addAll(labels);
        tagSpinnerAdapter.notifyDataSetChanged();

        boolean hasAvailable = !spinnerTags.isEmpty();
        tagSpinner.setEnabled(hasAvailable);
        addTagButton.setEnabled(hasAvailable);

        selectedTagsChipGroup.removeAllViews();
        for (Long tagId : selectedTagIds) {
            Tag tag = tagById.get(tagId);
            if (tag == null) {
                continue;
            }
            Chip chip = new Chip(this);
            chip.setText(Tag.fullPath(tag.getId(), tagById));
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                selectedTagIds.remove(tag.getId());
                refreshTagSelectorState();
            });
            selectedTagsChipGroup.addView(chip);
        }
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            File imageDir = AppPaths.getImageDir(this);
            String fileName = System.currentTimeMillis() + ".png";
            File targetFile = new File(imageDir, fileName);
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(targetFile)) {
                if (input == null) {
                    throw new IOException("画像の読み込みに失敗しました");
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }

            int cursorPosition = bodyField.getSelectionStart();
            if (cursorPosition < 0) {
                cursorPosition = bodyField.getText().length();
            }
            String snippet = "![image](" + fileName + ")";
            bodyField.getText().insert(cursorPosition, snippet);
            updatePreview();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.error_image_save_failed, e.getMessage()), Toast.LENGTH_LONG)
                .show();
        }
    }

    private void onBackgroundImagePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            File imageDir = AppPaths.getImageDir(this);
            String fileName = System.currentTimeMillis() + "_bg.png";
            File targetFile = new File(imageDir, fileName);
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(targetFile)) {
                if (input == null) {
                    throw new IOException("画像の読み込みに失敗しました");
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }
            backgroundImageFileName = fileName;
            updateBackgroundImageLabel();
            updatePreview();
        } catch (IOException e) {
            Toast.makeText(this, getString(R.string.error_background_image_save_failed, e.getMessage()),
                Toast.LENGTH_LONG).show();
        }
    }

    private void updatePreview() {
        String bodyText = bodyField.getText() == null ? "" : bodyField.getText().toString();
        String bodyHtml = new MarkdownRenderer().render(bodyText);

        BookTheme backgroundTheme = (BookTheme) backgroundThemeSpinner.getSelectedItem();
        NoteTextColor textColor = (NoteTextColor) textColorSpinner.getSelectedItem();
        String imageUrl = NoteAppearance.backgroundImageExists(this, backgroundImageFileName)
            ? "https://appassets.androidplatform.net" + IMAGE_ASSET_PATH + backgroundImageFileName
            : null;
        String bodyAppearance = NoteAppearance.buildBodyStyleCss(backgroundTheme, imageUrl, textColor);

        String document = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>"
            + "body{font-size:" + bookFontSizePt + "pt; padding:8px; line-height:1.6;" + bodyAppearance + "}"
            + "table{border-collapse:collapse;} td,th{border:1px solid #999;padding:4px 8px;}"
            + "blockquote{border-left:4px solid #999;margin:8px 0;padding:4px 12px;color:#555;}"
            + "pre{background:#f4f4f4;padding:8px;overflow-x:auto;}"
            + "rt{font-size:0.6em;}"
            + "img{max-width:100%;display:block;margin:8px 0;}"
            + "h1,h2,h3,h4,h5,h6{font-weight:bold;margin:0.8em 0 0.3em;}"
            + "h1{font-size:1.8em;} h2{font-size:1.5em;} h3{font-size:1.3em;}"
            + "h4{font-size:1.15em;} h5{font-size:1.05em;} h6{font-size:1em;}"
            + "</style>" + com.mydictionary.android.KatexAssets.headHtml()
            + "</head><body>" + bodyHtml + "</body></html>";

        String baseUrl = "https://appassets.androidplatform.net" + IMAGE_ASSET_PATH;
        previewWebView.loadDataWithBaseURL(baseUrl, document, "text/html", "UTF-8", null);
    }

    private void onSave() {
        Map<Long, String> fieldValues = new LinkedHashMap<>();
        List<String> missingRequiredNames = new ArrayList<>();
        for (NoteColumn column : columns) {
            EditText field = columnFields.get(column.getId());
            String value = field.getText() == null ? "" : field.getText().toString().trim();
            if (column.isRequired() && value.isEmpty()) {
                missingRequiredNames.add(column.getName());
            }
            fieldValues.put(column.getId(), value);
        }
        if (!missingRequiredNames.isEmpty()) {
            errorLabel.setText(getString(R.string.error_required_columns_format,
                String.join("・", missingRequiredNames)));
            return;
        }

        List<Long> tagIdsToSave = new ArrayList<>(selectedTagIds);

        String bodyText = bodyField.getText() == null ? "" : bodyField.getText().toString();

        BookTheme backgroundTheme = (BookTheme) backgroundThemeSpinner.getSelectedItem();
        NoteTextColor textColor = (NoteTextColor) textColorSpinner.getSelectedItem();

        if (existingNote != null) {
            existingNote.setFieldValues(fieldValues);
            existingNote.setBody(bodyText);
            existingNote.setTagIds(tagIdsToSave);
            existingNote.setBackgroundTheme(backgroundTheme);
            existingNote.setBackgroundImageFileName(backgroundImageFileName);
            existingNote.setTextColor(textColor);
            noteRepository.update(existingNote);

            Intent intent = new Intent(this, NoteActivity.class);
            intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
            intent.putExtra(IntentKeys.EXTRA_NOTE_ID, existingNote.getId());
            startActivity(intent);
        } else {
            Note newNote = new Note(0, null, bookId, bodyText, Instant.now(), Instant.now());
            newNote.setFieldValues(fieldValues);
            newNote.setTagIds(tagIdsToSave);
            newNote.setBackgroundTheme(backgroundTheme);
            newNote.setBackgroundImageFileName(backgroundImageFileName);
            newNote.setTextColor(textColor);
            noteRepository.insert(newNote);

            Intent intent = new Intent(this, BookActivity.class);
            intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
            startActivity(intent);
        }
        finish();
    }

    private void onBack() {
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_discard_note_edit)
            .setPositiveButton(R.string.yes, (d, which) -> {
                if (existingNote != null) {
                    Intent intent = new Intent(this, NoteActivity.class);
                    intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
                    intent.putExtra(IntentKeys.EXTRA_NOTE_ID, existingNote.getId());
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(this, BookActivity.class);
                    intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
                    startActivity(intent);
                }
                finish();
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }
}
