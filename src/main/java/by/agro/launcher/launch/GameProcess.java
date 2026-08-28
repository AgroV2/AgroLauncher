package by.agro.launcher.launch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class GameProcess {

    private final Process process;
    private final Thread outputThread;

    private GameProcess(Process process, Thread outputThread) {
        this.process = process;
        this.outputThread = outputThread;
    }

    public static GameProcess start(List<String> command, Path workingDir,
                                    Consumer<String> onLine, IntConsumer onExit) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();

        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (onLine != null) {
                        onLine.accept(line);
                    }
                }
            } catch (IOException e) {
                if (onLine != null) {
                    onLine.accept("[лаунчер] Поток вывода закрыт: " + e.getMessage());
                }
            } finally {
                try {
                    int code = process.waitFor();
                    if (onExit != null) {
                        onExit.accept(code);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "emerald-game-output");
        reader.setDaemon(true);
        reader.start();

        return new GameProcess(process, reader);
    }

    public boolean isRunning() {
        return process.isAlive();
    }

    public void terminate() {
        process.destroy();
    }

    public void kill() {
        process.destroyForcibly();
    }

    public long pid() {
        try {
            return process.pid();
        } catch (UnsupportedOperationException e) {
            return -1;
        }
    }

    public Process raw() {
        return process;
    }

    public Thread outputThread() {
        return outputThread;
    }
}
