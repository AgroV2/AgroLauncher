package by.agro.launcher.core;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;


final class PinnedHttpsSocketFactory extends SSLSocketFactory {
    private final InetAddress address;
    private final String host;
    private final int port;
    private final Proxy proxy;
    private final SSLSocketFactory delegate;
    private final Connector connector;

    @FunctionalInterface
    interface Connector {
        void connect(PinnedSocket socket, InetSocketAddress endpoint, int timeout) throws IOException;
    }

    PinnedHttpsSocketFactory(InetAddress address, String host, int port, Proxy proxy) {
        this(address, host, port, proxy, (SSLSocketFactory) SSLSocketFactory.getDefault(),
                PinnedSocket::rawConnect);
    }


    PinnedHttpsSocketFactory(InetAddress address, String host, int port, Proxy proxy,
                             SSLSocketFactory delegate, Connector connector) {
        this.address = address;
        this.host = host;
        this.port = port;
        this.proxy = proxy == null ? Proxy.NO_PROXY : proxy;
        this.delegate = delegate;
        this.connector = connector;
    }

    @Override public Socket createSocket() { return new PinnedSocket(); }

    final class PinnedSocket extends Socket {
        private boolean pinned;
        private PinnedHttpsSocketFactory owner() { return PinnedHttpsSocketFactory.this; }
        private PinnedSocket() {
            super(proxy.type() == Proxy.Type.SOCKS ? proxy : Proxy.NO_PROXY);
        }
        void rawConnect(InetSocketAddress endpoint, int timeout) throws IOException {
            super.connect(endpoint, timeout);
        }
        @Override public void connect(SocketAddress endpoint) throws IOException { connect(endpoint, 20_000); }
        @Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
            if (!(endpoint instanceof InetSocketAddress target) || target.getPort() != port)
                throw new IOException("Unexpected HTTPS destination port");
            if (pinned || isConnected()) throw new SocketException("Already connected");
            int boundedTimeout = timeout > 0 ? timeout : 20_000;
            try {
                if (proxy.type() == Proxy.Type.HTTP) {
                    if (!(proxy.address() instanceof InetSocketAddress proxyAddress))
                        throw new IOException("Invalid HTTP proxy address");
                    connector.connect(this, proxyAddress, boundedTimeout);
                    int previousTimeout = getSoTimeout();
                    setSoTimeout(boundedTimeout);
                    try { connectTunnel(this); } finally { setSoTimeout(previousTimeout); }
                } else {
                    connector.connect(this, new InetSocketAddress(address, port), boundedTimeout);
                }
                pinned = true;
            } catch (IOException | RuntimeException e) {
                try { close(); } catch (IOException ignored) { }
                throw e;
            }
        }
    }

    private void connectTunnel(Socket socket) throws IOException {
        String ip = address.getHostAddress();
        String authority = (ip.contains(":") ? "[" + ip + "]" : ip) + ":" + port;
        socket.getOutputStream().write(("CONNECT " + authority + " HTTP/1.1\r\nHost: " + authority
                + "\r\nProxy-Connection: Keep-Alive\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int matched = 0;
        byte[] terminator = {'\r', '\n', '\r', '\n'};
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(socket.getSoTimeout());
        while (header.size() < 16_384) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new SocketTimeoutException("HTTP proxy CONNECT timed out");
            socket.setSoTimeout((int) Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(remaining)));
            int b = socket.getInputStream().read();
            if (b < 0) throw new EOFException("HTTP proxy closed CONNECT response");
            header.write(b);
            matched = b == terminator[matched] ? matched + 1 : (b == '\r' ? 1 : 0);
            if (matched == 4) {
                String status = header.toString(StandardCharsets.US_ASCII).split("\r\n", 2)[0];
                if (!status.matches("HTTP/1\\.[01] 200(?: .*)?"))
                    throw new IOException("HTTP proxy refused CONNECT");
                return;
            }
        }
        throw new IOException("HTTP proxy CONNECT response exceeds size limit");
    }

    @Override public Socket createSocket(Socket socket, String ignoredHost, int requestedPort,
                                         boolean autoClose) throws IOException {
        if (requestedPort != port) throw new IOException("Unexpected TLS destination port");
        boolean owned = socket instanceof PinnedHttpsSocketFactory.PinnedSocket pinnedSocket
                && pinnedSocket.owner() == this && pinnedSocket.pinned;
        if (!owned && (!socket.isConnected() || !address.equals(socket.getInetAddress())
                || socket.getPort() != port))
            throw new IOException("TLS socket is not connected to the validated address");
        SSLSocket tls = (SSLSocket) delegate.createSocket(socket, host, port, autoClose);
        SSLParameters parameters = tls.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        String name = host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
        if (!name.contains(":") && !name.matches("[0-9.]+"))
            parameters.setServerNames(java.util.List.of(new SNIHostName(name)));
        tls.setSSLParameters(parameters);
        return tls;
    }

    private Socket connected(int requestedPort, InetAddress local, int localPort) throws IOException {
        if (requestedPort != port) throw new IOException("Unexpected HTTPS destination port");
        Socket raw = createSocket();
        try {
            if (local != null) raw.bind(new InetSocketAddress(local, localPort));
            raw.connect(new InetSocketAddress(address, port), 20_000);
            return createSocket(raw, host, port, true);
        } catch (IOException | RuntimeException e) {
            try { raw.close(); } catch (IOException ignored) { }
            throw e;
        }
    }
    @Override public Socket createSocket(String ignored, int port) throws IOException {
        return connected(port, null, 0);
    }
    @Override public Socket createSocket(InetAddress ignored, int port) throws IOException {
        return connected(port, null, 0);
    }
    @Override public Socket createSocket(String ignored, int port, InetAddress local, int localPort)
            throws IOException { return connected(port, local, localPort); }
    @Override public Socket createSocket(InetAddress ignored, int port, InetAddress local, int localPort)
            throws IOException { return connected(port, local, localPort); }
    @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
}
