package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.InterfaceStyle;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;


final class StyleVisuals {
    private StyleVisuals() { }

    static void paintCard(Graphics2D g, InterfaceStyle style, int x, int y, int width, int height,
                          int arc, Color base, Color border) {
        int w = Math.max(1, width - 1), h = Math.max(1, height - 1);
        g.setColor(base);
        g.fillRoundRect(x, y, w, h, arc, arc);
        switch (style) {
            case FLUENT -> {
                g.setColor(withAlpha(border, 155));
                g.drawRoundRect(x, y, w, h, arc, arc);
                g.setColor(new Color(0, 0, 0, 48));
                g.drawLine(x + arc / 2 + 1, y + h - 1, x + w - arc / 2 - 1, y + h - 1);
            }
            case APPLE -> {
                g.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 17),
                        x, y + h, new Color(255, 255, 255, 0)));
                g.fillRoundRect(x + 1, y + 1, Math.max(1, w - 2), Math.max(1, h - 2), arc, arc);
                g.setColor(withAlpha(border, 135));
                g.drawRoundRect(x, y, w, h, arc, arc);
                g.setColor(new Color(255, 255, 255, 32));
                g.drawLine(x + arc / 2 + 2, y + 1, x + w - arc / 2 - 2, y + 1);
            }
            case GNOME -> {
                g.setColor(new Color(0, 0, 0, 50));
                g.drawRoundRect(x, y + 1, w, Math.max(1, h - 1), arc, arc);
                g.setColor(withAlpha(border, 125));
                g.drawRoundRect(x, y, w, h, arc, arc);
            }
            case BREEZE -> {
                g.setColor(border);
                g.drawRoundRect(x, y, w, h, arc, arc);
                g.setColor(new Color(255, 255, 255, 26));
                g.drawLine(x + 4, y + 1, x + w - 4, y + 1);
            }
            default -> {
                g.setColor(border);
                g.drawRoundRect(x, y, w, h, arc, arc);
            }
        }
    }

    static void paintButtonFinish(Graphics2D g, InterfaceStyle style, int x, int y, int width, int height,
                                  int arc, boolean pressed) {
        if (pressed || width < 4 || height < 4) return;
        switch (style) {
            case FLUENT -> {
                g.setColor(new Color(0, 0, 0, 45));
                g.drawLine(x + arc / 2 + 1, y + height - 2, x + width - arc / 2 - 2, y + height - 2);
                g.setColor(new Color(255, 255, 255, 28));
                g.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            }
            case APPLE -> {
                g.setPaint(new GradientPaint(x, y, new Color(255, 255, 255, 44),
                        x, y + height, new Color(0, 0, 0, 20)));
                g.fillRoundRect(x, y, width, height, arc, arc);
                g.setColor(new Color(0, 0, 0, 35));
                g.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            }
            case GNOME -> {
                g.setColor(new Color(255, 255, 255, 32));
                g.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
            }
            case BREEZE -> {
                g.setColor(new Color(0, 0, 0, 55));
                g.drawRoundRect(x, y, width - 1, height - 1, arc, arc);
                g.setColor(new Color(255, 255, 255, 45));
                g.drawLine(x + 4, y + 1, x + width - 5, y + 1);
            }
            default -> { }
        }
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
