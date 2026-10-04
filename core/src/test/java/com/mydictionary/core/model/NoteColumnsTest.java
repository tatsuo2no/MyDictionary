package com.mydictionary.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoteColumnsTest {

    private static NoteColumn column(long id, int sortOrder, boolean primary) {
        NoteColumn column = new NoteColumn(id, "uuid-" + id, 1, "列" + id, false, sortOrder, Instant.now());
        column.setPrimaryKey(primary);
        return column;
    }

    @Test
    void firstColumnIsPrimaryWhenNoneIsMarked() {
        List<NoteColumn> columns = List.of(column(1, 0, false), column(2, 1, false));
        assertEquals(1, NoteColumns.primary(columns).getId());
    }

    @Test
    void markedColumnIsPrimaryEvenIfNotFirst() {
        List<NoteColumn> columns = List.of(column(1, 0, false), column(2, 1, true), column(3, 2, false));
        assertEquals(2, NoteColumns.primary(columns).getId());
        assertTrue(NoteColumns.isPrimary(columns.get(1), columns));
        assertFalse(NoteColumns.isPrimary(columns.get(0), columns));
    }

    @Test
    void lowestSortOrderWinsWhenSyncLeftSeveralMarked() {
        List<NoteColumn> columns = List.of(column(1, 0, false), column(2, 1, true), column(3, 2, true));
        assertEquals(2, NoteColumns.primary(columns).getId());
    }

    @Test
    void primaryValueReadsTheMarkedColumn() {
        List<NoteColumn> columns = List.of(column(1, 0, false), column(2, 1, true));
        Note note = new Note(1, "n", 1, "", Instant.now(), Instant.now());
        note.setFieldValues(Map.of(1L, "いぬ", 2L, "犬"));
        assertEquals("犬", NoteColumns.primaryValue(note, columns));
    }

    @Test
    void emptyColumnListIsRejected() {
        assertThrows(IllegalStateException.class, () -> NoteColumns.primary(List.of()));
    }
}
