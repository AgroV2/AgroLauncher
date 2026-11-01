package by.agro.launcher.ui.panels;

import by.agro.launcher.core.Settings;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.version.RemoteVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VersionChoicesTest {
    private RemoteVersion version(String id) { return new RemoteVersion(id, "release", null, null, null); }
    private List<String> ids(List<RemoteVersion> versions) { return versions.stream().map(v -> v.id).toList(); }

    @Test void rustIsImmediatelyAboveVanilla263WithoutReorderingOtherVersions() {
        var source = List.of(version("26.4"), version("26.3"), version("26.2"), version("1.21"));
        assertEquals(List.of("26.4", "26.3 Rust", "26.3", "26.2", "1.21"), ids(VersionChoices.withRust(source)));
        assertEquals(List.of("26.4", "26.3", "26.2", "1.21"), ids(source));
    }

    @Test void rustRemainsAvailableWithoutBaseVersionOrNetwork() {
        assertEquals(List.of("26.4", "1.21", "26.3 Rust"), ids(VersionChoices.withRust(List.of(version("26.4"), version("1.21")))));
        assertEquals(List.of("26.3 Rust"), ids(VersionChoices.withRust(List.of())));
    }

    @Test void rebuildingDoesNotDuplicateRust() {
        var choices = VersionChoices.withRust(List.of(version("26.3")));
        assertEquals(ids(choices), ids(VersionChoices.withRust(choices)));
    }

    @Test void rustIsNotOfferedAsAModLoader() {
        assertFalse(VersionChoices.javaLoaders().contains(LoaderType.HORIZON));
        assertTrue(VersionChoices.javaLoaders().containsAll(List.of(LoaderType.VANILLA, LoaderType.FABRIC,
                LoaderType.QUILT, LoaderType.FORGE, LoaderType.NEOFORGE, LoaderType.OPTIFINE)));
    }

    @Test void choosingRustPreservesTheRealMinecraftIdForDownloads() {
        Settings settings = new Settings();
        settings.selectedLoader = "forge"; settings.selectedLoaderVersion = "old-forge";
        settings.selectedModBuildId = "java-mod-build";
        VersionChoices.select(VersionChoices.rust(), settings);
        assertEquals("26.3", settings.selectedVersion);
        assertEquals("horizon", settings.selectedLoader);
        assertEquals("nightly", settings.selectedLoaderVersion);
        assertEquals("", settings.selectedModBuildId);
    }

    @Test void vanilla263AndRustAreDifferentSelections() {
        Settings settings = new Settings();
        VersionChoices.select(VersionChoices.rust(), settings);
        assertTrue(VersionChoices.matches(VersionChoices.rust(), settings));
        assertFalse(VersionChoices.matches(version("26.3"), settings));
        VersionChoices.select(version("26.3"), settings);
        assertEquals("vanilla", settings.selectedLoader);
        assertEquals("", settings.selectedLoaderVersion);
        assertTrue(VersionChoices.matches(version("26.3"), settings));
        assertFalse(VersionChoices.matches(VersionChoices.rust(), settings));
    }

    @Test void leavingRustForAnotherVersionCannotLaunchItWithHorizon() {
        Settings settings = new Settings();
        VersionChoices.select(VersionChoices.rust(), settings);
        VersionChoices.select(version("1.21"), settings);
        assertEquals("1.21", settings.selectedVersion);
        assertEquals("vanilla", settings.selectedLoader);
    }

    @Test void ordinaryVersionSelectionKeepsTheJavaLoader() {
        Settings settings = new Settings(); settings.selectedLoader = "fabric"; settings.selectedLoaderVersion = "test";
        VersionChoices.select(version("26.3"), settings);
        assertEquals("fabric", settings.selectedLoader);
        assertEquals("test", settings.selectedLoaderVersion);
    }
}
