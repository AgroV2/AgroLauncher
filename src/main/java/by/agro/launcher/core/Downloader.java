package by.agro.launcher.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
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

    private static final String USER_AGENT = "AgroLauncher/1.0";
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_REDIRECTS = 6;
    private static final long DEFAULT_MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024;
    private static final long MAX_TEXT_BYTES = 16L * 1024 * 1024;

    private final int threads;
    private final Map<String, Map<String, String>> cookiesByHost = new ConcurrentHashMap<>();

    public Downloader() {
        this(Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2)));
    }

    public Downloader(int threads) {
        this.threads = threads;
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
        List<Task> pending = new ArrayList<>();
        for (Task task : tasks) {
            if (!HashUtil.verify(task.target, task.sha1)) {
                pending.add(task);
            }
        }
        if (pending.isEmpty()) {
            listener.onProgress(stageName, 1, 1, by.agro.launcher.i18n.Strings.get("progress.allDownloaded"));
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, pending.size()), r -> {
            Thread t = new Thread(r, "agro-downloader");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger done = new AtomicInteger();
        AtomicReference<Exception> firstError = new AtomicReference<>();
        int total = pending.size();
        List<Future<?>> futures = new ArrayList<>(total);
        try {
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
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
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
        if (HashUtil.verify(target, expectedSha1)) {
            return;
        }
        long limit = expectedSize > 0
                ? Math.min(DEFAULT_MAX_DOWNLOAD_BYTES, expectedSize + Math.max(1024, expectedSize / 100))
                : DEFAULT_MAX_DOWNLOAD_BYTES;
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Path temp = target.resolveSibling(target.getFileName() + ".partial");
            try {
                transferResumable(url, temp, Map.of(), limit);
                if (expectedSha1 != null && !expectedSha1.isBlank()) {
                    String actual = HashUtil.sha1(temp);
                    if (!actual.equalsIgnoreCase(expectedSha1)) {
                        throw new IOException("SHA-1 mismatch for " + url + " (expected "
                                + expectedSha1 + ", got " + actual + ")");
                    }
                }
                SecureFiles.atomicReplace(temp, target.toAbsolutePath().normalize());
                return;
            } catch (IOException e) {
                last = e;
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrupted", interrupted);
                    }
                }
            }
        }
        throw new IOException("Failed to download " + url + " after " + MAX_RETRIES + " attempts", last);
    }

    public void downloadVerified(String url, Path target, String algorithm, String expectedDigest,
                                 long maxBytes) throws IOException {
        if (expectedDigest == null || expectedDigest.isBlank()) {
            throw new IOException("A cryptographic digest is required for executable artifact: " + url);
        }
        if (Files.exists(target)) {
            try {
                if (expectedDigest.equalsIgnoreCase(HashUtil.digest(target, algorithm))) {
                    return;
                }
            } catch (IOException ignored) {
            }
        }
        Path temp = SecureFiles.createSiblingTemp(target, ".part");
        try {
            transfer(url, temp, Map.of(), maxBytes > 0 ? maxBytes : DEFAULT_MAX_DOWNLOAD_BYTES);
            String actual = HashUtil.digest(temp, algorithm);
            if (!actual.equalsIgnoreCase(expectedDigest)) {
                throw new IOException(algorithm + " mismatch for " + url);
            }
            SecureFiles.atomicReplace(temp, target.toAbsolutePath().normalize());
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

    private void transferResumable(String url, Path target, Map<String, String> headers, long maxBytes) throws IOException {
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
            copyLimited(in, out, maxBytes - offset);
        } finally { connection.disconnect(); }
    }

    private void transfer(String url, Path target, Map<String, String> headers, long maxBytes) throws IOException {
        HttpURLConnection connection = openWithRedirects(url, headers);
        long contentLength = connection.getContentLengthLong();
        if (contentLength > maxBytes) {
            connection.disconnect();
            throw new IOException("Download exceeds size limit: " + contentLength + " > " + maxBytes);
        }
        try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            copyLimited(in, out, maxBytes);
        } finally {
            connection.disconnect();
        }
    }

    private static long copyLimited(InputStream in, OutputStream out, long maxBytes) throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > maxBytes) {
                throw new IOException("Response exceeds size limit of " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
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
        
        SafeNetwork.validateResolvedAddresses(uri);
        URL parsed = uri.toURL();
        HttpURLConnection connection = (HttpURLConnection) parsed.openConnection();
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
        Map<String, String> hostCookies = cookiesByHost.computeIfAbsent(
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
                            hostCookies.put(cookie.getName(), cookie.getValue());
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                 
                }
            }
        }
    }

    private String cookieHeader(String host) {
        Map<String, String> hostCookies = cookiesByHost.get(host.toLowerCase());
        if (hostCookies == null || hostCookies.isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> cookie : hostCookies.entrySet()) {
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append(cookie.getKey()).append('=').append(cookie.getValue());
        }
        return result.toString();
    }
}
