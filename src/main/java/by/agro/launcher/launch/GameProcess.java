package by.agro.launcher.launch;

import by.agro.launcher.diagnostics.DiagnosticReport;
import by.agro.launcher.diagnostics.LaunchSession;
import by.agro.launcher.i18n.Strings;

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
    private final LaunchSession session;

    private GameProcess(Process process, Thread outputThread, LaunchSession session) {
        this.process = process;
        this.outputThread = outputThread;
        this.session = session;
    }

    public static GameProcess start(List<String> command, Path workingDir,
                                    Consumer<String> onLine, IntConsumer onExit) throws IOException {
        return start(command, workingDir, onLine, onExit, null);
    }

    public static GameProcess start(List<String> command, Path workingDir,
                                    Consumer<String> onLine, IntConsumer onExit,
                                    LaunchSession session) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();

        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (session != null) {
                        session.accept(line);
                    }
                    if (onLine != null) {
                        onLine.accept(by.agro.launcher.diagnostics.Redactor.redact(line));
                    }
                }
            } catch (IOException e) {
                if (onLine != null) {
                    onLine.accept(Strings.get("console.launcherPrefix") + " "
                            + Strings.get("console.outputClosed", e.getMessage()));
                }
            } finally {
                try {
                    int code = process.waitFor();
                    if (session != null) {
                        session.finish(code);
                    }
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

        return new GameProcess(process, reader, session);
    }

    public DiagnosticReport diagnosticReport() {
        return session == null ? null : session.report();
    }

    public Path sessionLogFile() {
        return session == null ? null : session.logFile();
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
