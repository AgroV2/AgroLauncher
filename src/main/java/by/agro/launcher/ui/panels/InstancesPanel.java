package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.Settings;
import by.agro.launcher.i18n.Strings;
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

    private final LauncherContext context;
    private final Consumer<String> statusReporter;
    private final Runnable onSelectionApplied;

    private final DefaultListModel<Installation> installationsModel = new DefaultListModel<>();
    private final JList<Installation> installationsList = new JList<>(installationsModel);
    private final DefaultListModel<ModBuildManager.ModBuild> buildsModel = new DefaultListModel<>();
    private final JList<ModBuildManager.ModBuild> buildsList = new JList<>(buildsModel);
    private final JLabel summaryLabel = new JLabel(" ");

    public InstancesPanel(LauncherContext context, Consumer<String> statusReporter,
                          Runnable onSelectionApplied) {
        this.context = context;
        this.statusReporter = statusReporter;
        this.onSelectionApplied = onSelectionApplied;

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

        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(false);
        tabs.addTab(Strings.get("instances.installations"), buildInstallationsTab());
        tabs.addTab(Strings.get("instances.builds"), buildBuildsTab());

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        refresh();
    }

    private JComponent buildInstallationsTab() {
        JPanel card = UiFactory.transparentPanel();
        card.setLayout(new BorderLayout(0, 10));

        installationsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        installationsList.setOpaque(false);
        installationsList.setCellRenderer(new InstallationRenderer());
        installationsList.setFixedCellHeight(56);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton play = UiFactory.primaryButton(Strings.get("instances.play"));
        play.addActionListener(e -> playSelectedInstallation());
        JButton open = UiFactory.button(Strings.get("common.openFolder"));
        open.addActionListener(e -> openSelectedInstallation());
        JButton delete = UiFactory.dangerButton(Strings.get("common.delete"));
        delete.addActionListener(e -> deleteSelectedInstallation());
        JButton reload = UiFactory.button(Strings.get("common.refresh"));
        reload.addActionListener(e -> refresh());
        actions.add(play);
        actions.add(open);
        actions.add(delete);
        actions.add(reload);

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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        JButton play = UiFactory.primaryButton(Strings.get("instances.play"));
        play.addActionListener(e -> playSelectedBuild());
        JButton open = UiFactory.button(Strings.get("common.openFolder"));
        open.addActionListener(e -> openSelectedBuild());
        JButton reload = UiFactory.button(Strings.get("common.refresh"));
        reload.addActionListener(e -> refresh());
        actions.add(play);
        actions.add(open);
        actions.add(reload);

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
        SwingUtilities.invokeLater(() -> {
            List<Installation> installations = scanInstallations();
            installationsModel.clear();
            for (Installation installation : installations) {
                installationsModel.addElement(installation);
            }

            buildsModel.clear();
            for (ModBuildManager.ModBuild build : new ModBuildManager(context.paths()).list()) {
                buildsModel.addElement(build);
            }

            if (installations.isEmpty()) {
                summaryLabel.setText(Strings.get("instances.empty"));
            } else {
                summaryLabel.setText(Strings.get("instances.summary", installations.size()));
            }
        });
    }

    private List<Installation> scanInstallations() {
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
        return result;
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
                displayName, modpackVersion);
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
            deleteTree(selected.gameDir);
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
        try {
            Files.createDirectories(path);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(path.toFile());
            } else {
                JOptionPane.showMessageDialog(this, path.toString(),
                        Strings.get("settings.folderPath"), JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    Strings.get("mods.openFolderFailed", e.getMessage()),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
        }
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

        Installation(String minecraftVersion, String loaderId, String loaderVersion,
                     Path gameDir, String modpackName, String modpackVersion) {
            this.minecraftVersion = minecraftVersion;
            this.loaderId = loaderId;
            this.loaderVersion = loaderVersion;
            this.gameDir = gameDir;
            this.modpackName = modpackName;
            this.modpackVersion = modpackVersion;
        }

        public String title() {
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
            detail.setText(value.gameDir.toString());
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
