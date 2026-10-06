package by.agro.launcher.auth;

import by.agro.launcher.core.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;


public final class ElyByAuth {

    public static final String AUTH_SERVER = "https://authserver.ely.by";
    public static final String INJECTOR_ARGUMENT = AUTH_SERVER;

    private static final int TIMEOUT_MS = 20_000;
    private static final String USER_AGENT = "AgroLauncher/1.0";

    private ElyByAuth() {
    }

    public static class AuthException extends IOException {
        public final String errorType;

        public AuthException(String errorType, String message) {
            super(message);
            this.errorType = errorType;
        }
    }

    public static class TwoFactorRequiredException extends AuthException {
        public TwoFactorRequiredException(String message) {
            super("ForbiddenOperationException", message);
        }
    }


    public static Account authenticate(String login, String password, String totpCode) throws IOException {
        String clientToken = UUID.randomUUID().toString();

        String effectivePassword = password;
        if (totpCode != null && !totpCode.isBlank()) {
            effectivePassword = password + ":" + totpCode.trim();
        }

        JsonObject request = new JsonObject();
        request.addProperty("username", login);
        request.addProperty("password", effectivePassword);
        request.addProperty("clientToken", clientToken);
        request.addProperty("requestUser", true);

        JsonObject response = post("/auth/authenticate", request);

        Account account = new Account();
        account.type = Account.Type.ELY_BY;
        account.login = login;
        account.clientToken = clientToken;
        account.accessToken = Json.string(response, "accessToken", "");
        account.tokenUpdatedAt = System.currentTimeMillis();

        JsonObject profile = Json.object(response, "selectedProfile");
        if (profile != null) {
            account.uuid = Json.string(profile, "id", "");
            account.username = Json.string(profile, "name", login);
            account.skinUrl = skinUrl(profile, skinUrl(response, account.skinUrl));
        } else {
            throw new AuthException("IllegalArgumentException",
                    "The server did not return a player profile. Check whether a username is linked to the Ely.by account.");
        }
        account.id = "elyby-" + account.uuid;
        return account;
    }


    public static void refresh(Account account) throws IOException {
        if (account.accessToken == null || account.accessToken.isBlank()
                || account.clientToken == null || account.clientToken.isBlank()) {
            throw new AuthException("IllegalArgumentException",
                    "Insufficient data to refresh the token; sign in again");
        }

        JsonObject request = new JsonObject();
        request.addProperty("accessToken", account.accessToken);
        request.addProperty("clientToken", account.clientToken);
        request.addProperty("requestUser", true);

        JsonObject response = post("/auth/refresh", request);

        account.accessToken = Json.string(response, "accessToken", account.accessToken);
        account.tokenUpdatedAt = System.currentTimeMillis();

        JsonObject profile = Json.object(response, "selectedProfile");
        if (profile != null) {
            account.uuid = Json.string(profile, "id", account.uuid);
            account.username = Json.string(profile, "name", account.username);
            account.skinUrl = skinUrl(profile, skinUrl(response, account.skinUrl));
        }
    }


    private static String skinUrl(JsonObject profile, String fallback) {
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

    public static boolean validate(Account account) {
        if (account.accessToken == null || account.accessToken.isBlank()) {
            return false;
        }
        JsonObject request = new JsonObject();
        request.addProperty("accessToken", account.accessToken);
        if (account.clientToken != null && !account.clientToken.isBlank()) {
            request.addProperty("clientToken", account.clientToken);
        }
        try {
            post("/auth/validate", request);
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    public static void invalidate(Account account) {
        if (account.accessToken == null || account.accessToken.isBlank()) {
            return;
        }
        JsonObject request = new JsonObject();
        request.addProperty("accessToken", account.accessToken);
        request.addProperty("clientToken", account.clientToken == null ? "" : account.clientToken);
        try {
            post("/auth/invalidate", request);
        } catch (IOException ignored) {
        }
    }


    public static void ensureValidToken(Account account) throws IOException {
        if (account.isOffline()) {
            return;
        }
        if (validate(account)) {
            return;
        }
        refresh(account);
    }

    private static JsonObject post(String path, JsonObject body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(AUTH_SERVER + path).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", USER_AGENT);

        byte[] payload = Json.GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(payload);
        }

        int code = conn.getResponseCode();
        String responseBody = readBody(conn, code);

        if (code == 200) {
            if (responseBody.isBlank()) {

                return new JsonObject();
            }
            return Json.parseObject(responseBody);
        }
        if (code == 204) {
            return new JsonObject();
        }

        throw buildError(code, responseBody);
    }

    private static String readBody(HttpURLConnection conn, int code) throws IOException {
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static AuthException buildError(int code, String body) {
        String errorType = "UnknownError";
        String message = "Authentication error (HTTP " + code + ")";

        if (body != null && !body.isBlank()) {
            try {
                JsonObject error = Json.parseObject(body);
                errorType = Json.string(error, "error", errorType);
                String serverMessage = Json.string(error, "errorMessage", null);
                if (serverMessage != null) {
                    message = translate(serverMessage);
                    if (serverMessage.toLowerCase().contains("two factor")) {
                        return new TwoFactorRequiredException(message);
                    }
                }
            } catch (RuntimeException ignored) {
                message = message + ": " + body.substring(0, Math.min(200, body.length()));
            }
        }
        return new AuthException(errorType, message);
    }


    private static String translate(String serverMessage) {
        String lower = serverMessage.toLowerCase();
        if (lower.contains("two factor")) {
            return "This account is protected by two-factor authentication; enter the code from your authenticator app";
        }
        if (lower.contains("invalid credentials")) {
            return "Invalid username or password";
        }
        if (lower.contains("token") && lower.contains("invalid")) {
            return "The token is invalid; sign in again";
        }
        if (lower.contains("account is not activated")) {
            return "The account is not activated; confirm your email address on ely.by";
        }
        if (lower.contains("banned") || lower.contains("blocked")) {
            return "The account is blocked";
        }
        return serverMessage;
    }
}
