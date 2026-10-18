package by.agro.launcher.integrity;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.SecureFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class ManifestStore {
    public static final String FILE_NAME = ".agrolauncher-installation-manifest.json";

    public InstallationManifest read(Path root) throws IOException {
        Path file = path(root);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return new InstallationManifest();
        SecureFiles.rejectSymbolicLink(file, "Installation manifest");
        InstallationManifest manifest = Json.read(file, InstallationManifest.class);
        if (manifest == null) manifest = new InstallationManifest();
        manifest.applyDefaults();
        if (manifest.schemaVersion > InstallationManifest.CURRENT_SCHEMA) {
            throw new IOException("Unsupported installation manifest schema: " + manifest.schemaVersion);
        }
        return manifest;
    }

    public void write(Path root, InstallationManifest manifest) throws IOException {
        manifest.applyDefaults();
        manifest.schemaVersion = InstallationManifest.CURRENT_SCHEMA;
        Json.write(path(root), manifest);
    }

    public Path path(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        SecureFiles.rejectSymlinkParents(normalized);
        return normalized.resolve(FILE_NAME);
    }
}
