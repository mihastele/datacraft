/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import io.datacraft.core.adapter.DatabaseException;
import static org.junit.jupiter.api.Assertions.*;

class QueryContractsTest {
    @Test void policyRejectsScriptsWritesAndEmptyQueries() {
        for (int count : new int[] {0, 2, 3}) {
            assertEquals(DatabaseException.Kind.POLICY,
                    assertThrows(DatabaseException.class,
                            () -> ReadOnlyQueryPolicy.requireSingleSelect(count, true)).kind());
        }
        assertThrows(DatabaseException.class, () -> ReadOnlyQueryPolicy.requireSingleSelect(1, false));
        assertDoesNotThrow(() -> ReadOnlyQueryPolicy.requireSingleSelect(1, true));
    }
    @Test void resultCopiesRowsAndPreservesNullVersusEmptyAndLiteralNull() {
        var row = new ArrayList<>(List.of(new QueryCell(null, false), new QueryCell("", false), new QueryCell("NULL", false)));
        var result = new QueryResult(List.of(new QueryColumn("a", "text"), new QueryColumn("b", "text"),
                new QueryColumn("c", "text")), List.of(row), false, 1);
        row.clear();
        assertTrue(result.rows().getFirst().getFirst().isNull());
        assertEquals("", result.rows().getFirst().get(1).value());
        assertFalse(result.rows().getFirst().get(2).isNull());
        assertThrows(UnsupportedOperationException.class, () -> result.rows().getFirst().clear());
    }
    @Test void requestsRejectUnboundedLimitsAndDoNotPrintSql() {
        assertThrows(IllegalArgumentException.class, () -> new QueryRequest("", 100, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> new QueryRequest("SELECT 1", 1001, 100, 10));
        assertThrows(IllegalArgumentException.class, () -> new QueryRequest("SELECT 1", 100, 4097, 10));
        assertThrows(IllegalArgumentException.class, () -> new QueryRequest("SELECT 1", 100, 100, 0));
        assertFalse(new QueryRequest("SELECT sensitive", 1, 1, 1).toString().contains("sensitive"));
    }
    @Test void cancellationBeforeRegistrationStillInvokesCallback() {
        var token = new QueryCancellation();
        var calls = new AtomicInteger();
        token.cancel();
        var registration = token.onCancel(calls::incrementAndGet);
        registration.close();
        assertTrue(token.isCancelled());
        assertEquals(1, calls.get());
    }
    @Test void completedWorkDetachesCancellationAndRepeatedCancelIsIdempotent() {
        var token = new QueryCancellation();
        var calls = new AtomicInteger();
        var registration = token.onCancel(calls::incrementAndGet);
        registration.close();
        token.cancel();
        assertEquals(0, calls.get());
        token = new QueryCancellation();
        registration = token.onCancel(calls::incrementAndGet);
        token.cancel();
        token.cancel();
        registration.close();
        assertEquals(1, calls.get());
    }
}
