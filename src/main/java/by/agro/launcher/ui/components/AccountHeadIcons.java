package by.agro.launcher.ui.components;

import by.agro.launcher.auth.Account;
import by.agro.launcher.core.Settings;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AccountHeadIcons {
    private static final String DEFAULT_SKIN_URL_TEMPLATE =
            "https://skinsystem.ely.by/skins/{username}.png";
    private static final int TIMEOUT_MS = 8_000;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int ICON_SIZE = 28;
    private static final int MAX_REDIRECTS = 5;
    private static final Logger LOGGER = Logger.getLogger(AccountHeadIcons.class.getName());
    private static final ConcurrentHashMap<String, Icon> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<Consumer<Icon>>> CALLBACKS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<String, Failure> LAST_FAILURE = new ConcurrentHashMap<>();

    enum Failure { NO_URL, HTTP_STATUS, INVALID_IMAGE, UNSUPPORTED_SIZE }

    private AccountHeadIcons() {
    }

    public static Icon cached(Account account) {
        return cached(account, null);
    }

    public static Icon cached(Account account, Settings settings) {
        String url = resolveSkinUrl(account, settings);
        return url.isBlank() ? null : CACHE.get(key(account, url));
    }

    public static void load(Account account, Consumer<Icon> callback) {
        load(account, null, callback);
    }

    public static void load(Account account, Settings settings, Consumer<Icon> callback) {
        String accountKey = accountKey(account);
        String primaryUrl = normalizeUrl(account == null ? null : account.skinUrl);
        String fallbackUrl = fallbackUrl(account);
        String selectedUrl = primaryUrl.isBlank() ? fallbackUrl : primaryUrl;
        String cacheKey = key(account, selectedUrl);
        if (selectedUrl.isBlank()) {
            if (!accountKey.isBlank()) LAST_FAILURE.put(accountKey, Failure.NO_URL);
            onEdt(() -> callback.accept(null));
            return;
        }
        Icon cached = CACHE.get(cacheKey);
        if (cached != null) {
            onEdt(() -> callback.accept(cached));
            return;
        }

        List<Consumer<Icon>> callbacks = new ArrayList<>();
        callbacks.add(callback);
        List<Consumer<Icon>> existing = CALLBACKS.putIfAbsent(cacheKey, callbacks);
        if (existing != null) {
            synchronized (existing) {
                existing.add(callback);
            }
            return;
        }

        Thread worker = new Thread(() -> {
            Icon icon = null;
            try {
                if (!primaryUrl.isBlank()) {
                    icon = download(primaryUrl, accountKey);
                }
                if (icon == null && !fallbackUrl.isBlank() && !fallbackUrl.equals(primaryUrl)) {
                    icon = download(fallbackUrl, accountKey);
                }
                if (icon != null) {
                    CACHE.put(cacheKey, icon);
                    LAST_FAILURE.remove(accountKey);
                }
            } catch (Exception error) {
                LAST_FAILURE.put(accountKey, Failure.INVALID_IMAGE);
                LOGGER.log(Level.WARNING, "Unable to load skin for account " + accountKey, error);
            }
            Icon loaded = icon;
            List<Consumer<Icon>> completed = CALLBACKS.remove(cacheKey);
            if (completed != null) {
                onEdt(() -> {
                    synchronized (completed) {
                        completed.forEach(consumer -> consumer.accept(loaded));
                    }
                });
            }
        }, "elyby-head-loader");
        worker.setDaemon(true);
        worker.start();
    }

    public static String resolveSkinUrl(Account account, Settings settings) {
        if (!isElyAccount(account)) return "";
        String primary = normalizeUrl(account.skinUrl);
        return primary.isBlank() ? fallbackUrl(account) : primary;
    }

    private static boolean isElyAccount(Account account) {
        return account != null && (account.type == Account.Type.ELY_BY || account.type == Account.Type.ELY_BY_OAUTH);
    }

    private static String fallbackUrl(Account account) {
        if (!isElyAccount(account) || account.username == null || account.username.isBlank()) return "";
        return DEFAULT_SKIN_URL_TEMPLATE.replace("{username}", encode(account.username));
    }

    private static String normalizeUrl(String value) {
        if (value == null) return "";
        String normalized = value.trim().replace("\\/", "/");
        if (normalized.startsWith("//")) normalized = "https:" + normalized;
        if (!normalized.isBlank() && !normalized.contains("://")) normalized = "https://" + normalized;
        try {
            URI uri = URI.create(normalized).normalize();
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) ? uri.toString() : "";
        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    static Failure lastFailure(Account account) {
        return LAST_FAILURE.get(accountKey(account));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String accountKey(Account account) {
        return account == null ? "" : (account.id == null ? account.username : account.id);
    }

    private static String key(Account account, String url) {
        return url + '\n' + accountKey(account);
    }

    private static Icon download(String skinUrl, String accountKey) throws IOException {
        String currentUrl = normalizeUrl(skinUrl);
        HttpURLConnection connection = null;
        int status = -1;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            connection = (HttpURLConnection) URI.create(currentUrl).toURL().openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "AgroLauncher/1.0");
            connection.setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.1");
            status = connection.getResponseCode();
            if (status < 300 || status >= 400) break;
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || redirects == MAX_REDIRECTS) {
                LAST_FAILURE.put(accountKey, Failure.HTTP_STATUS);
                LOGGER.warning("Skin redirect failed for account " + accountKey + ", status=" + status);
                return null;
            }
            currentUrl = normalizeUrl(URI.create(currentUrl).resolve(location).toString());
            if (currentUrl.isBlank()) {
                LAST_FAILURE.put(accountKey, Failure.HTTP_STATUS);
                return null;
            }
        }
        try {
            if (status < 200 || status >= 300) {
                LAST_FAILURE.put(accountKey, Failure.HTTP_STATUS);
                LOGGER.warning("Skin request failed for account " + accountKey + ", status=" + status);
                return null;
            }
            int declared = connection.getContentLength();
            if (declared > MAX_BYTES) {
                LAST_FAILURE.put(accountKey, Failure.UNSUPPORTED_SIZE);
                return null;
            }
            byte[] bytes;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(0, declared))) {
                byte[] buffer = new byte[8192];
                int total = 0;
                for (int read; (read = input.read(buffer)) != -1; ) {
                    total += read;
                    if (total > MAX_BYTES) {
                        LAST_FAILURE.put(accountKey, Failure.UNSUPPORTED_SIZE);
                        return null;
                    }
                    output.write(buffer, 0, read);
                }
                bytes = output.toByteArray();
            }
            BufferedImage skin = ImageIO.read(new ByteArrayInputStream(bytes));
            if (skin == null) {
                LAST_FAILURE.put(accountKey, Failure.INVALID_IMAGE);
                return null;
            }
            if (skin.getWidth() < 64 || skin.getHeight() < 32 || skin.getWidth() % 64 != 0) {
                LAST_FAILURE.put(accountKey, Failure.UNSUPPORTED_SIZE);
                return null;
            }
            int scale = skin.getWidth() / 64;
            if (skin.getHeight() < 16 * scale) {
                LAST_FAILURE.put(accountKey, Failure.UNSUPPORTED_SIZE);
                return null;
            }
            BufferedImage head = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = head.createGraphics();
            graphics.drawImage(skin, 0, 0, 8, 8,
                    8 * scale, 8 * scale, 16 * scale, 16 * scale, null);
            graphics.drawImage(skin, 0, 0, 8, 8,
                    40 * scale, 8 * scale, 48 * scale, 16 * scale, null);
            graphics.dispose();

            BufferedImage scaled = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(head, 0, 0, ICON_SIZE, ICON_SIZE, null);
            graphics.dispose();
            return new ImageIcon(scaled);
        } finally {
            connection.disconnect();
        }
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
