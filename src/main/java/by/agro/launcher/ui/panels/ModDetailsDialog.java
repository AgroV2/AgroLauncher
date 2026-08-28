package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.modrinth.ModInstaller;
import by.agro.launcher.modrinth.ModrinthClient;
import by.agro.launcher.modrinth.ModrinthProject;
import by.agro.launcher.modrinth.ModrinthVersion;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

public final class ModDetailsDialog extends JDialog {

    private final ModrinthProject project;
    private final ModrinthClient client;
    private final ModInstaller installer;
    private final LauncherContext context;
    private final String loaderFilter;
    private final String versionFilter;
    private final Consumer<String> onInstalled;

    private final DefaultComboBoxModel<ModrinthVersion> versionsModel = new DefaultComboBoxModel<>();
    private final JComboBox<ModrinthVersion> versionCombo = new JComboBox<>(versionsModel);
    private final JCheckBox withDependencies = new JCheckBox(Strings.get("modDialog.withDependencies"));
    private final JTextArea descriptionArea = new JTextArea();
    private final JLabel versionInfoLabel = new JLabel(" ");
    private final JLabel dependencyLabel = new JLabel(" ");
    private final JButton installButton = UiFactory.primaryButton(Strings.get("modDialog.install"));
    private final JProgressBar progressBar = new JProgressBar();
    private final JLabel progressLabel = new JLabel(" ");

    public ModDetailsDialog(Window owner, ModrinthProject project, ModrinthClient client,
                            ModInstaller installer, LauncherContext context,
                            String loaderFilter, String versionFilter,
                            Consumer<String> onInstalled) {
        super(owner, project.title, ModalityType.APPLICATION_MODAL);
        this.project = project;
        this.client = client;
        this.installer = installer;
        this.context = context;
        this.loaderFilter = loaderFilter;
        this.versionFilter = versionFilter;
        this.onInstalled = onInstalled;

        setSize(new Dimension(620, 560));
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(AgroTheme.bgBase());
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
        loadDetails();
    }

    private JComponent buildHeader() {
        JPanel header = UiFactory.transparentPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = UiFactory.title(project.title);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        StringBuilder meta = new StringBuilder();
        if (project.author != null && !project.author.isBlank()) {
            meta.append(Strings.get("browser.byAuthor", project.author)).append("   •   ");
        }
        meta.append("↓ ").append(project.formattedDownloads());
        var categories = project.visibleCategories();
        if (!categories.isEmpty()) {
            meta.append("   •   ").append(String.join(", ", categories));
        }

        JLabel metaLabel = UiFactory.subtitle(meta.toString());
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        header.add(title);
        header.add(UiFactory.verticalGap(4));
        header.add(metaLabel);
        return header;
    }

    private JComponent buildBody() {
        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BorderLayout(0, 14));

        descriptionArea.setText(project.description);
        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(AgroTheme.font(13));
        descriptionArea.setForeground(AgroTheme.textSecondary());
        descriptionArea.setBackground(AgroTheme.bgPanel());
        descriptionArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AgroTheme.border(), 1, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)));

        body.add(UiFactory.scroll(descriptionArea), BorderLayout.CENTER);
        body.add(buildVersionSection(), BorderLayout.SOUTH);
        return body;
    }

    private JComponent buildVersionSection() {
        JPanel section = UiFactory.transparentPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

        JLabel label = UiFactory.fieldLabel(Strings.get("modDialog.version"));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        UiFactory.fixHeight(versionCombo, 34);
        versionCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        versionCombo.addActionListener(e -> updateVersionInfo());

        versionInfoLabel.setFont(AgroTheme.font(11));
        versionInfoLabel.setForeground(AgroTheme.textMuted());
        versionInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        dependencyLabel.setFont(AgroTheme.font(11));
        dependencyLabel.setForeground(AgroTheme.warning());
        dependencyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        withDependencies.setSelected(context.settings().installModDependencies);
        withDependencies.setOpaque(false);
        withDependencies.setFont(AgroTheme.font(12));
        withDependencies.setAlignmentX(Component.LEFT_ALIGNMENT);
        withDependencies.addActionListener(e -> {
            context.settings().installModDependencies = withDependencies.isSelected();
            context.settings().save();
        });

        section.add(label);
        section.add(UiFactory.verticalGap(4));
        section.add(versionCombo);
        section.add(UiFactory.verticalGap(6));
        section.add(versionInfoLabel);
        section.add(UiFactory.verticalGap(2));
        section.add(dependencyLabel);
        section.add(UiFactory.verticalGap(10));
        section.add(withDependencies);
        return section;
    }

    private JComponent buildFooter() {
        JPanel footer = UiFactory.transparentPanel();
        footer.setLayout(new BorderLayout(0, 10));

        progressBar.setVisible(false);
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(100, 8));

        progressLabel.setFont(AgroTheme.font(11));
        progressLabel.setForeground(AgroTheme.textSecondary());

        JPanel progressArea = UiFactory.transparentPanel();
        progressArea.setLayout(new BoxLayout(progressArea, BoxLayout.Y_AXIS));
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressArea.add(progressLabel);
        progressArea.add(UiFactory.verticalGap(4));
        progressArea.add(progressBar);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);

        JButton pageButton = UiFactory.button(Strings.get("modDialog.openPage"));
        pageButton.addActionListener(e -> openPage());

        JButton closeButton = UiFactory.button(Strings.get("common.close"));
        closeButton.addActionListener(e -> dispose());

        installButton.setPreferredSize(new Dimension(150, 38));
        installButton.setEnabled(false);
        installButton.addActionListener(e -> install());

        buttons.add(pageButton);
        buttons.add(closeButton);
        buttons.add(installButton);

        footer.add(progressArea, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.SOUTH);
        return footer;
    }

    private void loadDetails() {
        progressLabel.setText(Strings.get("modDialog.loadingVersions"));
        progressBar.setVisible(true);

        new SwingWorker<List<ModrinthVersion>, Void>() {
            private String fullDescription;

            @Override
            protected List<ModrinthVersion> doInBackground() throws Exception {
                try {
                    fullDescription = client.projectBody(project.id);
                } catch (Exception ignored) {
                }
                List<ModrinthVersion> versions = client.versions(project.id, loaderFilter, versionFilter);
                if (versions.isEmpty()) {
                    versions = client.versions(project.id, loaderFilter, null);
                }
                return versions;
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                progressLabel.setText(" ");
                try {
                    if (fullDescription != null && !fullDescription.isBlank()) {
                        descriptionArea.setText(stripMarkdown(fullDescription));
                        descriptionArea.setCaretPosition(0);
                    }
                    List<ModrinthVersion> versions = get();
                    versionsModel.removeAllElements();
                    for (ModrinthVersion version : versions) {
                        versionsModel.addElement(version);
                    }
                    if (versions.isEmpty()) {
                        versionInfoLabel.setText(Strings.get("modDialog.noVersions"));
                        installButton.setEnabled(false);
                    } else {
                        for (int i = 0; i < versionsModel.getSize(); i++) {
                            if (versionsModel.getElementAt(i).isStable()) {
                                versionCombo.setSelectedIndex(i);
                                break;
                            }
                        }
                        installButton.setEnabled(true);
                        updateVersionInfo();
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    versionInfoLabel.setText(Strings.get("modDialog.versionsFailed", cause.getMessage()));
                }
            }
        }.execute();
    }

    private void updateVersionInfo() {
        ModrinthVersion version = (ModrinthVersion) versionCombo.getSelectedItem();
        if (version == null) {
            versionInfoLabel.setText(" ");
            dependencyLabel.setText(" ");
            return;
        }

        ModrinthVersion.File file = version.primaryFile();
        StringBuilder info = new StringBuilder();
        if (file != null) {
            info.append(file.filename).append("  •  ").append(file.formattedSize());
        }
        if (!version.gameVersions.isEmpty()) {
            info.append("  •  MC ").append(String.join(", ",
                    version.gameVersions.subList(0, Math.min(4, version.gameVersions.size()))));
        }
        if (!version.loaders.isEmpty()) {
            info.append("  •  ").append(String.join(", ", version.loaders));
        }
        versionInfoLabel.setText(info.toString());

        int required = version.requiredDependencies().size();
        if (required > 0) {
            dependencyLabel.setText(Strings.get("modDialog.requiresDeps", required)
                    + (withDependencies.isSelected() ? Strings.get("modDialog.depsWillInstall") : Strings.get("modDialog.depsDisabled")));
        } else {
            dependencyLabel.setText(" ");
        }

        boolean alreadyInstalled = installer.isInstalled(version);
        installButton.setText(alreadyInstalled ? Strings.get("modDialog.alreadyInstalled") : Strings.get("modDialog.install"));
        installButton.setEnabled(!alreadyInstalled);
    }

    private void install() {
        ModrinthVersion version = (ModrinthVersion) versionCombo.getSelectedItem();
        if (version == null) {
            return;
        }

        installButton.setEnabled(false);
        progressBar.setVisible(true);
        progressLabel.setText(Strings.get("modDialog.installing"));

        ProgressListener listener = new ProgressListener() {
            @Override
            public void onProgress(String stage, long current, long total, String detail) {
                SwingUtilities.invokeLater(() ->
                        progressLabel.setText(stage + (detail != null ? " — " + detail : "")));
            }

            @Override
            public void onMessage(String message) {
                SwingUtilities.invokeLater(() -> progressLabel.setText(message));
            }
        };

        boolean dependencies = withDependencies.isSelected();

        new SwingWorker<ModInstaller.Result, Void>() {
            @Override
            protected ModInstaller.Result doInBackground() throws Exception {
                return installer.install(version, loaderFilter, versionFilter, dependencies, listener);
            }

            @Override
            protected void done() {
                progressBar.setVisible(false);
                try {
                    ModInstaller.Result result = get();
                    progressLabel.setText(result.summary());
                    if (onInstalled != null) {
                        onInstalled.accept(project.title + ": " + result.summary());
                    }
                    if (result.failed.isEmpty()) {
                        installButton.setText(Strings.get("modDialog.installed"));
                    } else {
                        installButton.setEnabled(true);
                        progressLabel.setText(result.summary() + " — " + result.failed.get(0));
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    progressLabel.setText(Strings.get("common.error") + ": " + cause.getMessage());
                    installButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void openPage() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(project.pageUrl()));
            }
        } catch (Exception e) {
            progressLabel.setText(Strings.get("modDialog.browserFailed", e.getMessage()));
        }
    }

    private String stripMarkdown(String markdown) {
        return markdown
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replaceAll("<[^>]+>", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ")
                .replaceAll("`{1,3}", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
