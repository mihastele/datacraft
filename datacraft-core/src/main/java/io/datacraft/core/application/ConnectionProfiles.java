/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.application;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import io.datacraft.core.connection.SavedConnection;
import io.datacraft.core.persistence.*;

/** Single-owner profile service. Secret operations always require an explicit saved preference. */
public final class ConnectionProfiles {
    private final ConnectionRepository repository;
    private final CredentialStore credentials;
    public ConnectionProfiles(ConnectionRepository repository, CredentialStore credentials) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.credentials = java.util.Objects.requireNonNull(credentials);
    }
    public boolean canStorePassword() { return credentials.available(); }
    public List<SavedConnection> list() throws IOException { return repository.load(); }
    public void save(SavedConnection profile, char[] password) throws IOException {
        try { repository.withLock(() -> { saveLocked(profile, password); return null; }); }
        finally { Arrays.fill(password, '\0'); }
    }
    private void saveLocked(SavedConnection profile, char[] password) throws IOException {
        char[] backup = null;
        boolean changedSecret = false;
        try {
            var previous = repository.load();
            var old = previous.stream().filter(p -> p.id().equals(profile.id())).findFirst();
            if (profile.storePassword()) {
                if (!credentials.available()) throw new IOException("Secure password storage is unavailable.");
                if (password.length == 0) {
                    // An empty editor preserves an existing password, but never silently enables storage.
                    var retained = credentials.read(profile.id());
                    if (old.isEmpty() || !old.get().storePassword() || !old.get().settings().equals(profile.settings()) || retained.isEmpty()) {
                        retained.ifPresent(value -> Arrays.fill(value, '\0'));
                        throw new IOException("Enter a password before enabling secure storage.");
                    }
                    retained.ifPresent(value -> Arrays.fill(value, '\0'));
                } else {
                    if (old.isPresent() && old.get().storePassword()) backup = credentials.read(profile.id()).orElse(null);
                    credentials.write(profile.id(), password); changedSecret = true;
                }
            } else if (old.isPresent() && old.get().storePassword()) {
                if (!credentials.available()) throw new IOException("Remove the saved password on its original platform.");
                credentials.delete(profile.id());
            }
            var next = new ArrayList<>(previous);
            next.removeIf(p -> p.id().equals(profile.id())); next.add(profile);
            try { repository.save(next); }
            catch (IOException failure) {
                if (changedSecret) {
                    try {
                        if (backup == null) credentials.delete(profile.id());
                        else credentials.write(profile.id(), backup);
                    } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
                }
                throw failure;
            }
        } finally { Arrays.fill(password, '\0'); if (backup != null) Arrays.fill(backup, '\0'); }
    }
    public char[] password(UUID id) throws IOException {
        return password(id, null);
    }
    public char[] password(UUID id, io.datacraft.core.connection.ConnectionProfile expected) throws IOException {
        return repository.withLock(() -> passwordLocked(id, expected));
    }
    private char[] passwordLocked(UUID id, io.datacraft.core.connection.ConnectionProfile expected) throws IOException {
        var profile = repository.load().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IOException("Profile no longer exists."));
        if (expected != null && !profile.settings().equals(expected)) throw new IOException("Profile changed. Reload before connecting.");
        if (!profile.storePassword() || !credentials.available()) return new char[0];
        return credentials.read(id).orElseGet(() -> new char[0]);
    }
    public void delete(UUID id) throws IOException {
        repository.withLock(() -> { deleteLocked(id); return null; });
    }
    private void deleteLocked(UUID id) throws IOException {
        var profiles = new ArrayList<>(repository.load());
        var old = profiles.stream().filter(p -> p.id().equals(id)).findFirst();
        if (old.isEmpty()) return;
        if (old.get().storePassword()) {
            if (!credentials.available()) throw new IOException("Remove the saved password on its original platform.");
            credentials.delete(id);
        }
        profiles.removeIf(p -> p.id().equals(id)); repository.save(profiles);
    }
}
