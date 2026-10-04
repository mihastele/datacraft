/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

import io.datacraft.core.adapter.DatabaseException;

/** Central MVP gate; classification is supplied by the database dialect implementation. */
public final class ReadOnlyQueryPolicy {
    private ReadOnlyQueryPolicy() { }
    public static void requireSingleSelect(int statementCount, boolean select) throws DatabaseException {
        if (statementCount != 1 || !select) throw new DatabaseException(DatabaseException.Kind.POLICY);
    }
}
