package by.agro.launcher.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe shared state for every file download performed by the launcher. */
public final class DownloadManager {
    public enum Status { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

    public interface Listener { void downloadsChanged(); }

    public static final class CancelledException extends java.io.IOException {
        public CancelledException() { super("Download cancelled"); }
    }

    public static final class Handle {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final String url;
        private final Path target;
        private final long totalBytes;
        private final AtomicLong downloadedBytes = new AtomicLong();
        private final AtomicBoolean paused = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Status status = Status.QUEUED;
        private volatile String error;
        private volatile int attempt;
        private volatile long startedNanos;
        private volatile long updatedNanos;
        private final Instant createdAt = Instant.now();

        private Handle(String name, String url, Path target, long totalBytes) {
            this.name = name;
            this.url = url;
            this.target = target;
            this.totalBytes = Math.max(0, totalBytes);
        }

        public UUID id() { return id; }
        public String name() { return name; }
        public String url() { return url; }
        public Path target() { return target; }
        public long totalBytes() { return totalBytes; }
        public long downloadedBytes() { return downloadedBytes.get(); }
        public Status status() { return status; }
        public String error() { return error; }
        public int attempt() { return attempt; }
        public Instant createdAt() { return createdAt; }
        public double progress() { return totalBytes <= 0 ? -1 : Math.min(1d, downloadedBytes() / (double) totalBytes); }
        public long bytesPerSecond() {
            long elapsed = updatedNanos - startedNanos;
            return elapsed <= 0 ? 0 : Math.max(0, (long) (downloadedBytes() * 1_000_000_000d / elapsed));
        }
        public void pause() {
            if (status == Status.RUNNING || status == Status.QUEUED) {
                paused.set(true);
                status = Status.PAUSED;
            }
        }
        public void resume() {
            paused.set(false);
            if (status == Status.PAUSED) status = Status.RUNNING;
            synchronized (this) { notifyAll(); }
        }
        public void cancel() {
            cancelled.set(true);
            paused.set(false);
            status = Status.CANCELLED;
            synchronized (this) { notifyAll(); }
        }
        public boolean canRetry() { return status == Status.FAILED || status == Status.CANCELLED; }

        private void checkpoint() throws CancelledException {
            if (cancelled.get()) throw new CancelledException();
            while (paused.get()) {
                status = Status.PAUSED;
                synchronized (this) {
                    try { wait(200L); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new CancelledException(); }
                }
                if (cancelled.get()) throw new CancelledException();
            }
            if (status == Status.PAUSED) status = Status.RUNNING;
        }
    }

    private final ConcurrentHashMap<UUID, Handle> tasks = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public Handle queued(String name, String url, Path target, long totalBytes) {
        Handle handle = new Handle(name, url, target, totalBytes);
        tasks.put(handle.id(), handle);
        changed();
        return handle;
    }

    public void running(Handle handle, int attempt, long existingBytes) {
        handle.attempt = attempt;
        handle.downloadedBytes.set(Math.max(0, existingBytes));
        handle.startedNanos = System.nanoTime();
        handle.updatedNanos = handle.startedNanos;
        handle.error = null;
        handle.status = Status.RUNNING;
        changed();
    }

    public void advanced(Handle handle, long bytes) throws CancelledException {
        handle.checkpoint();
        handle.downloadedBytes.addAndGet(bytes);
        handle.updatedNanos = System.nanoTime();
        changed();
    }

    public void pause(Handle handle) { handle.pause(); changed(); }
    public void resume(Handle handle) { handle.resume(); changed(); }
    public void cancel(Handle handle) { handle.cancel(); changed(); }

    public void completed(Handle handle) { handle.status = Status.COMPLETED; changed(); }
    public void failed(Handle handle, Throwable error) {
        handle.error = error == null ? null : error.getMessage();
        handle.status = error instanceof CancelledException ? Status.CANCELLED : Status.FAILED;
        changed();
    }

    public void retry(Handle handle) {
        if (!handle.canRetry()) return;
        handle.cancelled.set(false);
        handle.paused.set(false);
        handle.error = null;
        handle.status = Status.QUEUED;
        changed();
    }

    public List<Handle> snapshot() {
        List<Handle> result = new ArrayList<>(tasks.values());
        result.sort(Comparator.comparing(Handle::createdAt).reversed());
        return List.copyOf(result);
    }

    public void addListener(Listener listener) { listeners.add(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    private void changed() { for (Listener listener : listeners) listener.downloadsChanged(); }
}
