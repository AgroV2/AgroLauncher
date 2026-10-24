package by.agro.launcher.launch;

import by.agro.launcher.auth.Account;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Platform;
import by.agro.launcher.core.Settings;
import by.agro.launcher.version.ResolvedVersion;
import by.agro.launcher.version.VersionResolver;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchCommandBuilderRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    void inheritedForgeClasspathContainsBaseClientJarExactlyOnce() throws Exception {
        LauncherPaths paths = LauncherPaths.forRoot(tempDir.resolve("data"));
        paths.ensureDirectories();

        JsonObject base = new JsonObject();
        base.addProperty("id", "1.12.2");
        base.addProperty("mainClass", "net.minecraft.client.main.Main");
        base.addProperty("minecraftArguments", "--username ${auth_player_name}");
        JsonObject javaVersion = new JsonObject();
        javaVersion.addProperty("majorVersion", 8);
        base.add("javaVersion", javaVersion);
        base.add("libraries", new com.google.gson.JsonArray());

        JsonObject forge = new JsonObject();
        forge.addProperty("id", "1.12.2-forge-14.23.5.2859");
        forge.addProperty("inheritsFrom", "1.12.2");
        forge.addProperty("mainClass", "net.minecraft.launchwrapper.Launch");
        forge.add("libraries", new com.google.gson.JsonArray());

        VersionResolver resolver = new VersionResolver(paths, new by.agro.launcher.core.Downloader());
        resolver.writeVersionJson("1.12.2", base);
        resolver.writeVersionJson("1.12.2-forge-14.23.5.2859", forge);
        Path clientJar = paths.versionJar("1.12.2");
        Files.createDirectories(clientJar.getParent());
        Files.write(clientJar, new byte[]{1});

        ResolvedVersion version = resolver.resolve("1.12.2-forge-14.23.5.2859", null,
                by.agro.launcher.core.ProgressListener.NOOP, true);
        LaunchOptions options = new LaunchOptions();
        options.version = version;
        options.javaExecutable = "java";
        options.settings = new Settings();
        options.account = Account.offline("Player");
        options.nativesDir = tempDir.resolve("natives");
        options.gameDir = tempDir.resolve("game");
        options.assetsDir = paths.assetsDir();
        options.launcherVersion = "test";

        List<String> command = new LaunchCommandBuilder(paths).build(options);
        String classpath = command.get(command.indexOf("-cp") + 1);
        String expected = clientJar.toAbsolutePath().normalize().toString();
        assertTrue(List.of(classpath.split(java.util.regex.Pattern.quote(Platform.classpathSeparator())))
                .contains(expected));
        assertEquals(1, List.of(classpath.split(java.util.regex.Pattern.quote(Platform.classpathSeparator())))
                .stream().filter(expected::equals).count());
    }

    @Test
    void quickPlayServerAddsServerAndPortArguments() throws Exception {
        LauncherPaths paths = LauncherPaths.forRoot(tempDir.resolve("quickplay"));
        paths.ensureDirectories();

        JsonObject base = new JsonObject();
        base.addProperty("id", "1.21.1");
        base.addProperty("mainClass", "net.minecraft.client.main.Main");
        base.addProperty("minecraftArguments", "--username ${auth_player_name}");
        base.add("libraries", new com.google.gson.JsonArray());

        VersionResolver resolver = new VersionResolver(paths, new by.agro.launcher.core.Downloader());
        resolver.writeVersionJson("1.21.1", base);

        ResolvedVersion version = resolver.resolve("1.21.1", null,
                by.agro.launcher.core.ProgressListener.NOOP, true);
        LaunchOptions options = baseOptions(version, paths);
        options.quickPlayServer = "play.example.com:25565";

        List<String> command = new LaunchCommandBuilder(paths).build(options);
        int serverIndex = command.indexOf("--server");
        assertTrue(serverIndex > 0, "--server must be present, command: " + command);
        assertEquals("play.example.com", command.get(serverIndex + 1));
        int portIndex = command.indexOf("--port");
        assertTrue(portIndex > 0, "--port must be present, command: " + command);
        assertEquals("25565", command.get(portIndex + 1));
    }

    @Test
    void quickPlayServerWithoutPortOmitsPortArgument() throws Exception {
        LauncherPaths paths = LauncherPaths.forRoot(tempDir.resolve("quickplay-noport"));
        paths.ensureDirectories();

        JsonObject base = new JsonObject();
        base.addProperty("id", "1.21.1");
        base.addProperty("mainClass", "net.minecraft.client.main.Main");
        base.addProperty("minecraftArguments", "--username ${auth_player_name}");
        base.add("libraries", new com.google.gson.JsonArray());

        VersionResolver resolver = new VersionResolver(paths, new by.agro.launcher.core.Downloader());
        resolver.writeVersionJson("1.21.1", base);

        ResolvedVersion version = resolver.resolve("1.21.1", null,
                by.agro.launcher.core.ProgressListener.NOOP, true);
        LaunchOptions options = baseOptions(version, paths);
        options.quickPlayServer = "play.example.com";

        List<String> command = new LaunchCommandBuilder(paths).build(options);
        assertTrue(command.contains("--server"), "--server must be present");
        assertEquals(-1, command.indexOf("--port"), "--port must be absent for an address without a port");
    }

    private LaunchOptions baseOptions(ResolvedVersion version, LauncherPaths paths) {
        LaunchOptions options = new LaunchOptions();
        options.version = version;
        options.javaExecutable = "java";
        options.settings = new Settings();
        options.account = Account.offline("Player");
        options.nativesDir = tempDir.resolve("natives");
        options.gameDir = tempDir.resolve("game");
        options.assetsDir = paths.assetsDir();
        options.launcherVersion = "test";
        return options;
    }
}
