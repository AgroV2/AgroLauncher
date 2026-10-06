package by.agro.launcher.repair;

import by.agro.launcher.backup.BackupService;
import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.SecureFiles;
import by.agro.launcher.integrity.InstallationManifest;
import by.agro.launcher.integrity.IntegrityService;
import by.agro.launcher.integrity.ManifestEntry;
import by.agro.launcher.integrity.ManifestStore;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.loaders.LoaderInstaller;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.modrinth.ModrinthVersion;
import by.agro.launcher.version.RemoteVersion;
import by.agro.launcher.version.VersionManifest;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RepairService {

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final IntegrityService integrity = new IntegrityService();
    private final ManifestStore manifestStore = new ManifestStore();

    public RepairService(LauncherPaths paths, Downloader downloader) {
        this.paths = paths;
        this.downloader = downloader;
    }

    public static final class Request {
        public String minecraftVersion;
        public LoaderType loaderType = LoaderType.VANILLA;
        public String loaderVersion = "";
        public LoaderInstaller loaderInstaller;
        public VersionManifest manifest;
        public Path gameDir;
    }

    public static final class Result {
        public final String versionId;
        public final int managedMods;

        Result(String versionId, int managedMods) {
            this.versionId = versionId;
            this.managedMods = managedMods;
        }
    }

    public Result repair(Request request, ProgressListener listener) throws IOException {
        validateRequest(request);
        Path gameRoot = requireSafeRoot(request.gameDir, "Game directory");
        requireSafeRoot(paths.gameDir(), "Minecraft data directory");
        Path backup = new BackupService(paths.root().resolve("backups")).create(gameRoot);
        listener.onMessage(Strings.get("repair.backupCreated", backup.getFileName()));

        InstallationManifest installation = manifestStore.read(paths.root());
        listener.onProgress(Strings.get("repair.stageMinecraft"), 0, 3, request.minecraftVersion);
        int repairedMods;
        try (RepairTransaction transaction = new RepairTransaction(
                paths.root(), paths.root().resolve("transactions"), integrity)) {
            stageVanilla(request.minecraftVersion, request.manifest, transaction, installation);
            repairedMods = stageManagedMods(gameRoot, transaction, installation, listener);
            transaction.commit();
        }

        String versionId = request.minecraftVersion;
        if (request.loaderType != LoaderType.VANILLA) {
            if (request.loaderInstaller == null) {
                throw new IOException("Installer is unavailable for " + request.loaderType.displayName());
            }
            listener.onProgress(Strings.get("repair.stageLoader"), 1, 3, request.loaderType.displayName());
            versionId = installLoaderWithRollback(request, installation, listener);
        }

        recordManagedJre(installation);
        manifestStore.write(paths.root(), installation);
        listener.onProgress(Strings.get("repair.stageCompleted"), 3, 3, versionId);
        return new Result(versionId, repairedMods);
    }

    private void stageVanilla(String versionId, VersionManifest manifest, RepairTransaction transaction,
                              InstallationManifest installation) throws IOException {
        RemoteVersion remote = manifest.byId(versionId);
        if (remote == null || remote.url == null || remote.url.isBlank()) {
            throw new IOException("Minecraft version is absent from the Mojang manifest: " + versionId);
        }
        String jsonPath = relativeToRoot(paths.versionJson(versionId));
        ManifestEntry jsonPlaceholder = new ManifestEntry(jsonPath, "pending", 0, "mojang", "observed", remote.url);
        Path stagedJson = transaction.stagePath(jsonPlaceholder);
        downloader.download(remote.url, stagedJson, remote.sha1);
        ManifestEntry jsonEntry = observedStaged(stagedJson, jsonPath, "mojang", remote.url);
        transaction.addStaged(jsonEntry);

        JsonObject root = Json.readObject(stagedJson);
        JsonObject client = Json.object(Json.object(root, "downloads"), "client");
        String url = Json.string(client, "url", null);
        String sha1 = Json.string(client, "sha1", null);
        long size = Json.longValue(client, "size", 0);
        if (url == null || sha1 == null || sha1.isBlank()) {
            throw new IOException("Mojang did not provide a verified client JAR for " + versionId);
        }
        String jarPath = relativeToRoot(paths.versionJar(versionId));
        ManifestEntry jarPlaceholder = new ManifestEntry(jarPath, "pending", size, "mojang", "observed", url);
        Path stagedJar = transaction.stagePath(jarPlaceholder);
        downloader.download(url, stagedJar, sha1, size);
        ManifestEntry jarEntry = observedStaged(stagedJar, jarPath, "mojang", url);
        transaction.addStaged(jarEntry);
        replaceEntry(installation, jsonEntry);
        replaceEntry(installation, jarEntry);
    }

    private int stageManagedMods(Path gameRoot, RepairTransaction transaction,
                                 InstallationManifest installation, ProgressListener listener) throws IOException {
        Path metadata = requireManagedPath(gameRoot, gameRoot.resolve(".agrolauncher-managed-mods.json"),
                "Managed-mod metadata");
        if (!Files.exists(metadata, LinkOption.NOFOLLOW_LINKS)) return 0;
        rejectExistingLink(metadata, "Managed-mod metadata");
        ManagedMods managed = Json.read(metadata, ManagedMods.class);
        if (managed == null || managed.mods == null) return 0;

        Path modsRoot = requireManagedPath(gameRoot, gameRoot.resolve("mods"), "Mods directory");
        Map<String, Path> snapshot = snapshotModFiles(modsRoot);
        int done = 0;
        for (ManagedMod mod : managed.mods) {
            if (mod == null || mod.fileName == null || mod.url == null || mod.sha1 == null) continue;
            Path metadataName = safeChild(modsRoot, mod.fileName, "Managed mod");
            String baseName = metadataName.getFileName().toString();
            Path enabled = snapshot.get(baseName.toLowerCase(Locale.ROOT));
            Path disabled = snapshot.get((baseName + ".disabled").toLowerCase(Locale.ROOT));
            if (enabled != null && disabled != null) {
                listener.onMessage(Strings.get("repair.modConflict",
                        enabled.getFileName(), disabled.getFileName()));
                continue;
            }
            Path target = enabled != null ? enabled : disabled;
            if (target == null) continue;

            String relative = relativeToRoot(target);
            ManifestEntry placeholder = new ManifestEntry(relative, "pending", mod.size,
                    "modrinth", "observed", mod.url);
            Path staged = transaction.stagePath(placeholder);
            downloader.download(mod.url, staged, mod.sha1, mod.size);
            ManifestEntry entry = observedStaged(staged, relative, "modrinth", mod.url);
            transaction.addStaged(entry);
            replaceEntry(installation, entry);
            done++;
            listener.onProgress(Strings.get("repair.stageManagedMods"), done, managed.mods.size(),
                    target.getFileName().toString());
        }
        return done;
    }

    static Map<String, Path> snapshotModFiles(Path modsRoot) throws IOException {
        Map<String, Path> result = new HashMap<>();
        if (!Files.isDirectory(modsRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(modsRoot)) {
            return result;
        }
        try (var files = Files.list(modsRoot)) {
            for (Path file : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(path)).toList()) {
                String name = file.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".jar") || lower.endsWith(".jar.disabled")) {
                    result.put(lower, file);
                }
            }
        }
        return result;
    }

    static List<Path> selectManagedModCandidates(ManagedMods managed, Map<String, Path> snapshot) {
        List<Path> result = new ArrayList<>();
        if (managed == null || managed.mods == null || snapshot == null) return result;
        for (ManagedMod mod : managed.mods) {
            if (mod == null || mod.fileName == null) continue;
            String key = mod.fileName.toLowerCase(Locale.ROOT);
            Path enabled = snapshot.get(key);
            Path disabled = snapshot.get((mod.fileName + ".disabled").toLowerCase(Locale.ROOT));
            if ((enabled == null) != (disabled == null)) {
                result.add(enabled != null ? enabled : disabled);
            }
        }
        return result;
    }

    private ManifestEntry observedStaged(Path staged, String relative, String ownership,
                                         String sourceUrl) throws IOException {
        Path stageRoot = staged;
        for (int i = 0; i < Path.of(relative).getNameCount(); i++) stageRoot = stageRoot.getParent();
        ManifestEntry entry = integrity.observe(stageRoot, staged, ownership, sourceUrl);
        entry.path = relative;
        return entry;
    }

    private String installLoaderWithRollback(Request request, InstallationManifest installation,
                                             ProgressListener listener) throws IOException {
        Path snapshot = paths.root().resolve("transactions").resolve("loader-" + System.nanoTime());
        Files.createDirectories(snapshot);
        snapshotKnownLoaderOutputs(snapshot);
        try {
            String versionId = request.loaderInstaller.forceInstall(request.minecraftVersion,
                    request.loaderVersion == null || request.loaderVersion.isBlank()
                            ? null : request.loaderVersion, listener);
            Path profile = paths.versionJson(versionId);
            rejectExistingLink(profile, "Generated loader profile");
            JsonObject parsed = Json.readObject(profile);
            if (Json.string(parsed, "mainClass", null) == null) {
                throw new IOException("Generated loader profile has no mainClass: " + profile);
            }
            replaceEntry(installation, integrity.observe(paths.root(), profile, "loader", null));
            Path marker = newestLoaderMarker();
            if (marker == null) throw new IOException("Loader installation marker was not generated");
            replaceEntry(installation, integrity.observe(paths.root(), marker, "loader", null));
            return versionId;
        } catch (IOException | RuntimeException failure) {
            try { restoreKnownLoaderOutputs(snapshot); }
            catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        }
    }

    private void snapshotKnownLoaderOutputs(Path snapshot) throws IOException {
        copyRegularTree(paths.versionsDir(), snapshot.resolve("versions"));
        if (Files.isDirectory(paths.installersDir())) {
            try (var files = Files.list(paths.installersDir())) {
                for (Path marker : files.filter(p -> p.getFileName().toString().endsWith(".installed.json")).toList()) {
                    copyRegular(marker, snapshot.resolve("markers").resolve(marker.getFileName()));
                }
            }
        }
    }

    private void restoreKnownLoaderOutputs(Path snapshot) throws IOException {
        Path versions = snapshot.resolve("versions");
        if (Files.isDirectory(versions)) copyRegularTree(versions, paths.versionsDir());
        Path markers = snapshot.resolve("markers");
        if (Files.isDirectory(markers)) copyRegularTree(markers, paths.installersDir());
    }

    private void copyRegularTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) return;
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(p)).toList()) {
                copyRegular(file, target.resolve(source.relativize(file)));
            }
        }
    }

    private void copyRegular(Path source, Path target) throws IOException {
        SecureFiles.rejectSymlinkParents(source);
        SecureFiles.rejectSymlinkParents(target);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
    }

    private Path newestLoaderMarker() throws IOException {
        if (!Files.isDirectory(paths.installersDir())) return null;
        try (var files = Files.list(paths.installersDir())) {
            return files.filter(p -> p.getFileName().toString().endsWith(".installed.json"))
                    .filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(p))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElse(null);
        }
    }

    private void recordManagedJre(InstallationManifest installation) throws IOException {
        if (!Files.isDirectory(paths.runtimesDir(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(paths.runtimesDir())) return;
        try (var files = Files.walk(paths.runtimesDir())) {
            for (Path file : files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(p)).toList()) {
                replaceEntry(installation, integrity.observe(paths.root(), file, "managed-jre", null));
            }
        }
    }

    private String relativeToRoot(Path file) throws IOException {
        Path root = paths.root().toAbsolutePath().normalize();
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IOException("Managed file escapes launcher root: " + normalized);
        }
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private void replaceEntry(InstallationManifest manifest, ManifestEntry entry) {
        manifest.entries.removeIf(existing -> existing != null && entry.path.equals(existing.path));
        manifest.entries.add(entry);
    }

    private void validateRequest(Request request) throws IOException {
        if (request == null || request.minecraftVersion == null || request.minecraftVersion.isBlank()) {
            throw new IOException("Minecraft version is not selected");
        }
        if (request.manifest == null) {
            throw new IOException("Minecraft version manifest is unavailable");
        }
        if (request.gameDir == null) {
            throw new IOException("Game directory is not selected");
        }
    }

    private Path requireSafeRoot(Path root, String description) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        SecureFiles.rejectSymlinkParents(normalized);
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            SecureFiles.rejectSymbolicLink(normalized, description);
        }
        Files.createDirectories(normalized);
        return normalized;
    }

    private Path requireManagedPath(Path root, Path candidate, String description) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot) || normalized.equals(normalizedRoot)) {
            throw new IOException(description + " escapes its managed root: " + normalized);
        }
        SecureFiles.rejectSymlinkParents(normalized);
        return normalized;
    }

    private Path safeChild(Path root, String fileName, String description) throws IOException {
        Path relative;
        try {
            relative = Path.of(fileName);
        } catch (RuntimeException e) {
            throw new IOException("Invalid " + description + " filename", e);
        }
        if (relative.isAbsolute() || relative.getNameCount() != 1
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0) {
            throw new IOException("Unsafe " + description + " filename: " + fileName);
        }
        return requireManagedPath(root, root.resolve(relative), description);
    }

    private void rejectExistingLink(Path path, String description) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            SecureFiles.rejectSymbolicLink(path, description);
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(description + " is not a regular file: " + path);
            }
        }
    }

    public static final class ManagedMods {
        public List<ManagedMod> mods = new ArrayList<>();
    }

    public static final class ManagedMod {
        public String fileName;
        public String url;
        public String sha1;
        public long size;
        public boolean disabled;

        public static ManagedMod from(ModrinthVersion.File file, boolean disabled) {
            ManagedMod result = new ManagedMod();
            result.fileName = file.filename;
            result.url = file.url;
            result.sha1 = file.sha1;
            result.size = file.size;
            result.disabled = disabled;
            return result;
        }
    }
}
