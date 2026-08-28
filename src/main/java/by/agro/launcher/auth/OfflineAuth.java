package by.agro.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;



public final class OfflineAuth {

    
    public static final String PLACEHOLDER_TOKEN = "0";

    private static final String PREFIX = "OfflinePlayer:";

    private OfflineAuth() {
    }

    
    public static String uuidFor(String username) {
        return offlineUuid(username).toString().replace("-", "");
    }

    
    public static UUID offlineUuid(String username) {
        byte[] source = (PREFIX + username).getBytes(StandardCharsets.UTF_8);
        return uuidVersion3(source);
    }

    private static UUID uuidVersion3(byte[] data) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 недоступен", e);
        }
        byte[] hash = md5.digest(data);
        hash[6] &= 0x0f;
        hash[6] |= 0x30;
        hash[8] &= 0x3f;
        hash[8] |= (byte) 0x80;

        long most = 0;
        long least = 0;
        for (int i = 0; i < 8; i++) {
            most = (most << 8) | (hash[i] & 0xff);
        }
        for (int i = 8; i < 16; i++) {
            least = (least << 8) | (hash[i] & 0xff);
        }
        return new UUID(most, least);
    }

    
    public static boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        String trimmed = username.trim();
        return trimmed.matches("[A-Za-z0-9_]{3,16}");
    }
}
