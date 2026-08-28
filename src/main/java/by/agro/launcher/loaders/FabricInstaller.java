package by.agro.launcher.loaders;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FabricInstaller implements LoaderInstaller {

    protected static final String META_BASE = "https://meta.fabricmc.net/v2";

    protected final LauncherPaths paths;
    protected final Downloader downloader;

    public FabricInstaller(LauncherPaths paths, Downloader downloader) {
        this.paths = paths;
        this.downloader = downloader;
    }

    @Override
    public LoaderType type() {
        return LoaderType.FABRIC;
    }

    protected String metaBase() {
        return META_BASE;
    }

    protected String loaderName() {
        return "fabric-loader";
    }

    @Override
    public List<LoaderVersion> availableVersions(String minecraftVersion) throws IOException {
        String url = metaBase() + "/versions/loader/" + encode(minecraftVersion);
        String body = downloader.getString(url);
        JsonArray array = Json.parse(body).getAsJsonArray();

        List<LoaderVersion> result = new ArrayList<>();
        boolean firstStable = true;
        for (JsonElement element : array) {
            JsonObject entry = element.getAsJsonObject();
            JsonObject loader = Json.object(entry, "loader");
            if (loader == null) {
                continue;
            }
            String version = Json.string(loader, "version", null);
            if (version == null) {
                continue;
            }
            boolean stable = Json.boolValue(loader, "stable", false);
            boolean recommended = false;
            if (stable && firstStable) {
                recommended = true;
                firstStable = false;
            }
            result.add(new LoaderVersion(version, stable, recommended));
        }
        return result;
    }

    @Override
    public String install(String minecraftVersion, String loaderVersion, ProgressListener listener)
            throws IOException {
        String resolvedLoader = loaderVersion;
        if (resolvedLoader == null || resolvedLoader.isBlank()) {
            resolvedLoader = pickRecommended(minecraftVersion);
        }

        listener.onMessage(type().displayName() + " " + resolvedLoader + " для Minecraft " + minecraftVersion);
        listener.onProgress("Установка " + type().displayName(), 0, 2, "получение профиля");

        String profileUrl = metaBase() + "/versions/loader/"
                + encode(minecraftVersion) + "/" + encode(resolvedLoader) + "/profile/json";
        String profileJson = downloader.getString(profileUrl);
        JsonObject profile = Json.parseObject(profileJson);

        String versionId = Json.string(profile, "id", null);
        if (versionId == null || versionId.isBlank()) {
            versionId = loaderName() + "-" + resolvedLoader + "-" + minecraftVersion;
            profile.addProperty("id", versionId);
        }
        if (!profile.has("inheritsFrom")) {
            profile.addProperty("inheritsFrom", minecraftVersion);
        }

        Path target = paths.versionJson(versionId);
        Files.createDirectories(target.getParent());
        Json.write(target, profile);

        listener.onProgress("Установка " + type().displayName(), 2, 2, versionId);
        listener.onMessage("Профиль сохранён: " + versionId);
        return versionId;
    }

    protected String pickRecommended(String minecraftVersion) throws IOException {
        List<LoaderVersion> versions = availableVersions(minecraftVersion);
        if (versions.isEmpty()) {
            throw new IOException(type().displayName() + " не поддерживает Minecraft " + minecraftVersion);
        }
        for (LoaderVersion version : versions) {
            if (version.stable) {
                return version.version;
            }
        }
        return versions.get(0).version;
    }

    protected static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
