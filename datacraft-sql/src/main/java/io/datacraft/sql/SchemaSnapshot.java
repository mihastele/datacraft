/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sql;

import java.util.List;
import java.util.Map;
import io.datacraft.core.metadata.*;

/** Only metadata already discovered from the active connection; absence means unknown. */
public record SchemaSnapshot(List<String> schemas, List<RelationMetadata> relations,
                             Map<QualifiedName, List<ColumnMetadata>> columns) {
    public static final SchemaSnapshot EMPTY = new SchemaSnapshot(List.of(), List.of(), Map.of());
    public SchemaSnapshot {
        schemas = List.copyOf(schemas); relations = List.copyOf(relations);
        columns = columns.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
