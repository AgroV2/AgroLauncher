package by.agro.launcher.integrity;

import by.agro.launcher.core.HashUtil;
import by.agro.launcher.core.SecureFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class IntegrityService {
    public ManifestEntry observe(Path root, Path file, String ownership, String sourceUrl) throws IOException {
        return create(root, file, ownership, "observed", sourceUrl, null);
    }

    public ManifestEntry trusted(Path root, Path file, String ownership, String sourceUrl,
                                 String upstreamSha256) throws IOException {
        return create(root, file, ownership, "trusted", sourceUrl, upstreamSha256);
    }

    private ManifestEntry create(Path root, Path file, String ownership, String provenance,
                                 String sourceUrl, String expectedSha256) throws IOException {
        Path safe = resolve(root, root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString());
        requireRegular(safe);
        String digest = HashUtil.digest(safe, "SHA-256");
        if (expectedSha256 != null && !expectedSha256.isBlank()
                && !digest.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("SHA-256 mismatch for " + safe);
        }
        String relative = root.toAbsolutePath().normalize().relativize(safe).toString().replace('\\', '/');
        return new ManifestEntry(relative, digest, Files.size(safe), ownership, provenance, sourceUrl);
    }

    public boolean verify(Path root, ManifestEntry entry) throws IOException {
        if (entry == null || entry.sha256 == null || entry.sha256.isBlank() || entry.size < 0) return false;
        Path file = resolve(root, entry.path);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) return false;
        return Files.size(file) == entry.size
                && entry.sha256.equalsIgnoreCase(HashUtil.digest(file, "SHA-256"));
    }

    public Path resolve(Path root, String relativeText) throws IOException {
        if (relativeText == null || relativeText.isBlank()) throw new IOException("Empty manifest path");
        Path relative;
        try { relative = Path.of(relativeText); }
        catch (RuntimeException e) { throw new IOException("Invalid manifest path", e); }
        if (relative.isAbsolute()) throw new IOException("Absolute manifest path is forbidden: " + relativeText);
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path result = normalizedRoot.resolve(relative).normalize();
        if (result.equals(normalizedRoot) || !result.startsWith(normalizedRoot)) {
            throw new IOException("Manifest path escapes root: " + relativeText);
        }
        SecureFiles.rejectSymlinkParents(result);
        return result;
    }

    private void requireRegular(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException("Manifest entry is not a regular file: " + file);
        }
    }
}
