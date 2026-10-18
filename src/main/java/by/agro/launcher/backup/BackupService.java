package by.agro.launcher.backup;

import by.agro.launcher.core.HashUtil;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.SecureFiles;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;


public final class BackupService {
    private static final List<String> PERSONAL = List.of(
            "saves", "config", "options.txt", "servers.dat", "resourcepacks", "shaderpacks",
            ".agrolauncher-managed-mods.json", ".agrolauncher-installation-manifest.json");
    private final Path backupsRoot;
    private final int retention;

    public BackupService(Path backupsRoot) { this(backupsRoot, 5); }
    public BackupService(Path backupsRoot, int retention) {
        this.backupsRoot = backupsRoot.toAbsolutePath().normalize();
        this.retention = Math.max(1, retention);
    }

    public Path create(Path gameRoot) throws IOException {
        Path sourceRoot = safeRoot(gameRoot);
        Files.createDirectories(backupsRoot);
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC)
                .format(Instant.now());
        BackupManifest manifest = new BackupManifest();
        manifest.backupId = stamp + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path backup = backupsRoot.resolve(manifest.backupId);
        Path data = backup.resolve("data");
        Files.createDirectories(data);
        for (String relative : PERSONAL) {
            Path source = resolve(sourceRoot, relative);
            if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) continue;
            copyTree(sourceRoot, source, data, manifest);
        }
        try (var files = Files.list(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                String name = source.getFileName().toString();
                if ((name.startsWith("options") && name.endsWith(".txt"))
                        || name.startsWith("servers.dat")) {
                    copyTree(sourceRoot, source, data, manifest);
                }
            }
        }
        Json.write(backup.resolve("backup.json"), manifest);
        prune();
        return backup;
    }

    public void restore(Path backup, Path gameRoot) throws IOException {
        Path normalizedBackup = backup.toAbsolutePath().normalize();
        if (!normalizedBackup.startsWith(backupsRoot) || Files.isSymbolicLink(normalizedBackup)) {
            throw new IOException("Unsafe backup path: " + backup);
        }
        BackupManifest manifest = Json.read(normalizedBackup.resolve("backup.json"), BackupManifest.class);
        if (manifest == null || manifest.entries == null) throw new IOException("Invalid backup manifest");
        Path root = safeRoot(gameRoot);
        Path stage = backupsRoot.resolve(".restore-" + UUID.randomUUID());
        Files.createDirectories(stage);
        try {
            for (BackupManifest.Entry entry : manifest.entries) {
                Path source = resolve(normalizedBackup.resolve("data"), entry.path);
                if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)
                        || Files.size(source) != entry.size
                        || !entry.sha256.equalsIgnoreCase(HashUtil.digest(source, "SHA-256"))) {
                    throw new IOException("Backup verification failed: " + entry.path);
                }
                Path staged = resolve(stage, entry.path);
                Files.createDirectories(staged.getParent());
                Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
            }
            for (BackupManifest.Entry entry : manifest.entries) {
                Path staged = resolve(stage, entry.path);
                Path target = resolve(root, entry.path);
                Files.createDirectories(target.getParent());
                SecureFiles.atomicReplace(staged, target);
            }
        } finally { deleteTree(stage); }
    }

    private void copyTree(Path root, Path source, Path data, BackupManifest manifest) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(resolve(data, root.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || !attrs.isRegularFile() || Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE;
                String relative = root.relativize(file).toString().replace('\\', '/');
                Path target = resolve(data, relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                manifest.entries.add(new BackupManifest.Entry(relative, HashUtil.digest(target, "SHA-256"), Files.size(target)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path safeRoot(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        SecureFiles.rejectSymlinkParents(normalized);
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) SecureFiles.rejectSymbolicLink(normalized, "Backup root");
        Files.createDirectories(normalized);
        return normalized;
    }

    private Path resolve(Path root, String relativeText) throws IOException {
        Path relative;
        try { relative = Path.of(relativeText); } catch (RuntimeException e) { throw new IOException("Invalid backup path", e); }
        Path normalized = root.toAbsolutePath().normalize();
        Path result = normalized.resolve(relative).normalize();
        if (relative.isAbsolute() || result.equals(normalized) || !result.startsWith(normalized)) throw new IOException("Backup path escapes root: " + relativeText);
        SecureFiles.rejectSymlinkParents(result);
        return result;
    }

    private void prune() throws IOException {
        try (var stream = Files.list(backupsRoot)) {
            List<Path> backups = stream.filter(Files::isDirectory).filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted(Comparator.comparing(Path::getFileName).reversed()).toList();
            for (int i = retention; i < backups.size(); i++) deleteTree(backups.get(i));
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.deleteIfExists(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { if (exc != null) throw exc; Files.deleteIfExists(dir); return FileVisitResult.CONTINUE; }
        });
    }
}
