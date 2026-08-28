package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.auth.Account;
import by.agro.launcher.auth.ElyByAuth;
import by.agro.launcher.auth.OfflineAuth;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
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
    private final JTextField elyTotpField = new JTextField();
    private final JLabel elyStatusLabel = new JLabel(" ");
    private final JButton elyLoginButton;

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
        content.add(buildLoginForms(), BorderLayout.CENTER);
        return content;
    }

    private JComponent buildAccountListSection() {
        JPanel panel = UiFactory.titledCard(Strings.get("accounts.saved"));
        panel.setPreferredSize(new Dimension(330, 100));

        accountList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        accountList.setOpaque(false);
        accountList.setBackground(new java.awt.Color(0, 0, 0, 0));
        accountList.setFixedCellHeight(48);
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
        forms.add(buildElyForm());
        forms.add(UiFactory.verticalGap(0));
        return forms;
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
        elyLoginButton.addActionListener(e -> loginToElyBy());
        body.add(elyLoginButton, c);

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
        String totp = elyTotpField.getText().trim();

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
        if (!selected.isOffline()) {
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
