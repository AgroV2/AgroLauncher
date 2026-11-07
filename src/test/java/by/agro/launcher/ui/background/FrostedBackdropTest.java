package by.agro.launcher.ui.background;

import by.agro.launcher.core.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FrostedBackdropTest {
    @TempDir Path temp;

    @Test void staticBackgroundIsBlurredAsyncSharedAndInvalidated() throws Exception {
        Path file = temp.resolve("background.png");
        BufferedImage source = new BufferedImage(90, 60, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++)
            source.setRGB(x, y, (x / 5 % 2 == 0 ? Color.BLACK : Color.WHITE).getRGB());
        ImageIO.write(source, "png", file.toFile());
        Settings settings = new Settings(); settings.backgroundImagePath = file.toString();
        BackgroundManager manager = new BackgroundManager(); assertTrue(manager.load(settings));
        CountDownLatch ready = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> assertNull(manager.frostedBackdrop(180, 120, 12, ready::countDown)));
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        AtomicReference<BufferedImage> first = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            first.set(manager.frostedBackdrop(180, 120, 12, null));
            assertNotNull(first.get());
            assertEquals(60, first.get().getWidth());
            assertEquals(40, first.get().getHeight());
            assertSame(first.get(), manager.frostedBackdrop(180, 120, 12, null));
        });
        int red = (first.get().getRGB(12, 20) >> 16) & 0xff;
        assertTrue(red > 5 && red < 250, "Striped image should be blurred");
        CountDownLatch next = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            manager.invalidateCache();
            assertNull(manager.frostedBackdrop(180, 120, 12, next::countDown));
        });
        assertTrue(next.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> assertNotSame(first.get(), manager.frostedBackdrop(180, 120, 12, null)));
    }

    @Test void animatedBackgroundDoesNotScheduleAStaleBlur() throws Exception {
        Path file = temp.resolve("animated.gif");
        ImageIO.write(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB), "gif", file.toFile());
        Settings settings = new Settings(); settings.backgroundImagePath = file.toString();
        BackgroundManager manager = new BackgroundManager(); assertTrue(manager.load(settings));
        SwingUtilities.invokeAndWait(() -> assertNull(manager.frostedBackdrop(100, 100, 12,
                () -> fail("animated GIF must not schedule a static backdrop"))));
    }
}
