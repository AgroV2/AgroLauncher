package by.agro.launcher.core;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HostLimiterTest {
    @Test void limitsSameHostAndAllowsOtherHosts() throws Exception {
        HostLimiter limiter = new HostLimiter(1);
        URI first=URI.create("https://EXAMPLE.com/a"), same=URI.create("https://example.com/b"), other=URI.create("https://other.com/");
        var pool=Executors.newFixedThreadPool(2);
        try (var held=limiter.acquire(first,null)) {
            var blocked=pool.submit(() -> { try(var p=limiter.acquire(same,null)) {return true;} });
            assertThrows(TimeoutException.class, () -> blocked.get(200,TimeUnit.MILLISECONDS));
            var independent=pool.submit(() -> {try(var p=limiter.acquire(other,null)) {return true;}});
            assertTrue(independent.get(2,TimeUnit.SECONDS));
            held.close(); assertTrue(blocked.get(2,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertEquals(0,limiter.hostCount());
    }
    @Test void waitingDownloadCanBeCancelledAndReleasesHostEntry() throws Exception {
        HostLimiter limiter=new HostLimiter(1); URI uri=URI.create("https://example.com/");
        DownloadManager mgr=new DownloadManager(); var handle=mgr.queued("x",uri.toString(),Path.of("x"),10);
        var pool=Executors.newSingleThreadExecutor();
        try(var held=limiter.acquire(uri,null)) {
            var started=new CountDownLatch(1);
            var waiting=pool.submit(() -> {started.countDown(); return limiter.acquire(uri,handle);});
            assertTrue(started.await(2,TimeUnit.SECONDS)); handle.cancel();
            var failure=assertThrows(ExecutionException.class,()->waiting.get(3,TimeUnit.SECONDS));
            assertInstanceOf(DownloadManager.CancelledException.class,failure.getCause());
        } finally {pool.shutdownNow();}
        assertEquals(0,limiter.hostCount());
    }
    @Test void permitCloseIsIdempotent() throws Exception {
        HostLimiter limiter=new HostLimiter(1); var permit=limiter.acquire(URI.create("https://example.com"),null);
        permit.close(); permit.close(); assertEquals(0,limiter.hostCount());
    }
}
