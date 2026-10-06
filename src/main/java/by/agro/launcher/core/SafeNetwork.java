package by.agro.launcher.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

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
        return !(first == 0 || first == 0xff
                || (first == 0xfe && (second & 0xc0) == 0x80)
                || (first & 0xfe) == 0xfc);
    }
}
