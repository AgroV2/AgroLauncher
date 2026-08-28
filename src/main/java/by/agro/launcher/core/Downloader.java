package by.agro.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final int threads;

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
            Thread t = new Thread(r, "emerald-downloader");
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
                        if (firstError.get() != null) {
                            return;
                        }
                        download(task.url, task.target, task.sha1);
                        int n = done.incrementAndGet();
                        listener.onProgress(stageName, n, total, task.target.getFileName().toString());
                    } catch (Exception e) {
                        firstError.compareAndSet(null, e);
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
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

        Exception error = firstError.get();
        if (error != null) {
            throw new IOException("Не удалось загрузить файлы: " + error.getMessage(), error);
        }
    }

    
    public void download(String url, Path target, String expectedSha1) throws IOException {
        if (HashUtil.verify(target, expectedSha1)) {
            return;
        }
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".part");

        IOException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                transfer(url, temp);
                if (expectedSha1 != null && !expectedSha1.isBlank()) {
                    String actual = HashUtil.sha1(temp);
                    if (!actual.equalsIgnoreCase(expectedSha1)) {
                        Files.deleteIfExists(temp);
                        throw new IOException("Несовпадение SHA-1 для " + url
                                + " (ожидалось " + expectedSha1 + ", получено " + actual + ")");
                    }
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                }
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Загрузка прервана", ie);
                    }
                }
            }
        }
        throw new IOException("Не удалось скачать " + url + " после " + MAX_RETRIES + " попыток", last);
    }

    public void download(String url, Path target) throws IOException {
        download(url, target, null);
    }

    private void transfer(String url, Path target) throws IOException {
        transfer(url, target, Map.of());
    }

    private void transfer(String url, Path target, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = openWithRedirects(url, headers);
        try (InputStream in = conn.getInputStream();
             OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        } finally {
            conn.disconnect();
        }
    }

    
    public String getString(String url) throws IOException {
        return getString(url, Map.of());
    }

    
    public String getString(String url, Map<String, String> headers) throws IOException {
        HttpURLConnection conn = openWithRedirects(url, headers);
        try (InputStream in = conn.getInputStream()) {
            collectCookies(conn);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }


    public void downloadWithHeaders(String url, Path target, Map<String, String> headers) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        try {
            transfer(url, temp, headers);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    
    private final Map<String, String> cookies = new java.util.concurrent.ConcurrentHashMap<>();

    private void collectCookies(HttpURLConnection conn) {
        List<String> setCookies = conn.getHeaderFields().get("Set-Cookie");
        if (setCookies == null) {
            return;
        }
        for (String header : setCookies) {
            int semicolon = header.indexOf(';');
            String pair = semicolon > 0 ? header.substring(0, semicolon) : header;
            int equals = pair.indexOf('=');
            if (equals > 0) {
                cookies.put(pair.substring(0, equals).trim(), pair.substring(equals + 1).trim());
            }
        }
    }

    private String cookieHeader() {
        if (cookies.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    
    private HttpURLConnection openWithRedirects(String url, Map<String, String> headers) throws IOException {
        String current = url;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpURLConnection conn = open(current, headers);
            collectCookies(conn);
            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null || location.isBlank()) {
                    throw new IOException("Редирект без Location: " + current);
                }
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            if (code < 200 || code >= 300) {
                conn.disconnect();
                throw new IOException("HTTP " + code + " для " + current);
            }
            return conn;
        }
        throw new IOException("Слишком много редиректов для " + url);
    }

    private HttpURLConnection open(String url, Map<String, String> headers) throws IOException {
        URL parsed = URI.create(url).toURL();
        HttpURLConnection conn = (HttpURLConnection) parsed.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");

        String cookieHeader = cookieHeader();
        if (cookieHeader != null) {
            conn.setRequestProperty("Cookie", cookieHeader);
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return conn;
    }
}
