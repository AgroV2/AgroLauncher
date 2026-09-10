package by.agro.launcher.loaders;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.jvm.JavaManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

public final class OptiFineInstaller implements LoaderInstaller {

    private static final String VERSION_LIST_URL = "https://bmclapi2.bangbang93.com/optifine/versionlist";
    private static final String LAUNCHWRAPPER_ENTRY_PREFIX = "launchwrapper-of-";
    private static final long PATCH_TIMEOUT_MINUTES = 10;

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final JavaManager javaManager;

    public OptiFineInstaller(LauncherPaths paths, Downloader downloader, JavaManager javaManager) {
        this.paths = paths;
        this.downloader = downloader;
        this.javaManager = javaManager;
    }

    @Override
    public LoaderType type() {
        return LoaderType.OPTIFINE;
    }

    @Override
    public List<LoaderVersion> availableVersions(String minecraftVersion) throws IOException {
        String body = downloader.getString(VERSION_LIST_URL);
        JsonArray array = Json.parse(body).getAsJsonArray();

        List<LoaderVersion> result = new ArrayList<>();
        boolean first = true;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String mcVersion = Json.string(entry, "mcversion", null);
            if (mcVersion == null || !mcVersion.equals(minecraftVersion)) {
                continue;
            }
            String patch = Json.string(entry, "patch", "");
            String kind = Json.string(entry, "type", "HD_U");
            String edition = kind + "_" + patch;
            boolean stable = !patch.toLowerCase().contains("pre");
            boolean recommended = stable && first;
            if (recommended) {
                first = false;
            }
            result.add(new LoaderVersion(edition, stable, recommended));
        }
        return result;
    }

    @Override
    public String install(String minecraftVersion, String loaderVersion, ProgressListener listener)
            throws IOException {
        String edition = loaderVersion;
        if (edition == null || edition.isBlank()) {
            List<LoaderVersion> versions = availableVersions(minecraftVersion);
            if (versions.isEmpty()) {
                throw new IOException("OptiFine was not found for Minecraft " + minecraftVersion);
            }
            edition = versions.get(0).version;
        }

        String fileName = "OptiFine_" + minecraftVersion + "_" + edition + ".jar";
        Path installerJar = paths.installersDir().resolve(fileName);

        if (!Files.exists(installerJar)) {
            listener.onProgress("Установка OptiFine", 0, 4, "загрузка " + fileName);
            downloadOptiFine(fileName, installerJar, listener);
        }
        return installFromJar(minecraftVersion, edition, installerJar, listener);
    }

    public String installFromJar(String minecraftVersion, String edition, Path installerJar,
                                 ProgressListener listener) throws IOException {
        if (!Files.exists(installerJar)) {
            throw new IOException("OptiFine file not found: " + installerJar);
        }
        if (edition == null || edition.isBlank()) {
            edition = guessEdition(installerJar.getFileName().toString());
        }

        Path vanillaJar = paths.versionJar(minecraftVersion);
        if (!Files.exists(vanillaJar)) {
            throw new IOException("Install vanilla version " + minecraftVersion + " first; file not found: "
                    + vanillaJar);
        }

        String versionId = minecraftVersion + "-OptiFine_" + edition;
        String optifineLibCoords = "optifine:OptiFine:" + minecraftVersion + "_" + edition;
        Path optifineLib = paths.libraryPath(optifineLibCoords);

        listener.onProgress("Установка OptiFine", 1, 4, "патчинг клиента");
        Files.createDirectories(optifineLib.getParent());
        runPatcher(installerJar, vanillaJar, optifineLib, listener);

        listener.onProgress("Установка OptiFine", 2, 4, "извлечение launchwrapper");
        String launchwrapperCoords = extractLaunchwrapper(installerJar, listener);

        listener.onProgress("Установка OptiFine", 3, 4, "создание профиля");
        JsonObject profile = buildProfile(versionId, minecraftVersion, optifineLibCoords, launchwrapperCoords);
        Path target = paths.versionJson(versionId);
        Files.createDirectories(target.getParent());
        Json.write(target, profile);

        listener.onProgress("Установка OptiFine", 4, 4, versionId);
        listener.onMessage("OptiFine установлен: " + versionId);
        return versionId;
    }

    private void downloadOptiFine(String fileName, Path target, ProgressListener listener) throws IOException {
        throw new IOException("OptiFine does not publish a cryptographic digest for executable JAR "
                + fileName + "; automatic download is disabled. Select a trusted local JAR instead.");
    }

    private void runPatcher(Path installerJar, Path vanillaJar, Path outputJar, ProgressListener listener)
            throws IOException {
        String javaExe = javaManager.resolveJavaForInstaller();
        List<String> command = List.of(
                javaExe,
                "-cp", installerJar.toAbsolutePath().toString(),
                "optifine.Patcher",
                vanillaJar.toAbsolutePath().toString(),
                installerJar.toAbsolutePath().toString(),
                outputJar.toAbsolutePath().toString()
        );

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        StringBuilder tail = new StringBuilder();
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int counter = 0;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Same:") || line.startsWith("Patching:") || line.startsWith("Copy:")) {
                    if (++counter % 500 == 0) {
                        listener.onProgress("Патчинг OptiFine", counter, 0, "обработано: " + counter);
                    }
                    continue;
                }
                if (!line.isBlank()) {
                    listener.onMessage("[optifine] " + line);
                    tail.append(line).append('\n');
                }
            }
        }

        try {
            if (!process.waitFor(PATCH_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("OptiFine patching timed out");
            }
            if (process.exitValue() != 0) {
                throw new IOException("optifine.Patcher exited with code "
                        + process.exitValue() + "\n" + tail);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Patching was interrupted", e);
        }

        if (!Files.exists(outputJar) || Files.size(outputJar) < 100_000) {
            throw new IOException("OptiFine patching did not produce a valid library: " + outputJar);
        }
    }

    private String extractLaunchwrapper(Path installerJar, ProgressListener listener) throws IOException {
        try (ZipFile zip = new ZipFile(installerJar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.contains("/")) {
                    continue;
                }
                if (!name.startsWith(LAUNCHWRAPPER_ENTRY_PREFIX) || !name.endsWith(".jar")) {
                    continue;
                }
                String version = name.substring(LAUNCHWRAPPER_ENTRY_PREFIX.length(), name.length() - 4);
                String coords = "optifine:launchwrapper-of:" + version;
                Path target = paths.libraryPath(coords);
                Files.createDirectories(target.getParent());
                if (!Files.exists(target)) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                listener.onMessage("launchwrapper извлечён: " + version);
                return coords;
            }
        }
        listener.onMessage("Собственный launchwrapper не найден, используется net.minecraft:launchwrapper:1.12");
        return "net.minecraft:launchwrapper:1.12";
    }

    private JsonObject buildProfile(String versionId, String minecraftVersion,
                                    String optifineCoords, String launchwrapperCoords) {
        JsonObject profile = new JsonObject();
        profile.addProperty("id", versionId);
        profile.addProperty("inheritsFrom", minecraftVersion);
        profile.addProperty("type", "release");
        profile.addProperty("mainClass", "net.minecraft.launchwrapper.Launch");

        JsonArray libraries = new JsonArray();
        libraries.add(libraryEntry(optifineCoords));
        libraries.add(libraryEntry(launchwrapperCoords));
        profile.add("libraries", libraries);

        JsonObject arguments = new JsonObject();
        JsonArray game = new JsonArray();
        game.add("--tweakClass");
        game.add("optifine.OptiFineTweaker");
        arguments.add("game", game);
        arguments.add("jvm", new JsonArray());
        profile.add("arguments", arguments);

        profile.addProperty("minecraftArguments",
                "--tweakClass optifine.OptiFineTweaker");

        return profile;
    }

    private JsonObject libraryEntry(String coords) {
        JsonObject library = new JsonObject();
        library.addProperty("name", coords);
        return library;
    }

    private String guessEdition(String fileName) {
        String name = fileName;
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.startsWith("preview_")) {
            name = name.substring("preview_".length());
        }
        if (name.startsWith("OptiFine_")) {
            name = name.substring("OptiFine_".length());
        }
        int index = name.indexOf('_');
        return index >= 0 ? name.substring(index + 1) : name;
    }

    public void installAsForgeMod(Path installerJar, Path modsDir, ProgressListener listener) throws IOException {
        Files.createDirectories(modsDir);
        Path target = modsDir.resolve(installerJar.getFileName());
        Files.copy(installerJar, target, StandardCopyOption.REPLACE_EXISTING);
        listener.onMessage("OptiFine добавлен в mods: " + target.getFileName());
    }
}
