package by.agro.launcher.ui.components;

import by.agro.launcher.modrinth.IconCache;
import by.agro.launcher.modrinth.ModrinthProject;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public final class ModCard extends JPanel {

    private static final int ICON_SIZE = 56;
    private static final int CARD_HEIGHT = 132;

    private final ModrinthProject project;
    private final IconCache iconCache;

    private final IconPanel iconPanel = new IconPanel();
    private final JLabel titleLabel = new JLabel();
    private final JLabel authorLabel = new JLabel();
    private final JLabel descriptionLabel = new JLabel();
    private final JLabel statsLabel = new JLabel();
    private final JLabel installedMark = new JLabel();

    private boolean hovered;
    private boolean installed;

    public ModCard(ModrinthProject project, IconCache iconCache, Consumer<ModrinthProject> onOpen) {
        this.project = project;
        this.iconCache = iconCache;

        setOpaque(false);
        setLayout(new BorderLayout(14, 0));
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(420, CARD_HEIGHT));

        buildContent();
        loadIcon();

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
                if (onOpen != null) {
                    onOpen.accept(project);
                }
            }
        });
    }

    private void buildContent() {
        iconPanel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
        JPanel iconWrapper = new JPanel(new BorderLayout());
        iconWrapper.setOpaque(false);
        iconWrapper.add(iconPanel, BorderLayout.NORTH);

        titleLabel.setText(project.title);
        titleLabel.setFont(AgroTheme.boldFont(14));
        titleLabel.setForeground(AgroTheme.textPrimary());

        authorLabel.setText(project.author == null || project.author.isBlank()
                ? "" : Strings.get("browser.byAuthor", project.author));
        authorLabel.setFont(AgroTheme.font(11));
        authorLabel.setForeground(AgroTheme.textMuted());

        descriptionLabel.setText(wrapDescription(project.description));
        descriptionLabel.setFont(AgroTheme.font(12));
        descriptionLabel.setForeground(AgroTheme.textSecondary());
        descriptionLabel.setVerticalAlignment(JLabel.TOP);

        statsLabel.setText(buildStats());
        statsLabel.setFont(AgroTheme.font(11));
        statsLabel.setForeground(AgroTheme.textMuted());

        installedMark.setFont(AgroTheme.boldFont(10));
        installedMark.setForeground(AgroTheme.accent());

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        authorLabel.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(titleLabel);
        titles.add(authorLabel);
        header.add(titles, BorderLayout.CENTER);
        header.add(installedMark, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.setOpaque(false);
        body.add(header, BorderLayout.NORTH);
        body.add(descriptionLabel, BorderLayout.CENTER);
        body.add(statsLabel, BorderLayout.SOUTH);

        add(iconWrapper, BorderLayout.WEST);
        add(body, BorderLayout.CENTER);
    }

    private String wrapDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String escaped = description
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        if (escaped.length() > 108) {
            int cut = escaped.lastIndexOf(' ', 105);
            escaped = escaped.substring(0, cut > 60 ? cut : 105) + "…";
        }
        return "<html><body style='width:270px'>" + escaped + "</body></html>";
    }

    private String buildStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("↓ ").append(project.formattedDownloads());
        var categories = project.visibleCategories();
        if (!categories.isEmpty()) {
            sb.append("   •   ");
            sb.append(String.join(", ", categories.subList(0, Math.min(3, categories.size()))));
        }
        return sb.toString();
    }

    private void loadIcon() {
        BufferedImage cached = iconCache.cached(project.id);
        if (cached != null) {
            iconPanel.setIcon(cached);
            return;
        }
        iconCache.loadAsync(project.id, project.iconUrl, image ->
                SwingUtilities.invokeLater(() -> {
                    iconPanel.setIcon(image);
                    iconPanel.repaint();
                }));
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
        installedMark.setText(installed ? Strings.get("browser.installedMark") : "");
        repaint();
    }

    public ModrinthProject project() {
        return project;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();
        int arc = 12;

        Color background = hovered ? AgroTheme.bgElevated() : AgroTheme.bgPanel();
        g2.setColor(background);
        g2.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);

        Color borderColor = hovered
                ? AgroTheme.accent()
                : (installed ? AgroTheme.accentDark() : AgroTheme.border());
        g2.setColor(borderColor);
        g2.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);

        g2.dispose();
        super.paintComponent(g);
    }

    private final class IconPanel extends JPanel {

        private BufferedImage icon;

        IconPanel() {
            setOpaque(false);
        }

        void setIcon(BufferedImage icon) {
            this.icon = icon;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int size = Math.min(getWidth(), getHeight());
            int arc = 10;

            if (icon != null) {
                java.awt.geom.RoundRectangle2D clip =
                        new java.awt.geom.RoundRectangle2D.Float(0, 0, size, size, arc, arc);
                g2.setClip(clip);
                g2.drawImage(icon, 0, 0, size, size, null);
                g2.setClip(null);
            } else {
                g2.setColor(AgroTheme.accentDeep());
                g2.fillRoundRect(0, 0, size, size, arc, arc);
                g2.setColor(AgroTheme.accentLight());
                g2.setFont(AgroTheme.boldFont(24));
                String letter = project.title.isBlank()
                        ? "?" : project.title.substring(0, 1).toUpperCase();
                var metrics = g2.getFontMetrics();
                int textX = (size - metrics.stringWidth(letter)) / 2;
                int textY = (size - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.drawString(letter, textX, textY);
            }

            g2.setColor(AgroTheme.border());
            g2.drawRoundRect(0, 0, size - 1, size - 1, arc, arc);
            g2.dispose();
        }
    }
}
