/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

import java.util.List;

/** Bounded, immutable display result; truncated is true when rows or cells were omitted. */
public record QueryResult(List<QueryColumn> columns, List<List<QueryCell>> rows,
        boolean truncated, long elapsedMillis) {
    public QueryResult {
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
        int columnCount = columns.size();
        if (elapsedMillis < 0 || rows.stream().anyMatch(row -> row.size() != columnCount)) {
            throw new IllegalArgumentException("Invalid query result shape.");
        }
    }
    @Override public String toString() { return "QueryResult[" + rows.size() + " rows; values omitted]"; }
}
