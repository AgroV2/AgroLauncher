package by.agro.launcher.ui.components;

import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.ui.theme.InterfaceStyle;
import by.agro.launcher.ui.theme.ThemePalette;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

public final class InterfaceStylePreviewCard extends JPanel {

    private static final int WIDTH = 178;
    private static final int HEIGHT = 108;

    private final InterfaceStyle style;
    private ThemePalette palette;

    private boolean selected;
    private boolean hovered;

    public InterfaceStylePreviewCard(InterfaceStyle style, Consumer<InterfaceStyle> onSelect) {
        this.style = style;

        setOpaque(false);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setToolTipText(Strings.get("appearance.style." + style.id())
                + " — " + Strings.get("appearance.style." + style.id() + ".hint"));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onSelect != null) {
                    onSelect.accept(style);
                }
            }
        });
    }

    public InterfaceStyle style() {
        return style;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    private int componentArc() {
        return switch (style) {
            case MATERIAL_YOU -> 16;
            case LIQUID_GLASS -> 18;
            default -> 6;
        };
    }

    private int buttonArc() {
        return style == InterfaceStyle.STANDARD ? 5 : 11;
    }

    @Override
    protected void paintComponent(Graphics g) {
        palette = AgroTheme.palette();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int arc = 11;

        g2.setColor(palette.bgBase);
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);

        int sidebarWidth = 40;
        g2.setColor(palette.bgDeep);
        g2.fillRoundRect(0, 0, sidebarWidth, height - 1, arc, arc);
        g2.fillRect(sidebarWidth - arc, 0, arc, height - 1);

        boolean accentButton = selected;
        if (accentButton) {
            g2.setPaint(AgroTheme.gradientPaint(3, 16, 3, 14));
        } else {
            g2.setColor(AgroTheme.accent());
        }
        g2.fillRoundRect(3, 16, 3, 14, 2, 2);
        g2.setColor(palette.bgElevated);
        g2.fillRoundRect(9, 15, 25, 16, componentArc() / 2, componentArc() / 2);

        g2.setColor(palette.textMuted);
        for (int i = 0; i < 3; i++) {
            g2.fillRoundRect(11, 38 + i * 12, 21, 4, 2, 2);
        }

        int contentX = sidebarWidth + 10;
        int contentY = 14;
        int contentWidth = width - contentX - 12;
        int contentHeight = 34;

        if (style == InterfaceStyle.LIQUID_GLASS) {
            paintGlassBlobs(g2, contentX, contentY, contentWidth, contentHeight);
            paintGlassPanel(g2, contentX, contentY, contentWidth, contentHeight);
        } else {
            g2.setColor(palette.bgPanel);
            g2.fillRoundRect(contentX, contentY, contentWidth, contentHeight,
                    componentArc(), componentArc());
            g2.setColor(palette.border);
            g2.drawRoundRect(contentX, contentY, contentWidth, contentHeight,
                    componentArc(), componentArc());
        }

        g2.setColor(palette.textPrimary);
        g2.fillRoundRect(contentX + 8, contentY + 8, 52, 5, 2, 2);
        g2.setColor(palette.textSecondary);
        g2.fillRoundRect(contentX + 8, contentY + 19, 74, 4, 2, 2);

        int buttonY = 58;
        int buttonHeight = 20;
        if (accentButton) {
            g2.setPaint(AgroTheme.gradientPaint(contentX, buttonY, 56, buttonHeight));
        } else {
            g2.setColor(palette.accent);
        }
        g2.fillRoundRect(contentX, buttonY, 56, buttonHeight, buttonArc(), buttonArc());
        if (style == InterfaceStyle.LIQUID_GLASS) {
            g2.setPaint(new GradientPaint(0, buttonY, new Color(255, 255, 255, 60),
                    0, buttonY + buttonHeight, new Color(255, 255, 255, 0)));
            g2.fillRoundRect(contentX, buttonY, 56, buttonHeight, buttonArc(), buttonArc());
        }
        g2.setColor(palette.textOnAccent);
        g2.fillRoundRect(contentX + 14, buttonY + 8, 28, 4, 2, 2);

        g2.setColor(palette.bgElevated);
        g2.fillRoundRect(contentX + 64, buttonY, 44, buttonHeight, buttonArc(), buttonArc());
        g2.setColor(palette.border);
        g2.drawRoundRect(contentX + 64, buttonY, 44, buttonHeight, buttonArc(), buttonArc());

        g2.setColor(palette.textSecondary);
        g2.setFont(AgroTheme.font(10));
        String name = Strings.get("appearance.style." + style.id());
        FontMetrics metrics = g2.getFontMetrics();
        int nameWidth = metrics.stringWidth(name);
        if (nameWidth > width - contentX - 14) {
            while (nameWidth > width - contentX - 20 && name.length() > 4) {
                name = name.substring(0, name.length() - 2);
                nameWidth = metrics.stringWidth(name + "…");
            }
            name = name + "…";
        }
        g2.drawString(name, contentX, height - 8);

        Color borderColor;
        float strokeWidth;
        if (selected) {
            borderColor = AgroTheme.accent();
            strokeWidth = 2.4f;
        } else if (hovered) {
            borderColor = AgroTheme.borderLight();
            strokeWidth = 1.6f;
        } else {
            borderColor = AgroTheme.border();
            strokeWidth = 1f;
        }
        g2.setStroke(new BasicStroke(strokeWidth));
        g2.setColor(borderColor);
        g2.drawRoundRect(1, 1, width - 3, height - 3, arc, arc);

        if (selected) {
            int markSize = 18;
            int markX = width - markSize - 7;
            int markY = 7;
            g2.setColor(AgroTheme.accent());
            g2.fillOval(markX, markY, markSize, markSize);
            g2.setColor(AgroTheme.textOnAccent());
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(markX + 5, markY + 9, markX + 8, markY + 12);
            g2.drawLine(markX + 8, markY + 12, markX + 13, markY + 6);
        }

        g2.dispose();
    }

    private void paintGlassBlobs(Graphics2D g2, int x, int y, int width, int height) {
        Color accent = AgroTheme.accent();
        Color accentLight = AgroTheme.accentLight();
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
        g2.fillOval(x + 6, y + 4, 26, 26);
        g2.setColor(new Color(accentLight.getRed(), accentLight.getGreen(),
                accentLight.getBlue(), 110));
        g2.fillOval(x + width - 34, y + 10, 24, 20);
        g2.setColor(new Color(palette.info.getRed(), palette.info.getGreen(),
                palette.info.getBlue(), 90));
        g2.fillOval(x + width / 2 - 8, y + 2, 22, 18);
    }

    private void paintGlassPanel(Graphics2D g2, int x, int y, int width, int height) {
        int arc = componentArc();
        RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, width, height, arc, arc);

        Color base = palette.bgPanel;
        Color accent = AgroTheme.accent();
        Color tint = new Color(
                Math.round(base.getRed() * 0.86f + accent.getRed() * 0.14f),
                Math.round(base.getGreen() * 0.86f + accent.getGreen() * 0.14f),
                Math.round(base.getBlue() * 0.86f + accent.getBlue() * 0.14f));
        g2.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 96));
        g2.fill(shape);

        g2.setPaint(new GradientPaint(0, y, new Color(255, 255, 255, 54),
                0, y + height, new Color(255, 255, 255, 0)));
        g2.fill(shape);

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(255, 255, 255, 60));
        g2.draw(shape);
    }
}
