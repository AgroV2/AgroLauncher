package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
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
import java.util.function.Consumer;

public final class ModsPanel extends JPanel {

    private static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/mc-mods";

    private final LauncherContext context;
    private final ModManager modManager;
    private final ModBuildManager buildManager;
    private final Consumer<String> statusReporter;

    private final JTabbedPane tabs = new JTabbedPane();
    private final ModBrowserPanel browserPanel;

    private final DefaultListModel<ModManager.ModFile> model = new DefaultListModel<>();
    private final JList<ModManager.ModFile> modsList = new JList<>(model);
    private final JLabel summaryLabel = new JLabel();
    private final JLabel loaderWarning = new JLabel(" ");
    private final JComboBox<ModBuildManager.ModBuild> buildsCombo = new JComboBox<>();

    public ModsPanel(LauncherContext context, Consumer<String> statusReporter) {
        this.context = context;
        this.modManager = new ModManager(context.paths());
        this.buildManager = new ModBuildManager(context.paths());
        this.statusReporter = statusReporter;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

        browserPanel = new ModBrowserPanel(context, statusReporter, this::refresh);

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
        tabs.addTab(Strings.get("mods.installed"), buildInstalledSection());
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 0) {
                browserPanel.refreshFilters();
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

        actions.add(addButton);
        actions.add(toggleButton);
        actions.add(deleteButton);
        actions.add(openButton);
        actions.add(refreshButton);

        JPanel builds = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        builds.setOpaque(false);
        builds.add(new JLabel(Strings.get("builds.independent")));
        buildsCombo.setPreferredSize(new java.awt.Dimension(360, 34));
        buildsCombo.addActionListener(e -> selectBuild());
        JButton createBuild = UiFactory.primaryButton(Strings.get("builds.createSnapshot"));
        createBuild.addActionListener(e -> createBuild());
        JButton deleteBuild = UiFactory.dangerButton(Strings.get("builds.delete"));
        deleteBuild.addActionListener(e -> deleteBuild());
        builds.add(buildsCombo);
        builds.add(createBuild);
        builds.add(deleteBuild);

        JPanel bottom = UiFactory.transparentPanel();
        bottom.setLayout(new BorderLayout(0, 10));
        summaryLabel.setFont(AgroTheme.font(12));
        summaryLabel.setForeground(AgroTheme.textSecondary());
        bottom.add(builds, BorderLayout.NORTH);
        JPanel lower = UiFactory.transparentPanel();
        lower.setLayout(new BorderLayout(0, 8));
        lower.add(summaryLabel, BorderLayout.NORTH);
        lower.add(actions, BorderLayout.SOUTH);
        bottom.add(lower, BorderLayout.SOUTH);

        card.add(UiFactory.scroll(modsList), BorderLayout.CENTER);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    public void refresh() {
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
        reloadBuilds();
        browserPanel.refreshFilters();
    }

    private void reloadBuilds() {
        String selectedId = context.settings().selectedModBuildId;
        buildsCombo.removeAllItems();
        for (ModBuildManager.ModBuild build : buildManager.list()) {
            buildsCombo.addItem(build);
            if (build.id.equals(selectedId)) buildsCombo.setSelectedItem(build);
        }
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
                context.settings().save();
            }
            reloadBuilds();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), Strings.get("common.error"),
                    JOptionPane.WARNING_MESSAGE);
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
                    "Не удалось открыть браузер: " + e.getMessage(),
                    "Ошибка", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void addMods() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("mods.chooseFiles"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(Strings.get("mods.jarFilter"), "jar"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        int added = 0;
        for (File file : chooser.getSelectedFiles()) {
            try {
                modManager.add(file.toPath());
                added++;
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        Strings.get("mods.addFailed", file.getName(), e.getMessage()),
                        Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            }
        }
        refresh();
        report(Strings.get("mods.addedCount", added));
    }

    private void toggleSelected() {
        var selected = modsList.getSelectedValuesList();
        if (selected.isEmpty()) {
            return;
        }
        for (ModManager.ModFile mod : selected) {
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
            stateLabel.setText(value.enabled ? Strings.get("mods.enabled") : Strings.get("mods.disabled"));
            stateLabel.setForeground(value.enabled ? AgroTheme.accent() : AgroTheme.textMuted());

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
