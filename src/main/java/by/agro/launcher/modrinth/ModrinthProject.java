package by.agro.launcher.modrinth;

import by.agro.launcher.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public final class ModrinthProject {

    public final String id;
    public final String slug;
    public final String title;
    public final String description;
    public final String author;
    public final long downloads;
    public final long follows;
    public final String iconUrl;
    public final List<String> categories;
    public final List<String> loaders;
    public final List<String> gameVersions;
    public final String projectType;
    public final String clientSide;
    public final String serverSide;
    public final String dateModified;

    private ModrinthProject(Builder b) {
        this.id = b.id;
        this.slug = b.slug;
        this.title = b.title;
        this.description = b.description;
        this.author = b.author;
        this.downloads = b.downloads;
        this.follows = b.follows;
        this.iconUrl = b.iconUrl;
        this.categories = b.categories;
        this.loaders = b.loaders;
        this.gameVersions = b.gameVersions;
        this.projectType = b.projectType;
        this.clientSide = b.clientSide;
        this.serverSide = b.serverSide;
        this.dateModified = b.dateModified;
    }

    public static ModrinthProject fromSearchHit(JsonObject obj) {
        Builder b = new Builder();
        b.id = Json.string(obj, "project_id", null);
        b.slug = Json.string(obj, "slug", b.id);
        b.title = Json.string(obj, "title", "Без названия");
        b.description = Json.string(obj, "description", "");
        b.author = Json.string(obj, "author", "");
        b.downloads = Json.longValue(obj, "downloads", 0);
        b.follows = Json.longValue(obj, "follows", 0);
        b.iconUrl = Json.string(obj, "icon_url", null);
        b.projectType = Json.string(obj, "project_type", "mod");
        b.clientSide = Json.string(obj, "client_side", "unknown");
        b.serverSide = Json.string(obj, "server_side", "unknown");
        b.dateModified = Json.string(obj, "date_modified", null);
        b.categories = stringList(obj.getAsJsonArray("categories"));
        b.loaders = stringList(obj.getAsJsonArray("categories"));
        b.gameVersions = stringList(obj.getAsJsonArray("versions"));
        return new ModrinthProject(b);
    }

    public static ModrinthProject fromProject(JsonObject obj) {
        Builder b = new Builder();
        b.id = Json.string(obj, "id", null);
        b.slug = Json.string(obj, "slug", b.id);
        b.title = Json.string(obj, "title", "Без названия");
        b.description = Json.string(obj, "description", "");
        b.author = Json.string(obj, "author", "");
        b.downloads = Json.longValue(obj, "downloads", 0);
        b.follows = Json.longValue(obj, "followers", 0);
        b.iconUrl = Json.string(obj, "icon_url", null);
        b.projectType = Json.string(obj, "project_type", "mod");
        b.clientSide = Json.string(obj, "client_side", "unknown");
        b.serverSide = Json.string(obj, "server_side", "unknown");
        b.dateModified = Json.string(obj, "updated", null);
        b.categories = stringList(obj.getAsJsonArray("categories"));
        b.loaders = stringList(obj.getAsJsonArray("loaders"));
        b.gameVersions = stringList(obj.getAsJsonArray("game_versions"));
        return new ModrinthProject(b);
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

    public String formattedDownloads() {
        if (downloads >= 1_000_000) {
            return String.format("%.1f млн", downloads / 1_000_000.0);
        }
        if (downloads >= 1_000) {
            return String.format("%.1f тыс.", downloads / 1_000.0);
        }
        return String.valueOf(downloads);
    }

    public String pageUrl() {
        return "https://modrinth.com/mod/" + (slug != null ? slug : id);
    }

    public List<String> visibleCategories() {
        List<String> result = new ArrayList<>();
        for (String category : categories) {
            if (!ModrinthClient.isLoaderCategory(category)) {
                result.add(category);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return title;
    }

    private static final class Builder {
        String id;
        String slug;
        String title;
        String description;
        String author;
        long downloads;
        long follows;
        String iconUrl;
        List<String> categories = new ArrayList<>();
        List<String> loaders = new ArrayList<>();
        List<String> gameVersions = new ArrayList<>();
        String projectType;
        String clientSide;
        String serverSide;
        String dateModified;
    }
}
