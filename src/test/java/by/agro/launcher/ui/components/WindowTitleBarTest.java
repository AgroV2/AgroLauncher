package by.agro.launcher.ui.components;

import by.agro.launcher.i18n.Language;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.theme.AgroTheme;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class WindowTitleBarTest {
    @Test
    void controlsMaximizeRestoreMinimizeAndRequestNormalClose() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            Language original = Strings.language();
            TestFrame frame = new TestFrame();
            try {
                AgroTheme.install();
                Strings.setLanguage(Language.RUSSIAN);
                frame.setUndecorated(true);
                frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                frame.setBounds(100, 100, 800, 600);
                WindowTitleBar bar = new WindowTitleBar(frame);
                frame.add(bar);
                WindowGestures.installResize(frame);
                AtomicInteger closes = new AtomicInteger();
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) { closes.incrementAndGet(); }
                });
                frame.setVisible(true);
                JButton minimize = find(bar, "window.minimize");
                JButton maximize = find(bar, "window.maximize");
                JButton close = find(bar, "window.close");
                assertEquals("Свернуть", minimize.getToolTipText());
                assertEquals("Развернуть", maximize.getAccessibleContext().getAccessibleName());
                Rectangle originalBounds = frame.getBounds();
                maximize.doClick();
                if (!Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
                    assertTrue(WindowGestures.isMaximized(frame));
                    assertEquals("Восстановить размер", maximize.getToolTipText());
                    assertNotEquals(originalBounds, frame.getBounds());
                    maximize.doClick();
                    assertFalse(WindowGestures.isMaximized(frame));
                    assertEquals(originalBounds, frame.getBounds());
                } else {
                    assertTrue((frame.requestedState & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH);
                }
                minimize.doClick();
                assertTrue((frame.requestedState & Frame.ICONIFIED) != 0);
                close.doClick();
                assertEquals(1, closes.get());
                assertTrue(frame.isDisplayable());
                Strings.setLanguage(Language.ENGLISH);
                bar.updateLabels();
                assertEquals("Close window", close.getToolTipText());
            } finally {
                frame.dispose();
                Strings.setLanguage(original);
            }
        });
    }

    private static JButton find(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && name.equals(button.getName())) return button;
            if (child instanceof Container nested) {
                JButton button = findOrNull(nested, name);
                if (button != null) return button;
            }
        }
        throw new AssertionError("Missing control: " + name);
    }

    private static JButton findOrNull(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton button && name.equals(button.getName())) return button;
            if (child instanceof Container nested) {
                JButton button = findOrNull(nested, name);
                if (button != null) return button;
            }
        }
        return null;
    }

    private static final class TestFrame extends JFrame {
        private int requestedState;
        @Override
        public void setExtendedState(int state) {
            requestedState = state;
            super.setExtendedState(state);
        }
    }
}
