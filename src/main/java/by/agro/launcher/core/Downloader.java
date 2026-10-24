package by.agro.launcher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class Downloader {

    private static final String USER_AGENT = LauncherVersion.USER_AGENT;
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_REDIRECTS = 6;
    private static final long DEFAULT_MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024;
    private static final long MAX_TEXT_BYTES = 16L * 1024 * 1024;

    private static volatile Proxy proxy = Proxy.NO_PROXY;

    private final int threads;
    private final DownloadManager downloadManager;
    private final Map<String, Map<String, CookieRecord>> cookiesByHost = new ConcurrentHashMap<>();
    private final ExecutorService pool;

    private static final class CookieRecord {
        final String value;
        final long expiresAtMs;

        CookieRecord(String value, long maxAgeSeconds) {
            this.value = value;
            this.expiresAtMs = maxAgeSeconds >= 0
                    ? System.currentTimeMillis() + maxAgeSeconds * 1000L
                    : Long.MAX_VALUE;
        }

        boolean expired() {
            return System.currentTimeMillis() >= expiresAtMs;
        }
    }

    public static void configureProxy(String mode, String host, int port) {
        if (mode == null || "NONE".equalsIgnoreCase(mode)
                || host == null || host.isBlank() || port <= 0 || port > 65535) {
            proxy = Proxy.NO_PROXY;
            return;
        }
        Proxy.Type type = "SOCKS".equalsIgnoreCase(mode) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
        proxy = new Proxy(type, new java.net.InetSocketAddress(host, port));
    }

    public static Proxy configuredProxy() {
        return proxy;
    }

    private static final java.util.Map<Path, String> VERIFIED_FILES =
            new java.util.concurrent.ConcurrentHashMap<>();

    public boolean verifyOnce(Path target, String expectedSha1) {
        if (target == null) {
            return false;
        }
        if (expectedSha1 == null || expectedSha1.isBlank()) {
            return HashUtil.verify(target, expectedSha1);
        }
        if (expectedSha1.equalsIgnoreCase(VERIFIED_FILES.get(target))) {
            return true;
        }
        boolean verified = HashUtil.verify(target, expectedSha1);
        if (verified) {
            VERIFIED_FILES.put(target, expectedSha1);
        }
        return verified;
    }

    public Downloader() {
        this(Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2)), new DownloadManager());
    }

    public Downloader(int threads) {
        this(threads, new DownloadManager());
    }

    public Downloader(int threads, DownloadManager downloadManager) {
        this.threads = Math.max(1, Math.min(32, threads));
        this.downloadManager = java.util.Objects.requireNonNull(downloadManager, "downloadManager");
        this.pool = Executors.newFixedThreadPool(this.threads, r -> {
            Thread t = new Thread(r, "agro-downloader");
            t.setDaemon(true);
            return t;
        });
    }

    public DownloadManager downloadManager() {
        return downloadManager;
    }

    public static final class HashMismatchException extends IOException {
        public HashMismatchException(String message) { super(message); }
    }

    public static final class Task {
        public final String url;
        public final Path target;
        public final String sha1;
        public final long size;

        public Task(String url, Path target, String sha1, long size) {
            this.url = url;
            this.target = target;
            this.sha1 = sha1;
            this.size = size;
        }

        public Task(String url, Path target) {
            this(url, target, null, 0);
        }
    }

    public void downloadAll(List<Task> tasks, String stageName, ProgressListener listener) throws IOException {
        List<Task> pending = filterUnverified(tasks);
        if (pending.isEmpty()) {
            listener.onProgress(stageName, 1, 1, by.agro.launcher.i18n.Strings.get("progress.allDownloaded"));
            return;
        }

        AtomicInteger done = new AtomicInteger();
        AtomicReference<Exception> firstError = new AtomicReference<>();
        int total = pending.size();
        List<Future<?>> futures = new ArrayList<>(total);
        for (Task task : pending) {
            futures.add(pool.submit(() -> {
                try {
                    if (firstError.get() == null) {
                        download(task.url, task.target, task.sha1, task.size);
                        int n = done.incrementAndGet();
                        listener.onProgress(stageName, n, total, task.target.getFileName().toString());
                    }
                } catch (Exception e) {
                    firstError.compareAndSet(null, e);
                }
            }));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                firstError.compareAndSet(null, e);
            }
        }
        if (firstError.get() != null) {
            throw new IOException("Failed to download files: " + firstError.get().getMessage(), firstError.get());
        }
    }

    public void download(String url, Path target, String expectedSha1) throws IOException {
        download(url, target, expectedSha1, 0);
    }

    public void download(String url, Path target, String expectedSha1, long expectedSize) throws IOException {
        if (verifyOnce(target, expectedSha1)) {
            return;
        }
        DownloadManager.Handle handle = downloadManager.queued(target.getFileName().toString(), url, target, expectedSize);
        download(handle, expectedSha1, expectedSize);
    }

    public void retry(DownloadManager.Handle handle, String expectedSha1) throws IOException {
        downloadManager.retry(handle);
        download(handle, expectedSha1, handle.totalBytes());
    }

    private void download(DownloadManager.Handle handle, String expectedSha1, long expectedSize) throws IOException {
        long limit = expectedSize > 0
                ? Math.min(DEFAULT_MAX_DOWNLOAD_BYTES, expectedSize + Math.max(1024, expectedSize / 100))
                : DEFAULT_MAX_DOWNLOAD_BYTES;
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Path temp = handle.target().resolveSibling(handle.target().getFileName() + ".partial");
            long existing = Files.exists(temp) ? Files.size(temp) : 0;
            downloadManager.running(handle, attempt, existing);
            try {
                transferResumable(handle.url(), temp, Map.of(), limit, handle);
                if (expectedSha1 != null && !expectedSha1.isBlank()) {
                    String actual = HashUtil.sha1(temp);
                    if (!actual.equalsIgnoreCase(expectedSha1)) {
                        throw new HashMismatchException("SHA-1 mismatch for " + handle.url() + " (expected "
                                + expectedSha1 + ", got " + actual + ")");
                    }
                }
                SecureFiles.atomicReplace(temp, handle.target().toAbsolutePath().normalize());
                if (expectedSha1 != null && !expectedSha1.isBlank()) {
                    VERIFIED_FILES.put(handle.target().toAbsolutePath().normalize(), expectedSha1);
                }
                downloadManager.completed(handle);
                return;
            } catch (IOException e) {
                last = e;
                if (e instanceof DownloadManager.CancelledException) break;
                if (e instanceof HashMismatchException || isRangeNotSatisfiable(e)) {
                    try {
                        Files.deleteIfExists(temp);
                    } catch (IOException cleanupFailure) {
                        last.addSuppressed(cleanupFailure);
                    }
                }
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(500L * attempt); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        last = new IOException("Download interrupted", interrupted);
                        break;
                    }
                }
            }
        }
        downloadManager.failed(handle, last);
        if (last instanceof DownloadManager.CancelledException) throw last;
        throw new IOException("Failed to download " + handle.url() + " after " + MAX_RETRIES + " attempts", last);
    }

    private List<Task> filterUnverified(List<Task> tasks) {
        List<Task> pending = new ArrayList<>();
        if (tasks.size() >= 16) {
            List<java.util.concurrent.Future<Boolean>> checks = new ArrayList<>(tasks.size());
            for (Task task : tasks) {
                checks.add(pool.submit(() -> verifyOnce(task.target, task.sha1)));
            }
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    if (!checks.get(i).get()) {
                        pending.add(tasks.get(i));
                    }
                } catch (Exception e) {
                    pending.add(tasks.get(i));
                }
            }
            return pending;
        }
        for (Task task : tasks) {
            if (!verifyOnce(task.target, task.sha1)) {
                pending.add(task);
            }
        }
        return pending;
    }

    private static boolean isRangeNotSatisfiable(IOException error) {
        String message = error.getMessage();
        return message != null && message.startsWith("HTTP 416");
    }

    public void downloadVerified(String url, Path target, String algorithm, String expectedDigest,
                                 long maxBytes) throws IOException {
        if (expectedDigest == null || expectedDigest.isBlank()) {
            throw new IOException("A cryptographic digest is required for executable artifact: " + url);
        }
        if (Files.exists(target)) {
            try {
                if (expectedDigest.equalsIgnoreCase(HashUtil.digest(target, algorithm))) return;
            } catch (IOException ignored) { }
        }
        DownloadManager.Handle handle = downloadManager.queued(target.getFileName().toString(), url, target, maxBytes);
        Path temp = SecureFiles.createSiblingTemp(target, ".part");
        downloadManager.running(handle, 1, 0);
        try {
            transfer(url, temp, Map.of(), maxBytes > 0 ? maxBytes : DEFAULT_MAX_DOWNLOAD_BYTES, handle);
            String actual = HashUtil.digest(temp, algorithm);
            if (!actual.equalsIgnoreCase(expectedDigest)) throw new IOException(algorithm + " mismatch for " + url);
            SecureFiles.atomicReplace(temp, target.toAbsolutePath().normalize());
            downloadManager.completed(handle);
        } catch (IOException e) {
            downloadManager.failed(handle, e);
            throw e;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public void download(String url, Path target) throws IOException {
        download(url, target, null, 0);
    }

    public String getString(String url) throws IOException {
        return getString(url, Map.of());
    }

    public String getString(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = openWithRedirects(url, headers);
        try (InputStream in = connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copyLimited(in, out, MAX_TEXT_BYTES);
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    public void downloadWithHeaders(String url, Path target, Map<String, String> headers) throws IOException {
        Path temp = SecureFiles.createSiblingTemp(target, ".part");
        try {
            transfer(url, temp, headers, DEFAULT_MAX_DOWNLOAD_BYTES);
            SecureFiles.atomicReplace(temp, target.toAbsolutePath().normalize());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void transferResumable(String url, Path target, Map<String, String> headers, long maxBytes,
                                   DownloadManager.Handle handle) throws IOException {
        Files.createDirectories(target.toAbsolutePath().normalize().getParent());
        long offset = Files.exists(target) ? Files.size(target) : 0;
        Map<String, String> requestHeaders = new java.util.LinkedHashMap<>(headers);
        if (offset > 0) requestHeaders.put("Range", "bytes=" + offset + "-");
        HttpURLConnection connection = openWithRedirects(url, requestHeaders);
        int code = connection.getResponseCode();
        boolean append = offset > 0 && code == HttpURLConnection.HTTP_PARTIAL;
        if (!append) offset = 0;
        long contentLength = connection.getContentLengthLong();
        if (contentLength > 0 && offset + contentLength > maxBytes) {
            connection.disconnect(); throw new IOException("Download exceeds size limit");
        }
        try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target,
                append ? new java.nio.file.StandardOpenOption[]{java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND}
                        : new java.nio.file.StandardOpenOption[]{java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING})) {
            copyLimited(in, out, maxBytes - offset, handle, downloadManager);
        } finally {
            connection.disconnect();
        }
    }

    private void transfer(String url, Path target, Map<String, String> headers, long maxBytes) throws IOException {
        transfer(url, target, headers, maxBytes, null);
    }

    private void transfer(String url, Path target, Map<String, String> headers, long maxBytes,
                          DownloadManager.Handle handle) throws IOException {
        HttpURLConnection connection = openWithRedirects(url, headers);
        long contentLength = connection.getContentLengthLong();
        if (contentLength > maxBytes) {
            connection.disconnect();
            throw new IOException("Download exceeds size limit: " + contentLength + " > " + maxBytes);
        }
        try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            copyLimited(in, out, maxBytes, handle, downloadManager);
        } finally {
            connection.disconnect();
        }
    }

    private static long copyLimited(InputStream in, OutputStream out, long maxBytes) throws IOException {
        return copyLimited(in, out, maxBytes, null, null);
    }

    private static long copyLimited(InputStream in, OutputStream out, long maxBytes,
                                    DownloadManager.Handle handle, DownloadManager manager) throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read == 0) continue;
            total += read;
            if (total > maxBytes) {
                throw new IOException("Response exceeds size limit of " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
            if (handle != null && manager != null) manager.advanced(handle, read);
        }
        return total;
    }

    private HttpURLConnection openWithRedirects(String url, Map<String, String> headers) throws IOException {
        URI current = SafeNetwork.requirePublicHttps(url);
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpURLConnection connection = open(current, headers);
            int code = connection.getResponseCode();
            collectCookies(connection, current);
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) {
                    throw new IOException("Redirect response has no Location header: " + current);
                }
                current = SafeNetwork.resolvePublicHttps(current, location);
                continue;
            }
            if (code < 200 || code >= 300) {
                connection.disconnect();
                throw new IOException("HTTP " + code + " for " + current);
            }
            return connection;
        }
        throw new IOException("Too many redirects for " + url);
    }

    private HttpURLConnection open(URI uri, Map<String, String> headers) throws IOException {

        InetAddress[] validated = SafeNetwork.resolveAndValidate(uri);
        URL parsed = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) parsed.openConnection(proxy);
        if (proxy == Proxy.NO_PROXY && connection instanceof javax.net.ssl.HttpsURLConnection https
                && validated.length > 0) {
            https.setSSLSocketFactory(new PinnedAddressSSLSocketFactory(validated[0], uri.getHost()));
            String hostHeader = uri.getHost();
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                hostHeader = hostHeader + ":" + uri.getPort();
            }
            https.setRequestProperty("Host", hostHeader);
        }
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "*/*");
        String cookieHeader = cookieHeader(uri.getHost());
        if (cookieHeader != null) {
            connection.setRequestProperty("Cookie", cookieHeader);
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (!"cookie".equalsIgnoreCase(entry.getKey())) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        return connection;
    }

    private void collectCookies(HttpURLConnection connection, URI origin) {
        Map<String, CookieRecord> hostCookies = cookiesByHost.computeIfAbsent(
                origin.getHost().toLowerCase(), ignored -> new ConcurrentHashMap<>());
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() == null || !"set-cookie".equalsIgnoreCase(header.getKey())) {
                continue;
            }
            for (String value : header.getValue()) {
                try {
                    for (HttpCookie cookie : HttpCookie.parse(value)) {
                        String domain = cookie.getDomain();
                        if (domain == null || domain.isBlank()
                                || domain.replaceFirst("^\\.", "").equalsIgnoreCase(origin.getHost())) {
                            hostCookies.put(cookie.getName(),
                                    new CookieRecord(cookie.getValue(), cookie.getMaxAge()));
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                 
                }
            }
        }
    }

    private String cookieHeader(String host) {
        Map<String, CookieRecord> hostCookies = cookiesByHost.get(host.toLowerCase());
        if (hostCookies == null || hostCookies.isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, CookieRecord> cookie : hostCookies.entrySet()) {
            if (cookie.getValue().expired()) {
                hostCookies.remove(cookie.getKey(), cookie.getValue());
                continue;
            }
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(cookie.getKey()).append('=').append(cookie.getValue().value);
        }
        return result.length() == 0 ? null : result.toString();
    }
    private static final class PinnedAddressSSLSocketFactory extends javax.net.ssl.SSLSocketFactory {
        private final javax.net.ssl.SSLSocketFactory delegate =
                (javax.net.ssl.SSLSocketFactory) javax.net.ssl.SSLSocketFactory.getDefault();
        private final InetAddress address;
        private final String host;

        PinnedAddressSSLSocketFactory(InetAddress address, String host) {
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
}