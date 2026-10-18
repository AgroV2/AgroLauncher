package by.agro.launcher.core;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;

public final class MetadataCache {

    private static final long MAX_BODY_BYTES = 8L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 12L * 1024 * 1024;
    private static final int MAX_ENTRIES = 512;

    private final Path directory;
    private final Downloader downloader;
    private final boolean enabled;

    public MetadataCache(Path directory, Downloader downloader) {
        this(directory, downloader, true);
    }

    public MetadataCache(Path directory, Downloader downloader, boolean enabled) {
        this.directory = directory.toAbsolutePath().normalize();
        this.downloader = java.util.Objects.requireNonNull(downloader, "downloader");
        this.enabled = enabled;
    }

    public String get(String url, Duration ttl) throws IOException {
        return get(url, Map.of(), ttl);
    }

    public String get(String url, Map<String, String> headers, Duration ttl) throws IOException {
        if (!enabled || containsCredentials(headers)) {
            return downloader.getString(url, headers);
        }
        long ttlMillis = Math.max(0, java.util.Objects.requireNonNull(ttl, "ttl").toMillis());
        Path entry = entryPath(url);
        Cached cached = read(entry);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.storedAt <= ttlMillis) {
            return cached.body;
        }
        try {
            String body = downloader.getString(url, headers);
            write(entry, url, body, now);
            prune();
            return body;
        } catch (IOException e) {
            if (cached != null) {
                return cached.body;
            }
            throw e;
        }
    }

    private Cached read(Path entry) {
        try {
            if (!Files.isRegularFile(entry) || Files.size(entry) > MAX_ENTRY_BYTES) {
                Files.deleteIfExists(entry);
                return null;
            }
            JsonObject root = Json.parseObject(Files.readString(entry, StandardCharsets.UTF_8));
            long storedAt = root.has("storedAt") ? root.get("storedAt").getAsLong() : -1;
            String body = Json.string(root, "body", null);
            if (storedAt < 0 || body == null || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
                throw new IOException("Invalid metadata cache entry");
            }
            return new Cached(storedAt, body);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(entry);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    private void write(Path entry, String url, String body, long storedAt) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BODY_BYTES) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("url", url);
        root.addProperty("storedAt", storedAt);
        root.addProperty("body", body);
        byte[] encoded = root.toString().getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_ENTRY_BYTES) {
            return;
        }
        Files.createDirectories(directory);
        Path temporary = SecureFiles.createSiblingTemp(entry, ".tmp");
        try {
            Files.write(temporary, encoded);
            SecureFiles.atomicReplace(temporary, entry);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void prune() {
        try (var stream = Files.list(directory)) {
            var entries = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(MetadataCache::lastModified).reversed())
                    .toList();
            for (int i = MAX_ENTRIES; i < entries.size(); i++) {
                Files.deleteIfExists(entries.get(i));
            }
        } catch (IOException ignored) {
        }
    }

    private Path entryPath(String url) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                name.append(String.format("%02x", value & 0xff));
            }
            return directory.resolve(name + ".json");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static boolean containsCredentials(Map<String, String> headers) {
        if (headers == null) {
            return false;
        }
        for (String key : headers.keySet()) {
            if (key != null && (key.equalsIgnoreCase("Authorization")
                    || key.equalsIgnoreCase("Proxy-Authorization")
                    || key.equalsIgnoreCase("Cookie")
                    || key.equalsIgnoreCase("Set-Cookie")
                    || key.equalsIgnoreCase("X-Api-Key")
                    || key.equalsIgnoreCase("Api-Key"))) {
                return true;
            }
        }
        return false;
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private record Cached(long storedAt, String body) {
    }
}
