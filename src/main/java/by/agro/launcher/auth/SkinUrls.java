package by.agro.launcher.auth;

import by.agro.launcher.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class SkinUrls {

    private SkinUrls() {
    }

    static String skinUrl(JsonObject profile, String fallback) {
        String direct = Json.string(profile, "skinUrl", Json.string(profile, "skin_url", ""));
        if (!direct.isBlank()) return direct;
        String fromSkin = textureUrl(profile.get("skin"));
        if (!fromSkin.isBlank()) return fromSkin;
        String fromTextures = textureUrl(profile.get("textures"));
        if (!fromTextures.isBlank()) return fromTextures;
        JsonArray properties = profile.has("properties") && profile.get("properties").isJsonArray()
                ? profile.getAsJsonArray("properties") : null;
        if (properties != null) {
            for (JsonElement element : properties) {
                if (!element.isJsonObject()) continue;
                JsonObject property = element.getAsJsonObject();
                if (!"textures".equals(Json.string(property, "name", ""))) continue;
                String url = textureUrl(property.get("value"));
                if (!url.isBlank()) return url;
            }
        }
        return fallback == null ? "" : fallback;
    }

    private static String textureUrl(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        try {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                String text = value.getAsString().trim();
                if (text.isEmpty()) return "";
                if (text.startsWith("http://") || text.startsWith("https://")) return text;
                try { return textureUrl(JsonParser.parseString(text)); }
                catch (RuntimeException ignored) {
                    try {
                        return textureUrl(JsonParser.parseString(new String(
                                decodeBase64(text), StandardCharsets.UTF_8)));
                    } catch (RuntimeException ignoredAgain) { return ""; }
                }
            }
            if (!value.isJsonObject()) return "";
            JsonObject object = value.getAsJsonObject();
            String direct = Json.string(object, "url", "");
            if (!direct.isBlank()) return direct;
            JsonObject textures = Json.object(object, "textures");
            if (textures != null) object = textures;
            JsonObject skin = Json.object(object, "SKIN");
            if (skin == null) skin = Json.object(object, "skin");
            return skin == null ? "" : textureUrl(skin);
        } catch (RuntimeException ignored) { return ""; }
    }

    private static byte[] decodeBase64(String value) {
        String normalized = value.trim();
        int remainder = normalized.length() % 4;
        if (remainder != 0) normalized += "=".repeat(4 - remainder);
        try {
            return Base64.getUrlDecoder().decode(normalized);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(normalized);
        }
    }
}
