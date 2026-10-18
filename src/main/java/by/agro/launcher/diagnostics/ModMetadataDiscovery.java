package by.agro.launcher.diagnostics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ModMetadataDiscovery {
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final int MAX_CLASS_ENTRIES = 20000;
    private static final int MAX_PACKAGE_ROOTS = 64;

    private ModMetadataDiscovery() {
    }

    public static List<ModIdentity> discover(Path gameDir) {
        List<ModIdentity> result = new ArrayList<>();
        if (gameDir == null) return result;
        Path modsDir = gameDir.toAbsolutePath().normalize().resolve("mods");
        if (!Files.isDirectory(modsDir, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(modsDir)) return result;
        try (var stream = Files.list(modsDir)) {
            for (Path file : stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")).toList()) {
                result.add(read(file));
            }
        } catch (IOException | SecurityException ignored) {
        }
        result.sort(Comparator.comparing(identity -> identity.fileName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static ModIdentity read(Path file) {
        String fileName = file.getFileName().toString();
        String fallback = fileName.substring(0, fileName.length() - 4);
        String id = "";
        String name = "";
        Set<String> roots = new LinkedHashSet<>();
        try (JarFile jar = new JarFile(file.toFile(), false)) {
            JsonObject fabric = json(jar, "fabric.mod.json");
            if (fabric != null) {
                id = string(fabric, "id", id);
                name = string(fabric, "name", name);
                collectEntrypoints(fabric, roots);
            }
            JsonObject quilt = json(jar, "quilt.mod.json");
            if (quilt != null) {
                JsonObject loader = object(quilt, "quilt_loader");
                id = string(loader, "id", id);
                JsonObject metadata = object(loader, "metadata");
                name = string(metadata, "name", name);
                collectEntrypoints(loader, roots);
            }
            String toml = text(jar, "META-INF/mods.toml");
            if (toml == null) toml = text(jar, "META-INF/neoforge.mods.toml");
            if (toml != null) {
                id = firstTomlValue(toml, "modId", id);
                name = firstTomlValue(toml, "displayName", name);
            }
            collectClassRoots(jar, roots);
        } catch (IOException | RuntimeException ignored) {
        }
        if (id.isBlank()) id = fallbackId(fallback);
        if (name.isBlank()) name = fallback;
        return new ModIdentity(fileName, id, name, new ArrayList<>(roots));
    }

    private static JsonObject json(JarFile jar, String path) throws IOException {
        String value = text(jar, path);
        if (value == null) return null;
        JsonElement parsed = JsonParser.parseString(value);
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    private static String text(JarFile jar, String path) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        if (entry == null || entry.isDirectory() || entry.getSize() > MAX_METADATA_BYTES) return null;
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(MAX_METADATA_BYTES + 1);
            if (bytes.length > MAX_METADATA_BYTES) return null;
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void collectEntrypoints(JsonObject object, Set<String> roots) {
        if (object == null || !object.has("entrypoints")) return;
        collectJsonStrings(object.get("entrypoints"), roots);
    }

    private static void collectJsonStrings(JsonElement element, Set<String> roots) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            addClassRoot(element.getAsString(), roots);
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectJsonStrings(child, roots);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("value")) collectJsonStrings(object.get("value"), roots);
            for (var entry : object.entrySet()) {
                if (!"value".equals(entry.getKey())) collectJsonStrings(entry.getValue(), roots);
            }
        }
    }

    private static void collectClassRoots(JarFile jar, Set<String> roots) {
        int seen = 0;
        var entries = jar.entries();
        while (entries.hasMoreElements() && seen < MAX_CLASS_ENTRIES && roots.size() < MAX_PACKAGE_ROOTS) {
            JarEntry entry = entries.nextElement();
            String path = entry.getName();
            if (!entry.isDirectory() && path.endsWith(".class") && !path.startsWith("META-INF/versions/")) {
                seen++;
                String className = path.substring(0, path.length() - 6).replace('/', '.');
                addClassRoot(className, roots);
            }
        }
    }

    private static void addClassRoot(String value, Set<String> roots) {
        if (value == null) return;
        String className = value.trim();
        int separator = className.indexOf("::");
        if (separator >= 0) className = className.substring(0, separator);
        separator = className.indexOf('#');
        if (separator >= 0) className = className.substring(0, separator);
        String[] parts = className.split("\\.");
        if (parts.length < 3) return;
        int length = Math.min(parts.length - 1, 4);
        StringBuilder root = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (!parts[i].matches("[A-Za-z_$][A-Za-z0-9_$]*")) return;
            if (i > 0) root.append('.');
            root.append(parts[i].toLowerCase(Locale.ROOT));
        }
        String candidate = root.toString();
        if (!candidate.startsWith("java.") && !candidate.startsWith("javax.")
                && !candidate.startsWith("net.minecraft") && !candidate.startsWith("com.mojang")
                && !candidate.startsWith("org.spongepowered") && !candidate.startsWith("org.objectweb")) {
            roots.add(candidate);
        }
    }

    private static String firstTomlValue(String text, String key, String fallback) {
        String prefix = key.toLowerCase(Locale.ROOT);
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.toLowerCase(Locale.ROOT).startsWith(prefix)) continue;
            int equals = trimmed.indexOf('=');
            if (equals < 0) continue;
            String value = trimmed.substring(equals + 1).trim();
            int comment = value.indexOf('#');
            if (comment >= 0) value = value.substring(0, comment).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1).trim();
            }
            if (!value.isBlank() && !value.contains("${")) return value;
        }
        return fallback;
    }

    private static String fallbackId(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized;
    }

    private static String string(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            String value = object.get(key).getAsString().trim();
            return value.isBlank() ? fallback : value;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static JsonObject object(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonObject()
                ? object.getAsJsonObject(key) : null;
    }
}
