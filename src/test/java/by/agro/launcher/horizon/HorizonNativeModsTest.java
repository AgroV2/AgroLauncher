package by.agro.launcher.horizon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.google.gson.JsonObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class HorizonNativeModsTest {
    @TempDir Path temp;
    private Path bundle(String name, String id, String extra, String mixinExtra, boolean evil) throws IOException {
        Path p = temp.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(p))) {
            entry(zip, "horizon-native.json", "{\"schema\":1,\"id\":\"" + id + "\",\"version\":\"1.0\",\"game\":\"26.3\",\"horizon\":\"" + "a".repeat(40) + "\",\"mixins\":\"" + "b".repeat(40) + "\"" + extra + "}");
            entry(zip, "mixins/" + id + "/horizon-mixin.toml", "[mod]\nid='" + id + "'\nversion='1.0'\n[compatibility]\nhorizon_revision='" + "a".repeat(40) + "'\n" + mixinExtra);
            entry(zip, "mixins/" + id + "/helper.rs", "// inert test fixture");
            if (evil) entry(zip, "../escape.rs", "no");
        }
        return p;
    }
    private void entry(ZipOutputStream z, String name, String value) throws IOException { z.putNextEntry(new ZipEntry(name)); z.write(value.getBytes(StandardCharsets.UTF_8)); z.closeEntry(); }
    @Test void installToggleAndDeletePreserveOtherSettings() throws Exception {
        HorizonNativeMods manager = new HorizonNativeMods(temp.resolve("game"), temp.resolve("root"));
        Files.createDirectories(temp.resolve("game/horizon"));
        Files.writeString(temp.resolve("game/horizon/horizon-mods.txt"), "other:value\nmod.java:true\nmod.demo:false\n");
        manager.add(bundle("input.hnmod", "demo", "", "", false));
        assertTrue(manager.list().get(0).enabled());
        String flags = Files.readString(temp.resolve("game/horizon/horizon-mods.txt"));
        assertTrue(flags.contains("other:value\n")); assertTrue(flags.contains("mod.java:true\n"));
        assertFalse(flags.contains("mod.demo:false")); assertTrue(flags.contains("mod.demo:true\n"));
        assertThrows(IOException.class, () -> manager.add(bundle("other.hnmod", "demo", "", "", false)));
        manager.setEnabled("demo", false); assertNull(manager.builtClient());
        assertFalse(manager.list().get(0).enabled()); manager.remove(manager.list().get(0));
        assertTrue(manager.list().isEmpty()); assertTrue(Files.readString(temp.resolve("game/horizon/horizon-mods.txt")).contains("mod.java:true"));
        assertEquals(temp.resolve("game/horizon/.agro-native-build"), manager.builderDirectory());
    }
    @Test void rejectsTraversalAndInvalidId() throws Exception {
        assertThrows(IOException.class, () -> HorizonNativeMods.inspect(bundle("bad.hnmod", "demo", "", "", true), Set.of()));
        assertThrows(IOException.class, () -> HorizonNativeMods.inspect(bundle("badid.hnmod", "../evil", "", "", false), Set.of()));
        assertFalse(Files.exists(temp.resolve("escape.rs")));
    }
    @Test void detectsChangedModAndBinaryBeforeLaunch() throws Exception {
        HorizonNativeMods manager = new HorizonNativeMods(temp.resolve("game"), temp.resolve("root"));
        manager.add(bundle("mod.hnmod", "demo", "", "", false));
        var mod = manager.list().get(0); assertThrows(IOException.class, manager::builtClient);
        Path dir = manager.builderDirectory().resolve("versions/native-fixture");
        Files.createDirectories(HorizonManager.resources(dir)); Files.writeString(HorizonManager.resources(dir).resolve("horizon-resources.json"), "{}");
        Files.writeString(dir.resolve(".installed"), ""); Path bin = dir.resolve("horizon-client" + (by.agro.launcher.core.Platform.isWindows() ? ".exe" : ""));
        Files.writeString(bin, "fake");
        String snapshot = "demo.hnmod:demo:true:" + hash(mod.file()) + "\n";
        JsonObject record = new JsonObject(); record.addProperty("version", "native-fixture"); record.addProperty("snapshot", snapshot); record.addProperty("binarySha256", hash(bin));
        Files.writeString(manager.builderDirectory().resolve("agro-native-build.json"), record.toString());
        assertEquals(dir, manager.builtClient());
        Files.writeString(bin, "changed"); assertThrows(IOException.class, manager::builtClient);
        manager.setEnabled("demo", false); assertNull(manager.builtClient());
    }
    @Test @org.junit.jupiter.api.condition.EnabledOnOs({org.junit.jupiter.api.condition.OS.LINUX, org.junit.jupiter.api.condition.OS.MAC})
    void officialBuilderCreatesSeparateValidatedInstance() throws Exception {
        Path base = temp.resolve("base"), game = temp.resolve("game"), tool = temp.resolve("fake-horizon-launcher");
        Files.createDirectories(base.resolve("crates/horizon-prepare/src"));
        Files.createDirectories(HorizonManager.resources(base));
        Files.writeString(base.resolve("research-lock.json"), "{\"minecraft\":{\"version\":\"26.3\",\"client_sha1\":\"" + "a".repeat(40) + "\",\"server_sha1\":\"" + "b".repeat(40) + "\",\"java_major\":25}}");
        Files.writeString(base.resolve("crates/horizon-prepare/src/lib.rs"), "pub const OUTPUT_VERSION: u32 = 3;");
        Files.writeString(HorizonManager.resources(base).resolve("horizon-resources.json"), "{}");
        Files.writeString(base.resolve("horizon-client"), "original");
        Files.writeString(tool, "#!/bin/sh\nset -eu\n" +
                "printf '%s\n' \"$@\" > \"$HORIZON_LAUNCHER_HOME/args.txt\"\n" +
                "cp -R \"$HORIZON_LAUNCHER_HOME/versions/nightly\" \"$HORIZON_LAUNCHER_HOME/versions/native-fixture\"\n" +
                "echo modded > \"$HORIZON_LAUNCHER_HOME/versions/native-fixture/horizon-client\"\n" +
                "mkdir -p \"$HORIZON_LAUNCHER_HOME/native/source/" + "a".repeat(40) + "\"\n" +
                "cp -R \"$HORIZON_LAUNCHER_HOME/versions/nightly/.\" \"$HORIZON_LAUNCHER_HOME/native/source/" + "a".repeat(40) + "\"\n" +
                "echo '{\"horizon\":\"" + "a".repeat(40) + "\",\"target\":\"client\",\"mods\":[{\"id\":\"demo\",\"version\":\"1.0\"}]}' > \"$HORIZON_LAUNCHER_HOME/versions/native-fixture/native-mods.json\"\n" +
                "echo '{\"instances\":{\"horizon\":{\"version\":\"native-fixture\"}}}' > \"$HORIZON_LAUNCHER_HOME/instances.json\"\n");
        assertTrue(tool.toFile().setExecutable(true));
        HorizonNativeMods manager = new HorizonNativeMods(game, temp.resolve("root"));
        manager.add(bundle("demo.hnmod", "demo", "", "", false));
        manager.buildOfficial(tool, base, x -> {});
        assertEquals("modded", Files.readString(manager.builtClient().resolve("horizon-client")).trim());
        assertEquals("original", Files.readString(base.resolve("horizon-client")));
        assertTrue(Files.readString(manager.builderDirectory().resolve("args.txt")).contains("--trust-native-code"));
        assertFalse(Files.exists(game.resolve("saves")));
        manager.setEnabled("demo", false); assertNull(manager.builtClient());
        Path badTool = temp.resolve("fake-omitted");
        Files.writeString(badTool, Files.readString(tool).replace("\"id\":\"demo\"", "\"id\":\"other\""));
        assertTrue(badTool.toFile().setExecutable(true));
        HorizonNativeMods rejected = new HorizonNativeMods(temp.resolve("other-game"), temp.resolve("root"));
        rejected.add(bundle("second.hnmod", "demo", "", "", false));
        assertThrows(IOException.class, () -> rejected.buildOfficial(badTool, base, x -> {}));
        assertFalse(Files.exists(rejected.builderDirectory().resolve("agro-native-build.json")));
        assertEquals("original", Files.readString(base.resolve("horizon-client")));
    }
    @Test void createsIndependentUniquelyNamedBuildMetadata() throws Exception {
        var builds = new by.agro.launcher.mods.ModBuildManager(by.agro.launcher.core.LauncherPaths.forRoot(temp));
        var first = builds.newHorizonBuild(2);
        assertTrue(first.name.contains("2")); assertTrue(first.name.contains(java.time.LocalDate.now().toString()));
        assertEquals("horizon", first.loader); assertTrue(builds.list().isEmpty());
        builds.writeMetadata(first);
        var second = builds.newHorizonBuild(2);
        assertNotEquals(first.id, second.id); assertNotEquals(first.name, second.name);
        assertNotEquals(builds.gameDir(first), builds.gameDir(second));
        assertFalse(Files.exists(builds.gameDir(first).resolve("saves")));
    }
    private String hash(Path p) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))); }
}
