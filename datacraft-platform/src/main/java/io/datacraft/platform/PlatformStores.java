/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.platform;

import java.nio.file.Path;
import java.util.Locale;
import io.datacraft.core.application.ConnectionProfiles;
import io.datacraft.core.persistence.CredentialStore;

/** OS composition outside the core and UI; no credentials are written to application files. */
public final class PlatformStores {
    private PlatformStores() { }
    public static ConnectionProfiles profiles() {
        return new ConnectionProfiles(new FileConnectionRepository(directory()), credentials());
    }
    public static CredentialStore credentials() {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")) {
            try { return new WindowsCredentialStore(); } catch (LinkageError failure) { return new UnavailableCredentialStore(); }
        }
        return new UnavailableCredentialStore();
    }
    public static Path directory() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            String local = System.getenv("LOCALAPPDATA");
            return (local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), "AppData", "Local") : Path.of(local)).resolve("DataCraft");
        }
        if (os.startsWith("mac")) return Path.of(System.getProperty("user.home"), "Library", "Application Support", "DataCraft");
        String config = System.getenv("XDG_CONFIG_HOME");
        return (config == null || config.isBlank() ? Path.of(System.getProperty("user.home"), ".config") : Path.of(config)).resolve("datacraft");
    }
}
