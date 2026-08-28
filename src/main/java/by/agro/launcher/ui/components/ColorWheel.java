package by.agro.launcher.ui.components;

import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.ui.theme.PaletteFactory;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;

public final class ColorWheel extends JPanel {

    private static final int WHEEL_SIZE = 190;

    private final WheelCanvas canvas;
    private final JSlider brightnessSlider;
    private final JTextField hexField;
    private final JPanel previewSwatch;

    private float hue = 0.42f;
    private float saturation = 0.86f;
    private float brightness = 0.73f;

    private Consumer<Color> changeListener;
    private Consumer<Color> changeFinishedListener;
    private boolean updatingHexField;

    public ColorWheel(Color initialColor) {
        setOpaque(false);
        setLayout(new BorderLayout(16, 10));

        if (initialColor != null) {
            float[] hsb = Color.RGBtoHSB(
                    initialColor.getRed(), initialColor.getGreen(), initialColor.getBlue(), null);
            hue = hsb[0];
            saturation = hsb[1];
            brightness = Math.max(0.25f, hsb[2]);
        }

        canvas = new WheelCanvas();
        canvas.setPreferredSize(new Dimension(WHEEL_SIZE, WHEEL_SIZE));
        canvas.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

        brightnessSlider = new JSlider(15, 100, Math.round(brightness * 100));
        brightnessSlider.setOpaque(false);
        brightnessSlider.setPreferredSize(new Dimension(180, 30));
        brightnessSlider.addChangeListener(e -> {
            brightness = brightnessSlider.getValue() / 100f;
            canvas.repaint();
            fireChange();
            if (!brightnessSlider.getValueIsAdjusting() && changeFinishedListener != null) {
                Color finalColor = color();
                javax.swing.SwingUtilities.invokeLater(() -> changeFinishedListener.accept(finalColor));
            }
        });

        hexField = new JTextField(PaletteFactory.toHex(color()));
        hexField.setPreferredSize(new Dimension(96, 30));
        hexField.setFont(AgroTheme.monoFont(12));
        hexField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onHexEdited();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onHexEdited();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onHexEdited();
            }
        });

        previewSwatch = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(AgroTheme.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        previewSwatch.setPreferredSize(new Dimension(46, 30));
        previewSwatch.setOpaque(false);

        add(canvas, BorderLayout.WEST);
        add(buildControls(), BorderLayout.CENTER);
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JLabel brightnessLabel = new JLabel(Strings.get("appearance.brightness"));
        brightnessLabel.setFont(AgroTheme.boldFont(12));
        brightnessLabel.setForeground(AgroTheme.textSecondary());
        brightnessLabel.setAlignmentX(LEFT_ALIGNMENT);

        brightnessSlider.setAlignmentX(LEFT_ALIGNMENT);

        JLabel hexLabel = new JLabel(Strings.get("appearance.colorCode"));
        hexLabel.setFont(AgroTheme.boldFont(12));
        hexLabel.setForeground(AgroTheme.textSecondary());
        hexLabel.setAlignmentX(LEFT_ALIGNMENT);

        JPanel hexRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        hexRow.setOpaque(false);
        hexRow.setAlignmentX(LEFT_ALIGNMENT);
        hexRow.add(hexField);
        hexRow.add(previewSwatch);

        JLabel hint = new JLabel(Strings.get("appearance.wheelHint"));
        hint.setFont(AgroTheme.font(11));
        hint.setForeground(AgroTheme.textMuted());
        hint.setAlignmentX(LEFT_ALIGNMENT);

        controls.add(brightnessLabel);
        controls.add(brightnessSlider);
        controls.add(javax.swing.Box.createRigidArea(new Dimension(0, 12)));
        controls.add(hexLabel);
        controls.add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
        controls.add(hexRow);
        controls.add(javax.swing.Box.createRigidArea(new Dimension(0, 8)));
        controls.add(hint);
        controls.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        return controls;
    }

    public Color color() {
        return Color.getHSBColor(hue, saturation, brightness);
    }

    public void setColor(Color color) {
        if (color == null) {
            return;
        }
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = Math.max(0.15f, hsb[2]);
        brightnessSlider.setValue(Math.round(brightness * 100));
        updateHexField();
        canvas.repaint();
        previewSwatch.repaint();
    }

    public void onChange(Consumer<Color> listener) {
        this.changeListener = listener;
    }

    public void onChangeFinished(Consumer<Color> listener) {
        this.changeFinishedListener = listener;
    }

    private void onHexEdited() {
        if (updatingHexField) {
            return;
        }
        Color parsed = PaletteFactory.parseHex(hexField.getText());
        if (parsed == null) {
            return;
        }
        float[] hsb = Color.RGBtoHSB(parsed.getRed(), parsed.getGreen(), parsed.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = Math.max(0.15f, hsb[2]);
        brightnessSlider.setValue(Math.round(brightness * 100));
        canvas.repaint();
        previewSwatch.repaint();
        if (changeListener != null) {
            changeListener.accept(color());
        }
    }

    private void updateHexField() {
        updatingHexField = true;
        try {
            hexField.setText(PaletteFactory.toHex(color()));
        } finally {
            updatingHexField = false;
        }
    }

    private void fireChange() {
        updateHexField();
        previewSwatch.repaint();
        if (changeListener != null) {
            changeListener.accept(color());
        }
    }

    private final class WheelCanvas extends JPanel {

        private BufferedImage cache;
        private float cachedBrightness = -1;
        private int cachedSize = -1;

        WheelCanvas() {
            setOpaque(false);
            MouseAdapter picker = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    pick(e.getPoint());
                }
            };
            addMouseListener(picker);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    pick(e.getPoint());
                }
            });
        }

        private void pick(Point point) {
            int size = Math.min(getWidth(), getHeight());
            int radius = size / 2;
            double dx = point.x - radius;
            double dy = point.y - radius;
            double distance = Math.sqrt(dx * dx + dy * dy);

            double normalized = Math.min(1.0, distance / radius);

            double angle = Math.atan2(dy, dx);
            if (angle < 0) {
                angle += 2 * Math.PI;
            }

            hue = (float) (angle / (2 * Math.PI));
            saturation = (float) normalized;
            repaint();
            fireChange();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int size = Math.min(getWidth(), getHeight());
            if (size <= 0) {
                return;
            }
            if (cache == null || cachedBrightness != brightness || cachedSize != size) {
                cache = renderWheel(size, brightness);
                cachedBrightness = brightness;
                cachedSize = size;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.drawImage(cache, 0, 0, null);

            int radius = size / 2;
            double angle = hue * 2 * Math.PI;
            double distance = saturation * radius;
            int markerX = (int) Math.round(radius + Math.cos(angle) * distance);
            int markerY = (int) Math.round(radius + Math.sin(angle) * distance);

            g2.setStroke(new BasicStroke(2.4f));
            g2.setColor(Color.WHITE);
            g2.drawOval(markerX - 7, markerY - 7, 14, 14);
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(0, 0, 0, 140));
            g2.drawOval(markerX - 8, markerY - 8, 16, 16);

            g2.dispose();
        }

        private BufferedImage renderWheel(int size, float value) {
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            int radius = size / 2;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double dx = x - radius;
                    double dy = y - radius;
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    if (distance > radius) {
                        continue;
                    }
                    double angle = Math.atan2(dy, dx);
                    if (angle < 0) {
                        angle += 2 * Math.PI;
                    }
                    float h = (float) (angle / (2 * Math.PI));
                    float s = (float) (distance / radius);
                    int rgb = Color.HSBtoRGB(h, s, value);

                    int alpha = 255;
                    double edge = radius - distance;
                    if (edge < 1.5) {
                        alpha = (int) (255 * Math.max(0, edge / 1.5));
                    }
                    image.setRGB(x, y, (alpha << 24) | (rgb & 0x00FFFFFF));
                }
            }
            return image;
        }
    }
}
