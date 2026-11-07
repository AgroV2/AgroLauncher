package by.agro.launcher.mods;

import by.agro.launcher.i18n.Strings;
import by.agro.launcher.loaders.LoaderType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;






public final class ModCompatibilityChecker {

    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final int MAX_NESTED_JAR_BYTES = 64 * 1024 * 1024;
    private static final int MAX_NESTED_DEPTH = 3;
    private static final int MAX_NESTED_JARS = 512;

    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String fileName, String modName, String message) {
        @Override
        public String toString() {
            String prefix = Strings.get(severity == Severity.ERROR ? "compat.severity.error" : "compat.severity.warning");
            String who = fileName == null || fileName.isBlank() ? "" : " " + (modName == null || modName.isBlank()
                    ? fileName : modName + " (" + fileName + ")") + ":";
            return prefix + who + " " + message;
        }
    }

    public record Report(List<Issue> issues, int modsChecked) {
        public long errorCount() {
            return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        }

        public long warningCount() {
            return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count();
        }

        public boolean hasErrors() {
            return errorCount() > 0;
        }

        public String summary() {
            if (issues.isEmpty()) return Strings.get("compat.noIssues", modsChecked);
            return Strings.get("compat.summary", modsChecked, errorCount(), warningCount());
        }
    }

    enum Format { FABRIC, QUILT, FORGE, NEOFORGE }

    enum Kind { REQUIRED, BREAKS, CONFLICTS }

    record Dependency(String modId, Kind kind, List<String> predicates, boolean maven) { }

    static final class ModEntry {
        String id = "";
        String name = "";
        String version = "";
        final Set<String> provides = new LinkedHashSet<>();
        final List<Dependency> dependencies = new ArrayList<>();
    }

    static final class JarInfo {
        String fileName;
        final Map<Format, List<ModEntry>> entries = new EnumMap<>(Format.class);

        final Map<Format, Map<String, String>> nested = new EnumMap<>(Format.class);
        String manifestVersion = "";
        boolean legacyForge;

        List<ModEntry> entriesFor(List<Format> formats) {
            for (Format format : formats) {
                List<ModEntry> found = entries.get(format);
                if (found != null && !found.isEmpty()) return found;
            }
            return List.of();
        }

        Format formatFor(List<Format> formats) {
            for (Format format : formats) {
                List<ModEntry> found = entries.get(format);
                if (found != null && !found.isEmpty()) return format;
            }
            return null;
        }
    }

    private ModCompatibilityChecker() {
    }

    public static Report check(Path gameDir, String minecraftVersion, LoaderType loader, String loaderVersion) {
        List<Issue> issues = new ArrayList<>();
        List<JarInfo> jars = new ArrayList<>();
        Path mods = gameDir == null ? null : gameDir.toAbsolutePath().normalize().resolve("mods");
        if (mods != null && Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(mods)) {
            try (var stream = Files.list(mods)) {
                for (Path file : stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(path)
                        && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .sorted().toList()) {
                    jars.add(readJar(file));
                }
            } catch (IOException | SecurityException e) {
                issues.add(new Issue(Severity.WARNING, "", "", Strings.get("compat.readFailed", e.getMessage())));
            }
        }
        return new Report(evaluate(jars, minecraftVersion, loader, loaderVersion, issues), jars.size());
    }

    static List<Issue> evaluate(List<JarInfo> jars, String minecraftVersion, LoaderType loader,
                                String loaderVersion, List<Issue> issues) {
        if (jars.isEmpty()) return issues;
        LoaderType type = loader == null ? LoaderType.VANILLA : loader;
        if (!type.supportsMods()) {
            issues.add(new Issue(Severity.WARNING, "", "", Strings.get("compat.noLoader", jars.size(),
                    type == LoaderType.VANILLA ? "Vanilla" : type.displayName())));
            return issues;
        }
        List<Format> accepted = acceptedFormats(type);
        boolean connector = jars.stream().anyMatch(jar -> jar.entries.values().stream()
                .flatMap(List::stream).anyMatch(entry -> "connector".equals(entry.id)));
        String cleanLoaderVersion = cleanLoaderVersion(minecraftVersion, loaderVersion);

        Map<String, String> available = new HashMap<>(builtIns(type, minecraftVersion, cleanLoaderVersion));
        Map<String, List<String>> owners = new LinkedHashMap<>();
        Map<JarInfo, List<ModEntry>> selected = new LinkedHashMap<>();
        for (JarInfo jar : jars) {
            Format format = jar.formatFor(accepted);
            if (format == null) {
                reportWrongLoader(jar, type, connector, issues);
                continue;
            }
            if (type == LoaderType.NEOFORGE && format == Format.FORGE && !jar.legacyForge
                    && ModVersion.matchesMaven(minecraftVersion, "[1.20.2,)") == ModVersion.Match.YES) {
                ModEntry first = jar.entriesFor(accepted).get(0);
                issues.add(new Issue(Severity.WARNING, jar.fileName, first.name,
                        Strings.get("compat.forgeOnNeoForge")));
            }
            List<ModEntry> entries = jar.entriesFor(accepted);
            selected.put(jar, entries);
            for (ModEntry entry : entries) {
                if (!entry.id.isBlank()) {
                    available.putIfAbsent(entry.id, entry.version);
                    owners.computeIfAbsent(entry.id, id -> new ArrayList<>()).add(jar.fileName);
                }
                for (String provided : entry.provides) available.putIfAbsent(provided, entry.version);
            }
            Map<String, String> nested = new HashMap<>();
            for (Format nestedFormat : accepted) {
                Map<String, String> ids = jar.nested.get(nestedFormat);
                if (ids != null) ids.forEach(nested::putIfAbsent);
            }
            nested.forEach(available::putIfAbsent);
        }

        for (Map.Entry<String, List<String>> owner : owners.entrySet()) {
            if (owner.getValue().size() > 1) {
                issues.add(new Issue(Severity.ERROR, String.join(", ", owner.getValue()), owner.getKey(),
                        Strings.get("compat.duplicate", owner.getKey())));
            }
        }

        for (Map.Entry<JarInfo, List<ModEntry>> jarEntries : selected.entrySet()) {
            JarInfo jar = jarEntries.getKey();
            for (ModEntry entry : jarEntries.getValue()) {
                for (Dependency dependency : entry.dependencies) {
                    checkDependency(jar, entry, dependency, available, issues);
                }
            }
        }
        return issues;
    }

    private static void checkDependency(JarInfo jar, ModEntry entry, Dependency dependency,
                                        Map<String, String> available, List<Issue> issues) {
        String id = dependency.modId();
        if (id == null || id.isBlank() || id.equals(entry.id) || ignoredDependency(id)) return;
        boolean present = available.containsKey(id);
        String version = present ? available.get(id) : null;
        ModVersion.Match match = present && version != null && !version.isBlank()
                ? (dependency.maven() ? matchAnyMaven(version, dependency.predicates())
                : ModVersion.matchesAnyFabric(version, dependency.predicates()))
                : ModVersion.Match.UNKNOWN;
        String wanted = String.join(" | ", dependency.predicates());
        switch (dependency.kind()) {
            case REQUIRED -> {
                if (!present) {
                    issues.add(new Issue(Severity.ERROR, jar.fileName, entry.name,
                            "minecraft".equals(id) ? Strings.get("compat.minecraftUnknown")
                                    : Strings.get("compat.missingDependency", id, describe(wanted))));
                } else if (match == ModVersion.Match.NO) {
                    String key = "minecraft".equals(id) ? "compat.minecraftMismatch" : "compat.dependencyVersion";
                    issues.add(new Issue(Severity.ERROR, jar.fileName, entry.name,
                            "minecraft".equals(id) ? Strings.get(key, describe(wanted), version)
                                    : Strings.get(key, id, describe(wanted), version)));
                }
            }
            case BREAKS -> {
                if (present && (match == ModVersion.Match.YES
                        || (dependency.predicates().isEmpty() || isWildcard(dependency.predicates())))) {
                    issues.add(new Issue(Severity.ERROR, jar.fileName, entry.name,
                            Strings.get("compat.breaks", id)));
                }
            }
            case CONFLICTS -> {
                if (present && (match == ModVersion.Match.YES
                        || (dependency.predicates().isEmpty() || isWildcard(dependency.predicates())))) {
                    issues.add(new Issue(Severity.WARNING, jar.fileName, entry.name,
                            Strings.get("compat.conflicts", id)));
                }
            }
        }
    }

    private static boolean isWildcard(List<String> predicates) {
        return predicates.stream().allMatch(value -> value == null || value.isBlank() || "*".equals(value.trim()));
    }

    private static ModVersion.Match matchAnyMaven(String version, List<String> ranges) {
        if (ranges.isEmpty()) return ModVersion.Match.YES;
        boolean unknown = false;
        for (String range : ranges) {
            ModVersion.Match match = ModVersion.matchesMaven(version, range);
            if (match == ModVersion.Match.YES) return match;
            if (match == ModVersion.Match.UNKNOWN) unknown = true;
        }
        return unknown ? ModVersion.Match.UNKNOWN : ModVersion.Match.NO;
    }

    private static String describe(String wanted) {
        return wanted == null || wanted.isBlank() || "*".equals(wanted.trim()) ? Strings.get("common.any") : wanted;
    }

    private static boolean ignoredDependency(String id) {
        return switch (id) {
            case "java", "javafml", "lowcodefml", "mcp", "fml" -> true;
            default -> false;
        };
    }

    private static void reportWrongLoader(JarInfo jar, LoaderType loader, boolean connector, List<Issue> issues) {
        Set<String> formats = new LinkedHashSet<>();
        String name = "";
        for (Map.Entry<Format, List<ModEntry>> entry : jar.entries.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            formats.add(formatName(entry.getKey()));
            if (name.isBlank()) name = entry.getValue().get(0).name;
        }
        if (formats.isEmpty()) {
            issues.add(new Issue(Severity.WARNING, jar.fileName, "", Strings.get("compat.notAMod")));
            return;
        }
        boolean fabricOnly = jar.entries.containsKey(Format.FABRIC) || jar.entries.containsKey(Format.QUILT);
        Severity severity = connector && fabricOnly
                && (loader == LoaderType.FORGE || loader == LoaderType.NEOFORGE) ? Severity.WARNING : Severity.ERROR;
        issues.add(new Issue(severity, jar.fileName, name,
                Strings.get("compat.wrongLoader", String.join(" / ", formats), loader.displayName())));
    }

    private static String formatName(Format format) {
        return switch (format) {
            case FABRIC -> "Fabric";
            case QUILT -> "Quilt";
            case FORGE -> "Forge";
            case NEOFORGE -> "NeoForge";
        };
    }

    static List<Format> acceptedFormats(LoaderType loader) {
        return switch (loader) {
            case FABRIC -> List.of(Format.FABRIC);
            case QUILT -> List.of(Format.QUILT, Format.FABRIC);
            case FORGE -> List.of(Format.FORGE);
            case NEOFORGE -> List.of(Format.NEOFORGE, Format.FORGE);
            default -> List.of();
        };
    }

    static String cleanLoaderVersion(String minecraftVersion, String loaderVersion) {
        if (loaderVersion == null) return "";
        String value = loaderVersion.trim();
        if ("default".equals(value) || "recommended".equals(value) || "latest".equals(value)) return "";
        if (minecraftVersion != null && !minecraftVersion.isBlank() && value.startsWith(minecraftVersion + "-")) {
            value = value.substring(minecraftVersion.length() + 1);
        }
        return value;
    }

    private static Map<String, String> builtIns(LoaderType loader, String minecraftVersion, String loaderVersion) {
        Map<String, String> builtIns = new HashMap<>();
        builtIns.put("minecraft", minecraftVersion == null ? "" : minecraftVersion);
        switch (loader) {
            case FABRIC -> {
                builtIns.put("fabricloader", loaderVersion);
                builtIns.put("fabric-loader", loaderVersion);
            }
            case QUILT -> {
                builtIns.put("quilt_loader", loaderVersion);
                builtIns.put("quilt-loader", loaderVersion);
                builtIns.put("fabricloader", "");
            }
            case FORGE -> builtIns.put("forge", loaderVersion);
            case NEOFORGE -> {
                builtIns.put("neoforge", loaderVersion);
                builtIns.put("forge", "");
            }
            default -> {
            }
        }
        return builtIns;
    }



    interface EntrySource {
        byte[] read(String name) throws IOException;
    }

    static JarInfo readJar(Path file) {
        JarInfo info = new JarInfo();
        info.fileName = file.getFileName().toString();
        try (JarFile jar = new JarFile(file.toFile(), false)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String version = manifest.getMainAttributes().getValue("Implementation-Version");
                info.manifestVersion = version == null ? "" : version.trim();
            }
            EntrySource source = name -> {
                ZipEntry entry = jar.getEntry(name);
                if (entry == null || entry.isDirectory()) return null;
                long limit = name.endsWith(".jar") ? MAX_NESTED_JAR_BYTES : MAX_METADATA_BYTES;
                if (entry.getSize() > limit) return null;
                try (InputStream input = jar.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes((int) limit + 1);
                    return bytes.length > limit ? null : bytes;
                }
            };
            parse(info, source, 0, new int[]{0});
        } catch (IOException | RuntimeException ignored) {
        }
        return info;
    }

    private static void parse(JarInfo info, EntrySource source, int depth, int[] nestedCounter) throws IOException {
        List<String> nestedJars = new ArrayList<>();
        JsonObject fabric = json(source.read("fabric.mod.json"));
        if (fabric != null) {
            ModEntry entry = fabricEntry(fabric);
            addEntry(info, Format.FABRIC, entry, depth);
            for (JsonElement element : array(fabric, "jars")) {
                if (element.isJsonObject()) {
                    String path = string(element.getAsJsonObject(), "file");
                    if (!path.isBlank()) nestedJars.add(path);
                }
            }
        }
        JsonObject quilt = json(source.read("quilt.mod.json"));
        if (quilt != null && quilt.has("quilt_loader") && quilt.get("quilt_loader").isJsonObject()) {
            JsonObject loader = quilt.getAsJsonObject("quilt_loader");
            addEntry(info, Format.QUILT, quiltEntry(loader), depth);
            for (JsonElement element : array(loader, "jars")) {
                if (element.isJsonPrimitive()) nestedJars.add(element.getAsString());
            }
        }
        String neoToml = text(source.read("META-INF/neoforge.mods.toml"));
        if (neoToml != null) {
            for (ModEntry entry : tomlEntries(neoToml, info.manifestVersion)) addEntry(info, Format.NEOFORGE, entry, depth);
        }
        String forgeToml = text(source.read("META-INF/mods.toml"));
        if (forgeToml != null) {
            for (ModEntry entry : tomlEntries(forgeToml, info.manifestVersion)) addEntry(info, Format.FORGE, entry, depth);
        }
        if (depth == 0 && fabric == null && quilt == null && neoToml == null && forgeToml == null) {
            String legacy = text(source.read("mcmod.info"));
            if (legacy != null) {
                info.legacyForge = true;
                ModEntry entry = legacyEntry(legacy);
                addEntry(info, Format.FORGE, entry, depth);
            }
        }
        JsonObject jarJar = json(source.read("META-INF/jarjar/metadata.json"));
        if (jarJar != null) {
            for (JsonElement element : array(jarJar, "jars")) {
                if (element.isJsonObject()) {
                    String path = string(element.getAsJsonObject(), "path");
                    if (!path.isBlank()) nestedJars.add(path);
                }
            }
        }
        if (depth >= MAX_NESTED_DEPTH) return;
        for (String path : nestedJars) {
            if (nestedCounter[0]++ >= MAX_NESTED_JARS) return;
            byte[] bytes = source.read(path);
            if (bytes == null) continue;
            Map<String, byte[]> nestedEntries = readNested(bytes);
            String previousManifest = info.manifestVersion;
            byte[] manifest = nestedEntries.get("META-INF/MANIFEST.MF");
            if (manifest != null) {
                try {
                    String version = new Manifest(new ByteArrayInputStream(manifest)).getMainAttributes()
                            .getValue("Implementation-Version");
                    info.manifestVersion = version == null ? "" : version.trim();
                } catch (IOException ignored) {
                    info.manifestVersion = "";
                }
            } else {
                info.manifestVersion = "";
            }
            parse(info, nestedEntries::get, depth + 1, nestedCounter);
            info.manifestVersion = previousManifest;
        }
    }

    private static Map<String, byte[]> readNested(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                boolean metadata = name.equals("fabric.mod.json") || name.equals("quilt.mod.json")
                        || name.equals("META-INF/mods.toml") || name.equals("META-INF/neoforge.mods.toml")
                        || name.equals("META-INF/MANIFEST.MF") || name.equals("META-INF/jarjar/metadata.json");
                boolean jar = name.toLowerCase(Locale.ROOT).endsWith(".jar");
                if (!metadata && !jar) continue;
                int limit = jar ? MAX_NESTED_JAR_BYTES : MAX_METADATA_BYTES;
                byte[] data = zip.readNBytes(limit + 1);
                if (data.length > limit) continue;
                total += data.length;
                if (total > MAX_NESTED_JAR_BYTES) break;
                entries.put(name, data);
            }
        }
        return entries;
    }

    private static void addEntry(JarInfo info, Format format, ModEntry entry, int depth) {
        if (entry == null || entry.id.isBlank()) return;
        if (depth == 0) {
            info.entries.computeIfAbsent(format, f -> new ArrayList<>()).add(entry);
        } else {
            Map<String, String> nested = info.nested.computeIfAbsent(format, f -> new HashMap<>());
            nested.putIfAbsent(entry.id, entry.version);
            for (String provided : entry.provides) nested.putIfAbsent(provided, entry.version);
        }
    }

    static ModEntry fabricEntry(JsonObject root) {
        ModEntry entry = new ModEntry();
        entry.id = string(root, "id");
        entry.name = string(root, "name");
        entry.version = string(root, "version");
        for (JsonElement provided : array(root, "provides")) {
            if (provided.isJsonPrimitive()) entry.provides.add(provided.getAsString());
        }
        addFabricDependencies(entry, root, "depends", Kind.REQUIRED);
        addFabricDependencies(entry, root, "breaks", Kind.BREAKS);
        addFabricDependencies(entry, root, "conflicts", Kind.CONFLICTS);
        if (entry.name.isBlank()) entry.name = entry.id;
        return entry;
    }

    private static void addFabricDependencies(ModEntry entry, JsonObject root, String key, Kind kind) {
        if (!root.has(key) || !root.get(key).isJsonObject()) return;
        for (Map.Entry<String, JsonElement> dependency : root.getAsJsonObject(key).entrySet()) {
            entry.dependencies.add(new Dependency(dependency.getKey(), kind, predicates(dependency.getValue()), false));
        }
    }

    static ModEntry quiltEntry(JsonObject loader) {
        ModEntry entry = new ModEntry();
        entry.id = stripGroup(string(loader, "id"));
        entry.version = string(loader, "version");
        if (loader.has("metadata") && loader.get("metadata").isJsonObject()) {
            entry.name = string(loader.getAsJsonObject("metadata"), "name");
        }
        for (JsonElement provided : array(loader, "provides")) {
            if (provided.isJsonPrimitive()) entry.provides.add(stripGroup(provided.getAsString()));
            else if (provided.isJsonObject()) entry.provides.add(stripGroup(string(provided.getAsJsonObject(), "id")));
        }
        addQuiltDependencies(entry, array(loader, "depends"), Kind.REQUIRED);
        addQuiltDependencies(entry, array(loader, "breaks"), Kind.BREAKS);
        if (entry.name.isBlank()) entry.name = entry.id;
        return entry;
    }

    private static void addQuiltDependencies(ModEntry entry, JsonArray dependencies, Kind kind) {
        for (JsonElement element : dependencies) {
            if (element.isJsonPrimitive()) {
                entry.dependencies.add(new Dependency(stripGroup(element.getAsString()), kind, List.of(), false));
            } else if (element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if (object.has("optional") && object.get("optional").isJsonPrimitive()
                        && object.get("optional").getAsBoolean()) continue;
                if (object.has("unless")) continue;
                entry.dependencies.add(new Dependency(stripGroup(string(object, "id")), kind,
                        object.has("versions") ? predicates(object.get("versions")) : List.of(), false));
            }
        }
    }

    private static ModEntry legacyEntry(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            JsonObject first = null;
            if (parsed.isJsonArray() && !parsed.getAsJsonArray().isEmpty()
                    && parsed.getAsJsonArray().get(0).isJsonObject()) {
                first = parsed.getAsJsonArray().get(0).getAsJsonObject();
            } else if (parsed.isJsonObject() && parsed.getAsJsonObject().has("modList")) {
                JsonArray list = array(parsed.getAsJsonObject(), "modList");
                if (!list.isEmpty() && list.get(0).isJsonObject()) first = list.get(0).getAsJsonObject();
            }
            if (first == null) return null;
            ModEntry entry = new ModEntry();
            entry.id = string(first, "modid");
            entry.name = string(first, "name");
            entry.version = string(first, "version");
            if (entry.name.isBlank()) entry.name = entry.id;
            return entry;
        } catch (RuntimeException e) {
            return null;
        }
    }


    static List<ModEntry> tomlEntries(String toml, String manifestVersion) {
        List<ModEntry> mods = new ArrayList<>();
        Map<String, List<Map<String, String>>> dependencies = new LinkedHashMap<>();
        Map<String, String> current = null;
        String section = "";
        String[] lines = toml.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("[[") && line.endsWith("]]")) {
                section = line.substring(2, line.length() - 2).trim();
                current = new HashMap<>();
                if ("mods".equals(section)) {
                    ModEntry entry = new ModEntry();
                    mods.add(entry);
                    current.put("__mod__", String.valueOf(mods.size() - 1));
                } else if (section.startsWith("dependencies.")) {
                    String owner = unquote(section.substring("dependencies.".length()).trim());
                    dependencies.computeIfAbsent(owner, key -> new ArrayList<>()).add(current);
                } else {
                    current = null;
                }
                continue;
            }
            if (line.startsWith("[")) {
                section = line;
                current = null;
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) continue;
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            if (value.startsWith("'''") || value.startsWith("\"\"\"")) {
                String delimiter = value.substring(0, 3);
                if (value.length() < 6 || !value.substring(3).contains(delimiter)) {
                    while (i + 1 < lines.length && !lines[i + 1].contains(delimiter)) i++;
                    i++;
                }
                continue;
            }
            if (current == null) continue;
            current.put(key, unquote(value));
            if (current.containsKey("__mod__")) {
                ModEntry entry = mods.get(Integer.parseInt(current.get("__mod__")));
                switch (key) {
                    case "modId" -> entry.id = unquote(value);
                    case "displayName" -> entry.name = unquote(value);
                    case "version" -> entry.version = unquote(value);
                    default -> {
                    }
                }
            }
        }
        for (ModEntry entry : mods) {
            if (entry.version.contains("${")) entry.version = manifestVersion == null ? "" : manifestVersion;
            if (entry.name.isBlank() || entry.name.contains("${")) entry.name = entry.id;
            for (Map<String, String> dependency : dependencies.getOrDefault(entry.id, List.of())) {
                Dependency parsed = tomlDependency(dependency);
                if (parsed != null) entry.dependencies.add(parsed);
            }
        }
        if (!mods.isEmpty()) {
            for (Map.Entry<String, List<Map<String, String>>> owner : dependencies.entrySet()) {
                boolean known = mods.stream().anyMatch(mod -> mod.id.equals(owner.getKey()));
                if (known) continue;
                for (Map<String, String> dependency : owner.getValue()) {
                    Dependency parsed = tomlDependency(dependency);
                    if (parsed != null) mods.get(0).dependencies.add(parsed);
                }
            }
        }
        mods.removeIf(mod -> mod.id.isBlank());
        return mods;
    }

    private static Dependency tomlDependency(Map<String, String> values) {
        String id = values.getOrDefault("modId", "");
        if (id.isBlank()) return null;
        String side = values.getOrDefault("side", "BOTH").toUpperCase(Locale.ROOT);
        if ("SERVER".equals(side)) return null;
        String type = values.getOrDefault("type", "").toLowerCase(Locale.ROOT);
        Kind kind;
        if (type.isBlank()) {
            if (!"true".equalsIgnoreCase(values.getOrDefault("mandatory", "false"))) return null;
            kind = Kind.REQUIRED;
        } else {
            kind = switch (type) {
                case "required" -> Kind.REQUIRED;
                case "incompatible" -> Kind.BREAKS;
                case "discouraged" -> Kind.CONFLICTS;
                default -> null;
            };
            if (kind == null) return null;
        }
        String range = values.getOrDefault("versionRange", "");
        return new Dependency(id, kind, range.isBlank() ? List.of() : List.of(range), true);
    }

    private static String stripComment(String line) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && !single) dbl = !dbl;
            else if (c == '\'' && !dbl) single = !single;
            else if (c == '#' && !single && !dbl) return line.substring(0, i);
        }
        return line;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static String stripGroup(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static List<String> predicates(JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonPrimitive()) return List.of(element.getAsString());
        if (element.isJsonArray()) {
            List<String> result = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive()) result.add(child.getAsString());
            }
            return result;
        }
        return List.of();
    }

    private static JsonObject json(byte[] bytes) {
        String text = text(bytes);
        if (text == null) return null;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(byte[] bytes) {
        if (bytes == null || bytes.length > MAX_METADATA_BYTES) return null;
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray()
                ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        try {
            return object.get(key).getAsString().trim();
        } catch (RuntimeException e) {
            return "";
        }
    }
}
