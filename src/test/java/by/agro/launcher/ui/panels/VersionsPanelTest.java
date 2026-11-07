package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Settings;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.version.RemoteVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class VersionsPanelTest {
    @TempDir Path temp;

    @Test void existingRustSelectionSurvivesRefreshAndVanillaRemainsSeparate() throws Exception {
        Field singleton = LauncherPaths.class.getDeclaredField("instance");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        singleton.set(null, LauncherPaths.forRoot(temp));
        try {
            Settings settings = new Settings();
            settings.offlineMode = true;
            settings.selectedVersion = "26.3";
            settings.selectedLoader = "horizon";
            settings.selectedLoaderVersion = "nightly";
            LauncherContext context = new LauncherContext(settings);
            AtomicReference<VersionsPanel> panelRef = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> panelRef.set(new VersionsPanel(context, null, null)));
            VersionsPanel panel = panelRef.get();
            AtomicBoolean ready = new AtomicBoolean();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (!ready.get() && System.nanoTime() < deadline) {
                SwingUtilities.invokeAndWait(() -> ready.set(((JButton) field(panel, "refreshButton")).isEnabled()));
                if (!ready.get()) Thread.sleep(20);
            }
            assertTrue(ready.get());
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> {
                try {
                    var display = VersionsPanel.class.getDeclaredMethod("displayVersions", List.class);
                    display.setAccessible(true);
                    var source = List.of(new RemoteVersion("26.4", "release", null, null, null),
                            new RemoteVersion("26.3", "release", null, null, null));
                    display.invoke(panel, source);
                    @SuppressWarnings("unchecked") JList<RemoteVersion> list = (JList<RemoteVersion>) field(panel, "versionsList");
                    @SuppressWarnings("unchecked") JComboBox<LoaderType> loaders = (JComboBox<LoaderType>) field(panel, "loaderCombo");
                    assertEquals("26.3 Rust", list.getSelectedValue().id);
                    assertFalse(loaders.isEnabled());
                    assertNull(loaders.getSelectedItem());
                    for (int i = 0; i < loaders.getItemCount(); i++) assertNotEquals(LoaderType.HORIZON, loaders.getItemAt(i));
                    list.setSelectedIndex(2);
                    assertEquals("26.3", settings.selectedVersion);
                    assertEquals("vanilla", settings.selectedLoader);
                    assertTrue(loaders.isEnabled());
                    list.setSelectedIndex(1);
                    assertEquals("26.3", settings.selectedVersion);
                    assertEquals("horizon", settings.selectedLoader);
                    assertEquals("nightly", settings.selectedLoaderVersion);
                    assertFalse(loaders.isEnabled());
                    panel.refreshSelection();
                    assertEquals("26.3 Rust", list.getSelectedValue().id);
                    display.invoke(panel, source);
                    assertEquals("26.3 Rust", list.getSelectedValue().id);
                    list.setSelectedIndex(0);
                    assertEquals("26.4", settings.selectedVersion);
                    assertEquals("vanilla", settings.selectedLoader);
                    assertTrue(loaders.isEnabled());
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
        } finally {
            singleton.set(null, previous);
        }
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
