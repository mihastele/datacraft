/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.adapter;

import java.util.Objects;

/** Sanitized failure; driver messages and causes may contain credentials or user data. */
public final class DatabaseException extends Exception {
    private static final long serialVersionUID = 1L;
    public enum Kind { CONNECTION, AUTHENTICATION, TIMEOUT, CANCELLED, POLICY, RESULT_LIMIT,
        PERMISSION, NOT_FOUND, CLOSED, OTHER }
    private final Kind kind;

    public DatabaseException(Kind kind) {
        super("Database operation failed: " + Objects.requireNonNull(kind, "kind").name(), null);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
