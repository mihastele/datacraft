/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.persistence;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/** OS-backed secrets, separate from profiles. Caller owns and wipes read/write arrays. */
public interface CredentialStore {
    boolean available();
    Optional<char[]> read(UUID id) throws IOException;
    void write(UUID id, char[] password) throws IOException;
    void delete(UUID id) throws IOException;
}
