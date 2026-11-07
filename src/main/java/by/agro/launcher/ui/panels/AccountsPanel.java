package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.auth.Account;
import by.agro.launcher.auth.ElyByAuth;
import by.agro.launcher.auth.ElyByOAuth;
import by.agro.launcher.auth.MicrosoftAuth;
import by.agro.launcher.auth.OfflineAuth;
import by.agro.launcher.ui.components.AccountHeadIcons;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

public final class AccountsPanel extends JPanel {

    private final LauncherContext context;
    private final Consumer<String> statusReporter;

    private final DefaultListModel<Account> listModel = new DefaultListModel<>();
    private final JList<Account> accountList = new JList<>(listModel);

    private final JTextField offlineNickField = new JTextField();
    private final JTextField elyLoginField = new JTextField();
    private final JPasswordField elyPasswordField = new JPasswordField();
    private final JPasswordField elyTotpField = new JPasswordField();
    private final JLabel elyStatusLabel = new JLabel(" ");
    private final JButton elyLoginButton;
    private final JButton elyOAuthButton = UiFactory.button(Strings.get("accounts.oauth"));
    private final JButton microsoftButton = UiFactory.primaryButton(Strings.get("microsoft.signIn"));
    private final JLabel microsoftStatus = new JLabel(" ");

    public AccountsPanel(LauncherContext context, Consumer<String> statusReporter) {
        this.context = context;
        this.statusReporter = statusReporter;
        this.elyLoginButton = UiFactory.primaryButton(Strings.get("accounts.signIn"));

        setOpaque(false);
        setLayout(new BorderLayout(20, 0));
        setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);

        refreshAccounts();
    }

    private JComponent buildHeader() {
        JPanel header = UiFactory.transparentPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(UiFactory.title(Strings.get("accounts.title")));
        header.add(UiFactory.verticalGap(4));
        header.add(UiFactory.subtitle(Strings.get("accounts.subtitle")));
        header.add(UiFactory.verticalGap(20));
        return header;
    }

    private JComponent buildContent() {
        JPanel content = UiFactory.transparentPanel();
        content.setLayout(new BorderLayout(22, 0));

        content.add(buildAccountListSection(), BorderLayout.WEST);
        content.add(UiFactory.scroll(buildLoginForms()), BorderLayout.CENTER);
        return content;
    }

    private JComponent buildAccountListSection() {
        JPanel panel = UiFactory.titledCard(Strings.get("accounts.saved"));
        panel.setPreferredSize(new Dimension(330, 100));

        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setOpaque(false);
        accountList.setBackground(new java.awt.Color(0, 0, 0, 0));
        accountList.setFixedCellHeight(-1);
        accountList.setCellRenderer(new AccountCellRenderer());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);

        JButton selectButton = UiFactory.button(Strings.get("accounts.makeActive"));
        selectButton.addActionListener(e -> activateSelected());

        JButton removeButton = UiFactory.dangerButton(Strings.get("common.delete"));
        removeButton.addActionListener(e -> removeSelected());

        actions.add(selectButton);
        actions.add(removeButton);

        panel.add(UiFactory.scroll(accountList), BorderLayout.CENTER);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildLoginForms() {
        JPanel forms = UiFactory.transparentPanel();
        forms.setLayout(new BoxLayout(forms, BoxLayout.Y_AXIS));

        forms.add(buildOfflineForm());
        forms.add(UiFactory.verticalGap(18));
        forms.add(buildMicrosoftForm());
        forms.add(UiFactory.verticalGap(18));
        forms.add(buildElyForm());
        forms.add(UiFactory.verticalGap(0));
        return forms;
    }

    private JComponent buildMicrosoftForm() {
        JPanel card = UiFactory.titledCard(Strings.get("accounts.microsoft"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel body = UiFactory.transparentPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(UiFactory.hint(Strings.get("microsoft.hint")));
        body.add(UiFactory.verticalGap(8));
        microsoftButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        microsoftButton.addActionListener(e -> loginMicrosoft());
        body.add(microsoftButton);
        body.add(UiFactory.verticalGap(4));
        microsoftStatus.setFont(AgroTheme.font(12));
        body.add(microsoftStatus);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void loginMicrosoft() {
        if (context.settings().offlineMode) {
            microsoftStatus.setText(Strings.get("microsoft.offline"));
            microsoftStatus.setForeground(AgroTheme.warning());
            return;
        }
        microsoftButton.setEnabled(false);
        microsoftStatus.setText(Strings.get("accounts.oauthWaiting"));
        microsoftStatus.setForeground(AgroTheme.textSecondary());
        new SwingWorker<Account, Void>() {
            @Override protected Account doInBackground() throws Exception { return MicrosoftAuth.authorize(AccountsPanel.this::showMicrosoftBrowserFallback); }
            @Override protected void done() {
                microsoftButton.setEnabled(true);
                try {
                    Account account = get();
                    context.accounts().save(account);
                    context.settings().activeAccountId = account.id;
                    context.settings().save();
                    refreshAccounts();
                    microsoftStatus.setText(Strings.get("accounts.signedIn", account.username));
                    microsoftStatus.setForeground(AgroTheme.accentLight());
                    report(Strings.get("accounts.signedIn", account.username));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage() != null ? cause.getMessage() : Strings.get("common.error");
                    microsoftStatus.setText(Strings.get("common.error"));
                    microsoftStatus.setForeground(AgroTheme.error());
                    JOptionPane.showMessageDialog(AccountsPanel.this, message, Strings.get("common.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private boolean showMicrosoftBrowserFallback(java.net.URI uri) throws java.io.IOException {
        final boolean[] proceed = {false};
        try {
            SwingUtilities.invokeAndWait(() -> {
                JPanel content = new JPanel();
                content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
                content.add(new JLabel(Strings.get("microsoft.browserManualHint")));
                content.add(Box.createVerticalStrut(8));
                JTextField link = new JTextField(uri.toString(), 42);
                link.setEditable(false);
                content.add(link);
                link.selectAll();
                content.add(new JLabel(Strings.get("microsoft.browserManualWarning")));
                proceed[0] = JOptionPane.showConfirmDialog(AccountsPanel.this, content,
                        Strings.get("microsoft.browserManualTitle"), JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.INFORMATION_MESSAGE) == JOptionPane.OK_OPTION;
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException(Strings.get("microsoft.interrupted"), e);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new java.io.IOException(Strings.get("microsoft.browserUnavailable"), e);
        }
        return proceed[0];
    }

    private JComponent buildOfflineForm() {
        JPanel card = UiFactory.titledCard(Strings.get("accounts.offlineProfile"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 4, 0);
        body.add(UiFactory.fieldLabel(Strings.get("accounts.nickname")), c);

        c.gridy = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        offlineNickField.putClientProperty("JTextField.placeholderText", Strings.get("accounts.nicknamePlaceholder"));
        UiFactory.fixHeight(offlineNickField, 34);
        body.add(offlineNickField, c);

        c.gridy = 2;
        c.insets = new Insets(6, 0, 12, 0);
        body.add(UiFactory.hint(Strings.get("accounts.nicknameHint")), c);

        c.gridy = 3;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        JButton addButton = UiFactory.primaryButton(Strings.get("accounts.addProfile"));
        addButton.addActionListener(e -> addOfflineAccount());
        body.add(addButton, c);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildElyForm() {
        JPanel card = UiFactory.titledCard(Strings.get("accounts.elyLogin"));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 4, 0);
        body.add(UiFactory.fieldLabel(Strings.get("accounts.emailOrName")), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        elyLoginField.putClientProperty("JTextField.placeholderText", "user@example.com");
        UiFactory.fixHeight(elyLoginField, 34);
        body.add(elyLoginField, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 4, 0);
        body.add(UiFactory.fieldLabel(Strings.get("accounts.password")), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        UiFactory.fixHeight(elyPasswordField, 34);
        body.add(elyPasswordField, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 4, 0);
        body.add(UiFactory.fieldLabel(Strings.get("accounts.totp")), c);

        c.gridy++;
        c.insets = new Insets(0, 0, 12, 0);
        elyTotpField.putClientProperty("JTextField.placeholderText", Strings.get("accounts.totpPlaceholder"));
        UiFactory.fixHeight(elyTotpField, 34);
        body.add(elyTotpField, c);

        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 0);
        JPanel loginActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        loginActions.setOpaque(false);
        elyLoginButton.addActionListener(e -> loginToElyBy());
        elyOAuthButton.addActionListener(e -> loginToElyByOAuth());
        loginActions.add(elyLoginButton);
        loginActions.add(elyOAuthButton);
        body.add(loginActions, c);

        c.gridy++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        elyStatusLabel.setFont(AgroTheme.font(12));
        body.add(elyStatusLabel, c);

        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void addOfflineAccount() {
        String nick = offlineNickField.getText().trim();
        if (!OfflineAuth.isValidUsername(nick)) {
            JOptionPane.showMessageDialog(this,
                    Strings.get("accounts.invalidNickname"),
                    Strings.get("accounts.invalidNicknameTitle"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        Account account = Account.offline(nick);
        context.accounts().save(account);
        context.settings().activeAccountId = account.id;
        context.settings().save();
        offlineNickField.setText("");
        refreshAccounts();
        report(Strings.get("accounts.added", nick));
    }

    private void loginToElyBy() {
        String login = elyLoginField.getText().trim();
        String password = new String(elyPasswordField.getPassword());
        char[] totpChars = elyTotpField.getPassword();
        String totp = new String(totpChars).trim();
        java.util.Arrays.fill(totpChars, '\0');

        if (login.isEmpty() || password.isEmpty()) {
            setElyStatus(Strings.get("accounts.fillCredentials"), AgroTheme.warning());
            return;
        }

        elyLoginButton.setEnabled(false);
        setElyStatus(Strings.get("accounts.checking"), AgroTheme.textSecondary());

        new SwingWorker<Account, Void>() {
            @Override
            protected Account doInBackground() throws Exception {
                return ElyByAuth.authenticate(login, password, totp.isEmpty() ? null : totp);
            }

            @Override
            protected void done() {
                elyLoginButton.setEnabled(true);
                try {
                    Account account = get();
                    context.accounts().save(account);
                    context.settings().activeAccountId = account.id;
                    context.settings().save();
                    elyPasswordField.setText("");
                    elyTotpField.setText("");
                    refreshAccounts();
                    setElyStatus(Strings.get("accounts.signedIn", account.username), AgroTheme.accentLight());
                    report(Strings.get("accounts.signedIn", account.username));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage() != null ? cause.getMessage() : Strings.get("common.error");
                    setElyStatus(message, AgroTheme.error());
                    if (cause instanceof ElyByAuth.TwoFactorRequiredException) {
                        elyTotpField.requestFocusInWindow();
                    }
                }
            }
        }.execute();
    }

    private void loginToElyByOAuth() {
        var settings = context.settings();
        ElyByOAuth.Config config = new ElyByOAuth.Config(
                settings.elyOAuthAuthorizationEndpoint, settings.elyOAuthTokenEndpoint,
                settings.elyOAuthProfileEndpoint, settings.elyOAuthClientId,
                settings.elyOAuthScope, settings.elyOAuthCallbackPort, settings.elyOAuthTimeoutSeconds);
        if (!config.complete()) {
            setElyStatus(Strings.get("accounts.oauthNotConfigured"), AgroTheme.warning());
            return;
        }
        elyOAuthButton.setEnabled(false);
        setElyStatus(Strings.get("accounts.oauthWaiting"), AgroTheme.textSecondary());
        new SwingWorker<Account, Void>() {
            @Override
            protected Account doInBackground() throws Exception {
                return ElyByOAuth.authorize(config);
            }

            @Override
            protected void done() {
                elyOAuthButton.setEnabled(true);
                try {
                    Account account = get();
                    context.accounts().save(account);
                    context.settings().activeAccountId = account.id;
                    context.settings().save();
                    refreshAccounts();
                    setElyStatus(Strings.get("accounts.signedIn", account.username), AgroTheme.accentLight());
                    report(Strings.get("accounts.signedIn", account.username));
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    setElyStatus(cause.getMessage() != null ? cause.getMessage() : Strings.get("common.error"),
                            AgroTheme.error());
                }
            }
        }.execute();
    }

    private void activateSelected() {
        Account selected = accountList.getSelectedValue();
        if (selected == null) {
            return;
        }
        context.settings().activeAccountId = selected.id;
        context.settings().save();
        refreshAccounts();
        report(Strings.get("accounts.activeAccount", selected.username));
    }

    private void removeSelected() {
        Account selected = accountList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                Strings.get("accounts.deleteConfirm", selected.username),
                Strings.get("common.confirm"), JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        if (selected.type == Account.Type.ELY_BY || selected.type == Account.Type.ELY_BY_OAUTH) {
            ElyByAuth.invalidate(selected);
        }
        context.accounts().remove(selected);
        if (selected.id.equals(context.settings().activeAccountId)) {
            context.settings().activeAccountId = "";
            context.settings().save();
        }
        refreshAccounts();
        report(Strings.get("accounts.deleted"));
    }

    public void refreshAccounts() {
        SwingUtilities.invokeLater(() -> {
            listModel.clear();
            for (Account account : context.accounts().accounts()) {
                listModel.addElement(account);
            }
            String activeId = context.settings().activeAccountId;
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).id != null && listModel.get(i).id.equals(activeId)) {
                    accountList.setSelectedIndex(i);
                    break;
                }
            }
            accountList.repaint();
        });
    }

    private void setElyStatus(String text, java.awt.Color color) {
        elyStatusLabel.setText(text);
        elyStatusLabel.setForeground(color);
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }

    private final class AccountCellRenderer extends JPanel implements javax.swing.ListCellRenderer<Account> {

        private final JLabel nameLabel = new JLabel();
        private final JLabel typeLabel = new JLabel();
        private final JLabel activeMark = new JLabel();

        AccountCellRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

            nameLabel.setFont(AgroTheme.boldFont(13));
            typeLabel.setFont(AgroTheme.font(11));
            activeMark.setFont(AgroTheme.boldFont(11));
            activeMark.setForeground(AgroTheme.accent());

            JPanel texts = new JPanel();
            texts.setOpaque(false);
            texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
            texts.add(nameLabel);
            texts.add(typeLabel);

            add(texts, BorderLayout.CENTER);
            add(activeMark, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Account> list, Account value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            nameLabel.setText(value.username);
            typeLabel.setText(value.type.displayName());
            javax.swing.Icon icon = AccountHeadIcons.cached(value, context.settings());
            nameLabel.setIcon(icon);
            nameLabel.setIconTextGap(icon == null ? 0 : 10);
            nameLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            nameLabel.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
            if (icon == null) {
                AccountHeadIcons.load(value, context.settings(), loaded -> {
                    if (loaded != null) {
                        accountList.revalidate();
                        accountList.repaint();
                    }
                });
            }

            boolean active = value.id != null && value.id.equals(context.settings().activeAccountId);
            activeMark.setText(active ? Strings.get("accounts.active") : "");

            if (isSelected) {
                setOpaque(true);
                setBackground(AgroTheme.accentDeep());
                nameLabel.setForeground(AgroTheme.textPrimary());
                typeLabel.setForeground(AgroTheme.accentLight());
            } else {
                setOpaque(false);
                setBackground(new java.awt.Color(0, 0, 0, 0));
                nameLabel.setForeground(AgroTheme.textPrimary());
                typeLabel.setForeground(AgroTheme.textMuted());
            }
            return this;
        }
    }
}
