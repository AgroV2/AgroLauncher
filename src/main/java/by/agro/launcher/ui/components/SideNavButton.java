package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.Timer;
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
    private float selectionProgress;
    private float selectionStart;
    private long selectionAnimationStarted;
    private final Timer selectionTimer;

    private final NavIcon navIcon;

    public SideNavButton(String text, NavIcon.Kind iconKind) {
        super(text);
        this.navIcon = new NavIcon(iconKind, 16, AgroTheme.textSecondary());
        selectionTimer = new Timer(15, e -> updateSelectionAnimation());
        selectionTimer.setCoalesce(true);
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
        if (this.active == active && selectionProgress == (active ? 1f : 0f)) return;
        this.active = active;
        selectionStart = selectionProgress;
        selectionAnimationStarted = System.nanoTime();
        setFont(active ? AgroTheme.boldFont(13) : AgroTheme.font(13));
        setForeground(active ? AgroTheme.textPrimary() : AgroTheme.textSecondary());
        navIcon.setColor(active ? AgroTheme.accent() : AgroTheme.textSecondary());
        if (!selectionTimer.isRunning()) selectionTimer.start();
        repaint();
    }

    private void updateSelectionAnimation() {
        float elapsed = (System.nanoTime() - selectionAnimationStarted) / 1_000_000f;
        float t = Math.min(1f, elapsed / 220f);
        float eased = 1f - (float) Math.pow(1f - t, 3);
        float target = active ? 1f : 0f;
        selectionProgress = selectionStart + (target - selectionStart) * eased;
        if (t >= 1f) {
            selectionProgress = target;
            selectionTimer.stop();
        }
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

        float backgroundProgress = Math.max(selectionProgress, hovered ? 0.35f : 0f);
        if (backgroundProgress > 0f) {
            Color base = selectionProgress > 0f ? AgroTheme.bgElevated() : AgroTheme.bgPanel();
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(),
                    Math.round(255 * backgroundProgress)));
            int arc = AgroTheme.navigationArc();
            int inset = Math.round(3 * (1f - selectionProgress));
            g2.fillRoundRect(6 + inset, 2, width - 12 - inset, height - 4, arc, arc);
        }

        if (selectionProgress > 0f) {
            int indicatorHeight = Math.max(4, Math.round((height - 16) * selectionProgress));
            int indicatorY = (height - indicatorHeight) / 2;
            g2.setPaint(AgroTheme.gradientPaint(0, indicatorY,
                    INDICATOR_WIDTH + 2, indicatorY + indicatorHeight));
            g2.fillRoundRect(0, indicatorY, INDICATOR_WIDTH + 2, indicatorHeight, 3, 3);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
