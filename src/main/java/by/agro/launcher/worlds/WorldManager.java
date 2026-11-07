package by.agro.launcher.worlds;

import by.agro.launcher.core.DiskSpace;
import by.agro.launcher.core.FileTrees;
import by.agro.launcher.i18n.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


public final class WorldManager {

    static final int MAX_ARCHIVE_ENTRIES = 200_000;
    static final long MAX_ARCHIVE_BYTES = 32L * 1024 * 1024 * 1024;
    private static final String LOCK_FILE = "session.lock";

    private final Path savesDir;

    public WorldManager(Path savesDir) {
        this.savesDir = savesDir.toAbsolutePath().normalize();
    }

    public Path savesDir() {
        return savesDir;
    }

    public record WorldInfo(Path path, String folderName, String levelName, long lastPlayedMillis, long sizeBytes) {
        public String displayName() {
            return levelName == null || levelName.isBlank() ? folderName : levelName;
        }
    }

    public static boolean isWorld(Path path) {
        return path != null && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                && Files.isRegularFile(path.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS);
    }


    public List<WorldInfo> list() {
        List<WorldInfo> result = new ArrayList<>();
        if (!Files.isDirectory(savesDir, LinkOption.NOFOLLOW_LINKS)) return result;
        try (var stream = Files.list(savesDir)) {
            for (Path world : stream.filter(WorldManager::isWorld).toList()) {
                result.add(info(world));
            }
        } catch (IOException ignored) {
        }
        result.sort(Comparator.comparingLong(WorldInfo::lastPlayedMillis).reversed());
        return result;
    }

    public WorldInfo info(Path world) {
        long modified = 0;
        try {
            modified = Files.getLastModifiedTime(world.resolve("level.dat")).toMillis();
        } catch (IOException ignored) {
        }
        return new WorldInfo(world, world.getFileName().toString(), LevelDat.levelName(world.resolve("level.dat")),
                modified, DiskSpace.directorySize(world));
    }

    private Path requireWorld(Path world) throws IOException {
        Path normalized = world.toAbsolutePath().normalize();
        if (!savesDir.equals(normalized.getParent()) || !isWorld(normalized)) {
            throw new IOException(Strings.get("worlds.notAWorld", world.getFileName()));
        }
        return normalized;
    }


    public Path duplicate(Path world) throws IOException {
        Path source = requireWorld(world);
        DiskSpace.require(savesDir, DiskSpace.directorySize(source));
        String name = FileTrees.safeName(Strings.get("worlds.copyName", source.getFileName().toString()), "World");
        Path target = FileTrees.uniqueChild(savesDir, name);
        Path staging = savesDir.resolve(".agro-copy-" + UUID.randomUUID());
        try {
            FileTrees.copy(source, staging, relative -> !LOCK_FILE.equals(relative.toString()));
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            FileTrees.deleteQuietly(staging);
            throw e;
        }
        return target;
    }


    public void exportZip(Path world, Path zipFile) throws IOException {
        Path source = requireWorld(world);
        Path target = zipFile.toAbsolutePath().normalize();
        if (target.startsWith(source)) throw new IOException(Strings.get("worlds.exportInside"));
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        DiskSpace.require(target, DiskSpace.directorySize(source));
        Path temporary = (parent == null ? Path.of(".") : parent)
                .resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        String root = source.getFileName().toString();
        try (OutputStream output = Files.newOutputStream(temporary);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink()) return FileVisitResult.SKIP_SUBTREE;
                    String relative = source.relativize(dir).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(root + "/" + (relative.isEmpty() ? "" : relative + "/")));
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                    String relative = source.relativize(file).toString().replace('\\', '/');
                    if (LOCK_FILE.equals(relative)) return FileVisitResult.CONTINUE;
                    ZipEntry entry = new ZipEntry(root + "/" + relative);
                    entry.setLastModifiedTime(attrs.lastModifiedTime());
                    zip.putNextEntry(entry);
                    Files.copy(file, zip);
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    public Path importWorld(Path source) throws IOException {
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) return importFolder(source);
        return importZip(source);
    }

    Path importFolder(Path folder) throws IOException {
        Path source = folder.toAbsolutePath().normalize();
        if (!isWorld(source)) throw new IOException(Strings.get("worlds.noLevelDat"));
        if (savesDir.startsWith(source) || source.startsWith(savesDir)) {
            throw new IOException(Strings.get("worlds.alreadyInSaves"));
        }
        Files.createDirectories(savesDir);
        DiskSpace.require(savesDir, DiskSpace.directorySize(source));
        Path target = FileTrees.uniqueChild(savesDir, FileTrees.safeName(source.getFileName().toString(), "World"));
        Path staging = savesDir.resolve(".agro-import-" + UUID.randomUUID());
        try {
            FileTrees.copy(source, staging, relative -> !LOCK_FILE.equals(relative.toString()));
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            FileTrees.deleteQuietly(staging);
            throw e;
        }
        return target;
    }

    Path importZip(Path zipPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            String prefix = null;
            int bestDepth = Integer.MAX_VALUE;
            long declared = 0;
            int count = 0;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ARCHIVE_ENTRIES) throw new IOException(Strings.get("worlds.archiveTooLarge"));
                String name = safeEntryName(entry.getName());
                if (entry.getSize() > 0) declared += entry.getSize();
                if (entry.isDirectory()) continue;
                if (name.equals("level.dat") || name.endsWith("/level.dat")) {
                    int depth = name.split("/").length;
                    if (depth < bestDepth) {
                        bestDepth = depth;
                        prefix = name.substring(0, name.length() - "level.dat".length());
                    }
                }
            }
            if (prefix == null) throw new IOException(Strings.get("worlds.noLevelDat"));
            if (declared > MAX_ARCHIVE_BYTES) throw new IOException(Strings.get("worlds.archiveTooLarge"));
            Files.createDirectories(savesDir);
            DiskSpace.require(savesDir, declared);

            String folderName = prefix.isEmpty()
                    ? stripExtension(zipPath.getFileName().toString())
                    : prefix.substring(0, prefix.length() - 1).substring(prefix.substring(0, prefix.length() - 1)
                    .lastIndexOf('/') + 1);
            Path target = FileTrees.uniqueChild(savesDir, FileTrees.safeName(folderName, "World"));
            Path staging = savesDir.resolve(".agro-import-" + UUID.randomUUID());
            long written = 0;
            try {
                Files.createDirectories(staging);
                for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                    ZipEntry entry = entries.nextElement();
                    String name = safeEntryName(entry.getName());
                    if (!name.startsWith(prefix) || name.length() == prefix.length()) continue;
                    String relative = name.substring(prefix.length());
                    if (relative.equals(LOCK_FILE)) continue;
                    Path destination = staging.resolve(relative).normalize();
                    if (!destination.startsWith(staging)) throw new IOException(Strings.get("worlds.unsafeArchive"));
                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                        continue;
                    }
                    Files.createDirectories(destination.getParent());
                    try (InputStream input = zip.getInputStream(entry);
                         OutputStream output = Files.newOutputStream(destination)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) >= 0) {
                            written += read;
                            if (written > MAX_ARCHIVE_BYTES) throw new IOException(Strings.get("worlds.archiveTooLarge"));
                            output.write(buffer, 0, read);
                        }
                    }
                    FileTime time = entry.getLastModifiedTime();
                    if (time != null) Files.setLastModifiedTime(destination, time);
                }
                if (!Files.isRegularFile(staging.resolve("level.dat"))) {
                    throw new IOException(Strings.get("worlds.noLevelDat"));
                }
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | RuntimeException e) {
                FileTrees.deleteQuietly(staging);
                throw e;
            }
            return target;
        } catch (java.util.zip.ZipException e) {
            throw new IOException(Strings.get("worlds.badArchive", e.getMessage()), e);
        }
    }


    static String safeEntryName(String raw) throws IOException {
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) name = name.substring(2);
        if (name.startsWith("/") || name.matches("^[A-Za-z]:.*") || name.indexOf('\0') >= 0) {
            throw new IOException(Strings.get("worlds.unsafeArchive"));
        }
        for (String part : name.split("/")) {
            if ("..".equals(part)) throw new IOException(Strings.get("worlds.unsafeArchive"));
        }
        return name;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static String suggestedArchiveName(Path world) {
        return FileTrees.safeName(world.getFileName().toString(), "world") + ".zip";
    }

    public void delete(Path world) throws IOException {
        FileTrees.delete(requireWorld(world));
    }
}
