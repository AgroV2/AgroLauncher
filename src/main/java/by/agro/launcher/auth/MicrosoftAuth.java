package by.agro.launcher.auth;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.SafeNetwork;
import by.agro.launcher.i18n.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public final class MicrosoftAuth {
    public static final String CLIENT_ID = "42a1fa65-f2e3-4b5b-b63c-675cd7bb2415";
    static final String AUTHORIZE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    static final String TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    static final String XBL = "https://user.auth.xboxlive.com/user/authenticate";
    static final String XSTS = "https://xsts.auth.xboxlive.com/xsts/authorize";
    static final String MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox";
    static final String MC_ENTITLEMENTS = "https://api.minecraftservices.com/entitlements/mcstore";
    static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";
    static final String SCOPE = "XboxLive.signin offline_access";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_RESPONSE = 1024 * 1024;
    private MicrosoftAuth() { }

    record Response(int status, JsonObject json) { }
    interface Transport {
        Response form(String url, String body) throws IOException;
        Response post(String url, JsonObject body) throws IOException;
        Response get(String url, String accessToken) throws IOException;
    }
    private static final Transport HTTP = new HttpTransport();

    @FunctionalInterface public interface BrowserFallback { boolean openManually(URI uri) throws IOException; }

    public static Account authorize() throws IOException { return authorize(null); }

    public static Account authorize(BrowserFallback fallback) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String redirect = "http://localhost:" + server.getAddress().getPort() + "/";
        String state = random(32), verifier = random(48);
        CompletableFuture<Map<String, String>> callback = new CompletableFuture<>();
        server.createContext("/", exchange -> handleCallback(exchange, state, callback));
        server.start();
        try {
            URI uri = authorizationUri(redirect, state, challenge(verifier));
            try { BrowserOpener.open(uri); }
            catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) throw new IOException(Strings.get("microsoft.interrupted"), e);
                if (fallback == null) throw new IOException(Strings.get("microsoft.browserUnavailable"), e);
                if (!fallback.openManually(uri)) throw new IOException(Strings.get("microsoft.denied"));
            }
            Map<String, String> result;
            try { result = callback.get(180, TimeUnit.SECONDS); }
            catch (java.util.concurrent.TimeoutException e) { throw new IOException(Strings.get("microsoft.timeout"), e); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(Strings.get("microsoft.interrupted"), e); }
            catch (java.util.concurrent.ExecutionException e) { throw new IOException(Strings.get("microsoft.callbackFailed"), e); }
            if (result.containsKey("error")) throw new IOException(Strings.get("microsoft.denied"));
            String code = result.get("code");
            if (code == null || code.isBlank()) throw new IOException(Strings.get("microsoft.callbackFailed"));
            Response tokens = HTTP.form(TOKEN, "client_id=" + enc(CLIENT_ID) + "&grant_type=authorization_code&code="
                    + enc(code) + "&redirect_uri=" + enc(redirect) + "&code_verifier=" + enc(verifier)
                    + "&scope=" + enc(SCOPE));
            JsonObject microsoft = successful(tokens, "Microsoft");
            String access = required(microsoft, "access_token"), refresh = required(microsoft, "refresh_token");
            Account account = exchange(HTTP, access, refresh);
            return account;
        } finally { server.stop(0); }
    }

    public static void refresh(Account account) throws IOException { refresh(HTTP, account); }
    static void refresh(Transport http, Account account) throws IOException {
        if (account.clientToken == null || account.clientToken.isBlank()) throw new IOException(Strings.get("microsoft.signInAgain"));
        JsonObject response = successful(http.form(TOKEN, "client_id=" + enc(CLIENT_ID)
                + "&grant_type=refresh_token&refresh_token=" + enc(account.clientToken) + "&scope=" + enc(SCOPE)), "Microsoft");
        String access = required(response, "access_token");
        String rotated = Json.string(response, "refresh_token", "");
        if (!rotated.isBlank()) account.clientToken = rotated;
        Account fresh = exchange(http, access, account.clientToken);
        if (!fresh.uuid.equalsIgnoreCase(account.uuid)) throw new IOException(Strings.get("microsoft.accountChanged"));
        account.username = fresh.username;
        account.accessToken = fresh.accessToken;
        account.tokenUpdatedAt = fresh.tokenUpdatedAt;
        account.tokenExpiresAt = fresh.tokenExpiresAt;
    }

    static Account exchange(Transport http, String microsoftToken, String refreshToken) throws IOException {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS"); props.addProperty("SiteName", "user.auth.xboxlive.com"); props.addProperty("RpsTicket", "d=" + microsoftToken);
        JsonObject xblRequest = request(props, "http://auth.xboxlive.com");
        String xbl = required(successful(http.post(XBL, xblRequest), "Xbox Live"), "Token");
        JsonObject xstsProps = new JsonObject(); xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray(); userTokens.add(xbl); xstsProps.add("UserTokens", userTokens);
        Response xstsResponse = http.post(XSTS, request(xstsProps, "rp://api.minecraftservices.com/"));
        if (xstsResponse.status() < 200 || xstsResponse.status() >= 300) {
            String xerr = "";
            try {
                if (xstsResponse.json() != null && xstsResponse.json().has("XErr")) {
                    long code = xstsResponse.json().get("XErr").getAsLong();
                    if (code >= 0) xerr = Long.toString(code);
                }
            } catch (RuntimeException ignored) { }
            throw new IOException(Strings.get("microsoft.xstsFailed", xstsResponse.status(), xerr.isBlank() ? "—" : xerr));
        }
        JsonObject xsts = xstsResponse.json(); String xstsToken = required(xsts, "Token"), uhs;
        try { uhs = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString(); }
        catch (RuntimeException e) { throw new IOException(Strings.get("microsoft.invalidResponse"), e); }
        if (uhs == null || uhs.isBlank() || uhs.indexOf(';') >= 0) throw new IOException(Strings.get("microsoft.invalidResponse"));
        JsonObject mcRequest = new JsonObject(); mcRequest.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        Response mcResponse = http.post(MC_LOGIN, mcRequest);
        if (mcResponse.status() == 403) throw new IOException(Strings.get("microsoft.appNotApproved"));
        JsonObject mc = successful(mcResponse, "Minecraft Services"); String token = required(mc, "access_token");
        requireOwnership(http, token);
        Response profileResponse = http.get(MC_PROFILE, token);
        if (profileResponse.status() == 404) throw new IOException(Strings.get("microsoft.noProfile"));
        JsonObject profile = successful(profileResponse, "Minecraft profile");
        String uuid = required(profile, "id"), name = required(profile, "name");
        if (!uuid.matches("[0-9a-fA-F]{32}") || !name.matches("[A-Za-z0-9_]{3,16}"))
            throw new IOException(Strings.get("microsoft.invalidResponse"));
        long expires;
        try { expires = mc.get("expires_in").getAsLong(); }
        catch (RuntimeException e) { throw new IOException(Strings.get("microsoft.invalidResponse"), e); }
        if (expires <= 0 || expires > 31_536_000L) throw new IOException(Strings.get("microsoft.invalidResponse"));
        Account account = new Account(); account.type = Account.Type.MICROSOFT;
        account.uuid = uuid.toLowerCase(java.util.Locale.ROOT); account.username = name; account.id = "microsoft-" + account.uuid;
        account.accessToken = token; account.clientToken = refreshToken;
        account.tokenUpdatedAt = System.currentTimeMillis(); account.tokenExpiresAt = account.tokenUpdatedAt + expires * 1000L;
        return account;
    }

    public static void requireOwnership(Account account) throws IOException {
        if (account == null || account.type != Account.Type.MICROSOFT
                || account.accessToken == null || account.accessToken.isBlank())
            throw new IOException(Strings.get("microsoft.signInAgain"));
        requireOwnership(HTTP, account.accessToken);
        Response response = HTTP.get(MC_PROFILE, account.accessToken);
        if (response.status() == 404) throw new IOException(Strings.get("microsoft.noProfile"));
        JsonObject profile = successful(response, "Minecraft profile");
        String uuid = required(profile, "id"), name = required(profile, "name");
        if (!uuid.matches("[0-9a-fA-F]{32}") || !name.matches("[A-Za-z0-9_]{3,16}")
                || account.uuid == null || !uuid.equalsIgnoreCase(account.uuid.replace("-", "")))
            throw new IOException(Strings.get("microsoft.accountChanged"));
        account.username = name;
    }

    static void requireOwnership(Transport http, String token) throws IOException {
        JsonObject result = successful(http.get(MC_ENTITLEMENTS, token), "Minecraft entitlements");
        try {
            JsonArray items = result.getAsJsonArray("items");
            if (items == null) throw new IllegalArgumentException();
            for (var item : items) {
                if (!item.isJsonObject()) throw new IllegalArgumentException();
                String name = Json.string(item.getAsJsonObject(), "name", "");
                if ("game_minecraft".equals(name) || "product_minecraft".equals(name)) return;
            }
        } catch (RuntimeException e) { throw new IOException(Strings.get("microsoft.invalidResponse"), e); }
        throw new IOException(Strings.get("microsoft.noOwnership"));
    }

    private static JsonObject request(JsonObject properties, String relyingParty) {
        JsonObject req = new JsonObject(); req.add("Properties", properties); req.addProperty("RelyingParty", relyingParty); req.addProperty("TokenType", "JWT"); return req;
    }
    private static JsonObject successful(Response r, String stage) throws IOException {
        if (r.status() < 200 || r.status() >= 300) throw new IOException(Strings.get("microsoft.httpError", stage, r.status()));
        if (r.json() == null) throw new IOException(Strings.get("microsoft.invalidResponse"));
        return r.json();
    }
    private static String required(JsonObject object, String key) throws IOException {
        String s;
        try { s = Json.string(object, key, ""); } catch (RuntimeException e) { throw new IOException(Strings.get("microsoft.invalidResponse"), e); }
        if (s.isBlank()) throw new IOException(Strings.get("microsoft.invalidResponse")); return s;
    }
    static URI authorizationUri(String redirect, String state, String challenge) {
        return URI.create(AUTHORIZE + "?client_id=" + enc(CLIENT_ID) + "&response_type=code&redirect_uri=" + enc(redirect)
                + "&response_mode=query&scope=" + enc(SCOPE) + "&state=" + enc(state)
                + "&code_challenge=" + enc(challenge) + "&code_challenge_method=S256");
    }
    static String challenge(String verifier) throws IOException {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    private static String random(int length) { byte[] b = new byte[length]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static Map<String,String> query(String raw) throws IOException {
        Map<String,String> out = new HashMap<>(); if (raw == null || raw.length() > 8192) throw new IOException("Invalid callback");
        try {
            for (String pair : raw.split("&")) {
                String[] p = pair.split("=", 2); String key = URLDecoder.decode(p[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(p.length == 2 ? p[1] : "", StandardCharsets.UTF_8);
                if (out.putIfAbsent(key, value) != null) throw new IOException("Duplicate callback parameter");
            }
        } catch (IllegalArgumentException e) { throw new IOException("Invalid callback", e); }
        return out;
    }
    static void handleCallback(HttpExchange exchange, String state, CompletableFuture<Map<String,String>> callback) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod()) || !"/".equals(exchange.getRequestURI().getPath())) { exchange.sendResponseHeaders(404, -1); return; }
            Map<String,String> params;
            try { params = query(exchange.getRequestURI().getRawQuery()); } catch (IOException e) { exchange.sendResponseHeaders(400, -1); return; }
            if (!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8), params.getOrDefault("state", "").getBytes(StandardCharsets.UTF_8))) { exchange.sendResponseHeaders(400, -1); return; }
            if (!params.containsKey("code") && !params.containsKey("error")) { exchange.sendResponseHeaders(400, -1); return; }
            if (!callback.complete(params)) { exchange.sendResponseHeaders(409, -1); return; }
            byte[] body = Strings.get(params.containsKey("error") ? "microsoft.browserDenied" : "microsoft.browserReceived").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
        } finally { exchange.close(); }
    }
    private static final class HttpTransport implements Transport {
        public Response form(String url, String body) throws IOException { return send(url, "application/x-www-form-urlencoded", body, null); }
        public Response post(String url, JsonObject body) throws IOException { return send(url, "application/json", body.toString(), null); }
        public Response get(String url, String token) throws IOException { return send(url, null, null, token); }
        private Response send(String url, String contentType, String body, String token) throws IOException {

            var c = SafeNetwork.openConnection(SafeNetwork.requirePublicHttps(url), Downloader.configuredProxy());
            c.setConnectTimeout(20_000); c.setReadTimeout(20_000); c.setRequestProperty("Accept", "application/json");
            try {
                if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8); c.setRequestMethod("POST"); c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", contentType); c.setFixedLengthStreamingMode(bytes.length);
                    try (var out = c.getOutputStream()) { out.write(bytes); }
                }
                int status = c.getResponseCode();
                java.io.InputStream stream = status < 400 ? c.getInputStream() : c.getErrorStream();
                JsonObject json = new JsonObject();
                if (stream != null) try (stream) {
                    byte[] b = stream.readNBytes(MAX_RESPONSE + 1); if (b.length > MAX_RESPONSE) throw new IOException(Strings.get("microsoft.invalidResponse"));
                    try { JsonObject parsed = Json.parseObject(new String(b, StandardCharsets.UTF_8)); if (parsed != null) json = parsed; }
                    catch (RuntimeException e) { if (status >= 200 && status < 300) throw new IOException(Strings.get("microsoft.invalidResponse"), e); }
                }
                return new Response(status, json);
            } finally { c.disconnect(); }
        }
    }
}
