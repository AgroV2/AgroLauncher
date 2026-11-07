package by.agro.launcher.ui.panels;

import by.agro.launcher.core.DiskSpace;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.worlds.WorldManager;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.concurrent.Callable;


final class WorldsDialog extends JDialog {

    private final WorldManager manager;
    private final DefaultListModel<WorldManager.WorldInfo> model = new DefaultListModel<>();
    private final JList<WorldManager.WorldInfo> list = new JList<>(model);
    private final JLabel statusLabel = new JLabel(" ");
    private final List<JButton> selectionButtons;
    private final List<JButton> allButtons;
    private boolean busy;

    WorldsDialog(Window owner, String instanceTitle, Path savesDir) {
        super(owner, Strings.get("worlds.title", instanceTitle), ModalityType.APPLICATION_MODAL);
        this.manager = new WorldManager(savesDir);
        setSize(820, 520);
        setLocationRelativeTo(owner);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new WorldRenderer());
        list.setFixedCellHeight(54);
        list.addListSelectionListener(e -> updateButtons());
        content.add(new JScrollPane(list), BorderLayout.CENTER);

        JButton openSaves = UiFactory.button(Strings.get("common.openFolder"));
        openSaves.addActionListener(e -> openSavesFolder());
        JButton openWorld = UiFactory.button(Strings.get("worlds.openWorld"));
        openWorld.addActionListener(e -> {
            WorldManager.WorldInfo selected = list.getSelectedValue();
            if (selected != null) UiFactory.openInFileManager(this, selected.path());
        });
        JButton duplicate = UiFactory.button(Strings.get("worlds.duplicate"));
        duplicate.addActionListener(e -> duplicateSelected());
        JButton importButton = UiFactory.button(Strings.get("worlds.import"));
        importButton.addActionListener(e -> importWorld());
        JButton export = UiFactory.button(Strings.get("worlds.export"));
        export.addActionListener(e -> exportSelected());
        JButton delete = UiFactory.dangerButton(Strings.get("common.delete"));
        delete.addActionListener(e -> deleteSelected());
        JButton close = UiFactory.button(Strings.get("common.close"));
        close.addActionListener(e -> dispose());
        selectionButtons = List.of(openWorld, duplicate, export, delete);
        allButtons = List.of(openSaves, openWorld, duplicate, importButton, export, delete);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (JButton button : allButtons) buttons.add(button);
        buttons.add(close);
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        statusLabel.setFont(AgroTheme.font(12));
        statusLabel.setForeground(AgroTheme.textSecondary());
        bottom.add(statusLabel, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        reload(null, null);
    }

    private void updateButtons() {
        boolean selected = list.getSelectedValue() != null;
        for (JButton button : allButtons) button.setEnabled(!busy);
        for (JButton button : selectionButtons) button.setEnabled(!busy && selected);
    }

    private void reload(Path select, String message) {
        busy = true;
        updateButtons();
        statusLabel.setText(Strings.get("common.loading"));
        new SwingWorker<List<WorldManager.WorldInfo>, Void>() {
            @Override
            protected List<WorldManager.WorldInfo> doInBackground() {
                return manager.list();
            }

            @Override
            protected void done() {
                busy = false;
                model.clear();
                try {
                    List<WorldManager.WorldInfo> worlds = get();
                    long total = 0;
                    for (WorldManager.WorldInfo world : worlds) {
                        model.addElement(world);
                        total += world.sizeBytes();
                        if (world.path().equals(select)) list.setSelectedValue(world, true);
                    }
                    long free = DiskSpace.usableBytes(manager.savesDir());
                    statusLabel.setText(worlds.isEmpty() ? Strings.get("worlds.empty")
                            : Strings.get("worlds.summary", worlds.size(), DiskSpace.format(total), DiskSpace.format(free)));
                    statusLabel.setForeground(DiskSpace.isLow(manager.savesDir())
                            ? AgroTheme.warning() : AgroTheme.textSecondary());
                    if (message != null) statusLabel.setText(message + " · " + statusLabel.getText());
                } catch (Exception e) {
                    statusLabel.setText(Strings.get("worlds.loadFailed", rootMessage(e)));
                }
                updateButtons();
            }
        }.execute();
    }

    private void runTask(String progress, Callable<Path> task, String successKey) {
        busy = true;
        updateButtons();
        statusLabel.setText(progress);
        new SwingWorker<Path, Void>() {
            @Override
            protected Path doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    Path result = get();
                    reload(result, successKey == null ? null
                            : Strings.get(successKey, result == null ? "" : result.getFileName()));
                } catch (Exception e) {
                    updateButtons();
                    statusLabel.setText(" ");
                    JOptionPane.showMessageDialog(WorldsDialog.this, rootMessage(e),
                            Strings.get("common.error"), JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private static String rootMessage(Exception error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private void openSavesFolder() {
        try {
            Files.createDirectories(manager.savesDir());
        } catch (Exception ignored) {
        }
        UiFactory.openInFileManager(this, manager.savesDir());
    }

    private void duplicateSelected() {
        WorldManager.WorldInfo selected = list.getSelectedValue();
        if (selected == null) return;
        runTask(Strings.get("worlds.copying", selected.displayName()),
                () -> manager.duplicate(selected.path()), "worlds.copied");
    }

    private void importWorld() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("worlds.importTitle"));
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setFileFilter(new FileNameExtensionFilter(Strings.get("worlds.zipFilter"), "zip"));
        chooser.setAcceptAllFileFilterUsed(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();
        runTask(Strings.get("worlds.importing", source.getFileName()),
                () -> manager.importWorld(source), "worlds.imported");
    }

    private void exportSelected() {
        WorldManager.WorldInfo selected = list.getSelectedValue();
        if (selected == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(Strings.get("worlds.exportTitle"));
        chooser.setFileFilter(new FileNameExtensionFilter(Strings.get("worlds.zipFilter"), "zip"));
        chooser.setSelectedFile(new File(WorldManager.suggestedArchiveName(selected.path())));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path chosen = chooser.getSelectedFile().toPath();
        Path target = chosen.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")
                ? chosen : chosen.resolveSibling(chosen.getFileName() + ".zip");
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this,
                Strings.get("worlds.overwriteConfirm", target.getFileName()), Strings.get("common.confirm"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        runTask(Strings.get("worlds.exporting", selected.displayName()), () -> {
            manager.exportZip(selected.path(), target);
            return target;
        }, "worlds.exported");
    }

    private void deleteSelected() {
        WorldManager.WorldInfo selected = list.getSelectedValue();
        if (selected == null) return;
        if (JOptionPane.showConfirmDialog(this, Strings.get("instances.deleteEntryConfirm", selected.displayName()),
                Strings.get("common.confirm"), JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        runTask(Strings.get("worlds.deleting", selected.displayName()), () -> {
            manager.delete(selected.path());
            return selected.path();
        }, "worlds.deleted");
    }

    private static final class WorldRenderer extends JPanel implements ListCellRenderer<WorldManager.WorldInfo> {
        private final JLabel title = new JLabel();
        private final JLabel details = new JLabel();

        WorldRenderer() {
            setLayout(new GridLayout(2, 1));
            add(title);
            add(details);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends WorldManager.WorldInfo> list,
                                                      WorldManager.WorldInfo value, int index,
                                                      boolean selected, boolean focused) {
            title.setText(value.displayName());
            title.setFont(AgroTheme.boldFont(13));
            title.setForeground(selected ? AgroTheme.accent() : AgroTheme.textPrimary());
            String played = value.lastPlayedMillis() <= 0 ? "" : DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(Strings.language().locale())
                    .format(Instant.ofEpochMilli(value.lastPlayedMillis()).atZone(ZoneId.systemDefault()));
            details.setText(Strings.get("worlds.details", value.folderName(), played, DiskSpace.format(value.sizeBytes())));
            details.setFont(AgroTheme.font(11));
            details.setForeground(AgroTheme.textMuted());
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            setOpaque(selected);
            setBackground(AgroTheme.bgHover());
            return this;
        }
    }
}
