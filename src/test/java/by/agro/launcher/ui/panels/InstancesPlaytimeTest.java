package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class InstancesPlaytimeTest {
    @TempDir Path temp;

    @Test void rustInstanceKeepsOldPlaytimeAndRefreshesTotalsInsideScanCacheTtl() throws Exception {
        Field singleton = LauncherPaths.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        LauncherPaths paths = LauncherPaths.forRoot(temp);
        singleton.set(null, paths);
        try {
            Settings settings = new Settings(); settings.offlineMode = true;
            LauncherContext context = new LauncherContext(settings);
            Path instance = context.horizon().instanceDir();
            Files.createDirectories(instance);
            Files.writeString(instance.resolve(".agrolauncher-playtime.json"), "{\"totalMs\":7200000}");
            AtomicReference<InstancesPanel> ref = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> ref.set(new InstancesPanel(context, null, null)));
            InstancesPanel panel = ref.get();
            Field modelField = InstancesPanel.class.getDeclaredField("installationsModel");
            modelField.setAccessible(true);
            @SuppressWarnings("unchecked") var model = (DefaultListModel<InstancesPanel.Installation>) modelField.get(panel);
            waitFor(() -> model.size() == 1);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(1, model.size());
                assertEquals("26.3 Rust · Horizon", model.get(0).title());
                assertEquals(7200000, model.get(0).playtimeMs);
                assertFalse(model.get(0).formattedPlaytime().isBlank());
            });
            context.gameLauncher().recordPlayTime(instance, 60000);
            panel.refresh();
            waitFor(() -> model.size() == 1 && model.get(0).playtimeMs == 7260000);
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(7260000, model.get(0).playtimeMs);
            });
            assertNotNull(InstancesPanel.class.getDeclaredField("worldsButton"));
            assertNotNull(InstancesPanel.class.getDeclaredField("screenshotsButton"));
        } finally {
            singleton.set(null, previous);
        }
    }
    private static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(20);
        }
        fail("Timed out waiting for asynchronous scan");
    }

}
