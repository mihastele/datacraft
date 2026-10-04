/* SPDX-License-Identifier: Apache-2.0 */
package io.datacraft.core.query;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One-shot cancellation signal with at most one active adapter callback. */
public final class QueryCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicReference<Runnable> action = new AtomicReference<>();

    public boolean isCancelled() { return cancelled.get(); }

    public void cancel() {
        if (cancelled.compareAndSet(false, true)) invoke();
    }

    /** Adapter callback must be safe to invoke from a different thread. */
    public Registration onCancel(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        if (!action.compareAndSet(null, callback)) {
            throw new IllegalStateException("Cancellation already has an active callback.");
        }
        if (cancelled.get()) invoke();
        return () -> action.compareAndSet(callback, null);
    }

    private void invoke() {
        Runnable callback = action.getAndSet(null);
        if (callback != null) callback.run();
    }

    @FunctionalInterface public interface Registration extends AutoCloseable {
        @Override void close();
    }
}
