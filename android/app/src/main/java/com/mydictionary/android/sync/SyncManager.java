package com.mydictionary.android.sync;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.mydictionary.android.AppPaths;
import com.mydictionary.android.db.AndroidBookRepository;
import com.mydictionary.android.db.AndroidNoteColumnRepository;
import com.mydictionary.android.db.AndroidNoteRepository;
import com.mydictionary.android.db.AndroidShelfRepository;
import com.mydictionary.android.db.AndroidTagRepository;
import com.mydictionary.android.db.DictionaryDbHelper;
import com.mydictionary.core.sync.DatabaseMerger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「今すぐ同期」機能。Googleの正式なDrive REST APIを使い、共有フォルダに置かれた
 * mydictionary.dbを端末にダウンロードして読み込み、ローカルDBとuuid・更新日時ベースで
 * マージしたうえで、結果を共有フォルダにアップロードし直す。画像ファイルは単純に
 * ファイル名ベースで両方向にコピーする。
 *
 * 以前はStorage Access Framework（SAF）経由でGoogle Driveアプリを介してアクセスしていたが、
 * Google Driveアプリ自身がバックグラウンドで保持するキャッシュが更新されず、他端末での
 * 変更を長時間検知できないことがあり、古い内容で上書きしてデータを消してしまう事象が
 * 実機で複数回発生した。Drive REST APIはGoogleのサーバーに直接アクセスするため、
 * この問題を構造的に回避できる。
 *
 * `supportsAllDrives`・`includeItemsFromAllDrives`・`corpora=allDrives`を明示的に指定しているのは、
 * 共有ドライブ配下のフォルダを共有先として使うケースにも対応するため（指定しないと
 * 共有ドライブの中身は`files.list()`の検索対象に含まれない）。
 *
 * なお、「同期フォルダが見つからない」という問題が長時間発生した際の真因は、PC側の
 * Google Driveアプリのサインインアカウントと、Android版でサインインしたGoogleアカウントが
 * 異なっていたことだった（PCに複数のGoogleアカウントが登録されており、既定のアカウントが
 * 意図したものと違っていた）。同じ症状が再発した場合は、まずアカウントの一致を疑うこと。
 */
public final class SyncManager {

    private SyncManager() {
    }

    public static void syncNow(Context context, DictionaryDbHelper localDbHelper,
                                GoogleSignInAccount account, String folderId) throws IOException {
        Drive drive = buildDriveService(context, account);

        com.google.api.services.drive.model.File existing = findChild(drive, folderId, "mydictionary.db");
        File remoteDbCopy = new File(context.getCacheDir(), "sync_remote_" + System.currentTimeMillis() + ".db");
        try {
            long remoteModifiedAtStart = existing != null && existing.getModifiedTime() != null
                ? existing.getModifiedTime().getValue() : -1;

            if (existing != null) {
                try (OutputStream out = new FileOutputStream(remoteDbCopy)) {
                    drive.files().get(existing.getId())
                        .setSupportsAllDrives(true)
                        .executeMediaAndDownloadTo(out);
                }

                Long expectedSize = existing.getSize();
                if (expectedSize != null && remoteDbCopy.length() != expectedSize) {
                    throw new IOException("共有フォルダからのダウンロードが不完全でした"
                        + "（サイズが一致しません）。もう一度「同期」を実行してください。");
                }
            }

            DictionaryDbHelper remoteHelper = new DictionaryDbHelper(context, remoteDbCopy.getAbsolutePath());
            try {
                SQLiteDatabase remoteDb = remoteHelper.getWritableDatabase();
                if (existing != null && !remoteDb.isDatabaseIntegrityOk()) {
                    throw new IOException("ダウンロードしたデータベースが破損している可能性があります。"
                        + "もう一度「同期」を実行してください。");
                }

                AndroidShelfRepository localShelves = new AndroidShelfRepository(localDbHelper);
                AndroidBookRepository localBooks = new AndroidBookRepository(localDbHelper);
                AndroidTagRepository localTags = new AndroidTagRepository(localDbHelper);
                AndroidNoteColumnRepository localColumns = new AndroidNoteColumnRepository(localDbHelper);
                AndroidNoteRepository localNotes = new AndroidNoteRepository(localDbHelper);

                AndroidShelfRepository remoteShelves = new AndroidShelfRepository(remoteHelper);
                AndroidBookRepository remoteBooks = new AndroidBookRepository(remoteHelper);
                AndroidTagRepository remoteTags = new AndroidTagRepository(remoteHelper);
                AndroidNoteColumnRepository remoteColumns = new AndroidNoteColumnRepository(remoteHelper);
                AndroidNoteRepository remoteNotes = new AndroidNoteRepository(remoteHelper);

                DatabaseMerger.merge(localShelves, localBooks, localTags, localColumns, localNotes,
                    remoteShelves, remoteBooks, remoteTags, remoteColumns, remoteNotes);
            } finally {
                remoteHelper.close();
            }

            // 書き戻す直前に、Driveサーバー上のファイルが読み込み時点から変わっていないか確認する
            // （複数端末からほぼ同時に同期した場合の上書き事故を防ぐための保険）。
            if (existing != null) {
                com.google.api.services.drive.model.File currentState = drive.files().get(existing.getId())
                    .setSupportsAllDrives(true)
                    .setFields("id, modifiedTime")
                    .execute();
                long currentModified = currentState.getModifiedTime() != null
                    ? currentState.getModifiedTime().getValue() : -1;
                if (currentModified != remoteModifiedAtStart) {
                    throw new IOException("同期中に共有フォルダのデータが更新されたため中断しました。"
                        + "少し待ってからもう一度「同期」を実行してください。");
                }
            }

            FileContent mediaContent = new FileContent("application/octet-stream", remoteDbCopy);
            if (existing != null) {
                drive.files().update(existing.getId(), null, mediaContent)
                    .setSupportsAllDrives(true)
                    .execute();
            } else {
                com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File();
                metadata.setName("mydictionary.db");
                metadata.setParents(Collections.singletonList(folderId));
                drive.files().create(metadata, mediaContent)
                    .setSupportsAllDrives(true)
                    .execute();
            }

            syncImages(context, drive, folderId);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            remoteDbCopy.delete();
        }
    }

    /**
     * Drive内を名前で検索し、一致するフォルダのIDを返す（見つからなければnull）。
     * 同名のフォルダが複数存在する場合は最初の1件を返す。
     */
    public static String findFolderIdByName(Context context, GoogleSignInAccount account, String folderName)
            throws IOException {
        Drive drive = buildDriveService(context, account);
        String query = "mimeType = 'application/vnd.google-apps.folder' and name = '"
            + folderName.replace("'", "\\'") + "' and trashed = false";
        FileList result = drive.files().list()
            .setQ(query)
            .setFields("files(id, name)")
            .setSpaces("drive")
            .setCorpora("allDrives")
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();
        List<com.google.api.services.drive.model.File> files = result.getFiles();
        return files.isEmpty() ? null : files.get(0).getId();
    }

    private static Drive buildDriveService(Context context, GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE));
        credential.setSelectedAccount(account.getAccount());
        return new Drive.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("MyDictionary")
            .build();
    }

    private static com.google.api.services.drive.model.File findChild(Drive drive, String parentId, String name)
            throws IOException {
        String query = "'" + parentId + "' in parents and name = '" + name.replace("'", "\\'")
            + "' and trashed = false";
        FileList result = drive.files().list()
            .setQ(query)
            .setFields("files(id, name, modifiedTime, size)")
            .setSpaces("drive")
            .setCorpora("allDrives")
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();
        List<com.google.api.services.drive.model.File> files = result.getFiles();
        return files.isEmpty() ? null : files.get(0);
    }

    private static void syncImages(Context context, Drive drive, String folderId) throws IOException {
        com.google.api.services.drive.model.File imagesFolder = findChild(drive, folderId, "images");
        String imagesFolderId;
        if (imagesFolder == null) {
            com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File();
            metadata.setName("images");
            metadata.setMimeType("application/vnd.google-apps.folder");
            metadata.setParents(Collections.singletonList(folderId));
            imagesFolderId = drive.files().create(metadata)
                .setSupportsAllDrives(true)
                .setFields("id")
                .execute()
                .getId();
        } else {
            imagesFolderId = imagesFolder.getId();
        }

        FileList remoteImages = drive.files().list()
            .setQ("'" + imagesFolderId + "' in parents and trashed = false")
            .setFields("files(id, name)")
            .setSpaces("drive")
            .setCorpora("allDrives")
            .setSupportsAllDrives(true)
            .setIncludeItemsFromAllDrives(true)
            .execute();
        Map<String, String> remoteIdByName = new HashMap<>();
        for (com.google.api.services.drive.model.File file : remoteImages.getFiles()) {
            remoteIdByName.put(file.getName(), file.getId());
        }

        File localImagesDir = AppPaths.getImageDir(context);
        File[] localFiles = localImagesDir.listFiles();
        if (localFiles != null) {
            for (File localFile : localFiles) {
                if (!localFile.isFile() || remoteIdByName.containsKey(localFile.getName())) {
                    continue;
                }
                com.google.api.services.drive.model.File metadata = new com.google.api.services.drive.model.File();
                metadata.setName(localFile.getName());
                metadata.setParents(Collections.singletonList(imagesFolderId));
                drive.files().create(metadata, new FileContent("application/octet-stream", localFile))
                    .setSupportsAllDrives(true)
                    .execute();
            }
        }

        for (Map.Entry<String, String> entry : remoteIdByName.entrySet()) {
            File localTarget = new File(localImagesDir, entry.getKey());
            if (!localTarget.exists()) {
                try (OutputStream out = new FileOutputStream(localTarget)) {
                    drive.files().get(entry.getValue())
                        .setSupportsAllDrives(true)
                        .executeMediaAndDownloadTo(out);
                }
            }
        }
    }
}
