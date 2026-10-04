/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import io.datacraft.core.adapter.DatabaseException;
import static org.junit.jupiter.api.Assertions.*;

class QueryResultBuilderTest {
    private static final QueryColumn COLUMN = new QueryColumn("value", "text");

    @Test void preservesNullEmptyAndSurrogatePairsAndRejectsExtraRows() throws Exception {
        var builder = new QueryResultBuilder(new QueryRequest("SELECT 1", 1, 2, 5),
                Collections.nCopies(3, COLUMN));
        assertTrue(builder.addRow(Arrays.asList(null, "", "a\ud83d\ude00")));
        assertFalse(builder.addRow(List.of("x", "x", "x")));
        var result = builder.build(3);
        assertTrue(result.rows().getFirst().getFirst().isNull());
        assertEquals("", result.rows().getFirst().get(1).value());
        assertEquals("a", result.rows().getFirst().get(2).value());
        assertTrue(result.rows().getFirst().get(2).truncated());
        assertTrue(result.truncated());
    }

    @Test void discardsWholeRowWhenAggregateBudgetIsExceeded() throws Exception {
        var builder = new QueryResultBuilder(new QueryRequest("SELECT 1", 1000, 4096, 5), List.of(COLUMN));
        for (int i = 0; i < 488; i++) assertTrue(builder.addRow(List.of("x".repeat(4096))));
        assertFalse(builder.addRow(List.of("x".repeat(4096))));
        assertEquals(488, builder.build(0).rows().size());
        assertTrue(builder.build(0).truncated());
    }

    @Test void validatesColumnAndRowShape() {
        var request = new QueryRequest("SELECT 1", 1, 1, 1);
        assertEquals(DatabaseException.Kind.RESULT_LIMIT, assertThrows(DatabaseException.class,
                () -> new QueryResultBuilder(request, Collections.nCopies(129, COLUMN))).kind());
        assertThrows(DatabaseException.class, () -> new QueryResultBuilder(request, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new QueryResultBuilder(request, List.of(COLUMN)).addRow(List.of()));
    }
}
