package by.agro.launcher.auth;

import by.agro.launcher.core.Json;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class MicrosoftAuthTest {
    private static final String UUID = "0123456789abcdef0123456789abcdef";
    private static JsonObject j(String s) { return Json.parseObject(s); }
    static final class Fake implements MicrosoftAuth.Transport {
        int failMinecraft = 0, profileStatus = 200; String uuid = UUID; String entitlementJson = "{\"items\":[{\"name\":\"game_minecraft\"}]}";
        public MicrosoftAuth.Response form(String url, String body) {
            assertEquals(MicrosoftAuth.TOKEN, url); assertTrue(body.contains("grant_type=refresh_token"));
            assertFalse(body.contains("client_secret"));
            return new MicrosoftAuth.Response(200, j("{\"access_token\":\"msa-test\",\"refresh_token\":\"rotated-test\"}"));
        }
        public MicrosoftAuth.Response post(String url, JsonObject body) {
            if (url.equals(MicrosoftAuth.XBL)) {
                assertEquals("d=msa-test", body.getAsJsonObject("Properties").get("RpsTicket").getAsString());
                return new MicrosoftAuth.Response(200, j("{\"Token\":\"xbl-test\"}"));
            }
            if (url.equals(MicrosoftAuth.XSTS)) {
                assertEquals("rp://api.minecraftservices.com/", body.get("RelyingParty").getAsString());
                assertEquals("xbl-test", body.getAsJsonObject("Properties").getAsJsonArray("UserTokens").get(0).getAsString());
                return new MicrosoftAuth.Response(200, j("{\"Token\":\"xsts-test\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"userhash\"}]}}"));
            }
            assertEquals(MicrosoftAuth.MC_LOGIN, url);
            assertEquals("XBL3.0 x=userhash;xsts-test", body.get("identityToken").getAsString());
            return failMinecraft != 0 ? new MicrosoftAuth.Response(failMinecraft, new JsonObject()) :
                    new MicrosoftAuth.Response(200, j("{\"access_token\":\"minecraft-test\",\"expires_in\":3600}"));
        }
        public MicrosoftAuth.Response get(String url, String accessToken) {
            assertEquals("minecraft-test", accessToken);
            if (url.equals(MicrosoftAuth.MC_ENTITLEMENTS)) return new MicrosoftAuth.Response(200, j(entitlementJson));
            assertEquals(MicrosoftAuth.MC_PROFILE, url);
            return new MicrosoftAuth.Response(profileStatus, j("{\"id\":\"" + uuid + "\",\"name\":\"Player_1\"}"));
        }
    }
    @Test void oauthPkceAndChainProduceMinecraftAccount() throws Exception {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", MicrosoftAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
        URI uri = MicrosoftAuth.authorizationUri("http://localhost:54321/", "state", "challenge");
        Map<String,String> q = MicrosoftAuth.query(uri.getRawQuery());
        assertEquals(MicrosoftAuth.CLIENT_ID, q.get("client_id")); assertEquals("S256", q.get("code_challenge_method"));
        assertEquals("http://localhost:54321/", q.get("redirect_uri")); assertTrue(q.get("scope").contains("XboxLive.signin"));
        Account a = MicrosoftAuth.exchange(new Fake(), "msa-test", "refresh-test");
        assertEquals(Account.Type.MICROSOFT, a.type); assertEquals("msa", a.userType());
        assertEquals("minecraft-test", a.accessToken); assertEquals("refresh-test", a.clientToken);
        assertEquals("microsoft-" + UUID, a.id); assertTrue(a.tokenExpiresAt > System.currentTimeMillis());
    }
    @Test void failedApprovalAndMissingProfileAreNotClaimedAsSuccess() {
        Fake f = new Fake(); f.failMinecraft = 403;
        IOException blocked = assertThrows(IOException.class, () -> MicrosoftAuth.exchange(f, "msa-test", "refresh"));
        assertTrue(blocked.getMessage().contains("403")); assertFalse(blocked.getMessage().contains("xsts-test"));
        f.failMinecraft = 0; f.profileStatus = 404;
        assertThrows(IOException.class, () -> MicrosoftAuth.exchange(f, "msa-test", "refresh"));
    }
    @Test void requiresActualEntitlementNotJustProfile() throws Exception {
        Fake f = new Fake(); f.entitlementJson = "{\"items\":[]}";
        assertThrows(IOException.class, () -> MicrosoftAuth.exchange(f, "msa-test", "refresh"));
        f.entitlementJson = "{\"items\":[{\"name\":\"product_minecraft\"}]}";
        assertNotNull(MicrosoftAuth.exchange(f, "msa-test", "refresh"));
        f.entitlementJson = "{}";
        assertThrows(IOException.class, () -> MicrosoftAuth.exchange(f, "msa-test", "refresh"));
    }
    @Test void refreshRotatesTokenAndRejectsDifferentProfile() throws Exception {
        Fake f = new Fake(); Account a = MicrosoftAuth.exchange(f, "msa-test", "old");
        MicrosoftAuth.refresh(f, a); assertEquals("rotated-test", a.clientToken);
        f.uuid = "fedcba9876543210fedcba9876543210";
        assertThrows(IOException.class, () -> MicrosoftAuth.refresh(f, a)); assertEquals(UUID, a.uuid);
    }
    @Test void loopbackRejectsWrongStateAndDuplicateParameters() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CompletableFuture<Map<String,String>> result = new CompletableFuture<>();
        server.createContext("/", x -> MicrosoftAuth.handleCallback(x, "expected", result)); server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            assertEquals(400, code(base + "?state=wrong&code=test")); assertFalse(result.isDone());
            assertEquals(400, code(base + "?state=expected&state=other&code=test")); assertFalse(result.isDone());
            assertEquals(404, code(base + "other?state=expected&code=test"));
            assertEquals(200, code(base + "?state=expected&code=test"));
            assertEquals("test", result.get(1, TimeUnit.SECONDS).get("code"));
        } finally { server.stop(0); }
    }
    private static int code(String url) throws Exception { HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); try { return c.getResponseCode(); } finally { c.disconnect(); } }
}
