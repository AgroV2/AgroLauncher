package by.agro.launcher.core;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


final class HostLimiter {
    private static final class Slot {
        final Semaphore semaphore;
        int references;
        Slot(int limit) { semaphore = new Semaphore(limit, true); }
    }
    private final int limit;
    private final Map<String, Slot> slots = new HashMap<>();
    HostLimiter(int limit) { this.limit = Math.max(1, Math.min(32, limit)); }

    Permit acquire(URI uri, DownloadManager.Handle handle) throws IOException {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Slot slot;
        synchronized (slots) {
            slot = slots.computeIfAbsent(host, ignored -> new Slot(limit));
            slot.references++;
        }
        boolean acquired = false;
        try {
            while (true) {
                if (handle != null) handle.checkpoint();
                try {
                    if (slot.semaphore.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                        acquired = true;
                        return new Permit(host, slot);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for download connection", e);
                }
            }
        } finally {
            if (!acquired) removeReference(host, slot);
        }
    }

    private void removeReference(String host, Slot slot) {
        synchronized (slots) {
            if (--slot.references == 0) slots.remove(host, slot);
        }
    }
    final class Permit implements AutoCloseable {
        private final String host;
        private final Slot slot;
        private final AtomicBoolean closed = new AtomicBoolean();
        Permit(String host, Slot slot) { this.host = host; this.slot = slot; }
        public void close() {
            if (closed.compareAndSet(false, true)) {
                slot.semaphore.release();
                removeReference(host, slot);
            }
        }
    }
    int hostCount() { synchronized (slots) { return slots.size(); } }
}
