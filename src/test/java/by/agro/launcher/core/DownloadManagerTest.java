package by.agro.launcher.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadManagerTest {
    @Test
    void publishesLifecycleProgressAndSpeed() throws Exception {
        DownloadManager manager = new DownloadManager();
        DownloadManager.Handle task = manager.queued("file.jar", "https://example.test/file.jar",
                Path.of("file.jar"), 100);
        assertEquals(DownloadManager.Status.QUEUED, task.status());
        manager.running(task, 1, 0);
        manager.advanced(task, 40);
        assertEquals(40, task.downloadedBytes());
        assertEquals(0.4, task.progress(), 0.001);
        manager.completed(task);
        assertEquals(DownloadManager.Status.COMPLETED, task.status());
    }

    @Test
    void concurrentProgressUpdatesAreNotLost() throws Exception {
        DownloadManager manager = new DownloadManager();
        DownloadManager.Handle task = manager.queued("asset", "https://example.test/asset", Path.of("asset"), 1000);
        manager.running(task, 1, 0);
        int workers = 8;
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(workers);
        try {
            for (int i = 0; i < workers; i++) pool.submit(() -> {
                start.await();
                for (int n = 0; n < 100; n++) manager.advanced(task, 1);
                return null;
            });
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(800, task.downloadedBytes());
    }

    @Test
    void cancelAndRetryResetControllableState() {
        DownloadManager manager = new DownloadManager();
        DownloadManager.Handle task = manager.queued("mod", "https://example.test/mod", Path.of("mod"), 10);
        manager.running(task, 1, 0);
        task.cancel();
        manager.failed(task, new DownloadManager.CancelledException());
        assertEquals(DownloadManager.Status.CANCELLED, task.status());
        assertTrue(task.canRetry());
        manager.retry(task);
        assertEquals(DownloadManager.Status.QUEUED, task.status());
    }
}
