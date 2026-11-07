package by.agro.launcher.ui.panels;

import by.agro.launcher.ui.theme.AgroTheme;
import javax.swing.*;
import java.awt.*;
import java.util.function.Function;


final class InstalledModCellRenderer<T> extends JPanel implements ListCellRenderer<T> {
    record Row(String name, String details, String state, boolean enabled, boolean info) { }
    private final Function<T, Row> row;
    private final JLabel nameLabel = new JLabel();
    private final JLabel detailsLabel = new JLabel();
    private final JLabel stateLabel = new JLabel();

    InstalledModCellRenderer(Function<T, Row> row) {
        this.row = row;
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        nameLabel.setFont(AgroTheme.font(13));
        detailsLabel.setFont(AgroTheme.font(11));
        stateLabel.setFont(AgroTheme.boldFont(10));
        JPanel texts = new JPanel();
        texts.setOpaque(false);
        texts.setLayout(new BoxLayout(texts, BoxLayout.Y_AXIS));
        texts.add(nameLabel);
        texts.add(detailsLabel);
        add(texts, BorderLayout.CENTER);
        add(stateLabel, BorderLayout.EAST);
    }

    @Override public Component getListCellRendererComponent(JList<? extends T> list, T value,
                                                            int index, boolean selected, boolean focus) {
        Row r = row.apply(value);
        nameLabel.setText(r.name());
        detailsLabel.setText(r.details());
        stateLabel.setText(r.state());
        stateLabel.setForeground(r.info() ? AgroTheme.info() : r.enabled() ? AgroTheme.accent() : AgroTheme.textMuted());
        setOpaque(selected);
        setBackground(selected ? AgroTheme.accentDeep() : new Color(0, 0, 0, 0));
        nameLabel.setForeground(selected || r.enabled() ? AgroTheme.textPrimary() : AgroTheme.textMuted());
        detailsLabel.setForeground(selected ? AgroTheme.accentLight() : AgroTheme.textMuted());
        return this;
    }
}
