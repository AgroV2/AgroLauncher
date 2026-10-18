package by.agro.launcher.version;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class VersionResolver {

    private static final int MAX_CHAIN_DEPTH = 12;

    private final LauncherPaths paths;
    private final Downloader downloader;

    public VersionResolver(LauncherPaths paths, Downloader downloader) {
        this.paths = paths;
        this.downloader = downloader;
    }


    public VersionJson ensureVersionJson(String versionId, VersionManifest manifest, ProgressListener listener)
            throws IOException {
        Path file = paths.versionJson(versionId);
        if (!Files.exists(file)) {
            RemoteVersion remote = manifest != null ? manifest.byId(versionId) : null;
            if (remote == null || remote.url == null) {
                throw new IOException("Version " + versionId + " was found neither locally nor in the Mojang manifest");
            }
            listener.onMessage("Загрузка описания версии " + versionId);
            Files.createDirectories(file.getParent());
            downloader.download(remote.url, file, remote.sha1);
        }
        return readLocal(versionId);
    }

    public VersionJson readLocal(String versionId) throws IOException {
        Path file = paths.versionJson(versionId);
        if (!Files.exists(file)) {
            throw new IOException("Version file not found: " + file);
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return VersionJson.parse(content);
    }


    public ResolvedVersion resolve(String versionId, VersionManifest manifest, ProgressListener listener)
            throws IOException {
        return resolve(versionId, manifest, listener, false);
    }

    public ResolvedVersion resolve(String versionId, VersionManifest manifest, ProgressListener listener,
                                   boolean localOnly) throws IOException {
        List<VersionJson> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();

        String currentId = versionId;
        for (int depth = 0; depth < MAX_CHAIN_DEPTH && currentId != null; depth++) {
            if (!visited.add(currentId)) {
                throw new IOException("Cyclic version inheritance: " + currentId);
            }
            VersionJson current = localOnly ? readLocal(currentId) : ensureVersionJson(currentId, manifest, listener);
            chain.add(current);
            currentId = current.inheritsFrom;
        }
        if (currentId != null) {
            throw new IOException("inheritsFrom chain is too long for " + versionId);
        }

        return merge(chain);
    }


    private ResolvedVersion merge(List<VersionJson> chain) {
        VersionJson child = chain.get(0);
        VersionJson root = chain.get(chain.size() - 1);

        String mainClass = firstNonNull(chain, v -> v.mainClass);
        String assetsId = firstNonNull(chain, v -> v.assets);
        VersionJson.AssetIndexInfo assetIndex = firstNonNull(chain, v -> v.assetIndex);
        VersionJson.DownloadInfo clientDownload = firstNonNull(chain, v -> v.clientDownload);
        VersionJson.JavaVersionInfo javaVersion = firstNonNull(chain, v -> v.javaVersion);
        String minecraftArguments = firstNonNull(chain, v -> v.minecraftArguments);
        List<Library> libraries = new ArrayList<>();
        Set<String> keysFromChildren = new HashSet<>();
        for (VersionJson version : chain) {
            Set<String> keysOfThisVersion = new HashSet<>();
            for (Library library : version.libraries) {
                String key = library.groupArtifactKey();
                
                if (keysFromChildren.contains(key)) {
                    continue;
                }
                libraries.add(library);
                keysOfThisVersion.add(key);
            }
            keysFromChildren.addAll(keysOfThisVersion);
        }

        
        List<Argument> gameArguments = new ArrayList<>();
        List<Argument> jvmArguments = new ArrayList<>();
        for (int i = chain.size() - 1; i >= 0; i--) {
            VersionJson version = chain.get(i);
            gameArguments.addAll(version.gameArguments);
            jvmArguments.addAll(version.jvmArguments);
        }

        boolean legacyArguments = gameArguments.isEmpty() && minecraftArguments != null;
        boolean preV16 = "pre-1.6".equals(assetsId) || "legacy".equals(assetsId);

        int javaMajor = javaVersion != null ? javaVersion.majorVersion : 0;
        if (javaMajor == 0) {
            javaMajor = JavaRequirement.guessByVersionId(root.id);
        }

        List<String> chainIds = new ArrayList<>();
        for (VersionJson version : chain) {
            chainIds.add(version.id);
        }

        String type = firstNonNull(chain, v -> v.type);

        return new ResolvedVersion(
                child.id,
                root.id,
                mainClass,
                type != null ? type : "release",
                assetsId,
                assetIndex,
                clientDownload,
                javaMajor,
                libraries,
                gameArguments,
                jvmArguments,
                minecraftArguments,
                legacyArguments,
                preV16,
                chainIds
        );
    }

    private <T> T firstNonNull(List<VersionJson> chain, java.util.function.Function<VersionJson, T> getter) {
        for (VersionJson version : chain) {
            T value = getter.apply(version);
            if (value != null) {
                if (value instanceof String str && str.isBlank()) {
                    continue;
                }
                return value;
            }
        }
        return null;
    }

  
    public List<String> installedVersions() {
        List<String> result = new ArrayList<>();
        Path dir = paths.versionsDir();
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                String name = path.getFileName().toString();
                if (Files.exists(path.resolve(name + ".json"))) {
                    result.add(name);
                }
            });
        } catch (IOException e) {
            System.err.println("Не удалось прочитать список версий: " + e.getMessage());
        }
        result.sort(String::compareToIgnoreCase);
        return result;
    }

 
    public void writeVersionJson(String versionId, com.google.gson.JsonObject json) throws IOException {
        Path file = paths.versionJson(versionId);
        Files.createDirectories(file.getParent());
        Json.write(file, json);
    }
}
