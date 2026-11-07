package by.agro.launcher.ui.theme;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Settings;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.background.BackgroundManager;
import by.agro.launcher.ui.components.InterfaceStylePreviewCard;
import by.agro.launcher.ui.panels.AppearancePanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InterfaceStyleTest {
    @TempDir Path temp;

    @Test void everyStyleHasNamesHintsAndMetrics() {
        for (InterfaceStyle style : InterfaceStyle.values()) {
            assertSame(style, InterfaceStyle.fromId(style.name()));
            assertSame(style, InterfaceStyle.fromId(style.id()));
            assertTrue(Strings.has("appearance.style." + style.id()));
            assertTrue(Strings.has("appearance.style." + style.id() + ".hint"));
            assertTrue(style.cardArc() > 0);
        }
        assertEquals(InterfaceStyle.STANDARD, InterfaceStyle.fromId("invalid"));
    }

    @Test void stylesApplyAndDoNotAlterColorsOrGradients() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AgroTheme.install();
            AgroTheme.apply(ThemePreset.EMERALD_DARK, null);
            AgroTheme.setGradient(GradientPreset.DEEP_SEA, "");
            Color[] gradient = AgroTheme.gradientColors();
            ThemePalette palette = AgroTheme.palette();
            for (InterfaceStyle style : InterfaceStyle.values()) {
                AgroTheme.setInterfaceStyle(style);
                assertSame(palette, AgroTheme.palette());
                assertArrayEquals(gradient, AgroTheme.gradientColors());
                assertEquals(style.buttonArc(), UIManager.getInt("Button.arc"));
                assertEquals(style.cardArc(), AgroTheme.cardArc());
                assertEquals(style.navigationArc(), AgroTheme.navigationArc());
            }
            AgroTheme.setGradient(GradientPreset.NONE, "");
            AgroTheme.setInterfaceStyle(InterfaceStyle.STANDARD);
        });
    }

    @Test void previewSelectionSavesAndGridFitsAllStyles() throws Exception {
        Path original = LauncherPaths.get().root();
        LauncherPaths.override(temp);
        try {
            Settings settings = new Settings();
            settings.themePreset = "EMERALD_DARK";
            settings.gradientPreset = "DEEP_SEA";
            LauncherContext context = new LauncherContext(settings);
            AtomicInteger changes = new AtomicInteger();
            SwingUtilities.invokeAndWait(() -> {
                AgroTheme.install();
                AppearancePanel panel = new AppearancePanel(context, new BackgroundManager(), null, changes::incrementAndGet);
                List<InterfaceStylePreviewCard> cards = new ArrayList<>();
                collect(panel, cards);
                assertEquals(InterfaceStyle.values().length, cards.size());
                JPanel grid = (JPanel) cards.get(0).getParent();
                Container body = grid.getParent();
                Container outer = body.getParent();
                assertTrue(outer.getMaximumSize().height >= outer.getPreferredSize().height,
                        "Style card must not clip its preview rows");
                grid.setSize(600, grid.getPreferredSize().height);
                grid.doLayout();
                for (InterfaceStylePreviewCard card : cards) {
                    assertTrue(card.getY() + card.getHeight() <= grid.getHeight());
                    BufferedImage image = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    card.paint(image.getGraphics());
                }
                Color[] colors = AgroTheme.gradientColors();
                for (InterfaceStylePreviewCard card : cards) {
                    card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 15, 15, 1, false));
                    assertEquals(card.style().name(), settings.interfaceStyle);
                    assertEquals(card.style().name(), Settings.load().interfaceStyle);
                    assertEquals(card.style(), AgroTheme.interfaceStyle());
                    assertArrayEquals(colors, AgroTheme.gradientColors());
                    assertEquals("DEEP_SEA", settings.gradientPreset);
                    assertEquals("EMERALD_DARK", settings.themePreset);
                }
                assertEquals(InterfaceStyle.values().length, changes.get());
            });
        } finally {
            LauncherPaths.override(original);
            SwingUtilities.invokeAndWait(() -> {
                AgroTheme.setGradient(GradientPreset.NONE, "");
                AgroTheme.setInterfaceStyle(InterfaceStyle.STANDARD);
            });
        }
    }

    private static void collect(Component node, List<InterfaceStylePreviewCard> out) {
        if (node instanceof InterfaceStylePreviewCard card) out.add(card);
        if (node instanceof Container c) for (Component child : c.getComponents()) collect(child, out);
    }
}
