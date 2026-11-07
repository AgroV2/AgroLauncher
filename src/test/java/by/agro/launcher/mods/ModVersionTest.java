package by.agro.launcher.mods;

import org.junit.jupiter.api.Test;

import java.util.List;

import static by.agro.launcher.mods.ModVersion.Match.NO;
import static by.agro.launcher.mods.ModVersion.Match.UNKNOWN;
import static by.agro.launcher.mods.ModVersion.Match.YES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModVersionTest {

    @Test
    void comparesNumericAndPreReleaseVersions() {
        assertTrue(ModVersion.parse("1.20.10").compareTo(ModVersion.parse("1.20.2")) > 0);
        assertTrue(ModVersion.parse("1.0.0-beta.2").compareTo(ModVersion.parse("1.0.0")) < 0);
        assertEquals(0, ModVersion.parse("0.15.3+build.5").compareTo(ModVersion.parse("0.15.3")));
        assertEquals(0, ModVersion.parse("1.20").compareTo(ModVersion.parse("1.20.0")));
    }

    @Test
    void fabricPredicates() {
        assertEquals(YES, ModVersion.matchesFabric("1.20.1", "~1.20"));
        assertEquals(YES, ModVersion.matchesFabric("1.20.4", ">=1.20.2 <1.21"));
        assertEquals(NO, ModVersion.matchesFabric("1.21", ">=1.20.2 <1.21"));
        assertEquals(YES, ModVersion.matchesFabric("1.20.6", "1.20.x"));
        assertEquals(NO, ModVersion.matchesFabric("1.19.4", "1.20.x"));
        assertEquals(YES, ModVersion.matchesFabric("0.16.0", "^0.15.0".replace("^", ">=")));
        assertEquals(NO, ModVersion.matchesFabric("2.0.0", "^1.4"));
        assertEquals(YES, ModVersion.matchesFabric("anything", "*"));
        assertEquals(UNKNOWN, ModVersion.matchesFabric("23w45a", ">=1.20"));
        assertEquals(YES, ModVersion.matchesAnyFabric("1.19.2", List.of("1.20.1", "1.19.2")));
    }

    @Test
    void mavenRanges() {
        assertEquals(YES, ModVersion.matchesMaven("1.20.1", "[1.20.1,1.21)"));
        assertEquals(NO, ModVersion.matchesMaven("1.21", "[1.20.1,1.21)"));
        assertEquals(YES, ModVersion.matchesMaven("47.2.0", "[47,)"));
        assertEquals(NO, ModVersion.matchesMaven("46.0.1", "[47,)"));
        assertEquals(YES, ModVersion.matchesMaven("1.20.1", "[1.20.1]"));
        assertEquals(YES, ModVersion.matchesMaven("3.5", "[1.0,2.0),[3.0,)"));
        assertEquals(YES, ModVersion.matchesMaven("1.0", "1.5"));
    }
}
