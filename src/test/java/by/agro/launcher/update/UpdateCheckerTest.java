package by.agro.launcher.update;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.Platform;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    private static final String REPOSITORY = "AgroV2/AgroLauncher";
    private static com.google.gson.JsonObject release(String tag) {
        var json = Json.parseObject("""
                {"tag_name":"placeholder","html_url":"https://github.com/AgroV2/AgroLauncher/releases/tag/AgroLauncher-v1.5.5",
                 "assets":[{"name":"AgroLauncher-Windows-x64.zip","browser_download_url":"https://example.test/windows.zip","size":20},
                           {"name":"AgroLauncher.jar","browser_download_url":"https://example.test/AgroLauncher.jar","size":10}]}
                """);
        json.addProperty("tag_name", tag);
        return json;
    }

    @Test void detectsRealReleaseTagAsNewerThanInstalledVersion() throws Exception {
        var update = UpdateChecker.parseRelease(release("AgroLauncher-v1.5.5"), REPOSITORY,
                "1.5.0", Platform.WINDOWS, "x64");
        assertNotNull(update);
        assertEquals("1.5.5", update.version());
        assertEquals("AgroLauncher.jar", update.asset().name());
        assertFalse(update.canAutoUpdate(), "unsigned releases must not auto-install");
    }

    @Test void selectsExactCompanionAssetsForSignedJar() throws Exception {
        var json = release("v1.5.6");
        var assets = json.getAsJsonArray("assets");
        assets.add(Json.parseObject("{\"name\":\"AgroLauncher.jar.manifest\",\"browser_download_url\":\"https://example.test/m\",\"size\":100}"));
        assets.add(Json.parseObject("{\"name\":\"AgroLauncher.jar.manifest.sig\",\"browser_download_url\":\"https://example.test/s\",\"size\":64}"));
        var update = UpdateChecker.parseRelease(json, REPOSITORY, "1.5.5", Platform.WINDOWS, "x64");
        assertEquals("AgroLauncher.jar.manifest", update.manifestAsset().name());
        assertEquals("AgroLauncher.jar.manifest.sig", update.signatureAsset().name());
    }

    @Test void acceptsAllSupportedTagFormats() {
        assertEquals("1.5.5", UpdateChecker.normalizeVersion("AgroLauncher-v1.5.5"));
        assertEquals("1.5.5", UpdateChecker.normalizeVersion("agrolauncher-V1.5.5"));
        assertEquals("1.5.5", UpdateChecker.normalizeVersion("AgroLauncher-1.5.5"));
        assertEquals("1.5.5", UpdateChecker.normalizeVersion("v1.5.5"));
        assertEquals("1.5.5", UpdateChecker.normalizeVersion("1.5.5"));
        assertEquals("1.5.5-rc.1+build.7", UpdateChecker.normalizeVersion("v1.5.5-rc.1+build.7"));
    }

    @Test void comparesNumericComponentsNotStringsOrPrefixes() {
        assertTrue(UpdateChecker.compare("AgroLauncher-v1.10.0", "1.9.9") > 0);
        assertTrue(UpdateChecker.compare("1.5.5", "1.6.0") < 0);
        assertEquals(0, UpdateChecker.compare("v1.5", "1.5.0"));
        assertTrue(UpdateChecker.compare("1.9999999999999999999999999", "1.2") > 0);
        assertEquals(0, UpdateChecker.compare("1.5.5-rc.1+build.7", "1.5.5"));
    }

    @Test void equalOrOlderReleaseIsNotAnUpdate() throws Exception {
        assertNull(UpdateChecker.parseRelease(release("AgroLauncher-v1.5.5"), REPOSITORY, "1.5.5", Platform.WINDOWS, "x64"));
        assertNull(UpdateChecker.parseRelease(release("v1.4.5"), REPOSITORY, "1.5.0", Platform.WINDOWS, "x64"));
    }

    @Test void invalidOrMissingVersionIsAnErrorNotLatest() {
        for (String tag : new String[]{"", "Stable", "AgroLauncher-v", "AgroLauncher-v1..5", "AgroLauncher-v1.5junk", "foo-1.5.5"}) {
            assertThrows(IOException.class, () -> UpdateChecker.parseRelease(release(tag), REPOSITORY,
                    "1.5.0", Platform.WINDOWS, "x64"), tag);
        }
        var missing = release("v1.5.5"); missing.remove("tag_name");
        assertThrows(IOException.class, () -> UpdateChecker.parseRelease(missing, REPOSITORY, "1.5.0", Platform.WINDOWS, "x64"));
        assertThrows(IOException.class, () -> UpdateChecker.parseRelease(release("v1.5.5"), REPOSITORY, "invalid", Platform.WINDOWS, "x64"));
        assertThrows(IOException.class, () -> UpdateChecker.latest(null, "", "1.5.0"));
    }
}
