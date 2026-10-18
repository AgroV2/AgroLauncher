package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.ui.theme.GradientPreset;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public final class GradientPreviewCard extends JPanel {
    private static final int WIDTH = 178;
    private static final int HEIGHT = 108;

    private final GradientPreset preset;
    private final Consumer<GradientPreset> onSelect;
    private boolean selected;
    private boolean hovered;

    public GradientPreviewCard(GradientPreset preset, Consumer<GradientPreset> onSelect) {
        this.preset = preset;
        this.onSelect = onSelect;
        setOpaque(false);
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setMinimumSize(new Dimension(WIDTH, HEIGHT));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setToolTipText(preset.displayName() + " — " + preset.description());
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) {
                if (GradientPreviewCard.this.onSelect != null) {
                    GradientPreviewCard.this.onSelect.accept(GradientPreviewCard.this.preset);
                }
            }
        });
    }

    public GradientPreset preset() { return preset; }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int arc = 11;
        g2.setColor(AgroTheme.bgBase());
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);

        int sidebarWidth = 40;
        g2.setColor(AgroTheme.bgDeep());
        g2.fillRoundRect(0, 0, sidebarWidth, height - 1, arc, arc);
        g2.fillRect(sidebarWidth - arc, 0, arc, height - 1);

        Color[] colors = preset.colors(null);
        if (colors.length >= 2) {
            float[] fractions = new float[colors.length];
            for (int i = 0; i < colors.length; i++) fractions[i] = (float) i / (colors.length - 1);
            g2.setPaint(new LinearGradientPaint(3, 16, 6, 30, fractions, colors));
        } else {
            g2.setColor(AgroTheme.accent());
        }
        g2.fillRoundRect(3, 16, 3, 14, 2, 2);
        g2.setColor(AgroTheme.bgElevated());
        g2.fillRoundRect(9, 15, 25, 16, 4, 4);

        g2.setColor(AgroTheme.textMuted());
        for (int i = 0; i < 3; i++) {
            g2.fillRoundRect(11, 38 + i * 12, 21, 4, 2, 2);
        }

        int contentX = sidebarWidth + 10;
        g2.setColor(AgroTheme.bgPanel());
        g2.fillRoundRect(contentX, 14, width - contentX - 12, 34, 7, 7);
        g2.setColor(AgroTheme.border());
        g2.drawRoundRect(contentX, 14, width - contentX - 12, 34, 7, 7);

        g2.setColor(AgroTheme.textPrimary());
        g2.fillRoundRect(contentX + 8, 22, 52, 5, 2, 2);
        g2.setColor(AgroTheme.textSecondary());
        g2.fillRoundRect(contentX + 8, 33, 74, 4, 2, 2);

        if (colors.length >= 2) {
            float[] fractions = new float[colors.length];
            for (int i = 0; i < colors.length; i++) fractions[i] = (float) i / (colors.length - 1);
            g2.setPaint(new LinearGradientPaint(contentX, 58, contentX + 56, 78, fractions, colors));
        } else {
            g2.setColor(AgroTheme.accent());
        }
        g2.fillRoundRect(contentX, 58, 56, 20, 6, 6);
        g2.setColor(AgroTheme.textOnAccent());
        g2.fillRoundRect(contentX + 14, 66, 28, 4, 2, 2);

        g2.setColor(AgroTheme.bgElevated());
        g2.fillRoundRect(contentX + 64, 58, 44, 20, 6, 6);
        g2.setColor(AgroTheme.border());
        g2.drawRoundRect(contentX + 64, 58, 44, 20, 6, 6);

        g2.setFont(AgroTheme.font(10));
        g2.setColor(AgroTheme.textSecondary());
        FontMetrics metrics = g2.getFontMetrics();
        String name = preset.displayName();
        int maxNameWidth = width - contentX - 14;
        if (metrics.stringWidth(name) > maxNameWidth) {
            while (metrics.stringWidth(name + "…") > maxNameWidth && name.length() > 4) {
                name = name.substring(0, name.length() - 2);
            }
            name += "…";
        }
        g2.drawString(name, contentX, height - 8);

        Color border;
        float strokeWidth;
        if (selected) {
            border = AgroTheme.accent();
            strokeWidth = 2.4f;
        } else if (hovered) {
            border = AgroTheme.borderLight();
            strokeWidth = 1.6f;
        } else {
            border = AgroTheme.border();
            strokeWidth = 1f;
        }
        g2.setColor(border);
        g2.setStroke(new java.awt.BasicStroke(strokeWidth));
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
