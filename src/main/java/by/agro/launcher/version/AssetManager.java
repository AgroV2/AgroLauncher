package by.agro.launcher.version;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.i18n.Strings;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Загрузка ресурсов игры (assets).
 *
 * Современная раскладка: assets/objects/{первые 2 символа хеша}/{хеш}
 * Legacy (1.7.2 и старше, assets = "legacy" или "pre-1.6"):
 *   дополнительно создаётся assets/virtual/{indexId}/{путь}, куда файлы копируются под своими именами.
 */
public final class AssetManager {

    private static final String RESOURCES_BASE = "https://resources.download.minecraft.net/";

    private final LauncherPaths paths;
    private final Downloader downloader;

    public AssetManager(LauncherPaths paths, Downloader downloader) {
        this.paths = paths;
        this.downloader = downloader;
    }

    /**
     * Скачивает индекс ассетов и все объекты.
     *
     * @return путь к каталогу, который нужно передать игре как --assetsDir
     */
    public Path downloadAssets(ResolvedVersion version, ProgressListener listener) throws IOException {
        if (version.assetIndex == null || version.assetIndex.url == null) {
            listener.onMessage("У версии нет индекса ассетов — пропускаем");
            return paths.assetsDir();
        }

        String indexId = version.assetIndex.id != null ? version.assetIndex.id : version.assetsId;
        Path indexFile = paths.assetIndexesDir().resolve(indexId + ".json");

        listener.onProgress(Strings.get("progress.assetIndex"), 0, 1, indexId);
        downloader.download(version.assetIndex.url, indexFile, version.assetIndex.sha1);

        JsonObject index = Json.readObject(indexFile);
        JsonObject objects = Json.object(index, "objects");
        if (objects == null) {
            return paths.assetsDir();
        }

        boolean virtual = Json.boolValue(index, "virtual", false);
        boolean mapToResources = Json.boolValue(index, "map_to_resources", false);
        boolean needsVirtual = virtual || mapToResources || version.preV16Assets;

        List<Downloader.Task> tasks = new ArrayList<>();
        List<String[]> virtualPairs = new ArrayList<>();

        for (String assetPath : objects.keySet()) {
            JsonObject entry = objects.getAsJsonObject(assetPath);
            String hash = Json.string(entry, "hash", null);
            long size = Json.longValue(entry, "size", 0);
            if (hash == null || hash.length() < 2) {
                continue;
            }
            String prefix = hash.substring(0, 2);
            Path target = paths.assetObjectsDir().resolve(prefix).resolve(hash);
            tasks.add(new Downloader.Task(RESOURCES_BASE + prefix + "/" + hash, target, hash, size));
            if (needsVirtual) {
                virtualPairs.add(new String[]{hash, assetPath});
            }
        }

        listener.onMessage(Strings.get("progress.assetsCount", tasks.size()));
        downloader.downloadAll(tasks, Strings.get("progress.assets"), listener);

        if (needsVirtual) {
            Path virtualRoot = mapToResources
                    ? paths.gameDir().resolve("resources")
                    : paths.assetsVirtualDir(indexId);
            materializeVirtual(virtualPairs, virtualRoot, listener);
            if (mapToResources) {
                // pre-1.6 ожидает ресурсы в game_dir/resources, --assetsDir остаётся стандартным
                return paths.assetsDir();
            }
            return virtualRoot;
        }

        return paths.assetsDir();
    }

    /** Копирует объекты в человекочитаемую структуру для legacy-версий. */
    private void materializeVirtual(List<String[]> pairs, Path virtualRoot, ProgressListener listener)
            throws IOException {
        Files.createDirectories(virtualRoot);
        int done = 0;
        for (String[] pair : pairs) {
            String hash = pair[0];
            String assetPath = pair[1];
            Path source = paths.assetObjectsDir().resolve(hash.substring(0, 2)).resolve(hash);
            Path target = virtualRoot.resolve(assetPath.replace('/', java.io.File.separatorChar));
            if (!Files.exists(source)) {
                continue;
            }
            if (Files.exists(target) && Files.size(target) == Files.size(source)) {
                done++;
                continue;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            done++;
            if (done % 200 == 0) {
                listener.onProgress(Strings.get("progress.legacyAssets"), done, pairs.size(), assetPath);
            }
        }
        listener.onProgress(Strings.get("progress.legacyAssets"), pairs.size(), pairs.size(), Strings.get("progress.done"));
    }

    /** Читает id индекса из локального файла (для случаев без сети). */
    public String readIndexId(Path indexFile) throws IOException {
        String content = Files.readString(indexFile, StandardCharsets.UTF_8);
        JsonObject obj = Json.parseObject(content);
        return Json.string(obj, "id", null);
    }
}
