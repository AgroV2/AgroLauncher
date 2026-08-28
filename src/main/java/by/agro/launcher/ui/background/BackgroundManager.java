package by.agro.launcher.ui.background;

import by.agro.launcher.core.Settings;
import by.agro.launcher.i18n.Strings;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BackgroundManager {

    private static final int MAX_SOURCE_SIDE = 2560;

    private String loadedPath = "";
    private BufferedImage sourceImage;
    private ImageIcon animatedIcon;
    private boolean animated;

    private BufferedImage renderedCache;
    private int cachedWidth;
    private int cachedHeight;
    private int cachedBlurRadius = -1;
    private boolean cachedBlurEnabled;

    private String lastError;

    public boolean load(Settings settings) {
        String path = settings.backgroundImagePath;
        if (path == null || path.isBlank()) {
            clear();
            return false;
        }
        if (path.equals(loadedPath) && (sourceImage != null || animatedIcon != null)) {
            return true;
        }

        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            lastError = Strings.get("appearance.fileNotFound", path);
            clear();
            return false;
        }

        try {
            String lower = path.toLowerCase();
            animated = lower.endsWith(".gif");

            if (animated) {
                animatedIcon = new ImageIcon(file.toAbsolutePath().toString());
                if (animatedIcon.getIconWidth() <= 0) {
                    lastError = Strings.get("appearance.gifReadFailed");
                    clear();
                    return false;
                }
                sourceImage = readFirstFrame(file);
            } else {
                BufferedImage read = ImageIO.read(file.toFile());
                if (read == null) {
                    lastError = Strings.get("appearance.formatUnsupported");
                    clear();
                    return false;
                }
                sourceImage = downscaleIfNeeded(read);
                animatedIcon = null;
            }

            loadedPath = path;
            lastError = null;
            invalidateCache();
            return true;
        } catch (IOException | RuntimeException e) {
            lastError = Strings.get("appearance.readError", e.getMessage());
            clear();
            return false;
        }
    }

    public BufferedImage render(int width, int height, Settings settings) {
        if (width <= 0 || height <= 0) {
            return null;
        }
        if (sourceImage == null) {
            return null;
        }

        boolean blurEnabled = settings.backgroundBlur;
        int blurRadius = settings.backgroundBlurRadius;

        boolean cacheValid = renderedCache != null
                && cachedWidth == width
                && cachedHeight == height
                && cachedBlurEnabled == blurEnabled
                && cachedBlurRadius == blurRadius;

        if (cacheValid) {
            return renderedCache;
        }

        BufferedImage scaled = scaleToCover(sourceImage, width, height);
        if (blurEnabled) {
            scaled = GaussianBlur.blur(scaled, blurRadius);
        }

        renderedCache = scaled;
        cachedWidth = width;
        cachedHeight = height;
        cachedBlurEnabled = blurEnabled;
        cachedBlurRadius = blurRadius;
        return renderedCache;
    }

    public ImageIcon animatedIcon(Settings settings) {
        if (!animated || settings.backgroundBlur) {
            return null;
        }
        return animatedIcon;
    }

    public boolean isAnimated() {
        return animated;
    }

    public boolean hasBackground() {
        return sourceImage != null || animatedIcon != null;
    }

    public String lastError() {
        return lastError;
    }

    public void clear() {
        sourceImage = null;
        animatedIcon = null;
        renderedCache = null;
        animated = false;
        loadedPath = "";
        invalidateCache();
    }

    public void invalidateCache() {
        renderedCache = null;
        cachedWidth = -1;
        cachedHeight = -1;
        cachedBlurRadius = -1;
    }

    private BufferedImage scaleToCover(BufferedImage source, int targetWidth, int targetHeight) {
        double scale = Math.max(
                targetWidth / (double) source.getWidth(),
                targetHeight / (double) source.getHeight());

        int scaledWidth = (int) Math.ceil(source.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(source.getHeight() * scale);

        BufferedImage result = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        var g2 = result.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);

        int offsetX = (targetWidth - scaledWidth) / 2;
        int offsetY = (targetHeight - scaledHeight) / 2;
        g2.drawImage(source, offsetX, offsetY, scaledWidth, scaledHeight, null);
        g2.dispose();
        return result;
    }

    private BufferedImage downscaleIfNeeded(BufferedImage source) {
        int maxSide = Math.max(source.getWidth(), source.getHeight());
        if (maxSide <= MAX_SOURCE_SIDE) {
            return source;
        }
        double scale = MAX_SOURCE_SIDE / (double) maxSide;
        int width = (int) (source.getWidth() * scale);
        int height = (int) (source.getHeight() * scale);

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g2 = result.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(source, 0, 0, width, height, null);
        g2.dispose();
        return result;
    }

    private BufferedImage readFirstFrame(Path file) {
        try (var stream = ImageIO.createImageInputStream(file.toFile())) {
            var readers = ImageIO.getImageReaders(stream);
            if (readers.hasNext()) {
                var reader = readers.next();
                reader.setInput(stream);
                BufferedImage frame = reader.read(0);
                reader.dispose();
                return frame != null ? downscaleIfNeeded(frame) : null;
            }
        } catch (IOException | RuntimeException e) {
        }
        if (animatedIcon != null) {
            Image image = animatedIcon.getImage();
            int width = animatedIcon.getIconWidth();
            int height = animatedIcon.getIconHeight();
            if (width > 0 && height > 0) {
                BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                var g2 = frame.createGraphics();
                g2.drawImage(image, 0, 0, null);
                g2.dispose();
                return frame;
            }
        }
        return null;
    }

    public static boolean isSupportedFile(File file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".bmp");
    }
}
