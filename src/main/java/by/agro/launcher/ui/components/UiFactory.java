package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;

public final class UiFactory {

    private UiFactory() {
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AgroTheme.boldFont(22));
        label.setForeground(AgroTheme.textPrimary());
        return label;
    }

    public static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AgroTheme.font(13));
        label.setForeground(AgroTheme.textSecondary());
        return label;
    }

    public static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AgroTheme.boldFont(12));
        label.setForeground(AgroTheme.textSecondary());
        return label;
    }

    public static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AgroTheme.font(11));
        label.setForeground(AgroTheme.textMuted());
        return label;
    }

    public static JPanel card() {
        JPanel panel = new TranslucentCard();
        panel.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        return panel;
    }

    private static volatile boolean liveResize;

    public static void setLiveResize(boolean active) {
        liveResize = active;
    }

    private static java.awt.Color mix(java.awt.Color first, java.awt.Color second, float secondWeight) {
        float weight = Math.max(0f, Math.min(1f, secondWeight));
        float firstWeight = 1f - weight;
        return new java.awt.Color(
                Math.round(first.getRed() * firstWeight + second.getRed() * weight),
                Math.round(first.getGreen() * firstWeight + second.getGreen() * weight),
                Math.round(first.getBlue() * firstWeight + second.getBlue() * weight));
    }

    private static final class TranslucentCard extends JPanel {
        private static final java.awt.BasicStroke GLASS_STROKE = new java.awt.BasicStroke(1f);


        private int cacheWidth = -1;
        private int cacheHeight = -1;
        private int cacheKeyBg;
        private int cacheKeyAccent;
        private int cacheKeyOpacity = -1;
        private boolean cacheKeyFast;
        private java.awt.Paint glassPaint;
        private java.awt.Color glassEdge;
        private boolean glassFast;

        TranslucentCard() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = AgroTheme.cardArc();
            if (AgroTheme.isLiquidGlass()) {
                paintLiquidGlass(g2, arc);
            } else {
                g2.setColor(cardBackground());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.setColor(AgroTheme.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        private void paintLiquidGlass(java.awt.Graphics2D g2, int arc) {
            int width = getWidth();
            int height = getHeight();
            prepareGlassCache(width, height);

            g2.setPaint(glassPaint);
            g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);

            if (!glassFast) {
                g2.setStroke(GLASS_STROKE);
                g2.setColor(glassEdge);
                g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            }
        }

        private void prepareGlassCache(int width, int height) {
            boolean fast = liveResize;
            int bgKey = AgroTheme.bgPanel().getRGB();
            int accentKey = AgroTheme.accent().getRGB();
            if (width == cacheWidth && height == cacheHeight && bgKey == cacheKeyBg
                    && accentKey == cacheKeyAccent && cardOpacityPercent == cacheKeyOpacity
                    && fast == cacheKeyFast) {
                return;
            }
            cacheWidth = width;
            cacheHeight = height;
            cacheKeyBg = bgKey;
            cacheKeyAccent = accentKey;
            cacheKeyOpacity = cardOpacityPercent;
            cacheKeyFast = fast;
            glassFast = fast;

            java.awt.Color base = AgroTheme.bgPanel();
            java.awt.Color accent = AgroTheme.accent();
            java.awt.Color tint = mix(base, accent, 0.14f);
            int alpha = Math.min(200, Math.max(52,
                    Math.round(255 * cardOpacityPercent / 100f * 0.6f)));
            java.awt.Color bottom = new java.awt.Color(
                    tint.getRed(), tint.getGreen(), tint.getBlue(), alpha);
            if (fast) {
                glassPaint = bottom;
                glassEdge = null;
                return;
            }
            java.awt.Color topTint = mix(tint, java.awt.Color.WHITE, 0.22f);
            java.awt.Color top = new java.awt.Color(
                    topTint.getRed(), topTint.getGreen(), topTint.getBlue(),
                    Math.min(255, alpha + 42));
            glassPaint = new java.awt.GradientPaint(0, 0, top, 0, Math.max(1, height), bottom);
            java.awt.Color edge = mix(AgroTheme.border(), accent, 0.30f);
            glassEdge = new java.awt.Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 150);
        }
    }

    private static java.awt.Color cardBackground() {
        java.awt.Color base = AgroTheme.bgPanel();
        int alpha = Math.round(255 * cardOpacityPercent / 100f);
        return new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue(),
                Math.max(60, Math.min(255, alpha)));
    }

    private static int cardOpacityPercent = 100;

    public static void setCardOpacityPercent(int percent) {
        cardOpacityPercent = Math.max(40, Math.min(100, percent));
    }

    public static JPanel titledCard(String titleText) {
        JPanel card = card();
        card.setLayout(new BorderLayout(0, 14));
        JLabel header = new JLabel(titleText);
        header.setFont(AgroTheme.boldFont(15));
        header.setForeground(AgroTheme.textPrimary());
        card.add(header, BorderLayout.NORTH);
        return card;
    }

    public static JButton primaryButton(String text) {
        return new AccentButton(text);
    }

    public static JButton button(String text) {
        JButton button = new JButton(text);
        button.setFont(AgroTheme.font(13));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        return button;
    }

    public static JButton linkButton(String text) {
        JButton button = new JButton(text) {
            @Override
            public java.awt.Color getForeground() {
                return AgroTheme.accentLight();
            }
        };
        button.setFont(AgroTheme.font(12));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    public static JButton dangerButton(String text) {
        JButton button = new JButton(text) {
            @Override
            public java.awt.Color getForeground() {
                return AgroTheme.error();
            }
        };
        button.setFont(AgroTheme.font(13));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setFocusPainted(false);
        return button;
    }

    public static JComponent separator() {
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setForeground(AgroTheme.border());
        separator.setBackground(AgroTheme.border());
        return separator;
    }

    public static JScrollPane scroll(Component content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    public static Component verticalGap(int height) {
        return Box.createRigidArea(new Dimension(0, height));
    }

    public static Component horizontalGap(int width) {
        return Box.createRigidArea(new Dimension(width, 0));
    }

    public static JPanel transparentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    public static JPanel basePanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    public static GridBagConstraints gbc(int x, int y, int width, double weightX) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.weightx = weightX;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 0, 5, 10);
        return c;
    }

    public static JLabel badge(String text, java.awt.Color background, java.awt.Color foreground) {
        JLabel label = new JLabel(text);
        label.setFont(AgroTheme.boldFont(10));
        label.setForeground(foreground);
        label.setBackground(background);
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        return label;
    }

    public static JLabel accentBadge(String text) {
        return badge(text, AgroTheme.accentDeep(), AgroTheme.accentLight());
    }

    public static void fixHeight(JComponent component, int height) {
        component.setPreferredSize(new Dimension(component.getPreferredSize().width, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setMinimumSize(new Dimension(0, height));
    }

    public static Font mono(int size) {
        return AgroTheme.monoFont(size);
    }
}
