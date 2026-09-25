package com.mydictionary.android.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.services.drive.DriveScopes;
import com.mydictionary.android.IntentKeys;
import com.mydictionary.android.MyDictionaryApplication;
import com.mydictionary.android.R;
import com.mydictionary.android.db.AndroidShelfRepository;
import com.mydictionary.android.sync.SyncManager;
import com.mydictionary.core.model.Shelf;

import java.util.ArrayList;
import java.util.List;

/** シェルフ一覧画面（ホーム画面）。ブックはこの下のシェルフごとに分類される。 */
public class ShelfListActivity extends AppCompatActivity implements ShelfAdapter.Listener {

    private static final String PREFS_NAME = "sync_prefs";
    private static final String KEY_DRIVE_FOLDER_ID = "drive_folder_id";
    private static final String DEFAULT_FOLDER_NAME = "MyDictionarySync";

    private AndroidShelfRepository shelfRepository;
    private GoogleSignInClient signInClient;
    private ActivityResultLauncher<Intent> signInLauncher;
    private ActivityResultLauncher<Intent> authRecoveryLauncher;
    private ItemTouchHelper itemTouchHelper;
    private AlertDialog syncProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shelf_list);

        shelfRepository = new AndroidShelfRepository(MyDictionaryApplication.from(this).getDbHelper());

        Button createShelfButton = findViewById(R.id.createShelfButton);
        createShelfButton.setOnClickListener(v -> startActivity(new Intent(this, ShelfCreateActivity.class)));

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(new Scope(DriveScopes.DRIVE))
            .build();
        signInClient = GoogleSignIn.getClient(this, gso);

        signInLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                proceedAfterSignIn(account);
            } catch (ApiException e) {
                showSyncError(e);
            }
        });

        authRecoveryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
            if (account != null) {
                proceedAfterSignIn(account);
            }
        });

        Button syncButton = findViewById(R.id.syncButton);
        syncButton.setOnClickListener(v -> onSyncClicked());
        syncButton.setOnLongClickListener(v -> {
            // フォルダの選び直し（同期先を変更したい場合）。
            preferences().edit().remove(KEY_DRIVE_FOLDER_ID).apply();
            onSyncClicked();
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmExit();
            }
        });
    }

    private SharedPreferences preferences() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void onSyncClicked() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null || !GoogleSignIn.hasPermissions(account, new Scope(DriveScopes.DRIVE))) {
            signInLauncher.launch(signInClient.getSignInIntent());
            return;
        }
        proceedAfterSignIn(account);
    }

    private void proceedAfterSignIn(GoogleSignInAccount account) {
        String folderId = preferences().getString(KEY_DRIVE_FOLDER_ID, null);
        if (folderId == null) {
            promptForFolderName(account);
        } else {
            performSync(account, folderId);
        }
    }

    /** 共有フォルダの名前を入力してもらい、Drive内をその名前で検索する。 */
    private void promptForFolderName(GoogleSignInAccount account) {
        EditText input = new EditText(this);
        input.setHint(R.string.folder_name_dialog_hint);
        input.setText(DEFAULT_FOLDER_NAME);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
            .setTitle(R.string.folder_name_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.yes, (dialog, which) -> {
                String name = input.getText() == null ? "" : input.getText().toString().trim();
                if (!name.isEmpty()) {
                    searchAndSync(account, name);
                }
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }

    private void searchAndSync(GoogleSignInAccount account, String folderName) {
        showSyncProgress();
        new Thread(() -> {
            try {
                String folderId = SyncManager.findFolderIdByName(this, account, folderName);
                if (folderId == null) {
                    runOnUiThread(() -> {
                        hideSyncProgress();
                        new AlertDialog.Builder(this)
                            .setTitle(R.string.sync_error_title)
                            .setMessage(R.string.sync_folder_not_found)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    });
                    return;
                }
                preferences().edit().putString(KEY_DRIVE_FOLDER_ID, folderId).apply();
                runOnUiThread(() -> performSync(account, folderId));
            } catch (UserRecoverableAuthIOException e) {
                runOnUiThread(() -> {
                    hideSyncProgress();
                    authRecoveryLauncher.launch(e.getIntent());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideSyncProgress();
                    showSyncError(e);
                });
            }
        }).start();
    }

    private void performSync(GoogleSignInAccount account, String folderId) {
        showSyncProgress();
        new Thread(() -> {
            try {
                SyncManager.syncNow(this, MyDictionaryApplication.from(this).getDbHelper(), account, folderId);
                runOnUiThread(() -> {
                    hideSyncProgress();
                    Toast.makeText(this, R.string.sync_completed, Toast.LENGTH_SHORT).show();
                    refresh();
                });
            } catch (UserRecoverableAuthIOException e) {
                runOnUiThread(() -> {
                    hideSyncProgress();
                    authRecoveryLauncher.launch(e.getIntent());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideSyncProgress();
                    showSyncError(e);
                });
            }
        }).start();
    }

    /**
     * 同期中はローカルDBに対して他の操作（ノート編集など）が同時に走ると不整合の原因に
     * なり得るため、同期が終わるまで他の操作をできないようモーダルな進捗ダイアログを表示する
     * （キャンセル不可・外側タップでも閉じない。裏の画面へのタッチも標準のモーダルダイアログ
     * の挙動としてブロックされる）。
     */
    private void showSyncProgress() {
        if (syncProgressDialog != null && syncProgressDialog.isShowing()) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (24 * density);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(this);
        content.addView(progressBar);

        TextView label = new TextView(this);
        label.setText(R.string.sync_in_progress);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMarginStart(padding);
        label.setLayoutParams(labelParams);
        content.addView(label);

        syncProgressDialog = new AlertDialog.Builder(this)
            .setView(content)
            .setCancelable(false)
            .create();
        syncProgressDialog.setCanceledOnTouchOutside(false);
        syncProgressDialog.show();
    }

    private void hideSyncProgress() {
        if (syncProgressDialog != null) {
            syncProgressDialog.dismiss();
            syncProgressDialog = null;
        }
    }

    private void showSyncError(Exception e) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.sync_error_title)
            .setMessage(String.valueOf(e))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_exit_message)
            .setPositiveButton(R.string.yes, (dialog, which) -> finishAffinity())
            .setNegativeButton(R.string.no, null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideSyncProgress();
    }

    // シェルフカバーが124dp幅の縦長カードのため、画面幅によっては固定列数だとタイトルが
    // 左右で切れてしまう（ブック一覧と同じ理由）。カード1枚に必要な最小幅を基準に、
    // 画面幅から列数を都度計算する。
    private static final int CARD_MIN_WIDTH_DP = 156;

    private void refresh() {
        List<Shelf> shelves = new ArrayList<>(shelfRepository.findAll());

        TextView countLabel = findViewById(R.id.shelfCountLabel);
        countLabel.setText(getString(R.string.book_count_format, shelves.size()));

        RecyclerView recyclerView = findViewById(R.id.shelfRecyclerView);
        float density = getResources().getDisplayMetrics().density;
        float screenWidthDp = getResources().getDisplayMetrics().widthPixels / density;
        int spanCount = Math.max(1, (int) (screenWidthDp / CARD_MIN_WIDTH_DP));
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));

        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
            itemTouchHelper = null;
        }
        ShelfAdapter[] adapterRef = new ShelfAdapter[1];
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
                for (Shelf shelf : adapterRef[0].currentShelves()) {
                    orderedIds.add(shelf.getId());
                }
                shelfRepository.updateManualOrder(orderedIds);
            }
        });
        ShelfAdapter adapter = new ShelfAdapter(shelves, this, itemTouchHelper::startDrag);
        adapterRef[0] = adapter;
        recyclerView.setAdapter(adapter);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onShelfClick(Shelf shelf) {
        Intent intent = new Intent(this, BookListActivity.class);
        intent.putExtra(IntentKeys.EXTRA_SHELF_ID, shelf.getId());
        startActivity(intent);
    }

    @Override
    public void onShelfLongClick(Shelf shelf) {
        Intent intent = new Intent(this, ShelfOptionsActivity.class);
        intent.putExtra(IntentKeys.EXTRA_SHELF_ID, shelf.getId());
        startActivity(intent);
    }
}
