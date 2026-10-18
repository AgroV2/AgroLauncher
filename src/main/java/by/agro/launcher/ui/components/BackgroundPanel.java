package by.agro.launcher.ui.components;

import by.agro.launcher.core.Settings;
import by.agro.launcher.ui.background.BackgroundManager;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.ImageIcon;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.image.BufferedImage;

public final class BackgroundPanel extends JPanel {

    private final BackgroundManager backgroundManager;
    private final Settings settings;

    public BackgroundPanel(LayoutManager layout, BackgroundManager backgroundManager, Settings settings) {
        super(layout);
        this.backgroundManager = backgroundManager;
        this.settings = settings;
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        int width = getWidth();
        int height = getHeight();

        g.setColor(AgroTheme.bgBase());
        g.fillRect(0, 0, width, height);

        if (!backgroundManager.hasBackground()) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        ImageIcon animated = backgroundManager.animatedIcon(settings);
        if (animated != null) {
            drawCovering(g2, animated, width, height);
        } else {
            BufferedImage rendered = backgroundManager.render(width, height, settings);
            if (rendered != null) {
                g2.drawImage(rendered, 0, 0, null);
            }
        }

        int dim = settings.backgroundDimPercent;
        if (dim > 0) {
            int alpha = Math.round(255 * dim / 100f);
            Color base = AgroTheme.bgBase();
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
            g2.fillRect(0, 0, width, height);
        }

        g2.dispose();
    }

    private void drawCovering(Graphics2D g2, ImageIcon icon, int width, int height) {
        int imageWidth = icon.getIconWidth();
        int imageHeight = icon.getIconHeight();
        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        double scale = Math.max(width / (double) imageWidth, height / (double) imageHeight);
        int scaledWidth = (int) Math.ceil(imageWidth * scale);
        int scaledHeight = (int) Math.ceil(imageHeight * scale);
        int x = (width - scaledWidth) / 2;
        int y = (height - scaledHeight) / 2;

        g2.drawImage(icon.getImage(), x, y, scaledWidth, scaledHeight, this);
    }

    public static Color panelBackground(Settings settings, boolean hasBackground) {
        Color base = AgroTheme.bgPanel();
        if (!hasBackground) {
            return base;
        }
        int alpha = Math.round(255 * settings.panelOpacityPercent / 100f);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }
}
