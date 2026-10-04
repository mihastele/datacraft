/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.mysql;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import io.datacraft.core.adapter.DatabaseException;

final class Failures {
    private Failures() { }
    static DatabaseException sanitize(SQLException failure) {
        String state = failure.getSQLState() == null ? "" : failure.getSQLState();
        DatabaseException.Kind kind;
        if (failure instanceof SQLTimeoutException || failure.getErrorCode() == 1969 || failure.getErrorCode() == 3024) kind = DatabaseException.Kind.TIMEOUT;
        else if (failure.getErrorCode() == 1792 || state.equals("25006")) kind = DatabaseException.Kind.POLICY;
        else if (failure.getErrorCode() == 1317 || state.equals("70100")) kind = DatabaseException.Kind.CANCELLED;
        else if (state.startsWith("28")) kind = DatabaseException.Kind.AUTHENTICATION;
        else if (state.startsWith("08")) kind = DatabaseException.Kind.CONNECTION;
        else if (failure.getErrorCode() == 1044 || failure.getErrorCode() == 1142 || failure.getErrorCode() == 1143) kind = DatabaseException.Kind.PERMISSION;
        else if (failure.getErrorCode() == 1049 || failure.getErrorCode() == 1146) kind = DatabaseException.Kind.NOT_FOUND;
        else kind = DatabaseException.Kind.OTHER;
        return new DatabaseException(kind);
    }
}
