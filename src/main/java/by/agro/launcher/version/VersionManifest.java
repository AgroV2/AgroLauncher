package by.agro.launcher.version;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Глобальный список версий Minecraft (version_manifest_v2.json).
 */
public final class VersionManifest {

    public static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private final List<RemoteVersion> versions;
    private final Map<String, RemoteVersion> byId;
    private final String latestRelease;
    private final String latestSnapshot;

    private VersionManifest(List<RemoteVersion> versions, String latestRelease, String latestSnapshot) {
        this.versions = versions;
        this.latestRelease = latestRelease;
        this.latestSnapshot = latestSnapshot;
        this.byId = new LinkedHashMap<>();
        for (RemoteVersion v : versions) {
            byId.put(v.id, v);
        }
    }

    public static VersionManifest fetch(Downloader downloader) throws IOException {
        String body = downloader.getString(MANIFEST_URL);
        return parse(body);
    }

    public static VersionManifest parse(String json) {
        JsonObject root = Json.parseObject(json);

        String latestRelease = null;
        String latestSnapshot = null;
        JsonObject latest = Json.object(root, "latest");
        if (latest != null) {
            latestRelease = Json.string(latest, "release", null);
            latestSnapshot = Json.string(latest, "snapshot", null);
        }

        List<RemoteVersion> list = new ArrayList<>();
        JsonArray array = root.getAsJsonArray("versions");
        if (array != null) {
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                list.add(new RemoteVersion(
                        Json.string(obj, "id", null),
                        Json.string(obj, "type", "release"),
                        Json.string(obj, "url", null),
                        Json.string(obj, "sha1", null),
                        Json.string(obj, "releaseTime", null)
                ));
            }
        }
        return new VersionManifest(list, latestRelease, latestSnapshot);
    }

    public List<RemoteVersion> versions() {
        return versions;
    }

    public RemoteVersion byId(String id) {
        return byId.get(id);
    }

    public String latestRelease() {
        return latestRelease;
    }

    public String latestSnapshot() {
        return latestSnapshot;
    }

    /** Отфильтрованный список для UI. */
    public List<RemoteVersion> filtered(boolean includeSnapshots, boolean includeOld) {
        List<RemoteVersion> result = new ArrayList<>();
        for (RemoteVersion v : versions) {
            if (v.isRelease()) {
                result.add(v);
            } else if (v.isSnapshot() && includeSnapshots) {
                result.add(v);
            } else if (v.isOld() && includeOld) {
                result.add(v);
            }
        }
        return result;
    }
}
