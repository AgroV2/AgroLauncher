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

    private static final class TranslucentCard extends JPanel {
        TranslucentCard() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(cardBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(AgroTheme.border());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
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
