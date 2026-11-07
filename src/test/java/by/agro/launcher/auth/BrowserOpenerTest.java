package by.agro.launcher.auth;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BrowserOpenerTest {
    private static final URI URI_OK = URI.create("https://login.microsoftonline.com/authorize?state=test");
    @Test void desktopSuccessSkipsCommand() throws Exception {
        BrowserOpener.open(URI_OK, u -> true, u -> { fail("fallback must not run"); return true; }, true);
    }
    @Test void linuxUsesXdgIfDesktopUnavailableOrFails() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        BrowserOpener.open(URI_OK, u -> false, u -> { calls.incrementAndGet(); assertEquals(URI_OK,u); return true; }, true);
        BrowserOpener.open(URI_OK, u -> { throw new IOException(); }, u -> { calls.incrementAndGet(); return true; }, true);
        assertEquals(2, calls.get());
    }
    @Test void failuresAndUnsafeUrlsAreRejected() {
        assertThrows(IOException.class, () -> BrowserOpener.open(URI_OK, u -> false, u -> false, true));
        assertThrows(IOException.class, () -> BrowserOpener.open(URI_OK, u -> false, u -> { fail(); return true; }, false));
        assertThrows(IOException.class, () -> BrowserOpener.open(URI.create("http://example.com"), u -> true, u -> true, true));
    }
}
