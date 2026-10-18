package by.agro.launcher.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;


public final class Json {

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private Json() {
    }

    public static JsonObject parseObject(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    public static JsonElement parse(String text) {
        return JsonParser.parseString(text);
    }

    public static JsonObject readObject(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    public static <T> T read(Path file, Class<T> type) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        }
    }

    public static void write(Path file, Object value) throws IOException {
        Path absoluteFile = file.toAbsolutePath().normalize();
        Path temp = SecureFiles.createSiblingTemp(absoluteFile, ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(value, writer);
            }
            SecureFiles.atomicReplace(temp, absoluteFile);
        } finally {
            Files.deleteIfExists(temp);
        }
    }


    public static String string(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsString();
    }

    public static long longValue(JsonObject obj, String key, long fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsLong();
    }

    public static int intValue(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsInt();
    }

    public static boolean boolValue(JsonObject obj, String key, boolean fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsBoolean();
    }

    public static JsonObject object(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonObject()) {
            return null;
        }
        return obj.getAsJsonObject(key);
    }
}
