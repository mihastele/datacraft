/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.connection;

import java.util.Objects;
import java.util.UUID;

/** A named non-secret profile. The ID is also the opaque credential reference. */
public record SavedConnection(UUID id, String name, ConnectionProfile settings, boolean storePassword) {
    public SavedConnection {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name");
        Objects.requireNonNull(settings, "settings");
        name = name.strip();
        if (name.isEmpty() || name.length() > 100 || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Enter a profile name of 1–100 characters.");
        if (settings.kind() == DatabaseKind.SQLITE && storePassword)
            throw new IllegalArgumentException("SQLite file profiles have no password.");
    }
    @Override public String toString() { return name; }
}
