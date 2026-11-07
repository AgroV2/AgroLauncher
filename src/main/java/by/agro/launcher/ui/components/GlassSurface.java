package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.InterfaceStyle;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;


final class GlassSurface {
    private static final BasicStroke STROKE = new BasicStroke(1f);
    private InterfaceStyle cachedStyle;
    private int cachedWidth = -1, cachedHeight = -1, cachedBg, cachedAccent, cachedBorder, cachedOpacity = -1;
    private boolean cachedFast;
    private Paint fill;
    private Color edge, innerEdge, shine;
    private int generation;

    void paint(Graphics2D g, InterfaceStyle style, int x, int y, int width, int height, int arc,
               Color background, Color accent, Color border, int opacity, boolean fast) {
        if (width < 2 || height < 2) return;
        prepare(style, width, height, background, accent, border, opacity, fast);
        g.translate(x, y);
        try {
            g.setPaint(fill);
            g.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (fast) return;
            g.setStroke(STROKE);
            g.setColor(edge);
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (innerEdge != null && width > 12 && height > 12) {
                g.setColor(innerEdge);
                g.drawRoundRect(3, 3, width - 7, height - 7, Math.max(2, arc - 5), Math.max(2, arc - 5));
            }
            if (shine != null && width > arc + 8) {
                g.setColor(shine);
                g.drawLine(arc / 2 + 3, 2, width - arc / 2 - 4, 2);
            }
        } finally {
            g.translate(-x, -y);
        }
    }

    private void prepare(InterfaceStyle style, int width, int height, Color bg, Color accent,
                         Color border, int opacity, boolean fast) {
        if (style == cachedStyle && width == cachedWidth && height == cachedHeight
                && bg.getRGB() == cachedBg && accent.getRGB() == cachedAccent
                && border.getRGB() == cachedBorder && opacity == cachedOpacity && fast == cachedFast) return;
        cachedStyle = style; cachedWidth = width; cachedHeight = height;
        cachedBg = bg.getRGB(); cachedAccent = accent.getRGB(); cachedBorder = border.getRGB();
        cachedOpacity = opacity; cachedFast = fast; generation++;
        innerEdge = null; shine = null;

        float accentMix;
        float opacityScale;
        float whiteMix;
        int extraAlpha;
        switch (style) {
            case LIQUID_GLASS -> { accentMix = 0.14f; opacityScale = 0.60f; whiteMix = 0.22f; extraAlpha = 42; }
            case FROSTED_GLASS -> { accentMix = 0.05f; opacityScale = 0.78f; whiteMix = 0.13f; extraAlpha = 14; }
            case CRYSTAL_GLASS -> { accentMix = 0.08f; opacityScale = 0.38f; whiteMix = 0.30f; extraAlpha = 30; }
            case GLASSMORPHISM -> { accentMix = 0.09f; opacityScale = 0.52f; whiteMix = 0.18f; extraAlpha = 28; }
            default -> throw new IllegalArgumentException("Not a glass style: " + style);
        }
        Color tint = mix(bg, accent, accentMix);
        int alpha = Math.min(200, Math.max(52, Math.round(255 * opacity / 100f * opacityScale)));
        Color bottom = withAlpha(tint, alpha);
        if (fast) { fill = bottom; edge = null; return; }
        Color top = withAlpha(mix(tint, Color.WHITE, whiteMix), Math.min(255, alpha + extraAlpha));
        fill = new GradientPaint(0, 0, top, 0, Math.max(1, height), bottom);
        switch (style) {
            case LIQUID_GLASS -> edge = withAlpha(mix(border, accent, 0.30f), 150);
            case FROSTED_GLASS -> edge = withAlpha(mix(border, Color.WHITE, 0.28f), 105);
            case CRYSTAL_GLASS -> {
                edge = withAlpha(mix(border, Color.WHITE, 0.70f), 195);
                innerEdge = withAlpha(Color.WHITE, 55);
                shine = withAlpha(Color.WHITE, 100);
            }
            case GLASSMORPHISM -> {
                edge = withAlpha(mix(border, Color.WHITE, 0.42f), 145);
                shine = withAlpha(Color.WHITE, 46);
            }
            default -> throw new IllegalArgumentException("Not a glass style: " + style);
        }
    }

    int generation() { return generation; }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    private static Color mix(Color a, Color b, float amount) {
        float inverse = 1f - amount;
        return new Color(Math.round(a.getRed() * inverse + b.getRed() * amount),
                Math.round(a.getGreen() * inverse + b.getGreen() * amount),
                Math.round(a.getBlue() * inverse + b.getBlue() * amount));
    }
}
