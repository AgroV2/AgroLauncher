package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class AccentButton extends JButton {

    private boolean hovered;
    private boolean pressed;

    public AccentButton(String text) {
        super(text);
        setFont(AgroTheme.boldFont(14));
        setForeground(AgroTheme.textOnAccent());
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                pressed = false;
                repaint();
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int arc = 11;
        Color background;
        if (!isEnabled()) {
            background = AgroTheme.bgHover();
        } else if (pressed) {
            background = AgroTheme.accentDark();
        } else if (hovered) {
            background = AgroTheme.accentLight();
        } else {
            background = AgroTheme.accent();
        }

        if (isEnabled() && hovered) {
            g2.setColor(AgroTheme.accentGlow());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc + 4, arc + 4);
        }

        g2.setColor(background);
        g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);

        setForeground(isEnabled() ? AgroTheme.textOnAccent() : AgroTheme.textMuted());

        g2.dispose();
        super.paintComponent(g);
    }
}
