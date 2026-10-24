package by.agro.launcher.auth;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineAuthUuidTest {

    @Test
    void offlineUuidMatchesServerAlgorithmReferenceVectors() {
        assertEquals(UUID.fromString("5627dd98-e6be-3c21-b8a8-e92344183641"),
                OfflineAuth.offlineUuid("Steve"));
        assertEquals(UUID.fromString("36532b5e-c442-3dbb-a24c-c7e55d0f979a"),
                OfflineAuth.offlineUuid("Alex"));
        assertEquals(UUID.fromString("a01e3843-e521-3998-958a-f459800e4d11"),
                OfflineAuth.offlineUuid("Player"));
    }

    @Test
    void uuidStringFormIsUndashedLowerCase() {
        assertEquals("5627dd98e6be3c21b8a8e92344183641", OfflineAuth.uuidFor("Steve"));
    }

    @Test
    void usernameValidation() {
        assertTrue(OfflineAuth.isValidUsername("Steve_123"));
        assertFalse(OfflineAuth.isValidUsername("ab"));
        assertFalse(OfflineAuth.isValidUsername("слишком-длинный-ник"));
        assertFalse(OfflineAuth.isValidUsername("space inside"));
        assertFalse(OfflineAuth.isValidUsername(null));
    }
}
