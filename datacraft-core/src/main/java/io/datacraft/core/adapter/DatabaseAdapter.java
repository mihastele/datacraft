/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.adapter;

import io.datacraft.core.connection.ConnectionProfile;

/** Blocking operations must be called off the UI thread. */
public interface DatabaseAdapter {
    Capabilities capabilities();

    /**
     * Opens a caller-owned session. Password is non-null, caller-owned, never modified or
     * retained by DataCraft, and must be cleared by the caller after this call.
     * Empty passwords may be used for explicitly configured passwordless authentication.
     * Drivers may require an internal String copy whose lifetime cannot be controlled.
     */
    DatabaseSession connect(ConnectionProfile settings, char[] password) throws DatabaseException;
}
