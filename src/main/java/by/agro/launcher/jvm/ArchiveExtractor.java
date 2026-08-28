package by.agro.launcher.jvm;

import by.agro.launcher.core.Platform;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public final class ArchiveExtractor {

    private ArchiveExtractor() {
    }

    public static void unzip(Path archive, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = resolveSafely(targetDir, entry.getName());
                if (target == null) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }


    public static void untarGz(Path archive, Path targetDir) throws IOException {
        try (InputStream fileIn = Files.newInputStream(archive);
             GZIPInputStream gzip = new GZIPInputStream(new BufferedInputStream(fileIn))) {
            byte[] header = new byte[512];
            while (true) {
                int read = readFully(gzip, header, 0, 512);
                if (read < 512) {
                    break;
                }
                if (isEmptyBlock(header)) {
                    break;
                }

                String name = readString(header, 0, 100);
                if (name.isEmpty()) {
                    break;
                }
                String sizeField = readString(header, 124, 12).trim();
                long size = parseOctal(sizeField);
                char typeFlag = (char) (header[156] & 0xFF);
                String prefix = readString(header, 345, 155);
                if (!prefix.isEmpty()) {
                    name = prefix + "/" + name;
                }
                String mode = readString(header, 100, 8).trim();
                String linkName = readString(header, 157, 100);

                Path target = resolveSafely(targetDir, name);

                if (typeFlag == '1' || typeFlag == '2') {
                    createLink(targetDir, target, linkName, typeFlag == '2');
                    skip(gzip, paddedSize(size));
                } else if (typeFlag == '5') {

                    if (target != null) {
                        Files.createDirectories(target);
                    }
                    skip(gzip, paddedSize(size));
                } else if (typeFlag == '0' || typeFlag == '\0') {

                    if (target != null) {
                        Path parent = target.getParent();
                        if (parent != null) {
                            Files.createDirectories(parent);
                        }
                        try (var out = Files.newOutputStream(target)) {
                            copyExactly(gzip, out, size);
                        }
                        if (isExecutableMode(mode)) {
                            makeExecutable(target);
                        }
                        skip(gzip, paddedSize(size) - size);
                    } else {
                        skip(gzip, paddedSize(size));
                    }
                } else {

                    skip(gzip, paddedSize(size));
                }
            }
        }
    }


    private static void createLink(Path targetDir, Path linkPath, String linkName, boolean symbolic) {
        if (linkPath == null || linkName == null || linkName.isBlank()) {
            return;
        }
        try {
            Path parent = linkPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(linkPath);

            if (symbolic) {

                Path resolved = linkPath.resolveSibling(linkName).normalize();
                if (!resolved.startsWith(targetDir.normalize())) {
                    return;
                }
                try {
                    Files.createSymbolicLink(linkPath, Path.of(linkName));
                    return;
                } catch (IOException | UnsupportedOperationException e) {

                    if (Files.exists(resolved)) {
                        Files.copy(resolved, linkPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                }
            }

            Path resolved = resolveSafely(targetDir, linkName);
            if (resolved == null || !Files.exists(resolved)) {
                return;
            }
            try {
                Files.createLink(linkPath, resolved);
            } catch (IOException | UnsupportedOperationException e) {
                Files.copy(resolved, linkPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {

        }
    }


    private static Path resolveSafely(Path targetDir, String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return null;
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path resolved = targetDir.resolve(normalized).normalize();
        if (!resolved.startsWith(targetDir.normalize())) {
            return null;
        }
        return resolved;
    }

    public static void makeExecutable(Path file) {
        if (Platform.isWindows()) {
            return;
        }
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (IOException | UnsupportedOperationException ignored) {

        }
    }

    private static boolean isExecutableMode(String mode) {
        try {
            long parsed = parseOctal(mode);
            return (parsed & 0111) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static long paddedSize(long size) {
        long remainder = size % 512;
        return remainder == 0 ? size : size + (512 - remainder);
    }

    private static boolean isEmptyBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readString(byte[] buffer, int offset, int length) {
        int end = offset;
        int limit = offset + length;
        while (end < limit && buffer[end] != 0) {
            end++;
        }
        return new String(buffer, offset, end - offset, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static long parseOctal(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        long result = 0;
        for (char c : trimmed.toCharArray()) {
            if (c < '0' || c > '7') {
                break;
            }
            result = result * 8 + (c - '0');
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

    private static void copyExactly(InputStream in, java.io.OutputStream out, long size) throws IOException {
        byte[] buffer = new byte[65536];
        long remaining = size;
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, toRead);
            if (read < 0) {
                throw new IOException("Неожиданный конец архива");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skip(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        byte[] buffer = new byte[8192];
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            int read = in.read(buffer, 0, toRead);
            if (read < 0) {
                return;
            }
            remaining -= read;
        }
    }
}
