package by.agro.launcher.ui.components;

import by.agro.launcher.core.DownloadManager;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;


public final class DownloadsPanel extends JPanel implements DownloadManager.Listener {
    private final DownloadManager manager;
    private final JPanel rows = new JPanel();

    public DownloadsPanel(DownloadManager manager) {
        super(new BorderLayout(0, 6));
        this.manager = manager;
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JLabel title = new JLabel(Strings.get("downloads.title"));
        title.setForeground(AgroTheme.textPrimary());
        add(title, BorderLayout.NORTH);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(rows);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(420, 180));
        add(scroll, BorderLayout.CENTER);
        manager.addListener(this);
        refresh();
    }

    @Override public void downloadsChanged() { SwingUtilities.invokeLater(this::refresh); }

    private void refresh() {
        rows.removeAll();
        int shown = 0;
        for (DownloadManager.Handle task : manager.snapshot()) {
            if (shown++ >= 8) break;
            JPanel row = new JPanel(new BorderLayout(6, 2));
            JLabel name = new JLabel(task.name() + " — " + status(task.status()));
            JProgressBar progress = new JProgressBar(0, 1000);
            progress.setStringPainted(true);
            if (task.progress() < 0) {
                progress.setIndeterminate(task.status() == DownloadManager.Status.RUNNING);
                progress.setString(formatSpeed(task.bytesPerSecond()));
            } else {
                progress.setValue((int) (task.progress() * 1000));
                progress.setString((int) (task.progress() * 100) + "% · " + formatSpeed(task.bytesPerSecond()));
            }
            JPanel actions = new JPanel();
            if (task.status() == DownloadManager.Status.RUNNING) {
                JButton pause = new JButton(Strings.get("downloads.pause"));
                pause.addActionListener(e -> manager.pause(task));
                actions.add(pause);
            } else if (task.status() == DownloadManager.Status.PAUSED) {
                JButton resume = new JButton(Strings.get("downloads.resume"));
                resume.addActionListener(e -> manager.resume(task));
                actions.add(resume);
            }
            if (task.status() == DownloadManager.Status.RUNNING || task.status() == DownloadManager.Status.PAUSED
                    || task.status() == DownloadManager.Status.QUEUED) {
                JButton cancel = new JButton(Strings.get("downloads.cancel"));
                cancel.addActionListener(e -> manager.cancel(task));
                actions.add(cancel);
            }
            row.add(name, BorderLayout.NORTH);
            row.add(progress, BorderLayout.CENTER);
            row.add(actions, BorderLayout.EAST);
            rows.add(row);
            rows.add(Box.createVerticalStrut(5));
        }
        if (shown == 0) rows.add(new JLabel(Strings.get("downloads.empty")));
        revalidate();
        repaint();
    }

    private static String status(DownloadManager.Status status) {
        return Strings.get("downloads.status." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static String formatSpeed(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes >= 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f MB/s", bytes / 1048576d);
        return String.format(java.util.Locale.ROOT, "%.0f KB/s", bytes / 1024d);
    }
}
