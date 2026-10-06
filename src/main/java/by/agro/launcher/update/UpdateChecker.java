package by.agro.launcher.update;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Map;

public final class UpdateChecker {
    private UpdateChecker() { }
    public record Release(String version, String pageUrl) { }

    public static Release latest(Downloader downloader, String repository, String current) throws IOException {
        if (repository == null || repository.isBlank()) return null;
        String repo = repository.trim().replaceFirst("^https://github\\.com/", "").replaceAll("/+$", "");
        if (!repo.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IOException("Invalid GitHub repository");
        String body = downloader.getString("https://api.github.com/repos/" + repo + "/releases/latest",
                Map.of("Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28"));
        JsonObject json = Json.parseObject(body);
        String tag = Json.string(json, "tag_name", "").replaceFirst("^[vV]", "");
        String page = Json.string(json, "html_url", "https://github.com/" + repo + "/releases/latest");
        return compare(tag, current) > 0 ? new Release(tag, page) : null;
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
