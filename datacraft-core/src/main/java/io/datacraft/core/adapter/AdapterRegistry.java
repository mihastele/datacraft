/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.adapter;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import io.datacraft.core.connection.DatabaseKind;

/** Explicit composition instead of reflective discovery or driver selection in the UI. */
public final class AdapterRegistry {
    private final Map<DatabaseKind, DatabaseAdapter> adapters;
    public AdapterRegistry(Map<DatabaseKind, DatabaseAdapter> adapters) {
        this.adapters = Map.copyOf(adapters);
        if (adapters.isEmpty()) throw new IllegalArgumentException("At least one adapter is required.");
    }
    public Set<DatabaseKind> available() { return adapters.keySet(); }
    public DatabaseAdapter resolve(DatabaseKind kind) throws DatabaseException {
        var adapter = adapters.get(Objects.requireNonNull(kind, "kind"));
        if (adapter == null) throw new DatabaseException(DatabaseException.Kind.POLICY);
        return adapter;
    }
}
