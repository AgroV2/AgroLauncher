package by.agro.launcher.ui.components;

import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class LogoIcon implements Icon {

    private static final double PHI = (1 + Math.sqrt(5)) / 2;

    private static final double[][] SQUARES = buildLayout();

    private final int size;
    private Color color;

    public LogoIcon(int size) {
        this(size, null);
    }

    public LogoIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }

    private static double[][] buildLayout() {
        double[] scales = {1.0, 1.0 / PHI, 1.0 / (PHI * PHI)};
        List<double[]> layout = new ArrayList<>();

        double x = 0;
        double y = 1.0;
        for (double scale : scales) {
            double side = 0.5 * scale;
            y -= side;
            layout.add(new double[]{x, y, side});
            x += side;
        }
        return layout.toArray(new double[0][]);
    }

    public void setColor(Color color) {
        this.color = color;
    }

    private Color resolveColor() {
        return color != null ? color : AgroTheme.accent();
    }

    @Override
    public int getIconWidth() {
        return size;
    }

    @Override
    public int getIconHeight() {
        return size;
    }

    @Override
    public void paintIcon(Component component, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        paint(g2, size, resolveColor());
        g2.dispose();
    }

    private static void paint(Graphics2D g2, int size, Color color) {
        g2.setColor(color);
        for (double[] square : SQUARES) {
            int left = (int) Math.round(square[0] * size);
            int top = (int) Math.round(square[1] * size);
            int side = (int) Math.round(square[2] * size);
            if (side <= 0) {
                continue;
            }
            g2.fillRect(left, top, side, side);
        }
    }

    public static BufferedImage toImage(int size, Color color) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paint(g2, size, color);
        g2.dispose();
        return image;
    }

    public static List<BufferedImage> windowIcons(Color color) {
        List<BufferedImage> icons = new ArrayList<>();
        for (int size : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            icons.add(toImage(size, color));
        }
        return icons;
    }
}
