package by.agro.launcher.version;

import by.agro.launcher.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Разобранный version.json — как ванильный, так и профили Fabric/Forge/Quilt/NeoForge/OptiFine.
 *
 * Поддерживает оба формата аргументов:
 *  - "arguments": { "game": [...], "jvm": [...] }  (1.13+)
 *  - "minecraftArguments": "строка"                (1.12.2 и старше)
 */
public final class VersionJson {

    public final String id;
    public final String inheritsFrom;
    public final String type;
    public final String mainClass;
    public final String assets;
    public final int minimumLauncherVersion;

    public final AssetIndexInfo assetIndex;
    public final DownloadInfo clientDownload;
    public final JavaVersionInfo javaVersion;

    public final List<Library> libraries;
    public final List<Argument> gameArguments;
    public final List<Argument> jvmArguments;

    /** Строковые аргументы старого формата (1.12.2 и ниже). */
    public final String minecraftArguments;

    /** Исходный JSON — нужен для повторной сериализации профилей. */
    public final JsonObject raw;

    private VersionJson(Builder b) {
        this.id = b.id;
        this.inheritsFrom = b.inheritsFrom;
        this.type = b.type;
        this.mainClass = b.mainClass;
        this.assets = b.assets;
        this.minimumLauncherVersion = b.minimumLauncherVersion;
        this.assetIndex = b.assetIndex;
        this.clientDownload = b.clientDownload;
        this.javaVersion = b.javaVersion;
        this.libraries = b.libraries;
        this.gameArguments = b.gameArguments;
        this.jvmArguments = b.jvmArguments;
        this.minecraftArguments = b.minecraftArguments;
        this.raw = b.raw;
    }

    public static final class AssetIndexInfo {
        public final String id;
        public final String url;
        public final String sha1;
        public final long size;
        public final long totalSize;

        AssetIndexInfo(String id, String url, String sha1, long size, long totalSize) {
            this.id = id;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
            this.totalSize = totalSize;
        }
    }

    public static final class DownloadInfo {
        public final String url;
        public final String sha1;
        public final long size;

        DownloadInfo(String url, String sha1, long size) {
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
        }
    }

    public static final class JavaVersionInfo {
        public final String component;
        public final int majorVersion;

        JavaVersionInfo(String component, int majorVersion) {
            this.component = component;
            this.majorVersion = majorVersion;
        }
    }

    public static VersionJson parse(String json) {
        return parse(Json.parseObject(json));
    }

    public static VersionJson parse(JsonObject root) {
        Builder b = new Builder();
        b.raw = root;
        b.id = Json.string(root, "id", null);
        b.inheritsFrom = Json.string(root, "inheritsFrom", null);
        b.type = Json.string(root, "type", "release");
        b.mainClass = Json.string(root, "mainClass", null);
        b.assets = Json.string(root, "assets", null);
        b.minimumLauncherVersion = Json.intValue(root, "minimumLauncherVersion", 0);
        b.minecraftArguments = Json.string(root, "minecraftArguments", null);

        JsonObject assetIndex = Json.object(root, "assetIndex");
        if (assetIndex != null) {
            b.assetIndex = new AssetIndexInfo(
                    Json.string(assetIndex, "id", null),
                    Json.string(assetIndex, "url", null),
                    Json.string(assetIndex, "sha1", null),
                    Json.longValue(assetIndex, "size", 0),
                    Json.longValue(assetIndex, "totalSize", 0)
            );
        }

        JsonObject downloads = Json.object(root, "downloads");
        if (downloads != null) {
            JsonObject client = Json.object(downloads, "client");
            if (client != null) {
                b.clientDownload = new DownloadInfo(
                        Json.string(client, "url", null),
                        Json.string(client, "sha1", null),
                        Json.longValue(client, "size", 0)
                );
            }
        }

        JsonObject javaVersion = Json.object(root, "javaVersion");
        if (javaVersion != null) {
            b.javaVersion = new JavaVersionInfo(
                    Json.string(javaVersion, "component", null),
                    Json.intValue(javaVersion, "majorVersion", 0)
            );
        }

        JsonArray libraries = root.getAsJsonArray("libraries");
        if (libraries != null) {
            for (JsonElement element : libraries) {
                if (element.isJsonObject()) {
                    Library library = Library.parse(element.getAsJsonObject());
                    if (library != null) {
                        b.libraries.add(library);
                    }
                }
            }
        }

        JsonObject arguments = Json.object(root, "arguments");
        if (arguments != null) {
            b.gameArguments = Argument.parseList(arguments.getAsJsonArray("game"));
            b.jvmArguments = Argument.parseList(arguments.getAsJsonArray("jvm"));
        }

        return new VersionJson(b);
    }

    /** Использует ли версия старый строковый формат аргументов. */
    public boolean isLegacyArguments() {
        return (gameArguments == null || gameArguments.isEmpty()) && minecraftArguments != null;
    }

    /** Версии до 1.6 требуют --session вместо --accessToken и virtual assets. */
    public boolean isPreV16Assets() {
        return "pre-1.6".equals(assets) || "legacy".equals(assets);
    }

    private static final class Builder {
        String id;
        String inheritsFrom;
        String type = "release";
        String mainClass;
        String assets;
        int minimumLauncherVersion;
        String minecraftArguments;
        AssetIndexInfo assetIndex;
        DownloadInfo clientDownload;
        JavaVersionInfo javaVersion;
        List<Library> libraries = new ArrayList<>();
        List<Argument> gameArguments = new ArrayList<>();
        List<Argument> jvmArguments = new ArrayList<>();
        JsonObject raw;
    }
}
