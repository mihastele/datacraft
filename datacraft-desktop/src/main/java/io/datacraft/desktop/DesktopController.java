/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.desktop;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import io.datacraft.core.application.WorkspaceService;
import io.datacraft.core.connection.ConnectionProfile;
import io.datacraft.core.connection.SavedConnection;
import io.datacraft.core.metadata.*;
import io.datacraft.core.query.*;

/** Schedules application services without depending on JavaFX or JDBC. */
public final class DesktopController {
    private final WorkspaceService workspace;
    private final io.datacraft.core.application.ConnectionProfiles profiles;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "datacraft-workspace"));
    private final ExecutorService cancelWorker = Executors.newSingleThreadExecutor(r -> new Thread(r, "datacraft-cancel"));
    private final AtomicReference<QueryCancellation> active = new AtomicReference<>();
    private CompletableFuture<Void> shutdown;

    public DesktopController(WorkspaceService workspace) { this(workspace, null); }
    public DesktopController(WorkspaceService workspace, io.datacraft.core.application.ConnectionProfiles profiles) {
        this.workspace = workspace; this.profiles = profiles;
    }
    public boolean profilesAvailable() { return profiles != null; }
    public boolean canStorePassword() { return profiles != null && profiles.canStorePassword(); }
    public CompletableFuture<List<SavedConnection>> profiles() {
        return submit(() -> profiles == null ? List.of() : profiles.list());
    }
    public CompletableFuture<List<SavedConnection>> saveProfile(SavedConnection profile, char[] password) {
        try {
            return submit(() -> {
                try { requireProfiles().save(profile, password); return profiles.list(); }
                finally { Arrays.fill(password, '\0'); }
            });
        } catch (RejectedExecutionException failure) { Arrays.fill(password, '\0'); throw failure; }
    }
    public CompletableFuture<List<SavedConnection>> deleteProfile(java.util.UUID id) {
        return submit(() -> { requireProfiles().delete(id); return profiles.list(); });
    }
    private io.datacraft.core.application.ConnectionProfiles requireProfiles() {
        if (profiles == null) throw new IllegalStateException("Profiles are unavailable.");
        return profiles;
    }
    public CompletableFuture<List<String>> connectProfile(SavedConnection profile, char[] password) {
        try {
            return submit(() -> {
                char[] stored = null;
                try {
                    var service = requireProfiles();
                    var existing = service.list().stream().filter(p -> p.id().equals(profile.id())).findFirst();
                    if (existing.isEmpty() || !existing.get().equals(profile)
                            || (profile.storePassword() && service.canStorePassword() && password.length > 0)) {
                        service.save(profile, password.clone());
                    }
                    if (password.length == 0 && profile.storePassword() && service.canStorePassword())
                        stored = service.password(profile.id(), profile.settings());
                    return workspace.connect(profile.settings(), stored == null ? password : stored);
                } finally {
                    Arrays.fill(password, '\0');
                    if (stored != null) Arrays.fill(stored, '\0');
                }
            });
        } catch (RejectedExecutionException failure) { Arrays.fill(password, '\0'); throw failure; }
    }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }
    private <T> CompletableFuture<T> submit(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try { return work.run(); } catch (Exception failure) { throw new CompletionException(failure); }
        }, worker);
    }
    public CompletableFuture<List<String>> connect(ConnectionProfile settings, char[] password) {
        try { return submit(() -> workspace.connect(settings, password)); }
        catch (RejectedExecutionException failure) { Arrays.fill(password, '\0'); throw failure; }
    }
    public java.util.Set<io.datacraft.core.connection.DatabaseKind> availableDatabases() { return workspace.availableDatabases(); }
    public CompletableFuture<List<String>> schemas() { return submit(workspace::schemas); }
    public CompletableFuture<List<RelationMetadata>> relations(String schema) { return submit(() -> workspace.relations(schema)); }
    public CompletableFuture<List<ColumnMetadata>> columns(QualifiedName relation) { return submit(() -> workspace.columns(relation)); }
    public CompletableFuture<QueryResult> query(QueryRequest request) {
        var signal = new QueryCancellation();
        if (!active.compareAndSet(null, signal)) throw new IllegalStateException("A query is already active.");
        try {
            return submit(() -> {
                try { return workspace.query(request, signal); }
                finally { active.compareAndSet(signal, null); }
            });
        } catch (RejectedExecutionException failure) { active.compareAndSet(signal, null); throw failure; }
    }
    public void cancel() {
        var signal = active.get();
        if (signal != null) cancelWorker.execute(signal::cancel);
    }
    public CompletableFuture<Void> disconnect() { return submit(() -> { workspace.close(); return null; }); }
    public synchronized CompletableFuture<Void> shutdown() {
        if (shutdown != null) return shutdown;
        cancel();
        shutdown = disconnect().whenComplete((ignored, failure) -> {
            worker.shutdown();
            cancelWorker.shutdown();
        });
        return shutdown;
    }
}
