package by.agro.launcher.ui.panels;

import by.agro.launcher.horizon.HorizonNativeMods;
import by.agro.launcher.i18n.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class HorizonNativeModsPanelTest {
    @TempDir Path temp;
    private Path bundle(String id, String version) throws Exception {
        Path path = temp.resolve(id + "-input.hnmod");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(out, "horizon-native.json", "{\"schema\":1,\"id\":\"" + id + "\",\"version\":\"" + version
                    + "\",\"game\":\"26.3\",\"horizon\":\"" + "a".repeat(40) + "\",\"mixins\":\"" + "b".repeat(40) + "\"}");
            entry(out, "mixins/" + id + "/horizon-mixin.toml", "[mod]\nid='" + id + "'\n");
        }
        return path;
    }
    private void entry(ZipOutputStream out, String name, String text) throws Exception {
        out.putNextEntry(new ZipEntry(name)); out.write(text.getBytes(StandardCharsets.UTF_8)); out.closeEntry();
    }
    @SuppressWarnings("unchecked") private static <T> T field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return (T) f.get(target);
    }
    @Test void listSearchAndSelectionUseInstalledModLayout() throws Exception {
        HorizonNativeMods mods = new HorizonNativeMods(temp.resolve("draft"), temp.resolve("root"));
        mods.add(bundle("zoom", "1.2")); mods.add(bundle("minimap", "2.0")); mods.setEnabled("minimap", false);
        SwingUtilities.invokeAndWait(() -> {
            try {
                HorizonNativeModsPanel panel = new HorizonNativeModsPanel(null, mods, () -> true);
                panel.refresh();
                JList<HorizonNativeMods.Mod> list = field(panel, "list");
                JTextField search = field(panel, "search");
                JLabel summary = field(panel, "summary");
                JScrollPane log = field(panel, "logScroll");
                assertEquals(2, list.getModel().getSize());
                assertEquals(44, list.getFixedCellHeight());
                assertEquals(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION, list.getSelectionMode());
                assertEquals(Strings.get("mods.summary", 2, 1L), summary.getText());
                assertFalse(log.isVisible());
                search.setText("ZOOM"); assertEquals(1, list.getModel().getSize());
                assertEquals("zoom", list.getModel().getElementAt(0).id());
                search.setText("2.0"); assertEquals("minimap", list.getModel().getElementAt(0).id());
                search.setText("no-such-mod"); assertEquals(0, list.getModel().getSize());
                search.setText(""); assertEquals(2, list.getModel().getSize());
                List<String> labels = new ArrayList<>();
                collectLabels(list.getCellRenderer().getListCellRendererComponent(list, list.getModel().getElementAt(0), 0, true, false), labels);
                assertTrue(labels.contains(Strings.get("mods.disabled")));
                assertTrue(labels.stream().anyMatch(x -> x.contains("2.0")));
                panel.setSize(760, 600); layout(panel);
                JButton build = field(panel, "buildButton");
                assertTrue(build.getWidth() > 0 && build.getX() + build.getWidth() <= build.getParent().getWidth());
                list.setSelectionInterval(0, 1);
                JButton toggle = findButton(panel, Strings.get("mods.toggle"));
                assertNotNull(toggle); toggle.doClick();
                assertTrue(mods.list().stream().filter(m -> m.id().equals("minimap")).findFirst().orElseThrow().enabled());
                assertFalse(mods.list().stream().filter(m -> m.id().equals("zoom")).findFirst().orElseThrow().enabled());
            } catch (Exception e) { throw new AssertionError(e); }
        });
    }
    private static JButton findButton(Component component, String text) {
        if (component instanceof JButton button && text.equals(button.getText())) return button;
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            JButton found = findButton(child, text); if (found != null) return found;
        }
        return null;
    }
    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
        }
    }
    private static void collectLabels(Component component, List<String> out) {
        if (component instanceof JLabel label) out.add(label.getText());
        if (component instanceof Container container) for (Component child : container.getComponents()) collectLabels(child, out);
    }
}
