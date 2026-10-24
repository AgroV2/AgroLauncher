package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.auth.Account;
import by.agro.launcher.core.DownloadManager;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.SystemInfo;
import by.agro.launcher.diagnostics.DiagnosticExporter;
import by.agro.launcher.diagnostics.DiagnosticFinding;
import by.agro.launcher.diagnostics.DiagnosticReport;
import by.agro.launcher.launch.GameProcess;
import by.agro.launcher.loaders.LoaderInstaller;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.mods.ModBuildManager;
import by.agro.launcher.repair.RepairService;
import by.agro.launcher.ui.components.AccountHeadIcons;
import by.agro.launcher.ui.components.DownloadsPanel;
import by.agro.launcher.ui.components.GameConsole;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.net.URL;
import java.util.List;
import java.util.function.Consumer;

public final class PlayPanel extends JPanel {

    private static final String MARGYCRAFT_SERVER_ADDRESS = "craft.narez.xyz";
    private static final String MARGYCRAFT_VERSION = "26.3";
    private static final String MARGYCRAFT_ICON_RESOURCE = "/margycrafticon.png";

    private final LauncherContext context;
    private final Consumer<String> statusReporter;

    private final JLabel versionLabel = new JLabel();
    private final JLabel accountLabel = new JLabel();
    private final JLabel ramLabel = new JLabel();
    private final JLabel loaderLabel = new JLabel();
    private final JButton playButton = UiFactory.primaryButton(Strings.get("play.button"));
    private final JButton margyCraftButton = UiFactory.primaryButton("MargyCraft");
    private final JButton repairButton = UiFactory.primaryButton(Strings.get("repair.button"));
    private final JButton stopButton = UiFactory.dangerButton(Strings.get("play.stop"));
    private final JButton exportButton = UiFactory.linkButton(Strings.get("play.exportAnalysis"));
    private final javax.swing.JTextField quickServerField = new javax.swing.JTextField();
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel progressLabel = new JLabel(" ");
    private final GameConsole console = new GameConsole();
    private final DownloadsPanel downloadsPanel;
    private final JPanel downloadsContainer = UiFactory.transparentPanel();
    private final DownloadManager.Listener downloadsListener = this::refreshDownloadsVisibility;

    private volatile GameProcess runningProcess;
    private volatile GameProcess lastProcess;
    private volatile DiagnosticReport lastReport;
    private volatile boolean repairRunning;

    public PlayPanel(LauncherContext context, Consumer<String> statusReporter) {
        this.context = context;
        this.statusReporter = statusReporter;
        this.downloadsPanel = new DownloadsPanel(context.downloader().downloadManager());
        context.downloader().downloadManager().addListener(downloadsListener);

        setOpaque(false);
        setLayout(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

        add(buildTop(), BorderLayout.NORTH);
        add(buildConsoleSection(), BorderLayout.CENTER);

        refresh();
    }

    private JComponent buildTop() {
        JPanel top = UiFactory.transparentPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel title = UiFactory.title(Strings.get("app.name"));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);
        top.add(UiFactory.verticalGap(4));

        JLabel subtitle = UiFactory.subtitle(Strings.get("app.subtitle"));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(subtitle);
        top.add(UiFactory.verticalGap(18));

        top.add(buildSummaryCard());
        top.add(UiFactory.verticalGap(14));
        top.add(buildLaunchCard());
        return top;
    }

    private JComponent buildSummaryCard() {
        JPanel card = UiFactory.card();
        card.setLayout(new java.awt.GridLayout(1, 4, 24, 0));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        card.add(row(Strings.get("play.version"), versionLabel));
        card.add(row(Strings.get("play.loader"), loaderLabel));
        card.add(row(Strings.get("play.account"), accountLabel));
        card.add(row(Strings.get("play.memory"), ramLabel));
        return card;
    }

    private JComponent row(String caption, JLabel valueLabel) {
        JPanel panel = UiFactory.transparentPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel captionLabel = UiFactory.fieldLabel(caption);
        captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueLabel.setFont(AgroTheme.boldFont(14));
        valueLabel.setForeground(AgroTheme.textPrimary());
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        valueLabel.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);

        panel.add(captionLabel);
        panel.add(UiFactory.verticalGap(3));
        panel.add(valueLabel);
        return panel;
    }

    private JComponent buildLaunchCard() {
        JPanel card = UiFactory.card();
        card.setLayout(new BorderLayout(20, 10));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        playButton.setPreferredSize(new Dimension(210, 52));
        playButton.setFont(AgroTheme.boldFont(17));
        playButton.addActionListener(e -> launchGame(quickServerAddress()));

        margyCraftButton.setPreferredSize(new Dimension(190, 52));
        margyCraftButton.setToolTipText("Vanilla · " + (MARGYCRAFT_SERVER_ADDRESS.isBlank()
                ? Strings.get("play.quickServerAddressMissing") : MARGYCRAFT_SERVER_ADDRESS));
        configureMargyCraftIcon();
        margyCraftButton.addActionListener(e -> launchMargyCraft());

        repairButton.setPreferredSize(new Dimension(190, 52));
        repairButton.addActionListener(e -> repairInstallation());

        stopButton.setVisible(false);
        stopButton.addActionListener(e -> stopGame());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(playButton);
        buttons.add(margyCraftButton);
        buttons.add(repairButton);
        buttons.add(stopButton);

        progressBar.setStringPainted(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(100, 8));

        progressLabel.setFont(AgroTheme.font(12));
        progressLabel.setForeground(AgroTheme.textSecondary());

        JPanel progressArea = UiFactory.transparentPanel();
        progressArea.setLayout(new BoxLayout(progressArea, BoxLayout.Y_AXIS));
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressArea.add(progressLabel);
        progressArea.add(UiFactory.verticalGap(6));
        progressArea.add(progressBar);
        progressArea.add(UiFactory.verticalGap(8));

        JPanel serverRow = new JPanel(new BorderLayout(8, 0));
        serverRow.setOpaque(false);
        serverRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        quickServerField.putClientProperty("JTextField.placeholderText", "play.example.com");
        quickServerField.setToolTipText(Strings.get("play.quickServer"));
        quickServerField.addActionListener(e -> saveQuickServerAddress());
        quickServerField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveQuickServerAddress();
            }
        });
        serverRow.add(UiFactory.hint(Strings.get("play.quickServer")), BorderLayout.WEST);
        serverRow.add(quickServerField, BorderLayout.CENTER);
        progressArea.add(serverRow);

        card.add(buttons, BorderLayout.WEST);
        card.add(progressArea, BorderLayout.CENTER);
        return card;
    }

    private String quickServerAddress() {
        String value = quickServerField.getText();
        return value == null ? "" : value.trim();
    }

    private void saveQuickServerAddress() {
        context.settings().quickPlayServer = quickServerAddress();
        context.settings().save();
    }

    private JComponent buildConsoleSection() {
        JPanel panel = UiFactory.transparentPanel();
        panel.setLayout(new BorderLayout(0, 8));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel label = UiFactory.fieldLabel(Strings.get("play.console"));
        header.add(label, BorderLayout.WEST);

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        tools.setOpaque(false);
        exportButton.setEnabled(false);
        exportButton.addActionListener(e -> exportDiagnosticReport());
        tools.add(exportButton);
        JButton clearButton = UiFactory.linkButton(Strings.get("play.clear"));
        clearButton.addActionListener(e -> console.clear());
        tools.add(clearButton);
        header.add(tools, BorderLayout.EAST);

        downloadsContainer.setLayout(new BorderLayout());
        downloadsContainer.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        downloadsContainer.setPreferredSize(new Dimension(440, 100));
        downloadsContainer.add(downloadsPanel, BorderLayout.CENTER);

        JPanel content = UiFactory.transparentPanel();
        content.setLayout(new BorderLayout());
        content.add(console, BorderLayout.CENTER);
        content.add(downloadsContainer, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        refreshDownloadsVisibility();
        return panel;
    }

    private void refreshDownloadsVisibility() {
        SwingUtilities.invokeLater(() -> {
            boolean hasActiveDownloads = context.downloader().downloadManager().snapshot().stream()
                    .anyMatch(task -> task.status() == DownloadManager.Status.QUEUED
                            || task.status() == DownloadManager.Status.RUNNING
                            || task.status() == DownloadManager.Status.PAUSED);
            if (downloadsContainer.isVisible() != hasActiveDownloads) {
                downloadsContainer.setVisible(hasActiveDownloads);
                if (downloadsContainer.getParent() != null) {
                    downloadsContainer.getParent().revalidate();
                    downloadsContainer.getParent().repaint();
                }
            }
        });
    }

    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            String version = context.settings().selectedVersion;
            versionLabel.setText(version == null || version.isBlank() ? Strings.get("play.versionNotSelected") : version);

            LoaderType loader = LoaderType.fromId(context.settings().selectedLoader);
            String loaderVersion = context.settings().selectedLoaderVersion;
            loaderLabel.setText(loader == LoaderType.VANILLA
                    ? Strings.get("play.noMods")
                    : loader.displayName() + (loaderVersion.isBlank() ? "" : " " + loaderVersion));

            Account account = activeAccount();
            accountLabel.setText(account == null
                    ? Strings.get("play.accountNotSelected")
                    : account.username + " · " + account.type.displayName());
            javax.swing.Icon accountIcon = AccountHeadIcons.cached(account, context.settings());
            accountLabel.setIcon(accountIcon);
            accountLabel.setIconTextGap(accountIcon == null ? 0 : 8);
            accountLabel.setMinimumSize(null);
            accountLabel.setPreferredSize(null);
            if (account != null) {
                String requestedAccountId = account.id;
                AccountHeadIcons.load(account, context.settings(), icon -> {
                    Account current = activeAccount();
                    if (current != null && java.util.Objects.equals(requestedAccountId, current.id)) {
                        accountLabel.setIcon(icon);
                        accountLabel.setIconTextGap(icon == null ? 0 : 8);
                        accountLabel.revalidate();
                        accountLabel.repaint();
                    }
                });
            }

            ramLabel.setText(SystemInfo.formatMb(context.settings().maxRamMb));
            quickServerField.setText(context.settings().quickPlayServer == null
                    ? "" : context.settings().quickPlayServer);

            boolean versionReady = version != null && !version.isBlank();
            playButton.setEnabled(versionReady && account != null && runningProcess == null);
            repairButton.setEnabled(versionReady && runningProcess == null && !repairRunning);
        });
    }

    private Account activeAccount() {
        Account account = context.accounts().byId(context.settings().activeAccountId);
        if (account != null) {
            return account;
        }
        var all = context.accounts().accounts();
        return all.isEmpty() ? null : all.get(0);
    }

    private void configureMargyCraftIcon() {
        URL iconUrl = PlayPanel.class.getResource(MARGYCRAFT_ICON_RESOURCE);
        if (iconUrl == null) {
            return;
        }
        ImageIcon source = new ImageIcon(iconUrl);
        if (source.getIconWidth() <= 0 || source.getIconHeight() <= 0) {
            return;
        }
        int maxWidth = 28;
        int maxHeight = 28;
        double scale = Math.min((double) maxWidth / source.getIconWidth(),
                (double) maxHeight / source.getIconHeight());
        int width = Math.max(1, (int) Math.round(source.getIconWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getIconHeight() * scale));
        Image scaled = source.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        margyCraftButton.setIcon(new ImageIcon(scaled));
        margyCraftButton.setIconTextGap(8);
    }

    private void launchMargyCraft() {
        launchGame(MARGYCRAFT_SERVER_ADDRESS, null, LoaderType.VANILLA, true);
    }

    private String resolveLatestRelease() {
        try {
            by.agro.launcher.version.VersionManifest manifest = context.manifestIfLoaded() != null
                    ? context.manifestIfLoaded() : context.manifest();
            String latest = manifest.latestRelease();
            if (latest != null && !latest.isBlank()) {
                return latest;
            }
        } catch (Exception ignored) {
        }
        return MARGYCRAFT_VERSION;
    }

    private void launchGame(String quickPlayServer) {
        launchGame(quickPlayServer, context.settings().selectedVersion,
                LoaderType.fromId(context.settings().selectedLoader), false);
    }

    private void launchGame(String quickPlayServer, String mcVersion, LoaderType loaderType,
                            boolean forceVanillaDirectory) {
        Account account = activeAccount();

        if (account == null) {
            report(Strings.get("play.needAccount"));
            console.appendLauncherMessage(Strings.get("play.needAccount"));
            return;
        }
        boolean resolveVersionDynamically = mcVersion == null;
        if (mcVersion == null || mcVersion.isBlank()) {
            if (!resolveVersionDynamically) {
                report(Strings.get("play.needVersion"));
                console.appendLauncherMessage(Strings.get("play.needVersion"));
                return;
            }
            mcVersion = "";
        }

        final String requestedVersion = mcVersion;
        playButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        console.clear();
        console.appendLauncherMessage(Strings.get("play.preparing",
                requestedVersion.isBlank() ? "MargyCraft" : requestedVersion));

        ProgressListener listener = new ProgressListener() {
            @Override
            public void onProgress(String stage, long current, long total, String detail) {
                SwingUtilities.invokeLater(() -> {
                    progressLabel.setText(stage + (detail == null || detail.isBlank() ? "" : " — " + detail));
                    if (total > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setMaximum((int) Math.min(total, Integer.MAX_VALUE));
                        progressBar.setValue((int) Math.min(current, Integer.MAX_VALUE));
                    } else {
                        progressBar.setIndeterminate(true);
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                console.appendLauncherMessage(message);
            }
        };

        new SwingWorker<GameProcess, Void>() {
            @Override
            protected GameProcess doInBackground() throws Exception {
                String effectiveVersion = resolveVersionDynamically ? resolveLatestRelease() : requestedVersion;
                String versionToLaunch = loaderType == LoaderType.VANILLA
                        ? effectiveVersion
                        : installLoaderIfNeeded(effectiveVersion, listener);
                java.nio.file.Path gameDir;
                if (forceVanillaDirectory) {
                    gameDir = isolatedGameDir(effectiveVersion, LoaderType.VANILLA);
                } else {
                    ModBuildManager buildManager = new ModBuildManager(context.paths());
                    ModBuildManager.ModBuild activeBuild = buildManager.list().stream()
                            .filter(build -> build.id.equals(context.settings().selectedModBuildId))
                            .findFirst().orElse(null);
                    gameDir = activeBuild == null
                            ? isolatedGameDir(effectiveVersion, loaderType)
                            : buildManager.gameDir(activeBuild);
                }
                return context.gameLauncher().launch(
                        versionToLaunch,
                        account,
                        context.manifest(),
                        listener,
                        console::append,
                        exitCode -> SwingUtilities.invokeLater(() -> onGameExit(exitCode)),
                        gameDir,
                        quickPlayServer);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                progressBar.setIndeterminate(false);
                try {
                    runningProcess = get();
                    lastProcess = runningProcess;
                    stopButton.setVisible(true);
                    progressLabel.setText(Strings.get("play.startedPid", runningProcess.pid()));
                    console.appendLauncherMessage(Strings.get("play.started"));
                    report(Strings.get("play.started"));
                    if (context.settings().closeOnLaunch) {
                        SwingUtilities.getWindowAncestor(PlayPanel.this).setVisible(false);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    progressLabel.setText(Strings.get("play.launchError"));
                    console.appendLauncherMessage("ОШИБКА: " + message);
                    report(Strings.get("play.launchErrorDetails", message));
                    playButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void repairInstallation() {
        String mcVersion = context.settings().selectedVersion;
        if (repairRunning || runningProcess != null || mcVersion == null || mcVersion.isBlank()) {
            return;
        }
        LoaderType loaderType = LoaderType.fromId(context.settings().selectedLoader);
        String confirmation = Strings.get("repair.confirm", mcVersion, loaderType.displayName());
        if (JOptionPane.showConfirmDialog(this, confirmation, Strings.get("repair.title"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        repairRunning = true;
        playButton.setEnabled(false);
        repairButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressLabel.setText(Strings.get("repair.starting"));
        String loaderVersion = context.settings().selectedLoaderVersion == null
                || context.settings().selectedLoaderVersion.isBlank()
                ? Strings.get("repair.recommendedVersion")
                : " " + context.settings().selectedLoaderVersion;
        console.appendLauncherMessage(Strings.get("repair.started",
                mcVersion, loaderType.displayName(), loaderVersion));

        ProgressListener listener = new ProgressListener() {
            @Override
            public void onProgress(String stage, long current, long total, String detail) {
                SwingUtilities.invokeLater(() -> {
                    progressLabel.setText(stage + (detail == null || detail.isBlank() ? "" : " — " + detail));
                    if (total > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setMaximum((int) Math.min(total, Integer.MAX_VALUE));
                        progressBar.setValue((int) Math.min(current, Integer.MAX_VALUE));
                    } else {
                        progressBar.setIndeterminate(true);
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                console.appendLauncherMessage(message);
                SwingUtilities.invokeLater(() -> progressLabel.setText(message));
            }
        };

        new SwingWorker<RepairService.Result, Void>() {
            @Override
            protected RepairService.Result doInBackground() throws Exception {
                ModBuildManager buildManager = new ModBuildManager(context.paths());
                ModBuildManager.ModBuild activeBuild = buildManager.list().stream()
                        .filter(build -> build.id.equals(context.settings().selectedModBuildId))
                        .findFirst().orElse(null);
                RepairService.Request request = new RepairService.Request();
                request.minecraftVersion = mcVersion;
                request.loaderType = loaderType;
                request.loaderVersion = context.settings().selectedLoaderVersion;
                request.loaderInstaller = context.installer(loaderType);
                request.manifest = context.manifest();
                request.gameDir = activeBuild == null
                        ? isolatedGameDir(mcVersion, loaderType)
                        : buildManager.gameDir(activeBuild);
                return context.repairService().repair(request, listener);
            }

            @Override
            protected void done() {
                repairRunning = false;
                progressBar.setVisible(false);
                progressBar.setIndeterminate(false);
                try {
                    RepairService.Result result = get();
                    String message = Strings.get("repair.success", result.managedMods);
                    progressLabel.setText(message);
                    console.appendLauncherMessage(Strings.get("repair.completedDetails",
                            result.versionId, result.managedMods));
                    report(message);
                    JOptionPane.showMessageDialog(PlayPanel.this, message,
                            Strings.get("repair.title"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String detail = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    String message = Strings.get("repair.error", detail);
                    progressLabel.setText(message);
                    console.appendLauncherMessage(Strings.get("repair.consoleError", detail));
                    appendRepairStackTrace(cause);
                    report(message);
                    JOptionPane.showMessageDialog(PlayPanel.this, message,
                            Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
                } finally {
                    refresh();
                }
            }
        }.execute();
    }

    private void appendRepairStackTrace(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            console.append(current == error ? current.toString() : "Caused by: " + current);
            for (StackTraceElement element : current.getStackTrace()) {
                console.append("\tat " + element);
            }
        }
    }

    private java.nio.file.Path isolatedGameDir(String mcVersion, LoaderType loaderType) {
        String loaderVersion = context.settings().selectedLoaderVersion;
        if (loaderVersion == null || loaderVersion.isBlank()) {
            loaderVersion = "recommended";
        }
        return context.paths().installationGameDir(
                mcVersion,
                loaderType.id(),
                loaderType == LoaderType.VANILLA ? "default" : loaderVersion);
    }

    private String installLoaderIfNeeded(String mcVersion, ProgressListener listener) throws Exception {
        LoaderType loaderType = LoaderType.fromId(context.settings().selectedLoader);
        if (loaderType == LoaderType.VANILLA) {
            return mcVersion;
        }
        LoaderInstaller installer = context.installer(loaderType);
        if (installer == null) {
            listener.onMessage(Strings.get("play.loaderUnavailable", loaderType.displayName()));
            return mcVersion;
        }
        String loaderVersion = context.settings().selectedLoaderVersion;
        boolean recommended = loaderVersion == null || loaderVersion.isBlank();
        listener.onMessage(Strings.get("play.installingLoader", loaderType.displayName())
                + (recommended ? Strings.get("play.loaderRecommended") : " " + loaderVersion));
        return installer.install(mcVersion, recommended ? null : loaderVersion, listener);
    }

    private void onGameExit(int exitCode) {
        GameProcess completed = lastProcess;
        runningProcess = null;
        if (completed != null) {
            lastReport = completed.diagnosticReport();
            exportButton.setEnabled(lastReport != null);
            if (lastReport != null && !lastReport.findings.isEmpty()) {
                console.appendLauncherMessage("Диагностика:");
                for (DiagnosticFinding finding : lastReport.findings) {
                    console.appendLauncherMessage(finding.toString());
                }
            }
        }
        stopButton.setVisible(false);
        playButton.setEnabled(true);
        progressLabel.setText(Strings.get("play.exited", exitCode));
        console.appendLauncherMessage(Strings.get("play.processExited", exitCode));
        report(Strings.get("play.exited", exitCode));

        java.awt.Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null && !window.isVisible()) {
            window.setVisible(true);
        }
    }

    private void exportDiagnosticReport() {
        DiagnosticReport report = lastReport;
        if (report == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Analysis export");
        chooser.setSelectedFile(new java.io.File("agrolauncher-diagnostic.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path destination = chooser.getSelectedFile().toPath();
        exportButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (destination.getFileName().toString().toLowerCase().endsWith(".json")) {
                    DiagnosticExporter.exportJson(report, destination);
                } else {
                    DiagnosticExporter.exportTxt(report, destination);
                }
                return null;
            }

            @Override
            protected void done() {
                exportButton.setEnabled(true);
                try {
                    get();
                    console.appendLauncherMessage("Analysis exported.");
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(PlayPanel.this,
                            Strings.get("play.exportAnalysisError", cause.getMessage()),
                            "Export error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void stopGame() {
        GameProcess process = runningProcess;
        if (process == null) {
            return;
        }
        console.appendLauncherMessage(Strings.get("play.stopping"));
        process.terminate();
        new javax.swing.Timer(4000, e -> {
            GameProcess current = runningProcess;
            if (current != null && current.isRunning()) {
                current.kill();
                console.appendLauncherMessage(Strings.get("play.killed"));
            }
            ((javax.swing.Timer) e.getSource()).stop();
        }).start();
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }

    public boolean isGameRunning() {
        GameProcess process = runningProcess;
        return process != null && process.isRunning();
    }

    public GameConsole console() {
        return console;
    }
}
