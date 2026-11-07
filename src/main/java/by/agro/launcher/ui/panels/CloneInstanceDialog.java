package by.agro.launcher.ui.panels;

import by.agro.launcher.core.DiskSpace;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.instances.InstanceCloner;
import by.agro.launcher.ui.components.UiFactory;
import by.agro.launcher.ui.theme.AgroTheme;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


final class CloneInstanceDialog extends JDialog {

    record Choice(String name, Set<InstanceCloner.Part> parts) { }

    private final Path gameDir;
    private final Path targetRoot;
    private final JTextField nameField = new JTextField(28);
    private final Map<InstanceCloner.Part, JCheckBox> boxes = new EnumMap<>(InstanceCloner.Part.class);
    private final JLabel estimateLabel = new JLabel(" ");
    private final JButton cloneButton = UiFactory.primaryButton(Strings.get("clone.start"));
    private Choice choice;
    private long estimateGeneration;

    CloneInstanceDialog(Window owner, String sourceTitle, Path gameDir, Path targetRoot) {
        super(owner, Strings.get("clone.title", sourceTitle), ModalityType.APPLICATION_MODAL);
        this.gameDir = gameDir;
        this.targetRoot = targetRoot;

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JLabel nameLabel = UiFactory.fieldLabel(Strings.get("clone.name"));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameField.setText(Strings.get("clone.defaultName", sourceTitle));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(nameLabel);
        form.add(UiFactory.verticalGap(4));
        form.add(nameField);
        form.add(UiFactory.verticalGap(12));
        JLabel partsLabel = UiFactory.fieldLabel(Strings.get("clone.parts"));
        partsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(partsLabel);
        for (InstanceCloner.Part part : InstanceCloner.Part.values()) {
            JCheckBox box = new JCheckBox(part.label(), true);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.setFont(AgroTheme.font(13));
            box.addActionListener(e -> updateEstimate());
            boxes.put(part, box);
            form.add(box);
        }
        form.add(UiFactory.verticalGap(10));
        estimateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        estimateLabel.setFont(AgroTheme.font(12));
        form.add(estimateLabel);
        JLabel hint = UiFactory.hint(Strings.get("clone.hint"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(UiFactory.verticalGap(6));
        form.add(hint);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancel = UiFactory.button(Strings.get("common.cancel"));
        cancel.addActionListener(e -> dispose());
        cloneButton.addActionListener(e -> {
            choice = new Choice(nameField.getText().trim(), selectedParts());
            dispose();
        });
        buttons.add(cancel);
        buttons.add(cloneButton);

        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(cloneButton);
        pack();
        setLocationRelativeTo(owner);
        updateEstimate();
    }

    private Set<InstanceCloner.Part> selectedParts() {
        Set<InstanceCloner.Part> parts = EnumSet.noneOf(InstanceCloner.Part.class);
        boxes.forEach((part, box) -> {
            if (box.isSelected()) parts.add(part);
        });
        return parts;
    }

    private void updateEstimate() {
        long generation = ++estimateGeneration;
        Set<InstanceCloner.Part> parts = selectedParts();
        estimateLabel.setForeground(AgroTheme.textSecondary());
        estimateLabel.setText(Strings.get("clone.estimating"));
        new SwingWorker<long[], Void>() {
            @Override
            protected long[] doInBackground() {
                return new long[]{InstanceCloner.estimateBytes(gameDir, parts), DiskSpace.usableBytes(targetRoot)};
            }

            @Override
            protected void done() {
                if (generation != estimateGeneration) return;
                try {
                    long[] values = get();
                    boolean enough = values[1] < 0 || values[1] - values[0] >= DiskSpace.RESERVE_BYTES;
                    estimateLabel.setText(Strings.get(enough ? "clone.estimate" : "clone.estimateLow",
                            DiskSpace.format(values[0]), DiskSpace.format(values[1])));
                    estimateLabel.setForeground(enough ? AgroTheme.textSecondary() : AgroTheme.error());
                    cloneButton.setEnabled(enough);
                } catch (Exception e) {
                    estimateLabel.setText(" ");
                }
            }
        }.execute();
    }


    Choice ask() {
        setVisible(true);
        return choice;
    }
}
