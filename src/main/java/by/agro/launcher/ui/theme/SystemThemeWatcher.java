

package by.agro.launcher.ui.theme;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


public final class SystemThemeWatcher {

    private final Consumer<ThemePalette> onChange;
    private final long intervalMillis;
    private ScheduledExecutorService executor;
    private ThemePalette last;

    public SystemThemeWatcher(Consumer<ThemePalette> onChange) {
        this(onChange, 2000L);
    }

    public SystemThemeWatcher(Consumer<ThemePalette> onChange, long intervalMillis) {
        this.onChange = onChange;
        this.intervalMillis = intervalMillis;
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        last = SystemPalette.read();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "system-theme-watcher");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void tick() {
        try {
            ThemePalette current = SystemPalette.read();
            if (last == null || !last.equals(current)) {
                last = current;
                SwingUtilities.invokeLater(() -> onChange.accept(current));
            }
        } catch (RuntimeException ignored) {

        }
    }
}

