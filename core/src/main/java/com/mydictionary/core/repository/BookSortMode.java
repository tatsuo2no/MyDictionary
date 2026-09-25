package com.mydictionary.core.repository;

/**
 * ブックリスト画面でのブックの並び順。
 * MANUAL はドラッグ&ドロップで決めた順（DBの sort_order 列。端末ローカル情報で同期対象外）、
 * TITLE は名前順、CREATED_AT は作成日順。
 */
public enum BookSortMode {
    MANUAL,
    TITLE,
    CREATED_AT
}
