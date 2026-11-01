package by.agro.launcher.horizon;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HorizonSignature {

    static final String RELEASE_PUBLIC_KEY = "MU4oiIQcCxkZvbwSjjXs7SySCuM6ywRQ3nGaJYZG0gw=";
    private static final byte[] X509_ED25519_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};

    private HorizonSignature() {
    }

    public static boolean verify(String channel, byte[] sums, String signatures) {
        return verify(RELEASE_PUBLIC_KEY, channel, sums, signatures);
    }

    static boolean verify(String publicKeyBase64, String channel, byte[] sums, String signatures) {
        if (sums == null || signatures == null) {
            return false;
        }
        PublicKey key;
        try {
            byte[] raw = Base64.getDecoder().decode(publicKeyBase64);
            if (raw.length != 32) {
                return false;
            }
            byte[] der = new byte[X509_ED25519_PREFIX.length + raw.length];
            System.arraycopy(X509_ED25519_PREFIX, 0, der, 0, X509_ED25519_PREFIX.length);
            System.arraycopy(raw, 0, der, X509_ED25519_PREFIX.length, raw.length);
            key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
        byte[] prefix = ("horizon release " + channel + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] message = new byte[prefix.length + sums.length];
        System.arraycopy(prefix, 0, message, 0, prefix.length);
        System.arraycopy(sums, 0, message, prefix.length, sums.length);
        for (String line : signatures.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                byte[] signature = Base64.getDecoder().decode(trimmed);
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(key);
                verifier.update(message);
                if (verifier.verify(signature)) {
                    return true;
                }
            } catch (GeneralSecurityException | IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    public static Map<String, String> parseSums(String sums) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : sums.split("\\R")) {
            String trimmed = line.trim();
            int space = trimmed.indexOf(' ');
            if (space != 64) {
                continue;
            }
            String hash = trimmed.substring(0, 64).toLowerCase(Locale.ROOT);
            if (!hash.matches("[0-9a-f]{64}")) {
                continue;
            }
            String name = trimmed.substring(space).trim();
            if (name.startsWith("*")) {
                name = name.substring(1);
            }
            if (!name.isEmpty()) {
                result.put(name, hash);
            }
        }
        return result;
    }
}
