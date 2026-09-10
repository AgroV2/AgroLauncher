package by.agro.launcher.modrinth;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.Json;
import by.agro.launcher.repair.RepairService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ModInstaller {

    private static final int MAX_DEPTH = 8;

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final ModrinthClient client;

    public ModInstaller(LauncherPaths paths, Downloader downloader, ModrinthClient client) {
        this.paths = paths;
        this.downloader = downloader;
        this.client = client;
    }

    public static final class Result {
        public final List<String> installed = new ArrayList<>();
        public final List<String> skipped = new ArrayList<>();
        public final List<String> failed = new ArrayList<>();

        public boolean isSuccess() {
            return failed.isEmpty() && !installed.isEmpty();
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(by.agro.launcher.i18n.Strings.get("install.summaryInstalled", installed.size()));
            if (!skipped.isEmpty()) {
                sb.append(by.agro.launcher.i18n.Strings.get("install.summarySkipped", skipped.size()));
            }
            if (!failed.isEmpty()) {
                sb.append(by.agro.launcher.i18n.Strings.get("install.summaryFailed", failed.size()));
            }
            return sb.toString();
        }
    }

    public Result install(ModrinthVersion version, String loader, String gameVersion,
                          boolean withDependencies, ProgressListener listener) throws IOException {
        Result result = new Result();
        Set<String> visited = new HashSet<>();
        Path modsDir = paths.modsDir();
        Files.createDirectories(modsDir);

        installRecursive(version, loader, gameVersion, withDependencies,
                visited, result, listener, 0);
        return result;
    }

    private void installRecursive(ModrinthVersion version, String loader, String gameVersion,
                                  boolean withDependencies, Set<String> visited, Result result,
                                  ProgressListener listener, int depth) {
        if (version == null || depth > MAX_DEPTH) {
            return;
        }
        if (version.projectId != null && !visited.add(version.projectId)) {
            return;
        }

        ModrinthVersion.File file = version.primaryFile();
        if (file == null || file.url == null) {
            result.failed.add(version.versionNumber + " (нет файла для загрузки)");
            return;
        }

        final Path target;
        final Path disabled;
        try {
            target = safeModPath(file.filename);
            disabled = safeModPath(file.filename + ".disabled");
        } catch (IllegalArgumentException e) {
            result.failed.add(file.filename + ": " + e.getMessage());
            listener.onMessage(by.agro.launcher.i18n.Strings.get("install.downloadFailed", file.filename, e.getMessage()));
            return;
        }

        try {
            if (Files.exists(target) || Files.exists(disabled)) {
                result.skipped.add(file.filename);
                listener.onMessage(by.agro.launcher.i18n.Strings.get("install.alreadyInstalled", file.filename));
            } else {
                listener.onProgress(by.agro.launcher.i18n.Strings.get("install.stage"), result.installed.size(), 0, file.filename);
                downloader.download(file.url, target, file.sha1);
                recordManagedMod(file);
                result.installed.add(file.filename);
                listener.onMessage(by.agro.launcher.i18n.Strings.get("install.installed", file.filename, file.formattedSize()));
            }
        } catch (IOException e) {
            result.failed.add(file.filename + ": " + e.getMessage());
            listener.onMessage(by.agro.launcher.i18n.Strings.get("install.downloadFailed", file.filename, e.getMessage()));
            return;
        }

        if (!withDependencies) {
            return;
        }

        for (ModrinthVersion.Dependency dependency : version.requiredDependencies()) {
            if (visited.contains(dependency.projectId)) {
                continue;
            }
            try {
                ModrinthVersion dependencyVersion = resolveDependency(dependency, loader, gameVersion);
                if (dependencyVersion == null) {
                    result.failed.add("зависимость " + dependency.projectId
                            + " (нет подходящей версии)");
                    listener.onMessage(by.agro.launcher.i18n.Strings.get(
                            "install.depNotFound", dependency.projectId));
                    continue;
                }
                listener.onMessage(by.agro.launcher.i18n.Strings.get("install.fetchingDep", dependencyVersion.versionNumber));
                installRecursive(dependencyVersion, loader, gameVersion, true,
                        visited, result, listener, depth + 1);
            } catch (IOException e) {
                result.failed.add("зависимость " + dependency.projectId + ": " + e.getMessage());
                listener.onMessage(by.agro.launcher.i18n.Strings.get("install.depError", dependency.projectId, e.getMessage()));
            }
        }
    }

    private Path safeModPath(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Mod filename is not specified");
        }
        Path modsRoot = paths.modsDir().toAbsolutePath().normalize();
        Path target = modsRoot.resolve(filename).normalize();
        if (Path.of(filename).isAbsolute() || !target.startsWith(modsRoot)
                || Path.of(filename).getNameCount() != 1
                || filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Unsafe mod filename: " + filename);
        }
        return target;
    }

    private synchronized void recordManagedMod(ModrinthVersion.File file) throws IOException {
        if (file.sha1 == null || file.sha1.isBlank()) {
            throw new IOException("Modrinth did not provide SHA-1 for " + file.filename);
        }
        Path metadata = paths.gameDir().resolve(".agrolauncher-managed-mods.json");
        RepairService.ManagedMods managed = Files.exists(metadata)
                ? Json.read(metadata, RepairService.ManagedMods.class)
                : new RepairService.ManagedMods();
        if (managed == null) {
            managed = new RepairService.ManagedMods();
        }
        managed.mods.removeIf(entry -> entry != null && file.filename.equals(entry.fileName));
        managed.mods.add(RepairService.ManagedMod.from(file, false));
        Json.write(metadata, managed);
    }

    private ModrinthVersion resolveDependency(ModrinthVersion.Dependency dependency,
                                              String loader, String gameVersion) throws IOException {
        if (dependency.versionId != null && !dependency.versionId.isBlank()) {
            return client.version(dependency.versionId);
        }
        List<ModrinthVersion> versions = client.versions(dependency.projectId, loader, gameVersion);
        if (versions.isEmpty()) {
            versions = client.versions(dependency.projectId, loader, null);
        }
        if (versions.isEmpty()) {
            return null;
        }
        for (ModrinthVersion version : versions) {
            if (version.isStable()) {
                return version;
            }
        }
        return versions.get(0);
    }

    public boolean isInstalled(ModrinthVersion version) {
        ModrinthVersion.File file = version.primaryFile();
        if (file == null) {
            return false;
        }
        try {
            return Files.exists(safeModPath(file.filename))
                    || Files.exists(safeModPath(file.filename + ".disabled"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
