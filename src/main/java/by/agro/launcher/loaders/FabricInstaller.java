package by.agro.launcher.loaders;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.MetadataCache;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.Settings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class FabricInstaller implements LoaderInstaller {

    protected static final String META_BASE = "https://meta.fabricmc.net/v2";

    protected final LauncherPaths paths;
    protected final Downloader downloader;
    protected final Settings settings;

    public FabricInstaller(LauncherPaths paths, Downloader downloader) {
        this(paths, downloader, Settings.load());
    }

    public FabricInstaller(LauncherPaths paths, Downloader downloader, Settings settings) {
        this.paths = paths;
        this.downloader = downloader;
        this.settings = settings;
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
        String body = new MetadataCache(paths.metadataCacheDir(), downloader, settings.metadataCacheEnabled)
                .get(url, Duration.ofHours(6));
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

        listener.onMessage(by.agro.launcher.i18n.Strings.get("loader.installSummary", type().displayName(), resolvedLoader, minecraftVersion));
        listener.onProgress(by.agro.launcher.i18n.Strings.get("loader.stageInstalling", type().displayName()), 0, 2, by.agro.launcher.i18n.Strings.get("loader.stageProfile"));

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

        listener.onProgress(by.agro.launcher.i18n.Strings.get("loader.stageInstalling", type().displayName()), 2, 2, versionId);
        listener.onMessage(by.agro.launcher.i18n.Strings.get("loader.profileSaved", versionId));
        return versionId;
    }

    protected String pickRecommended(String minecraftVersion) throws IOException {
        List<LoaderVersion> versions = availableVersions(minecraftVersion);
        if (versions.isEmpty()) {
            throw new IOException(type().displayName() + " does not support Minecraft " + minecraftVersion);
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
