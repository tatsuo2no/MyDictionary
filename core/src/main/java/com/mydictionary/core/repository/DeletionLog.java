package com.mydictionary.core.repository;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 削除したシェルフ/ブック/タグ/項目/ノートの記録（「いつ・どのuuidを削除したか」）。
 * 片方の端末で削除した内容を、同期でもう片方の端末にも反映する（削除が反映されず復活してしまわない）ために、
 * {@link com.mydictionary.core.sync.DatabaseMerger}が両端末の記録を突き合わせて使う。
 * <p>
 * 記録そのものは、データベースのトリガー（{@link DeletionLogSchema}）が行を削除した時点で自動的に残す。
 * そのため削除する側のコードは、記録のことを意識しなくてよい。
 */
public interface DeletionLog {
    String SHELF = "SHELF";
    String BOOK = "BOOK";
    String TAG = "TAG";
    String COLUMN = "COLUMN";
    String NOTE = "NOTE";

    /** 記録が無い（削除を同期しない）場合に使う、何も記録しないログ。 */
    DeletionLog NONE = new DeletionLog() {
        @Override
        public Map<String, Instant> findAll(String kind) {
            return Collections.emptyMap();
        }

        @Override
        public void record(String kind, String uuid, Instant deletedAt) {
        }

        @Override
        public void remove(String kind, String uuid) {
        }
    };

    /** 指定した種類の、削除したuuid→削除した日時。 */
    Map<String, Instant> findAll(String kind);

    /** 削除を記録する。既に記録があれば、新しい方の日時を残す。 */
    void record(String kind, String uuid, Instant deletedAt);

    /** 記録を消す（削除したものを、その後の編集で復活させたとき）。 */
    void remove(String kind, String uuid);
}
