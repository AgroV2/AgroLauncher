package by.agro.launcher.modrinth;

import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.SafeNetwork;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class IconCache {

    private static final int ICON_SIZE = 64;
    private static final int MAX_MEMORY_ENTRIES = 300;
    private static final long MAX_ICON_BYTES = 8L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private final Path cacheDir;
    private final Map<String, BufferedImage> memoryCache = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public IconCache(LauncherPaths paths) {
        this.cacheDir = paths.cacheDir().resolve("icons");
        this.executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "agro-icon-loader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            System.err.println("Не удалось создать каталог кэша иконок: " + e.getMessage());
        }
    }

    public BufferedImage cached(String projectId) {
        return projectId == null ? null : memoryCache.get(projectId);
    }

    public void loadAsync(String projectId, String iconUrl, Consumer<BufferedImage> onLoaded) {
        if (projectId == null || onLoaded == null) {
            return;
        }
        BufferedImage fromMemory = memoryCache.get(projectId);
        if (fromMemory != null) {
            onLoaded.accept(fromMemory);
            return;
        }
        executor.submit(() -> {
            BufferedImage icon = load(projectId, iconUrl);
            if (icon != null) {
                onLoaded.accept(icon);
            }
        });
    }

    private BufferedImage load(String projectId, String iconUrl) {
        Path file = cacheDir.resolve(sanitize(projectId) + ".png");

        if (Files.exists(file)) {
            try {
                BufferedImage image = ImageIO.read(file.toFile());
                if (image != null) {
                    remember(projectId, image);
                    return image;
                }
            } catch (IOException e) {
            }
        }

        if (iconUrl == null || iconUrl.isBlank()) {
            return null;
        }

        try {
            BufferedImage downloaded = download(iconUrl);
            if (downloaded == null) {
                return null;
            }
            BufferedImage scaled = scale(downloaded, ICON_SIZE);
            try {
                ImageIO.write(scaled, "png", file.toFile());
            } catch (IOException e) {
            }
            remember(projectId, scaled);
            return scaled;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private BufferedImage download(String url) throws IOException {
        URI current = SafeNetwork.requirePublicHttps(url);
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            
            SafeNetwork.validateResolvedAddresses(current);
            HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
            connection.setRequestProperty("User-Agent", "AgroLauncher/1.0");
            connection.setConnectTimeout(12_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(false);
            int code = connection.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isBlank()) {
                    throw new IOException("Icon redirect has no Location header");
                }
                current = SafeNetwork.resolvePublicHttps(current, location);
                continue;
            }
            if (code < 200 || code >= 300) {
                connection.disconnect();
                throw new IOException("HTTP " + code + " while loading icon");
            }
            long length = connection.getContentLengthLong();
            if (length > MAX_ICON_BYTES) {
                connection.disconnect();
                throw new IOException("Icon exceeds size limit");
            }
            try (InputStream raw = connection.getInputStream();
                 InputStream limited = new LimitedInputStream(raw, MAX_ICON_BYTES)) {
                return ImageIO.read(limited);
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many icon redirects");
    }

    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream input, long limit) {
            super(input);
            remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                throw new IOException("Icon exceeds size limit");
            }
            int value = super.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining == 0) {
                throw new IOException("Icon exceeds size limit");
            }
            int read = super.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }

    private void remember(String projectId, BufferedImage image) {
        if (memoryCache.size() > MAX_MEMORY_ENTRIES) {
            memoryCache.clear();
        }
        memoryCache.put(projectId, image);
    }

    private BufferedImage scale(BufferedImage source, int size) {
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, size, size, null);
        g2.dispose();
        return result;
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
