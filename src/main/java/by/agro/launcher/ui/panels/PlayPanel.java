package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.auth.Account;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.SystemInfo;
import by.agro.launcher.launch.GameProcess;
import by.agro.launcher.loaders.LoaderInstaller;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.mods.ModBuildManager;
import by.agro.launcher.ui.components.GameConsole;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;

public final class PlayPanel extends JPanel {

    private final LauncherContext context;
    private final Consumer<String> statusReporter;

    private final JLabel versionLabel = new JLabel();
    private final JLabel accountLabel = new JLabel();
    private final JLabel ramLabel = new JLabel();
    private final JLabel loaderLabel = new JLabel();
    private final JButton playButton = UiFactory.primaryButton(Strings.get("play.button"));
    private final JButton stopButton = UiFactory.dangerButton(Strings.get("play.stop"));
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel progressLabel = new JLabel(" ");
    private final GameConsole console = new GameConsole();

    private volatile GameProcess runningProcess;

    public PlayPanel(LauncherContext context, Consumer<String> statusReporter) {
        this.context = context;
        this.statusReporter = statusReporter;

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
        playButton.addActionListener(e -> launchGame());

        stopButton.setVisible(false);
        stopButton.addActionListener(e -> stopGame());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(playButton);
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

        card.add(buttons, BorderLayout.WEST);
        card.add(progressArea, BorderLayout.CENTER);
        return card;
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
        JButton clearButton = UiFactory.linkButton(Strings.get("play.clear"));
        clearButton.addActionListener(e -> console.clear());
        tools.add(clearButton);
        header.add(tools, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);
        panel.add(console, BorderLayout.CENTER);
        return panel;
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

            ramLabel.setText(SystemInfo.formatMb(context.settings().maxRamMb));

            boolean ready = version != null && !version.isBlank() && account != null;
            playButton.setEnabled(ready && runningProcess == null);
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

    private void launchGame() {
        Account account = activeAccount();
        String mcVersion = context.settings().selectedVersion;

        if (account == null) {
            report(Strings.get("play.needAccount"));
            console.appendLauncherMessage(Strings.get("play.needAccount"));
            return;
        }
        if (mcVersion == null || mcVersion.isBlank()) {
            report(Strings.get("play.needVersion"));
            console.appendLauncherMessage(Strings.get("play.needVersion"));
            return;
        }

        playButton.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        console.clear();
        console.appendLauncherMessage(Strings.get("play.preparing", mcVersion));

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
                String versionToLaunch = installLoaderIfNeeded(mcVersion, listener);
                ModBuildManager buildManager = new ModBuildManager(context.paths());
                ModBuildManager.ModBuild activeBuild = buildManager.list().stream()
                        .filter(build -> build.id.equals(context.settings().selectedModBuildId))
                        .findFirst().orElse(null);
                java.nio.file.Path gameDir = activeBuild == null
                        ? context.paths().gameDir() : buildManager.gameDir(activeBuild);
                return context.gameLauncher().launch(
                        versionToLaunch,
                        account,
                        context.manifest(),
                        listener,
                        console::append,
                        exitCode -> SwingUtilities.invokeLater(() -> onGameExit(exitCode)),
                        gameDir);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                progressBar.setIndeterminate(false);
                try {
                    runningProcess = get();
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
        listener.onMessage(Strings.get("play.installingLoader", loaderType.displayName())
                + (loaderVersion.isBlank() ? Strings.get("play.loaderRecommended") : " " + loaderVersion));
        return installer.install(mcVersion, loaderVersion.isBlank() ? null : loaderVersion, listener);
    }

    private void onGameExit(int exitCode) {
        runningProcess = null;
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
