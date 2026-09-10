package by.agro.launcher.jvm;

import by.agro.launcher.core.Platform;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ArchiveExtractor {

    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_EXPANDED_BYTES = 8L * 1024 * 1024 * 1024;

    private ArchiveExtractor() {
    }

    public static void unzip(Path archive, Path targetDir) throws IOException {
        ExtractionBudget budget = new ExtractionBudget();
        Path root = prepareRoot(targetDir);
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                budget.addEntry();
                Path target = resolveSafely(root, entry.getName());
                if (entry.isDirectory()) {
                    createDirectoriesSafely(root, target);
                } else {
                    createDirectoriesSafely(root, target.getParent());
                    rejectExistingLink(target);
                    try (OutputStream out = Files.newOutputStream(target)) {
                        copyLimited(zip, out, budget, -1);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    public static void untarGz(Path archive, Path targetDir) throws IOException {
        ExtractionBudget budget = new ExtractionBudget();
        Path root = prepareRoot(targetDir);
        try (InputStream fileIn = Files.newInputStream(archive);
             GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(fileIn))) {
            byte[] header = new byte[512];
            while (true) {
                int read = readFully(gzip, header, 0, header.length);
                if (read == 0 || isEmptyBlock(header)) {
                    break;
                }
                if (read != header.length) {
                    throw new IOException("Truncated tar header");
                }
                budget.addEntry();
                String name = readString(header, 0, 100);
                String prefix = readString(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                long size;
                try {
                    size = parseOctal(readString(header, 124, 12));
                } catch (ArithmeticException e) {
                    throw new IOException("Invalid tar entry size", e);
                }
                char type = (char) (header[156] & 0xff);
                String mode = readString(header, 100, 8);
                Path target = resolveSafely(root, name);

                if (type == '1' || type == '2') {
                    throw new IOException("Archive links are not allowed: " + name);
                } else if (type == '5') {
                    createDirectoriesSafely(root, target);
                    skipExactly(gzip, paddedSize(size));
                } else if (type == '0' || type == '\0') {
                    budget.reserveDeclared(size);
                    createDirectoriesSafely(root, target.getParent());
                    rejectExistingLink(target);
                    try (OutputStream out = Files.newOutputStream(target)) {
                        copyLimited(gzip, out, budget, size);
                    }
                    skipExactly(gzip, paddedSize(size) - size);
                    if (isExecutableMode(mode)) {
                        makeExecutable(target);
                    }
                } else {
                    skipExactly(gzip, paddedSize(size));
                }
            }
        }
    }

    private static Path prepareRoot(Path targetDir) throws IOException {
        Path root = targetDir.toAbsolutePath().normalize();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
            throw new IOException("Extraction target must not be a symbolic link: " + root);
        }
        Files.createDirectories(root);
        return root;
    }

    private static Path resolveSafely(Path root, String entryName) throws IOException {
        if (entryName == null || entryName.isBlank() || entryName.indexOf('\0') >= 0) {
            throw new IOException("Invalid empty archive entry");
        }
        String normalizedName = entryName.replace('\\', '/');
        if (normalizedName.startsWith("/") || normalizedName.matches("^[A-Za-z]:.*")) {
            throw new IOException("Absolute archive path is not allowed: " + entryName);
        }
        Path resolved = root.resolve(normalizedName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Archive path escapes target directory: " + entryName);
        }
        return resolved;
    }

    private static void createDirectoriesSafely(Path root, Path directory) throws IOException {
        if (directory == null) {
            return;
        }
        Path relative = root.relativize(directory.normalize());
        Path current = root;
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unsafe extraction path component: " + current);
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private static void rejectExistingLink(Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to overwrite symbolic link: " + target);
        }
    }

    public static void makeExecutable(Path file) {
        if (Platform.isWindows() || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
        }
    }

    private static boolean isExecutableMode(String mode) {
        try {
            return (parseOctal(mode) & 0111) != 0;
        } catch (ArithmeticException e) {
            return false;
        }
    }

    private static long paddedSize(long size) throws IOException {
        if (size < 0 || size > Long.MAX_VALUE - 511) {
            throw new IOException("Invalid archive entry size: " + size);
        }
        return ((size + 511) / 512) * 512;
    }

    private static boolean isEmptyBlock(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readString(byte[] buffer, int offset, int length) {
        int end = offset;
        while (end < offset + length && buffer[end] != 0) {
            end++;
        }
        return new String(buffer, offset, end - offset, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static long parseOctal(String value) {
        String trimmed = value.trim();
        long result = 0;
        for (char c : trimmed.toCharArray()) {
            if (c < '0' || c > '7') {
                break;
            }
            result = Math.addExact(Math.multiplyExact(result, 8), c - '0');
        }
        return result;
    }

    private static int readFully(InputStream in, byte[] buffer, int offset, int length) throws IOException {
        int total = 0;
        while (total < length) {
            int read = in.read(buffer, offset + total, length - total);
            if (read < 0) {
                break;
            }
            total += read;
        }
        return total;
    }

    private static void copyLimited(InputStream in, OutputStream out, ExtractionBudget budget,
                                    long exactSize) throws IOException {
        byte[] buffer = new byte[65536];
        long remaining = exactSize;
        while (exactSize < 0 || remaining > 0) {
            int requested = exactSize < 0 ? buffer.length : (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, requested);
            if (read < 0) {
                if (exactSize >= 0) {
                    throw new IOException("Unexpected end of archive");
                }
                break;
            }
            budget.addBytes(read);
            out.write(buffer, 0, read);
            if (exactSize >= 0) {
                remaining -= read;
            }
        }
    }

    private static void skipExactly(InputStream in, long bytes) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = bytes;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("Unexpected end of archive");
            }
            remaining -= read;
        }
    }

    private static final class ExtractionBudget {
        private int entries;
        private long bytes;

        void addEntry() throws IOException {
            if (++entries > MAX_ENTRIES) {
                throw new IOException("Archive contains too many entries");
            }
        }

        void reserveDeclared(long size) throws IOException {
            if (size < 0 || size > MAX_EXPANDED_BYTES - bytes) {
                throw new IOException("Archive exceeds expanded-size limit");
            }
        }

        void addBytes(long count) throws IOException {
            if (count < 0 || count > MAX_EXPANDED_BYTES - bytes) {
                throw new IOException("Archive exceeds expanded-size limit");
            }
            bytes += count;
        }
    }
}
