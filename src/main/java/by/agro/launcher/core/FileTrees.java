package by.agro.launcher.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.function.Predicate;


public final class FileTrees {

    private FileTrees() {
    }






    public static int copy(Path source, Path target, Predicate<Path> include) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) return 0;
        if (Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES,
                    LinkOption.NOFOLLOW_LINKS);
            return 1;
        }
        int[] copied = {0};
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink()) return FileVisitResult.SKIP_SUBTREE;
                Path relative = source.relativize(dir);
                if (!relative.toString().isEmpty() && include != null && !include.test(relative)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(relative.toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
                Path relative = source.relativize(file);
                if (include != null && !include.test(relative)) return FileVisitResult.CONTINUE;
                Files.copy(file, target.resolve(relative.toString()), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES, LinkOption.NOFOLLOW_LINKS);
                copied[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return copied[0];
    }


    public static void delete(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            Files.deleteIfExists(root);
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }


    public static void deleteQuietly(Path root) {
        try {
            delete(root);
        } catch (IOException | RuntimeException ignored) {
        }
    }


    public static String safeName(String name, String fallback) {
        String value = name == null ? "" : name.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim();
        while (value.endsWith(".") || value.endsWith(" ")) value = value.substring(0, value.length() - 1);
        while (value.startsWith(".")) value = value.substring(1);
        if (value.length() > 80) value = value.substring(0, 80).trim();
        if (value.isEmpty()) value = fallback;
        String upper = value.toUpperCase(java.util.Locale.ROOT);
        String base = upper.contains(".") ? upper.substring(0, upper.indexOf('.')) : upper;
        if (base.matches("CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]")) value = "_" + value;
        return value;
    }


    public static Path uniqueChild(Path parent, String name) {
        Path candidate = parent.resolve(name);
        int index = 2;
        while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            candidate = parent.resolve(name + " (" + index++ + ")");
        }
        return candidate;
    }
}
