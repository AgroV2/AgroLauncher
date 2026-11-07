package by.agro.launcher.ui.panels;

import by.agro.launcher.i18n.Strings;
import by.agro.launcher.stats.PlayStatsStore;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;


final class PlayStatsPanel extends JPanel {

    enum Period {
        WEEK(7, "stats.period.week"),
        MONTH(30, "stats.period.month"),
        QUARTER(90, "stats.period.quarter"),
        ALL(0, "stats.period.all");

        final int days;
        final String key;

        Period(int days, String key) {
            this.days = days;
            this.key = key;
        }

        @Override
        public String toString() {
            return Strings.get(key);
        }
    }

    private final PlayStatsStore store;
    private final Function<String, String> labelResolver;
    private final JComboBox<Period> periodBox = new JComboBox<>(Period.values());
    private final JLabel totalLabel = new JLabel(" ");
    private final DailyChart chart = new DailyChart();
    private final DefaultListModel<PlayStatsStore.InstanceTotal> instancesModel = new DefaultListModel<>();
    private final JList<PlayStatsStore.InstanceTotal> instancesList = new JList<>(instancesModel);
    private long totalForShare = 1;
    private long generation;

    PlayStatsPanel(PlayStatsStore store, Function<String, String> labelResolver) {
        this.store = store;
        this.labelResolver = labelResolver;
        setOpaque(false);
        setLayout(new BorderLayout(0, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        top.setOpaque(false);
        JLabel periodLabel = UiFactory.fieldLabel(Strings.get("stats.period"));
        periodBox.setSelectedItem(Period.MONTH);
        periodBox.addActionListener(e -> refresh());
        totalLabel.setFont(AgroTheme.boldFont(13));
        top.add(periodLabel);
        top.add(periodBox);
        top.add(UiFactory.horizontalGap(12));
        top.add(totalLabel);

        chart.setPreferredSize(new Dimension(100, 170));
        JPanel chartCard = UiFactory.transparentPanel();
        chartCard.setLayout(new BorderLayout(0, 4));
        JLabel chartTitle = UiFactory.fieldLabel(Strings.get("stats.byDay"));
        chartCard.add(chartTitle, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        instancesList.setOpaque(false);
        instancesList.setFixedCellHeight(46);
        instancesList.setCellRenderer(new InstanceRenderer());
        JPanel listCard = UiFactory.transparentPanel();
        listCard.setLayout(new BorderLayout(0, 4));
        listCard.add(UiFactory.fieldLabel(Strings.get("stats.byInstance")), BorderLayout.NORTH);
        listCard.add(UiFactory.scroll(instancesList), BorderLayout.CENTER);

        JPanel center = UiFactory.transparentPanel();
        center.setLayout(new BorderLayout(0, 12));
        center.add(chartCard, BorderLayout.NORTH);
        center.add(listCard, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(UiFactory.hint(Strings.get("stats.hint")), BorderLayout.SOUTH);
    }

    void refresh() {
        if (store == null) return;
        long current = ++generation;
        Period period = (Period) periodBox.getSelectedItem();
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() {
                PlayStatsStore.Data data = store.load();
                LocalDate today = LocalDate.now();
                int chartDays = period == Period.ALL ? 90 : period.days;
                LocalDate from = period == Period.ALL ? null : today.minusDays(period.days - 1L);
                return new Object[]{PlayStatsStore.byDay(data, today, chartDays),
                        PlayStatsStore.byInstance(data, from), PlayStatsStore.total(data, from)};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void done() {
                if (current != generation) return;
                try {
                    Object[] result = get();
                    List<PlayStatsStore.DayTotal> days = (List<PlayStatsStore.DayTotal>) result[0];
                    List<PlayStatsStore.InstanceTotal> instances = (List<PlayStatsStore.InstanceTotal>) result[1];
                    long total = (Long) result[2];
                    totalForShare = Math.max(1, total);
                    int sessions = instances.stream().mapToInt(PlayStatsStore.InstanceTotal::sessions).sum();
                    long activeDays = days.stream().filter(day -> day.ms() > 0).count();
                    totalLabel.setText(total <= 0 ? Strings.get("stats.empty")
                            : Strings.get("stats.total", formatDuration(total), sessions, activeDays));
                    chart.setDays(days);
                    instancesModel.clear();
                    for (PlayStatsStore.InstanceTotal instance : instances) instancesModel.addElement(instance);
                } catch (Exception e) {
                    totalLabel.setText(Strings.get("stats.empty"));
                }
            }
        }.execute();
    }

    static String formatDuration(long ms) {
        long minutes = Math.max(0, ms) / 60000L;
        if (minutes < 60) return Strings.get("stats.minutes", minutes);
        return Strings.get("instances.playtime", minutes / 60, minutes % 60);
    }

    private final class InstanceRenderer extends JPanel implements ListCellRenderer<PlayStatsStore.InstanceTotal> {
        private final JLabel title = new JLabel();
        private final JLabel detail = new JLabel();

        InstanceRenderer() {
            setOpaque(false);
            setLayout(new GridLayout(2, 1));
            add(title);
            add(detail);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PlayStatsStore.InstanceTotal> list,
                                                      PlayStatsStore.InstanceTotal value, int index,
                                                      boolean selected, boolean focused) {
            title.setText(labelResolver == null ? value.key() : labelResolver.apply(value.key()));
            title.setFont(AgroTheme.boldFont(13));
            title.setForeground(selected ? AgroTheme.accent() : AgroTheme.textPrimary());
            int share = (int) Math.round(value.ms() * 100.0 / totalForShare);
            String last = value.lastPlayed() == null ? "" : DateTimeFormatter.ofPattern("d MMM yyyy",
                    Strings.language().locale()).format(value.lastPlayed());
            detail.setText(Strings.get("stats.instanceDetail", formatDuration(value.ms()), share,
                    value.sessions(), last));
            detail.setFont(AgroTheme.font(11));
            detail.setForeground(AgroTheme.textMuted());
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            return this;
        }
    }


    static final class DailyChart extends JComponent {
        private List<PlayStatsStore.DayTotal> days = List.of();

        DailyChart() {
            setToolTipText("");
        }

        void setDays(List<PlayStatsStore.DayTotal> days) {
            this.days = days == null ? List.of() : List.copyOf(days);
            repaint();
        }

        private int barIndexAt(int x) {
            if (days.isEmpty()) return -1;
            double width = (getWidth() - 8) / (double) days.size();
            int index = (int) ((x - 4) / width);
            return index >= 0 && index < days.size() ? index : -1;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = barIndexAt(event.getX());
            if (index < 0) return null;
            PlayStatsStore.DayTotal day = days.get(index);
            return Strings.get("stats.dayTooltip", DateTimeFormatter.ofPattern("EEE, d MMM",
                    Strings.language().locale()).format(day.day()), formatDuration(day.ms()), day.sessions());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int labelHeight = 16;
                int height = getHeight() - labelHeight - 4;
                g.setColor(AgroTheme.border());
                g.drawLine(4, height, getWidth() - 4, height);
                if (days.isEmpty()) return;
                long max = days.stream().mapToLong(PlayStatsStore.DayTotal::ms).max().orElse(0);
                double width = (getWidth() - 8) / (double) days.size();
                int labelEvery = Math.max(1, (int) Math.ceil(38 / Math.max(1, width)));
                g.setFont(AgroTheme.font(10));
                DateTimeFormatter label = DateTimeFormatter.ofPattern("d.MM", Strings.language().locale());
                Color accent = AgroTheme.accent();
                for (int i = 0; i < days.size(); i++) {
                    PlayStatsStore.DayTotal day = days.get(i);
                    int x = (int) Math.round(4 + i * width);
                    int barWidth = Math.max(1, (int) Math.floor(width) - 2);
                    if (max > 0 && day.ms() > 0) {
                        int barHeight = Math.max(2, (int) Math.round((height - 6) * (day.ms() / (double) max)));
                        g.setColor(accent);
                        g.fillRoundRect(x + 1, height - barHeight, barWidth, barHeight, 4, 4);
                    }
                    if (i % labelEvery == 0) {
                        g.setColor(AgroTheme.textMuted());
                        g.drawString(label.format(day.day()), x, getHeight() - 4);
                    }
                }
            } finally {
                g.dispose();
            }
        }
    }
}
