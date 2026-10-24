package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.mods.ModBuildManager;
import by.agro.launcher.mods.ModManager;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class ModsPanel extends JPanel {

    private static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods";

    private final LauncherContext context;
    private final ModManager modManager;
    private final ModBuildManager buildManager;
    private final Consumer<String> statusReporter;

    private final JTabbedPane tabs = new JTabbedPane();
    private final ModBrowserPanel browserPanel;
    private final ModBrowserPanel resourcePacksPanel;
    private final ModBrowserPanel shadersPanel;
    private final ModBrowserPanel modpacksPanel;

    private final DefaultListModel<ModManager.ModFile> model = new DefaultListModel<>();
    private final JList<ModManager.ModFile> modsList = new JList<>(model);
    private final JLabel summaryLabel = new JLabel();
    private final JLabel loaderWarning = new JLabel(" ");
    private final JComboBox<ModBuildManager.ModBuild> buildsCombo = new JComboBox<>();
    private final JButton deleteBuildButton = UiFactory.dangerButton(Strings.get("builds.delete"));
    private final JLabel pathHintLabel = UiFactory.hint(" ");
    private final javax.swing.JProgressBar mrpackProgressBar = new javax.swing.JProgressBar();
    private final JLabel mrpackProgressLabel = new JLabel(" ");
    private final JButton switchModsButton = UiFactory.button(Strings.get("mods.switchTo"));
    private java.nio.file.Path activeGameDir;
    private ModsElsewhere modsElsewhere;

    public ModsPanel(LauncherContext context, Consumer<String> statusReporter) {
        this.context = context;
        this.modManager = new ModManager(context.paths());
        this.buildManager = new ModBuildManager(context.paths());
        this.statusReporter = statusReporter;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

        browserPanel = new ModBrowserPanel(context, statusReporter, this::refresh, "mod");
        resourcePacksPanel = new ModBrowserPanel(context, statusReporter, this::refresh, "resourcepack");
        shadersPanel = new ModBrowserPanel(context, statusReporter, this::refresh, "shader");
        modpacksPanel = new ModBrowserPanel(context, statusReporter, this::refresh, "modpack");

        add(buildHeader(), BorderLayout.NORTH);
        add(buildTabs(), BorderLayout.CENTER);

        refresh();
    }

    private JComponent buildHeader() {
        JPanel header = UiFactory.transparentPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);

        JPanel titles = UiFactory.transparentPanel();
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = UiFactory.title(Strings.get("mods.title"));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel subtitle = UiFactory.subtitle(Strings.get("mods.subtitle"));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(title);
        titles.add(UiFactory.verticalGap(4));
        titles.add(subtitle);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        switchModsButton.setToolTipText(Strings.get("mods.switchToTooltip"));
        switchModsButton.setVisible(false);
        switchModsButton.addActionListener(e -> switchToModsInstallation());
        actions.add(switchModsButton);
        JButton curseForgeButton = UiFactory.button(Strings.get("mods.curseForge"));
        curseForgeButton.setToolTipText(Strings.get("mods.curseForgeTooltip"));
        curseForgeButton.addActionListener(e -> openCurseForge());
        actions.add(curseForgeButton);

        titleRow.add(titles, BorderLayout.WEST);
        titleRow.add(actions, BorderLayout.EAST);

        loaderWarning.setFont(AgroTheme.font(12));
        loaderWarning.setForeground(AgroTheme.warning());
        loaderWarning.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(titleRow);
        header.add(UiFactory.verticalGap(8));
        header.add(loaderWarning);
        header.add(UiFactory.verticalGap(14));
        return header;
    }

    private JComponent buildTabs() {
        tabs.setOpaque(false);
        tabs.addTab(Strings.get("mods.catalog"), browserPanel);
        tabs.addTab(Strings.get("mods.resourcePacks"), resourcePacksPanel);
        tabs.addTab(Strings.get("mods.shaders"), shadersPanel);
        tabs.addTab(Strings.get("mods.modpacks"), modpacksPanel);
        tabs.addTab(Strings.get("mods.installed"), buildInstalledSection());
        tabs.addChangeListener(e -> {
            Component selected = tabs.getSelectedComponent();
            if (selected == browserPanel) {
                browserPanel.refreshFilters();
            } else if (selected == resourcePacksPanel) {
                resourcePacksPanel.refreshFilters();
            } else if (selected == shadersPanel) {
                shadersPanel.refreshFilters();
            } else if (selected == modpacksPanel) {
                modpacksPanel.refreshFilters();
            } else {
                refresh();
            }
        });
        return tabs;
    }

    private JComponent buildInstalledSection() {
        JPanel card = UiFactory.transparentPanel();
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        modsList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        modsList.setOpaque(false);
        modsList.setBackground(new java.awt.Color(0, 0, 0, 0));
        modsList.setFixedCellHeight(44);
        modsList.setCellRenderer(new ModCellRenderer());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        JButton addButton = UiFactory.primaryButton(Strings.get("mods.addFiles"));
        addButton.addActionListener(e -> addMods());

        JButton toggleButton = UiFactory.button(Strings.get("mods.toggle"));
        toggleButton.addActionListener(e -> toggleSelected());

        JButton deleteButton = UiFactory.dangerButton(Strings.get("common.delete"));
        deleteButton.addActionListener(e -> deleteSelected());

        JButton openButton = UiFactory.button(Strings.get("common.openFolder"));
        openButton.addActionListener(e -> openModsFolder());

        JButton refreshButton = UiFactory.button(Strings.get("common.refresh"));
        refreshButton.addActionListener(e -> refresh());

        JButton exportMrpack = UiFactory.button(Strings.get("mods.exportMrpack"));
        exportMrpack.setToolTipText(Strings.get("mods.exportMrpackTooltip"));
        exportMrpack.addActionListener(e -> exportMrpack());

        JButton importMrpack = UiFactory.button(Strings.get("mods.importMrpack"));
        importMrpack.setToolTipText(Strings.get("mods.importMrpackTooltip"));
        importMrpack.addActionListener(e -> chooseMrpack());

        JButton unpackMrpack = UiFactory.button(Strings.get("mods.unpackMrpack"));
        unpackMrpack.setToolTipText(Strings.get("mods.importMrpackTooltip"));
        unpackMrpack.addActionListener(e -> unpackSelectedMrpack());
        unpackMrpack.setEnabled(false);

        modsList.addListSelectionListener(e -> updateUnpackButtonState(unpackMrpack));

        actions.add(addButton);
        actions.add(toggleButton);
        actions.add(deleteButton);
        actions.add(unpackMrpack);
        actions.add(importMrpack);
        actions.add(openButton);
        actions.add(refreshButton);
        actions.add(exportMrpack);

        JPanel builds = UiFactory.transparentPanel();
        builds.setLayout(new BorderLayout(8, 0));
        builds.add(new JLabel(Strings.get("builds.independent")), BorderLayout.WEST);

        JPanel buildControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buildControls.setOpaque(false);
        buildsCombo.setPreferredSize(new java.awt.Dimension(300, 34));
        buildsCombo.addActionListener(e -> {
            deleteBuildButton.setEnabled(buildsCombo.getSelectedItem() != null);
            selectBuild();
        });
        JButton createBuild = UiFactory.primaryButton(Strings.get("builds.createSnapshot"));
        createBuild.addActionListener(e -> createBuild());
        deleteBuildButton.setEnabled(false);
        deleteBuildButton.addActionListener(e -> deleteBuild());
        buildControls.add(buildsCombo);
        buildControls.add(deleteBuildButton);
        buildControls.add(createBuild);
        builds.add(buildControls, BorderLayout.CENTER);

        JPanel bottom = UiFactory.transparentPanel();
        bottom.setLayout(new BorderLayout(0, 10));
        summaryLabel.setFont(AgroTheme.font(12));
        summaryLabel.setForeground(AgroTheme.textSecondary());
        pathHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(builds, BorderLayout.NORTH);
        JPanel lower = UiFactory.transparentPanel();
        lower.setLayout(new BorderLayout(0, 4));
        JPanel texts = UiFactory.transparentPanel();
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(summaryLabel);
        texts.add(UiFactory.verticalGap(2));
        texts.add(pathHintLabel);
        lower.add(texts, BorderLayout.NORTH);
        lower.add(buildMrpackProgressRow(), BorderLayout.CENTER);
        lower.add(actions, BorderLayout.SOUTH);
        bottom.add(lower, BorderLayout.SOUTH);

        card.add(UiFactory.scroll(modsList), BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    public void refresh() {
        applyActiveGameDir();
        model.clear();
        var mods = modManager.list();
        for (ModManager.ModFile mod : mods) {
            model.addElement(mod);
        }
        long enabled = mods.stream().filter(m -> m.enabled).count();
        summaryLabel.setText(Strings.get("mods.summary", mods.size(), enabled));

        LoaderType loader = LoaderType.fromId(context.settings().selectedLoader);
        if (!loader.supportsMods()) {
            loaderWarning.setText(Strings.get("mods.loaderWarning", loader.displayName()));
        } else {
            loaderWarning.setText(" ");
        }
        Path strayMrpack = findStrayMrpack();
        if (strayMrpack != null && loader.supportsMods()) {
            loaderWarning.setText(Strings.get("mods.mrpackInMods", strayMrpack.getFileName()));
        }
        modsElsewhere = model.isEmpty() ? findModsElsewhere() : null;
        if (modsElsewhere != null) {
            loaderWarning.setText(Strings.get("mods.modsElsewhere", modsElsewhere.description()));
            switchModsButton.setText(Strings.get("mods.switchTo")
                    + " · " + modsElsewhere.shortTitle());
            switchModsButton.setVisible(true);
        } else {
            switchModsButton.setVisible(false);
        }
        reloadBuilds();
        browserPanel.refreshFilters();
    }

    private void applyActiveGameDir() {
        ModBuildManager.ModBuild active = buildManager.list().stream()
                .filter(build -> build.id.equals(context.settings().selectedModBuildId))
                .findFirst().orElse(null);
        java.nio.file.Path gameDir;
        if (active != null) {
            gameDir = buildManager.gameDir(active);
        } else {
            String minecraft = context.settings().selectedVersion;
            LoaderType loader = LoaderType.fromId(context.settings().selectedLoader);
            if (minecraft == null || minecraft.isBlank()) {
                gameDir = context.paths().gameDir();
            } else {
                String loaderVersion = context.settings().selectedLoaderVersion;
                if (loaderVersion == null || loaderVersion.isBlank()) loaderVersion = "recommended";
                gameDir = context.paths().installationGameDir(minecraft, loader.id(),
                        loader == LoaderType.VANILLA ? "default" : loaderVersion);
            }
        }
        modManager.setGameDir(gameDir);
        activeGameDir = gameDir;
        pathHintLabel.setText(Strings.get("mods.installedInto", gameDir));
        browserPanel.setGameDir(gameDir);
        resourcePacksPanel.setGameDir(gameDir);
        shadersPanel.setGameDir(gameDir);
        modpacksPanel.setGameDir(gameDir);
    }

    private static final class ModsElsewhere {
        final String minecraftVersion;
        final String loaderId;
        final String loaderVersion;
        final java.nio.file.Path gameDir;
        final int modsCount;

        ModsElsewhere(String minecraftVersion, String loaderId, String loaderVersion,
                      java.nio.file.Path gameDir, int modsCount) {
            this.minecraftVersion = minecraftVersion;
            this.loaderId = loaderId;
            this.loaderVersion = loaderVersion;
            this.gameDir = gameDir;
            this.modsCount = modsCount;
        }

        String shortTitle() {
            String loader = LoaderType.fromId(loaderId).displayName();
            if (loaderVersion != null && !loaderVersion.isBlank() && !"default".equals(loaderVersion)) {
                loader = loader + " " + loaderVersion;
            }
            return minecraftVersion + " · " + loader;
        }

        String description() {
            return shortTitle() + " · " + modsCount;
        }
    }

    private ModsElsewhere findModsElsewhere() {
        java.nio.file.Path root = context.paths().instancesDir().resolve("installations");
        if (!Files.isDirectory(root) || activeGameDir == null) {
            return null;
        }
        java.nio.file.Path current = activeGameDir.toAbsolutePath().normalize();
        ModsElsewhere best = null;
        try (var minecraftDirs = Files.list(root)) {
            for (java.nio.file.Path minecraftDir : minecraftDirs.filter(Files::isDirectory).toList()) {
                try (var loaders = Files.list(minecraftDir)) {
                    for (java.nio.file.Path loaderDir : loaders.filter(Files::isDirectory).toList()) {
                        try (var versions = Files.list(loaderDir)) {
                            for (java.nio.file.Path gameDir : versions.filter(Files::isDirectory).toList()) {
                                if (gameDir.toAbsolutePath().normalize().equals(current)) {
                                    continue;
                                }
                                int count = countJarMods(gameDir.resolve("mods"));
                                if (count <= 0) {
                                    continue;
                                }
                                if (best == null || count > best.modsCount) {
                                    best = new ModsElsewhere(
                                            minecraftDir.getFileName().toString(),
                                            loaderDir.getFileName().toString(),
                                            gameDir.getFileName().toString(),
                                            gameDir, count);
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return best;
    }

    private int countJarMods(java.nio.file.Path modsDir) {
        if (!Files.isDirectory(modsDir)) {
            return 0;
        }
        try (var stream = Files.list(modsDir)) {
            return (int) stream.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return name.endsWith(".jar") || name.endsWith(".jar.disabled");
                    }).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private void switchToModsInstallation() {
        ModsElsewhere target = modsElsewhere;
        if (target == null) {
            return;
        }
        var settings = context.settings();
        settings.selectedVersion = target.minecraftVersion;
        settings.selectedLoader = target.loaderId;
        settings.selectedLoaderVersion = "default".equals(target.loaderVersion)
                ? "" : target.loaderVersion;
        settings.selectedModBuildId = "";
        settings.save();
        report(Strings.get("builds.selected", target.shortTitle()));
        refresh();
    }

    private Path findStrayMrpack() {
        Path mods = (activeGameDir != null ? activeGameDir : context.paths().gameDir()).resolve("mods");
        if (!Files.isDirectory(mods)) {
            return null;
        }
        try (var stream = Files.list(mods)) {
            return stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                            .toLowerCase(java.util.Locale.ROOT).endsWith(".mrpack"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private void exportMrpack() {
        String minecraft = context.settings().selectedVersion;
        if (minecraft == null || minecraft.isBlank()) {
            JOptionPane.showMessageDialog(this, Strings.get("builds.selectVersion"),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("mods.exportMrpack"));
        chooser.setSelectedFile(new File("build-" + minecraft + ".mrpack"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        Path gameDir = activeGameDir != null ? activeGameDir : context.paths().gameDir();
        LoaderType loader = LoaderType.fromId(context.settings().selectedLoader);
        String loaderVersion = context.settings().selectedLoaderVersion == null
                ? "" : context.settings().selectedLoaderVersion;
        String modpackName = JOptionPane.showInputDialog(this, Strings.get("modpack.namePrompt"),
                minecraft, JOptionPane.PLAIN_MESSAGE);
        if (modpackName == null) {
            return;
        }
        report(Strings.get("modpack.exporting"));
        showMrpackProgress(Strings.get("modpack.exporting"));
        new javax.swing.SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                new by.agro.launcher.modrinth.MrpackInstaller(context.paths(), context.downloader())
                        .export(target, gameDir, modpackName, "1.0.0", minecraft, loader, loaderVersion,
                                by.agro.launcher.core.ProgressListener.NOOP);
                return target;
            }

            @Override
            protected void done() {
                hideMrpackProgress();
                try {
                    Path exported = get();
                    report(Strings.get("modpack.exported", exported.getFileName()));
                    JOptionPane.showMessageDialog(ModsPanel.this,
                            Strings.get("modpack.exported", exported.getFileName()),
                            Strings.get("mods.exportMrpack"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    JOptionPane.showMessageDialog(ModsPanel.this,
                            Strings.get("modpack.exportFailed", detail),
                            Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void reloadBuilds() {
        String selectedId = context.settings().selectedModBuildId;
        buildsCombo.removeAllItems();
        for (ModBuildManager.ModBuild build : buildManager.list()) {
            buildsCombo.addItem(build);
            if (build.id.equals(selectedId)) buildsCombo.setSelectedItem(build);
        }
        deleteBuildButton.setEnabled(buildsCombo.getSelectedItem() != null);
    }

    private void createBuild() {
        String name = JOptionPane.showInputDialog(this, Strings.get("builds.namePrompt"),
                Strings.get("builds.newTitle"), JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        try {
            ModBuildManager.ModBuild build = buildManager.create(name,
                    context.settings().selectedVersion,
                    LoaderType.fromId(context.settings().selectedLoader),
                    context.settings().selectedLoaderVersion);
            context.settings().selectedModBuildId = build.id;
            context.settings().save();
            reloadBuilds();
            report(Strings.get("builds.created", build.name));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), Strings.get("common.error"),
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void selectBuild() {
        ModBuildManager.ModBuild build = (ModBuildManager.ModBuild) buildsCombo.getSelectedItem();
        if (build == null) return;
        context.settings().selectedModBuildId = build.id;
        context.settings().selectedVersion = build.minecraftVersion;
        context.settings().selectedLoader = build.loader;
        context.settings().selectedLoaderVersion = build.loaderVersion == null ? "" : build.loaderVersion;
        context.settings().save();
        report(Strings.get("builds.selected", build.name));
    }

    private void deleteBuild() {
        ModBuildManager.ModBuild build = (ModBuildManager.ModBuild) buildsCombo.getSelectedItem();
        if (build == null) return;
        if (JOptionPane.showConfirmDialog(this, Strings.get("builds.deleteConfirm", build.name),
                Strings.get("common.confirm"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            buildManager.delete(build);
            if (build.id.equals(context.settings().selectedModBuildId)) {
                context.settings().selectedModBuildId = "";
            }
            context.settings().save();
            reloadBuilds();
            report(Strings.get("builds.deleted", build.name));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    Strings.get("builds.deleteFailed", build.name, e.getMessage()),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openCurseForge() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(CURSEFORGE_URL));
                report(Strings.get("mods.curseForgeOpened"));
            } else {
                JOptionPane.showMessageDialog(this, CURSEFORGE_URL,
                        Strings.get("mods.curseForge"), JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    Strings.get("mods.openBrowserFailed", e.getMessage()),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void addMods() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("mods.chooseFiles"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                Strings.get("mods.addFilesFilter"), "jar", "mrpack"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        int added = 0;
        java.util.List<Path> modpacks = new java.util.ArrayList<>();
        for (File file : chooser.getSelectedFiles()) {
            if (file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".mrpack")) {
                modpacks.add(file.toPath());
                continue;
            }
            try {
                modManager.add(file.toPath());
                added++;
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        Strings.get("mods.addFailed", file.getName(), e.getMessage()),
                        Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            }
        }
        for (Path modpack : modpacks) {
            importMrpack(modpack);
        }
        refresh();
        if (added > 0) {
            report(Strings.get("mods.addedCount", added));
        }
    }

    private void chooseMrpack() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("mods.importMrpack"));
        chooser.setFileFilter(new FileNameExtensionFilter("Modrinth modpack (.mrpack)", "mrpack"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        importMrpack(chooser.getSelectedFile().toPath());
    }

    private void importMrpack(Path archive) {
        report(Strings.get("modpack.importing"));
        showMrpackProgress(Strings.get("modpack.importing"));
        ProgressListener listener = new ProgressListener() {
            @Override
            public void onProgress(String stage, long current, long total, String detail) {
                javax.swing.SwingUtilities.invokeLater(() ->
                        updateMrpackProgress(stage, current, total, detail));
            }

            @Override
            public void onMessage(String message) {
                javax.swing.SwingUtilities.invokeLater(() -> {
                    mrpackProgressLabel.setText(message);
                });
            }
        };
        new javax.swing.SwingWorker<by.agro.launcher.modrinth.MrpackInstaller.Installed, Void>() {
            @Override
            protected by.agro.launcher.modrinth.MrpackInstaller.Installed doInBackground() throws Exception {
                return new by.agro.launcher.modrinth.MrpackInstaller(context.paths(), context.downloader())
                        .install(archive, listener);
            }

            @Override
            protected void done() {
                hideMrpackProgress();
                try {
                    by.agro.launcher.modrinth.MrpackInstaller.Installed installed = get();
                    applyModpackSelection(installed);
                    Files.deleteIfExists(archive);
                    String summary = Strings.get("modpack.importedWithDir",
                            installed.name, installed.gameDir);
                    report(summary);
                    JOptionPane.showMessageDialog(ModsPanel.this, summary,
                            Strings.get("mods.importMrpack"), JOptionPane.INFORMATION_MESSAGE);
                    refresh();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    JOptionPane.showMessageDialog(ModsPanel.this,
                            Strings.get("modpack.importFailed", detail),
                            Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showMrpackProgress(String caption) {
        mrpackProgressLabel.setText(caption);
        mrpackProgressLabel.setVisible(true);
        mrpackProgressBar.setIndeterminate(true);
        mrpackProgressBar.setValue(0);
        mrpackProgressBar.setVisible(true);
        revalidate();
        repaint();
    }

    private void updateMrpackProgress(String stage, long current, long total, String detail) {
        if (total > 0) {
            mrpackProgressBar.setIndeterminate(false);
            mrpackProgressBar.setMaximum((int) Math.min(total, Integer.MAX_VALUE));
            mrpackProgressBar.setValue((int) Math.min(current, Integer.MAX_VALUE));
        } else {
            mrpackProgressBar.setIndeterminate(true);
        }
        mrpackProgressLabel.setText(stage + (detail == null || detail.isBlank() ? "" : " — " + detail));
    }

    private void hideMrpackProgress() {
        mrpackProgressBar.setVisible(false);
        mrpackProgressBar.setIndeterminate(false);
        mrpackProgressLabel.setVisible(false);
        revalidate();
        repaint();
    }

    private JComponent buildMrpackProgressRow() {
        JPanel row = UiFactory.transparentPanel();
        row.setLayout(new BorderLayout(0, 6));
        mrpackProgressBar.setStringPainted(false);
        mrpackProgressBar.setPreferredSize(new java.awt.Dimension(100, 8));
        mrpackProgressBar.setVisible(false);
        mrpackProgressLabel.setFont(AgroTheme.font(12));
        mrpackProgressLabel.setForeground(AgroTheme.textSecondary());
        mrpackProgressLabel.setVisible(false);
        row.add(mrpackProgressLabel, BorderLayout.NORTH);
        row.add(mrpackProgressBar, BorderLayout.CENTER);
        return row;
    }

    private void updateUnpackButtonState(JButton unpackButton) {
        boolean hasModpack = modsList.getSelectedValuesList().stream()
                .anyMatch(mod -> mod.modpack);
        unpackButton.setEnabled(hasModpack);
    }

    private void unpackSelectedMrpack() {
        ModManager.ModFile selected = modsList.getSelectedValuesList().stream()
                .filter(mod -> mod.modpack)
                .findFirst().orElse(null);
        if (selected == null) {
            return;
        }
        importMrpack(selected.path);
    }

    private void applyModpackSelection(by.agro.launcher.modrinth.MrpackInstaller.Installed installed) {
        var settings = context.settings();
        settings.selectedVersion = installed.minecraftVersion;
        settings.selectedLoader = installed.loader.id();
        settings.selectedLoaderVersion = installed.loaderVersion == null ? "" : installed.loaderVersion;
        settings.selectedModBuildId = "";
        settings.save();
    }

    private void toggleSelected() {
        var selected = modsList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        for (ModManager.ModFile mod : selected) {
            if (mod.modpack) {
                continue;
            }
            try {
                modManager.setEnabled(mod, !mod.enabled);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        Strings.get("mods.toggleFailed", mod.displayName(), e.getMessage()),
                        Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            }
        }
        refresh();
        report(Strings.get("mods.stateUpdated"));
    }

    private void deleteSelected() {
        var selected = modsList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                Strings.get("mods.deleteConfirm", selected.size()),
                Strings.get("common.confirm"), JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        for (ModManager.ModFile mod : selected) {
            try {
                modManager.delete(mod);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        Strings.get("mods.deleteFailed", mod.displayName(), e.getMessage()),
                        Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            }
        }
        refresh();
        report(Strings.get("mods.deleted"));
    }

    private void openModsFolder() {
        try {
            java.nio.file.Files.createDirectories(modManager.modsDir());
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(modManager.modsDir().toFile());
            } else {
                JOptionPane.showMessageDialog(this, modManager.modsDir().toString(),
                        Strings.get("mods.folderTitle"), JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, Strings.get("mods.openFolderFailed", e.getMessage()),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }

    public void shutdown() {
        browserPanel.shutdown();
    }

    private static final class ModCellRenderer extends JPanel
            implements javax.swing.ListCellRenderer<ModManager.ModFile> {

        private final JLabel nameLabel = new JLabel();
        private final JLabel detailsLabel = new JLabel();
        private final JLabel stateLabel = new JLabel();

        ModCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
            nameLabel.setFont(AgroTheme.font(13));
            detailsLabel.setFont(AgroTheme.font(11));
            stateLabel.setFont(AgroTheme.boldFont(10));

            JPanel texts = new JPanel();
            texts.setOpaque(false);
            texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
            texts.add(nameLabel);
            texts.add(detailsLabel);

            add(texts, BorderLayout.CENTER);
            add(stateLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ModManager.ModFile> list,
                                                      ModManager.ModFile value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value.displayName());
            detailsLabel.setText(value.formattedSize());
            if (value.modpack) {
                stateLabel.setText(Strings.get("mods.modpackBadge"));
                stateLabel.setForeground(AgroTheme.info());
            } else {
                stateLabel.setText(value.enabled ? Strings.get("mods.enabled") : Strings.get("mods.disabled"));
                stateLabel.setForeground(value.enabled ? AgroTheme.accent() : AgroTheme.textMuted());
            }

            if (isSelected) {
                setOpaque(true);
                setBackground(AgroTheme.accentDeep());
                nameLabel.setForeground(AgroTheme.textPrimary());
                detailsLabel.setForeground(AgroTheme.accentLight());
            } else {
                setOpaque(false);
                setBackground(new java.awt.Color(0, 0, 0, 0));
                nameLabel.setForeground(value.enabled
                        ? AgroTheme.textPrimary() : AgroTheme.textMuted());
                detailsLabel.setForeground(AgroTheme.textMuted());
            }
            return this;
        }
    }
}
