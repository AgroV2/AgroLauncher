package by.agro.launcher.diagnostics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class LaunchSession implements AutoCloseable {
    public static final int MAX_LINES = 5000;

    private final DiagnosticReport report = new DiagnosticReport();
    private final Deque<String> lines = new ArrayDeque<>();
    private final LogAnalyzer analyzer;
    private final Path logFile;
    private final java.io.Writer logWriter;

    public LaunchSession(Path logsDir, String minecraftVersion, String loader, Integer javaMajor,
                         String javaSource, int ramMb, List<String> command) throws IOException {
        this(logsDir, minecraftVersion, loader, javaMajor, javaSource, ramMb, command, null);
    }

    public LaunchSession(Path logsDir, String minecraftVersion, String loader, Integer javaMajor,
                         String javaSource, int ramMb, List<String> command, Path gameDir) throws IOException {
        analyzer = new LogAnalyzer(ModMetadataDiscovery.discover(gameDir));
        Files.createDirectories(logsDir);
        report.sessionId = UUID.randomUUID().toString();
        report.startedAt = Instant.now();
        report.minecraftVersion = minecraftVersion;
        report.loader = loader;
        report.javaMajor = javaMajor;
        report.javaSource = Redactor.redact(javaSource);
        report.ramMb = ramMb;
        report.command = Redactor.renderCommand(command);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC)
                .format(report.startedAt);
        logFile = logsDir.resolve("launch-" + stamp + "-" + report.sessionId.substring(0, 8) + ".log");
        Files.writeString(logFile, "Command: " + report.command + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        logWriter = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        by.agro.launcher.core.SecureFiles.setOwnerOnly(logFile);
    }

    public synchronized void accept(String rawLine) {
        String line = Redactor.redact(rawLine);
        analyzer.accept(line);
        if (lines.size() == MAX_LINES) lines.removeFirst();
        lines.addLast(line);
        try {
            logWriter.write(line);
            logWriter.write(System.lineSeparator());
            logWriter.flush();
        } catch (IOException ignored) {
        }
    }

    public synchronized void finish(int exitCode) {
        report.exitCode = exitCode;
        report.finishedAt = Instant.now();
        updateAnalysis();
        report.log = new ArrayList<>(lines);
        try {
            logWriter.write("Exit code: " + exitCode);
            logWriter.write(System.lineSeparator());
            logWriter.flush();
        } catch (IOException ignored) {
        }
    }

    public synchronized DiagnosticReport report() {
        updateAnalysis();
        report.log = new ArrayList<>(lines);
        return report;
    }

    private void updateAnalysis() {
        report.findings = analyzer.findings();
        report.implicatedModDetails = analyzer.implicatedMods();
        report.implicatedMods = report.implicatedModDetails.stream()
                .map(ModIdentity::displayName).distinct().toList();
    }

    public Path logFile() { return logFile; }

    @Override
    public synchronized void close() {
        try {
            logWriter.close();
        } catch (IOException ignored) {
        }
    }
}
