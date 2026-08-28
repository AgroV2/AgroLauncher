package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.i18n.Language;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.background.BackgroundManager;
import by.agro.launcher.ui.components.ColorWheel;
import by.agro.launcher.ui.components.ThemePreviewCard;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.ui.theme.PaletteFactory;
import by.agro.launcher.ui.theme.ThemePreset;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class AppearancePanel extends JPanel {

    private final LauncherContext context;
    private final BackgroundManager backgroundManager;
    private final Consumer<String> statusReporter;
    private final Runnable onAppearanceChanged;

    private final List<ThemePreviewCard> themeCards = new ArrayList<>();
    private final java.util.Map<Language, JButton> languageButtons =
            new java.util.EnumMap<>(Language.class);
    private final JPanel customColorSection = UiFactory.transparentPanel();
    private final ColorWheel colorWheel;

    private final JTextField backgroundPathField = new JTextField();
    private final JCheckBox blurCheckBox = new JCheckBox(Strings.get("appearance.blur"));
    private final JSlider blurRadiusSlider;
    private final JSlider dimSlider;
    private final JSlider opacitySlider;
    private final JLabel backgroundStatusLabel = new JLabel(" ");
    private final JLabel blurRadiusValue = new JLabel();
    private final JLabel dimValue = new JLabel();
    private final JLabel opacityValue = new JLabel();

    private final Timer applyDebounce;

    public AppearancePanel(LauncherContext context, BackgroundManager backgroundManager,
                           Consumer<String> statusReporter, Runnable onAppearanceChanged) {
        this.context = context;
        this.backgroundManager = backgroundManager;
        this.statusReporter = statusReporter;
        this.onAppearanceChanged = onAppearanceChanged;

        var settings = context.settings();
        Color customColor = PaletteFactory.parseHex(settings.customAccentColor);
        this.colorWheel = new ColorWheel(customColor != null ? customColor : AgroTheme.accent());
        this.blurRadiusSlider = new JSlider(1, 40, settings.backgroundBlurRadius);
        this.dimSlider = new JSlider(0, 90, settings.backgroundDimPercent);
        this.opacitySlider = new JSlider(40, 100, settings.panelOpacityPercent);

        applyDebounce = new Timer(220, e -> applyBackgroundSettings());
        applyDebounce.setRepeats(false);

        setOpaque(false);
        setLayout(new BorderLayout());

        JPanel content = UiFactory.transparentPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));

        content.add(buildLanguageCard());
        content.add(UiFactory.verticalGap(16));
        content.add(buildThemeCard());
        content.add(UiFactory.verticalGap(16));
        content.add(buildBackgroundCard());

        add(UiFactory.scroll(content), BorderLayout.CENTER);

        loadValues();
    }

    private JComponent buildLanguageCard() {
        JPanel card = UiFactory.titledCard(Strings.get("appearance.language"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (Language language : Language.values()) {
            row.add(buildLanguageButton(language));
        }

        JLabel hint = UiFactory.hint(Strings.get("appearance.languageHint"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        body.add(row);
        body.add(UiFactory.verticalGap(8));
        body.add(hint);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JButton buildLanguageButton(Language language) {
        boolean active = Strings.language() == language;
        JButton button = active
                ? UiFactory.primaryButton(language.displayName())
                : UiFactory.button(language.displayName());
        button.setPreferredSize(new Dimension(122, 34));
        button.addActionListener(e -> selectLanguage(language));
        languageButtons.put(language, button);
        return button;
    }

    private void selectLanguage(Language language) {
        if (Strings.language() == language) {
            return;
        }
        context.settings().language = language.code();
        context.settings().save();
        Strings.setLanguage(language);
        report(Strings.get("appearance.languageChanged", language.displayName()));
        notifyChanged();
    }

    private JComponent buildThemeCard() {
        JPanel card = UiFactory.titledCard(Strings.get("appearance.theme"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel grid = UiFactory.transparentPanel();
        grid.setLayout(new GridLayout(0, 4, 12, 12));
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (ThemePreset preset : ThemePreset.values()) {
            ThemePreviewCard previewCard = new ThemePreviewCard(preset, this::selectTheme);
            themeCards.add(previewCard);
            grid.add(previewCard);
        }

        body.add(grid);
        body.add(UiFactory.verticalGap(14));
        body.add(buildCustomColorSection());

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildCustomColorSection() {
        customColorSection.setLayout(new BoxLayout(customColorSection, BoxLayout.Y_AXIS));
        customColorSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        customColorSection.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AgroTheme.border()),
                BorderFactory.createEmptyBorder(14, 0, 0, 0)));

        JLabel label = UiFactory.fieldLabel(Strings.get("appearance.customColor"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel hint = UiFactory.hint(Strings.get("appearance.customColorHint"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        colorWheel.setAlignmentX(Component.LEFT_ALIGNMENT);
        colorWheel.onChange(this::previewCustomColor);
        colorWheel.onChangeFinished(this::applyCustomColor);

        customColorSection.add(label);
        customColorSection.add(UiFactory.verticalGap(2));
        customColorSection.add(hint);
        customColorSection.add(UiFactory.verticalGap(10));
        customColorSection.add(colorWheel);
        return customColorSection;
    }

    private void selectTheme(ThemePreset preset) {
        context.settings().themePreset = preset.name();
        context.settings().save();

        Color customColor = PaletteFactory.parseHex(context.settings().customAccentColor);
        AgroTheme.apply(preset, customColor != null ? customColor : colorWheel.color());

        updateThemeSelection();
        customColorSection.setVisible(preset.isCustom());
        revalidate();
        repaint();
        notifyChanged();
        report(Strings.get("appearance.themeSelected", preset.displayName()));
    }

    private void previewCustomColor(Color color) {
        context.settings().customAccentColor = PaletteFactory.toHex(color);

        for (ThemePreviewCard card : themeCards) {
            card.updateCustomColor(color);
        }
    }

    private void applyCustomColor(Color color) {
        context.settings().customAccentColor = PaletteFactory.toHex(color);
        context.settings().save();

        if (ThemePreset.fromId(context.settings().themePreset).isCustom()) {
            AgroTheme.apply(ThemePreset.CUSTOM, color);
        }
    }

    private void updateThemeSelection() {
        ThemePreset active = ThemePreset.fromId(context.settings().themePreset);
        for (ThemePreviewCard card : themeCards) {
            card.setSelected(card.preset() == active);
        }
    }

    private JComponent buildBackgroundCard() {
        JPanel card = UiFactory.titledCard(Strings.get("appearance.background"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel pathLabel = UiFactory.fieldLabel(Strings.get("appearance.backgroundPath"));
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pathRow = new JPanel(new BorderLayout(8, 0));
        pathRow.setOpaque(false);
        pathRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        backgroundPathField.putClientProperty("JTextField.placeholderText",
                Strings.get("appearance.backgroundPlaceholder"));
        UiFactory.fixHeight(backgroundPathField, 34);
        backgroundPathField.addActionListener(e -> applyBackgroundPath(backgroundPathField.getText()));
        backgroundPathField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyBackgroundPath(backgroundPathField.getText());
            }
        });

        JPanel pathButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        pathButtons.setOpaque(false);
        JButton browseButton = UiFactory.button(Strings.get("common.choose"));
        browseButton.addActionListener(e -> browseForImage());
        JButton clearButton = UiFactory.button(Strings.get("common.remove"));
        clearButton.addActionListener(e -> clearBackground());
        pathButtons.add(browseButton);
        pathButtons.add(clearButton);

        pathRow.add(backgroundPathField, BorderLayout.CENTER);
        pathRow.add(pathButtons, BorderLayout.EAST);

        backgroundStatusLabel.setFont(AgroTheme.font(11));
        backgroundStatusLabel.setForeground(AgroTheme.textMuted());
        backgroundStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        blurCheckBox.setOpaque(false);
        blurCheckBox.setFont(AgroTheme.font(13));
        blurCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        blurCheckBox.addActionListener(e -> {
            context.settings().backgroundBlur = blurCheckBox.isSelected();
            context.settings().save();
            blurRadiusSlider.setEnabled(blurCheckBox.isSelected());
            applyBackgroundSettings();
        });

        body.add(pathLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(pathRow);
        body.add(UiFactory.verticalGap(6));
        body.add(backgroundStatusLabel);
        body.add(UiFactory.verticalGap(14));
        body.add(blurCheckBox);
        body.add(UiFactory.verticalGap(10));
        body.add(buildSliderRow(Strings.get("appearance.blurRadius"), blurRadiusSlider, blurRadiusValue, " px"));
        body.add(UiFactory.verticalGap(10));
        body.add(buildSliderRow(Strings.get("appearance.dim"), dimSlider, dimValue, " %"));
        body.add(UiFactory.verticalGap(10));
        body.add(buildSliderRow(Strings.get("appearance.panelOpacity"), opacitySlider, opacityValue, " %"));

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildSliderRow(String caption, JSlider slider, JLabel valueLabel, String suffix) {
        JPanel row = UiFactory.transparentPanel();
        row.setLayout(new BorderLayout(12, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel captionLabel = UiFactory.fieldLabel(caption);
        captionLabel.setPreferredSize(new Dimension(148, 22));

        slider.setOpaque(false);
        slider.setPreferredSize(new Dimension(260, 28));
        slider.addChangeListener(e -> {
            valueLabel.setText(slider.getValue() + suffix);
            if (!slider.getValueIsAdjusting()) {
                saveSliderValues();
                applyDebounce.restart();
            }
        });

        valueLabel.setText(slider.getValue() + suffix);
        valueLabel.setFont(AgroTheme.boldFont(12));
        valueLabel.setForeground(AgroTheme.accentLight());
        valueLabel.setPreferredSize(new Dimension(56, 22));

        row.add(captionLabel, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private void saveSliderValues() {
        var settings = context.settings();
        settings.backgroundBlurRadius = blurRadiusSlider.getValue();
        settings.backgroundDimPercent = dimSlider.getValue();
        settings.panelOpacityPercent = opacitySlider.getValue();
        settings.save();
    }

    private void browseForImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("appearance.backgroundChoose"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                Strings.get("appearance.imageFilter"), "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            backgroundPathField.setText(file.getAbsolutePath());
            applyBackgroundPath(file.getAbsolutePath());
        }
    }

    private void applyBackgroundPath(String path) {
        String trimmed = path == null ? "" : path.trim();
        if (trimmed.equals(context.settings().backgroundImagePath)) {
            return;
        }
        context.settings().backgroundImagePath = trimmed;
        context.settings().save();
        applyBackgroundSettings();
    }

    private void clearBackground() {
        backgroundPathField.setText("");
        context.settings().backgroundImagePath = "";
        context.settings().save();
        backgroundManager.clear();
        backgroundStatusLabel.setText(Strings.get("appearance.backgroundOff"));
        backgroundStatusLabel.setForeground(AgroTheme.textMuted());
        notifyChanged();
        report(Strings.get("appearance.backgroundRemoved"));
    }

    private void applyBackgroundSettings() {
        backgroundManager.invalidateCache();
        boolean loaded = backgroundManager.load(context.settings());

        if (context.settings().backgroundImagePath.isBlank()) {
            backgroundStatusLabel.setText(Strings.get("appearance.backgroundOff"));
            backgroundStatusLabel.setForeground(AgroTheme.textMuted());
        } else if (loaded) {
            StringBuilder status = new StringBuilder(Strings.get("appearance.backgroundLoaded"));
            if (backgroundManager.isAnimated()) {
                status.append(Strings.get("appearance.backgroundGif"));
                if (context.settings().backgroundBlur) {
                    status.append(Strings.get("appearance.backgroundGifBlur"));
                }
            }
            backgroundStatusLabel.setText(status.toString());
            backgroundStatusLabel.setForeground(AgroTheme.accentLight());
        } else {
            String error = backgroundManager.lastError();
            backgroundStatusLabel.setText(error != null ? error : Strings.get("appearance.backgroundFailed"));
            backgroundStatusLabel.setForeground(AgroTheme.error());
        }
        notifyChanged();
    }

    private void loadValues() {
        var settings = context.settings();
        backgroundPathField.setText(settings.backgroundImagePath);
        blurCheckBox.setSelected(settings.backgroundBlur);
        blurRadiusSlider.setEnabled(settings.backgroundBlur);
        blurRadiusSlider.setValue(settings.backgroundBlurRadius);
        dimSlider.setValue(settings.backgroundDimPercent);
        opacitySlider.setValue(settings.panelOpacityPercent);

        ThemePreset active = ThemePreset.fromId(settings.themePreset);
        updateThemeSelection();
        customColorSection.setVisible(active.isCustom());

        Color customColor = PaletteFactory.parseHex(settings.customAccentColor);
        if (customColor != null) {
            for (ThemePreviewCard card : themeCards) {
                card.updateCustomColor(customColor);
            }
        }

        if (!settings.backgroundImagePath.isBlank()) {
            applyBackgroundSettings();
        } else {
            backgroundStatusLabel.setText(Strings.get("appearance.backgroundOff"));
        }
    }

    public void reload() {
        loadValues();
    }

    private void notifyChanged() {
        if (onAppearanceChanged != null) {
            onAppearanceChanged.run();
        }
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }
}
