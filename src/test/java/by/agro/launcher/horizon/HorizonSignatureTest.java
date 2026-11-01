package by.agro.launcher.horizon;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class HorizonSignatureTest {
    private byte[] fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/horizon/" + name)) {
            assertNotNull(in);
            return in.readAllBytes();
        }
    }

    @Test void verifiesActualSignedRelease() throws Exception {
        byte[] sums = fixture("SHA256SUMS");
        String signature = new String(fixture("SHA256SUMS.sig"), StandardCharsets.UTF_8);
        assertTrue(HorizonSignature.verify("nightly", sums, signature));
        assertTrue(HorizonSignature.verify("nightly", sums, "not-base64\n" + signature));
        assertFalse(HorizonSignature.verify("stable", sums, signature));
        sums[0] ^= 1;
        assertFalse(HorizonSignature.verify("nightly", sums, signature));
    }

    @Test void refusesInvalidKeysAndSignatures() {
        assertFalse(HorizonSignature.verify("nightly", new byte[0], ""));
        assertFalse(HorizonSignature.verify("nightly", null, "anything"));
        assertFalse(HorizonSignature.verify("nightly", new byte[0], "AAAA"));
        assertFalse(HorizonSignature.verify("bad key", "nightly", new byte[0], "AAAA"));
        assertFalse(HorizonSignature.verify("AAAA", "nightly", new byte[0], "AAAA"));
    }

    @Test void parsesOnlyValidSumsIncludingBinaryMode() throws Exception {
        var sums = HorizonSignature.parseSums(new String(fixture("SHA256SUMS"), StandardCharsets.UTF_8));
        assertEquals(6, sums.size());
        assertEquals("feab1879f35338e7cde06f11ae0861832d8cd0d915ee64a94ed5769b8ddd2e8a",
                sums.get("horizon-linux-x86_64.tar.gz"));
        String hash = "A".repeat(64);
        var parsed = HorizonSignature.parseSums("bad\n" + "x".repeat(64) + "  bad.zip\n" + hash + " *test.zip\r\n");
        assertEquals(1, parsed.size());
        assertEquals(hash.toLowerCase(java.util.Locale.ROOT), parsed.get("test.zip"));
    }
}
