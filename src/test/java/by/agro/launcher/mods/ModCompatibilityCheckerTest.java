package by.agro.launcher.mods;

import by.agro.launcher.loaders.LoaderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCompatibilityCheckerTest {

    @TempDir
    Path game;

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private void jar(String name, Map<String, byte[]> entries) throws IOException {
        Files.createDirectories(game.resolve("mods"));
        try (OutputStream out = Files.newOutputStream(game.resolve("mods").resolve(name))) {
            out.write(zip(entries));
        }
    }

    private void fabricJar(String name, String json) throws IOException {
        jar(name, Map.of("fabric.mod.json", json.getBytes(StandardCharsets.UTF_8)));
    }

    private static boolean has(ModCompatibilityChecker.Report report, ModCompatibilityChecker.Severity severity,
                               String file) {
        return report.issues().stream().anyMatch(i -> i.severity() == severity && i.fileName().contains(file));
    }

    @Test
    void cleanFabricSetupHasNoIssues() throws IOException {
        fabricJar("api.jar", "{\"id\":\"fabric-api\",\"version\":\"0.92.0+1.20.1\",\"provides\":[\"fabric\"]}");
        fabricJar("sodium.jar", "{\"id\":\"sodium\",\"version\":\"0.5.3\",\"depends\":{\"minecraft\":\"~1.20.1\","
                + "\"fabricloader\":\">=0.14\",\"fabric\":\"*\",\"java\":\">=17\"}}");
        var report = ModCompatibilityChecker.check(game, "1.20.1", LoaderType.FABRIC, "0.15.7");
        assertTrue(report.issues().isEmpty(), report.issues().toString());
        assertEquals(2, report.modsChecked());
    }

    @Test
    void detectsWrongLoaderMissingDependencyMinecraftMismatchAndDuplicates() throws IOException {
        fabricJar("needs-api.jar", "{\"id\":\"a\",\"version\":\"1.0\",\"depends\":{\"fabric-api\":\">=0.90\"}}");
        fabricJar("old.jar", "{\"id\":\"b\",\"version\":\"1.0\",\"depends\":{\"minecraft\":\"1.19.x\"}}");
        fabricJar("dup1.jar", "{\"id\":\"c\",\"version\":\"1.0\"}");
        fabricJar("dup2.jar", "{\"id\":\"c\",\"version\":\"1.1\"}");
        jar("forge.jar", Map.of("META-INF/mods.toml", ("modLoader=\"javafml\"\n[[mods]]\nmodId=\"jei\"\n"
                + "version=\"15.0\"\n").getBytes(StandardCharsets.UTF_8)));
        var report = ModCompatibilityChecker.check(game, "1.20.1", LoaderType.FABRIC, "");
        assertTrue(has(report, ModCompatibilityChecker.Severity.ERROR, "needs-api.jar"));
        assertTrue(has(report, ModCompatibilityChecker.Severity.ERROR, "old.jar"));
        assertTrue(has(report, ModCompatibilityChecker.Severity.ERROR, "dup1.jar"));
        assertTrue(has(report, ModCompatibilityChecker.Severity.ERROR, "forge.jar"));
        assertTrue(report.hasErrors());
    }

    @Test
    void nestedJarsSatisfyDependenciesAndQuiltAcceptsFabricMods() throws IOException {
        byte[] nested = zip(Map.of("fabric.mod.json",
                "{\"id\":\"mixinextras\",\"version\":\"0.3.5\"}".getBytes(StandardCharsets.UTF_8)));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("fabric.mod.json", ("{\"id\":\"host\",\"version\":\"1.0\",\"depends\":{\"mixinextras\":\">=0.3\"},"
                + "\"jars\":[{\"file\":\"META-INF/jars/mixinextras.jar\"}]}").getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/jars/mixinextras.jar", nested);
        jar("host.jar", entries);
        var report = ModCompatibilityChecker.check(game, "1.20.1", LoaderType.QUILT, "0.24.0");
        assertTrue(report.issues().isEmpty(), report.issues().toString());
    }

    @Test
    void forgeTomlDependenciesAndIncompatibilities() throws IOException {
        jar("create.jar", Map.of("META-INF/mods.toml", ("modLoader=\"javafml\" # comment\n"
                + "[[mods]]\nmodId=\"create\"\nversion=\"${file.jarVersion}\"\ndescription='''\nmulti\n[[mods]]\n'''\n"
                + "[[dependencies.create]]\nmodId=\"forge\"\nmandatory=true\nversionRange=\"[47.1,)\"\n"
                + "[[dependencies.create]]\nmodId=\"minecraft\"\nmandatory=true\nversionRange=\"[1.20.1,1.20.2)\"\n"
                + "[[dependencies.create]]\nmodId=\"flywheel\"\nmandatory=true\nversionRange=\"[0.6,)\"\n"
                + "[[dependencies.create]]\nmodId=\"optifine\"\ntype=\"incompatible\"\n")
                .getBytes(StandardCharsets.UTF_8)));
        jar("optifine.jar", Map.of("META-INF/mods.toml", "[[mods]]\nmodId=\"optifine\"\nversion=\"1\"\n"
                .getBytes(StandardCharsets.UTF_8)));
        var report = ModCompatibilityChecker.check(game, "1.20.1", LoaderType.FORGE, "1.20.1-47.2.0");
        assertEquals(2, report.errorCount(), report.issues().toString());
        assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("flywheel")));
        assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("optifine")));

        var oldForge = ModCompatibilityChecker.check(game, "1.20.1", LoaderType.FORGE, "47.0.3");
        assertTrue(oldForge.issues().stream().anyMatch(i -> i.message().contains("forge")));
    }

    @Test
    void vanillaWithModsOnlyWarns() throws IOException {
        fabricJar("a.jar", "{\"id\":\"a\",\"version\":\"1\"}");
        var report = ModCompatibilityChecker.check(game, "1.20.1", LoaderType.VANILLA, "");
        assertFalse(report.hasErrors());
        assertEquals(1, report.warningCount());
    }

    @Test
    void missingModsFolderIsEmptyReport() {
        var report = ModCompatibilityChecker.check(game.resolve("none"), "1.20.1", LoaderType.FABRIC, "");
        assertTrue(report.issues().isEmpty());
        assertEquals(0, report.modsChecked());
    }
}
