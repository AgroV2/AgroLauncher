package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.loaders.LoaderInstaller;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.version.RemoteVersion;
import by.agro.launcher.version.VersionManifest;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class VersionsPanel extends JPanel {

    private final LauncherContext context;
    private final Consumer<String> statusReporter;
    private final Runnable onSelectionChanged;

    private final DefaultListModel<RemoteVersion> versionsModel = new DefaultListModel<>();
    private final JList<RemoteVersion> versionsList = new JList<>(versionsModel);

    private final JCheckBox showSnapshots = new JCheckBox(Strings.get("versions.snapshots"));
    private final JCheckBox showOld = new JCheckBox(Strings.get("versions.oldVersions"));

    private final JComboBox<LoaderType> loaderCombo = new JComboBox<>();
    private final DefaultComboBoxModel<LoaderInstaller.LoaderVersion> loaderVersionsModel =
            new DefaultComboBoxModel<>();
    private final JComboBox<LoaderInstaller.LoaderVersion> loaderVersionCombo =
            new JComboBox<>(loaderVersionsModel);

    private final JLabel selectionLabel = new JLabel();
    private final JLabel loaderStatusLabel = new JLabel(" ");
    private final JButton refreshButton = UiFactory.button(Strings.get("versions.refreshList"));

    private List<RemoteVersion> allVersions = new ArrayList<>();

    public VersionsPanel(LauncherContext context, Consumer<String> statusReporter,
                         Runnable onSelectionChanged) {
        this.context = context;
        this.statusReporter = statusReporter;
        this.onSelectionChanged = onSelectionChanged;

        setOpaque(false);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        updateSelectionLabel();
        loadLoaderVersions();
        loadVersions(false);
    }

    private JComponent buildHeader() {
        JPanel header = UiFactory.transparentPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiFactory.title(Strings.get("versions.title")));
        header.add(UiFactory.verticalGap(4));
        header.add(UiFactory.subtitle(Strings.get("versions.subtitle")));
        header.add(UiFactory.verticalGap(18));
        return header;
    }

    private JComponent buildContent() {
        JPanel content = UiFactory.transparentPanel();
        content.setLayout(new BorderLayout(22, 0));
        content.add(buildVersionsSection(), BorderLayout.CENTER);
        content.add(buildLoaderSection(), BorderLayout.EAST);
        return content;
    }

    private JComponent buildVersionsSection() {
        JPanel card = UiFactory.titledCard(Strings.get("versions.minecraft"));

        versionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        versionsList.setOpaque(false);
        versionsList.setBackground(new java.awt.Color(0, 0, 0, 0));
        versionsList.setFixedCellHeight(34);
        versionsList.setCellRenderer(new VersionCellRenderer());
        versionsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onVersionSelected();
            }
        });

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        filters.setOpaque(false);

        showSnapshots.setSelected(context.settings().showSnapshots);
        showSnapshots.setOpaque(false);
        showSnapshots.setFont(AgroTheme.font(12));
        showSnapshots.addActionListener(e -> {
            context.settings().showSnapshots = showSnapshots.isSelected();
            context.settings().save();
            applyFilters();
        });

        showOld.setSelected(context.settings().showOldVersions);
        showOld.setOpaque(false);
        showOld.setFont(AgroTheme.font(12));
        showOld.addActionListener(e -> {
            context.settings().showOldVersions = showOld.isSelected();
            context.settings().save();
            applyFilters();
        });

        refreshButton.addActionListener(e -> loadVersions(true));

        filters.add(showSnapshots);
        filters.add(showOld);
        filters.add(refreshButton);

        card.add(UiFactory.scroll(versionsList), BorderLayout.CENTER);
        card.add(filters, BorderLayout.SOUTH);
        return card;
    }

    private JComponent buildLoaderSection() {
        JPanel card = UiFactory.titledCard(Strings.get("versions.loader"));
        card.setPreferredSize(new Dimension(330, 100));

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        for (LoaderType type : LoaderType.values()) {
            loaderCombo.addItem(type);
        }
        loaderCombo.setSelectedItem(LoaderType.fromId(context.settings().selectedLoader));
        UiFactory.fixHeight(loaderCombo, 34);
        loaderCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        loaderCombo.addActionListener(e -> onLoaderChanged());

        UiFactory.fixHeight(loaderVersionCombo, 34);
        loaderVersionCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        loaderVersionCombo.addActionListener(e -> saveLoaderVersion());

        selectionLabel.setFont(AgroTheme.boldFont(13));
        selectionLabel.setForeground(AgroTheme.accentLight());
        selectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        loaderStatusLabel.setFont(AgroTheme.font(11));
        loaderStatusLabel.setForeground(AgroTheme.textMuted());
        loaderStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel typeLabel = UiFactory.fieldLabel(Strings.get("versions.loaderType"));
        typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel versionLabel = UiFactory.fieldLabel(Strings.get("versions.loaderVersion"));
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel currentLabel = UiFactory.fieldLabel(Strings.get("versions.willLaunch"));
        currentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        body.add(typeLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(loaderCombo);
        body.add(UiFactory.verticalGap(14));
        body.add(versionLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(loaderVersionCombo);
        body.add(UiFactory.verticalGap(6));
        body.add(loaderStatusLabel);
        body.add(UiFactory.verticalGap(20));
        body.add(currentLabel);
        body.add(UiFactory.verticalGap(4));
        body.add(selectionLabel);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void loadVersions(boolean forceReload) {
        refreshButton.setEnabled(false);
        report(Strings.get("versions.loading"));

        new SwingWorker<VersionManifest, Void>() {
            @Override
            protected VersionManifest doInBackground() throws Exception {
                return forceReload ? context.reloadManifest() : context.manifest();
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                try {
                    VersionManifest manifest = get();
                    allVersions = manifest.versions();
                    applyFilters();
                    report(Strings.get("versions.available", allVersions.size(), manifest.latestRelease()));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    report(Strings.get("versions.loadFailed", cause.getMessage()));
                    loadInstalledOnly();
                }
            }
        }.execute();
    }

    private void loadInstalledOnly() {
        SwingUtilities.invokeLater(() -> {
            versionsModel.clear();
            List<String> installed = context.versionResolver().installedVersions();
            for (String id : installed) {
                versionsModel.addElement(new RemoteVersion(id, "release", null, null, null));
            }
            if (installed.isEmpty()) {
                report(Strings.get("versions.noVersions"));
            } else {
                report(Strings.get("versions.offlineList", installed.size()));
                String previous = context.settings().selectedVersion;
                for (int i = 0; i < versionsModel.size(); i++) {
                    if (versionsModel.get(i).id.equals(previous)) {
                        versionsList.setSelectedIndex(i);
                        return;
                    }
                }
            }
        });
    }

    private void applyFilters() {
        SwingUtilities.invokeLater(() -> {
            String previous = context.settings().selectedVersion;
            versionsModel.clear();
            for (RemoteVersion version : allVersions) {
                boolean include = version.isRelease()
                        || (version.isSnapshot() && showSnapshots.isSelected())
                        || (version.isOld() && showOld.isSelected());
                if (include) {
                    versionsModel.addElement(version);
                }
            }
            for (int i = 0; i < versionsModel.size(); i++) {
                if (versionsModel.get(i).id.equals(previous)) {
                    versionsList.setSelectedIndex(i);
                    versionsList.ensureIndexIsVisible(i);
                    return;
                }
            }
            if (!versionsModel.isEmpty()) {
                versionsList.setSelectedIndex(0);
            }
        });
    }

    private void onVersionSelected() {
        RemoteVersion selected = versionsList.getSelectedValue();
        if (selected == null) {
            return;
        }
        context.settings().selectedVersion = selected.id;
        context.settings().save();
        updateSelectionLabel();
        loadLoaderVersions();
        notifySelectionChanged();
    }

    private void onLoaderChanged() {
        LoaderType type = (LoaderType) loaderCombo.getSelectedItem();
        if (type == null) {
            return;
        }
        context.settings().selectedLoader = type.id();
        context.settings().selectedLoaderVersion = "";
        context.settings().save();
        updateSelectionLabel();
        loadLoaderVersions();
        notifySelectionChanged();
    }

    private void saveLoaderVersion() {
        LoaderInstaller.LoaderVersion selected =
                (LoaderInstaller.LoaderVersion) loaderVersionCombo.getSelectedItem();
        if (selected != null) {
            context.settings().selectedLoaderVersion = selected.version;
            context.settings().save();
            updateSelectionLabel();
            notifySelectionChanged();
        }
    }

    private void loadLoaderVersions() {
        LoaderType type = (LoaderType) loaderCombo.getSelectedItem();
        String mcVersion = context.settings().selectedVersion;

        loaderVersionsModel.removeAllElements();
        if (type == null || type == LoaderType.VANILLA || mcVersion == null || mcVersion.isBlank()) {
            loaderVersionCombo.setEnabled(false);
            loaderStatusLabel.setText(type == LoaderType.VANILLA ? Strings.get("versions.noMods") : " ");
            return;
        }

        loaderVersionCombo.setEnabled(false);
        loaderStatusLabel.setText(Strings.get("versions.loadingLoader", type.displayName()));

        LoaderInstaller installer = context.installer(type);
        if (installer == null) {
            loaderStatusLabel.setText(Strings.get("versions.loaderUnavailable"));
            return;
        }

        new SwingWorker<List<LoaderInstaller.LoaderVersion>, Void>() {
            @Override
            protected List<LoaderInstaller.LoaderVersion> doInBackground() throws Exception {
                return installer.availableVersions(mcVersion);
            }

            @Override
            protected void done() {
                try {
                    List<LoaderInstaller.LoaderVersion> versions = get();
                    loaderVersionsModel.removeAllElements();
                    if (versions.isEmpty()) {
                        loaderStatusLabel.setText(Strings.get("versions.notSupported", type.displayName(), mcVersion));
                        loaderVersionCombo.setEnabled(false);
                        return;
                    }
                    for (LoaderInstaller.LoaderVersion version : versions) {
                        loaderVersionsModel.addElement(version);
                    }
                    loaderVersionCombo.setEnabled(true);

                    String saved = context.settings().selectedLoaderVersion;
                    for (int i = 0; i < loaderVersionsModel.getSize(); i++) {
                        LoaderInstaller.LoaderVersion item = loaderVersionsModel.getElementAt(i);
                        if (item.version.equals(saved)) {
                            loaderVersionCombo.setSelectedIndex(i);
                            loaderStatusLabel.setText(Strings.get("versions.loaderAvailable", versions.size()));
                            return;
                        }
                    }
                    for (int i = 0; i < loaderVersionsModel.getSize(); i++) {
                        if (loaderVersionsModel.getElementAt(i).recommended) {
                            loaderVersionCombo.setSelectedIndex(i);
                            break;
                        }
                    }
                    loaderStatusLabel.setText(Strings.get("versions.loaderAvailable", versions.size()));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    loaderStatusLabel.setText(Strings.get("common.error") + ": " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void updateSelectionLabel() {
        String mcVersion = context.settings().selectedVersion;
        LoaderType type = LoaderType.fromId(context.settings().selectedLoader);
        if (mcVersion == null || mcVersion.isBlank()) {
            selectionLabel.setText(Strings.get("play.versionNotSelected"));
            return;
        }
        if (type == LoaderType.VANILLA) {
            selectionLabel.setText("Minecraft " + mcVersion);
        } else {
            String loaderVersion = context.settings().selectedLoaderVersion;
            selectionLabel.setText("Minecraft " + mcVersion + " + " + type.displayName()
                    + (loaderVersion.isBlank() ? "" : " " + loaderVersion));
        }
    }

    private void notifySelectionChanged() {
        if (onSelectionChanged != null) {
            onSelectionChanged.run();
        }
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }

    private static final class VersionCellRenderer extends JPanel
            implements javax.swing.ListCellRenderer<RemoteVersion> {

        private final JLabel idLabel = new JLabel();
        private final JLabel typeLabel = new JLabel();

        VersionCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            idLabel.setFont(AgroTheme.font(13));
            typeLabel.setFont(AgroTheme.font(11));
            typeLabel.setHorizontalAlignment(JLabel.RIGHT);
            add(idLabel, BorderLayout.CENTER);
            add(typeLabel, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends RemoteVersion> list,
                                                      RemoteVersion value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            idLabel.setText(value.id);
            typeLabel.setText(describeType(value.type));

            if (isSelected) {
                setOpaque(true);
                setBackground(AgroTheme.accentDeep());
                idLabel.setForeground(AgroTheme.textPrimary());
                idLabel.setFont(AgroTheme.boldFont(13));
                typeLabel.setForeground(AgroTheme.accentLight());
            } else {
                setOpaque(false);
                setBackground(new java.awt.Color(0, 0, 0, 0));
                idLabel.setForeground(AgroTheme.textPrimary());
                idLabel.setFont(AgroTheme.font(13));
                typeLabel.setForeground(AgroTheme.textMuted());
            }
            return this;
        }

        private String describeType(String type) {
            if (type == null) {
                return "";
            }
            switch (type) {
                case "release":
                    return Strings.get("versions.typeRelease");
                case "snapshot":
                    return Strings.get("versions.typeSnapshot");
                case "old_beta":
                    return Strings.get("versions.typeBeta");
                case "old_alpha":
                    return Strings.get("versions.typeAlpha");
                default:
                    return type;
            }
        }
    }
}
