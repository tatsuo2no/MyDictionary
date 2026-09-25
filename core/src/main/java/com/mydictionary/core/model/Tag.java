package com.mydictionary.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** ブックに紐づくタグ。parentTagIdによって2段階以上の入れ子構造を表現する。 */
public class Tag {
    private long id;
    private final String uuid;
    private final long bookId;
    private String name;
    private Long parentTagId;
    private Instant updatedAt;
    private final List<Tag> children = new ArrayList<>();

    /**
     * uuidはデバイス間同期でタグの同一性を判定するための識別子。
     * 新規作成してinsert()に渡す場合はnullでよい（リポジトリが自動採番する）。
     */
    public Tag(long id, String uuid, long bookId, String name, Long parentTagId, Instant updatedAt) {
        this.id = id;
        this.uuid = uuid;
        this.bookId = bookId;
        this.name = name;
        this.parentTagId = parentTagId;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public long getBookId() {
        return bookId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentTagId() {
        return parentTagId;
    }

    public void setParentTagId(Long parentTagId) {
        this.parentTagId = parentTagId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Tag> getChildren() {
        return children;
    }

    public void addChild(Tag child) {
        children.add(child);
    }

    /**
     * 指定したタグから根本の祖先タグまでを半角コロンで連結したフルパスを返す（例: "親タグ:子タグ"）。
     * 親を持たないタグの場合は自身の名前のみを返す。同名の親子タグをノート画面等で見分けられるように使う。
     */
    public static String fullPath(long tagId, Map<Long, Tag> tagById) {
        List<String> parts = new ArrayList<>();
        Long currentId = tagId;
        while (currentId != null) {
            Tag tag = tagById.get(currentId);
            if (tag == null) {
                break;
            }
            parts.add(0, tag.getName());
            currentId = tag.getParentTagId();
        }
        return String.join(":", parts);
    }

    /** フラットなタグ一覧から親子関係を組み立て、ルートタグの一覧を返す。 */
    public static List<Tag> buildTree(List<Tag> flatTags) {
        List<Tag> roots = new ArrayList<>();
        for (Tag tag : flatTags) {
            if (tag.getParentTagId() == null) {
                roots.add(tag);
            }
        }
        for (Tag parent : flatTags) {
            for (Tag candidate : flatTags) {
                Long parentTagId = candidate.getParentTagId();
                if (parentTagId != null && parentTagId == parent.getId()) {
                    parent.addChild(candidate);
                }
            }
        }
        return roots;
    }
}
