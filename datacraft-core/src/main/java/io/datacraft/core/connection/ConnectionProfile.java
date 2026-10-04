/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.connection;

/** Immutable non-secret settings; transport-specific properties belong to their profile. */
public sealed interface ConnectionProfile permits ConnectionSettings, SqliteConnectionSettings {
    DatabaseKind kind();
    Environment environment();
    int timeoutSeconds();
}
