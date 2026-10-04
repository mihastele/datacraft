/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.postgresql;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import io.datacraft.core.adapter.DatabaseException;
import io.datacraft.core.adapter.DatabaseException.Kind;

final class Failures {
    private Failures() { }
    static DatabaseException sanitize(SQLException failure) {
        String state = failure.getSQLState();
        Kind kind;
        if (failure instanceof SQLTimeoutException || "57014".equals(state)) kind = Kind.TIMEOUT;
        else if (state != null && state.startsWith("28")) kind = Kind.AUTHENTICATION;
        else if (state != null && state.startsWith("08")) kind = Kind.CONNECTION;
        else if ("25006".equals(state) || "0A000".equals(state)) kind = Kind.POLICY;
        else if ("42501".equals(state)) kind = Kind.PERMISSION;
        else if ("3D000".equals(state) || "42P01".equals(state)) kind = Kind.NOT_FOUND;
        else kind = Kind.OTHER;
        return new DatabaseException(kind);
    }
}
