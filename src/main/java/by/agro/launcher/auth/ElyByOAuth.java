package by.agro.launcher.auth;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.SafeNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Configurable OAuth 2.0 authorization-code flow with PKCE and a loopback callback. */
public final class ElyByOAuth {
    private static final SecureRandom RANDOM = new SecureRandom();
    private ElyByOAuth() { }

    public record Config(String authorizationEndpoint, String tokenEndpoint, String profileEndpoint,
                         String clientId, String scope, int callbackPort, int timeoutSeconds) {
        public boolean complete() {
            return nonBlank(authorizationEndpoint) && nonBlank(tokenEndpoint)
                    && nonBlank(profileEndpoint) && nonBlank(clientId);
        }
        private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    }

    public static Account authorize(Config config) throws IOException {
        validateEndpoints(config);
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new IOException("Opening a browser is not supported");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", config.callbackPort()), 0);
        CompletableFuture<Map<String, String>> callback = new CompletableFuture<>();
        String verifier = randomToken(64);
        String state = randomToken(32);
        String redirectUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth/callback";
        server.createContext("/oauth/callback", exchange -> handleCallback(exchange, callback, state));
        server.start();
        try {
            Desktop.getDesktop().browse(authorizationUri(config, redirectUri, state, challenge(verifier)));
            Map<String, String> query;
            try {
                query = callback.get(config.timeoutSeconds() > 0 ? config.timeoutSeconds() : 180, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new IOException("OAuth authorization timed out", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("OAuth authorization interrupted", e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new IOException("OAuth callback failed", e.getCause());
            }
            if (!state.equals(query.get("state"))) throw new IOException("OAuth state mismatch");
            if (query.containsKey("error")) throw new IOException("OAuth error: " + query.get("error")
                    + optionalDescription(query.get("error_description")));
            String code = query.get("code");
            if (code == null || code.isBlank()) throw new IOException("OAuth callback has no code");
            return exchangeCode(config, redirectUri, code, verifier);
        } finally {
            server.stop(0);
        }
    }

    public static Account refresh(Config config, Account account) throws IOException {
        validateEndpoints(config);
        if (account.clientToken == null || account.clientToken.isBlank())
            throw new IOException("OAuth refresh token is missing");
        JsonObject token = postForm(config.tokenEndpoint(), "grant_type=refresh_token&client_id="
                + enc(config.clientId()) + "&refresh_token=" + enc(account.clientToken));
        applyTokens(account, token);
        applyProfile(account, getJson(config.profileEndpoint(), account.accessToken));
        return account;
    }

    private static void validateEndpoints(Config config) throws IOException {
        if (config == null || !config.complete()) throw new IOException("Ely.by OAuth is not configured");
        requireHttps(config.authorizationEndpoint(), "authorization");
        requireHttps(config.tokenEndpoint(), "token");
        requireHttps(config.profileEndpoint(), "profile");
    }

    private static void requireHttps(String value, String name) throws IOException {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IOException("OAuth " + name + " endpoint is invalid", e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("OAuth " + name + " endpoint must use HTTPS");
        }
    }

    private static URI authorizationUri(Config c, String redirectUri, String state, String challenge) {
        String query = "response_type=code&client_id=" + enc(c.clientId()) + "&redirect_uri="
                + enc(redirectUri) + "&state=" + enc(state) + "&code_challenge=" + enc(challenge)
                + "&code_challenge_method=S256";
        if (c.scope() != null && !c.scope().isBlank()) query += "&scope=" + enc(c.scope());
        return URI.create(c.authorizationEndpoint() + (c.authorizationEndpoint().contains("?") ? "&" : "?") + query);
    }

    private static Account exchangeCode(Config c, String redirectUri, String code, String verifier) throws IOException {
        JsonObject token = postForm(c.tokenEndpoint(), "grant_type=authorization_code&client_id="
                + enc(c.clientId()) + "&redirect_uri=" + enc(redirectUri) + "&code=" + enc(code)
                + "&code_verifier=" + enc(verifier));
        Account account = new Account();
        account.type = Account.Type.ELY_BY_OAUTH;
        applyTokens(account, token);
        applyProfile(account, getJson(c.profileEndpoint(), account.accessToken));
        if (account.uuid.isBlank() || account.username.isBlank()) throw new IOException("OAuth profile is incomplete");
        account.id = "elyby-oauth-" + account.uuid;
        return account;
    }

    private static void applyTokens(Account account, JsonObject token) throws IOException {
        String access = Json.string(token, "access_token", "");
        if (access.isBlank()) throw new IOException("OAuth token response has no access_token");
        account.accessToken = access;
        String refresh = Json.string(token, "refresh_token", "");
        if (!refresh.isBlank()) account.clientToken = refresh;
        account.tokenUpdatedAt = System.currentTimeMillis();
        long expiresIn = token.has("expires_in") ? token.get("expires_in").getAsLong() : 0;
        account.tokenExpiresAt = expiresIn > 0 ? account.tokenUpdatedAt + expiresIn * 1000L : 0;
    }

    private static void applyProfile(Account account, JsonObject profile) {
        account.uuid = Json.string(profile, "id", Json.string(profile, "uuid", account.uuid));
        account.username = Json.string(profile, "name", Json.string(profile, "username", account.username));
        account.login = Json.string(profile, "email", account.login);
        account.skinUrl = skinUrl(profile, account.skinUrl);
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

    private static void handleCallback(HttpExchange exchange,
                                       CompletableFuture<Map<String, String>> callback,
                                       String expectedState) throws IOException {
        byte[] response = "Authorization received. You may close this window.".getBytes(StandardCharsets.UTF_8);
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!"/oauth/callback".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            if (!MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.US_ASCII),
                    query.getOrDefault("state", "").getBytes(StandardCharsets.US_ASCII))) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }
            if (!callback.complete(query)) {
                exchange.sendResponseHeaders(409, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
        } finally {
            exchange.close();
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return values;
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(dec(parts[0]), parts.length == 2 ? dec(parts[1]) : "");
        }
        return values;
    }

    private static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String challenge(String verifier) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private static JsonObject postForm(String url, String body) throws IOException {
        URI endpoint = SafeNetwork.requirePublicHttps(url);
        return send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }
    private static JsonObject getJson(String url, String token) throws IOException {
        URI endpoint = SafeNetwork.requirePublicHttps(url);
        return send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token).header("Accept", "application/json").GET().build());
    }
    private static JsonObject send(HttpRequest request) throws IOException {
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("OAuth request failed with HTTP " + response.statusCode());
            JsonObject json = Json.parseObject(response.body());
            if (json == null) throw new IOException("OAuth server returned an empty response");
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("OAuth request interrupted", e);
        }
    }
    private static String optionalDescription(String value) {
        return value == null || value.isBlank() ? "" : " (" + value + ")";
    }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String dec(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
}
