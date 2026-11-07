package by.agro.launcher.core;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.*;
import static org.junit.jupiter.api.Assertions.*;

class SafeNetworkTest {
    @Test void rejectsPrivateReservedAndTransitionAddresses() throws Exception {
        for (String ip : new String[]{"0.0.0.0", "10.0.0.1", "127.0.0.1", "169.254.169.254",
                "172.16.0.1", "192.168.1.1", "100.64.0.1", "198.18.0.1", "192.0.2.1",
                "198.51.100.1", "203.0.113.1", "192.88.99.1", "224.0.0.1", "240.0.0.1",
                "::", "::1", "::ffff:127.0.0.1", "fc00::1", "fe80::1", "ff02::1",
                "64:ff9b::7f00:1", "2002:7f00:1::", "2001::1", "2001:db8::1", "3fff::1"})
            assertFalse(SafeNetwork.isPublic(InetAddress.getByName(ip)), ip);
    }
    @Test void rejectsMixedPublicAndPrivateDnsAnswers() throws Exception {
        URI uri = URI.create("https://example.test");
        InetAddress publicIp = InetAddress.getByName("1.1.1.1");
        InetAddress privateIp = InetAddress.getByName("127.0.0.1");
        assertThrows(IOException.class, () -> SafeNetwork.validateAddresses(uri,
                new InetAddress[]{publicIp, privateIp}));
        assertThrows(IOException.class, () -> SafeNetwork.validateAddresses(uri, new InetAddress[0]));
        assertDoesNotThrow(() -> SafeNetwork.validateAddresses(uri, new InetAddress[]{publicIp}));
    }
    @Test void acceptsOrdinaryPublicAddresses() throws Exception {
        for (String ip : new String[]{"1.1.1.1", "8.8.8.8", "93.184.216.34", "2606:4700:4700::1111"})
            assertTrue(SafeNetwork.isPublic(InetAddress.getByName(ip)), ip);
    }
    @Test void rejectsUnsafeUrlsBeforeConnecting() {
        for (String url : new String[]{"http://example.com", "file:///etc/passwd", "https://a@localhost/",
                "https://127.0.0.1/", "https://[::1]/", "https://example.com:0/",
                "https://example.com:65536/", "https://[fe80::1%25eth0]/"})
            assertThrows(IOException.class, () -> SafeNetwork.openConnection(URI.create(url), Proxy.NO_PROXY), url);
    }
    @Test void rejectsRedirectToPrivateDestination() {
        assertThrows(IOException.class, () -> SafeNetwork.resolvePublicHttps(
                URI.create("https://example.com/a"), "https://127.0.0.1/admin"));
        assertThrows(IOException.class, () -> SafeNetwork.resolvePublicHttps(
                URI.create("https://example.com/a"), "http://example.com/insecure"));
    }
}
