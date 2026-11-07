package by.agro.launcher.instances;

import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.mods.ModBuildManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceClonerTest {

    @TempDir
    Path root;

    @Test
    void copiesOnlySelectedPartsIntoNewBuild() throws IOException {
        LauncherPaths paths = LauncherPaths.forRoot(root);
        Path source = root.resolve("instances/installations/1.20.1/fabric/recommended");
        Files.createDirectories(source.resolve("mods"));
        Files.writeString(source.resolve("mods/a.jar"), "a");
        Files.writeString(source.resolve("mods/b.jar.disabled"), "b");
        Files.createDirectories(source.resolve("saves/World/region"));
        Files.writeString(source.resolve("saves/World/level.dat"), "x");
        Files.writeString(source.resolve("saves/World/session.lock"), "x");
        Files.writeString(source.resolve("options.txt"), "fov:90");
        Files.createDirectories(source.resolve("config"));
        Files.writeString(source.resolve("config/sodium.json"), "{}");
        Files.writeString(source.resolve(".agrolauncher-playtime.json"), "{\"totalMs\":5}");

        InstanceCloner cloner = new InstanceCloner(paths);
        var sourceInfo = new InstanceCloner.Source(source, "1.20.1", "fabric", "recommended");
        assertTrue(InstanceCloner.estimateBytes(source, EnumSet.of(InstanceCloner.Part.MODS)) >= 2);

        ModBuildManager.ModBuild build = cloner.cloneToBuild(sourceInfo, "Test copy",
                EnumSet.of(InstanceCloner.Part.MODS, InstanceCloner.Part.SETTINGS));
        ModBuildManager builds = new ModBuildManager(paths);
        Path game = builds.gameDir(build);
        assertEquals("", build.loaderVersion);
        assertEquals(2, build.files);
        assertTrue(Files.isRegularFile(game.resolve("mods/a.jar")));
        assertTrue(Files.isRegularFile(game.resolve("options.txt")));
        assertTrue(Files.isRegularFile(game.resolve("config/sodium.json")));
        assertFalse(Files.exists(game.resolve("saves")));
        assertFalse(Files.exists(game.resolve(".agrolauncher-playtime.json")));
        assertEquals(1, builds.list().size());
        assertEquals("Test copy", builds.list().get(0).name);

        ModBuildManager.ModBuild worlds = cloner.cloneToBuild(sourceInfo, "Worlds",
                EnumSet.of(InstanceCloner.Part.WORLDS));
        Path worldsGame = builds.gameDir(worlds);
        assertTrue(Files.isRegularFile(worldsGame.resolve("saves/World/level.dat")));
        assertFalse(Files.exists(worldsGame.resolve("saves/World/session.lock")));
        assertEquals(0, worlds.files);
    }

    @Test
    void rejectsHorizonAndMissingSource() {
        LauncherPaths paths = LauncherPaths.forRoot(root);
        InstanceCloner cloner = new InstanceCloner(paths);
        assertThrows(IOException.class, () -> cloner.cloneToBuild(
                new InstanceCloner.Source(root.resolve("h"), "26.3", "horizon", "default"), "x",
                EnumSet.allOf(InstanceCloner.Part.class)));
        assertThrows(IOException.class, () -> cloner.cloneToBuild(
                new InstanceCloner.Source(root.resolve("missing"), "1.20.1", "vanilla", "default"), "x",
                EnumSet.allOf(InstanceCloner.Part.class)));
    }
}
