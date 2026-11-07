package by.agro.launcher.core;

import by.agro.launcher.i18n.Strings;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;


public final class DiskSpace {


    public static final long RESERVE_BYTES = 256L * 1024 * 1024;

    public static final long LOW_SPACE_BYTES = 2L * 1024 * 1024 * 1024;

    private DiskSpace() {
    }

    public static final class InsufficientSpaceException extends IOException {
        public final long requiredBytes;
        public final long availableBytes;

        public InsufficientSpaceException(Path target, long requiredBytes, long availableBytes) {
            super(Strings.get("disk.insufficient", format(requiredBytes), format(availableBytes),
                    target == null ? "" : target.toString()));
            this.requiredBytes = requiredBytes;
            this.availableBytes = availableBytes;
        }
    }


    public static Path existingAncestor(Path target) {
        if (target == null) return null;
        Path current = target.toAbsolutePath().normalize();
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        return current;
    }


    public static long usableBytes(Path target) {
        Path existing = existingAncestor(target);
        if (existing == null) return -1;
        try {
            return Files.getFileStore(existing).getUsableSpace();
        } catch (IOException | SecurityException | UnsupportedOperationException e) {
            return -1;
        }
    }

    public static boolean isLow(Path target) {
        long usable = usableBytes(target);
        return usable >= 0 && usable < LOW_SPACE_BYTES;
    }





    public static void require(Path target, long neededBytes) throws InsufficientSpaceException {
        if (neededBytes <= 0) return;
        long usable = usableBytes(target);
        if (usable < 0) return;
        if (usable - neededBytes < RESERVE_BYTES) {
            throw new InsufficientSpaceException(target, neededBytes, usable);
        }
    }


    public static void requireAll(Map<Path, Long> neededByTarget) throws InsufficientSpaceException {
        Map<Object, Long> totals = new LinkedHashMap<>();
        Map<Object, Path> sample = new LinkedHashMap<>();
        for (Map.Entry<Path, Long> entry : neededByTarget.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) continue;
            Path existing = existingAncestor(entry.getKey());
            if (existing == null) continue;
            Object key;
            try {
                FileStore store = Files.getFileStore(existing);
                key = store;
            } catch (IOException | SecurityException | UnsupportedOperationException e) {
                key = existing.getRoot() == null ? existing : existing.getRoot();
            }
            totals.merge(key, entry.getValue(), Long::sum);
            sample.putIfAbsent(key, entry.getKey());
        }
        for (Map.Entry<Object, Long> total : totals.entrySet()) {
            require(sample.get(total.getKey()), total.getValue());
        }
    }


    public static long directorySize(Path root) {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return 0;
        if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) {
            try {
                return Files.size(root);
            } catch (IOException e) {
                return 0;
            }
        }
        long[] total = {0};
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) total[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
        return total[0];
    }


    public static String format(long bytes) {
        if (bytes < 0) return Strings.get("disk.unknown");
        if (bytes < 1024) return Strings.get("units.bytes", bytes);
        double value = bytes / 1024.0;
        String[] units = {"units.kb", "units.mb", "units.gb", "units.tb"};
        int index = 0;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        String number = value >= 100 ? String.format(Strings.language().locale(), "%.0f", value)
                : String.format(Strings.language().locale(), "%.1f", value);
        return Strings.get(units[index], number);
    }
}
