package com.mydictionary.android.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.NoteColumns;
import com.mydictionary.core.model.Tag;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ノート画面。項目名・読み・英訳・タグは上部固定表示、本文（Markdown→HTML）はWebViewでスクロール表示する。
 * 注釈・画像・ノート内リンクはMarkdownRendererが出力するURLフラグメント（#footnote:.. 等）を
 * shouldOverrideUrlLoadingで検知して処理する（デスクトップ版と同じ方式で、JavaScriptは使用しない）。
 */
public class NoteActivity extends AppCompatActivity {

    private long bookId;
    private long noteId;

    private View scrimView;
    private View footnotePanel;
    private TextView footnoteTextView;
    private View imageZoomOverlay;
    private ImageView zoomImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note);

        bookId = getIntent().getLongExtra(IntentKeys.EXTRA_BOOK_ID, -1);
        noteId = getIntent().getLongExtra(IntentKeys.EXTRA_NOTE_ID, -1);

        Book book = new AndroidBookRepository(MyDictionaryApplication.from(this).getDbHelper()).findById(bookId)
            .orElseThrow(() -> new IllegalStateException("ブックが見つかりません: " + bookId));
        Note note = new AndroidNoteRepository(MyDictionaryApplication.from(this).getDbHelper()).findById(noteId)
            .orElseThrow(() -> new IllegalStateException("ノートが見つかりません: " + noteId));
        List<NoteColumn> columns = new AndroidNoteColumnRepository(MyDictionaryApplication.from(this).getDbHelper())
            .findByBookId(bookId);

        bindHeader(book, note, columns);
        bindBody(book, note);
        bindOverlays();

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.editNoteButton).setOnClickListener(v -> {
            Intent intent = new Intent(this, NoteCreateActivity.class);
            intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
            intent.putExtra(IntentKeys.EXTRA_NOTE_ID, noteId);
            startActivity(intent);
        });
        findViewById(R.id.deleteNoteButton).setOnClickListener(v -> onDeleteNote());
    }

    private void onDeleteNote() {
        new AlertDialog.Builder(this)
            .setMessage(R.string.delete_note_message)
            .setPositiveButton(R.string.yes, (d, which) -> {
                new AndroidNoteRepository(MyDictionaryApplication.from(this).getDbHelper()).delete(noteId);
                Intent intent = new Intent(this, BookActivity.class);
                intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
                startActivity(intent);
                finish();
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }

    private void bindHeader(Book book, Note note, List<NoteColumn> columns) {
        int textColor = Color.parseColor(book.getTheme().getTextColorCode());

        TextView titleLabel = findViewById(R.id.noteTitleLabel);
        titleLabel.setText(NoteColumns.primaryValue(note, columns));
        titleLabel.setTextColor(textColor);

        LinearLayout otherFieldsContainer = findViewById(R.id.otherFieldsContainer);
        for (int i = 1; i < columns.size(); i++) {
            NoteColumn column = columns.get(i);
            String value = note.getFieldValue(column.getId());
            if (value == null || value.isBlank()) {
                continue;
            }
            TextView fieldLabel = new TextView(this);
            fieldLabel.setText(column.getName() + ": " + value);
            fieldLabel.setTextColor(textColor);
            otherFieldsContainer.addView(fieldLabel);
        }

        ChipGroup chipGroup = findViewById(R.id.tagChipGroup);
        List<Long> tagIds = note.getTagIds();
        if (!tagIds.isEmpty()) {
            Map<Long, Tag> tagById = new AndroidTagRepository(MyDictionaryApplication.from(this)
                .getDbHelper()).findByBookId(bookId).stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag));
            for (Long tagId : tagIds) {
                Chip chip = new Chip(this);
                chip.setText(Tag.fullPath(tagId, tagById));
                chip.setClickable(false);
                chipGroup.addView(chip);
            }
        }

        findViewById(R.id.headerLayout).setBackgroundColor(Color.parseColor(book.getTheme().getColorCode()));
    }

    /** ノート本文中の画像を仮想ドメイン経由で配信する（file://直接参照はWebViewのセキュリティ制限で
     *  ERR_ACCESS_DENIEDになることを実機で確認済みのため、androidx.webkitの正式な回避策を使う）。 */
    private static final String IMAGE_ASSET_PATH = "/images/";

    private void bindBody(Book book, Note note) {
        WebView webView = findViewById(R.id.bodyWebView);
        webView.getSettings().setJavaScriptEnabled(true);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler(IMAGE_ASSET_PATH,
                new WebViewAssetLoader.InternalStoragePathHandler(this, AppPaths.getImageDir(this)))
            .build();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
        });

        String bodyHtml = new MarkdownRenderer().render(note.getBody());

        String imageUrl = NoteAppearance.backgroundImageExists(this, note.getBackgroundImageFileName())
            ? "https://appassets.androidplatform.net" + IMAGE_ASSET_PATH + note.getBackgroundImageFileName()
            : null;
        String bodyAppearance = NoteAppearance.buildBodyStyleCss(
            note.getBackgroundTheme(), imageUrl, note.getTextColor());

        String document = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/><style>"
            + "body{font-size:" + book.getFontSizePt() + "pt; padding:16px; line-height:1.7;" + bodyAppearance + "}"
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
        webView.loadDataWithBaseURL(baseUrl, document, "text/html", "UTF-8", null);
    }

    private void bindOverlays() {
        scrimView = findViewById(R.id.scrimView);
        footnotePanel = findViewById(R.id.footnotePanel);
        footnoteTextView = findViewById(R.id.footnoteTextView);
        imageZoomOverlay = findViewById(R.id.imageZoomOverlay);
        zoomImageView = findViewById(R.id.zoomImageView);

        scrimView.setOnClickListener(v -> hideFootnote());
        imageZoomOverlay.setOnClickListener(v -> imageZoomOverlay.setVisibility(View.GONE));
    }

    private boolean handleUrl(String url) {
        int hashIndex = url.indexOf('#');
        if (hashIndex < 0 || hashIndex == url.length() - 1) {
            return false;
        }
        String fragment = url.substring(hashIndex + 1);
        try {
            if (fragment.startsWith("footnote:")) {
                showFootnote(URLDecoder.decode(fragment.substring("footnote:".length()),
                    StandardCharsets.UTF_8.name()));
                return true;
            } else if (fragment.startsWith("image:")) {
                showImageZoom(URLDecoder.decode(fragment.substring("image:".length()),
                    StandardCharsets.UTF_8.name()));
                return true;
            } else if (fragment.startsWith("note:")) {
                long targetNoteId = Long.parseLong(fragment.substring("note:".length()));
                Intent intent = new Intent(this, NoteActivity.class);
                intent.putExtra(IntentKeys.EXTRA_BOOK_ID, bookId);
                intent.putExtra(IntentKeys.EXTRA_NOTE_ID, targetNoteId);
                startActivity(intent);
                return true;
            }
        } catch (Exception e) {
            // 不正なフラグメントは無視する
        }
        return false;
    }

    private void showFootnote(String text) {
        footnoteTextView.setText(text);
        scrimView.setVisibility(View.VISIBLE);
        footnotePanel.setVisibility(View.VISIBLE);
        float startY = footnotePanel.getHeight() == 0 ? 300f : footnotePanel.getHeight();
        footnotePanel.setTranslationY(startY);
        ObjectAnimator.ofFloat(footnotePanel, "translationY", 0f).setDuration(200).start();
    }

    private void hideFootnote() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(footnotePanel, "translationY", footnotePanel.getHeight());
        animator.setDuration(200);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                footnotePanel.setVisibility(View.GONE);
            }
        });
        animator.start();
        scrimView.setVisibility(View.GONE);
    }

    private void showImageZoom(String rawPath) {
        File imageFile = new File(AppPaths.getImageDir(this), rawPath);
        zoomImageView.setImageURI(Uri.fromFile(imageFile));
        imageZoomOverlay.setVisibility(View.VISIBLE);
    }
}
