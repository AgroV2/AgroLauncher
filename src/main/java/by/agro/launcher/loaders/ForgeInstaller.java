package by.agro.launcher.loaders;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.HashUtil;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.jvm.JavaManager;
import by.agro.launcher.version.RemoteVersion;
import by.agro.launcher.version.VersionManifest;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class ForgeInstaller implements LoaderInstaller {

    private static final String PROMOTIONS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";
    private static final String MAVEN_BASE =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final long INSTALL_TIMEOUT_MINUTES = 15;

    protected final LauncherPaths paths;
    protected final Downloader downloader;
    protected final JavaManager javaManager;

    public ForgeInstaller(LauncherPaths paths, Downloader downloader, JavaManager javaManager) {
        this.paths = paths;
        this.downloader = downloader;
        this.javaManager = javaManager;
    }

    @Override
    public LoaderType type() {
        return LoaderType.FORGE;
    }

    @Override
    public List<LoaderVersion> availableVersions(String minecraftVersion) throws IOException {
        String body = downloader.getString(PROMOTIONS_URL);
        JsonObject root = Json.parseObject(body);
        JsonObject promos = Json.object(root, "promos");
        if (promos == null) {
            return List.of();
        }

        String recommended = Json.string(promos, minecraftVersion + "-recommended", null);
        String latest = Json.string(promos, minecraftVersion + "-latest", null);

        List<LoaderVersion> result = new ArrayList<>();
        if (recommended != null) {
            result.add(new LoaderVersion(recommended, true, true));
        }
        if (latest != null && !latest.equals(recommended)) {
            result.add(new LoaderVersion(latest, true, false));
        }
        return result;
    }

    public List<LoaderVersion> allVersions(String minecraftVersion) throws IOException {
        String xml = downloader.getString(MAVEN_BASE + "maven-metadata.xml");
        List<String> versions = parseMavenVersions(xml);

        String prefix = minecraftVersion + "-";
        List<LoaderVersion> result = new ArrayList<>();
        for (String version : versions) {
            if (version.startsWith(prefix)) {
                String forgeVersion = version.substring(prefix.length());
                result.add(new LoaderVersion(forgeVersion, true, false));
            }
        }
        result.sort(Comparator.comparing((LoaderVersion v) -> v.version).reversed());
        return result;
    }

    @Override
    public String install(String minecraftVersion, String loaderVersion, ProgressListener listener)
            throws IOException {
        String forgeVersion = loaderVersion;
        if (forgeVersion == null || forgeVersion.isBlank()) {
            List<LoaderVersion> versions = availableVersions(minecraftVersion);
            if (versions.isEmpty()) {
                throw new IOException("Forge не найден для Minecraft " + minecraftVersion);
            }
            forgeVersion = versions.get(0).version;
        }

        String fullVersion = buildFullVersion(minecraftVersion, forgeVersion);
        listener.onMessage(type().displayName() + " " + fullVersion);

        Path installer = paths.installersDir()
                .resolve(type().id() + "-" + fullVersion + "-installer.jar");
        String installerUrl = installerUrl(fullVersion);
        listener.onProgress("Установка " + type().displayName(), 0, 3, "загрузка установщика");
        downloader.download(installerUrl, installer);

        Path gameDir = paths.gameDir();
        Files.createDirectories(gameDir);
        ensureLauncherProfiles(gameDir);

        ensureVanillaClient(minecraftVersion, listener);

        listener.onProgress("Установка " + type().displayName(), 1, 3, "запуск установщика");
        String javaExe = javaManager.resolveJavaForInstaller();
        try {
            runInstaller(javaExe, installer, gameDir, listener);
        } catch (ProcessorMismatchException e) {
            listener.onMessage("Промежуточные файлы не совпали с эталоном — очищаем и повторяем");
            cleanupPatchedArtifacts(minecraftVersion, listener);
            ensureVanillaClient(minecraftVersion, listener);
            runInstaller(javaExe, installer, gameDir, listener);
        }

        listener.onProgress("Установка " + type().displayName(), 2, 3, "поиск профиля");
        String versionId = findInstalledProfile(minecraftVersion, forgeVersion);
        if (versionId == null) {
            throw new IOException("Установщик " + type().displayName()
                    + " завершился, но профиль версии не найден в " + paths.versionsDir());
        }

        listener.onProgress("Установка " + type().displayName(), 3, 3, versionId);
        listener.onMessage("Профиль установлен: " + versionId);
        return versionId;
    }

    protected String installerUrl(String fullVersion) {
        return MAVEN_BASE + fullVersion + "/forge-" + fullVersion + "-installer.jar";
    }

    protected String buildFullVersion(String minecraftVersion, String forgeVersion) {
        if (forgeVersion.startsWith(minecraftVersion + "-")) {
            return forgeVersion;
        }
        return minecraftVersion + "-" + forgeVersion;
    }

    protected void ensureVanillaClient(String minecraftVersion, ProgressListener listener)
            throws IOException {
        Path versionJson = paths.versionJson(minecraftVersion);
        Path versionJar = paths.versionJar(minecraftVersion);

        RemoteVersion remote;
        try {
            remote = VersionManifest.fetch(downloader).byId(minecraftVersion);
        } catch (IOException e) {
            if (Files.exists(versionJar)) {
                listener.onMessage("Список версий недоступен — проверка ванильного клиента пропущена");
                return;
            }
            throw e;
        }
        if (remote == null || remote.url == null) {
            return;
        }

        listener.onProgress("Проверка ванильного клиента", 0, 1, minecraftVersion + ".jar");
        Files.createDirectories(versionJar.getParent());
        downloader.download(remote.url, versionJson, remote.sha1);

        JsonObject root = Json.parseObject(Files.readString(versionJson, StandardCharsets.UTF_8));
        JsonObject client = Json.object(Json.object(root, "downloads"), "client");
        if (client == null) {
            return;
        }
        String url = Json.string(client, "url", null);
        String sha1 = Json.string(client, "sha1", null);
        if (url == null || sha1 == null) {
            return;
        }

        boolean wasBroken = Files.exists(versionJar) && !HashUtil.verify(versionJar, sha1);
        if (wasBroken) {
            listener.onMessage("Ванильный " + minecraftVersion
                    + ".jar повреждён или изменён — скачиваем заново");
            Files.deleteIfExists(versionJar);
        }
        downloader.download(url, versionJar, sha1);
        listener.onProgress("Проверка ванильного клиента", 1, 1,
                wasBroken ? "файл восстановлен" : "файл в порядке");
    }

    protected void cleanupPatchedArtifacts(String minecraftVersion, ProgressListener listener) {
        Path clientLibs = paths.librariesDir()
                .resolve("net").resolve("minecraft").resolve("client");
        if (!Files.isDirectory(clientLibs)) {
            return;
        }
        int removed = 0;
        try (var dirs = Files.list(clientLibs)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                if (!dir.getFileName().toString().startsWith(minecraftVersion)) {
                    continue;
                }
                try (var files = Files.list(dir)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Files.deleteIfExists(file);
                        removed++;
                    }
                }
            }
        } catch (IOException e) {
            listener.onMessage("Не удалось очистить промежуточные файлы: " + e.getMessage());
            return;
        }
        listener.onMessage("Удалено промежуточных файлов: " + removed);
    }

    protected static final class ProcessorMismatchException extends IOException {
        ProcessorMismatchException(String message) {
            super(message);
        }
    }

    protected void ensureLauncherProfiles(Path gameDir) throws IOException {
        Path file = gameDir.resolve("launcher_profiles.json");
        if (Files.exists(file)) {
            return;
        }
        JsonObject root = new JsonObject();
        root.add("profiles", new JsonObject());
        root.addProperty("selectedProfile", "");
        root.addProperty("clientToken", "");
        root.add("authenticationDatabase", new JsonObject());
        JsonObject launcherVersion = new JsonObject();
        launcherVersion.addProperty("name", "2.1.1");
        launcherVersion.addProperty("format", 21);
        root.add("launcherVersion", launcherVersion);
        Json.write(file, root);

        Path msStore = gameDir.resolve("launcher_profiles_microsoft_store.json");
        if (!Files.exists(msStore)) {
            Json.write(msStore, root);
        }
    }

    protected void runInstaller(String javaExe, Path installer, Path gameDir, ProgressListener listener)
            throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaExe);
        command.add("-Djava.awt.headless=true");
        command.add("-jar");
        command.add(installer.toAbsolutePath().toString());
        command.add("--installClient");
        command.add(gameDir.toAbsolutePath().toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(gameDir.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        StringBuilder tail = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int counter = 0;
            while ((line = reader.readLine()) != null) {
                if (isPerFileNoise(line)) {
                    if (++counter % 500 == 0) {
                        listener.onProgress("Патчинг клиента", counter, 0,
                                "файлов обработано: " + counter);
                    }
                    continue;
                }
                if (!line.isBlank()) {
                    listener.onMessage("[installer] " + line);
                }
                tail.append(line).append('\n');
                if (tail.length() > 8000) {
                    tail.delete(0, tail.length() - 8000);
                }
            }
        }

        int exitCode;
        try {
            if (!process.waitFor(INSTALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("Установщик " + type().displayName() + " превысил лимит времени");
            }
            exitCode = process.exitValue();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Установка прервана", e);
        }

        if (exitCode != 0) {
            String output = tail.toString();
            if (output.contains("invalid outputs")) {
                throw new ProcessorMismatchException("Установщик " + type().displayName()
                        + " собрал файлы с неверными хешами");
            }
            throw new IOException("Установщик " + type().displayName()
                    + " завершился с кодом " + exitCode + ". Вывод:\n" + output);
        }
    }

    private static boolean isPerFileNoise(String line) {
        if (!line.startsWith("  ")) {
            return false;
        }
        String text = line.strip();
        for (String prefix : new String[]{"Patching ", "Slim ", "Data ", "Extra ", "Copying "}) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    protected String findInstalledProfile(String minecraftVersion, String loaderVersion) throws IOException {
        Path versionsDir = paths.versionsDir();
        if (!Files.isDirectory(versionsDir)) {
            return null;
        }
        Map<Long, String> candidates = new TreeMap<>(Comparator.reverseOrder());
        try (var stream = Files.list(versionsDir)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                String name = dir.getFileName().toString();
                Path json = dir.resolve(name + ".json");
                if (!Files.exists(json)) {
                    continue;
                }
                String lower = name.toLowerCase();
                boolean matchesLoader = lower.contains(type().id());
                boolean matchesVersion = loaderVersion == null || name.contains(loaderVersion);
                boolean matchesMc = !requiresMcVersionInName() || name.contains(minecraftVersion);
                if (matchesLoader && matchesMc && matchesVersion) {
                    candidates.put(Files.getLastModifiedTime(json).toMillis(), name);
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.values().iterator().next();
    }

    protected boolean requiresMcVersionInName() {
        return true;
    }

    protected static List<String> parseMavenVersions(String xml) {
        List<String> versions = new ArrayList<>();
        int index = 0;
        while (true) {
            int start = xml.indexOf("<version>", index);
            if (start < 0) {
                break;
            }
            int end = xml.indexOf("</version>", start);
            if (end < 0) {
                break;
            }
            versions.add(xml.substring(start + "<version>".length(), end).trim());
            index = end + 1;
        }
        return versions;
    }
}
