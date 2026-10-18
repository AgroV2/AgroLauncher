package by.agro.launcher.ui.panels;

import by.agro.launcher.Main;
import by.agro.launcher.core.Settings;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.net.URI;
import java.util.function.Consumer;

public final class AboutPanel extends JPanel {

    private static final String CREATOR_URL = "https://agrov2.github.io";
    private static final String TELEGRAM_URL = "https://t.me/AgroLauncher";

    private final Settings settings;
    private final Consumer<String> statusReporter;

    public AboutPanel(Settings settings, Consumer<String> statusReporter) {
        this.settings = settings;
        this.statusReporter = statusReporter;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);
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

        JPanel card = UiFactory.card();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel name = new JLabel(Strings.get("app.name"));
        name.setFont(AgroTheme.boldFont(24));
        name.setForeground(AgroTheme.accent());
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(name);
        card.add(UiFactory.verticalGap(6));

        JLabel description = UiFactory.subtitle(Strings.get("about.description"));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(description);
        card.add(UiFactory.verticalGap(18));

        JLabel version = UiFactory.fieldLabel(Strings.get("about.version") + ": " + Main.VERSION);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(version);
        card.add(UiFactory.verticalGap(18));
        card.add(UiFactory.separator());
        card.add(UiFactory.verticalGap(12));

        card.add(linkRow(Strings.get("about.creator"), "AgroV2", CREATOR_URL));
        card.add(linkRow(Strings.get("about.community"), "Telegram", TELEGRAM_URL));
        card.add(linkRow(Strings.get("about.sourceCode"), "GitHub", repositoryUrl()));

        wrapper.add(card, BorderLayout.NORTH);
        return wrapper;
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

    private String repositoryUrl() {
        String repository = settings.githubRepository;
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
