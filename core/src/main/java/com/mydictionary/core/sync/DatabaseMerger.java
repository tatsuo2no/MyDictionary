package com.mydictionary.core.sync;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.model.Tag;
import com.mydictionary.core.repository.BookRepository;
import com.mydictionary.core.repository.DeletionLog;
import com.mydictionary.core.repository.NoteColumnRepository;
import com.mydictionary.core.repository.NoteRepository;
import com.mydictionary.core.repository.ShelfRepository;
import com.mydictionary.core.repository.TagRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 2つのデータソース（例: 端末のローカルDBと、共有フォルダ上に置かれたもう一方の端末のDBのコピー）を
 * uuid・更新日時をもとに双方向でマージする。
 * 同じuuidのエンティティが両方にある場合は更新日時が新しい方を採用し、両側に反映する。
 * 片方にしかないuuidはもう片方に新規追加する。
 *
 * 削除も同期する。各端末は削除したuuidと日時を{@link DeletionLog}に残しており、両端末の記録を突き合わせて、
 * 「削除した日時が、そのものの最終更新より後」なら両側から削除する。削除した後でもう一方の端末で編集された
 * もの（更新が削除より新しい）は、削除を取り消して残す。削除の記録を渡さない{@code merge}では、
 * 従来どおり削除は同期されない（もう片方に残っていれば復活する）。
 */
public final class DatabaseMerger {

    private DatabaseMerger() {
    }

    public static void merge(
            ShelfRepository sideAShelves, BookRepository sideABooks, TagRepository sideATags,
            NoteColumnRepository sideANoteColumns, NoteRepository sideANotes,
            ShelfRepository sideBShelves, BookRepository sideBBooks, TagRepository sideBTags,
            NoteColumnRepository sideBNoteColumns, NoteRepository sideBNotes) {
        merge(sideAShelves, sideABooks, sideATags, sideANoteColumns, sideANotes, DeletionLog.NONE,
            sideBShelves, sideBBooks, sideBTags, sideBNoteColumns, sideBNotes, DeletionLog.NONE);
    }

    public static void merge(
            ShelfRepository sideAShelves, BookRepository sideABooks, TagRepository sideATags,
            NoteColumnRepository sideANoteColumns, NoteRepository sideANotes, DeletionLog sideADeletions,
            ShelfRepository sideBShelves, BookRepository sideBBooks, TagRepository sideBTags,
            NoteColumnRepository sideBNoteColumns, NoteRepository sideBNotes, DeletionLog sideBDeletions) {

        Deletions deletions = new Deletions(sideADeletions, sideBDeletions);

        Map<String, Long> shelfIdByUuidA = new HashMap<>();
        Map<String, Long> shelfIdByUuidB = new HashMap<>();
        mergeShelves(sideAShelves, shelfIdByUuidA, sideBShelves, shelfIdByUuidB);

        // ブックのshelfIdはそのブックが元々属していた側のローカルなシェルフIDなので、
        // そのままでは相手側に書き込めない。一旦シェルフのuuidに戻してから、上で作った
        // 「uuid→この端末でのローカルなシェルフID」のマップで両側それぞれのIDに変換する。
        Map<Long, String> shelfUuidByIdA = shelfIdToUuidMap(sideAShelves.findAll());
        Map<Long, String> shelfUuidByIdB = shelfIdToUuidMap(sideBShelves.findAll());

        Map<String, Book> aByUuid = indexBooks(sideABooks);
        Map<String, Book> bByUuid = indexBooks(sideBBooks);

        for (String uuid : unionOfKeys(aByUuid, bByUuid)) {
            Book a = aByUuid.get(uuid);
            Book b = bByUuid.get(uuid);
            if (deletions.applyIfDeleted(DeletionLog.BOOK, uuid, a, b, Book::getUpdatedAt, Book::getId,
                sideABooks::delete, sideBBooks::delete)) {
                continue;
            }
            Book winner = pickWinner(a, b, Book::getUpdatedAt);
            Map<Long, String> winnerShelfUuidById = (winner == a) ? shelfUuidByIdA : shelfUuidByIdB;
            String winnerShelfUuid = winnerShelfUuidById.get(winner.getShelfId());
            long shelfIdForA = shelfIdByUuidA.get(winnerShelfUuid);
            long shelfIdForB = shelfIdByUuidB.get(winnerShelfUuid);

            Book resolvedA = sideABooks.upsertFromSync(uuid, shelfIdForA, winner.getTitle(), winner.getTheme(),
                winner.getFont(), winner.getCustomFontFamily(), winner.getFontSizePt(), winner.getCover(),
                winner.getCreatedAt(), winner.getUpdatedAt());
            Book resolvedB = sideBBooks.upsertFromSync(uuid, shelfIdForB, winner.getTitle(), winner.getTheme(),
                winner.getFont(), winner.getCustomFontFamily(), winner.getFontSizePt(), winner.getCover(),
                winner.getCreatedAt(), winner.getUpdatedAt());

            mergeTags(sideATags, resolvedA.getId(), sideBTags, resolvedB.getId(), deletions);

            Map<String, Long> columnIdByUuidA = new HashMap<>();
            Map<String, Long> columnIdByUuidB = new HashMap<>();
            mergeNoteColumns(sideANoteColumns, resolvedA.getId(), columnIdByUuidA,
                sideBNoteColumns, resolvedB.getId(), columnIdByUuidB, deletions);

            mergeNotes(sideANotes, sideATags, columnIdByUuidA, resolvedA.getId(),
                sideBNotes, sideBTags, columnIdByUuidB, resolvedB.getId(), deletions);
        }

        deleteRemovedShelves(sideAShelves, sideABooks, sideBShelves, sideBBooks, deletions);
    }

    /**
     * 削除されたシェルフを両側から消す。ブックはシェルフに属しているので、ブックの削除を先に済ませたうえで、
     * まだブックが残っているシェルフは（削除の記録を取り消して）残す。
     */
    private static void deleteRemovedShelves(ShelfRepository sideAShelves, BookRepository sideABooks,
                                              ShelfRepository sideBShelves, BookRepository sideBBooks,
                                              Deletions deletions) {
        Map<String, Shelf> aByUuid = toUuidMap(sideAShelves.findAll(), Shelf::getUuid);
        Map<String, Shelf> bByUuid = toUuidMap(sideBShelves.findAll(), Shelf::getUuid);
        Set<Long> usedShelfIdsA = usedShelfIds(sideABooks);
        Set<Long> usedShelfIdsB = usedShelfIds(sideBBooks);
        for (String uuid : unionOfKeys(aByUuid, bByUuid)) {
            Shelf a = aByUuid.get(uuid);
            Shelf b = bByUuid.get(uuid);
            boolean stillUsed = (a != null && usedShelfIdsA.contains(a.getId()))
                || (b != null && usedShelfIdsB.contains(b.getId()));
            if (stillUsed) {
                deletions.forget(DeletionLog.SHELF, uuid);
                continue;
            }
            deletions.applyIfDeleted(DeletionLog.SHELF, uuid, a, b, Shelf::getUpdatedAt, Shelf::getId,
                sideAShelves::delete, sideBShelves::delete);
        }
    }

    private static Set<Long> usedShelfIds(BookRepository books) {
        Set<Long> result = new HashSet<>();
        for (Book book : books.findAll()) {
            result.add(book.getShelfId());
        }
        return result;
    }

    private static Map<String, Book> indexBooks(BookRepository repository) {
        Map<String, Book> result = new HashMap<>();
        for (Book book : repository.findAll()) {
            result.put(book.getUuid(), book);
        }
        return result;
    }

    private static void mergeTags(TagRepository sideA, long bookIdA, TagRepository sideB, long bookIdB,
                                   Deletions deletions) {
        List<Tag> flatA = sideA.findByBookId(bookIdA);
        List<Tag> flatB = sideB.findByBookId(bookIdB);

        Map<String, Tag> aByUuid = toUuidMap(flatA, Tag::getUuid);
        Map<String, Tag> bByUuid = toUuidMap(flatB, Tag::getUuid);
        Map<Long, Tag> aById = toIdMap(flatA);
        Map<Long, Tag> bById = toIdMap(flatB);

        // 親タグを子タグより先に処理できるよう、木構造のたどり順（親→子）でuuidを列挙する。
        List<String> processOrder = new ArrayList<>();
        collectPreOrderUuids(Tag.buildTree(new ArrayList<>(flatA)), processOrder);
        List<String> orderFromB = new ArrayList<>();
        collectPreOrderUuids(Tag.buildTree(new ArrayList<>(flatB)), orderFromB);
        for (String uuid : orderFromB) {
            if (!processOrder.contains(uuid)) {
                processOrder.add(uuid);
            }
        }

        Map<String, Long> resolvedIdA = new HashMap<>();
        Map<String, Long> resolvedIdB = new HashMap<>();
        Set<String> deletedUuids = new HashSet<>();

        for (String uuid : processOrder) {
            Tag a = aByUuid.get(uuid);
            Tag b = bByUuid.get(uuid);
            Tag winner = pickWinner(a, b, Tag::getUpdatedAt);
            Map<Long, Tag> winnerSideById = (winner == a) ? aById : bById;
            String parentUuid = resolveParentUuid(winner, winnerSideById);
            // 親タグが削除された場合、子タグも一緒に削除する（親を削除すると子も消えるため。親が消えたのに
            // 子だけ復活させると、親のいない根のタグになってしまう）。
            if (parentUuid != null && deletedUuids.contains(parentUuid)) {
                deletions.deleteWith(DeletionLog.TAG, uuid, a, b, Tag::getId, sideA::delete, sideB::delete);
                deletedUuids.add(uuid);
                continue;
            }
            if (deletions.applyIfDeleted(DeletionLog.TAG, uuid, a, b, Tag::getUpdatedAt, Tag::getId,
                sideA::delete, sideB::delete)) {
                deletedUuids.add(uuid);
                continue;
            }

            Long parentIdA = parentUuid == null ? null : resolvedIdA.get(parentUuid);
            Long parentIdB = parentUuid == null ? null : resolvedIdB.get(parentUuid);

            Tag resolvedA = sideA.upsertFromSync(bookIdA, uuid, winner.getName(), parentIdA, winner.getUpdatedAt());
            Tag resolvedB = sideB.upsertFromSync(bookIdB, uuid, winner.getName(), parentIdB, winner.getUpdatedAt());

            resolvedIdA.put(uuid, resolvedA.getId());
            resolvedIdB.put(uuid, resolvedB.getId());
        }
    }

    private static String resolveParentUuid(Tag tag, Map<Long, Tag> byId) {
        if (tag == null || tag.getParentTagId() == null) {
            return null;
        }
        Tag parent = byId.get(tag.getParentTagId());
        return parent == null ? null : parent.getUuid();
    }

    private static void collectPreOrderUuids(List<Tag> nodes, List<String> out) {
        for (Tag node : nodes) {
            out.add(node.getUuid());
            collectPreOrderUuids(node.getChildren(), out);
        }
    }

    /**
     * シェルフをuuidでマージする。タグと違い階層は無いので、単純にuuidの和集合ごとに
     * 新しい方を採用して両側へ反映するだけでよい。結果として得られる「uuid→この端末での
     * ローカルなシェルフID」のマップは、この後ブックのshelfIdを解決するために使う。
     */
    private static void mergeShelves(ShelfRepository sideA, Map<String, Long> outIdByUuidA,
                                      ShelfRepository sideB, Map<String, Long> outIdByUuidB) {
        List<Shelf> flatA = sideA.findAll();
        List<Shelf> flatB = sideB.findAll();

        Map<String, Shelf> aByUuid = toUuidMap(flatA, Shelf::getUuid);
        Map<String, Shelf> bByUuid = toUuidMap(flatB, Shelf::getUuid);

        for (String uuid : unionOfKeys(aByUuid, bByUuid)) {
            Shelf a = aByUuid.get(uuid);
            Shelf b = bByUuid.get(uuid);
            Shelf winner = pickWinner(a, b, Shelf::getUpdatedAt);

            Shelf resolvedA = sideA.upsertFromSync(uuid, winner.getName(), winner.getCover(),
                winner.getCreatedAt(), winner.getUpdatedAt());
            Shelf resolvedB = sideB.upsertFromSync(uuid, winner.getName(), winner.getCover(),
                winner.getCreatedAt(), winner.getUpdatedAt());

            outIdByUuidA.put(uuid, resolvedA.getId());
            outIdByUuidB.put(uuid, resolvedB.getId());
        }
    }

    private static Map<Long, String> shelfIdToUuidMap(List<Shelf> shelves) {
        Map<Long, String> result = new HashMap<>();
        for (Shelf shelf : shelves) {
            result.put(shelf.getId(), shelf.getUuid());
        }
        return result;
    }

    /**
     * ブックのノートカラム定義をuuidでマージする。タグと違い階層は無いので、単純にuuidの和集合ごとに
     * 新しい方を採用して両側へ反映するだけでよい。結果として得られる「uuid→このブックでのローカルな
     * カラムID」のマップは、この後mergeNotes()でノートのfieldValuesを解決するために使う。
     */
    private static void mergeNoteColumns(NoteColumnRepository sideA, long bookIdA, Map<String, Long> outIdByUuidA,
                                          NoteColumnRepository sideB, long bookIdB, Map<String, Long> outIdByUuidB,
                                          Deletions deletions) {
        List<NoteColumn> flatA = sideA.findByBookId(bookIdA);
        List<NoteColumn> flatB = sideB.findByBookId(bookIdB);

        Map<String, NoteColumn> aByUuid = toUuidMap(flatA, NoteColumn::getUuid);
        Map<String, NoteColumn> bByUuid = toUuidMap(flatB, NoteColumn::getUuid);

        for (String uuid : unionOfKeys(aByUuid, bByUuid)) {
            NoteColumn a = aByUuid.get(uuid);
            NoteColumn b = bByUuid.get(uuid);
            if (deletions.applyIfDeleted(DeletionLog.COLUMN, uuid, a, b, NoteColumn::getUpdatedAt,
                NoteColumn::getId, sideA::delete, sideB::delete)) {
                continue;
            }
            NoteColumn winner = pickWinner(a, b, NoteColumn::getUpdatedAt);

            NoteColumn resolvedA = sideA.upsertFromSync(bookIdA, uuid, winner.getName(), winner.isRequired(),
                winner.getSortOrder(), winner.isPrimaryKey(), winner.getUpdatedAt());
            NoteColumn resolvedB = sideB.upsertFromSync(bookIdB, uuid, winner.getName(), winner.isRequired(),
                winner.getSortOrder(), winner.isPrimaryKey(), winner.getUpdatedAt());

            outIdByUuidA.put(uuid, resolvedA.getId());
            outIdByUuidB.put(uuid, resolvedB.getId());
        }
    }

    private static void mergeNotes(NoteRepository sideA, TagRepository sideATags,
                                    Map<String, Long> columnIdByUuidA, long bookIdA,
                                    NoteRepository sideB, TagRepository sideBTags,
                                    Map<String, Long> columnIdByUuidB, long bookIdB, Deletions deletions) {
        List<Note> flatA = sideA.findByBookId(bookIdA);
        List<Note> flatB = sideB.findByBookId(bookIdB);

        Map<String, Note> aByUuid = toUuidMap(flatA, Note::getUuid);
        Map<String, Note> bByUuid = toUuidMap(flatB, Note::getUuid);

        List<Tag> tagsA = sideATags.findByBookId(bookIdA);
        List<Tag> tagsB = sideBTags.findByBookId(bookIdB);
        Map<String, Long> tagIdByUuidA = toUuidToIdMap(tagsA);
        Map<String, Long> tagIdByUuidB = toUuidToIdMap(tagsB);
        Map<Long, String> tagUuidByIdA = toIdToUuidMap(tagsA);
        Map<Long, String> tagUuidByIdB = toIdToUuidMap(tagsB);

        // ノートのfieldValuesはノート自身が属していた側のローカルなカラムIDで入っているので、
        // そのままでは相手側に書き込めない。カラムIDをuuidに戻す向きのマップも用意しておく。
        Map<Long, String> columnUuidByIdA = invert(columnIdByUuidA);
        Map<Long, String> columnUuidByIdB = invert(columnIdByUuidB);

        for (String uuid : unionOfKeys(aByUuid, bByUuid)) {
            Note a = aByUuid.get(uuid);
            Note b = bByUuid.get(uuid);
            if (deletions.applyIfDeleted(DeletionLog.NOTE, uuid, a, b, Note::getUpdatedAt, Note::getId,
                sideA::delete, sideB::delete)) {
                continue;
            }
            Note winner = pickWinner(a, b, Note::getUpdatedAt);
            boolean winnerIsA = winner == a;
            Map<Long, String> winnerTagUuidById = winnerIsA ? tagUuidByIdA : tagUuidByIdB;
            Map<Long, String> winnerColumnUuidById = winnerIsA ? columnUuidByIdA : columnUuidByIdB;

            List<String> tagUuids = winner.getTagIds().stream()
                .map(winnerTagUuidById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            List<Long> tagIdsForA = mapToIds(tagUuids, tagIdByUuidA);
            List<Long> tagIdsForB = mapToIds(tagUuids, tagIdByUuidB);

            Map<String, String> fieldValuesByColumnUuid = new HashMap<>();
            winner.getFieldValues().forEach((columnId, value) -> {
                String columnUuid = winnerColumnUuidById.get(columnId);
                if (columnUuid != null) {
                    fieldValuesByColumnUuid.put(columnUuid, value);
                }
            });
            // 更新日時が同じ（＝どちらかが編集したわけではない）のに値の持ち方だけ食い違う場合は、
            // 項目の削除や重複解消など「編集以外」の操作で片方の値が失われているだけなので、
            // 負けた側の入力済みの値を捨てずに残す（引き分けの勝者は呼び出し順で決まる偶然に過ぎない）。
            if (a != null && b != null && a.getUpdatedAt().equals(b.getUpdatedAt())) {
                Note loser = winnerIsA ? b : a;
                Map<Long, String> loserColumnUuidById = winnerIsA ? columnUuidByIdB : columnUuidByIdA;
                loser.getFieldValues().forEach((columnId, value) -> {
                    String columnUuid = loserColumnUuidById.get(columnId);
                    if (columnUuid != null && value != null && !value.isBlank()) {
                        String existing = fieldValuesByColumnUuid.get(columnUuid);
                        if (existing == null || existing.isBlank()) {
                            fieldValuesByColumnUuid.put(columnUuid, value);
                        }
                    }
                });
            }
            Map<Long, String> fieldValuesForA = mapToIdKeyed(fieldValuesByColumnUuid, columnIdByUuidA);
            Map<Long, String> fieldValuesForB = mapToIdKeyed(fieldValuesByColumnUuid, columnIdByUuidB);

            sideA.upsertFromSync(bookIdA, uuid, fieldValuesForA, winner.getBody(), tagIdsForA,
                winner.getBackgroundTheme(), winner.getBackgroundImageFileName(), winner.getTextColor(),
                winner.getCreatedAt(), winner.getUpdatedAt());
            sideB.upsertFromSync(bookIdB, uuid, fieldValuesForB, winner.getBody(), tagIdsForB,
                winner.getBackgroundTheme(), winner.getBackgroundImageFileName(), winner.getTextColor(),
                winner.getCreatedAt(), winner.getUpdatedAt());
        }
    }

    private static Map<Long, String> mapToIdKeyed(Map<String, String> valueByUuid, Map<String, Long> idByUuid) {
        Map<Long, String> result = new HashMap<>();
        valueByUuid.forEach((columnUuid, value) -> {
            Long id = idByUuid.get(columnUuid);
            if (id != null) {
                result.put(id, value);
            }
        });
        return result;
    }

    private static Map<Long, String> invert(Map<String, Long> uuidToId) {
        Map<Long, String> result = new HashMap<>();
        uuidToId.forEach((uuid, id) -> result.put(id, uuid));
        return result;
    }

    private static List<Long> mapToIds(List<String> uuids, Map<String, Long> idByUuid) {
        List<Long> result = new ArrayList<>();
        for (String uuid : uuids) {
            Long id = idByUuid.get(uuid);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private static <T> Map<String, T> toUuidMap(List<T> items, Function<T, String> uuidOf) {
        Map<String, T> result = new HashMap<>();
        for (T item : items) {
            result.put(uuidOf.apply(item), item);
        }
        return result;
    }

    private static Map<Long, Tag> toIdMap(List<Tag> tags) {
        Map<Long, Tag> result = new HashMap<>();
        for (Tag tag : tags) {
            result.put(tag.getId(), tag);
        }
        return result;
    }

    private static Map<String, Long> toUuidToIdMap(List<Tag> tags) {
        Map<String, Long> result = new HashMap<>();
        for (Tag tag : tags) {
            result.put(tag.getUuid(), tag.getId());
        }
        return result;
    }

    private static Map<Long, String> toIdToUuidMap(List<Tag> tags) {
        Map<Long, String> result = new HashMap<>();
        for (Tag tag : tags) {
            result.put(tag.getId(), tag.getUuid());
        }
        return result;
    }

    private static Set<String> unionOfKeys(Map<String, ?> a, Map<String, ?> b) {
        Set<String> result = new LinkedHashSet<>();
        result.addAll(a.keySet());
        result.addAll(b.keySet());
        return result;
    }

    private static <T> T pickWinner(T a, T b, Function<T, Instant> updatedAtOf) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return updatedAtOf.apply(a).isAfter(updatedAtOf.apply(b)) ? a : b;
    }

    /**
     * 両端末の削除の記録をまとめ、「削除されたものか」を判定して両側から削除する。
     * 記録の日時は、両側のうち新しい方を採用する。
     */
    private static final class Deletions {
        private static final String[] KINDS = {
            DeletionLog.SHELF, DeletionLog.BOOK, DeletionLog.TAG, DeletionLog.COLUMN, DeletionLog.NOTE};

        private final DeletionLog logA;
        private final DeletionLog logB;
        private final Map<String, Map<String, Instant>> deletedAtByKind = new HashMap<>();

        Deletions(DeletionLog logA, DeletionLog logB) {
            this.logA = logA;
            this.logB = logB;
            for (String kind : KINDS) {
                Map<String, Instant> merged = new HashMap<>(logA.findAll(kind));
                logB.findAll(kind).forEach((uuid, at) -> merged.merge(uuid, at,
                    (x, y) -> x.isAfter(y) ? x : y));
                deletedAtByKind.put(kind, merged);
            }
        }

        /**
         * 削除の記録があり、かつ削除が両側の最終更新より後なら、両側から削除してtrueを返す。
         * 削除の後に編集されていた（更新の方が新しい）場合は、削除の記録を取り消してfalseを返す
         * （編集された側の内容を残す）。記録が無ければfalse。
         */
        <T> boolean applyIfDeleted(String kind, String uuid, T a, T b, Function<T, Instant> updatedAtOf,
                                   Function<T, Long> idOf, Consumer<Long> deleteA, Consumer<Long> deleteB) {
            Instant deletedAt = deletedAtByKind.get(kind).get(uuid);
            if (deletedAt == null) {
                return false;
            }
            Instant latest = null;
            for (T item : java.util.Arrays.asList(a, b)) {
                if (item != null) {
                    Instant updatedAt = updatedAtOf.apply(item);
                    if (updatedAt != null && (latest == null || updatedAt.isAfter(latest))) {
                        latest = updatedAt;
                    }
                }
            }
            if (latest != null && latest.isAfter(deletedAt)) {
                forget(kind, uuid);
                return false;
            }
            deleteWith(kind, uuid, a, b, idOf, deleteA, deleteB);
            return true;
        }

        <T> void deleteWith(String kind, String uuid, T a, T b, Function<T, Long> idOf,
                            Consumer<Long> deleteA, Consumer<Long> deleteB) {
            if (a != null) {
                deleteA.accept(idOf.apply(a));
            }
            if (b != null) {
                deleteB.accept(idOf.apply(b));
            }
            // 削除したことを、両側に同じ日時で残す（記録が無かった側でも、以後の同期で削除として扱えるように）。
            Instant deletedAt = deletedAtByKind.get(kind).get(uuid);
            if (deletedAt == null) {
                deletedAt = Instant.now();
                deletedAtByKind.get(kind).put(uuid, deletedAt);
            }
            logA.record(kind, uuid, deletedAt);
            logB.record(kind, uuid, deletedAt);
        }

        /** 削除の記録を両側から消す（その後に編集された、または削除できないものを残すとき）。 */
        void forget(String kind, String uuid) {
            deletedAtByKind.get(kind).remove(uuid);
            logA.remove(kind, uuid);
            logB.remove(kind, uuid);
        }
    }
}
