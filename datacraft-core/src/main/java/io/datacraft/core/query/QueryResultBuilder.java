/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import io.datacraft.core.adapter.DatabaseException;

/** Single-owner display accumulator. Adapters must also bound values before transfer. */
public final class QueryResultBuilder {
    public static final int MAX_COLUMNS = 128;
    public static final int MAX_CHARACTERS = 2_000_000;
    private final QueryRequest request;
    private final List<QueryColumn> columns;
    private final List<List<QueryCell>> rows = new ArrayList<>();
    private int retainedCharacters;
    private boolean truncated;

    public QueryResultBuilder(QueryRequest request, List<QueryColumn> columns) throws DatabaseException {
        this.request = Objects.requireNonNull(request);
        this.columns = List.copyOf(columns);
        if (columns.isEmpty() || columns.size() > MAX_COLUMNS) {
            throw new DatabaseException(DatabaseException.Kind.RESULT_LIMIT);
        }
    }

    /** Returns false when the row cannot be retained; the adapter should stop fetching. */
    public boolean addRow(List<String> values) {
        if (values.size() != columns.size()) throw new IllegalArgumentException("Row width differs from columns.");
        if (rows.size() == request.rowLimit()) { truncated = true; return false; }
        var cells = new ArrayList<QueryCell>(values.size());
        int characters = 0;
        boolean clippedRow = false;
        for (String value : values) {
            boolean clipped = value != null && value.length() > request.cellCharacterLimit();
            if (clipped) {
                int end = request.cellCharacterLimit();
                if (Character.isHighSurrogate(value.charAt(end - 1))
                        && Character.isLowSurrogate(value.charAt(end))) end--;
                value = value.substring(0, end);
            }
            if (value != null) characters += value.length();
            cells.add(new QueryCell(value, clipped));
            clippedRow |= clipped;
        }
        if (retainedCharacters + characters > MAX_CHARACTERS) { truncated = true; return false; }
        retainedCharacters += characters;
        truncated |= clippedRow;
        rows.add(List.copyOf(cells));
        return true;
    }

    public QueryResult build(long elapsedMillis) {
        return new QueryResult(columns, rows, truncated, elapsedMillis);
    }
}
