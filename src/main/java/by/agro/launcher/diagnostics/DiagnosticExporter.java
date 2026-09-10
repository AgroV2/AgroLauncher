package by.agro.launcher.diagnostics;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.SecureFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class DiagnosticExporter {
    private DiagnosticExporter() {
    }

    public static void exportTxt(DiagnosticReport report, Path destination) throws IOException {
        Path target = safeTarget(destination, ".txt");
        Path temp = SecureFiles.createSiblingTemp(target, ".tmp");
        try {
            StringBuilder out = new StringBuilder();
            out.append("AgroLauncher diagnostic report\n")
                    .append("Session: ").append(report.sessionId).append('\n')
                    .append("Started: ").append(report.startedAt).append('\n')
                    .append("Finished: ").append(report.finishedAt).append('\n')
                    .append("Minecraft: ").append(Redactor.redact(report.minecraftVersion)).append('\n')
                    .append("Loader: ").append(Redactor.redact(report.loader)).append('\n')
                    .append("Java major: ").append(report.javaMajor).append('\n')
                    .append("Java source: ").append(Redactor.redact(report.javaSource)).append('\n')
                    .append("RAM MB: ").append(report.ramMb).append('\n')
                    .append("Exit code: ").append(report.exitCode).append('\n')
                    .append("Command: ").append(Redactor.redact(report.command)).append("\n\nFindings:\n");
            for (DiagnosticFinding finding : report.findings) out.append("- ").append(finding).append('\n');
            out.append("\nSanitized log:\n");
            for (String line : report.log) out.append(Redactor.redact(line)).append('\n');
            Files.writeString(temp, out.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            SecureFiles.setOwnerOnly(temp);
            SecureFiles.atomicReplace(temp, target);
            SecureFiles.setOwnerOnly(target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void exportJson(DiagnosticReport report, Path destination) throws IOException {
        Path target = safeTarget(destination, ".json");
        DiagnosticReport safe = sanitizedCopy(report);
        Json.write(target, safe);
        SecureFiles.setOwnerOnly(target);
    }

    private static DiagnosticReport sanitizedCopy(DiagnosticReport source) {
        DiagnosticReport safe = new DiagnosticReport();
        safe.sessionId = source.sessionId;
        safe.startedAt = source.startedAt;
        safe.finishedAt = source.finishedAt;
        safe.minecraftVersion = Redactor.redact(source.minecraftVersion);
        safe.loader = Redactor.redact(source.loader);
        safe.javaMajor = source.javaMajor;
        safe.javaSource = Redactor.redact(source.javaSource);
        safe.ramMb = source.ramMb;
        safe.exitCode = source.exitCode;
        safe.command = Redactor.redact(source.command);
        safe.findings.addAll(source.findings);
        for (String line : source.log) safe.log.add(Redactor.redact(line));
        return safe;
    }

    private static Path safeTarget(Path destination, String extension) throws IOException {
        if (destination == null) throw new IOException("Export destination is not selected");
        Path target = destination.toAbsolutePath().normalize();
        if (!target.getFileName().toString().toLowerCase().endsWith(extension)) {
            target = target.resolveSibling(target.getFileName() + extension);
        }
        SecureFiles.rejectSymlinkParents(target);
        if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            SecureFiles.rejectSymbolicLink(target, "Diagnostic report");
            if (!Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Diagnostic report destination is not a regular file");
            }
        }
        return target;
    }
}
