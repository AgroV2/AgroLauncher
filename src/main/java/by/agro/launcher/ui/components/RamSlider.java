package by.agro.launcher.ui.components;

import by.agro.launcher.core.SystemInfo;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.function.IntConsumer;

public final class RamSlider extends JPanel {

    private static final int STEP_MB = 512;
    private static final int MIN_MB = 512;

    private final JSlider slider;
    private final JLabel valueLabel;
    private final JLabel warningLabel;
    private IntConsumer changeListener;

    public RamSlider(int initialValueMb) {
        setOpaque(false);
        setLayout(new BorderLayout(0, 6));

        int maxMb = alignToStep(SystemInfo.ramSliderMaxMb());
        int value = clamp(alignToStep(initialValueMb), MIN_MB, maxMb);

        slider = new JSlider(MIN_MB / STEP_MB, maxMb / STEP_MB, value / STEP_MB);
        slider.setOpaque(false);
        slider.setPaintTicks(true);
        slider.setSnapToTicks(true);
        slider.setMajorTickSpacing(8);
        slider.setMinorTickSpacing(2);
        slider.setPreferredSize(new Dimension(320, 38));

        valueLabel = new JLabel();
        valueLabel.setFont(AgroTheme.boldFont(20));
        valueLabel.setForeground(AgroTheme.accentLight());

        warningLabel = new JLabel();
        warningLabel.setFont(AgroTheme.font(11));
        warningLabel.setForeground(AgroTheme.warning());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(valueLabel, BorderLayout.WEST);

        JPanel limits = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        limits.setOpaque(false);
        long total = SystemInfo.totalRamMb();
        JLabel totalLabel = UiFactory.hint(total > 0
                ? Strings.get("settings.ramTotal", SystemInfo.formatMb((int) total))
                : Strings.get("settings.ramUnknown"));
        limits.add(totalLabel);
        header.add(limits, BorderLayout.EAST);

        JPanel bounds = new JPanel(new BorderLayout());
        bounds.setOpaque(false);
        bounds.add(UiFactory.hint(SystemInfo.formatMb(MIN_MB)), BorderLayout.WEST);
        bounds.add(UiFactory.hint(SystemInfo.formatMb(maxMb)), BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 2));
        bottom.setOpaque(false);
        bottom.add(bounds, BorderLayout.NORTH);
        bottom.add(warningLabel, BorderLayout.SOUTH);

        add(header, BorderLayout.NORTH);
        add(slider, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        slider.addChangeListener(e -> {
            updateLabels();
            if (changeListener != null && !slider.getValueIsAdjusting()) {
                changeListener.accept(getValueMb());
            }
        });
        updateLabels();
    }

    private void updateLabels() {
        int mb = getValueMb();
        valueLabel.setText(SystemInfo.formatMb(mb));

        long total = SystemInfo.totalRamMb();
        if (total > 0 && mb > total * 0.8) {
            warningLabel.setText(Strings.get("settings.ramTooMuch"));
        } else if (mb < 2048) {
            warningLabel.setText(Strings.get("settings.ramTooLittle"));
        } else {
            warningLabel.setText(" ");
        }
    }

    public int getValueMb() {
        return slider.getValue() * STEP_MB;
    }

    public void setValueMb(int mb) {
        slider.setValue(clamp(alignToStep(mb), MIN_MB, slider.getMaximum() * STEP_MB) / STEP_MB);
    }

    public void onChange(IntConsumer listener) {
        this.changeListener = listener;
    }

    private static int alignToStep(int mb) {
        return Math.max(STEP_MB, (mb / STEP_MB) * STEP_MB);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
