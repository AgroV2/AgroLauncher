package by.agro.launcher.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Pattern;


final class UpdateSignature {
    private static final String RESOURCE = "/update/trusted-public-key.pem";
    private static final Pattern MANIFEST = Pattern.compile(
            "\\AAgroLauncher-update-v1\\nversion=([0-9]+(?:\\.[0-9]+)*(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?)\\n"
            + "artifact=([A-Za-z0-9._-]+\\.jar)\\nsize=([1-9][0-9]{0,9})\\nsha256=([0-9a-f]{64})\\n\\z");
    record Manifest(String version, String artifact, long size, String sha256) { }
    private UpdateSignature() { }

    static boolean hasTrustedKey() {
        try { trustedKey(); return true; }
        catch (IOException e) { return false; }
    }

    static PublicKey trustedKey() throws IOException {
        try (var input = UpdateSignature.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IOException("Trusted update signing key is not configured; see docs/SIGNED_UPDATES.md");
            byte[] pem = input.readNBytes(4097);
            if (pem.length > 4096) throw new IOException("Update public key is too large");
            return readPublicKey(new String(pem, StandardCharsets.US_ASCII));
        }
    }

    static PublicKey readPublicKey(String pem) throws IOException {
        try {
            String text = pem.trim();
            String begin = "-----BEGIN PUBLIC KEY-----", end = "-----END PUBLIC KEY-----";
            if (!text.startsWith(begin) || !text.endsWith(end)) throw new IllegalArgumentException("PEM format");
            String encoded = text.substring(begin.length(), text.length() - end.length()).replaceAll("\\s", "");
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IOException("Invalid Ed25519 update public key", e);
        }
    }

    static Manifest verify(byte[] data, byte[] signature, PublicKey key, String expectedVersion,
                           String expectedArtifact) throws IOException {
        if (data == null || data.length == 0 || data.length > 4096 || signature == null || signature.length != 64 || key == null)
            throw new IOException("Missing or invalid signed update metadata");
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key); verifier.update(data);
            if (!verifier.verify(signature)) throw new IOException("Update signature verification failed");
        } catch (GeneralSecurityException e) {
            throw new IOException("Update signature verification failed", e);
        }
        String text = new String(data, StandardCharsets.US_ASCII);
        var matcher = MANIFEST.matcher(text);
        if (!matcher.matches()) throw new IOException("Invalid signed update manifest");
        String version = matcher.group(1), artifact = matcher.group(2);
        long size;
        try { size = Long.parseLong(matcher.group(3)); }
        catch (NumberFormatException e) { throw new IOException("Invalid update size", e); }
        if (!version.equals(expectedVersion) || !artifact.equals(expectedArtifact)) {
            throw new IOException("Signed update version or artifact does not match release");
        }
        return new Manifest(version, artifact, size, matcher.group(4));
    }
}
