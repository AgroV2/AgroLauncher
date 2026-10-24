package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.Main;
import by.agro.launcher.core.Settings;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.components.LogoIcon;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.update.UpdateChecker;
import by.agro.launcher.update.UpdateManager;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Image;
import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;

public final class AboutPanel extends JPanel {

    private static final String CREATOR_URL = "https://agrov2.github.io";
    private static final String TELEGRAM_URL = "https://t.me/AgroLauncher";
    private static final String MARGYCRAFT_CREATOR_URL = "https://t.me/narezany";
    private static final String MARGYCRAFT_SERVER_URL = "https://craft.narez.xyz";
    private static final String MARGYCRAFT_ICON_RESOURCE = "/margycrafticon.png";

    private final LauncherContext context;
    private final Consumer<String> statusReporter;
    private LogoIcon heroLogo;
    private JLabel heroLogoLabel;

    public AboutPanel(LauncherContext context, Consumer<String> statusReporter) {
        this.context = context;
        this.statusReporter = statusReporter;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        AgroTheme.addThemeListener(palette -> javax.swing.SwingUtilities.invokeLater(() -> {
            if (heroLogo != null) {
                heroLogo.setColor(AgroTheme.gradientAverageColor());
            }
            if (heroLogoLabel != null) {
                heroLogoLabel.repaint();
            }
        }));
    }

    private Settings settings() {
        return context.settings();
    }

    private JComponent buildHeader() {
        JPanel header = UiFactory.transparentPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiFactory.title(Strings.get("about.title")));
        header.add(UiFactory.verticalGap(4));
        header.add(UiFactory.subtitle(Strings.get("about.subtitle")));
        header.add(UiFactory.verticalGap(18));
        return header;
    }

    private JComponent buildContent() {
        JPanel wrapper = UiFactory.transparentPanel();
        wrapper.setLayout(new BorderLayout());

        JPanel content = UiFactory.transparentPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(stretch(buildHeroCard()));
        content.add(UiFactory.verticalGap(14));
        content.add(stretch(buildMargyCraftCard()));
        content.add(UiFactory.verticalGap(14));
        content.add(stretch(buildUpdatesCard()));
        content.add(UiFactory.verticalGap(12));
        content.add(stretch(buildFooter()));

        wrapper.add(UiFactory.scroll(content), BorderLayout.CENTER);
        return wrapper;
    }

    private JComponent stretch(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return component;
    }

    private JComponent buildHeroCard() {
        JPanel card = UiFactory.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        heroLogo = new LogoIcon(76);
        heroLogo.setColor(AgroTheme.gradientAverageColor());
        heroLogoLabel = new JLabel(heroLogo);
        heroLogoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(UiFactory.verticalGap(18));
        card.add(heroLogoLabel);
        card.add(UiFactory.verticalGap(10));

        JLabel name = new JLabel(Strings.get("app.name"));
        name.setFont(AgroTheme.boldFont(30));
        name.setForeground(AgroTheme.accent());
        name.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(name);
        card.add(UiFactory.verticalGap(6));

        JLabel description = UiFactory.subtitle(Strings.get("about.description"));
        description.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(description);
        card.add(UiFactory.verticalGap(12));

        card.add(chip(Strings.get("app.version", Main.VERSION)));
        card.add(UiFactory.verticalGap(16));
        card.add(centered(UiFactory.separator()));
        card.add(UiFactory.verticalGap(12));

        JLabel linksCaption = UiFactory.fieldLabel(Strings.get("about.linksCaption"));
        linksCaption.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(linksCaption);
        card.add(UiFactory.verticalGap(8));

        JPanel links = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        links.setOpaque(false);
        links.setAlignmentX(Component.CENTER_ALIGNMENT);
        links.add(linkButton("AgroV2", CREATOR_URL, Strings.get("about.creator")));
        links.add(linkButton("Telegram", TELEGRAM_URL, Strings.get("about.community")));
        links.add(linkButton("GitHub", repositoryUrl(), Strings.get("about.sourceCode")));
        card.add(links);
        card.add(UiFactory.verticalGap(18));

        return card;
    }

    private JComponent buildMargyCraftCard() {
        JPanel card = UiFactory.card();
        card.setLayout(new BorderLayout(16, 0));

        JLabel icon = new JLabel();
        ImageIcon margyIcon = loadMargyCraftIcon(44);
        if (margyIcon != null) {
            icon.setIcon(margyIcon);
        }
        icon.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        card.add(icon, BorderLayout.WEST);

        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("MargyCraft");
        title.setFont(AgroTheme.boldFont(20));
        title.setForeground(AgroTheme.accent());
        titleRow.add(title);
        titleRow.add(chip("craft.narez.xyz"));
        body.add(titleRow);
        body.add(UiFactory.verticalGap(5));

        JLabel subtitle = UiFactory.subtitle(Strings.get("about.margycraftSubtitle"));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(subtitle);
        body.add(UiFactory.verticalGap(12));
        body.add(linkRow(Strings.get("about.creatorLabel"), "@narezany", MARGYCRAFT_CREATOR_URL));

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildUpdatesCard() {
        JPanel card = UiFactory.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(Strings.get("about.updates"));
        title.setFont(AgroTheme.boldFont(16));
        title.setForeground(AgroTheme.textPrimary());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(UiFactory.verticalGap(8));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton checkButton = UiFactory.button(Strings.get("about.checkUpdates"));
        checkButton.addActionListener(e -> checkForUpdates(checkButton));
        row.add(checkButton);

        JCheckBox autoCheck = new JCheckBox(Strings.get("about.autoCheck"));
        autoCheck.setOpaque(false);
        autoCheck.setFont(AgroTheme.font(13));
        autoCheck.setSelected(settings().updateAutoCheck);
        autoCheck.addActionListener(e -> {
            settings().updateAutoCheck = autoCheck.isSelected();
            settings().save();
        });
        row.add(autoCheck);

        card.add(row);
        return card;
    }

    private JComponent buildFooter() {
        JLabel tech = UiFactory.hint(Strings.get("about.techLine"));
        tech.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel footer = UiFactory.transparentPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(tech);
        return footer;
    }

    private JComponent chip(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AgroTheme.boldFont(12));
        label.setForeground(AgroTheme.accentLight());
        label.setBackground(AgroTheme.bgElevated());
        label.setOpaque(true);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.LineBorder(AgroTheme.border(), 1, true),
                BorderFactory.createEmptyBorder(4, 14, 4, 14)));
        return label;
    }

    private JComponent centered(JComponent component) {
        JPanel wrapper = UiFactory.transparentPanel();
        wrapper.setLayout(new BorderLayout());
        wrapper.add(component, BorderLayout.CENTER);
        wrapper.setAlignmentX(Component.CENTER_ALIGNMENT);
        return wrapper;
    }

    private JButton linkButton(String text, String url, String tooltip) {
        JButton link = UiFactory.linkButton(text);
        link.setToolTipText(tooltip + " · " + url);
        link.addActionListener(e -> openUrl(url));
        return link;
    }

    private JComponent linkRow(String labelText, String linkText, String url) {
        JPanel row = UiFactory.transparentPanel();
        row.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 2));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = UiFactory.fieldLabel(labelText + ": ");
        JButton link = UiFactory.linkButton(linkText);
        link.setToolTipText(url);
        link.addActionListener(e -> openUrl(url));

        row.add(label);
        row.add(link);
        return row;
    }

    private ImageIcon loadMargyCraftIcon(int maxSize) {
        URL iconUrl = AboutPanel.class.getResource(MARGYCRAFT_ICON_RESOURCE);
        if (iconUrl == null) {
            return null;
        }
        ImageIcon source = new ImageIcon(iconUrl);
        if (source.getIconWidth() <= 0 || source.getIconHeight() <= 0) {
            return null;
        }
        double scale = Math.min((double) maxSize / source.getIconWidth(),
                (double) maxSize / source.getIconHeight());
        int width = Math.max(1, (int) Math.round(source.getIconWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getIconHeight() * scale));
        Image scaled = source.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private void checkForUpdates(JButton button) {
        button.setEnabled(false);
        statusReporter.accept(Strings.get("update.checking"));
        Settings settings = settings();
        String repository = settings.githubRepository;
        new SwingWorker<UpdateChecker.Release, Void>() {
            @Override
            protected UpdateChecker.Release doInBackground() throws Exception {
                return UpdateChecker.latest(context.downloader(), repository, Main.VERSION);
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    UpdateChecker.Release release = get();
                    if (release == null) {
                        JOptionPane.showMessageDialog(AboutPanel.this,
                                Strings.get("update.latest"), Strings.get("update.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                        statusReporter.accept(Strings.get("update.latest"));
                        return;
                    }
                    UpdateManager.Installation installation = UpdateManager.currentInstallation();
                    if (!release.canAutoUpdate() || installation == null) {
                        int manual = JOptionPane.showConfirmDialog(AboutPanel.this,
                                Strings.get("update.manual"), Strings.get("update.title"),
                                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
                        if (manual == JOptionPane.YES_OPTION) openUrl(release.pageUrl());
                        return;
                    }
                    int answer = JOptionPane.showConfirmDialog(AboutPanel.this,
                            Strings.get("update.downloadPrompt", release.version()),
                            Strings.get("update.title"), JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE);
                    if (answer != JOptionPane.YES_OPTION) {
                        statusReporter.accept(Strings.get("update.declined", release.version()));
                        return;
                    }
                    downloadUpdate(release, installation, button);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    statusReporter.accept(Strings.get("update.checkFailed",
                            cause.getMessage() == null ? cause.toString() : cause.getMessage()));
                }
            }
        }.execute();
    }

    private void downloadUpdate(UpdateChecker.Release release, UpdateManager.Installation installation,
                                JButton button) {
        button.setEnabled(false);
        statusReporter.accept(Strings.get("update.downloading", release.version()));
        new SwingWorker<UpdateManager.StagedUpdate, Void>() {
            @Override
            protected UpdateManager.StagedUpdate doInBackground() throws Exception {
                return UpdateManager.stage(context.downloader(), release, installation);
            }

            @Override
            protected void done() {
                button.setEnabled(true);
                try {
                    UpdateManager.StagedUpdate staged = get();
                    int answer = JOptionPane.showConfirmDialog(AboutPanel.this,
                            Strings.get("update.ready"), Strings.get("update.title"),
                            JOptionPane.YES_NO_OPTION);
                    if (answer == JOptionPane.YES_OPTION) {
                        settings().save();
                        UpdateManager.installAndRestart(staged, java.util.List.of());
                        System.exit(0);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
                    statusReporter.accept(Strings.get("update.failed", message));
                    JOptionPane.showMessageDialog(AboutPanel.this,
                            Strings.get("update.failed", message), Strings.get("update.title"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private String repositoryUrl() {
        String repository = settings().githubRepository;
        if (repository == null || repository.isBlank()) {
            return "https://github.com/AgroV2/AgroLauncher";
        }
        String value = repository.trim();
        if (value.startsWith("https://") || value.startsWith("http://")) {
            return value;
        }
        return "https://github.com/" + value;
    }

    private void openUrl(String url) {
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                statusReporter.accept(Strings.get("about.browseUnsupported"));
                return;
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            statusReporter.accept(Strings.get("about.openFailed", e.getMessage()));
        }
    }
}
