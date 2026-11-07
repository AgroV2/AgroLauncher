package by.agro.launcher.ui.panels;

import by.agro.launcher.i18n.Strings;
import by.agro.launcher.mods.ModCompatibilityChecker;
import by.agro.launcher.ui.components.UiFactory;

import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.awt.Dimension;


final class CompatibilityDialog {

    private CompatibilityDialog() {
    }

    static String render(ModCompatibilityChecker.Report report) {
        StringBuilder text = new StringBuilder(report.summary());
        for (ModCompatibilityChecker.Issue issue : report.issues()) {
            text.append("\n\n• ").append(issue);
        }
        return text.toString();
    }

    private static JScrollPane body(ModCompatibilityChecker.Report report) {
        JTextArea area = new JTextArea(render(report));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UiFactory.mono(12));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(620, report.issues().isEmpty() ? 80 : 320));
        return scroll;
    }

    static void show(Component parent, String title, ModCompatibilityChecker.Report report) {
        int type = report.hasErrors() ? JOptionPane.WARNING_MESSAGE
                : JOptionPane.INFORMATION_MESSAGE;
        JOptionPane.showMessageDialog(parent, body(report), Strings.get("compat.title", title), type);
    }


    static boolean confirmLaunch(Component parent, ModCompatibilityChecker.Report report) {
        Object[] options = {Strings.get("compat.launchAnyway"), Strings.get("common.cancel")};
        int answer = JOptionPane.showOptionDialog(parent, body(report), Strings.get("compat.preflightTitle"),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return answer == 0;
    }
}
