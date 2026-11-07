package by.agro.launcher.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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
        requireHttpsSyntax(uri);
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
        validateAddresses(uri, addresses);
        return addresses;
    }

    static void validateAddresses(URI uri, InetAddress[] addresses) throws IOException {
        if (addresses.length == 0) {
            throw new IOException("Host resolved to no addresses: " + uri.getHost());
        }
        for (InetAddress address : addresses) {
            if (!isPublic(address)) {
                throw new IOException("Refusing non-public address for " + uri.getHost()
                        + ": " + address.getHostAddress());
            }
        }
    }


    public static javax.net.ssl.HttpsURLConnection openConnection(URI uri, java.net.Proxy proxy)
            throws IOException {
        requireHttpsSyntax(uri);
        InetAddress[] addresses = resolveAndValidate(uri);
        return openPinned(uri, proxy, addresses[0]);
    }

    private static javax.net.ssl.HttpsURLConnection openPinned(URI uri, java.net.Proxy proxy,
                                                       InetAddress address) throws IOException {

        var connection = (javax.net.ssl.HttpsURLConnection) uri.toURL()
                .openConnection(java.net.Proxy.NO_PROXY);
        connection.setSSLSocketFactory(new PinnedHttpsSocketFactory(address, uri.getHost(),
                uri.getPort() == -1 ? 443 : uri.getPort(), proxy));
        connection.setInstanceFollowRedirects(false);
        return connection;
    }

    private static void requireHttpsSyntax(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getHost().contains("%")
                || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getPort() < -1)
            throw new IOException("Only absolute public HTTPS URLs without user info are allowed");
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

    static boolean isPublic(InetAddress address) {
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
                    && !(first == 192 && second == 88 && (bytes[2] & 0xff) == 99)
                    && !(first == 198 && second == 51 && (bytes[2] & 0xff) == 100)
                    && !(first == 203 && second == 0 && (bytes[2] & 0xff) == 113)
                    && !(first == 198 && (second == 18 || second == 19))
                    && first < 224;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        int third = bytes[2] & 0xff;
        int fourth = bytes[3] & 0xff;

        return (first & 0xe0) == 0x20
                && !(first == 0x20 && second == 0x01 && third < 2)
                && !(first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8)
                && !(first == 0x20 && second == 0x02)
                && !(first == 0x3f && second == 0xfe)
                && !(first == 0x3f && second == 0xff && (third & 0xf0) == 0);
    }
}
