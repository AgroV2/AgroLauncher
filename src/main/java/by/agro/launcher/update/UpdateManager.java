package by.agro.launcher.update;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.HashUtil;
import by.agro.launcher.core.Platform;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

public final class UpdateManager {
    private static final long MAX_ARTIFACT_BYTES = 512L * 1024 * 1024;
    private static final long MAX_CHECKSUM_BYTES = 1024L * 1024;

    private UpdateManager() { }

    public record Installation(Path target, boolean portable, String description) { }
    public record StagedUpdate(UpdateChecker.Release release, Installation installation, Path stagedFile) { }

    public static Installation currentInstallation() {
        try {
            CodeSource source = UpdateManager.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) return null;
            URI uri = source.getLocation().toURI();
            if (!"file".equalsIgnoreCase(uri.getScheme())) return null;
            Path code = Path.of(uri).toAbsolutePath().normalize();
            if (!Files.isRegularFile(code) || !code.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return null;
            }
            Path parent = code.getParent();
            if (parent != null && Files.isRegularFile(parent.resolve("AgroLauncher.exe"))) {
                return new Installation(code, true, "Windows portable launcher");
            }
            return new Installation(code, false, "executable JAR");
        } catch (Exception ignored) {
            return null;
        }
    }

    public static StagedUpdate stage(Downloader downloader, UpdateChecker.Release release,
                                     Installation installation) throws IOException {
        if (release == null || release.asset() == null) throw new IOException("No compatible update artifact");
        if (installation == null) throw new IOException("Automatic update is unavailable for this launch mode");
        Path target = installation.target().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent) || !Files.isWritable(parent)) {
            throw new IOException("Launcher directory is not writable: " + parent);
        }
        Path staged = parent.resolve(target.getFileName() + ".update");
        Files.deleteIfExists(staged);
        downloader.download(release.asset().downloadUrl(), staged, null, release.asset().size());
        if (Files.size(staged) <= 0 || Files.size(staged) > MAX_ARTIFACT_BYTES) {
            Files.deleteIfExists(staged);
            throw new IOException("Invalid update artifact size");
        }
        verifyChecksum(downloader, release, staged);
        validateJar(staged);
        return new StagedUpdate(release, installation, staged);
    }

    static void verifyChecksum(Downloader downloader, UpdateChecker.Release release, Path staged) throws IOException {
        UpdateChecker.Asset checksum = release.checksumAsset();
        if (checksum == null) return;
        String text = downloader.getString(checksum.downloadUrl());
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_CHECKSUM_BYTES) {
            throw new IOException("Checksum file is too large");
        }
        String expected = checksumFor(text, release.asset().name());
        if (expected == null) throw new IOException("Checksum for " + release.asset().name() + " was not found");
        String actual = HashUtil.digest(staged, "SHA-256");
        if (!expected.equalsIgnoreCase(actual)) {
            Files.deleteIfExists(staged);
            throw new IOException("SHA-256 checksum mismatch for update artifact");
        }
    }

    static String checksumFor(String text, String fileName) {
        if (text == null) return null;
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length == 1 && parts[0].matches("(?i)[0-9a-f]{64}")) return parts[0];
            if (parts.length == 2 && parts[0].matches("(?i)[0-9a-f]{64}")) {
                String listed = parts[1].replaceFirst("^[* ]+", "").trim();
                if (listed.equals(fileName) || Path.of(listed).getFileName().toString().equals(fileName)) return parts[0];
            }
            int equals = trimmed.indexOf('=');
            if (equals > 0) {
                String digest = trimmed.substring(equals + 1).trim();
                if (trimmed.substring(0, equals).contains(fileName) && digest.matches("(?i)[0-9a-f]{64}")) return digest;
            }
        }
        return null;
    }

    static void validateJar(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile(), true)) {
            if (file.getManifest() == null || file.getManifest().getMainAttributes().getValue("Main-Class") == null) {
                throw new IOException("Downloaded JAR has no Main-Class manifest entry");
            }
        }
    }

    public static Process installAndRestart(StagedUpdate update, List<String> applicationArgs) throws IOException {
        Path target = update.installation().target().toAbsolutePath().normalize();
        Path staged = update.stagedFile().toAbsolutePath().normalize();
        Path backup = target.resolveSibling(target.getFileName() + ".backup");
        Path script = target.resolveSibling(Platform.isWindows() ? ".agro-update.cmd" : ".agro-update.sh");
        List<String> restart = new ArrayList<>();
        restart.add(javaExecutable());
        restart.add("-jar");
        restart.add(target.toString());
        if (applicationArgs != null) restart.addAll(applicationArgs);
        Files.writeString(script, Platform.isWindows()
                ? windowsScript(target, staged, backup, restart)
                : unixScript(target, staged, backup, restart), StandardCharsets.UTF_8);
        if (!Platform.isWindows()) script.toFile().setExecutable(true, true);
        ProcessBuilder builder = Platform.isWindows()
                ? new ProcessBuilder("cmd.exe", "/d", "/c", "start", "", "/b", script.toString())
                : new ProcessBuilder("sh", script.toString());
        builder.directory(target.getParent().toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    static void replaceWithRollback(Path target, Path staged, Path backup) throws IOException {
        Files.deleteIfExists(backup);
        move(target, backup);
        try {
            move(staged, target);
            validateJar(target);
        } catch (Exception failure) {
            Files.deleteIfExists(target);
            move(backup, target);
            throw new IOException("Update replacement failed; previous launcher restored", failure);
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String javaExecutable() {
        Path bin = Path.of(System.getProperty("java.home"), "bin", Platform.isWindows() ? "javaw.exe" : "java");
        return Files.isRegularFile(bin) ? bin.toString() : (Platform.isWindows() ? "javaw.exe" : "java");
    }

    private static String windowsScript(Path target, Path staged, Path backup, List<String> restart) {
        return "@echo off\r\n"
                + "ping 127.0.0.1 -n 3 >nul\r\n"
                + "del /q " + q(backup) + " 2>nul\r\n"
                + "move /y " + q(target) + " " + q(backup) + " >nul || exit /b 1\r\n"
                + "move /y " + q(staged) + " " + q(target) + " >nul || (move /y " + q(backup) + " " + q(target) + " >nul & exit /b 1)\r\n"
                + "start \"\" " + command(restart) + " || (del /q " + q(target) + " & move /y " + q(backup) + " " + q(target) + " >nul)\r\n"
                + "del /q \"%~f0\"\r\n";
    }

    private static String unixScript(Path target, Path staged, Path backup, List<String> restart) {
        return "#!/bin/sh\nset -u\nsleep 2\nrm -f " + sh(backup) + "\n"
                + "mv " + sh(target) + " " + sh(backup) + " || exit 1\n"
                + "if ! mv " + sh(staged) + " " + sh(target) + "; then mv " + sh(backup) + " " + sh(target) + "; exit 1; fi\n"
                + commandUnix(restart) + " >/dev/null 2>&1 &\n"
                + "pid=$!\nsleep 2\nif ! kill -0 $pid 2>/dev/null; then rm -f " + sh(target) + "; mv " + sh(backup) + " " + sh(target) + "; exit 1; fi\n"
                + "rm -f -- \"$0\"\n";
    }

    private static String command(List<String> values) { return values.stream().map(UpdateManager::q).reduce((a, b) -> a + " " + b).orElse(""); }
    private static String commandUnix(List<String> values) { return values.stream().map(UpdateManager::sh).reduce((a, b) -> a + " " + b).orElse(""); }
    private static String q(Path value) { return q(value.toString()); }
    private static String q(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
    private static String sh(Path value) { return sh(value.toString()); }
    private static String sh(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
