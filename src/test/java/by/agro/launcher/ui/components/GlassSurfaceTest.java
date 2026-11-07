package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.InterfaceStyle;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class GlassSurfaceTest {
    private static void draw(GlassSurface surface, Graphics2D g, InterfaceStyle style, int width, int height,
                             Color bg, Color accent, Color border, int opacity, boolean fast) {
        surface.paint(g, style, 4, 5, width, height, style.cardArc(), bg, accent, border, opacity, fast);
    }

    @Test void cacheIsReusedAndInvalidatedOnlyWhenInputsChange() {
        GlassSurface surface = new GlassSurface();
        Graphics2D g = new BufferedImage(240, 150, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            Color bg = new Color(20, 25, 30), accent = Color.BLUE, border = Color.GRAY;
            draw(surface, g, InterfaceStyle.LIQUID_GLASS, 120, 80, bg, accent, border, 88, false);
            assertEquals(1, surface.generation());
            draw(surface, g, InterfaceStyle.LIQUID_GLASS, 120, 80, bg, accent, border, 88, false);
            assertEquals(1, surface.generation());
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 120, 80, bg, accent, border, 88, false);
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 80, bg, accent, border, 88, false);
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 81, bg, accent, border, 88, false);
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 81, Color.BLACK, accent, border, 88, false);
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 81, Color.BLACK, Color.RED, border, 88, false);
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 81, Color.BLACK, Color.RED, Color.WHITE, 88, false);
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 81, Color.BLACK, Color.RED, Color.WHITE, 65, false);
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 81, Color.BLACK, Color.RED, Color.WHITE, 65, true);
            assertEquals(9, surface.generation());
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 81, Color.BLACK, Color.RED, Color.WHITE, 65, true);
            assertEquals(9, surface.generation());
            draw(surface, g, InterfaceStyle.FROSTED_GLASS, 121, 81, Color.BLACK, Color.RED, Color.WHITE, 65, false);
            assertEquals(10, surface.generation());
        } finally { g.dispose(); }
    }

    @Test void stylesProduceDifferentTranslucentSurfacesAndSupportFastResize() {
        Set<Integer> centerPixels = new HashSet<>();
        for (InterfaceStyle style : new InterfaceStyle[]{InterfaceStyle.LIQUID_GLASS, InterfaceStyle.FROSTED_GLASS,
                InterfaceStyle.CRYSTAL_GLASS, InterfaceStyle.GLASSMORPHISM}) {
            assertTrue(style.isGlass());
            GlassSurface surface = new GlassSurface();
            BufferedImage image = new BufferedImage(140, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                draw(surface, g, style, 120, 80, new Color(20, 30, 40), Color.GREEN, Color.GRAY, 88, false);
                int pixel = image.getRGB(60, 40);
                assertTrue((pixel >>> 24) > 0 && (pixel >>> 24) < 255, style.name());
                centerPixels.add(pixel);
                draw(surface, g, style, 120, 80, new Color(20, 30, 40), Color.GREEN, Color.GRAY, 88, true);
                assertEquals(2, surface.generation());
            } finally { g.dispose(); }
        }
        assertEquals(4, centerPixels.size(), "Each glass style must render a distinct surface");
        assertFalse(InterfaceStyle.STANDARD.isGlass());
    }
}
