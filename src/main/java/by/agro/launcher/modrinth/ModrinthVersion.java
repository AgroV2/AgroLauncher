package by.agro.launcher.modrinth;

import by.agro.launcher.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class ModrinthVersion {

    public final String id;
    public final String projectId;
    public final String name;
    public final String versionNumber;
    public final String versionType;
    public final String datePublished;
    public final long downloads;
    public final List<String> gameVersions;
    public final List<String> loaders;
    public final List<File> files;
    public final List<Dependency> dependencies;

    private ModrinthVersion(Builder b) {
        this.id = b.id;
        this.projectId = b.projectId;
        this.name = b.name;
        this.versionNumber = b.versionNumber;
        this.versionType = b.versionType;
        this.datePublished = b.datePublished;
        this.downloads = b.downloads;
        this.gameVersions = b.gameVersions;
        this.loaders = b.loaders;
        this.files = b.files;
        this.dependencies = b.dependencies;
    }

    public static final class File {
        public final String filename;
        public final String url;
        public final String sha1;
        public final long size;
        public final boolean primary;

        File(String filename, String url, String sha1, long size, boolean primary) {
            this.filename = filename;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
            this.primary = primary;
        }

        public String formattedSize() {
            if (size >= 1024 * 1024) {
                return String.format("%.1f %s", size / (1024.0 * 1024.0),
                        by.agro.launcher.i18n.Strings.get("unit.mb"));
            }
            if (size >= 1024) {
                return (size / 1024) + " " + by.agro.launcher.i18n.Strings.get("unit.kb");
            }
            return size + " " + by.agro.launcher.i18n.Strings.get("unit.b");
        }
    }

    public static final class Dependency {
        public final String projectId;
        public final String versionId;
        public final String type;

        Dependency(String projectId, String versionId, String type) {
            this.projectId = projectId;
            this.versionId = versionId;
            this.type = type;
        }

        public boolean isRequired() {
            return "required".equals(type);
        }

        public boolean isOptional() {
            return "optional".equals(type);
        }

        public boolean isIncompatible() {
            return "incompatible".equals(type);
        }
    }

    public static ModrinthVersion parse(JsonObject obj) {
        Builder b = new Builder();
        b.id = Json.string(obj, "id", null);
        b.projectId = Json.string(obj, "project_id", null);
        b.name = Json.string(obj, "name", "");
        b.versionNumber = Json.string(obj, "version_number", "");
        b.versionType = Json.string(obj, "version_type", "release");
        b.datePublished = Json.string(obj, "date_published", null);
        b.downloads = Json.longValue(obj, "downloads", 0);
        b.gameVersions = stringList(obj.getAsJsonArray("game_versions"));
        b.loaders = stringList(obj.getAsJsonArray("loaders"));

        JsonArray filesArray = obj.getAsJsonArray("files");
        if (filesArray != null) {
            for (JsonElement element : filesArray) {
                JsonObject fileObj = element.getAsJsonObject();
                String sha1 = null;
                JsonObject hashes = Json.object(fileObj, "hashes");
                if (hashes != null) {
                    sha1 = Json.string(hashes, "sha1", null);
                }
                b.files.add(new File(
                        Json.string(fileObj, "filename", "mod.jar"),
                        Json.string(fileObj, "url", null),
                        sha1,
                        Json.longValue(fileObj, "size", 0),
                        Json.boolValue(fileObj, "primary", false)
                ));
            }
        }

        JsonArray dependenciesArray = obj.getAsJsonArray("dependencies");
        if (dependenciesArray != null) {
            for (JsonElement element : dependenciesArray) {
                JsonObject dependencyObj = element.getAsJsonObject();
                b.dependencies.add(new Dependency(
                        Json.string(dependencyObj, "project_id", null),
                        Json.string(dependencyObj, "version_id", null),
                        Json.string(dependencyObj, "dependency_type", "required")
                ));
            }
        }

        return new ModrinthVersion(b);
    }

    private static List<String> stringList(JsonArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (element != null && !element.isJsonNull()) {
                result.add(element.getAsString());
            }
        }
        return result;
    }

    public File primaryFile() {
        for (File file : files) {
            if (file.primary) {
                return file;
            }
        }
        return files.isEmpty() ? null : files.get(0);
    }

    public List<Dependency> requiredDependencies() {
        List<Dependency> result = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            if (dependency.isRequired() && dependency.projectId != null) {
                result.add(dependency);
            }
        }
        return result;
    }

    public String versionTypeLabel() {
        switch (versionType) {
            case "beta":
                return by.agro.launcher.i18n.Strings.get("versions.typeBeta");
            case "alpha":
                return by.agro.launcher.i18n.Strings.get("versions.typeAlpha");
            default:
                return by.agro.launcher.i18n.Strings.get("versions.typeRelease");
        }
    }

    public boolean isStable() {
        return "release".equals(versionType);
    }

    @Override
    public String toString() {
        String label = versionNumber != null && !versionNumber.isBlank() ? versionNumber : name;
        return isStable() ? label : label + " (" + versionTypeLabel() + ")";
    }

    private static final class Builder {
        String id;
        String projectId;
        String name;
        String versionNumber;
        String versionType;
        String datePublished;
        long downloads;
        List<String> gameVersions = new ArrayList<>();
        List<String> loaders = new ArrayList<>();
        List<File> files = new ArrayList<>();
        List<Dependency> dependencies = new ArrayList<>();
    }
}
