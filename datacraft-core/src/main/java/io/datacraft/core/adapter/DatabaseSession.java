/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.adapter;

import java.util.List;
import io.datacraft.core.metadata.ColumnMetadata;
import io.datacraft.core.metadata.QualifiedName;
import io.datacraft.core.metadata.RelationMetadata;
import io.datacraft.core.query.QueryCancellation;
import io.datacraft.core.query.QueryRequest;
import io.datacraft.core.query.QueryResult;

/**
 * Single-owner, blocking, read-only session. Returned lists are immutable snapshots.
 * QueryCancellation may be signalled from another thread. Close is idempotent;
 * other calls after close fail. No write or transaction-control API is exposed.
 */
public interface DatabaseSession extends AutoCloseable {
    boolean ping() throws DatabaseException;
    List<String> listSchemas() throws DatabaseException;
    List<RelationMetadata> listRelations(String schema) throws DatabaseException;
    /** Missing objects fail with NOT_FOUND; zero-column relations produce an empty list. */
    List<ColumnMetadata> describeColumns(QualifiedName relation) throws DatabaseException;
    QueryResult query(QueryRequest request, QueryCancellation cancellation) throws DatabaseException;
    @Override void close() throws DatabaseException;
}
