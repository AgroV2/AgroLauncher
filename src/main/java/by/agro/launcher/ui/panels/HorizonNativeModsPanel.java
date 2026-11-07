package by.agro.launcher.ui.panels;

import by.agro.launcher.horizon.HorizonNativeMods;
import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;


final class HorizonNativeModsPanel extends JPanel {
    private final HorizonNativeMods mods;
    private final LauncherContext context;
    private final DefaultListModel<HorizonNativeMods.Mod> model = new DefaultListModel<>();
    private final JList<HorizonNativeMods.Mod> list = new JList<>(model);
    private final List<HorizonNativeMods.Mod> allMods = new ArrayList<>();
    private final JTextField search = new JTextField();
    private final JLabel summary = new JLabel();
    private final JLabel pathHint = UiFactory.hint(" ");
    private final JTextArea log = new JTextArea(5, 50);
    private final JScrollPane logScroll = UiFactory.scroll(log);
    private final JLabel status = new JLabel(" ");
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JButton buildButton = UiFactory.primaryButton(Strings.get("hnmod.newInstance"));
    private final java.util.function.BooleanSupplier offline;
    private boolean building;

    HorizonNativeModsPanel(LauncherContext context, java.util.function.BooleanSupplier offline) {
        this(context, context.horizon().nativeMods(), offline);
    }

    HorizonNativeModsPanel(LauncherContext context, HorizonNativeMods mods, java.util.function.BooleanSupplier offline) {
        this.context = context;
        this.mods = mods;
        this.offline = offline;
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setOpaque(false);
        list.setBackground(new Color(0, 0, 0, 0));
        list.setFixedCellHeight(44);
        list.setCellRenderer(new InstalledModCellRenderer<>(mod -> new InstalledModCellRenderer.Row(
                mod.id(), Strings.get("hnmod.details", mod.version(), mod.file().getFileName()),
                Strings.get(mod.enabled() ? "mods.enabled" : "mods.disabled"), mod.enabled(), false)));
        add(UiFactory.scroll(list), BorderLayout.CENTER);

        summary.setFont(AgroTheme.font(12));
        summary.setForeground(AgroTheme.textSecondary());
        pathHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        search.putClientProperty("JTextField.placeholderText", Strings.get("mods.searchPlaceholder"));
        UiFactory.fixHeight(search, 30);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        JPanel texts = UiFactory.transparentPanel();
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(UiFactory.hint(Strings.get("hnmod.hint")));
        texts.add(UiFactory.verticalGap(6));
        texts.add(summary);
        texts.add(UiFactory.verticalGap(2));
        texts.add(pathHint);
        texts.add(UiFactory.verticalGap(6));
        JPanel searchRow = new JPanel(new BorderLayout());
        searchRow.setOpaque(false);
        searchRow.add(search, BorderLayout.CENTER);
        texts.add(searchRow);

        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        logScroll.setPreferredSize(new Dimension(400, 110));
        logScroll.setVisible(false);
        status.setFont(AgroTheme.font(12));
        status.setForeground(AgroTheme.textSecondary());
        status.setVisible(false);
        JPanel buildLog = UiFactory.transparentPanel();
        buildLog.setLayout(new BorderLayout(0, 4));
        buildLog.add(status, BorderLayout.NORTH);
        buildLog.add(logScroll, BorderLayout.CENTER);

        actions.setOpaque(false);
        JButton addButton = UiFactory.primaryButton(Strings.get("mods.addFiles"));
        addButton.addActionListener(e -> add());
        JButton toggleButton = UiFactory.button(Strings.get("mods.toggle"));
        toggleButton.addActionListener(e -> toggle());
        JButton deleteButton = UiFactory.dangerButton(Strings.get("common.delete"));
        deleteButton.addActionListener(e -> remove());
        JButton openButton = UiFactory.button(Strings.get("common.openFolder"));
        openButton.addActionListener(e -> UiFactory.openInFileManager(this, mods.directory()));
        JButton refreshButton = UiFactory.button(Strings.get("common.refresh"));
        refreshButton.addActionListener(e -> refresh());
        buildButton.addActionListener(e -> build());
        actions.add(addButton); actions.add(toggleButton); actions.add(deleteButton);
        actions.add(openButton); actions.add(refreshButton);

        JPanel buildControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buildControls.setOpaque(false);
        buildControls.add(buildButton);
        JPanel footerTop = UiFactory.transparentPanel();
        footerTop.setLayout(new BorderLayout(0, 10));
        footerTop.add(buildControls, BorderLayout.NORTH);
        footerTop.add(texts, BorderLayout.CENTER);
        JPanel bottom = UiFactory.transparentPanel();
        bottom.setLayout(new BorderLayout(0, 4));
        bottom.add(footerTop, BorderLayout.NORTH);
        bottom.add(buildLog, BorderLayout.CENTER);
        bottom.add(actions, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private void busy(boolean value) {
        building = value;
        for (Component c : actions.getComponents()) c.setEnabled(!value);
        list.setEnabled(!value);
        search.setEnabled(!value);
        buildButton.setEnabled(!value);
    }
    private void error(Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
    }
    void refresh() {
        if (building) return;
        try {
            List<HorizonNativeMods.Mod> loaded = mods.list();
            allMods.clear(); allMods.addAll(loaded);
            applyFilter();
            summary.setText(Strings.get("mods.summary", loaded.size(), loaded.stream().filter(HorizonNativeMods.Mod::enabled).count()));
            pathHint.setText(Strings.get("hnmod.path", mods.directory()));
        } catch (IOException e) {
            allMods.clear(); model.clear();
            summary.setText(Strings.get("common.error"));
            pathHint.setText(e.getMessage());
        }
    }
    static boolean matches(HorizonNativeMods.Mod mod, String text) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() || mod.id().toLowerCase(Locale.ROOT).contains(query)
                || mod.version().toLowerCase(Locale.ROOT).contains(query)
                || mod.file().getFileName().toString().toLowerCase(Locale.ROOT).contains(query);
    }
    private void applyFilter() {
        model.clear();
        for (var mod : allMods) if (matches(mod, search.getText())) model.addElement(mod);
    }
    private void add() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(Strings.get("hnmod.fileFilter"), "hnmod"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File[] files = chooser.getSelectedFiles();
        if (files.length == 0 && chooser.getSelectedFile() != null) files = new File[]{chooser.getSelectedFile()};
        for (File file : files) try { mods.add(file.toPath()); } catch (IOException e) { error(e); }
        refresh();
    }
    private void toggle() {
        List<HorizonNativeMods.Mod> selected = list.getSelectedValuesList();
        for (var mod : selected) try { mods.setEnabled(mod.id(), !mod.enabled()); } catch (IOException e) { error(e); }
        if (!selected.isEmpty()) refresh();
    }
    private void remove() {
        List<HorizonNativeMods.Mod> selected = list.getSelectedValuesList();
        if (selected.isEmpty()) return;
        if (JOptionPane.showConfirmDialog(this, Strings.get("mods.deleteConfirm", selected.size()),
                Strings.get("common.confirm"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        for (var mod : selected) try { mods.remove(mod); } catch (IOException e) { error(e); }
        refresh();
    }
    private void build() {
        if (offline.getAsBoolean()) { error(new IOException(Strings.get("hnmod.offline"))); return; }
        List<HorizonNativeMods.Mod> enabled;
        try { enabled = mods.list().stream().filter(HorizonNativeMods.Mod::enabled).toList(); }
        catch (IOException e) { error(e); return; }
        if (enabled.isEmpty()) { error(new IOException(Strings.get("hnmod.noEnabled"))); return; }
        String names = String.join(", ", enabled.stream().map(HorizonNativeMods.Mod::id).toList());
        Object[] options = { Strings.get("hnmod.trust"), Strings.get("common.cancel") };
        int result = JOptionPane.showOptionDialog(this, Strings.get("hnmod.trustWarning", names), Strings.get("common.warning"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        if (result != 0) return;
        busy(true); log.setText(""); status.setText(Strings.get("hnmod.building"));
        status.setVisible(true); logScroll.setVisible(true); revalidate(); repaint();
        AtomicLong last = new AtomicLong();
        new SwingWorker<by.agro.launcher.mods.ModBuildManager.ModBuild, String>() {
            @Override protected by.agro.launcher.mods.ModBuildManager.ModBuild doInBackground() throws Exception {
                var builds = new by.agro.launcher.mods.ModBuildManager(context.paths());
                var created = builds.newHorizonBuild(enabled.size());
                Path buildDir = builds.buildDirectory(created);
                java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>();
                boolean success = false;
                try {
                    var target = context.horizon().nativeMods(builds.gameDir(created));
                    for (var mod : enabled) target.add(mod.file());
                    ProgressListener progress = new ProgressListener() {
                        @Override public void onProgress(String stage, long current, long total, String detail) { publish(stage + " " + detail); }
                        @Override public void onMessage(String message) { publish(message); }
                    };
                    Path base = context.horizon().prepareNativeBase(context.manifestIfLoaded(), progress);
                    Path launcher = context.horizon().downloadNativeBuilder(buildDir.resolve("official-builder"), progress);
                    target.buildOfficial(launcher, base, line -> {
                        if (tail.size() == 20) tail.removeFirst(); tail.addLast(line);
                        long now = System.nanoTime(), prev = last.get();
                        if (now - prev > 150_000_000L && last.compareAndSet(prev, now)) publish(line);
                    });
                    builds.writeMetadata(created);
                    success = true;
                    return created;
                } catch (Exception e) {
                    publish("---", String.join("\n", tail)); throw e;
                } finally {
                    if (!success) by.agro.launcher.core.FileTrees.deleteQuietly(buildDir);
                }
            }
            @Override protected void process(List<String> chunks) {
                for (String s : chunks) log.append(s + "\n");
                if (log.getDocument().getLength() > 20000) log.setText(log.getText().substring(log.getText().length() - 10000));
                log.setCaretPosition(log.getDocument().getLength());
            }
            @Override protected void done() {
                busy(false);
                try {
                    var made = get(); context.settings().selectedModBuildId = made.id;
                    context.settings().selectedVersion = made.minecraftVersion; context.settings().selectedLoader = made.loader;
                    context.settings().selectedLoaderVersion = ""; context.settings().save();
                    status.setText(Strings.get("hnmod.instanceDone", made.name));
                    refresh(); JOptionPane.showMessageDialog(HorizonNativeModsPanel.this, Strings.get("hnmod.instanceDone", made.name));
                } catch (Exception e) { refresh(); Throwable t = e.getCause() == null ? e : e.getCause();
                    status.setText(Strings.get("common.error") + ": " + String.valueOf(t.getMessage()));
                    error(new IOException(t.getMessage(), t)); }
            }

        }.execute();
    }
}
