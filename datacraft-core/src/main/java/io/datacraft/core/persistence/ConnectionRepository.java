/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.persistence;

import java.io.IOException;
import java.util.List;
import io.datacraft.core.connection.SavedConnection;

/** Atomic non-secret profile snapshots; implementations must reject corrupt/unknown formats. */
public interface ConnectionRepository {
    @FunctionalInterface interface Operation<T> { T run() throws IOException; }
    /** Persistent implementations serialize across instances; default is for single-owner memory repositories. */
    default <T> T withLock(Operation<T> operation) throws IOException { return operation.run(); }
    List<SavedConnection> load() throws IOException;
    void save(List<SavedConnection> profiles) throws IOException;
}
