/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.platform;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import io.datacraft.core.persistence.CredentialStore;

/** Explicitly unsupported provider; never falls back to file-based secret storage. */
public final class UnavailableCredentialStore implements CredentialStore {
    @Override public boolean available() { return false; }
    @Override public Optional<char[]> read(UUID id) throws IOException { throw unavailable(); }
    @Override public void write(UUID id, char[] password) throws IOException { throw unavailable(); }
    @Override public void delete(UUID id) throws IOException { throw unavailable(); }
    private static IOException unavailable() { return new IOException("Secure password storage is unavailable."); }
}
