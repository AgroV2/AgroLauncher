package by.agro.launcher.update;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.Platform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UpdateChecker {
    private UpdateChecker() { }

    public record Asset(String name, String downloadUrl, long size) { }
    public record Release(String version, String pageUrl, Asset asset, Asset checksumAsset) {
        public boolean canAutoUpdate() { return asset != null; }
    }

    public static Release latest(Downloader downloader, String repository, String current) throws IOException {
        if (repository == null || repository.isBlank()) return null;
        String repo = repository.trim().replaceFirst("^https://github\\.com/", "").replaceAll("/+$", "");
        if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IOException("Invalid GitHub repository");
        String body = downloader.getString("https://api.github.com/repos/" + repo + "/releases/latest",
                Map.of("Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28"));
        JsonObject json = Json.parseObject(body);
        String tag = Json.string(json, "tag_name", "").replaceFirst("^[vV]", "");
        if (tag.isBlank() || compare(tag, current) <= 0) return null;
        String page = Json.string(json, "html_url", "https://github.com/" + repo + "/releases/latest");
        List<Asset> assets = parseAssets(json);
        Asset artifact = selectArtifact(assets, Platform.current(), Platform.arch());
        Asset checksum = artifact == null ? null : selectChecksum(assets, artifact.name());
        return new Release(tag, page, artifact, checksum);
    }

    static List<Asset> parseAssets(JsonObject json) {
        List<Asset> result = new ArrayList<>();
        JsonArray array = json.has("assets") && json.get("assets").isJsonArray()
                ? json.getAsJsonArray("assets") : new JsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject asset = element.getAsJsonObject();
            String name = Json.string(asset, "name", "");
            String url = Json.string(asset, "browser_download_url", "");
            long size = asset.has("size") ? asset.get("size").getAsLong() : 0;
            if (!name.isBlank() && url.startsWith("https://")) result.add(new Asset(name, url, size));
        }
        return result;
    }

    static Asset selectArtifact(List<Asset> assets, Platform platform, String arch) {
        Asset best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Asset asset : assets) {
            String name = asset.name().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".jar") || isChecksum(name) || name.contains("sources") || name.contains("javadoc")) continue;
            int score = name.contains("agrolauncher") ? 20 : 0;
            if (name.contains("portable")) score += platform == Platform.WINDOWS ? 8 : -8;
            if (name.contains("windows") || name.contains("win64")) score += platform == Platform.WINDOWS ? 5 : -20;
            if (name.contains("linux")) score += platform == Platform.LINUX ? 5 : -20;
            if (name.contains("macos") || name.contains("darwin") || name.contains("osx")) score += platform == Platform.OSX ? 5 : -20;
            if (name.contains(arch.toLowerCase(Locale.ROOT))) score += 2;
            if (score > bestScore) { best = asset; bestScore = score; }
        }
        return bestScore < 0 ? null : best;
    }

    static Asset selectChecksum(List<Asset> assets, String artifactName) {
        String lower = artifactName.toLowerCase(Locale.ROOT);
        for (Asset asset : assets) {
            String name = asset.name().toLowerCase(Locale.ROOT);
            if (name.equals(lower + ".sha256") || name.equals(lower + ".sha256sum")) return asset;
        }
        for (Asset asset : assets) if (isChecksum(asset.name().toLowerCase(Locale.ROOT))) return asset;
        return null;
    }

    private static boolean isChecksum(String name) {
        return name.endsWith(".sha256") || name.endsWith(".sha256sum")
                || name.equals("sha256sums") || name.equals("checksums.txt") || name.equals("sha256sums.txt");
    }

    static int compare(String a, String b) {
        String[] left = a.split("[-+]", 2)[0].split("\\.");
        String[] right = b.split("[-+]", 2)[0].split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int x = i < left.length ? number(left[i]) : 0;
            int y = i < right.length ? number(right[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int number(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9].*$", "")); }
        catch (NumberFormatException e) { return 0; }
    }
}
