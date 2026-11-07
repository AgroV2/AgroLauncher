package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.Settings;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.instances.InstanceCloner;
import by.agro.launcher.mods.ModCompatibilityChecker;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.mods.ModBuildManager;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;
import com.google.gson.JsonObject;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class InstancesPanel extends JPanel {

    private static final java.util.concurrent.ExecutorService SCANNER =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "agro-instance-scanner");
                t.setDaemon(true);
                return t;
            });
    private final java.util.concurrent.atomic.AtomicLong refreshGeneration = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong cacheGeneration = new java.util.concurrent.atomic.AtomicLong();
    private final Object cacheGuard = new Object();
    private final LauncherContext context;
    private final Consumer<String> statusReporter;
    private final Runnable onSelectionApplied;
    record ScanResult(List<Installation> installations, List<ModBuildManager.ModBuild> builds) { }
    private final java.util.function.Supplier<ScanResult> scanLoader;

    private final DefaultListModel<Installation> installationsModel = new DefaultListModel<>();
    private final JList<Installation> installationsList = new JList<>(installationsModel);
    private final DefaultListModel<ModBuildManager.ModBuild> buildsModel = new DefaultListModel<>();
    private final JList<ModBuildManager.ModBuild> buildsList = new JList<>(buildsModel);
    private final JButton playInstallationButton = UiFactory.primaryButton(Strings.get("instances.play"));
    private final JButton openInstallationButton = UiFactory.button(Strings.get("common.openFolder"));
    private final JButton worldsButton = UiFactory.button(Strings.get("instances.worlds"));
    private final JButton screenshotsButton = UiFactory.button(Strings.get("instances.screenshots"));
    private final JButton deleteInstallationButton = UiFactory.dangerButton(Strings.get("common.delete"));
    private final JButton playBuildButton = UiFactory.primaryButton(Strings.get("instances.play"));
    private final JButton openBuildButton = UiFactory.button(Strings.get("common.openFolder"));
    private final JButton cloneInstallationButton = UiFactory.button(Strings.get("clone.button"));
    private final JButton compatInstallationButton = UiFactory.button(Strings.get("compat.button"));
    private final JButton worldsBuildButton = UiFactory.button(Strings.get("instances.worlds"));
    private final JButton cloneBuildButton = UiFactory.button(Strings.get("clone.button"));
    private final JButton compatBuildButton = UiFactory.button(Strings.get("compat.button"));
    private final JLabel summaryLabel = new JLabel(" ");
    private final JTabbedPane tabs = new JTabbedPane();
    private PlayStatsPanel statsPanel;

    public InstancesPanel(LauncherContext context, Consumer<String> statusReporter,
                          Runnable onSelectionApplied) {
        this(context, statusReporter, onSelectionApplied, null);
    }

    InstancesPanel(LauncherContext context, Consumer<String> statusReporter, Runnable onSelectionApplied,
                   java.util.function.Supplier<ScanResult> scanLoader) {
        this.context = context;
        this.statusReporter = statusReporter;
        this.onSelectionApplied = onSelectionApplied;
        this.scanLoader = scanLoader != null ? scanLoader : () -> new ScanResult(scanInstallations(),
                new ModBuildManager(context.paths()).list());

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(26, 30, 20, 30));

        JPanel header = UiFactory.transparentPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = UiFactory.title(Strings.get("instances.title"));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = UiFactory.subtitle(Strings.get("instances.subtitle"));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(title);
        header.add(UiFactory.verticalGap(4));
        header.add(subtitle);
        header.add(UiFactory.verticalGap(14));

        tabs.setOpaque(false);
        tabs.addTab(Strings.get("instances.installations"), buildInstallationsTab());
        tabs.addTab(Strings.get("instances.builds"), buildBuildsTab());
        if (context != null) {
            statsPanel = new PlayStatsPanel(new by.agro.launcher.stats.PlayStatsStore(context.paths().root()),
                    this::statsLabel);
            tabs.addTab(Strings.get("stats.tab"), statsPanel);
            tabs.addChangeListener(e -> {
                if (tabs.getSelectedComponent() == statsPanel) statsPanel.refresh();
            });
        }

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        updateActionButtons();
        refresh();
    }

    private JComponent buildInstallationsTab() {
        JPanel card = UiFactory.transparentPanel();
        card.setLayout(new BorderLayout(0, 10));

        installationsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installationsList.setOpaque(false);
        installationsList.setCellRenderer(new InstallationRenderer());
        installationsList.setFixedCellHeight(56);
        installationsList.addListSelectionListener(e -> updateActionButtons());

        playInstallationButton.addActionListener(e -> playSelectedInstallation());
        openInstallationButton.addActionListener(e -> openSelectedInstallation());
        worldsButton.addActionListener(e -> openWorlds(selectedInstallationTarget()));
        screenshotsButton.addActionListener(e -> openContentDialog(true));
        deleteInstallationButton.addActionListener(e -> deleteSelectedInstallation());
        cloneInstallationButton.addActionListener(e -> cloneTarget(selectedInstallationTarget()));
        compatInstallationButton.addActionListener(e -> checkCompatibility(selectedInstallationTarget()));

        JButton reload = UiFactory.button(Strings.get("common.refresh"));
        reload.addActionListener(e -> refresh());
        JPanel actions = actionRows(
                new JButton[]{playInstallationButton, openInstallationButton, worldsButton, screenshotsButton},
                new JButton[]{cloneInstallationButton, compatInstallationButton, deleteInstallationButton, reload});

        JPanel bottom = UiFactory.transparentPanel();
        bottom.setLayout(new BorderLayout(0, 8));
        summaryLabel.setFont(AgroTheme.font(12));
        summaryLabel.setForeground(AgroTheme.textSecondary());
        bottom.add(summaryLabel, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);

        card.add(UiFactory.scroll(installationsList), BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildBuildsTab() {
        JPanel card = UiFactory.transparentPanel();
        card.setLayout(new BorderLayout(0, 10));

        buildsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        buildsList.setOpaque(false);
        buildsList.setCellRenderer(new BuildRenderer());
        buildsList.setFixedCellHeight(56);
        buildsList.addListSelectionListener(e -> updateActionButtons());

        playBuildButton.addActionListener(e -> playSelectedBuild());
        openBuildButton.addActionListener(e -> openSelectedBuild());
        worldsBuildButton.addActionListener(e -> openWorlds(selectedBuildTarget()));
        cloneBuildButton.addActionListener(e -> cloneTarget(selectedBuildTarget()));
        compatBuildButton.addActionListener(e -> checkCompatibility(selectedBuildTarget()));

        JButton reload = UiFactory.button(Strings.get("common.refresh"));
        reload.addActionListener(e -> refresh());
        JPanel actions = actionRows(
                new JButton[]{playBuildButton, openBuildButton, worldsBuildButton},
                new JButton[]{cloneBuildButton, compatBuildButton, reload});
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(UiFactory.scroll(buildsList), BorderLayout.CENTER);
        JPanel buildsBottom = UiFactory.transparentPanel();
        buildsBottom.setLayout(new BoxLayout(buildsBottom, BoxLayout.Y_AXIS));
        JLabel buildsHint = UiFactory.hint(Strings.get("instances.buildsEmpty"));
        buildsHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        buildsBottom.add(UiFactory.verticalGap(8));
        buildsBottom.add(actions);
        buildsBottom.add(UiFactory.verticalGap(6));
        buildsBottom.add(buildsHint);
        card.add(buildsBottom, BorderLayout.SOUTH);
        return card;
    }

    public void refresh() {
        long generation = refreshGeneration.incrementAndGet();
        SCANNER.execute(() -> {
            if (generation != refreshGeneration.get()) return;
            final ScanResult result;
            try { result = scanLoader.get(); }
            catch (RuntimeException error) {
                SwingUtilities.invokeLater(() -> {
                    if (generation == refreshGeneration.get()) report(Strings.get("instances.scanFailed", error.getMessage()));
                });
                return;
            }
            List<Installation> installations = result.installations();
            List<ModBuildManager.ModBuild> builds = result.builds();
            Path spaceTarget = context == null ? null : context.paths().instancesDir();
            long freeBytes = spaceTarget == null ? -1 : by.agro.launcher.core.DiskSpace.usableBytes(spaceTarget);
            SwingUtilities.invokeLater(() -> {
                if (generation != refreshGeneration.get()) return;
                Path selectedPath = installationsList.getSelectedValue() == null
                        ? null : installationsList.getSelectedValue().gameDir;
                String selectedBuild = buildsList.getSelectedValue() == null
                        ? null : buildsList.getSelectedValue().id;
                installationsModel.clear();
                for (Installation installation : installations) installationsModel.addElement(installation);
                buildsModel.clear();
                for (ModBuildManager.ModBuild build : builds) buildsModel.addElement(build);
                lastInstallations = List.copyOf(installations);
                lastBuilds = List.copyOf(builds);
                String summary = installations.isEmpty() ? Strings.get("instances.empty")
                        : Strings.get("instances.summary", installations.size());
                boolean low = freeBytes >= 0 && freeBytes < by.agro.launcher.core.DiskSpace.LOW_SPACE_BYTES;
                if (freeBytes >= 0) {
                    summary += " · " + Strings.get(low ? "disk.freeLow" : "disk.free",
                            by.agro.launcher.core.DiskSpace.format(freeBytes));
                }
                summaryLabel.setText(summary);
                summaryLabel.setForeground(low ? AgroTheme.warning() : AgroTheme.textSecondary());
                if (statsPanel != null && tabs.getSelectedComponent() == statsPanel) statsPanel.refresh();
                for (int i = 0; i < installationsModel.size(); i++) {
                    Installation candidate = installationsModel.get(i);
                    if ((selectedPath != null && candidate.gameDir.equals(selectedPath))
                            || (selectedPath == null && i == 0)) {
                        installationsList.setSelectedIndex(i);
                        break;
                    }
                }
                if (selectedBuild != null) {
                    for (int i = 0; i < buildsModel.size(); i++) {
                        if (selectedBuild.equals(buildsModel.get(i).id)) {
                            buildsList.setSelectedIndex(i);
                            break;
                        }
                    }
                }
                updateActionButtons();
            });
        });
    }

    private void updateActionButtons() {
        boolean installationSelected = installationsList.getSelectedValue() != null;
        playInstallationButton.setEnabled(installationSelected);
        openInstallationButton.setEnabled(installationSelected);
        worldsButton.setEnabled(installationSelected);
        screenshotsButton.setEnabled(installationSelected);
        deleteInstallationButton.setEnabled(installationSelected);
        Installation installation = installationsList.getSelectedValue();
        boolean horizon = installation != null && LoaderType.fromId(installation.loaderId) == LoaderType.HORIZON;
        cloneInstallationButton.setEnabled(installationSelected && !horizon);
        cloneInstallationButton.setToolTipText(horizon ? Strings.get("clone.horizonUnsupported") : null);
        compatInstallationButton.setEnabled(installationSelected);

        boolean buildSelected = buildsList.getSelectedValue() != null;
        playBuildButton.setEnabled(buildSelected);
        openBuildButton.setEnabled(buildSelected);
        worldsBuildButton.setEnabled(buildSelected);
        cloneBuildButton.setEnabled(buildSelected);
        compatBuildButton.setEnabled(buildSelected);
    }

    private static JPanel actionRows(JButton[] first, JButton[] second) {
        JPanel rows = UiFactory.transparentPanel();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        for (JButton[] row : new JButton[][]{first, second}) {
            JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            line.setOpaque(false);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (JButton button : row) line.add(button);
            rows.add(line);
            if (row == first) rows.add(UiFactory.verticalGap(6));
        }
        return rows;
    }


    record Target(String title, Path gameDir, String minecraftVersion, String loaderId, String loaderVersion) { }

    private volatile List<Installation> lastInstallations = List.of();
    private volatile List<ModBuildManager.ModBuild> lastBuilds = List.of();

    private Target selectedInstallationTarget() {
        Installation selected = installationsList.getSelectedValue();
        if (selected == null) return null;
        return new Target(selected.title(), selected.gameDir, selected.minecraftVersion, selected.loaderId,
                selected.loaderVersion);
    }

    private Target selectedBuildTarget() {
        ModBuildManager.ModBuild selected = buildsList.getSelectedValue();
        if (selected == null || context == null) return null;
        try {
            return new Target(selected.name, new ModBuildManager(context.paths()).gameDir(selected),
                    selected.minecraftVersion, selected.loader, selected.loaderVersion);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private void openWorlds(Target target) {
        if (target == null) return;
        new WorldsDialog(SwingUtilities.getWindowAncestor(this), target.title(),
                target.gameDir().resolve("saves")).setVisible(true);
    }

    private void cloneTarget(Target target) {
        if (target == null || context == null) return;
        InstanceCloner.Source source = new InstanceCloner.Source(target.gameDir(), target.minecraftVersion(),
                target.loaderId(), target.loaderVersion());
        if (!InstanceCloner.supported(source)) {
            JOptionPane.showMessageDialog(this, Strings.get("clone.horizonUnsupported"),
                    Strings.get("common.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path buildsRoot = new ModBuildManager(context.paths()).buildsDir();
        CloneInstanceDialog.Choice choice = new CloneInstanceDialog(SwingUtilities.getWindowAncestor(this),
                target.title(), target.gameDir(), buildsRoot).ask();
        if (choice == null) return;
        report(Strings.get("clone.running", target.title()));
        setCloneButtonsEnabled(false);
        new javax.swing.SwingWorker<ModBuildManager.ModBuild, Void>() {
            @Override
            protected ModBuildManager.ModBuild doInBackground() throws Exception {
                return new InstanceCloner(context.paths()).cloneToBuild(source, choice.name(), choice.parts());
            }

            @Override
            protected void done() {
                setCloneButtonsEnabled(true);
                updateActionButtons();
                try {
                    ModBuildManager.ModBuild build = get();
                    report(Strings.get("clone.done", build.name));
                    tabs.setSelectedIndex(1);
                    refresh();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(InstancesPanel.this,
                            Strings.get("clone.failed", cause.getMessage() == null ? cause.toString() : cause.getMessage()),
                            Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void setCloneButtonsEnabled(boolean enabled) {
        cloneInstallationButton.setEnabled(enabled);
        cloneBuildButton.setEnabled(enabled);
    }

    private void checkCompatibility(Target target) {
        if (target == null) return;
        compatInstallationButton.setEnabled(false);
        compatBuildButton.setEnabled(false);
        report(Strings.get("compat.running", target.title()));
        new javax.swing.SwingWorker<ModCompatibilityChecker.Report, Void>() {
            @Override
            protected ModCompatibilityChecker.Report doInBackground() {
                return ModCompatibilityChecker.check(target.gameDir(), target.minecraftVersion(),
                        LoaderType.fromId(target.loaderId()), target.loaderVersion());
            }

            @Override
            protected void done() {
                updateActionButtons();
                try {
                    ModCompatibilityChecker.Report result = get();
                    report(result.summary());
                    CompatibilityDialog.show(InstancesPanel.this, target.title(), result);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(InstancesPanel.this, String.valueOf(cause.getMessage()),
                            Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }


    private String statsLabel(String key) {
        if (context == null) return key;
        Path path = new by.agro.launcher.stats.PlayStatsStore(context.paths().root()).pathFor(key);
        for (Installation installation : lastInstallations) {
            if (installation.gameDir.toAbsolutePath().normalize().equals(path)) return installation.title();
        }
        ModBuildManager builds = new ModBuildManager(context.paths());
        for (ModBuildManager.ModBuild build : lastBuilds) {
            try {
                if (builds.gameDir(build).toAbsolutePath().normalize().equals(path)) {
                    return Strings.get("stats.buildLabel", build.name);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (path.toAbsolutePath().normalize().equals(context.paths().gameDir().toAbsolutePath().normalize())) {
            return Strings.get("stats.sharedDir");
        }
        return Strings.get("stats.removed", key);
    }

    private volatile List<Installation> installationCache;
    private volatile long installationCacheAtMillis;
    private static final long INSTALLATION_CACHE_TTL_MS = 5_000;

    private List<Installation> scanInstallations() {
        long cacheVersion = cacheGeneration.get();
        List<Installation> cached = installationCache;
        if (cached != null
                && System.currentTimeMillis() - installationCacheAtMillis < INSTALLATION_CACHE_TTL_MS) {
            return cached.stream().map(i -> new Installation(i.minecraftVersion, i.loaderId,
                    i.loaderVersion, i.gameDir, i.modpackName, i.modpackVersion, readPlaytimeMs(i.gameDir))).toList();
        }
        List<Installation> result = new ArrayList<>();
        Path installationsRoot = context.paths().instancesDir().resolve("installations");
        if (!Files.isDirectory(installationsRoot)) {
            return result;
        }
        try (var minecraftVersions = Files.list(installationsRoot)) {
            for (Path minecraftDir : minecraftVersions.filter(Files::isDirectory).toList()) {
                try (var loaders = Files.list(minecraftDir)) {
                    for (Path loaderDir : loaders.filter(Files::isDirectory).toList()) {
                        try (var versions = Files.list(loaderDir)) {
                            for (Path gameDir : versions.filter(Files::isDirectory).toList()) {
                                result.add(toInstallation(minecraftDir.getFileName().toString(),
                                        loaderDir.getFileName().toString(),
                                        gameDir.getFileName().toString(), gameDir));
                            }
                        } catch (IOException ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        result.sort(Comparator.comparing((Installation i) -> i.minecraftVersion).reversed()
                .thenComparing(i -> i.gameDir.toString()));
        synchronized (cacheGuard) {
            if (cacheVersion == cacheGeneration.get()) {
                installationCacheAtMillis = System.currentTimeMillis();
                installationCache = new ArrayList<>(result);
            }
        }
        return result;
    }

    private long readPlaytimeMs(Path gameDir) {
        try {
            Path marker = gameDir.resolve(".agrolauncher-playtime.json");
            if (!Files.isRegularFile(marker)) {
                return 0;
            }
            var playtime = by.agro.launcher.core.Json.read(marker,
                    by.agro.launcher.launch.GameLauncher.PlaytimeMarker.class);
            return playtime == null ? 0 : Math.max(0, playtime.totalMs);
        } catch (Exception e) {
            return 0;
        }
    }

    private Installation toInstallation(String minecraftVersion, String loaderId,
                                        String loaderVersion, Path gameDir) {
        String displayName = null;
        String modpackVersion = null;
        Path marker = gameDir.resolve(".agrolauncher-modpack.json");
        if (Files.isRegularFile(marker)) {
            try {
                JsonObject parsed = Json.readObject(marker);
                displayName = Json.string(parsed, "name", null);
                modpackVersion = Json.string(parsed, "versionId", null);
            } catch (Exception ignored) {
            }
        }
        return new Installation(minecraftVersion, loaderId, loaderVersion, gameDir,
                displayName, modpackVersion, readPlaytimeMs(gameDir));
    }

    private void playSelectedInstallation() {
        Installation selected = installationsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        Settings settings = context.settings();
        settings.selectedVersion = selected.minecraftVersion;
        settings.selectedLoader = selected.loaderId;
        settings.selectedLoaderVersion = "default".equals(selected.loaderVersion)
                ? "" : selected.loaderVersion;
        settings.selectedModBuildId = "";
        settings.save();
        report(Strings.get("instances.selected", selected.title()));
        if (onSelectionApplied != null) {
            onSelectionApplied.run();
        }
    }

    private void openSelectedInstallation() {
        Installation selected = installationsList.getSelectedValue();
        if (selected != null) {
            openFolder(selected.gameDir);
        }
    }

    private void deleteSelectedInstallation() {
        Installation selected = installationsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (JOptionPane.showConfirmDialog(this, Strings.get("instances.deleteConfirm", selected.title()),
                Strings.get("common.confirm"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            by.agro.launcher.core.SecureFiles.rejectSymlinkParents(selected.gameDir);
            if (Files.exists(selected.gameDir, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                by.agro.launcher.core.SecureFiles.rejectSymbolicLink(selected.gameDir,
                        "Instance directory");
            }
            deleteTree(selected.gameDir);
            synchronized (cacheGuard) {
                cacheGeneration.incrementAndGet();
                installationCache = null;
            }
            report(Strings.get("instances.deleted", selected.title()));
            refresh();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), Strings.get("common.error"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void playSelectedBuild() {
        ModBuildManager.ModBuild selected = buildsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        Settings settings = context.settings();
        settings.selectedModBuildId = selected.id;
        settings.selectedVersion = selected.minecraftVersion;
        settings.selectedLoader = selected.loader;
        settings.selectedLoaderVersion = selected.loaderVersion == null ? "" : selected.loaderVersion;
        settings.save();
        report(Strings.get("builds.selected", selected.name));
        if (onSelectionApplied != null) {
            onSelectionApplied.run();
        }
    }

    private void openSelectedBuild() {
        ModBuildManager.ModBuild selected = buildsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        try {
            openFolder(new ModBuildManager(context.paths()).gameDir(selected));
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), Strings.get("common.error"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openFolder(Path path) {
        UiFactory.openInFileManager(this, path);
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }

    public static final class Installation {
        public final String minecraftVersion;
        public final String loaderId;
        public final String loaderVersion;
        public final Path gameDir;
        public final String modpackName;
        public final String modpackVersion;
        public final long playtimeMs;

        Installation(String minecraftVersion, String loaderId, String loaderVersion,
                     Path gameDir, String modpackName, String modpackVersion, long playtimeMs) {
            this.minecraftVersion = minecraftVersion;
            this.loaderId = loaderId;
            this.loaderVersion = loaderVersion;
            this.gameDir = gameDir;
            this.modpackName = modpackName;
            this.modpackVersion = modpackVersion;
            this.playtimeMs = playtimeMs;
        }

        public String formattedPlaytime() {
            if (playtimeMs <= 0) {
                return "";
            }
            long totalMinutes = playtimeMs / 60000L;
            return Strings.get("instances.playtime", totalMinutes / 60, totalMinutes % 60);
        }

        public String title() {
            if (LoaderType.fromId(loaderId) == LoaderType.HORIZON) return "26.3 Rust · Horizon";
            String base = modpackName != null && !modpackName.isBlank()
                    ? modpackName : "Minecraft " + minecraftVersion;
            LoaderType loader = LoaderType.fromId(loaderId);
            String loaderText = loader == LoaderType.VANILLA ? "Vanilla" : loader.displayName();
            if (!"default".equals(loaderVersion) && !loaderVersion.isBlank()) {
                loaderText += " " + loaderVersion;
            }
            if (modpackVersion != null && !modpackVersion.isBlank()) {
                base += " " + modpackVersion;
            }
            return base + " · " + loaderText;
        }

        @Override
        public String toString() {
            return title();
        }
    }

    private void openContentDialog(boolean screenshots) {
        Installation selected = installationsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        if (!screenshots) {
            openWorlds(selectedInstallationTarget());
            return;
        }
        Path folder = selected.gameDir.resolve("screenshots");
        new InstanceContentDialog(javax.swing.SwingUtilities.getWindowAncestor(this),
                folder, screenshots).setVisible(true);
    }

    private static final class InstanceContentDialog extends javax.swing.JDialog {
        private final Path folder;
        private final boolean screenshots;
        private final DefaultListModel<Path> model = new DefaultListModel<>();
        private final JList<Path> list = new JList<>(model);
        private final JLabel previewLabel = new JLabel();

        InstanceContentDialog(java.awt.Window owner, Path folder, boolean screenshots) {
            super(owner, screenshots ? Strings.get("instances.screenshots")
                    : Strings.get("instances.worlds"),
                    java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            this.folder = folder;
            this.screenshots = screenshots;
            setSize(760, 500);
            setLocationRelativeTo(owner);

            JPanel content = new JPanel(new BorderLayout(10, 10));
            content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setCellRenderer(new EntryRenderer());
            list.setFixedCellHeight(52);
            list.addListSelectionListener(e -> updatePreview());
            content.add(new javax.swing.JScrollPane(list), BorderLayout.CENTER);

            if (screenshots) {
                previewLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
                previewLabel.setPreferredSize(new Dimension(300, 0));
                previewLabel.setVerticalAlignment(javax.swing.SwingConstants.CENTER);
                content.add(previewLabel, BorderLayout.EAST);
            }

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton openFolderButton = UiFactory.button(Strings.get("common.openFolder"));
            openFolderButton.addActionListener(e -> openFolder());
            JButton openFileButton = UiFactory.button(Strings.get("instances.openFile"));
            openFileButton.addActionListener(e -> openSelected());
            JButton deleteButton = UiFactory.dangerButton(Strings.get("common.delete"));
            deleteButton.addActionListener(e -> deleteSelected());
            JButton closeButton = UiFactory.button(Strings.get("common.ok"));
            closeButton.addActionListener(e -> dispose());
            buttons.add(openFolderButton);
            buttons.add(openFileButton);
            buttons.add(deleteButton);
            buttons.add(closeButton);
            content.add(buttons, BorderLayout.SOUTH);

            setContentPane(content);
            loadEntries();
        }

        private void loadEntries() {
            model.clear();
            previewLabel.setIcon(null);
            try {
                if (Files.isDirectory(folder)) {
                    try (var stream = Files.list(folder)) {
                        var entries = stream.filter(this::isEntry)
                                .sorted(java.util.Comparator.comparingLong(
                                        InstanceContentDialog::lastModified).reversed())
                                .toList();
                        for (Path entry : entries) {
                            model.addElement(entry);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }

        private boolean isEntry(Path path) {
            String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (screenshots) {
                return Files.isRegularFile(path)
                        && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"));
            }
            return Files.isDirectory(path) && Files.isRegularFile(path.resolve("level.dat"));
        }

        private static long lastModified(Path path) {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return 0;
            }
        }

        private void updatePreview() {
            if (!screenshots) {
                return;
            }
            Path selected = list.getSelectedValue();
            previewLabel.setIcon(null);
            if (selected == null) {
                return;
            }
            try {
                javax.swing.ImageIcon icon = new javax.swing.ImageIcon(selected.toString());
                if (icon.getIconWidth() > 0) {
                    double scale = Math.min(280.0 / icon.getIconWidth(),
                            340.0 / icon.getIconHeight());
                    java.awt.Image scaled = icon.getImage().getScaledInstance(
                            Math.max(1, (int) Math.round(icon.getIconWidth() * scale)),
                            Math.max(1, (int) Math.round(icon.getIconHeight() * scale)),
                            java.awt.Image.SCALE_SMOOTH);
                    previewLabel.setIcon(new javax.swing.ImageIcon(scaled));
                }
            } catch (RuntimeException ignored) {
            }
        }

        private void openFolder() {
            UiFactory.openInFileManager(this, folder);
        }

        private void openSelected() {
            Path selected = list.getSelectedValue();
            if (selected == null) {
                return;
            }
            UiFactory.openInFileManager(this, selected);
        }

        private void deleteSelected() {
            Path selected = list.getSelectedValue();
            if (selected == null) {
                return;
            }
            int answer = JOptionPane.showConfirmDialog(this,
                    Strings.get("instances.deleteEntryConfirm", selected.getFileName()),
                    Strings.get("common.confirm"), JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                by.agro.launcher.core.SecureFiles.rejectSymbolicLink(selected, "Entry");
                if (Files.isDirectory(selected)) {
                    try (var walk = Files.walk(selected)) {
                        walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException ignored) {
                            }
                        });
                    }
                } else {
                    Files.deleteIfExists(selected);
                }
                loadEntries();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(),
                        Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            }
        }

        private final class EntryRenderer extends JPanel
                implements javax.swing.ListCellRenderer<Path> {
            private final JLabel titleLabel = new JLabel();
            private final JLabel detailsLabel = new JLabel();

            EntryRenderer() {
                setLayout(new java.awt.GridLayout(2, 1));
                add(titleLabel);
                add(detailsLabel);
            }

            @Override
            public Component getListCellRendererComponent(JList<? extends Path> list,
                                                          Path value, int index,
                                                          boolean selected, boolean focused) {
                titleLabel.setText(value.getFileName().toString());
                titleLabel.setFont(AgroTheme.boldFont(13));
                titleLabel.setForeground(selected ? AgroTheme.accent() : AgroTheme.textPrimary());
                String details = java.time.Instant.ofEpochMilli(lastModified(value)).toString();
                if (Files.isRegularFile(value)) {
                    try {
                        long kb = Files.size(value) / 1024;
                        details = details + " · " + kb + " KB";
                    } catch (IOException ignored) {
                    }
                }
                detailsLabel.setText(details);
                detailsLabel.setFont(AgroTheme.font(11));
                detailsLabel.setForeground(AgroTheme.textMuted());
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                return this;
            }
        }
    }

    private static final class InstallationRenderer extends JPanel
            implements javax.swing.ListCellRenderer<Installation> {
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();

        InstallationRenderer() {
            setOpaque(false);
            setLayout(new GridLayout(2, 1));
            add(title);
            add(detail);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Installation> list,
                                                      Installation value, int index, boolean selected,
                                                      boolean focused) {
            title.setText(value.title());
            title.setFont(AgroTheme.boldFont(14));
            title.setForeground(selected ? AgroTheme.accent() : AgroTheme.textPrimary());
            detail.setText((value.playtimeMs > 0 ? value.formattedPlaytime() + " · " : "")
                    + value.gameDir);
            detail.setToolTipText(value.gameDir.toString());
            detail.setFont(AgroTheme.font(11));
            detail.setForeground(AgroTheme.textMuted());
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            return this;
        }
    }

    private static final class BuildRenderer extends JPanel
            implements javax.swing.ListCellRenderer<ModBuildManager.ModBuild> {
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();

        BuildRenderer() {
            setOpaque(false);
            setLayout(new GridLayout(2, 1));
            add(title);
            add(detail);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ModBuildManager.ModBuild> list,
                                                      ModBuildManager.ModBuild value, int index,
                                                      boolean selected, boolean focused) {
            title.setText(value.name);
            title.setFont(AgroTheme.boldFont(14));
            title.setForeground(selected ? AgroTheme.accent() : AgroTheme.textPrimary());
            detail.setText(value.toString());
            detail.setFont(AgroTheme.font(11));
            detail.setForeground(AgroTheme.textMuted());
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            return this;
        }
    }
}
