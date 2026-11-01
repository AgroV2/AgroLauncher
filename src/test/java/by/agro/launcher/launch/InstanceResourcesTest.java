package by.agro.launcher.launch;

import by.agro.launcher.core.*;
import by.agro.launcher.modrinth.ModInstaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class InstanceResourcesTest {
    @TempDir Path temp;

    @Test void packsAreIndependentForTwoInstances() throws Exception {
        LauncherPaths paths = LauncherPaths.forRoot(temp);
        GameLauncher launcher = new GameLauncher(paths, null, new Settings(), null);
        Path a = paths.installationGameDir("26.3", "vanilla", "default");
        Path b = paths.installationGameDir("26.3", "horizon", "default");
        launcher.ensureInstanceResourceFolders(a);
        launcher.ensureInstanceResourceFolders(b);
        for (String name : new String[]{"resourcepacks", "shaderpacks"}) {
            assertTrue(Files.isDirectory(a.resolve(name)));
            assertTrue(Files.isDirectory(b.resolve(name)));
            Files.writeString(a.resolve(name).resolve("only-a.zip"), "pack");
            assertFalse(Files.exists(b.resolve(name).resolve("only-a.zip")));
            assertFalse(Files.exists(paths.gameDir().resolve(name).resolve("only-a.zip")));
        }
    }

    @Test void detachesOldSharedLinksWithoutDeletingSharedPacks() throws Exception {
        if (Platform.isWindows()) return;
        LauncherPaths paths = LauncherPaths.forRoot(temp);
        GameLauncher launcher = new GameLauncher(paths, null, new Settings(), null);
        Path gameDir = paths.installationGameDir("26.3", "vanilla", "default");
        Files.createDirectories(gameDir);
        for (String name : new String[]{"resourcepacks", "shaderpacks"}) {
            Path shared = Files.createDirectories(paths.gameDir().resolve(name));
            Files.writeString(shared.resolve("existing.zip"), "keep-me");
            Files.createSymbolicLink(gameDir.resolve(name), shared);
        }
        launcher.ensureInstanceResourceFolders(gameDir);
        for (String name : new String[]{"resourcepacks", "shaderpacks"}) {
            Path local = gameDir.resolve(name);
            Path shared = paths.gameDir().resolve(name);
            assertFalse(Files.isSymbolicLink(local));
            assertEquals("keep-me", Files.readString(local.resolve("existing.zip")));
            Files.writeString(local.resolve("existing.zip"), "local-change");
            assertEquals("keep-me", Files.readString(shared.resolve("existing.zip")));
        }
    }

    @Test void modrinthInstallsAllContentIntoTheSelectedGameDirectory() throws Exception {
        LauncherPaths paths = LauncherPaths.forRoot(temp);
        Path gameDir = paths.installationGameDir("26.3", "fabric", "test");
        ModInstaller installer = new ModInstaller(paths, null, null, gameDir);
        var destination = ModInstaller.class.getDeclaredMethod("destinationFor", String.class);
        destination.setAccessible(true);
        assertEquals(gameDir.resolve("resourcepacks"), destination.invoke(installer, "resourcepack"));
        assertEquals(gameDir.resolve("shaderpacks"), destination.invoke(installer, "shader"));
        assertEquals(gameDir.resolve("mods"), destination.invoke(installer, "mod"));
    }

    @Test void existingPlaytimeFormatAndAccumulatedHoursRemainUnchanged() throws Exception {
        LauncherPaths paths = LauncherPaths.forRoot(temp);
        GameLauncher launcher = new GameLauncher(paths, null, new Settings(), null);
        Path gameDir = paths.installationGameDir("26.3", "horizon", "default");
        Files.createDirectories(gameDir);
        Path marker = gameDir.resolve(".agrolauncher-playtime.json");
        Files.writeString(marker, "{\"totalMs\":7200000}");
        launcher.recordPlayTime(gameDir, 60000);
        assertEquals(7260000, Json.read(marker, GameLauncher.PlaytimeMarker.class).totalMs);
        assertFalse(Json.readObject(marker).has("totalMillis"));
        launcher.recordPlayTime(gameDir, 0);
        assertEquals(7260000, Json.read(marker, GameLauncher.PlaytimeMarker.class).totalMs);
    }
}
