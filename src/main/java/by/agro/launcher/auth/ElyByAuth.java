package by.agro.launcher.auth;

import by.agro.launcher.core.Json;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;


public final class ElyByAuth {

    public static final String AUTH_SERVER = "https://authserver.ely.by";
    public static final String INJECTOR_ARGUMENT = "ely.by";

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
        } else {
            throw new AuthException("IllegalArgumentException",
                    "Сервер не вернул профиль игрока. Проверьте, привязан ли ник к аккаунту Ely.by.");
        }
        account.id = "elyby-" + account.uuid;
        return account;
    }


    public static void refresh(Account account) throws IOException {
        if (account.accessToken == null || account.accessToken.isBlank()
                || account.clientToken == null || account.clientToken.isBlank()) {
            throw new AuthException("IllegalArgumentException",
                    "Недостаточно данных для обновления токена — требуется повторный вход");
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
        String message = "Ошибка авторизации (HTTP " + code + ")";

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
            return "Аккаунт защищён двухфакторной аутентификацией — введите код из приложения";
        }
        if (lower.contains("invalid credentials")) {
            return "Неверный логин или пароль";
        }
        if (lower.contains("token") && lower.contains("invalid")) {
            return "Токен недействителен — требуется повторный вход";
        }
        if (lower.contains("account is not activated")) {
            return "Аккаунт не активирован — подтвердите e-mail на ely.by";
        }
        if (lower.contains("banned") || lower.contains("blocked")) {
            return "Аккаунт заблокирован";
        }
        return serverMessage;
    }
}
