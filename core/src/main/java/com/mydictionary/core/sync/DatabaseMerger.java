package com.mydictionary.core.sync;

import com.mydictionary.core.model.Book;
import com.mydictionary.core.model.Note;
import com.mydictionary.core.model.NoteColumn;
import com.mydictionary.core.model.Shelf;
import com.mydictionary.core.model.Tag;
import com.mydictionary.core.repository.BookRepository;
import com.mydictionary.core.repository.NoteColumnRepository;
import com.mydictionary.core.repository.NoteRepository;
import com.mydictionary.core.repository.ShelfRepository;
import com.mydictionary.core.repository.TagRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 2つのデータソース（例: 端末のローカルDBと、共有フォルダ上に置かれたもう一方の端末のDBのコピー）を
 * uuid・更新日時をもとに双方向でマージする。
 * 同じuuidのエンティティが両方にある場合は更新日時が新しい方を採用し、両側に反映する。
 * 片方にしかないuuidはもう片方に新規追加する。
 *
 * 制限事項: 削除の同期はサポートしない。片方で削除したブック/タグ/ノート/カラムは、
 * もう片方にまだ存在していれば同期のたびに復活してしまう（既知の制限）。
 */
public final class DatabaseMerger {

    private DatabaseMerger() {
    }

    public static void merge(
            ShelfRepository sideAShelves, BookRepository sideABooks, TagRepository sideATags,
            NoteColumnRepository sideANoteColumns, NoteRepository sideANotes,
            ShelfRepository sideBShelves, BookRepository sideBBooks, TagRepository sideBTags,
            NoteColumnRepository sideBNoteColumns, NoteRepository sideBNotes) {

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
            Book winner = pickWinner(a, b, Book::getUpdatedAt);
            Map<Long, String> winnerShelfUuidById = (winner == a) ? shelfUuidByIdA : shelfUuidByIdB;
            String winnerShelfUuid = winnerShelfUuidById.get(winner.getShelfId());
            long shelfIdForA = shelfIdByUuidA.get(winnerShelfUuid);
            long shelfIdForB = shelfIdByUuidB.get(winnerShelfUuid);

            Book resolvedA = sideABooks.upsertFromSync(uuid, shelfIdForA, winner.getTitle(), winner.getTheme(),
                winner.getFont(), winner.getFontSizePt(), winner.getCover(), winner.getCreatedAt(),
                winner.getUpdatedAt());
            Book resolvedB = sideBBooks.upsertFromSync(uuid, shelfIdForB, winner.getTitle(), winner.getTheme(),
                winner.getFont(), winner.getFontSizePt(), winner.getCover(), winner.getCreatedAt(),
                winner.getUpdatedAt());

            mergeTags(sideATags, resolvedA.getId(), sideBTags, resolvedB.getId());

            Map<String, Long> columnIdByUuidA = new HashMap<>();
            Map<String, Long> columnIdByUuidB = new HashMap<>();
            mergeNoteColumns(sideANoteColumns, resolvedA.getId(), columnIdByUuidA,
                sideBNoteColumns, resolvedB.getId(), columnIdByUuidB);

            mergeNotes(sideANotes, sideATags, columnIdByUuidA, resolvedA.getId(),
                sideBNotes, sideBTags, columnIdByUuidB, resolvedB.getId());
        }
    }

    private static Map<String, Book> indexBooks(BookRepository repository) {
        Map<String, Book> result = new HashMap<>();
        for (Book book : repository.findAll()) {
            result.put(book.getUuid(), book);
        }
        return result;
    }

    private static void mergeTags(TagRepository sideA, long bookIdA, TagRepository sideB, long bookIdB) {
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

        for (String uuid : processOrder) {
            Tag a = aByUuid.get(uuid);
            Tag b = bByUuid.get(uuid);
            Tag winner = pickWinner(a, b, Tag::getUpdatedAt);
            Map<Long, Tag> winnerSideById = (winner == a) ? aById : bById;
            String parentUuid = resolveParentUuid(winner, winnerSideById);

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
                                          NoteColumnRepository sideB, long bookIdB, Map<String, Long> outIdByUuidB) {
        List<NoteColumn> flatA = sideA.findByBookId(bookIdA);
        List<NoteColumn> flatB = sideB.findByBookId(bookIdB);

        Map<String, NoteColumn> aByUuid = toUuidMap(flatA, NoteColumn::getUuid);
        Map<String, NoteColumn> bByUuid = toUuidMap(flatB, NoteColumn::getUuid);

        for (String uuid : unionOfKeys(aByUuid, bByUuid)) {
            NoteColumn a = aByUuid.get(uuid);
            NoteColumn b = bByUuid.get(uuid);
            NoteColumn winner = pickWinner(a, b, NoteColumn::getUpdatedAt);

            NoteColumn resolvedA = sideA.upsertFromSync(bookIdA, uuid, winner.getName(), winner.isRequired(),
                winner.getSortOrder(), winner.getUpdatedAt());
            NoteColumn resolvedB = sideB.upsertFromSync(bookIdB, uuid, winner.getName(), winner.isRequired(),
                winner.getSortOrder(), winner.getUpdatedAt());

            outIdByUuidA.put(uuid, resolvedA.getId());
            outIdByUuidB.put(uuid, resolvedB.getId());
        }
    }

    private static void mergeNotes(NoteRepository sideA, TagRepository sideATags,
                                    Map<String, Long> columnIdByUuidA, long bookIdA,
                                    NoteRepository sideB, TagRepository sideBTags,
                                    Map<String, Long> columnIdByUuidB, long bookIdB) {
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
}
