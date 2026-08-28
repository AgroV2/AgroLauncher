package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.ui.theme.ThemePalette;
import by.agro.launcher.ui.theme.ThemePreset;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public final class ThemePreviewCard extends JPanel {

    private static final int WIDTH = 178;
    private static final int HEIGHT = 108;

    private final ThemePreset preset;
    private ThemePalette previewPalette;

    private boolean selected;
    private boolean hovered;

    public ThemePreviewCard(ThemePreset preset, Consumer<ThemePreset> onSelect) {
        this.preset = preset;
        this.previewPalette = preset.isCustom()
                ? ThemePreset.customPalette(preset.accentColor())
                : preset.palette();

        setOpaque(false);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setToolTipText(preset.displayName() + " — " + preset.description());

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
                    onSelect.accept(preset);
                }
            }
        });
    }

    public ThemePreset preset() {
        return preset;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    public void updateCustomColor(Color accent) {
        if (preset.isCustom() && accent != null) {
            previewPalette = ThemePreset.customPalette(accent);
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int arc = 11;
        ThemePalette p = previewPalette;

        g2.setColor(p.bgBase);
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);

        int sidebarWidth = 40;
        g2.setColor(p.bgDeep);
        g2.fillRoundRect(0, 0, sidebarWidth, height - 1, arc, arc);
        g2.fillRect(sidebarWidth - arc, 0, arc, height - 1);

        g2.setColor(p.accent);
        g2.fillRoundRect(3, 16, 3, 14, 2, 2);
        g2.setColor(p.bgElevated);
        g2.fillRoundRect(9, 15, 25, 16, 4, 4);

        g2.setColor(p.textMuted);
        for (int i = 0; i < 3; i++) {
            g2.fillRoundRect(11, 38 + i * 12, 21, 4, 2, 2);
        }

        int contentX = sidebarWidth + 10;
        g2.setColor(p.bgPanel);
        g2.fillRoundRect(contentX, 14, width - contentX - 12, 34, 7, 7);
        g2.setColor(p.border);
        g2.drawRoundRect(contentX, 14, width - contentX - 12, 34, 7, 7);

        g2.setColor(p.textPrimary);
        g2.fillRoundRect(contentX + 8, 22, 52, 5, 2, 2);
        g2.setColor(p.textSecondary);
        g2.fillRoundRect(contentX + 8, 33, 74, 4, 2, 2);

        g2.setColor(p.accent);
        g2.fillRoundRect(contentX, 58, 56, 20, 6, 6);
        g2.setColor(p.textOnAccent);
        g2.fillRoundRect(contentX + 14, 66, 28, 4, 2, 2);

        g2.setColor(p.bgElevated);
        g2.fillRoundRect(contentX + 64, 58, 44, 20, 6, 6);
        g2.setColor(p.border);
        g2.drawRoundRect(contentX + 64, 58, 44, 20, 6, 6);

        g2.setColor(p.textSecondary);
        g2.setFont(AgroTheme.font(10));
        String name = preset.displayName();
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
        g2.setStroke(new java.awt.BasicStroke(strokeWidth));
        g2.setColor(borderColor);
        g2.drawRoundRect(1, 1, width - 3, height - 3, arc, arc);

        if (selected) {
            int markSize = 18;
            int markX = width - markSize - 7;
            int markY = 7;
            g2.setColor(AgroTheme.accent());
            g2.fillOval(markX, markY, markSize, markSize);
            g2.setColor(AgroTheme.textOnAccent());
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.drawLine(markX + 5, markY + 9, markX + 8, markY + 12);
            g2.drawLine(markX + 8, markY + 12, markX + 13, markY + 6);
        }

        g2.dispose();
    }
}
