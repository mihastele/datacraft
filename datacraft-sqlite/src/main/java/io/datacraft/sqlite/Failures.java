/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.sqlite;

import java.sql.SQLException;
import io.datacraft.core.adapter.DatabaseException;

final class Failures {
    private Failures() { }
    static DatabaseException sanitize(SQLException failure) {
        // Mask extended result bits; never retain driver messages, filenames, or SQL.
        var kind = switch (failure.getErrorCode() & 255) {
            case 3, 23 -> DatabaseException.Kind.PERMISSION; // PERM / AUTH
            case 5, 6 -> DatabaseException.Kind.TIMEOUT; // BUSY / LOCKED
            case 8 -> DatabaseException.Kind.POLICY; // READONLY
            case 9 -> DatabaseException.Kind.TIMEOUT; // INTERRUPT; query cancellation is classified by its signal.
            case 10, 14 -> DatabaseException.Kind.CONNECTION; // IOERR / CANTOPEN
            default -> DatabaseException.Kind.OTHER;
        };
        return new DatabaseException(kind);
    }
}
