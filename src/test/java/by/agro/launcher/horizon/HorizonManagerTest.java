package by.agro.launcher.horizon;

import by.agro.launcher.core.*;
import by.agro.launcher.loaders.LoaderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HorizonManagerTest {
    @TempDir Path temp;

    @Test void mapsOnlySupportedNativePlatforms() throws Exception {
        assertEquals("horizon-linux-x86_64.tar.gz", HorizonManager.platformArchive(Platform.LINUX, "x64"));
        assertEquals("horizon-windows-x64.zip", HorizonManager.platformArchive(Platform.WINDOWS, "x64"));
        assertEquals("horizon-macos-arm64.zip", HorizonManager.platformArchive(Platform.OSX, "arm64"));
        assertEquals("horizon-macos-x64.zip", HorizonManager.platformArchive(Platform.OSX, "x64"));
        assertThrows(IOException.class, () -> HorizonManager.platformArchive(Platform.LINUX, "arm64"));
        assertThrows(IOException.class, () -> HorizonManager.platformArchive(Platform.WINDOWS, "x86"));
    }

    @Test void launcherAdapterOnlyOffers263() throws Exception {
        var installer = new HorizonInstaller(null);
        assertEquals(LoaderType.HORIZON, installer.type());
        assertFalse(LoaderType.HORIZON.supportsMods());
        assertTrue(installer.availableVersions("1.21.1").isEmpty());
        assertEquals("nightly", installer.availableVersions("26.3").get(0).version);
        assertThrows(IOException.class, () -> installer.install("1.21.1", null, ProgressListener.NOOP));
        assertEquals(LoaderType.HORIZON, LoaderType.fromId("horizon"));
    }

    @Test void nativeCommandIsIsolatedAndContainsNoAuthenticationOrJvmFlags() {
        Settings settings = new Settings();
        settings.windowWidth = 1440;
        settings.windowHeight = 900;
        List<String> command = HorizonManager.launchCommand(temp.resolve("engine"), temp.resolve("resources"),
                temp.resolve("instance"), "OfflineName", settings, "localhost:25565");
        assertEquals("OfflineName", value(command, "--name"));
        assertEquals(temp.resolve("instance").toString(), value(command, "--game-dir"));
        assertEquals(temp.resolve("instance/horizon").toString(), value(command, "--home"));
        assertEquals("1440", value(command, "--width"));
        assertEquals("900", value(command, "--height"));
        assertEquals("localhost:25565", value(command, "--server"));
        assertTrue(command.stream().noneMatch(a -> a.contains("accessToken") || a.contains("session") || a.startsWith("-X")));
        settings.fullscreen = true;
        command = HorizonManager.launchCommand(temp, temp, temp, "Alex", settings, "");
        assertTrue(command.contains("--fullscreen"));
        assertFalse(command.contains("--width"));
        assertFalse(command.contains("--server"));
    }

    @Test void rejectsAbsentOrUnsafeOfflineBuild() throws Exception {
        Settings settings = new Settings(); settings.offlineMode = true;
        HorizonManager manager = new HorizonManager(LauncherPaths.forRoot(temp), null, settings, null);
        assertThrows(IOException.class, () -> manager.ensureInstalled(ProgressListener.NOOP));
        Files.writeString(manager.root().resolve("current"), "../../outside");
        assertThrows(IOException.class, () -> manager.ensureInstalled(ProgressListener.NOOP));
    }

    @Test void offlineInstallationUsesExistingBuildWithoutNetwork() throws Exception {
        Settings settings = new Settings(); settings.offlineMode = true;
        HorizonManager manager = new HorizonManager(LauncherPaths.forRoot(temp), null, settings, null);
        Path build = manager.root().resolve("builds/nightly-0123456789ab");
        Files.createDirectories(build);
        String ext = Platform.isWindows() ? ".exe" : "";
        Files.writeString(build.resolve("research-lock.json"), "{}");
        Files.writeString(build.resolve("horizon-client" + ext), "placeholder");
        Files.writeString(build.resolve("horizon-prepare" + ext), "placeholder");
        Files.writeString(manager.root().resolve("current"), build.getFileName().toString());
        assertEquals(build, manager.ensureInstalled(ProgressListener.NOOP));
        assertEquals(temp.resolve("instances/installations/26.3/horizon/default"), manager.instanceDir());
    }

    @Test void preparationMarkerRequiresMatchingPinsAndSounds() throws Exception {
        var pins = new HorizonManager.Pins("26.3", "a".repeat(40), "b".repeat(40), 25, 3);
        Path resources = HorizonManager.resources(temp);
        Files.createDirectories(resources.resolve("assets/minecraft"));
        Files.writeString(resources.resolve("horizon-resources.json"), "{}");
        Files.writeString(temp.resolve(".agrolauncher-prepared"), pins.key());
        assertFalse(HorizonManager.prepared(temp, pins));
        Files.writeString(resources.resolve("assets/minecraft/sounds.json"), "{}");
        assertTrue(HorizonManager.prepared(temp, pins));
        assertFalse(HorizonManager.prepared(temp,
                new HorizonManager.Pins("26.3", "a".repeat(40), "b".repeat(40), 25, 4)));
    }

    @Test void readsOutputVersionAndRejectsChangedMinecraftPin() throws Exception {
        Files.createDirectories(temp.resolve("crates/horizon-prepare/src"));
        Files.writeString(temp.resolve("crates/horizon-prepare/src/lib.rs"), "pub const OUTPUT_VERSION: u32 = 3;");
        String lock = "{\"minecraft\":{\"version\":\"26.3\",\"java_major\":25,\"client_sha1\":\"" + "a".repeat(40)
                + "\",\"server_sha1\":\"" + "b".repeat(40) + "\"}}";
        Files.writeString(temp.resolve("research-lock.json"), lock);
        assertEquals(3, HorizonManager.readPins(temp).outputVersion());
        Files.writeString(temp.resolve("research-lock.json"), lock.replace("26.3", "26.4"));
        assertThrows(IOException.class, () -> HorizonManager.readPins(temp));
    }

    @Test void copyingResourcesDoesNotLinkOrEscapeThroughSymlinks() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Path destination = temp.resolve("destination");
        Files.writeString(source.resolve("file.json"), "original");
        HorizonManager.copyTree(source, destination);
        Files.writeString(destination.resolve("file.json"), "changed");
        assertEquals("original", Files.readString(source.resolve("file.json")));
        if (!Platform.isWindows()) {
            Files.createSymbolicLink(source.resolve("escape"), temp);
            assertThrows(IOException.class, () -> HorizonManager.copyTree(source, destination));
        }
    }

    @Test void rejectsSymlinkedBuildRoot() throws Exception {
        if (Platform.isWindows()) return;
        Settings settings = new Settings(); settings.offlineMode = true;
        HorizonManager manager = new HorizonManager(LauncherPaths.forRoot(temp.resolve("launcher")), null, settings, null);
        Files.createDirectories(manager.root());
        Path external = Files.createDirectory(temp.resolve("external"));
        Files.createSymbolicLink(manager.root().resolve("builds"), external);
        assertThrows(IOException.class, () -> manager.ensureInstalled(ProgressListener.NOOP));
        try (var entries = Files.list(external)) { assertEquals(0, entries.count()); }
    }

    @Test void preparedOfflineInstanceLaunchesWithoutJavaNetworkOrAccountTokens() throws Exception {
        if (Platform.isWindows()) return;
        LauncherPaths paths = LauncherPaths.forRoot(temp);
        paths.ensureDirectories();
        Settings settings = new Settings(); settings.offlineMode = true;
        var vanilla = new by.agro.launcher.launch.GameLauncher(paths, null, settings, null);
        var manager = new HorizonManager(paths, null, settings, vanilla);
        Path build = manager.root().resolve("builds/nightly-0123456789ab");
        Files.createDirectories(build.resolve("crates/horizon-prepare/src"));
        Files.writeString(build.resolve("crates/horizon-prepare/src/lib.rs"), "pub const OUTPUT_VERSION: u32 = 3;");
        Files.writeString(build.resolve("research-lock.json"),
                "{\"minecraft\":{\"version\":\"26.3\",\"java_major\":25,\"client_sha1\":\"" + "a".repeat(40)
                        + "\",\"server_sha1\":\"" + "b".repeat(40) + "\"}}");
        Files.writeString(build.resolve("horizon-client"), "#!/bin/sh\nprintf '%s\\n' \"$@\"\nsleep 0.1\n");
        by.agro.launcher.jvm.ArchiveExtractor.makeExecutable(build.resolve("horizon-client"));
        Files.writeString(build.resolve("horizon-prepare"), "unused");
        Files.writeString(manager.root().resolve("current"), build.getFileName().toString());
        Path resources = HorizonManager.resources(build);
        Files.createDirectories(resources.resolve("assets/minecraft"));
        Files.writeString(resources.resolve("horizon-resources.json"), "{}");
        Files.writeString(resources.resolve("assets/minecraft/sounds.json"), "{}");
        Files.writeString(build.resolve(".agrolauncher-prepared"), HorizonManager.readPins(build).key());
        Path vanillaOptions = paths.gameDir().resolve("options.txt");
        Files.writeString(vanillaOptions, "do-not-change");
        var account = by.agro.launcher.auth.Account.offline("Alex");
        account.type = by.agro.launcher.auth.Account.Type.ELY_BY;
        account.accessToken = "secret-access-token";
        var lines = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var exited = new java.util.concurrent.CountDownLatch(1);
        var process = manager.launch(account, null, ProgressListener.NOOP, lines::add, code -> exited.countDown(), null);
        assertTrue(exited.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, process.raw().exitValue());
        assertTrue(lines.contains("Alex"));
        assertTrue(lines.contains(manager.instanceDir().toString()));
        assertTrue(lines.stream().noneMatch(line -> line.contains("secret-access-token") || line.contains("session")));
        assertFalse(Files.readString(process.sessionLogFile()).contains("secret-access-token"));
        assertEquals("do-not-change", Files.readString(vanillaOptions));
        assertTrue(Files.isDirectory(manager.instanceDir().resolve("resourcepacks")));
        assertTrue(Json.read(manager.instanceDir().resolve(".agrolauncher-playtime.json"),
                by.agro.launcher.launch.GameLauncher.PlaytimeMarker.class).totalMs > 0);
    }

    @Test void unknownRepublishedManifestDownloadsPinnedOriginalWithoutTouchingVanilla() throws Exception {
        Path build = Files.createDirectory(temp.resolve("engine"));
        Path vanilla = temp.resolve("minecraft/versions/26.3/26.3.json");
        Files.createDirectories(vanilla.getParent());
        Files.writeString(vanilla, "republished or rewritten JSON");
        String original = "original Mojang manifest";
        Path fixture = temp.resolve("original.json");
        Files.writeString(fixture, original);
        String sha = HashUtil.sha1(fixture);
        Files.writeString(build.resolve("research-lock.json"), "{\"minecraft\":{\"manifest_sha1\":\"" + sha + "\"}}");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        HorizonManager.ManifestDownload download = (url, target, hash) -> {
            calls.incrementAndGet();
            assertEquals("https://piston-meta.mojang.com/v1/packages/" + sha + "/26.3.json", url);
            assertEquals(sha, hash);
            Files.createDirectories(target.getParent());
            Files.copy(fixture, target);
        };
        HorizonManager.ensureKnownManifest(build, vanilla, false, download, ProgressListener.NOOP);
        Path fallback = build.resolve(".local/minecraft/26.3/manifest.json");
        assertEquals(original, Files.readString(fallback));
        assertEquals("republished or rewritten JSON", Files.readString(vanilla));
        HorizonManager.ensureKnownManifest(build, vanilla, true, (u, t, h) -> fail("unexpected network"), ProgressListener.NOOP);
        assertEquals(1, calls.get());
    }

    @Test void acceptedVanillaManifestSkipsFallbackAndNetwork() throws Exception {
        Path build = Files.createDirectory(temp.resolve("engine"));
        Path vanilla = temp.resolve("version.json");
        Files.writeString(vanilla, "accepted republished version");
        String accepted = HashUtil.sha1(vanilla);
        Files.writeString(build.resolve("research-lock.json"), "{\"minecraft\":{\"manifest_sha1\":\"" + "a".repeat(40)
                + "\",\"accepted_manifest_sha1\":[\"" + accepted + "\"]}}");
        HorizonManager.ensureKnownManifest(build, vanilla, true, (u, t, h) -> fail("unexpected network"), ProgressListener.NOOP);
        assertFalse(Files.exists(build.resolve(".local")));
    }

    @Test void missingOrCorruptFallbackOfflineDoesNotAlterVanilla() throws Exception {
        Path build = Files.createDirectory(temp.resolve("engine"));
        Path vanilla = temp.resolve("version.json");
        Files.writeString(vanilla, "unknown");
        Files.writeString(build.resolve("research-lock.json"), "{\"minecraft\":{\"manifest_sha1\":\"" + "a".repeat(40) + "\"}}");
        Path fallback = build.resolve(".local/minecraft/26.3/manifest.json");
        Files.createDirectories(fallback.getParent());
        Files.writeString(fallback, "bad cached data");
        assertThrows(IOException.class, () -> HorizonManager.ensureKnownManifest(build, vanilla, true,
                (u,t,h) -> fail("unexpected network"), ProgressListener.NOOP));
        assertEquals("unknown", Files.readString(vanilla));
        assertEquals("bad cached data", Files.readString(fallback));
    }

    @Test void rejectsIncorrectDownloadAndUnsafeLockHash() throws Exception {
        Path build = Files.createDirectory(temp.resolve("engine"));
        Path vanilla = temp.resolve("version.json");
        Files.writeString(vanilla, "unknown");
        Path lock = build.resolve("research-lock.json");
        Files.writeString(lock, "{\"minecraft\":{\"manifest_sha1\":\"" + "a".repeat(40) + "\"}}");
        assertThrows(IOException.class, () -> HorizonManager.ensureKnownManifest(build, vanilla, false, (u,t,h) -> {
            Files.createDirectories(t.getParent()); Files.writeString(t, "wrong data");
        }, ProgressListener.NOOP));
        Files.writeString(lock, "{\"minecraft\":{\"manifest_sha1\":\"../../unsafe\"}}");
        assertThrows(IOException.class, () -> HorizonManager.ensureKnownManifest(build, vanilla, false,
                (u,t,h) -> fail("unexpected network"), ProgressListener.NOOP));
        assertEquals("unknown", Files.readString(vanilla));
    }

    @Test void rejectsSymlinkedFallback() throws Exception {
        if (Platform.isWindows()) return;
        Path build = Files.createDirectory(temp.resolve("engine"));
        Path vanilla = temp.resolve("version.json"); Files.writeString(vanilla, "unknown");
        Files.writeString(build.resolve("research-lock.json"), "{\"minecraft\":{\"manifest_sha1\":\"" + "a".repeat(40) + "\"}}");
        Files.createSymbolicLink(build.resolve(".local"), temp);
        assertThrows(IOException.class, () -> HorizonManager.ensureKnownManifest(build, vanilla, false,
                (u,t,h) -> fail("unexpected network"), ProgressListener.NOOP));
    }

    private String value(List<String> command, String flag) { return command.get(command.indexOf(flag) + 1); }
}
