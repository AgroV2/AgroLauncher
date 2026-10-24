package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.backup.BackupManifest;
import by.agro.launcher.backup.BackupService;
import by.agro.launcher.core.Json;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.mods.ModBuildManager;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class BackupsPanel extends JPanel {

    private final LauncherContext context;
    private final Consumer<String> statusReporter;

    private final DefaultListModel<BackupEntry> model = new DefaultListModel<>();
    private final JList<BackupEntry> list = new JList<>(model);
    private final JLabel summaryLabel = new JLabel(" ");
    private final JButton restoreButton = UiFactory.primaryButton(Strings.get("backups.restore"));

    public BackupsPanel(LauncherContext context, Consumer<String> statusReporter) {
        this.context = context;
        this.statusReporter = statusReporter;

        setOpaque(false);
        setLayout(new BorderLayout(0, 10));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setCellRenderer(new BackupRenderer());
        list.setFixedCellHeight(56);
        list.addListSelectionListener(e ->
                restoreButton.setEnabled(list.getSelectedValue() != null));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        restoreButton.setEnabled(false);
        restoreButton.addActionListener(e -> restoreSelected());
        JButton open = UiFactory.button(Strings.get("common.openFolder"));
        open.addActionListener(e -> openBackupsFolder());
        JButton reload = UiFactory.button(Strings.get("common.refresh"));
        reload.addActionListener(e -> refresh());
        actions.add(restoreButton);
        actions.add(open);
        actions.add(reload);

        JLabel hint = UiFactory.hint(Strings.get("backups.hint"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = UiFactory.transparentPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        summaryLabel.setFont(AgroTheme.font(12));
        summaryLabel.setForeground(AgroTheme.textSecondary());
        summaryLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(summaryLabel);
        header.add(UiFactory.verticalGap(6));
        header.add(hint);
        header.add(UiFactory.verticalGap(10));

        add(header, BorderLayout.NORTH);
        add(UiFactory.scroll(list), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        refresh();
    }

    public void refresh() {
        List<BackupEntry> entries = scan();
        model.clear();
        for (BackupEntry entry : entries) {
            model.addElement(entry);
        }
        summaryLabel.setText(entries.isEmpty()
                ? Strings.get("backups.empty")
                : Strings.get("backups.summary", entries.size()));
        restoreButton.setEnabled(list.getSelectedValue() != null);
    }

    private List<BackupEntry> scan() {
        List<BackupEntry> result = new ArrayList<>();
        Path root = context.paths().root().resolve("backups");
        if (!Files.isDirectory(root)) {
            return result;
        }
        try (Stream<Path> stream = Files.walk(root, 4)) {
            stream.filter(Files::isDirectory)
                    .filter(dir -> Files.isRegularFile(dir.resolve("backup.json")))
                    .forEach(dir -> {
                        try {
                            BackupManifest manifest = Json.read(dir.resolve("backup.json"),
                                    BackupManifest.class);
                            if (manifest != null && manifest.entries != null) {
                                result.add(new BackupEntry(dir, manifest.backupId,
                                        manifest.createdAt, manifest.entries.size()));
                            }
                        } catch (Exception ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
        result.sort(Comparator.comparing((BackupEntry entry) -> entry.backupId).reversed());
        return result;
    }

    private void restoreSelected() {
        BackupEntry selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        Path gameDir = currentGameDir();
        if (gameDir == null) {
            JOptionPane.showMessageDialog(this, Strings.get("builds.selectVersion"),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        String confirm = Strings.get("backups.restoreConfirm", selected.backupId, gameDir);
        if (JOptionPane.showConfirmDialog(this, confirm, Strings.get("backups.restore"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        restoreButton.setEnabled(false);
        report(Strings.get("backups.restoring", selected.backupId));
        Path backupsRoot = context.paths().root().resolve("backups");
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                new BackupService(backupsRoot).restore(selected.directory, gameDir);
                return gameDir;
            }

            @Override
            protected void done() {
                restoreButton.setEnabled(list.getSelectedValue() != null);
                try {
                    Path restored = get();
                    report(Strings.get("backups.restored", restored));
                    JOptionPane.showMessageDialog(BackupsPanel.this,
                            Strings.get("backups.restored", restored),
                            Strings.get("backups.restore"), JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    report(Strings.get("backups.restoreFailed", cause.getMessage()));
                    JOptionPane.showMessageDialog(BackupsPanel.this,
                            Strings.get("backups.restoreFailed", cause.getMessage()),
                            Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private Path currentGameDir() {
        ModBuildManager buildManager = new ModBuildManager(context.paths());
        ModBuildManager.ModBuild activeBuild = buildManager.list().stream()
                .filter(build -> build.id.equals(context.settings().selectedModBuildId))
                .findFirst().orElse(null);
        if (activeBuild != null) {
            return buildManager.gameDir(activeBuild);
        }
        String minecraft = context.settings().selectedVersion;
        if (minecraft == null || minecraft.isBlank()) {
            return null;
        }
        LoaderType loader = LoaderType.fromId(context.settings().selectedLoader);
        String loaderVersion = context.settings().selectedLoaderVersion;
        if (loaderVersion == null || loaderVersion.isBlank()) {
            loaderVersion = "recommended";
        }
        return context.paths().installationGameDir(minecraft, loader.id(),
                loader == LoaderType.VANILLA ? "default" : loaderVersion);
    }

    private void openBackupsFolder() {
        Path root = context.paths().root().resolve("backups");
        try {
            Files.createDirectories(root);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(root.toFile());
            } else {
                JOptionPane.showMessageDialog(this, root.toString(),
                        Strings.get("settings.folderPath"), JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    Strings.get("mods.openFolderFailed", e.getMessage()),
                    Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
        }
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }

    public static final class BackupEntry {
        public final Path directory;
        public final String backupId;
        public final String createdAt;
        public final int files;

        BackupEntry(Path directory, String backupId, String createdAt, int files) {
            this.directory = directory;
            this.backupId = backupId;
            this.createdAt = createdAt;
            this.files = files;
        }

        public String formattedCreated() {
            try {
                return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
                        .format(ZonedDateTime.parse(createdAt));
            } catch (Exception e) {
                return createdAt == null ? "" : createdAt;
            }
        }

        @Override
        public String toString() {
            return backupId;
        }
    }

    private static final class BackupRenderer extends JPanel
            implements javax.swing.ListCellRenderer<BackupEntry> {
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();

        BackupRenderer() {
            setOpaque(false);
            setLayout(new GridLayout(2, 1));
            add(title);
            add(detail);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends BackupEntry> list,
                                                      BackupEntry value, int index, boolean selected,
                                                      boolean focused) {
            title.setText(value.backupId);
            title.setFont(AgroTheme.boldFont(14));
            title.setForeground(selected ? AgroTheme.accent() : AgroTheme.textPrimary());
            detail.setText(Strings.get("backups.entryDetail", value.formattedCreated(), value.files));
            detail.setFont(AgroTheme.font(11));
            detail.setForeground(AgroTheme.textMuted());
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            return this;
        }
    }
}
