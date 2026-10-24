package by.agro.launcher.modrinth;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.HashUtil;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.repair.RepairService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class MrpackInstaller {

    public static final String MANIFEST_ENTRY = "modrinth.index.json";
    private static final String OVERRIDES_PREFIX = "overrides/";
    private static final String MARKER_FILE = ".agrolauncher-modpack.json";
    private static final int MAX_PACK_FILES = 1000;
    private static final long MAX_TOTAL_DOWNLOAD_BYTES = 4L * 1024 * 1024 * 1024;
    private static final int MAX_OVERRIDE_ENTRIES = 20_000;
    private static final long MAX_OVERRIDE_BYTES = 2L * 1024 * 1024 * 1024;

    private final LauncherPaths paths;
    private final Downloader downloader;

    public MrpackInstaller(LauncherPaths paths, Downloader downloader) {
        this.paths = paths;
        this.downloader = downloader;
    }

    public static final class Installed {
        public final String name;
        public final String versionId;
        public final String minecraftVersion;
        public final LoaderType loader;
        public final String loaderVersion;
        public final Path gameDir;

        Installed(String name, String versionId, String minecraftVersion, LoaderType loader,
                  String loaderVersion, Path gameDir) {
            this.name = name;
            this.versionId = versionId;
            this.minecraftVersion = minecraftVersion;
            this.loader = loader;
            this.loaderVersion = loaderVersion;
            this.gameDir = gameDir;
        }
    }

    public Installed install(Path mrpackFile, ProgressListener listener) throws IOException {
        if (mrpackFile == null || !Files.isRegularFile(mrpackFile)) {
            throw new IOException("Modpack file not found: " + mrpackFile);
        }
        JsonObject manifest;
        try (ZipFile zip = new ZipFile(mrpackFile.toFile())) {
            ZipEntry entry = zip.getEntry(MANIFEST_ENTRY);
            if (entry == null) {
                throw new IOException("The file is not a Modrinth modpack: " + MANIFEST_ENTRY + " is missing");
            }
            try (InputStream in = zip.getInputStream(entry)) {
                manifest = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        String name = Json.string(manifest, "name", "modpack");
        String versionId = Json.string(manifest, "versionId", "");
        JsonObject dependencies = Json.object(manifest, "dependencies");
        String minecraftVersion = dependencies != null ? Json.string(dependencies, "minecraft", "") : "";
        if (minecraftVersion.isBlank()) {
            throw new IOException("The modpack manifest does not specify a Minecraft version");
        }
        LoaderType loader = LoaderType.VANILLA;
        String loaderVersion = "";
        if (dependencies != null) {
            for (String key : dependencies.keySet()) {
                String value = Json.string(dependencies, key, "");
                switch (key) {
                    case "fabric-loader": loader = LoaderType.FABRIC; loaderVersion = value; break;
                    case "quilt-loader": loader = LoaderType.QUILT; loaderVersion = value; break;
                    case "forge": loader = LoaderType.FORGE; loaderVersion = value; break;
                    case "neoforge": loader = LoaderType.NEOFORGE; loaderVersion = value; break;
                    default: break;
                }
            }
        }

        Path gameDir = paths.installationGameDir(minecraftVersion, loader.id(),
                loaderVersion.isBlank() ? "default" : loaderVersion);
        listener.onMessage(name + " " + (versionId.isBlank() ? "" : versionId)
                + " · Minecraft " + minecraftVersion + (loader == LoaderType.VANILLA ? ""
                : " · " + loader.displayName() + (loaderVersion.isBlank() ? "" : " " + loaderVersion)));
        Files.createDirectories(gameDir);

        JsonArray files = manifest.has("files") && manifest.get("files").isJsonArray()
                ? manifest.getAsJsonArray("files") : new JsonArray();
        if (files.size() > MAX_PACK_FILES) {
            throw new IOException("Modpack declares too many files: " + files.size());
        }
        long declaredTotalBytes = 0;
        int index = 0;
        for (JsonElement element : files) {
            if (!element.isJsonObject()) continue;
            JsonObject file = element.getAsJsonObject();
            String relative = Json.string(file, "path", null);
            JsonObject hashes = Json.object(file, "hashes");
            String sha1 = hashes != null ? Json.string(hashes, "sha1", null) : null;
            JsonArray downloads = file.has("downloads") && file.get("downloads").isJsonArray()
                    ? file.getAsJsonArray("downloads") : null;
            String url = downloads != null && !downloads.isEmpty()
                    && downloads.get(0).isJsonPrimitive() ? downloads.get(0).getAsString() : null;
            index++;
            if (relative == null || url == null || url.isBlank()) {
                listener.onMessage("skip: " + relative);
                continue;
            }
            Path target = safeTarget(gameDir, relative);
            long declaredSize = Json.longValue(file, "fileSize", 0);
            declaredTotalBytes += Math.max(0, declaredSize);
            if (declaredTotalBytes > MAX_TOTAL_DOWNLOAD_BYTES) {
                throw new IOException("Modpack declares more than "
                        + (MAX_TOTAL_DOWNLOAD_BYTES / (1024 * 1024)) + " MB of downloads");
            }
            listener.onProgress(name, index, files.size(), relative);
            downloader.download(url, target, sha1, declaredSize);
        }

        copyOverrides(mrpackFile, gameDir);

        JsonObject marker = new JsonObject();
        marker.addProperty("name", name);
        marker.addProperty("versionId", versionId);
        marker.addProperty("minecraftVersion", minecraftVersion);
        marker.addProperty("loader", loader.id());
        marker.addProperty("loaderVersion", loaderVersion);
        Json.write(gameDir.resolve(MARKER_FILE), marker);
        listener.onProgress(name, files.size(), files.size(), "OK");
        return new Installed(name, versionId, minecraftVersion, loader, loaderVersion, gameDir);
    }

    private void copyOverrides(Path mrpackFile, Path gameDir) throws IOException {
        try (ZipFile zip = new ZipFile(mrpackFile.toFile())) {
            var entries = zip.entries();
            int entryCount = 0;
            long totalBytes = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(OVERRIDES_PREFIX)) {
                    continue;
                }
                String relative = name.substring(OVERRIDES_PREFIX.length());
                if (relative.isBlank()) {
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_OVERRIDE_ENTRIES) {
                    throw new IOException("Modpack overrides declare too many entries");
                }
                Path target = safeTarget(gameDir, relative);
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    long copied = Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    totalBytes += copied;
                    if (totalBytes > MAX_OVERRIDE_BYTES) {
                        throw new IOException("Modpack overrides exceed the extraction size limit");
                    }
                }
            }
        }
    }

    public void export(Path targetZip, Path gameDir, String modpackName, String versionId,
                       String minecraftVersion, LoaderType loader, String loaderVersion,
                       ProgressListener listener) throws IOException {
        if (!Files.isDirectory(gameDir)) {
            throw new IOException("Game directory not found: " + gameDir);
        }
        JsonObject manifest = new JsonObject();
        manifest.addProperty("formatVersion", 1);
        manifest.addProperty("game", "minecraft");
        manifest.addProperty("versionId", versionId == null || versionId.isBlank() ? "1.0.0" : versionId);
        manifest.addProperty("name", modpackName == null || modpackName.isBlank() ? "AgroLauncher build" : modpackName);

        JsonObject dependencies = new JsonObject();
        dependencies.addProperty("minecraft", minecraftVersion);
        if (loader != null && loader != LoaderType.VANILLA && loader != LoaderType.OPTIFINE) {
            dependencies.addProperty(loaderDependencyKey(loader),
                    loaderVersion == null ? "" : loaderVersion);
        }
        manifest.add("dependencies", dependencies);

        JsonArray files = new JsonArray();
        Set<String> managedNames = new HashSet<>();
        Path metadata = gameDir.resolve(".agrolauncher-managed-mods.json");
        if (Files.isRegularFile(metadata)) {
            RepairService.ManagedMods managed = Json.read(metadata, RepairService.ManagedMods.class);
            if (managed != null && managed.mods != null) {
                for (RepairService.ManagedMod mod : managed.mods) {
                    if (mod == null || mod.fileName == null || mod.url == null || mod.sha1 == null) continue;
                    JsonObject file = new JsonObject();
                    file.addProperty("path", "mods/" + mod.fileName);
                    JsonObject hashes = new JsonObject();
                    hashes.addProperty("sha1", mod.sha1);
                    file.add("hashes", hashes);
                    JsonArray urls = new JsonArray();
                    urls.add(mod.url);
                    file.add("downloads", urls);
                    file.addProperty("fileSize", mod.size);
                    file.add("env", defaultEnv());
                    files.add(file);
                    managedNames.add(mod.fileName);
                    managedNames.add(mod.fileName + ".disabled");
                }
            }
        }
        manifest.add("files", files);

        Path modsDir = gameDir.resolve("mods");
        List<Path> extraFiles = new ArrayList<>();
        if (Files.isDirectory(modsDir, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(modsDir)) {
            try (var stream = Files.list(modsDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String fileName = file.getFileName().toString();
                    String lower = fileName.toLowerCase(java.util.Locale.ROOT);
                    if (!lower.endsWith(".jar") && !lower.endsWith(".jar.disabled")) continue;
                    if (managedNames.contains(fileName)) continue;
                    extraFiles.add(file);
                }
            }
        }

        Path temporary = targetZip.toAbsolutePath().resolveSibling(
                "." + targetZip.getFileName() + ".tmp");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary),
                StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zip.write(Json.GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Path extra : extraFiles) {
                String relative = "overrides/mods/" + extra.getFileName();
                zip.putNextEntry(new ZipEntry(relative));
                try (InputStream in = Files.newInputStream(extra)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
        by.agro.launcher.core.SecureFiles.atomicReplace(temporary, targetZip.toAbsolutePath().normalize());
        listener.onMessage("mrpack: " + files.size() + " + " + extraFiles.size() + " overrides");
    }

    private static JsonObject defaultEnv() {
        JsonObject env = new JsonObject();
        env.addProperty("client", "required");
        env.addProperty("server", "unsupported");
        return env;
    }

    private static String loaderDependencyKey(LoaderType loader) {
        switch (loader) {
            case FABRIC: return "fabric-loader";
            case QUILT: return "quilt-loader";
            case FORGE: return "forge";
            case NEOFORGE: return "neoforge";
            default: return "minecraft";
        }
    }

    private static Path safeTarget(Path root, String relative) throws IOException {
        Path parsed;
        try {
            parsed = Path.of(relative);
        } catch (RuntimeException e) {
            throw new IOException("Invalid modpack path: " + relative);
        }
        if (parsed.isAbsolute()) {
            throw new IOException("Absolute modpack path is forbidden: " + relative);
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path result = normalizedRoot.resolve(parsed).normalize();
        if (!result.startsWith(normalizedRoot) || result.equals(normalizedRoot)) {
            throw new IOException("Modpack path escapes the game directory: " + relative);
        }
        by.agro.launcher.core.SecureFiles.rejectSymlinkParents(result);
        return result;
    }
}
