package by.agro.launcher.ui.panels;

import by.agro.launcher.LauncherContext;
import by.agro.launcher.modrinth.IconCache;
import by.agro.launcher.modrinth.ModInstaller;
import by.agro.launcher.modrinth.ModrinthClient;
import by.agro.launcher.modrinth.ModrinthProject;
import by.agro.launcher.ui.components.ModCard;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ModBrowserPanel extends JPanel {

    private static final int PAGE_SIZE = 20;
    private static final int SEARCH_DELAY_MS = 400;
    private static final int COLUMNS = 2;

    private final LauncherContext context;
    private final ModrinthClient client;
    private final IconCache iconCache;
    private final ModInstaller installer;
    private final Consumer<String> statusReporter;
    private final Runnable onModsChanged;

    private final JTextField searchField = new JTextField();
    private final JComboBox<String> categoryCombo = new JComboBox<>();
    private final JComboBox<ModrinthClient.Sort> sortCombo =
            new JComboBox<>(ModrinthClient.Sort.values());
    private final JLabel filterInfoLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final JPanel grid = new JPanel();
    private final JScrollPane scrollPane;
    private final JButton loadMoreButton = UiFactory.button(Strings.get("browser.loadMore"));

    private final Timer searchDebounce;
    private final Set<String> shownProjectIds = new HashSet<>();
    private final List<ModCard> cards = new ArrayList<>();

    private int currentOffset;
    private int totalHits;
    private boolean loading;

    public ModBrowserPanel(LauncherContext context, Consumer<String> statusReporter,
                           Runnable onModsChanged) {
        this.context = context;
        this.client = new ModrinthClient(context.downloader());
        this.iconCache = new IconCache(context.paths());
        this.installer = new ModInstaller(context.paths(), context.downloader(), client);
        this.statusReporter = statusReporter;
        this.onModsChanged = onModsChanged;

        setOpaque(false);
        setLayout(new BorderLayout(0, 14));

        searchDebounce = new Timer(SEARCH_DELAY_MS, e -> reload());
        searchDebounce.setRepeats(false);

        grid.setOpaque(false);
        grid.setLayout(new GridLayout(0, COLUMNS, 14, 14));

        JPanel gridWrapper = new JPanel(new BorderLayout());
        gridWrapper.setOpaque(false);
        gridWrapper.add(grid, BorderLayout.NORTH);

        scrollPane = UiFactory.scroll(gridWrapper);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> maybeLoadMore());

        add(buildFilters(), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        loadCategories();
        reload();
    }

    private JComponent buildFilters() {
        JPanel container = UiFactory.transparentPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        searchField.putClientProperty("JTextField.placeholderText",
                Strings.get("browser.searchPlaceholder"));
        UiFactory.fixHeight(searchField, 34);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);

        categoryCombo.addItem(Strings.get("browser.allCategories"));
        UiFactory.fixHeight(categoryCombo, 34);
        categoryCombo.setPreferredSize(new Dimension(168, 34));
        categoryCombo.addActionListener(e -> reload());

        UiFactory.fixHeight(sortCombo, 34);
        sortCombo.setPreferredSize(new Dimension(158, 34));
        sortCombo.setSelectedItem(ModrinthClient.Sort.fromValue(context.settings().modSearchSort));
        sortCombo.addActionListener(e -> {
            ModrinthClient.Sort sort = (ModrinthClient.Sort) sortCombo.getSelectedItem();
            if (sort != null) {
                context.settings().modSearchSort = sort.apiValue();
                context.settings().save();
            }
            reload();
        });

        controls.add(categoryCombo);
        controls.add(sortCombo);

        row.add(searchField, BorderLayout.CENTER);
        row.add(controls, BorderLayout.EAST);

        filterInfoLabel.setFont(AgroTheme.font(11));
        filterInfoLabel.setForeground(AgroTheme.textMuted());
        filterInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        container.add(row);
        container.add(UiFactory.verticalGap(8));
        container.add(filterInfoLabel);
        container.add(UiFactory.verticalGap(6));
        return container;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(10, 0));
        footer.setOpaque(false);

        statusLabel.setFont(AgroTheme.font(12));
        statusLabel.setForeground(AgroTheme.textSecondary());

        loadMoreButton.addActionListener(e -> loadNextPage());
        loadMoreButton.setVisible(false);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(loadMoreButton);

        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);
        footer.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        return footer;
    }

    private void loadCategories() {
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return client.categories();
            }

            @Override
            protected void done() {
                try {
                    List<String> categories = get();
                    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
                    model.addElement(Strings.get("browser.allCategories"));
                    for (String category : categories) {
                        model.addElement(category);
                    }
                    categoryCombo.setModel(model);
                } catch (Exception e) {
                }
            }
        }.execute();
    }

    public void reload() {
        currentOffset = 0;
        totalHits = 0;
        shownProjectIds.clear();
        cards.clear();
        grid.removeAll();
        grid.revalidate();
        grid.repaint();
        updateFilterInfo();
        loadNextPage();
    }

    private void loadNextPage() {
        if (loading) {
            return;
        }
        loading = true;
        loadMoreButton.setEnabled(false);
        statusLabel.setText(currentOffset == 0 ? Strings.get("browser.loadingCatalog") : Strings.get("common.loading"));

        String query = searchField.getText().trim();
        String loader = resolveLoaderFilter();
        String gameVersion = resolveVersionFilter();
        String category = resolveCategoryFilter();
        ModrinthClient.Sort sort = (ModrinthClient.Sort) sortCombo.getSelectedItem();
        int offset = currentOffset;

        new SwingWorker<ModrinthClient.SearchResult, Void>() {
            @Override
            protected ModrinthClient.SearchResult doInBackground() throws Exception {
                return client.search(query, loader, gameVersion, category, sort, offset, PAGE_SIZE);
            }

            @Override
            protected void done() {
                loading = false;
                loadMoreButton.setEnabled(true);
                try {
                    ModrinthClient.SearchResult result = get();
                    totalHits = result.totalHits;
                    appendResults(result.projects);
                    currentOffset = offset + result.projects.size();

                    boolean hasMore = currentOffset < totalHits;
                    loadMoreButton.setVisible(hasMore);

                    if (cards.isEmpty()) {
                        statusLabel.setText(Strings.get("browser.nothingFound"));
                    } else {
                        statusLabel.setText(Strings.get("browser.shown", cards.size(), totalHits));
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String message = cause.getMessage() != null ? cause.getMessage() : "ошибка сети";
                    statusLabel.setText(Strings.get("browser.loadFailed", message));
                    report(Strings.get("browser.unavailable", message));
                }
            }
        }.execute();
    }

    private void appendResults(List<ModrinthProject> projects) {
        for (ModrinthProject project : projects) {
            if (project.id == null || !shownProjectIds.add(project.id)) {
                continue;
            }
            ModCard card = new ModCard(project, iconCache, this::openModDialog);
            card.setInstalled(false);
            cards.add(card);
            grid.add(card);
        }
        grid.revalidate();
        grid.repaint();
    }

    private void maybeLoadMore() {
        if (loading || currentOffset >= totalHits || cards.isEmpty()) {
            return;
        }
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        int position = bar.getValue() + bar.getVisibleAmount();
        if (position >= bar.getMaximum() - 300) {
            loadNextPage();
        }
    }

    private void openModDialog(ModrinthProject project) {
        ModDetailsDialog dialog = new ModDetailsDialog(
                SwingUtilities.getWindowAncestor(this),
                project,
                client,
                installer,
                context,
                resolveLoaderFilter(),
                resolveVersionFilter(),
                message -> {
                    report(message);
                    if (onModsChanged != null) {
                        onModsChanged.run();
                    }
                });
        dialog.setVisible(true);
    }

    private String resolveLoaderFilter() {
        String loader = context.settings().selectedLoader;
        if (loader == null || loader.isBlank() || "vanilla".equalsIgnoreCase(loader)
                || "optifine".equalsIgnoreCase(loader)) {
            return null;
        }
        return loader.toLowerCase();
    }

    private String resolveVersionFilter() {
        String version = context.settings().selectedVersion;
        return (version == null || version.isBlank()) ? null : version;
    }

    private String resolveCategoryFilter() {
        Object selected = categoryCombo.getSelectedItem();
        if (selected == null || Strings.get("browser.allCategories").equals(selected)) {
            return null;
        }
        return selected.toString();
    }

    private void updateFilterInfo() {
        String loader = resolveLoaderFilter();
        String version = resolveVersionFilter();

        StringBuilder sb = new StringBuilder(Strings.get("browser.filterByProfile"));
        if (loader == null && version == null) {
            sb.append(Strings.get("browser.filterNotSet"));
        } else {
            if (loader != null) {
                sb.append(loader);
            } else {
                sb.append(Strings.get("browser.anyLoader"));
            }
            sb.append(" · ");
            sb.append(version != null ? version : Strings.get("browser.anyVersion"));
        }
        filterInfoLabel.setText(sb.toString());
    }

    public void refreshFilters() {
        updateFilterInfo();
    }

    private void report(String message) {
        if (statusReporter != null) {
            statusReporter.accept(message);
        }
    }

    public void shutdown() {
        iconCache.shutdown();
    }
}
