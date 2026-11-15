package by.agro.launcher.ui.components;

import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;

public final class WindowTitleBar extends JPanel {
    private final JFrame frame;
    private final JLabel title = new JLabel();
    private final ControlButton minimize = new ControlButton(Kind.MINIMIZE);
    private final ControlButton maximize = new ControlButton(Kind.MAXIMIZE);
    private final ControlButton close = new ControlButton(Kind.CLOSE);
    private Rectangle restoreBounds;

    public WindowTitleBar(JFrame frame) {
        super(new BorderLayout());
        this.frame = frame;
        setPreferredSize(new Dimension(100, 38));
        title.setFont(AgroTheme.font(12));
        title.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
        add(title, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        controls.setOpaque(false);
        controls.add(minimize);
        controls.add(maximize);
        controls.add(close);
        add(controls, BorderLayout.EAST);
        minimize.addActionListener(e -> frame.setExtendedState(frame.getExtendedState() | Frame.ICONIFIED));
        maximize.addActionListener(e -> toggleMaximize());
        close.addActionListener(e -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        frame.addWindowStateListener(e -> updateLabels());
        WindowGestures.makeDraggable(frame, this);
        WindowGestures.makeDraggable(frame, title);
        MouseAdapter doubleClick = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) toggleMaximize();
            }
        };
        addMouseListener(doubleClick);
        title.addMouseListener(doubleClick);
        updateLabels();
        refreshTheme();
    }

    public void refreshTheme() {
        setBackground(AgroTheme.bgDeep());
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AgroTheme.border()));
        title.setForeground(AgroTheme.textMuted());
        repaint();
    }

    public void updateLabels() {
        title.setText(Strings.get("app.name"));
        label(minimize, Strings.get("window.minimize"));
        label(maximize, Strings.get(WindowGestures.isMaximized(frame) ? "window.restore" : "window.maximize"));
        label(close, Strings.get("window.close"));
        maximize.repaint();
    }

    private static void label(JButton button, String text) {
        button.setToolTipText(text);
        button.getAccessibleContext().setAccessibleName(text);
    }

    private void toggleMaximize() {
        if (!frame.isResizable()) return;
        if (Boolean.TRUE.equals(frame.getRootPane().getClientProperty("agro.manualMaximized"))) {
            frame.getRootPane().putClientProperty("agro.manualMaximized", Boolean.FALSE);
            if (restoreBounds != null) frame.setBounds(restoreBounds);
            restoreBounds = null;
        } else if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
            frame.setExtendedState(frame.getExtendedState() & ~Frame.MAXIMIZED_BOTH);
        } else if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
            frame.setMaximizedBounds(workArea());
            frame.setExtendedState((frame.getExtendedState() & ~Frame.ICONIFIED) | Frame.MAXIMIZED_BOTH);
        } else {
            restoreBounds = frame.getBounds();
            frame.getRootPane().putClientProperty("agro.manualMaximized", Boolean.TRUE);
            frame.setBounds(workArea());
        }
        updateLabels();
        frame.revalidate();
        frame.repaint();
    }

    private Rectangle workArea() {
        GraphicsConfiguration configuration = frame.getGraphicsConfiguration();
        Rectangle bounds = new Rectangle(configuration.getBounds());
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        bounds.x += insets.left;
        bounds.y += insets.top;
        bounds.width -= insets.left + insets.right;
        bounds.height -= insets.top + insets.bottom;
        return bounds;
    }

    private enum Kind { MINIMIZE, MAXIMIZE, CLOSE }

    private final class ControlButton extends JButton {
        private final Kind kind;

        ControlButton(Kind kind) {
            this.kind = kind;
            setName("window." + kind.name().toLowerCase(java.util.Locale.ROOT));
            setPreferredSize(new Dimension(46, 37));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(kind == Kind.CLOSE ? new Color(0xd83b3b) : AgroTheme.bgHover());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.setColor(kind == Kind.CLOSE && (getModel().isRollover() || getModel().isPressed())
                        ? Color.WHITE : AgroTheme.textPrimary());
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int x = getWidth() / 2;
                int y = getHeight() / 2;
                if (kind == Kind.MINIMIZE) {
                    g2.drawLine(x - 5, y + 3, x + 5, y + 3);
                } else if (kind == Kind.CLOSE) {
                    g2.drawLine(x - 5, y - 5, x + 5, y + 5);
                    g2.drawLine(x + 5, y - 5, x - 5, y + 5);
                } else if (WindowGestures.isMaximized(frame)) {
                    g2.drawRect(x - 4, y - 3, 8, 8);
                    g2.drawLine(x - 1, y - 6, x + 6, y - 6);
                    g2.drawLine(x + 6, y - 6, x + 6, y + 1);
                } else {
                    g2.drawRect(x - 5, y - 5, 10, 10);
                }
                if (hasFocus()) {
                    g2.setColor(AgroTheme.accent());
                    g2.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 5, 5);
                }
            } finally {
                g2.dispose();
            }
        }
    }
}
