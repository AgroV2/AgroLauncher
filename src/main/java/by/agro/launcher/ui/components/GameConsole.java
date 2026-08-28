package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;

public final class GameConsole extends JPanel {

    private static final int MAX_LINES = 5000;

    private final JTextPane textPane;
    private final JScrollPane scrollPane;
    private final StyledDocument document;

    private final SimpleAttributeSet styleDefault = new SimpleAttributeSet();
    private final SimpleAttributeSet styleError = new SimpleAttributeSet();
    private final SimpleAttributeSet styleWarning = new SimpleAttributeSet();
    private final SimpleAttributeSet styleInfo = new SimpleAttributeSet();
    private final SimpleAttributeSet styleLauncher = new SimpleAttributeSet();
    private final SimpleAttributeSet styleMuted = new SimpleAttributeSet();

    private boolean autoScroll = true;

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        Color base = AgroTheme.bgDeep();
        g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 225));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
        g2.dispose();
        super.paintComponent(g);
    }

    public GameConsole() {
        setLayout(new BorderLayout());
        setOpaque(false);

        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setOpaque(false);
        textPane.setBackground(new Color(0, 0, 0, 0));
        textPane.setForeground(AgroTheme.textSecondary());
        textPane.setFont(AgroTheme.monoFont(12));
        textPane.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        document = textPane.getStyledDocument();

        initStyles();

        scrollPane = new JScrollPane(textPane);
        scrollPane.setBorder(BorderFactory.createLineBorder(AgroTheme.border(), 1, true));
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        AgroTheme.addThemeListener(palette -> refreshTheme());
    }

    private void initStyles() {
        StyleConstants.setForeground(styleDefault, AgroTheme.textSecondary());
        StyleConstants.setForeground(styleError, AgroTheme.error());
        StyleConstants.setForeground(styleWarning, AgroTheme.warning());
        StyleConstants.setForeground(styleInfo, AgroTheme.info());
        StyleConstants.setForeground(styleLauncher, AgroTheme.accentLight());
        StyleConstants.setForeground(styleMuted, AgroTheme.textMuted());
        StyleConstants.setBold(styleLauncher, true);
    }

    private void refreshTheme() {
        initStyles();
        textPane.setForeground(AgroTheme.textSecondary());
        scrollPane.setBorder(BorderFactory.createLineBorder(AgroTheme.border(), 1, true));
        javax.swing.text.Element root = document.getDefaultRootElement();
        for (int i = 0; i < root.getElementCount(); i++) {
            javax.swing.text.Element line = root.getElement(i);
            int start = line.getStartOffset();
            int length = Math.min(line.getEndOffset(), document.getLength()) - start;
            if (length <= 0) {
                continue;
            }
            try {
                String text = document.getText(start, length);
                document.setCharacterAttributes(start, length, styleFor(text), true);
            } catch (BadLocationException ignored) {
            }
        }
        revalidate();
        repaint();
    }

    public void append(String line) {
        if (line == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> appendInternal(line));
    }

    public void appendLauncherMessage(String message) {
        SwingUtilities.invokeLater(() -> appendWithStyle("[лаунчер] " + message, styleLauncher));
    }

    private void appendInternal(String line) {
        appendWithStyle(line, styleFor(line));
    }

    private void appendWithStyle(String line, SimpleAttributeSet style) {
        try {
            document.insertString(document.getLength(), line + "\n", style);
            trimIfNeeded();
            if (autoScroll) {
                textPane.setCaretPosition(document.getLength());
            }
        } catch (BadLocationException ignored) {
        }
    }

    private SimpleAttributeSet styleFor(String line) {
        String upper = line.toUpperCase();
        if (upper.contains("/ERROR") || upper.contains("[ERROR]") || upper.contains("SEVERE")
                || upper.contains("EXCEPTION") || upper.startsWith("\tAT ")
                || upper.contains("CAUSED BY")) {
            return styleError;
        }
        if (upper.contains("/WARN") || upper.contains("[WARN]") || upper.contains("WARNING")) {
            return styleWarning;
        }
        if (upper.contains("/INFO") || upper.contains("[INFO]")) {
            return styleInfo;
        }
        if (line.startsWith("[лаунчер]") || line.startsWith("[installer]") || line.startsWith("[optifine]")) {
            return styleLauncher;
        }
        if (upper.contains("/DEBUG") || upper.contains("[DEBUG]") || upper.contains("/TRACE")) {
            return styleMuted;
        }
        return styleDefault;
    }

    private void trimIfNeeded() throws BadLocationException {
        javax.swing.text.Element root = document.getDefaultRootElement();
        int lines = root.getElementCount();
        if (lines <= MAX_LINES) {
            return;
        }
        int excess = lines - MAX_LINES;
        javax.swing.text.Element last = root.getElement(excess - 1);
        document.remove(0, last.getEndOffset());
    }

    public void clear() {
        SwingUtilities.invokeLater(() -> {
            try {
                document.remove(0, document.getLength());
            } catch (BadLocationException ignored) {
            }
        });
    }

    public void setAutoScroll(boolean autoScroll) {
        this.autoScroll = autoScroll;
    }

    public boolean isAutoScroll() {
        return autoScroll;
    }

    public String text() {
        try {
            return document.getText(0, document.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }
}
