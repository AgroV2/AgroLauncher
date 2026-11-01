package by.agro.launcher.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SafeNetwork {

    private SafeNetwork() {
    }

    public static URI requirePublicHttps(String value) throws IOException {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL: " + value, e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null) {
            throw new IOException("Only absolute HTTPS URLs without user info are allowed: " + value);
        }
        validateResolvedAddresses(uri);
        return uri;
    }

    public static URI resolvePublicHttps(URI base, String location) throws IOException {
        try {
            return requirePublicHttps(base.resolve(location).toString());
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid redirect URL from " + base, e);
        }
    }

    public static void validateResolvedAddresses(URI uri) throws IOException {
        resolveAndValidate(uri);
    }

    public static InetAddress[] resolveAndValidate(URI uri) throws IOException {
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new IOException("Unable to resolve host: " + uri.getHost(), e);
        }
        if (addresses.length == 0) {
            throw new IOException("Host resolved to no addresses: " + uri.getHost());
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IOException("Refusing non-public address for " + uri.getHost()
                        + ": " + address.getHostAddress());
            }
        }
        return addresses;
    }

    public static void pinConnection(javax.net.ssl.HttpsURLConnection connection, URI uri)
            throws IOException {
        InetAddress[] validated = resolveAndValidate(uri);
        if (validated.length == 0) {
            return;
        }
        connection.setSSLSocketFactory(new PinnedAddressSSLSocketFactory(validated[0], uri.getHost()));
        String hostHeader = uri.getHost();
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            hostHeader = hostHeader + ":" + uri.getPort();
        }
        connection.setRequestProperty("Host", hostHeader);
    }

    public static final class PinnedAddressSSLSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private final javax.net.ssl.SSLSocketFactory delegate =
                (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        private final InetAddress address;
        private final String host;

        public PinnedAddressSSLSocketFactory(InetAddress address, String host) {
            this.address = address;
            this.host = host;
        }

        private javax.net.ssl.SSLSocket pinned(int port) throws IOException {
            javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) delegate.createSocket(address, port);
            try {
                javax.net.ssl.SSLParameters parameters = socket.getSSLParameters();
                parameters.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(host)));
                socket.setSSLParameters(parameters);
            } catch (RuntimeException ignored) {

            }
            return socket;
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return pinned(port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return pinned(port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port) throws IOException {
            return pinned(port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return pinned(port);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
                throws IOException {
            return delegate.createSocket(socket, host, port, autoClose);
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }
    }

    public static final class HostPinningSslSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private final javax.net.ssl.SSLSocketFactory delegate =
                (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            InetAddress[] validated = resolveAndValidate(URI.create("https://" + host));
            if (validated.length == 0) {
                return delegate.createSocket(host, port);
            }
            return new PinnedAddressSSLSocketFactory(validated[0], host).createSocket(host, port);
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return delegate.createSocket(host, port, localAddress, localPort);
        }

        @Override
        public Socket createSocket(InetAddress address, int port) throws IOException {
            return delegate.createSocket(address, port);
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
                throws IOException {
            return delegate.createSocket(address, port, localAddress, localPort);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
                throws IOException {
            return delegate.createSocket(socket, host, port, autoClose);
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }
    }

    private static final List<String> DEFAULT_DOWNLOAD_HOSTS = List.of(
            "libraries.minecraft.net",
            "resources.download.minecraft.net",
            "piston-meta.mojang.com",
            "piston-data.mojang.com",
            "maven.fabricmc.net",
            "maven.quiltmc.org",
            "maven.minecraftforge.net",
            "maven.neoforged.net",
            "cdn.modrinth.com",
            "media.forgecdn.net",
            "edge.forgecdn.net",
            "github.com");

    private static volatile Set<String> allowedDownloadHosts = Set.copyOf(DEFAULT_DOWNLOAD_HOSTS);

    public static void configureAllowedDownloadHosts(String setting) {
        if (setting == null || setting.isBlank()) {
            allowedDownloadHosts = Set.copyOf(DEFAULT_DOWNLOAD_HOSTS);
            return;
        }
        String value = setting.trim();
        if ("*".equals(value)) {
            allowedDownloadHosts = null;
            return;
        }
        Set<String> parsed = new HashSet<>();
        for (String host : value.split(",")) {
            String trimmed = host.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        allowedDownloadHosts = parsed.isEmpty()
                ? Set.copyOf(DEFAULT_DOWNLOAD_HOSTS) : Set.copyOf(parsed);
    }

    public static void ensureAllowedDownloadHost(String url) throws IOException {
        Set<String> allowed = allowedDownloadHosts;
        if (allowed == null) {
            return;
        }
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid URL: " + url, e);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowed.contains(host)) {
            throw new IOException("Download host is not in the allow list: "
                    + (host.isBlank() ? url : host)
                    + " (downloadHostAllowList in settings.json)");
        }
    }

    private static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first != 0
                    && first != 10
                    && first != 127
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 169 && second == 254)
                    && !(first == 172 && second >= 16 && second <= 31)
                    && !(first == 192 && second == 0)
                    && !(first == 192 && second == 168)
                    && !(first == 198 && (second == 18 || second == 19))
                    && first < 224;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        int fourth = bytes[3] & 0xff;
        boolean teredoOrDocumentation = first == 0x20 && second == 0x01
                && ((third == 0x00 && fourth == 0x00) || (third == 0x0d && fourth == 0xb8));
        return !(first == 0 || first == 0xff
                || (first == 0xfe && (second & 0xc0) == 0x80)
                || (first & 0xfe) == 0xfc
                || teredoOrDocumentation);
    }
}
