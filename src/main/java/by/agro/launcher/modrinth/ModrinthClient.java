package by.agro.launcher.modrinth;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModrinthClient {

    private static final String API_BASE = "https://api.modrinth.com/v2";

    private static final List<String> LOADER_CATEGORIES = List.of(
            "fabric", "forge", "neoforge", "quilt", "rift", "modloader", "liteloader", "bukkit",
            "bungeecord", "canvas", "datapack", "folia", "iris", "optifine", "paper", "purpur",
            "spigot", "sponge", "velocity", "waterfall", "minecraft");

    private final Downloader downloader;

    public ModrinthClient(Downloader downloader) {
        this.downloader = downloader;
    }

    public enum Sort {
        RELEVANCE("relevance", "browser.sortRelevance"),
        DOWNLOADS("downloads", "browser.sortDownloads"),
        FOLLOWS("follows", "browser.sortFollows"),
        NEWEST("newest", "browser.sortNewest"),
        UPDATED("updated", "browser.sortUpdated");

        private final String apiValue;
        private final String nameKey;

        Sort(String apiValue, String nameKey) {
            this.apiValue = apiValue;
            this.nameKey = nameKey;
        }

        public String apiValue() {
            return apiValue;
        }

        public static Sort fromValue(String value) {
            for (Sort sort : values()) {
                if (sort.apiValue.equals(value)) {
                    return sort;
                }
            }
            return DOWNLOADS;
        }

        @Override
        public String toString() {
            return by.agro.launcher.i18n.Strings.get(nameKey);
        }
    }

    public static final class SearchResult {
        public final List<ModrinthProject> projects;
        public final int totalHits;
        public final int offset;

        SearchResult(List<ModrinthProject> projects, int totalHits, int offset) {
            this.projects = projects;
            this.totalHits = totalHits;
            this.offset = offset;
        }

        public boolean hasMore() {
            return offset + projects.size() < totalHits;
        }
    }

    public SearchResult search(String query, String loader, String gameVersion, String category,
                               Sort sort, int offset, int limit) throws IOException {
        List<String> facetGroups = new ArrayList<>();
        facetGroups.add("[\"project_type:mod\"]");

        if (loader != null && !loader.isBlank() && !"vanilla".equalsIgnoreCase(loader)) {
            facetGroups.add("[\"categories:" + loader.toLowerCase() + "\"]");
        }
        if (gameVersion != null && !gameVersion.isBlank()) {
            facetGroups.add("[\"versions:" + gameVersion + "\"]");
        }
        if (category != null && !category.isBlank()) {
            facetGroups.add("[\"categories:" + category + "\"]");
        }

        StringBuilder url = new StringBuilder(API_BASE).append("/search?");
        url.append("limit=").append(Math.min(100, Math.max(1, limit)));
        url.append("&offset=").append(Math.max(0, offset));
        url.append("&index=").append(sort != null ? sort.apiValue() : Sort.DOWNLOADS.apiValue());
        if (query != null && !query.isBlank()) {
            url.append("&query=").append(encode(query.trim()));
        }
        url.append("&facets=").append(encode("[" + String.join(",", facetGroups) + "]"));

        String body = downloader.getString(url.toString(), headers());
        JsonObject root = Json.parseObject(body);

        List<ModrinthProject> projects = new ArrayList<>();
        JsonArray hits = root.getAsJsonArray("hits");
        if (hits != null) {
            for (JsonElement element : hits) {
                if (element.isJsonObject()) {
                    projects.add(ModrinthProject.fromSearchHit(element.getAsJsonObject()));
                }
            }
        }
        int totalHits = Json.intValue(root, "total_hits", projects.size());
        return new SearchResult(projects, totalHits, Math.max(0, offset));
    }

    public ModrinthProject project(String idOrSlug) throws IOException {
        String body = downloader.getString(API_BASE + "/project/" + encode(idOrSlug), headers());
        return ModrinthProject.fromProject(Json.parseObject(body));
    }

    public String projectBody(String idOrSlug) throws IOException {
        String body = downloader.getString(API_BASE + "/project/" + encode(idOrSlug), headers());
        JsonObject root = Json.parseObject(body);
        return Json.string(root, "body", "");
    }

    public List<ModrinthVersion> versions(String projectId, String loader, String gameVersion)
            throws IOException {
        StringBuilder url = new StringBuilder(API_BASE)
                .append("/project/").append(encode(projectId)).append("/version");

        List<String> params = new ArrayList<>();
        if (loader != null && !loader.isBlank() && !"vanilla".equalsIgnoreCase(loader)) {
            params.add("loaders=" + encode("[\"" + loader.toLowerCase() + "\"]"));
        }
        if (gameVersion != null && !gameVersion.isBlank()) {
            params.add("game_versions=" + encode("[\"" + gameVersion + "\"]"));
        }
        if (!params.isEmpty()) {
            url.append('?').append(String.join("&", params));
        }

        String body = downloader.getString(url.toString(), headers());
        JsonArray array = Json.parse(body).getAsJsonArray();

        List<ModrinthVersion> result = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                result.add(ModrinthVersion.parse(element.getAsJsonObject()));
            }
        }
        return result;
    }

    public ModrinthVersion version(String versionId) throws IOException {
        String body = downloader.getString(API_BASE + "/version/" + encode(versionId), headers());
        return ModrinthVersion.parse(Json.parseObject(body));
    }

    public List<String> categories() throws IOException {
        String body = downloader.getString(API_BASE + "/tag/category", headers());
        JsonArray array = Json.parse(body).getAsJsonArray();

        Map<String, Boolean> unique = new LinkedHashMap<>();
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            if (!"mod".equals(Json.string(obj, "project_type", ""))) {
                continue;
            }
            String name = Json.string(obj, "name", null);
            if (name != null && !isLoaderCategory(name)) {
                unique.put(name, Boolean.TRUE);
            }
        }
        return new ArrayList<>(unique.keySet());
    }

    public static boolean isLoaderCategory(String category) {
        return category != null && LOADER_CATEGORIES.contains(category.toLowerCase());
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "AgroLauncher/1.0 (Minecraft launcher)");
        headers.put("Accept", "application/json");
        return headers;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
