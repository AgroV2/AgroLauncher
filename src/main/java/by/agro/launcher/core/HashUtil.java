package by.agro.launcher.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public final class HashUtil {

    private HashUtil() {
    }

    public static String sha1(Path file) throws IOException {
        return digest(file, "SHA-1");
    }

    public static String sha256(Path file) throws IOException {
        return digest(file, "SHA-256");
    }

    public static String digest(Path file, String algorithm) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Алгоритм не поддерживается: " + algorithm, e);
        }
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return toHex(digest.digest());
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }


    public static boolean verify(Path file, String expectedSha1) {
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return false;
            }
            if (expectedSha1 == null || expectedSha1.isBlank()) {
                return true;
            }
            return sha1(file).equalsIgnoreCase(expectedSha1);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean verifySha256(Path file, String expectedSha256) {
        try {
            if (!Files.exists(file) || Files.size(file) == 0) {
                return false;
            }
            if (expectedSha256 == null || expectedSha256.isBlank()) {
                return true;
            }
            return sha256(file).equalsIgnoreCase(expectedSha256);
        } catch (IOException e) {
            return false;
        }
    }
}
