package by.agro.launcher.mods;

import by.agro.launcher.core.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModBuildManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void deletesBuildRecursivelyWithoutFollowingSymlinks() throws IOException {
        ModBuildManager manager = new ModBuildManager(LauncherPaths.forRoot(tempDir));
        ModBuildManager.ModBuild build = build("safe-build");
        Path buildDir = manager.buildsDir().resolve(build.id);
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(buildDir.resolve("game/mods"));
        Files.writeString(buildDir.resolve("game/mods/mod.jar"), "data");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("keep.txt"), "keep");
        try {
            Files.createSymbolicLink(buildDir.resolve("outside-link"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) {

        }

        manager.delete(build);

        assertFalse(Files.exists(buildDir));
        assertTrue(Files.exists(outside.resolve("keep.txt")));
    }

    @Test
    void rejectsMissingAndTraversalIds() {
        ModBuildManager manager = new ModBuildManager(LauncherPaths.forRoot(tempDir));

        assertThrows(IOException.class, () -> manager.delete(null));
        assertThrows(IOException.class, () -> manager.delete(build("")));
        assertThrows(IOException.class, () -> manager.delete(build("../outside")));
        assertThrows(IOException.class, () -> manager.delete(build("nested/build")));
        assertThrows(IOException.class, () -> manager.delete(build(tempDir.resolve("outside").toString())));
    }

    private static ModBuildManager.ModBuild build(String id) {
        ModBuildManager.ModBuild build = new ModBuildManager.ModBuild();
        build.id = id;
        return build;
    }
}
