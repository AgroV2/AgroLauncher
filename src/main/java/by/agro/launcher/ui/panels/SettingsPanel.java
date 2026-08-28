package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Platform;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.Settings;
import by.agro.launcher.jvm.JavaManager;
import by.agro.launcher.ui.components.RamSlider;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class SettingsPanel extends JPanel {

    private final LauncherContext context;
    private final Consumer<String> statusReporter;

    private final RamSlider ramSlider;
    private final JSpinner minRamSpinner;
    private final JTextField javaPathField = new JTextField();
    private final JCheckBox useManagedJava = new JCheckBox(Strings.get("settings.useManagedJava"));
    private final JComboBox<String> javaInstallationsCombo = new JComboBox<>();
    private final JTextField jvmArgsField = new JTextField();
    private final JTextField gameArgsField = new JTextField();
    private final JSpinner widthSpinner;
    private final JSpinner heightSpinner;
    private final JCheckBox fullscreen = new JCheckBox(Strings.get("settings.fullscreen"));
    private final JCheckBox closeOnLaunch = new JCheckBox(Strings.get("settings.closeOnLaunch"));
    private final JCheckBox showConsole = new JCheckBox(Strings.get("settings.showConsole"));
    private final JCheckBox optimizedFlags = new JCheckBox(Strings.get("settings.optimizedFlags"));
    private final JLabel javaStatusLabel = new JLabel(" ");

    private final javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
    private final AppearancePanel appearancePanel;

    public SettingsPanel(LauncherContext context,
                         by.agro.launcher.ui.background.BackgroundManager backgroundManager,
                         Consumer<String> statusReporter, Runnable onAppearanceChanged) {
        this.context = context;
        this.statusReporter = statusReporter;
        this.appearancePanel = new AppearancePanel(
                context, backgroundManager, statusReporter, onAppearanceChanged);
        Settings settings = context.settings();

        this.ramSlider = new RamSlider(settings.maxRamMb);
        this.minRamSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0, settings.minRamMb), 0, 8192, 256));
        this.widthSpinner = new JSpinner(new SpinnerNumberModel(settings.windowWidth, 320, 7680, 2));
        this.heightSpinner = new JSpinner(new SpinnerNumberModel(settings.windowHeight, 240, 4320, 2));

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(26, 30, 20, 30));

        JPanel header = UiFactory.transparentPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel titleLabel = UiFactory.title(Strings.get("settings.title"));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitleLabel = UiFactory.subtitle(Strings.get("settings.subtitle"));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLabel);
        header.add(UiFactory.verticalGap(4));
        header.add(subtitleLabel);
        header.add(UiFactory.verticalGap(16));

        JPanel general = UiFactory.transparentPanel();
        general.setLayout(new BoxLayout(general, BoxLayout.Y_AXIS));
        general.setBorder(BorderFactory.createEmptyBorder(4, 0, 10, 0));
        general.add(buildMemoryCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildJavaCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildWindowCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildAdvancedCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildFoldersCard());

        tabs.setOpaque(false);
        tabs.addTab(Strings.get("settings.general"), UiFactory.scroll(general));
        tabs.addTab(Strings.get("settings.appearance"), appearancePanel);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        loadValues();
        refreshJavaInstallations();
    }

    public AppearancePanel appearancePanel() {
        return appearancePanel;
    }

    public int selectedTab() {
        return tabs.getSelectedIndex();
    }

    public void selectTab(int index) {
        if (index >= 0 && index < tabs.getTabCount()) {
            tabs.setSelectedIndex(index);
        }
    }

    private JComponent buildMemoryCard() {
        JPanel card = UiFactory.titledCard(Strings.get("settings.memory"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel maxLabel = UiFactory.fieldLabel(Strings.get("settings.maxRam"));
        maxLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        ramSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        ramSlider.onChange(value -> {
            context.settings().maxRamMb = value;
            context.settings().save();
            report(Strings.get("settings.ramAllocated", by.agro.launcher.core.SystemInfo.formatMb(value)));
        });

        JPanel minRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        minRow.setOpaque(false);
        minRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        minRow.add(UiFactory.fieldLabel(Strings.get("settings.minRam")));
        minRamSpinner.setPreferredSize(new Dimension(96, 30));
        minRamSpinner.addChangeListener(e -> {
            context.settings().minRamMb = (Integer) minRamSpinner.getValue();
            context.settings().save();
        });
        minRow.add(minRamSpinner);
        minRow.add(UiFactory.hint(Strings.get("settings.minRamHint")));

        body.add(maxLabel);
        body.add(UiFactory.verticalGap(8));
        body.add(ramSlider);
        body.add(UiFactory.verticalGap(12));
        body.add(minRow);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildJavaCard() {
        JPanel card = UiFactory.titledCard(Strings.get("settings.java"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        useManagedJava.setOpaque(false);
        useManagedJava.setFont(AgroTheme.font(13));
        useManagedJava.setAlignmentX(Component.LEFT_ALIGNMENT);
        useManagedJava.addActionListener(e -> {
            context.settings().useManagedJava = useManagedJava.isSelected();
            context.settings().save();
            updateJavaFieldsState();
        });

        JLabel hint = UiFactory.hint(Strings.get("settings.javaHint"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel pathLabel = UiFactory.fieldLabel(Strings.get("settings.javaPath"));
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pathRow = new JPanel(new BorderLayout(8, 0));
        pathRow.setOpaque(false);
        pathRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        javaPathField.putClientProperty("JTextField.placeholderText",
                Platform.isWindows() ? "C:\\Program Files\\Java\\bin\\javaw.exe" : "/usr/lib/jvm/java-21/bin/java");
        javaPathField.addActionListener(e -> saveJavaPath());
        javaPathField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveJavaPath();
            }
        });
        JButton browseButton = UiFactory.button(Strings.get("common.choose"));
        browseButton.addActionListener(e -> browseForJava());
        pathRow.add(javaPathField, BorderLayout.CENTER);
        pathRow.add(browseButton, BorderLayout.EAST);

        JPanel detectedRow = new JPanel(new BorderLayout(8, 0));
        detectedRow.setOpaque(false);
        detectedRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        detectedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        UiFactory.fixHeight(javaInstallationsCombo, 32);
        JButton useDetected = UiFactory.button(Strings.get("common.apply"));
        useDetected.addActionListener(e -> applyDetectedJava());
        detectedRow.add(javaInstallationsCombo, BorderLayout.CENTER);
        detectedRow.add(useDetected, BorderLayout.EAST);

        JPanel downloadRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        downloadRow.setOpaque(false);
        downloadRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (int version : by.agro.launcher.version.JavaRequirement.supportedVersions()) {
            JButton button = UiFactory.button(Strings.get("settings.javaDownload", version));
            button.addActionListener(e -> downloadJava(version));
            downloadRow.add(button);
        }

        javaStatusLabel.setFont(AgroTheme.font(11));
        javaStatusLabel.setForeground(AgroTheme.textMuted());
        javaStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel detectedLabel = UiFactory.fieldLabel(Strings.get("settings.javaDetected"));
        detectedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        body.add(useManagedJava);
        body.add(UiFactory.verticalGap(2));
        body.add(hint);
        body.add(UiFactory.verticalGap(14));
        body.add(detectedLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(detectedRow);
        body.add(UiFactory.verticalGap(14));
        body.add(pathLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(pathRow);
        body.add(UiFactory.verticalGap(12));
        body.add(downloadRow);
        body.add(UiFactory.verticalGap(6));
        body.add(javaStatusLabel);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildWindowCard() {
        JPanel card = UiFactory.titledCard(Strings.get("settings.window"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 10);
        body.add(UiFactory.fieldLabel(Strings.get("settings.width")), c);

        c.gridx = 1;
        widthSpinner.setPreferredSize(new Dimension(90, 30));
        widthSpinner.addChangeListener(e -> {
            context.settings().windowWidth = (Integer) widthSpinner.getValue();
            context.settings().save();
        });
        body.add(widthSpinner, c);

        c.gridx = 2;
        body.add(UiFactory.fieldLabel(Strings.get("settings.height")), c);

        c.gridx = 3;
        heightSpinner.setPreferredSize(new Dimension(90, 30));
        heightSpinner.addChangeListener(e -> {
            context.settings().windowHeight = (Integer) heightSpinner.getValue();
            context.settings().save();
        });
        body.add(heightSpinner, c);

        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 4;
        fullscreen.setOpaque(false);
        fullscreen.setFont(AgroTheme.font(13));
        fullscreen.addActionListener(e -> {
            context.settings().fullscreen = fullscreen.isSelected();
            context.settings().save();
            widthSpinner.setEnabled(!fullscreen.isSelected());
            heightSpinner.setEnabled(!fullscreen.isSelected());
        });
        body.add(fullscreen, c);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildAdvancedCard() {
        JPanel card = UiFactory.titledCard(Strings.get("settings.advanced"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        for (JCheckBox box : new JCheckBox[]{optimizedFlags, closeOnLaunch, showConsole}) {
            box.setOpaque(false);
            box.setFont(AgroTheme.font(13));
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        optimizedFlags.addActionListener(e -> {
            context.settings().optimizedJvmFlags = optimizedFlags.isSelected();
            context.settings().save();
        });
        closeOnLaunch.addActionListener(e -> {
            context.settings().closeOnLaunch = closeOnLaunch.isSelected();
            context.settings().save();
        });
        showConsole.addActionListener(e -> {
            context.settings().showConsole = showConsole.isSelected();
            context.settings().save();
        });

        JLabel jvmLabel = UiFactory.fieldLabel(Strings.get("settings.jvmArgs"));
        jvmLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        UiFactory.fixHeight(jvmArgsField, 32);
        jvmArgsField.setAlignmentX(Component.LEFT_ALIGNMENT);
        jvmArgsField.putClientProperty("JTextField.placeholderText", "-XX:+UseZGC -Dsun.java2d.opengl=true");
        jvmArgsField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                context.settings().extraJvmArgs = jvmArgsField.getText().trim();
                context.settings().save();
            }
        });

        JLabel gameLabel = UiFactory.fieldLabel(Strings.get("settings.gameArgs"));
        gameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        UiFactory.fixHeight(gameArgsField, 32);
        gameArgsField.setAlignmentX(Component.LEFT_ALIGNMENT);
        gameArgsField.putClientProperty("JTextField.placeholderText", "--server play.example.com --port 25565");
        gameArgsField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                context.settings().extraGameArgs = gameArgsField.getText().trim();
                context.settings().save();
            }
        });

        body.add(optimizedFlags);
        body.add(closeOnLaunch);
        body.add(showConsole);
        body.add(UiFactory.verticalGap(14));
        body.add(jvmLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(jvmArgsField);
        body.add(UiFactory.verticalGap(12));
        body.add(gameLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(gameArgsField);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildFoldersCard() {
        JPanel card = UiFactory.titledCard(Strings.get("settings.folders"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        LauncherPaths paths = context.paths();
        JLabel rootLabel = UiFactory.hint(Strings.get("settings.launcherData", paths.root()));
        rootLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel gameLabel = UiFactory.hint(Strings.get("settings.gameFiles", paths.gameDir()));
        gameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton openGame = UiFactory.button(Strings.get("settings.openGameFolder"));
        openGame.addActionListener(e -> openFolder(paths.gameDir()));
        JButton openMods = UiFactory.button(Strings.get("settings.openModsFolder"));
        openMods.addActionListener(e -> openFolder(paths.modsDir()));
        JButton openRoot = UiFactory.button(Strings.get("settings.openLauncherFolder"));
        openRoot.addActionListener(e -> openFolder(paths.root()));

        buttons.add(openGame);
        buttons.add(openMods);
        buttons.add(openRoot);

        body.add(rootLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(gameLabel);
        body.add(UiFactory.verticalGap(12));
        body.add(buttons);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void loadValues() {
        Settings settings = context.settings();
        ramSlider.setValueMb(settings.maxRamMb);
        minRamSpinner.setValue(Math.max(0, settings.minRamMb));
        javaPathField.setText(settings.javaPath == null ? "" : settings.javaPath);
        useManagedJava.setSelected(settings.useManagedJava);
        jvmArgsField.setText(settings.extraJvmArgs == null ? "" : settings.extraJvmArgs);
        gameArgsField.setText(settings.extraGameArgs == null ? "" : settings.extraGameArgs);
        widthSpinner.setValue(settings.windowWidth);
        heightSpinner.setValue(settings.windowHeight);
        fullscreen.setSelected(settings.fullscreen);
        closeOnLaunch.setSelected(settings.closeOnLaunch);
        showConsole.setSelected(settings.showConsole);
        optimizedFlags.setSelected(settings.optimizedJvmFlags);
        widthSpinner.setEnabled(!settings.fullscreen);
        heightSpinner.setEnabled(!settings.fullscreen);
        updateJavaFieldsState();
    }

    private void updateJavaFieldsState() {
        boolean managed = useManagedJava.isSelected();
        javaPathField.setEnabled(!managed || !context.settings().javaPath.isBlank());
    }

    private void saveJavaPath() {
        String path = javaPathField.getText().trim();
        context.settings().javaPath = path;
        context.settings().save();
        if (!path.isEmpty()) {
            java.nio.file.Path candidate = java.nio.file.Path.of(path);
            if (!java.nio.file.Files.isExecutable(candidate)) {
                javaStatusLabel.setText(Strings.get("settings.javaNotFound"));
                javaStatusLabel.setForeground(AgroTheme.error());
                return;
            }
            int version = by.agro.launcher.jvm.JavaDetector.queryMajorVersion(candidate);
            javaStatusLabel.setText(version > 0
                    ? Strings.get("settings.javaVersionDetected", version)
                    : Strings.get("settings.javaVersionUnknown"));
            javaStatusLabel.setForeground(version > 0 ? AgroTheme.accentLight() : AgroTheme.warning());
        }
    }

    private void browseForJava() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("settings.javaChoose"));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            javaPathField.setText(file.getAbsolutePath());
            saveJavaPath();
        }
    }

    private void refreshJavaInstallations() {
        javaInstallationsCombo.removeAllItems();
        for (JavaManager.JavaInstallation installation : context.javaManager().listInstallations()) {
            javaInstallationsCombo.addItem(installation.toString());
        }
        if (javaInstallationsCombo.getItemCount() == 0) {
            javaInstallationsCombo.addItem(Strings.get("settings.javaNone"));
        }
    }

    private void applyDetectedJava() {
        String selected = (String) javaInstallationsCombo.getSelectedItem();
        if (selected == null || !selected.contains(" — ")) {
            return;
        }
        String path = selected.substring(selected.indexOf(" — ") + 3).trim();
        javaPathField.setText(path);
        useManagedJava.setSelected(false);
        context.settings().useManagedJava = false;
        saveJavaPath();
    }

    private void downloadJava(int version) {
        javaStatusLabel.setText(Strings.get("settings.javaDownloading", version));
        javaStatusLabel.setForeground(AgroTheme.textSecondary());
        report(Strings.get("settings.javaDownloading", version));

        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return context.javaManager().downloadJre(version, new ProgressListener() {
                    @Override
                    public void onProgress(String stage, long current, long total, String detail) {
                        report(stage + ": " + (detail == null ? "" : detail));
                    }

                    @Override
                    public void onMessage(String message) {
                        report(message);
                    }
                });
            }

            @Override
            protected void done() {
                try {
                    Path path = get();
                    javaStatusLabel.setText(Strings.get("settings.javaInstalled", version));
                    javaStatusLabel.setForeground(AgroTheme.accentLight());
                    refreshJavaInstallations();
                    report(Strings.get("settings.javaReady", version, path));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    javaStatusLabel.setText(Strings.get("common.error") + ": " + cause.getMessage());
                    javaStatusLabel.setForeground(AgroTheme.error());
                }
            }
        }.execute();
    }

    private void openFolder(Path path) {
        try {
            java.nio.file.Files.createDirectories(path);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                JOptionPane.showMessageDialog(this, path.toString(), Strings.get("settings.folderPath"),
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    Strings.get("mods.openFolderFailed", e.getMessage()),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
        }
    }

    public void reload() {
        loadValues();
        refreshJavaInstallations();
        appearancePanel.reload();
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }
}
