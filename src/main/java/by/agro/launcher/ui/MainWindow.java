package by.agro.launcher.ui;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.ui.background.BackgroundManager;
import by.agro.launcher.ui.components.BackgroundPanel;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.components.LogoIcon;
import by.agro.launcher.ui.components.NavIcon;
import by.agro.launcher.ui.components.SideNavButton;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.panels.AccountsPanel;
import by.agro.launcher.ui.panels.ModsPanel;
import by.agro.launcher.ui.panels.PlayPanel;
import by.agro.launcher.ui.panels.SettingsPanel;
import by.agro.launcher.ui.panels.VersionsPanel;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;


public final class MainWindow extends JFrame {

    private static final String CARD_PLAY = "play";
    private static final String CARD_VERSIONS = "versions";
    private static final String CARD_ACCOUNTS = "accounts";
    private static final String CARD_MODS = "mods";
    private static final String CARD_SETTINGS = "settings";


    private static final int MAX_STATUS_LENGTH = 110;

    private final LauncherContext context;

    private final CardLayout cardLayout = new CardLayout();
    private final BackgroundManager backgroundManager = new BackgroundManager();
    private final JPanel cards;
    private final JLabel statusLabel = new JLabel(Strings.get("app.ready"));
    private final JLabel logoLabel = new JLabel("AGRO");
    private final JLabel logoSubLabel = new JLabel("LAUNCHER");
    private final LogoIcon logoIcon = new LogoIcon(30);
    private JLabel logoIconLabel;
    private JPanel sidebar;
    private JPanel statusBar;
    private JLabel offlineWarningLabel;
    private String currentCard;
    private JLabel versionLabel;
    private final List<SideNavButton> navButtons = new ArrayList<>();

    private PlayPanel playPanel;
    private VersionsPanel versionsPanel;
    private AccountsPanel accountsPanel;
    private ModsPanel modsPanel;
    private SettingsPanel settingsPanel;

    public MainWindow(LauncherContext context) {
        this.context = context;

        backgroundManager.load(context.settings());
        this.cards = new BackgroundPanel(cardLayout, backgroundManager, context.settings());

        setTitle(Strings.get("app.name"));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 700));
        setSize(new Dimension(1180, 780));
        setLocationRelativeTo(null);

        getContentPane().setBackground(AgroTheme.bgBase());
        setLayout(new BorderLayout());

        buildPanels();
        add(buildSidebar(), BorderLayout.WEST);
        add(cards, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        applyWindowIcons();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleClose();
            }
        });

        by.agro.launcher.ui.components.UiFactory.setCardOpacityPercent(
                backgroundManager.hasBackground() ? context.settings().panelOpacityPercent : 100);

        AgroTheme.addThemeListener(palette -> SwingUtilities.invokeLater(() -> {
            applyThemeColors();
            repaint();
        }));


        Strings.addLanguageListener(() -> SwingUtilities.invokeLater(() -> {
            setTitle(Strings.get("app.name"));
            refreshNavTexts();
            if (offlineWarningLabel != null) {
                offlineWarningLabel.setText(Strings.get("app.offlineOnly"));
            }
            if (versionLabel != null) {
                versionLabel.setText(Strings.get("app.version", "1.0.0"));
            }
            statusLabel.setText(Strings.get("app.ready"));
            rebuildPanels();
        }));

        showCard(CARD_PLAY);
    }

    private void buildPanels() {
        playPanel = new PlayPanel(context, this::setStatus);
        versionsPanel = new VersionsPanel(context, this::setStatus, () -> playPanel.refresh());
        accountsPanel = new AccountsPanel(context, message -> {
            setStatus(message);
            playPanel.refresh();
        });
        modsPanel = new ModsPanel(context, this::setStatus);
        settingsPanel = new SettingsPanel(context, backgroundManager,
                message -> {
                    setStatus(message);
                    playPanel.refresh();
                },
                this::onAppearanceChanged);

        cards.setBackground(AgroTheme.bgBase());
        cards.add(playPanel, CARD_PLAY);
        cards.add(versionsPanel, CARD_VERSIONS);
        cards.add(accountsPanel, CARD_ACCOUNTS);
        cards.add(modsPanel, CARD_MODS);
        cards.add(settingsPanel, CARD_SETTINGS);
    }

    private JComponent buildSidebar() {
        sidebar = new JPanel();
        sidebar.setBackground(AgroTheme.bgDeep());
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(212, 100));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, AgroTheme.border()));

        sidebar.add(buildLogo());
        sidebar.add(UiFactory.verticalGap(16));

        sidebar.add(navButton("nav.play", NavIcon.Kind.PLAY, CARD_PLAY));
        sidebar.add(navButton("nav.versions", NavIcon.Kind.VERSIONS, CARD_VERSIONS));
        sidebar.add(navButton("nav.accounts", NavIcon.Kind.ACCOUNTS, CARD_ACCOUNTS));
        sidebar.add(navButton("nav.mods", NavIcon.Kind.MODS, CARD_MODS));
        sidebar.add(navButton("nav.settings", NavIcon.Kind.SETTINGS, CARD_SETTINGS));

        sidebar.add(javax.swing.Box.createVerticalGlue());
        sidebar.add(buildSidebarFooter());
        return sidebar;
    }

    private JComponent buildLogo() {
        JPanel logo = new JPanel(new BorderLayout(11, 0));
        logo.setOpaque(false);
        logo.setBorder(BorderFactory.createEmptyBorder(24, 20, 8, 16));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        logo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));

        logoIconLabel = new JLabel(logoIcon);
        logoIconLabel.setVerticalAlignment(JLabel.CENTER);

        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));

        JLabel name = logoLabel;
        name.setFont(AgroTheme.boldFont(19));
        name.setForeground(AgroTheme.accent());
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = logoSubLabel;
        subtitle.setFont(AgroTheme.font(11));
        subtitle.setForeground(AgroTheme.textMuted());
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        texts.add(name);
        texts.add(subtitle);

        logo.add(logoIconLabel, BorderLayout.WEST);
        logo.add(texts, BorderLayout.CENTER);
        return logo;
    }

    private JComponent buildSidebarFooter() {
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 20, 18, 20));

        JLabel version = new JLabel(Strings.get("app.version", "1.0.0"));
        versionLabel = version;
        version.setFont(AgroTheme.font(10));
        version.setForeground(AgroTheme.textMuted());
        version.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel platform = new JLabel(System.getProperty("os.name", "")
                + " · " + by.agro.launcher.core.Platform.arch());
        platform.setFont(AgroTheme.font(10));
        platform.setForeground(AgroTheme.textMuted());
        platform.setAlignmentX(Component.LEFT_ALIGNMENT);

        footer.add(version);
        footer.add(platform);
        return footer;
    }

    private SideNavButton navButton(String textKey, NavIcon.Kind iconKind, String card) {
        SideNavButton button = new SideNavButton(Strings.get(textKey), iconKind);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> showCard(card));
        button.putClientProperty("card", card);
        button.putClientProperty("textKey", textKey);
        navButtons.add(button);
        return button;
    }

    private void refreshNavTexts() {
        for (SideNavButton button : navButtons) {
            Object key = button.getClientProperty("textKey");
            if (key instanceof String textKey) {
                button.setText(Strings.get(textKey));
            }
        }
    }

    private JComponent buildStatusBar() {
        statusBar = new JPanel(new BorderLayout());
        JPanel bar = statusBar;
        bar.setBackground(AgroTheme.bgDeep());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AgroTheme.border()),
                BorderFactory.createEmptyBorder(7, 18, 7, 18)));

        statusLabel.setFont(AgroTheme.font(11));
        statusLabel.setForeground(AgroTheme.textSecondary());
        bar.add(statusLabel, BorderLayout.WEST);

        JLabel warning = new JLabel(Strings.get("app.offlineOnly"));
        offlineWarningLabel = warning;
        warning.setFont(AgroTheme.font(11));
        warning.setForeground(AgroTheme.textMuted());
        bar.add(warning, BorderLayout.EAST);

        return bar;
    }

    private void showCard(String card) {
        currentCard = card;
        cardLayout.show(cards, card);
        for (SideNavButton button : navButtons) {
            button.setActive(card.equals(button.getClientProperty("card")));
        }
        switch (card) {
            case CARD_PLAY:
                playPanel.refresh();
                break;
            case CARD_MODS:
                modsPanel.refresh();
                break;
            case CARD_SETTINGS:
                settingsPanel.reload();
                break;
            case CARD_ACCOUNTS:
                accountsPanel.refreshAccounts();
                break;
            default:
                break;
        }
    }


    private void rebuildPanels() {
        String activeCard = currentCard;

        int settingsTab = settingsPanel != null ? settingsPanel.selectedTab() : 0;

        modsPanel.shutdown();
        cards.removeAll();
        buildPanels();
        showCard(activeCard != null ? activeCard : CARD_PLAY);
        if (settingsTab > 0) {
            settingsPanel.selectTab(settingsTab);
        }
        applyThemeColors();
        revalidate();
        repaint();
    }


    private void onAppearanceChanged() {
        SwingUtilities.invokeLater(() -> {
            backgroundManager.load(context.settings());

            by.agro.launcher.ui.components.UiFactory.setCardOpacityPercent(
                    backgroundManager.hasBackground()
                            ? context.settings().panelOpacityPercent
                            : 100);
            applyThemeColors();
            SwingUtilities.updateComponentTreeUI(this);
            repaint();
        });
    }


    private void applyWindowIcons() {
        java.util.List<java.awt.image.BufferedImage> icons =
                LogoIcon.windowIcons(AgroTheme.accent());
        try {
            setIconImages(icons);
        } catch (RuntimeException e) {

        }
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {

                    taskbar.setIconImage(icons.get(icons.size() - 1));
                }
            }
        } catch (RuntimeException e) {

        }
    }


    private void applyThemeColors() {
        sidebar.setBackground(AgroTheme.bgDeep());
        statusBar.setBackground(AgroTheme.bgDeep());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AgroTheme.border()),
                BorderFactory.createEmptyBorder(7, 18, 7, 18)));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, AgroTheme.border()));
        logoLabel.setForeground(AgroTheme.accent());
        logoSubLabel.setForeground(AgroTheme.textMuted());

        logoIcon.setColor(AgroTheme.accent());
        if (logoIconLabel != null) {
            logoIconLabel.repaint();
        }
        applyWindowIcons();
        for (SideNavButton button : navButtons) {
            button.refreshTheme();
        }
        statusLabel.setForeground(AgroTheme.textSecondary());
        cards.setBackground(AgroTheme.bgBase());

        sidebar.setOpaque(!backgroundManager.hasBackground());
        statusBar.setOpaque(!backgroundManager.hasBackground());
    }


    public void setStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            String text = message == null ? "" : message.replaceAll("\\s+", " ").trim();
            statusLabel.setToolTipText(text.isEmpty() ? null : text);
            statusLabel.setText(text.length() > MAX_STATUS_LENGTH
                    ? text.substring(0, MAX_STATUS_LENGTH - 1) + "…"
                    : text);
        });
    }

    private void handleClose() {
        if (playPanel.isGameRunning()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    Strings.get("play.exitConfirm"),
                    Strings.get("play.exitTitle"), JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        context.settings().save();
        modsPanel.shutdown();
        dispose();
        System.exit(0);
    }
}
