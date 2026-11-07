package by.agro.launcher.core;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.Test;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PinnedHttpsSocketFactoryTest {
    private static final InetAddress PUBLIC_IP;
    static { try { PUBLIC_IP = InetAddress.getByName("93.184.216.34"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); } }

    private SSLContext context() throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var in = getClass().getResourceAsStream("/network/localhost-test.p12")) {
            keys.load(in, "test-only-password".toCharArray());
        }
        var km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        km.init(keys, "test-only-password".toCharArray());
        var tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tm.init(keys);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(km.getKeyManagers(), tm.getTrustManagers(), null);
        return context;
    }
    private HttpsServer server(SSLContext context, AtomicReference<String> host) throws IOException {
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(context));
        server.createContext("/", exchange -> {
            host.set(exchange.getRequestHeaders().getFirst("Host"));
            byte[] text = "pinned".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, text.length);
            try (var out = exchange.getResponseBody()) { out.write(text); }
        });
        server.start();
        return server;
    }
    private PinnedHttpsSocketFactory factory(SSLContext context, int port, String host,
                                             List<InetSocketAddress> endpoints) {
        return new PinnedHttpsSocketFactory(PUBLIC_IP, host, port, Proxy.NO_PROXY,
                context.getSocketFactory(), (socket, endpoint, timeout) -> {
                    endpoints.add(endpoint);

                    socket.rawConnect(new InetSocketAddress("127.0.0.1", port), timeout);
                });
    }
    @Test void urlConnectionIgnoresReboundLoopbackAndKeepsTlsNameAndHost() throws Exception {
        SSLContext context = context();
        AtomicReference<String> host = new AtomicReference<>();
        HttpsServer server = server(context, host);
        List<InetSocketAddress> endpoints = new ArrayList<>();
        int port = server.getAddress().getPort();
        var factory = factory(context, port, "localhost", endpoints);

        var connection = (HttpsURLConnection) new URL("https://localhost:" + port + "/")
                .openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(factory);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        try {
            assertEquals("pinned", new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            assertFalse(endpoints.isEmpty());
            assertTrue(endpoints.stream().allMatch(e -> PUBLIC_IP.equals(e.getAddress())));
            assertEquals("localhost:" + port, host.get());
        } finally { connection.disconnect(); server.stop(0); }
    }
    @Test void allConnectedOverloadsUsePinnedIpAndOriginalTlsIdentity() throws Exception {
        SSLContext context = context();
        HttpsServer server = server(context, new AtomicReference<>());
        int port = server.getAddress().getPort();
        List<InetSocketAddress> endpoints = new ArrayList<>();
        var factory = factory(context, port, "localhost", endpoints);
        InetAddress attacker = InetAddress.getLoopbackAddress();
        try {
            List<Socket> sockets = new ArrayList<>();
            try {
                sockets.add(factory.createSocket("evil.invalid", port));
                sockets.add(factory.createSocket(attacker, port));
                sockets.add(factory.createSocket("evil.invalid", port, attacker, 0));
                sockets.add(factory.createSocket(attacker, port, attacker, 0));
                for (Socket socket : sockets) {
                    socket.setSoTimeout(3000);
                    var tls = (SSLSocket) socket;
                    assertEquals("HTTPS", tls.getSSLParameters().getEndpointIdentificationAlgorithm());
                    assertEquals("localhost", ((SNIHostName) tls.getSSLParameters().getServerNames().get(0)).getAsciiName());

                }
            } finally { for (Socket socket : sockets) socket.close(); }
            assertEquals(4, endpoints.size());
            assertTrue(endpoints.stream().allMatch(e -> PUBLIC_IP.equals(e.getAddress()) && port == e.getPort()));
        } finally { server.stop(0); }
    }
    @Test void mismatchingCertificateIsNotAccepted() throws Exception {
        SSLContext context = context();
        HttpsServer server = server(context, new AtomicReference<>());
        int port = server.getAddress().getPort();
        var factory = factory(context, port, "127.0.0.1", new ArrayList<>());
        try (var tls = (SSLSocket) factory.createSocket("ignored", port)) {
            tls.setSoTimeout(3000);
            assertThrows(SSLHandshakeException.class, tls::startHandshake);
        } finally { server.stop(0); }
    }
    @Test void untrustedCertificateIsNotAccepted() throws Exception {
        SSLContext context = context();
        HttpsServer server = server(context, new AtomicReference<>());
        int port = server.getAddress().getPort();
        var factory = new PinnedHttpsSocketFactory(PUBLIC_IP, "localhost", port, Proxy.NO_PROXY,
                (SSLSocketFactory) SSLSocketFactory.getDefault(),
                (socket, endpoint, timeout) -> socket.rawConnect(new InetSocketAddress("127.0.0.1", port), timeout));
        try (var tls = (SSLSocket) factory.createSocket("ignored", port)) {
            tls.setSoTimeout(3000);
            assertThrows(SSLHandshakeException.class, tls::startHandshake);
        } finally { server.stop(0); }
    }
    @Test void refusesForeignLayeredSocketAndWrongPorts() throws Exception {
        var factory = new PinnedHttpsSocketFactory(PUBLIC_IP, "example.com", 443, Proxy.NO_PROXY);
        try (Socket socket = new Socket()) {
            assertThrows(IOException.class, () -> factory.createSocket(socket, "example.com", 443, true));
            assertThrows(IOException.class, () -> factory.createSocket("example.com", 8443));
            assertThrows(IOException.class, () -> factory.createSocket().connect(
                    new InetSocketAddress(PUBLIC_IP, 8443)));
        }
    }
    @Test void httpProxyConnectUsesNumericPinnedIp() throws Exception {
        checkHttpProxy("HTTP/1.1 200 Connection established\r\n\r\n", true);
    }
    @Test void refusesProxyAuthenticationAndOversizedHeaders() throws Exception {
        checkHttpProxy("HTTP/1.1 407 Proxy Authentication Required\r\n\r\n", false);
        checkHttpProxy("HTTP/1.1 200 OK\r\nX: " + "a".repeat(17000), false);
    }
    private void checkHttpProxy(String response, boolean accepted) throws Exception {
        try (ServerSocket proxyServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<String> request = pool.submit(() -> {
                try (Socket peer = proxyServer.accept()) {
                    peer.setSoTimeout(3000);
                    String header = readHeader(peer.getInputStream());
                    peer.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    return header;
                }
            });
            Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyServer.getLocalSocketAddress());
            var factory = new PinnedHttpsSocketFactory(PUBLIC_IP, "evil.invalid", 443, proxy);
            try (Socket raw = factory.createSocket()) {
                var target = InetSocketAddress.createUnresolved("evil.invalid", 443);
                if (accepted) raw.connect(target, 3000);
                else assertThrows(IOException.class, () -> raw.connect(target, 3000));
                String header = request.get(5, TimeUnit.SECONDS);
                assertTrue(header.startsWith("CONNECT 93.184.216.34:443 HTTP/1.1\r\n"));
                assertFalse(header.contains("evil.invalid"));
            } finally { pool.shutdownNow(); }
        }
    }
    @Test void socksProxyReceivesNumericIpNotHostname() throws Exception {
        try (ServerSocket proxyServer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ExecutorService pool = Executors.newSingleThreadExecutor();
            Future<byte[]> request = pool.submit(() -> {
                try (Socket peer = proxyServer.accept()) {
                    peer.setSoTimeout(3000);
                    var in = new DataInputStream(peer.getInputStream());
                    var out = peer.getOutputStream();
                    assertEquals(5, in.readUnsignedByte());
                    in.readNBytes(in.readUnsignedByte());
                    out.write(new byte[]{5, 0}); out.flush();
                    assertEquals(5, in.readUnsignedByte());
                    assertEquals(1, in.readUnsignedByte());
                    assertEquals(0, in.readUnsignedByte());
                    assertEquals(1, in.readUnsignedByte(), "SOCKS must receive IPv4, not a DNS name");
                    byte[] address = in.readNBytes(4);
                    assertEquals(443, in.readUnsignedShort());
                    out.write(new byte[]{5, 0, 0, 1, 0, 0, 0, 0, 0, 0}); out.flush();
                    return address;
                }
            });
            var factory = new PinnedHttpsSocketFactory(PUBLIC_IP, "evil.invalid", 443,
                    new Proxy(Proxy.Type.SOCKS, proxyServer.getLocalSocketAddress()));
            try (Socket raw = factory.createSocket()) {
                raw.connect(InetSocketAddress.createUnresolved("evil.invalid", 443), 3000);
                assertArrayEquals(PUBLIC_IP.getAddress(), request.get(5, TimeUnit.SECONDS));
            } finally { pool.shutdownNow(); }
        }
    }
    private static String readHeader(InputStream in) throws IOException {
        StringBuilder text = new StringBuilder();
        while (text.length() < 16384) {
            int b = in.read(); if (b < 0) throw new EOFException();
            text.append((char)b);
            if (text.toString().endsWith("\r\n\r\n")) return text.toString();
        }
        throw new IOException("Header too big");
    }
}
