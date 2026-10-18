package by.agro.launcher.update;

import by.agro.launcher.core.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateManagerTest {
    @TempDir Path tempDir;

    @Test
    void selectsPlatformJarAndMatchingChecksum() {
        var generic = new UpdateChecker.Asset("AgroLauncher.jar", "https://example.test/a", 10);
        var windows = new UpdateChecker.Asset("AgroLauncher-Windows-x64-portable.jar", "https://example.test/w", 10);
        var checksum = new UpdateChecker.Asset(windows.name() + ".sha256", "https://example.test/s", 64);
        var assets = List.of(generic, windows, checksum);

        assertEquals(windows, UpdateChecker.selectArtifact(assets, Platform.WINDOWS, "x64"));
        assertEquals(generic, UpdateChecker.selectArtifact(assets, Platform.LINUX, "x64"));
        assertEquals(checksum, UpdateChecker.selectChecksum(assets, windows.name()));
    }

    @Test
    void parsesCommonSha256Formats() {
        String digest = "a".repeat(64);
        assertEquals(digest, UpdateManager.checksumFor(digest + "  AgroLauncher.jar\n", "AgroLauncher.jar"));
        assertEquals(digest, UpdateManager.checksumFor("SHA256 (AgroLauncher.jar) = " + digest, "AgroLauncher.jar"));
    }

    @Test
    void rejectsJarWithoutMainClass() throws IOException {
        Path jar = tempDir.resolve("bad.jar");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(jar))) { }
        assertThrows(IOException.class, () -> UpdateManager.validateJar(jar));
    }

    @Test
    void replacementRollsBackWhenStagedJarIsInvalid() throws IOException {
        Path target = tempDir.resolve("AgroLauncher.jar");
        Path staged = tempDir.resolve("AgroLauncher.jar.update");
        Path backup = tempDir.resolve("AgroLauncher.jar.backup");
        createRunnableJar(target);
        Files.writeString(staged, "not a jar");

        assertThrows(IOException.class, () -> UpdateManager.replaceWithRollback(target, staged, backup));
        UpdateManager.validateJar(target);
        assertTrue(Files.exists(target));
    }

    private static void createRunnableJar(Path path) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "example.Main");
        try (JarOutputStream ignored = new JarOutputStream(Files.newOutputStream(path), manifest)) { }
    }
}
