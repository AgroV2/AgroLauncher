package by.agro.launcher.ui.components;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

public final class NavIcon implements Icon {

    public enum Kind {
        PLAY,
        VERSIONS,
        ACCOUNTS,
        MODS,
        SETTINGS
    }

    private final Kind kind;
    private final int size;
    private Color color;

    public NavIcon(Kind kind, int size, Color color) {
        this.kind = kind;
        this.size = size;
        this.color = color;
    }

    public void setColor(Color color) {
        this.color = color;
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
        g2.setColor(color);

        float stroke = Math.max(1.4f, size / 11f);
        g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (kind) {
            case PLAY:
                paintPlay(g2);
                break;
            case VERSIONS:
                paintVersions(g2);
                break;
            case ACCOUNTS:
                paintAccount(g2);
                break;
            case MODS:
                paintMods(g2);
                break;
            case SETTINGS:
                paintSettings(g2);
                break;
            default:
                break;
        }
        g2.dispose();
    }

    private void paintPlay(Graphics2D g2) {
        float inset = size * 0.18f;
        GeneralPath triangle = new GeneralPath(Path2D.WIND_NON_ZERO);
        triangle.moveTo(inset + size * 0.06f, inset);
        triangle.lineTo(size - inset, size / 2f);
        triangle.lineTo(inset + size * 0.06f, size - inset);
        triangle.closePath();
        g2.fill(triangle);
    }

    private void paintVersions(Graphics2D g2) {
        float width = size * 0.72f;
        float height = size * 0.17f;
        float left = (size - width) / 2f;
        for (int i = 0; i < 3; i++) {
            float top = size * 0.18f + i * (height + size * 0.09f);
            RoundRectangle2D layer = new RoundRectangle2D.Float(
                    left, top, width, height, height * 0.7f, height * 0.7f);
            if (i == 0) {
                g2.fill(layer);
            } else {
                g2.draw(layer);
            }
        }
    }

    private void paintAccount(Graphics2D g2) {
        float headDiameter = size * 0.34f;
        float headX = (size - headDiameter) / 2f;
        float headY = size * 0.14f;
        g2.fill(new Ellipse2D.Float(headX, headY, headDiameter, headDiameter));

        float bodyWidth = size * 0.62f;
        float bodyHeight = size * 0.40f;
        float bodyX = (size - bodyWidth) / 2f;
        float bodyY = size * 0.55f;
        java.awt.geom.Arc2D shoulders = new java.awt.geom.Arc2D.Float(
                bodyX, bodyY, bodyWidth, bodyHeight * 1.6f, 0, 180, java.awt.geom.Arc2D.CHORD);
        g2.fill(shoulders);
    }

    private void paintMods(Graphics2D g2) {
        float center = size / 2f;
        float radius = size * 0.36f;
        float half = radius * 0.866f;
        float quarter = radius / 2f;

        GeneralPath top = new GeneralPath(Path2D.WIND_NON_ZERO);
        top.moveTo(center, center - radius);
        top.lineTo(center + half, center - quarter);
        top.lineTo(center, center + quarter * 0.18f);
        top.lineTo(center - half, center - quarter);
        top.closePath();
        g2.fill(top);

        GeneralPath left = new GeneralPath(Path2D.WIND_NON_ZERO);
        left.moveTo(center - half, center - quarter);
        left.lineTo(center, center + quarter * 0.18f);
        left.lineTo(center, center + radius);
        left.lineTo(center - half, center + quarter * 0.72f);
        left.closePath();
        g2.draw(left);

        GeneralPath right = new GeneralPath(Path2D.WIND_NON_ZERO);
        right.moveTo(center + half, center - quarter);
        right.lineTo(center, center + quarter * 0.18f);
        right.lineTo(center, center + radius);
        right.lineTo(center + half, center + quarter * 0.72f);
        right.closePath();
        g2.draw(right);
    }

    private void paintSettings(Graphics2D g2) {
        float center = size / 2f;
        float outer = size * 0.42f;
        float inner = size * 0.27f;
        int teeth = 8;

        GeneralPath gear = new GeneralPath(Path2D.WIND_EVEN_ODD);
        for (int i = 0; i < teeth * 2; i++) {
            double angle = Math.PI * i / teeth - Math.PI / 2;
            float radius = (i % 2 == 0) ? outer : inner;
            float px = center + (float) (Math.cos(angle) * radius);
            float py = center + (float) (Math.sin(angle) * radius);
            if (i == 0) {
                gear.moveTo(px, py);
            } else {
                gear.lineTo(px, py);
            }
        }
        gear.closePath();

        float holeRadius = size * 0.11f;
        gear.append(new Ellipse2D.Float(center - holeRadius, center - holeRadius,
                holeRadius * 2, holeRadius * 2), false);
        g2.fill(gear);
    }
}
