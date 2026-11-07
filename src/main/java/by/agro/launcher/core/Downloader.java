package by.agro.launcher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
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
    private final HostLimiter hostLimiter;
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

    private static final java.util.Map<Path, PersistentEntry> VERIFIED_FILES =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final class PersistentEntry {
        public long size;
        public long mtime;
        public String sha1;
        public String modifiedExact;
        public String fileKey;

        public PersistentEntry() {
        }

        PersistentEntry(java.nio.file.attribute.BasicFileAttributes attrs, String sha1) {
            this.size = attrs.size();
            this.mtime = attrs.lastModifiedTime().toMillis();
            this.modifiedExact = attrs.lastModifiedTime().toString();
            this.fileKey = attrs.fileKey() == null ? "" : attrs.fileKey().toString();
            this.sha1 = sha1;
        }

        boolean matches(java.nio.file.attribute.BasicFileAttributes attrs, String expected) {
            return attrs.isRegularFile() && attrs.size() > 0 && sha1 != null
                    && sha1.equalsIgnoreCase(expected) && attrs.size() == size
                    && modifiedExact != null && modifiedExact.equals(attrs.lastModifiedTime().toString())
                    && fileKey != null && fileKey.equals(attrs.fileKey() == null ? "" : attrs.fileKey().toString());
        }
    }

    private static final int MAX_PERSISTENT_ENTRIES = 50_000;
    private static final java.util.Map<String, PersistentEntry> PERSISTENT_VERIFIED =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile Path verificationCacheFile;
    private static volatile boolean verificationDirty;
    private static volatile boolean verificationLoaded;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(Downloader::saveVerificationCache,
                "agro-verify-cache"));
    }

    public static void setVerificationCacheFile(Path file) {
        verificationCacheFile = file;
    }

    private static void loadVerificationCache() {
        if (verificationLoaded) {
            return;
        }
        verificationLoaded = true;
        Path file = verificationCacheFile;
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            java.util.Map<String, PersistentEntry> loaded = Json.GSON.fromJson(reader,
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, PersistentEntry>>() {
                    }.getType());
            if (loaded != null) {
                PERSISTENT_VERIFIED.putAll(loaded);
            }
        } catch (Exception ignored) {

        }
    }

    private static void saveVerificationCache() {
        Path file = verificationCacheFile;
        if (file == null || !verificationDirty) {
            return;
        }
        verificationDirty = false;
        try {
            Files.createDirectories(file.getParent());
            java.util.Map<String, PersistentEntry> snapshot = new java.util.LinkedHashMap<>();
            PERSISTENT_VERIFIED.forEach((path, entry) -> snapshot.put(path, entry));
            Json.write(file, snapshot);
        } catch (Exception ignored) {

        }
    }

    public boolean verifyOnce(Path target, String expectedSha1) {
        if (target == null) return false;
        if (expectedSha1 == null || expectedSha1.isBlank()) return HashUtil.verify(target, expectedSha1);
        Path path = target.toAbsolutePath().normalize();
        try {
            var before = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            if (!before.isRegularFile() || before.size() <= 0) return false;
            boolean executable = isExecutableArtifact(path);
            if (!executable) {
                PersistentEntry entry = VERIFIED_FILES.get(path);
                if (entry != null && entry.matches(before, expectedSha1)) return true;
                loadVerificationCache();
                entry = PERSISTENT_VERIFIED.get(path.toString());
                if (entry != null && entry.matches(before, expectedSha1)) {
                    VERIFIED_FILES.put(path, entry);
                    return true;
                }
            }
            boolean verified = HashUtil.verify(path, expectedSha1);
            var after = Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            PersistentEntry observed = new PersistentEntry(before, expectedSha1.toLowerCase(java.util.Locale.ROOT));
            if (!verified || !observed.matches(after, expectedSha1)) {
                evictVerification(path);
                return false;
            }

            if (!executable) {
                VERIFIED_FILES.put(path, observed);
                if (PERSISTENT_VERIFIED.size() < MAX_PERSISTENT_ENTRIES) {
                    PERSISTENT_VERIFIED.put(path.toString(), observed);
                    verificationDirty = true;
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            evictVerification(path);
            return false;
        }
    }

    private static void evictVerification(Path path) {
        VERIFIED_FILES.remove(path);
        if (PERSISTENT_VERIFIED.remove(path.toString()) != null) verificationDirty = true;
    }

    static boolean isExecutableArtifact(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".jar") || name.endsWith(".exe") || name.endsWith(".dll")
                || name.endsWith(".so") || name.contains(".so.") || name.endsWith(".dylib")
                || name.endsWith(".bin");
    }

    public Downloader() {
        this(Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2)), new DownloadManager());
    }

    public Downloader(int threads) {
        this(threads, new DownloadManager());
    }

    public Downloader(int threads, DownloadManager downloadManager) {
        this(threads, 4, downloadManager);
    }

    public Downloader(int threads, int perHost, DownloadManager downloadManager) {
        this.threads = Math.max(1, Math.min(32, threads));
        this.hostLimiter = new HostLimiter(perHost);
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
        for (Task task : tasks) {
            SafeNetwork.ensureAllowedDownloadHost(task.url);
        }
        List<Task> pending = filterUnverified(tasks);
        if (pending.isEmpty()) {
            listener.onProgress(stageName, 1, 1, by.agro.launcher.i18n.Strings.get("progress.allDownloaded"));
            return;
        }

        long estimatedBytes = ensureSpaceFor(pending);
        if (estimatedBytes > 0) {
            Path sampleTarget = pending.get(0).target;
            listener.onMessage(by.agro.launcher.i18n.Strings.get("disk.downloadEstimate", stageName,
                    DiskSpace.format(estimatedBytes), DiskSpace.format(DiskSpace.usableBytes(sampleTarget))));
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
        saveVerificationCache();
        if (firstError.get() != null) {
            Exception error = firstError.get();
            if (error instanceof DiskSpace.InsufficientSpaceException space) throw space;
            throw new IOException(by.agro.launcher.i18n.Strings.get("downloads.failedFiles", error.getMessage()), error);
        }
    }


    static long remainingBytes(Task task) {
        if (task == null || task.size <= 0 || task.target == null) return 0;
        long existing = 0;
        try {
            Path partial = task.target.resolveSibling(task.target.getFileName() + ".partial");
            if (Files.isRegularFile(partial)) existing = Files.size(partial);
        } catch (IOException | RuntimeException ignored) {
        }
        return Math.max(0, task.size - existing);
    }


    static long ensureSpaceFor(List<Task> pending) throws IOException {
        java.util.Map<Path, Long> needed = new java.util.LinkedHashMap<>();
        long total = 0;
        for (Task task : pending) {
            long bytes = remainingBytes(task);
            if (bytes <= 0) continue;
            Path parent = task.target.toAbsolutePath().normalize().getParent();
            needed.merge(parent == null ? task.target : parent, bytes, Long::sum);
            total += bytes;
        }
        DiskSpace.requireAll(needed);
        return total;
    }

    public void download(String url, Path target, String expectedSha1) throws IOException {
        download(url, target, expectedSha1, 0);
    }

    public void download(String url, Path target, String expectedSha1, long expectedSize) throws IOException {
        SafeNetwork.ensureAllowedDownloadHost(url);
        if (verifyOnce(target, expectedSha1)) {
            return;
        }
        if (expectedSize > 0) {
            ensureSpaceFor(List.of(new Task(url, target, expectedSha1, expectedSize)));
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
        return getStringLegacy(url, headers);
    }

    private String getStringLegacy(String url, Map<String, String> headers) throws IOException {
        try (ConnectionLease lease = openWithRedirects(url, headers, null);
             InputStream in = lease.connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copyLimited(in, out, MAX_TEXT_BYTES);
            return out.toString(StandardCharsets.UTF_8);
        }
    }


    public byte[] getBytes(String url, long maxBytes) throws IOException {
        if (maxBytes <= 0 || maxBytes > MAX_TEXT_BYTES) throw new IOException("Invalid response limit");
        try (ConnectionLease lease = openWithRedirects(url, Map.of(), null);
             InputStream in = lease.connection.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (lease.connection.getContentLengthLong() > maxBytes) throw new IOException("Response exceeds size limit");
            copyLimited(in, out, maxBytes);
            return out.toByteArray();
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
        try (ConnectionLease lease = openWithRedirects(url, requestHeaders, handle)) {
            HttpURLConnection connection = lease.connection;
            int code = connection.getResponseCode();
            boolean append = offset > 0 && code == HttpURLConnection.HTTP_PARTIAL;
            if (!append) offset = 0;
            long contentLength = connection.getContentLengthLong();
            if (contentLength > 0 && offset + contentLength > maxBytes) throw new IOException("Download exceeds size limit");
            try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target,
                    append ? new java.nio.file.StandardOpenOption[]{java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND}
                            : new java.nio.file.StandardOpenOption[]{java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING})) {
                copyLimited(in, out, maxBytes - offset, handle, downloadManager);
            }
        }
    }

    private void transfer(String url, Path target, Map<String, String> headers, long maxBytes) throws IOException {
        transfer(url, target, headers, maxBytes, null);
    }

    private void transfer(String url, Path target, Map<String, String> headers, long maxBytes,
                          DownloadManager.Handle handle) throws IOException {
        try (ConnectionLease lease = openWithRedirects(url, headers, handle)) {
            HttpURLConnection connection = lease.connection;
            long contentLength = connection.getContentLengthLong();
            if (contentLength > maxBytes) throw new IOException("Download exceeds size limit: " + contentLength + " > " + maxBytes);
            try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
                copyLimited(in, out, maxBytes, handle, downloadManager);
            }
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

    private static final class ConnectionLease implements AutoCloseable {
        final HttpURLConnection connection;
        final HostLimiter.Permit permit;
        ConnectionLease(HttpURLConnection connection, HostLimiter.Permit permit) {
            this.connection = connection; this.permit = permit;
        }
        public void close() {
            try { connection.disconnect(); } finally { permit.close(); }
        }
    }

    private ConnectionLease openWithRedirects(String url, Map<String, String> headers,
                                               DownloadManager.Handle handle) throws IOException {
        URI current = SafeNetwork.requirePublicHttps(url);
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HostLimiter.Permit permit = hostLimiter.acquire(current, handle);
            HttpURLConnection connection = null;
            boolean handedOff = false;
            try {
                connection = open(current, headers);
                int code = connection.getResponseCode();
                collectCookies(connection, current);
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new IOException("Redirect response has no Location header: " + current);
                    current = SafeNetwork.resolvePublicHttps(current, location);
                    continue;
                }
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " for " + current);
                handedOff = true;
                return new ConnectionLease(connection, permit);
            } finally {
                if (!handedOff) {
                    try { if (connection != null) connection.disconnect(); } finally { permit.close(); }
                }
            }
        }
        throw new IOException("Too many redirects for " + url);
    }

    private HttpURLConnection open(URI uri, Map<String, String> headers) throws IOException {

        HttpURLConnection connection = SafeNetwork.openConnection(uri, proxy);
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
        collectCookies(connection.getHeaderFields(), origin);
    }

    private void collectCookies(Map<String, List<String>> responseHeaders, URI origin) {
        Map<String, CookieRecord> hostCookies = cookiesByHost.computeIfAbsent(
                origin.getHost().toLowerCase(), ignored -> new ConcurrentHashMap<>());
        for (Map.Entry<String, List<String>> header : responseHeaders.entrySet()) {
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

}