package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.cleanup.CleanupService;
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
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

public final class SettingsPanel extends JPanel {

    private final LauncherContext context;
    private final Consumer<String> statusReporter;

    private final RamSlider ramSlider;
    private final JSpinner minRamSpinner;
    private final JTextField javaPathField = new JTextField();
    private final JCheckBox useManagedJava = new JCheckBox(Strings.get("settings.useManagedJava"));
    private final JComboBox<String> javaModeCombo = new JComboBox<>(new String[]{"AUTO", "MANAGED", "SYSTEM", "CUSTOM"});
    private final JCheckBox offlineMode = new JCheckBox(Strings.get("settings.offlineMode"));
    private final JComboBox<String> javaInstallationsCombo = new JComboBox<>();
    private final JTextField jvmArgsField = new JTextField();
    private final JTextField gameArgsField = new JTextField();
    private final javax.swing.JTextArea envVarsField = new javax.swing.JTextArea();
    private final JSpinner widthSpinner;
    private final JSpinner heightSpinner;
    private final JSpinner downloadParallelismSpinner;
    private final JSpinner downloadPerHostSpinner;
    private final JCheckBox metadataCacheEnabled = new JCheckBox(Strings.get("settings.metadataCacheEnabled"));
    private final JSpinner metadataCacheTtlSpinner;
    private final JCheckBox fullscreen = new JCheckBox(Strings.get("settings.fullscreen"));
    private final JCheckBox closeOnLaunch = new JCheckBox(Strings.get("settings.closeOnLaunch"));
    private final JCheckBox showConsole = new JCheckBox(Strings.get("settings.showConsole"));
    private final JCheckBox checkModsBeforeLaunch = new JCheckBox(Strings.get("settings.checkModsBeforeLaunch"));
    private final JCheckBox optimizedFlags = new JCheckBox(Strings.get("settings.optimizedFlags"));
    private final JLabel javaStatusLabel = new JLabel(" ");
    private final JComboBox<String> proxyModeCombo = new JComboBox<>(new String[]{"NONE", "HTTP", "SOCKS"});
    private final JTextField gameDataPathField = new JTextField();
    private final JTextField proxyHostField = new JTextField();
    private final JSpinner proxyPortSpinner;
    private final BackupsPanel backupsPanel;

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
        this.downloadParallelismSpinner = new JSpinner(new SpinnerNumberModel(settings.downloadParallelism, 1, 32, 1));
        this.downloadPerHostSpinner = new JSpinner(new SpinnerNumberModel(settings.downloadPerHost, 1, 32, 1));
        this.metadataCacheTtlSpinner = new JSpinner(new SpinnerNumberModel(settings.metadataCacheTtlMinutes, 1, 10080, 1));
        this.proxyPortSpinner = new JSpinner(new SpinnerNumberModel(settings.proxyPort, 1, 65535, 1));
        this.backupsPanel = new BackupsPanel(context, statusReporter);

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
        general.add(buildPerformanceCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildJavaCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildWindowCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildAdvancedCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildNetworkCard());
        general.add(UiFactory.verticalGap(16));
        general.add(buildFoldersCard());

        tabs.setOpaque(false);
        tabs.addTab(Strings.get("settings.general"), UiFactory.scroll(general));
        tabs.addTab(Strings.get("settings.appearance"), appearancePanel);
        tabs.addTab(Strings.get("settings.backups"), backupsPanel);

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

    private JComponent buildPerformanceCard() {
        JPanel card = UiFactory.titledCard(Strings.get("settings.performance"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel parallelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        parallelRow.setOpaque(false);
        parallelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        parallelRow.add(UiFactory.fieldLabel(Strings.get("settings.parallelDownloads")));
        downloadParallelismSpinner.setPreferredSize(new Dimension(72, 30));
        downloadParallelismSpinner.addChangeListener(e -> {
            context.settings().downloadParallelism = (Integer) downloadParallelismSpinner.getValue();
            context.settings().save();
            report(Strings.get("settings.parallelDownloadsSaved"));
        });
        parallelRow.add(downloadParallelismSpinner);
        parallelRow.add(UiFactory.hint(Strings.get("settings.parallelDownloadsRestart")));

        JPanel perHostRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        perHostRow.setOpaque(false);
        perHostRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        perHostRow.add(UiFactory.fieldLabel(Strings.get("settings.downloadPerHost")));
        downloadPerHostSpinner.setPreferredSize(new Dimension(72, 30));
        downloadPerHostSpinner.addChangeListener(e -> {
            context.settings().downloadPerHost = (Integer) downloadPerHostSpinner.getValue();
            context.settings().save();
            report(Strings.get("settings.parallelDownloadsSaved"));
        });
        perHostRow.add(downloadPerHostSpinner);
        perHostRow.add(UiFactory.hint(Strings.get("settings.parallelDownloadsRestart")));

        metadataCacheEnabled.setOpaque(false);
        metadataCacheEnabled.setAlignmentX(Component.LEFT_ALIGNMENT);
        metadataCacheEnabled.addActionListener(e -> {
            context.settings().metadataCacheEnabled = metadataCacheEnabled.isSelected();
            context.settings().save();
            metadataCacheTtlSpinner.setEnabled(metadataCacheEnabled.isSelected());
        });

        JPanel ttlRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        ttlRow.setOpaque(false);
        ttlRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        ttlRow.add(UiFactory.fieldLabel(Strings.get("settings.metadataCacheTtl")));
        metadataCacheTtlSpinner.setPreferredSize(new Dimension(92, 30));
        metadataCacheTtlSpinner.addChangeListener(e -> {
            context.settings().metadataCacheTtlMinutes = (Integer) metadataCacheTtlSpinner.getValue();
            context.settings().save();
        });
        ttlRow.add(metadataCacheTtlSpinner);
        ttlRow.add(UiFactory.hint(Strings.get("settings.minutes")));

        JPanel cleanupRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        cleanupRow.setOpaque(false);
        cleanupRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton cacheCleanup = UiFactory.button(Strings.get("settings.cleanupCache"));
        cacheCleanup.addActionListener(e -> startCleanup(true, cacheCleanup));
        JButton unusedCleanup = UiFactory.button(Strings.get("settings.cleanupUnused"));
        unusedCleanup.addActionListener(e -> startCleanup(false, unusedCleanup));
        cleanupRow.add(cacheCleanup);
        cleanupRow.add(unusedCleanup);

        JLabel cleanupHint = UiFactory.hint(Strings.get("settings.cleanupHint"));
        cleanupHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(parallelRow);
        body.add(perHostRow);
        body.add(UiFactory.verticalGap(10));
        body.add(metadataCacheEnabled);
        body.add(UiFactory.verticalGap(4));
        body.add(ttlRow);
        body.add(UiFactory.verticalGap(12));
        body.add(cleanupRow);
        body.add(UiFactory.verticalGap(5));
        body.add(cleanupHint);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void startCleanup(boolean cacheOnly, JButton button) {
        button.setEnabled(false);
        report(Strings.get("settings.cleanupScanning"));
        CleanupService service = new CleanupService(context.paths(), context.versionResolver(), context.settings());
        new SwingWorker<CleanupService.Plan, Void>() {
            @Override
            protected CleanupService.Plan doInBackground() throws Exception {
                return cacheOnly
                        ? service.scanCache(new CleanupService.CachePolicy(Duration.ZERO, 0))
                        : service.scanReachability();
            }

            @Override
            protected void done() {
                try {
                    CleanupService.Plan plan = get();
                    if (plan.totalItems() == 0) {
                        JOptionPane.showMessageDialog(SettingsPanel.this, Strings.get("settings.cleanupNothing"),
                                Strings.get("settings.cleanupTitle"), JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    String preview = cleanupPreview(plan);
                    int answer = JOptionPane.showConfirmDialog(SettingsPanel.this, preview,
                            Strings.get("settings.cleanupConfirmTitle"), JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (answer == JOptionPane.OK_OPTION) {
                        executeCleanup(service, plan, button);
                    } else {
                        button.setEnabled(true);
                    }
                } catch (Exception e) {
                    button.setEnabled(true);
                    showCleanupError(e);
                }
            }
        }.execute();
    }

    private void executeCleanup(CleanupService service, CleanupService.Plan plan, JButton button) {
        String original = button.getText();
        button.setText(Strings.get("settings.cleanupRunning"));
        button.setEnabled(false);
        new SwingWorker<CleanupService.Result, Void>() {
            @Override
            protected CleanupService.Result doInBackground() throws Exception {
                return service.execute(plan);
            }

            @Override
            protected void done() {
                button.setText(original);
                button.setEnabled(true);
                try {
                    CleanupService.Result result = get();
                    StringBuilder message = new StringBuilder(Strings.get("settings.cleanupComplete",
                            result.deletedItems(), formatBytes(result.reclaimedBytes())));
                    if (!result.failures().isEmpty()) {
                        message.append("\n\n").append(Strings.get("settings.cleanupPartial", result.failures().size()));
                        for (CleanupService.Failure failure : result.failures()) {
                            message.append("\n• ").append(failure.path()).append(": ").append(failure.message());
                        }
                    }
                    JOptionPane.showMessageDialog(SettingsPanel.this, message.toString(),
                            Strings.get("settings.cleanupTitle"), result.failures().isEmpty()
                                    ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                    report(Strings.get("settings.cleanupReclaimed", formatBytes(result.reclaimedBytes())));
                } catch (Exception e) {
                    showCleanupError(e);
                }
            }
        }.execute();
    }

    private String cleanupPreview(CleanupService.Plan plan) {
        StringBuilder text = new StringBuilder(Strings.get("settings.cleanupPreview",
                plan.totalItems(), formatBytes(plan.reclaimableBytes())));
        for (Map.Entry<CleanupService.Category, CleanupService.CategoryTotal> entry : plan.totals().entrySet()) {
            text.append("\n• ").append(Strings.get("settings.cleanupCategory." + entry.getKey().name()))
                    .append(": ").append(Strings.get("settings.cleanupCategoryTotal",
                            entry.getValue().items(), formatBytes(entry.getValue().bytes())));
        }
        text.append("\n\n").append(Strings.get("settings.cleanupConfirm"));
        return text.toString();
    }

    private void showCleanupError(Exception error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        JOptionPane.showMessageDialog(this, Strings.get("settings.cleanupFailed", cause.getMessage()),
                Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format("%.2f %s", bytes / (1024d * 1024 * 1024), Strings.get("unit.gb"));
        if (bytes >= 1024L * 1024) return String.format("%.2f %s", bytes / (1024d * 1024), Strings.get("unit.mb"));
        if (bytes >= 1024L) return String.format("%.2f %s", bytes / 1024d, Strings.get("unit.kb"));
        return bytes + " " + Strings.get("unit.b");
    }

    private JComponent buildJavaCard() {
        JPanel card = UiFactory.titledCard(Strings.get("settings.java"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        useManagedJava.setVisible(false);
        javaModeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        javaModeCombo.addActionListener(e -> {
            String mode = (String) javaModeCombo.getSelectedItem();
            if (mode != null) {
                context.settings().javaMode = mode;
                context.settings().useManagedJava = "MANAGED".equals(mode);
                context.settings().save();
                updateJavaFieldsState();
            }
        });
        offlineMode.setOpaque(false);
        offlineMode.setAlignmentX(Component.LEFT_ALIGNMENT);
        offlineMode.setToolTipText(Strings.get("settings.offlineModeTooltip"));
        offlineMode.addActionListener(e -> {
            context.settings().offlineMode = offlineMode.isSelected();
            context.settings().save();
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

        body.add(UiFactory.fieldLabel(Strings.get("settings.javaMode")));
        body.add(UiFactory.verticalGap(4));
        body.add(javaModeCombo);
        body.add(UiFactory.verticalGap(6));
        body.add(offlineMode);
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

        for (JCheckBox box : new JCheckBox[]{optimizedFlags, closeOnLaunch, showConsole, checkModsBeforeLaunch}) {
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
        checkModsBeforeLaunch.setToolTipText(Strings.get("settings.checkModsBeforeLaunchHint"));
        checkModsBeforeLaunch.addActionListener(e -> {
            context.settings().checkModsBeforeLaunch = checkModsBeforeLaunch.isSelected();
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
        body.add(checkModsBeforeLaunch);
        body.add(UiFactory.verticalGap(14));
        body.add(jvmLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(jvmArgsField);
        body.add(UiFactory.verticalGap(12));
        body.add(gameLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(gameArgsField);
        body.add(UiFactory.verticalGap(12));
        body.add(buildEnvVarsSection());

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildEnvVarsSection() {
        JPanel section = UiFactory.transparentPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = UiFactory.fieldLabel(Strings.get("settings.envVars"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hint = UiFactory.hint(Strings.get("settings.envVarsHint"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        envVarsField.setRows(2);
        envVarsField.setLineWrap(true);
        envVarsField.setWrapStyleWord(true);
        envVarsField.setFont(AgroTheme.font(13));
        envVarsField.putClientProperty("JTextField.placeholderText",
                "__GLX_VENDOR_LIBRARY_NAME=nvidia");
        envVarsField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                context.settings().extraEnvVars = envVarsField.getText().trim();
                context.settings().save();
            }
        });

        section.add(label);
        section.add(UiFactory.verticalGap(4));
        section.add(envVarsField);
        section.add(UiFactory.verticalGap(4));
        section.add(hint);
        return section;
    }

    private JComponent buildNetworkCard() {
        JPanel card = UiFactory.titledCard(Strings.get("settings.network"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        modeRow.setOpaque(false);
        modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeRow.add(UiFactory.fieldLabel(Strings.get("settings.proxyMode")));
        UiFactory.fixHeight(proxyModeCombo, 32);
        proxyModeCombo.setSelectedItem(context.settings().proxyMode);
        proxyModeCombo.addActionListener(e -> saveProxy());
        modeRow.add(proxyModeCombo);
        modeRow.add(UiFactory.hint(Strings.get("settings.proxyRestartHint")));

        JPanel hostRow = new JPanel(new BorderLayout(10, 0));
        hostRow.setOpaque(false);
        hostRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        hostRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        proxyHostField.putClientProperty("JTextField.placeholderText", "127.0.0.1");
        proxyHostField.setText(context.settings().proxyHost == null ? "" : context.settings().proxyHost);
        proxyHostField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveProxy();
            }
        });
        proxyPortSpinner.setPreferredSize(new Dimension(96, 30));
        proxyPortSpinner.addChangeListener(e -> saveProxy());
        JPanel portRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        portRow.setOpaque(false);
        portRow.add(UiFactory.fieldLabel(Strings.get("settings.proxyPort")));
        portRow.add(proxyPortSpinner);
        hostRow.add(proxyHostField, BorderLayout.CENTER);
        hostRow.add(portRow, BorderLayout.EAST);

        body.add(modeRow);
        body.add(UiFactory.verticalGap(10));
        body.add(hostRow);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void saveProxy() {
        String mode = (String) proxyModeCombo.getSelectedItem();
        context.settings().proxyMode = mode == null ? "NONE" : mode;
        context.settings().proxyHost = proxyHostField.getText().trim();
        context.settings().proxyPort = (Integer) proxyPortSpinner.getValue();
        context.settings().save();
        by.agro.launcher.core.Downloader.configureProxy(
                context.settings().proxyMode, context.settings().proxyHost, context.settings().proxyPort);
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

        JPanel dataRow = new JPanel(new BorderLayout(8, 0));
        dataRow.setOpaque(false);
        dataRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dataRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        gameDataPathField.putClientProperty("JTextField.placeholderText",
                context.paths().gameDir().toString());
        JButton gameDataBrowse = UiFactory.button(Strings.get("common.choose"));
        gameDataBrowse.addActionListener(e -> browseForGameDataDir());
        dataRow.add(gameDataPathField, BorderLayout.CENTER);
        dataRow.add(gameDataBrowse, BorderLayout.EAST);

        JPanel dataButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dataButtons.setOpaque(false);
        dataButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton copyData = UiFactory.button(Strings.get("settings.gameDataCopy"));
        copyData.addActionListener(e -> applyGameDataPath(false));
        JButton moveData = UiFactory.button(Strings.get("settings.gameDataMove"));
        moveData.addActionListener(e -> applyGameDataPath(true));
        dataButtons.add(copyData);
        dataButtons.add(moveData);
        JLabel dataHint = UiFactory.hint(Strings.get("settings.gameDataPathHint"));
        dataHint.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton openGame = UiFactory.button(Strings.get("settings.openGameFolder"));
        openGame.addActionListener(e -> openFolder(paths.gameDir()));
        JButton openMods = UiFactory.button(Strings.get("settings.openModsFolder"));
        openMods.addActionListener(e -> openFolder(paths.modsDir()));
        JButton openRoot = UiFactory.button(Strings.get("settings.openLauncherFolder"));
        openRoot.addActionListener(e -> openFolder(paths.root()));
        JButton exportSettingsButton = UiFactory.button(Strings.get("settings.exportSettings"));
        exportSettingsButton.addActionListener(e -> exportLauncherSettings());
        JButton importSettingsButton = UiFactory.button(Strings.get("settings.importSettings"));
        importSettingsButton.addActionListener(e -> importLauncherSettings());

        buttons.add(openGame);
        buttons.add(openMods);
        buttons.add(openRoot);
        buttons.add(exportSettingsButton);
        buttons.add(importSettingsButton);

        body.add(rootLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(gameLabel);
        body.add(UiFactory.verticalGap(12));
        body.add(UiFactory.fieldLabel(Strings.get("settings.gameDataPath")));
        body.add(UiFactory.verticalGap(4));
        body.add(dataRow);
        body.add(UiFactory.verticalGap(6));
        body.add(dataButtons);
        body.add(UiFactory.verticalGap(4));
        body.add(dataHint);
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
        javaModeCombo.setSelectedItem(settings.javaMode == null || settings.javaMode.isBlank() ? "AUTO" : settings.javaMode);
        offlineMode.setSelected(settings.offlineMode);
        jvmArgsField.setText(settings.extraJvmArgs == null ? "" : settings.extraJvmArgs);
        gameArgsField.setText(settings.extraGameArgs == null ? "" : settings.extraGameArgs);
        envVarsField.setText(settings.extraEnvVars == null ? "" : settings.extraEnvVars);
        widthSpinner.setValue(settings.windowWidth);
        heightSpinner.setValue(settings.windowHeight);
        fullscreen.setSelected(settings.fullscreen);
        closeOnLaunch.setSelected(settings.closeOnLaunch);
        showConsole.setSelected(settings.showConsole);
        checkModsBeforeLaunch.setSelected(settings.checkModsBeforeLaunch);
        optimizedFlags.setSelected(settings.optimizedJvmFlags);
        downloadParallelismSpinner.setValue(settings.downloadParallelism);
        downloadPerHostSpinner.setValue(settings.downloadPerHost);
        metadataCacheEnabled.setSelected(settings.metadataCacheEnabled);
        metadataCacheTtlSpinner.setValue(settings.metadataCacheTtlMinutes);
        metadataCacheTtlSpinner.setEnabled(settings.metadataCacheEnabled);
        gameDataPathField.setText(settings.gameDataPath == null ? "" : settings.gameDataPath);
        widthSpinner.setEnabled(!settings.fullscreen);
        heightSpinner.setEnabled(!settings.fullscreen);
        updateJavaFieldsState();
    }

    private void updateJavaFieldsState() {
        boolean custom = "CUSTOM".equals(javaModeCombo.getSelectedItem());
        javaPathField.setEnabled(custom);
    }

    private void saveJavaPath() {
        String path = javaPathField.getText().trim();
        context.settings().javaPath = path;
        if (!path.isEmpty()) {
            context.settings().javaMode = "CUSTOM";
            javaModeCombo.setSelectedItem("CUSTOM");
        }
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
        context.settings().javaMode = "CUSTOM";
        javaModeCombo.setSelectedItem("CUSTOM");
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
        UiFactory.openInFileManager(this, path);
    }

    private void browseForGameDataDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("settings.gameDataPath"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            gameDataPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void applyGameDataPath(boolean move) {
        String value = gameDataPathField.getText().trim();
        if (value.isEmpty()) {
            context.settings().gameDataPath = "";
            context.settings().save();
            LauncherPaths.setGameDataDir(null);
            report(Strings.get("settings.gameDataApplied", context.paths().gameDir()));
            return;
        }
        Path target = Path.of(value).toAbsolutePath().normalize();
        Path source = context.paths().gameDir().toAbsolutePath().normalize();
        if (!Path.of(value).isAbsolute()) {
            JOptionPane.showMessageDialog(this, Strings.get("settings.gameDataNotAbsolute"),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (target.equals(source)) {
            return;
        }
        if (target.startsWith(source) || source.startsWith(target)) {
            JOptionPane.showMessageDialog(this, Strings.get("settings.gameDataNested"),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                Strings.get("settings.gameDataConfirm", target),
                Strings.get("common.confirm"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        report(move ? Strings.get("settings.gameDataMove") : Strings.get("settings.gameDataCopy"));
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                migrateGameData(source, target, move);
                return target;
            }

            @Override
            protected void done() {
                try {
                    Path applied = get();
                    context.settings().gameDataPath = applied.toString();
                    context.settings().save();
                    LauncherPaths.setGameDataDir(applied);
                    report(Strings.get("settings.gameDataApplied", applied));
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            Strings.get("settings.gameDataApplied", applied),
                            Strings.get("settings.gameDataPath"),
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(SettingsPanel.this,
                            cause.getMessage() == null ? cause.toString() : cause.getMessage(),
                            Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static void migrateGameData(Path source, Path target, boolean deleteSource)
            throws java.io.IOException {
        java.nio.file.Files.createDirectories(target);
        try (var walk = java.nio.file.Files.walk(source)) {
            for (Path entry : walk.toList()) {
                if (entry.equals(source)) {
                    continue;
                }
                Path destination = target.resolve(source.relativize(entry)).normalize();
                if (!destination.startsWith(target)) {
                    continue;
                }
                if (java.nio.file.Files.isSymbolicLink(entry)) {
                    try {
                        if (!java.nio.file.Files.exists(destination,
                                java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                            java.nio.file.Files.createSymbolicLink(destination,
                                    java.nio.file.Files.readSymbolicLink(entry));
                        }
                    } catch (IOException | UnsupportedOperationException ignored) {

                    }
                } else if (java.nio.file.Files.isDirectory(entry)) {
                    java.nio.file.Files.createDirectories(destination);
                } else if (java.nio.file.Files.isRegularFile(entry)) {
                    java.nio.file.Files.createDirectories(destination.getParent());
                    java.nio.file.Files.copy(entry, destination,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        if (deleteSource) {
            try (var walk = java.nio.file.Files.walk(source)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        java.nio.file.Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    private void exportLauncherSettings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("settings.exportSettings"));
        chooser.setSelectedFile(new File("agrolauncher-settings.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            java.util.Map<String, Object> wrapper = new java.util.LinkedHashMap<>();
            wrapper.put("format", 1);
            wrapper.put("launcherVersion",
                    by.agro.launcher.core.LauncherVersion.VERSION);
            wrapper.put("settings", context.settings());
            by.agro.launcher.core.Json.write(chooser.getSelectedFile().toPath(), wrapper);
            String message = Strings.get("settings.settingsExported",
                    chooser.getSelectedFile().getName());
            report(message);
            JOptionPane.showMessageDialog(this, message,
                    Strings.get("settings.exportSettings"),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void importLauncherSettings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("settings.importSettings"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            com.google.gson.JsonObject root = by.agro.launcher.core.Json
                    .readObject(chooser.getSelectedFile().toPath());
            if (root == null || !root.has("settings")) {
                throw new IOException("The file does not contain launcher settings");
            }
            Settings imported = by.agro.launcher.core.Json.GSON
                    .fromJson(root.get("settings"), Settings.class);
            if (imported == null) {
                throw new IOException("The file does not contain launcher settings");
            }
            int answer = JOptionPane.showConfirmDialog(this,
                    Strings.get("settings.importConfirm"),
                    Strings.get("settings.importSettings"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            for (java.lang.reflect.Field field : Settings.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(context.settings(), field.get(imported));
            }
            context.settings().save();
            JOptionPane.showMessageDialog(this,
                    Strings.get("settings.settingsImportedRestart"),
                    Strings.get("settings.importSettings"),
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    Strings.get("settings.importFailed", e.getMessage()),
                    Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    public void reload() {
        loadValues();
        refreshJavaInstallations();
        appearancePanel.reload();
        backupsPanel.refresh();
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }
}
