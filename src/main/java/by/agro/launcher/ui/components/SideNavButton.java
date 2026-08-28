package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class SideNavButton extends JButton {

    private static final int INDICATOR_WIDTH = 3;

    private boolean active;
    private boolean hovered;

    private final NavIcon navIcon;

    public SideNavButton(String text, NavIcon.Kind iconKind) {
        super(text);
        this.navIcon = new NavIcon(iconKind, 16, AgroTheme.textSecondary());
        setIcon(navIcon);
        setIconTextGap(12);
        setHorizontalAlignment(LEFT);
        setFont(AgroTheme.font(13));
        setForeground(AgroTheme.textSecondary());
        setBorder(BorderFactory.createEmptyBorder(11, 18, 11, 14));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        Dimension size = new Dimension(196, 42);
        setPreferredSize(size);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        setMinimumSize(new Dimension(0, 42));

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
        });
    }

    public void setActive(boolean active) {
        this.active = active;
        setFont(active ? AgroTheme.boldFont(13) : AgroTheme.font(13));
        setForeground(active ? AgroTheme.textPrimary() : AgroTheme.textSecondary());
        navIcon.setColor(active ? AgroTheme.accent() : AgroTheme.textSecondary());
        repaint();
    }

    public void refreshTheme() {
        navIcon.setColor(active ? AgroTheme.accent() : AgroTheme.textSecondary());
        setForeground(active ? AgroTheme.textPrimary() : AgroTheme.textSecondary());
        repaint();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        Color background = null;
        if (active) {
            background = AgroTheme.bgElevated();
        } else if (hovered) {
            background = AgroTheme.bgPanel();
        }
        if (background != null) {
            g2.setColor(background);
            g2.fillRoundRect(6, 2, width - 12, height - 4, 9, 9);
        }

        if (active) {
            g2.setColor(AgroTheme.accent());
            g2.fillRoundRect(0, 8, INDICATOR_WIDTH + 2, height - 16, 3, 3);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
