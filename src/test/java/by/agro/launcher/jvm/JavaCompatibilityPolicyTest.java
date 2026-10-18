package by.agro.launcher.jvm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaCompatibilityPolicyTest {

    @Test
    void autoRequiresExactJava8ForLegacyProfiles() {
        assertFalse(JavaManager.isAutoCompatible(8, 21));
        assertTrue(JavaManager.isAutoCompatible(8, 8));
    }

    @Test
    void autoKeepsCurrentMinimumPolicyForModernProfiles() {
        assertTrue(JavaManager.isAutoCompatible(17, 21));
        assertFalse(JavaManager.isAutoCompatible(21, 17));
    }
}
