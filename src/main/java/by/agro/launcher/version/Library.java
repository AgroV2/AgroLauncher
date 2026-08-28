package by.agro.launcher.version;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Platform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Библиотека из version.json.
 *
 * Поддерживает три формата, встречающиеся в реальных манифестах:
 *  1. Современный (1.19+): natives как отдельная библиотека с classifier в name и rules по ОС.
 *  2. Legacy (1.7–1.12): блок "natives" с шаблоном ${arch} и downloads.classifiers.
 *  3. Maven-only (Fabric/Quilt/Forge): только name + url репозитория, без downloads.
 */
public final class Library {

    public final String name;
    public final List<Rule> rules;

    /** downloads.artifact */
    public final Artifact artifact;

    /** downloads.classifiers: имя classifier → артефакт */
    public final Map<String, Artifact> classifiers;

    /** natives: ОС → шаблон classifier (например "natives-windows-${arch}") */
    public final Map<String, String> natives;

    /** Базовый URL maven-репозитория (Fabric/Forge/Quilt), если downloads отсутствует. */
    public final String mavenUrl;

    /** extract.exclude — какие пути не распаковывать из нативного архива. */
    public final List<String> extractExclude;

    public Library(String name, List<Rule> rules, Artifact artifact, Map<String, Artifact> classifiers,
                   Map<String, String> natives, String mavenUrl, List<String> extractExclude) {
        this.name = name;
        this.rules = rules;
        this.artifact = artifact;
        this.classifiers = classifiers;
        this.natives = natives;
        this.mavenUrl = mavenUrl;
        this.extractExclude = extractExclude;
    }

    /** Один скачиваемый файл. */
    public static final class Artifact {
        public final String path;
        public final String url;
        public final String sha1;
        public final long size;

        public Artifact(String path, String url, String sha1, long size) {
            this.path = path;
            this.url = url;
            this.sha1 = sha1;
            this.size = size;
        }

        public static Artifact parse(JsonObject obj) {
            if (obj == null) {
                return null;
            }
            return new Artifact(
                    Json.string(obj, "path", null),
                    Json.string(obj, "url", null),
                    Json.string(obj, "sha1", null),
                    Json.longValue(obj, "size", 0)
            );
        }
    }

    public static Library parse(JsonObject obj) {
        String name = Json.string(obj, "name", null);
        if (name == null) {
            return null;
        }

        List<Rule> rules = Rule.parseList(obj.getAsJsonArray("rules"));

        Artifact artifact = null;
        Map<String, Artifact> classifiers = new LinkedHashMap<>();
        JsonObject downloads = Json.object(obj, "downloads");
        if (downloads != null) {
            artifact = Artifact.parse(Json.object(downloads, "artifact"));
            JsonObject classifiersObj = Json.object(downloads, "classifiers");
            if (classifiersObj != null) {
                for (String key : classifiersObj.keySet()) {
                    Artifact parsed = Artifact.parse(classifiersObj.getAsJsonObject(key));
                    if (parsed != null) {
                        classifiers.put(key, parsed);
                    }
                }
            }
        }

        Map<String, String> natives = new LinkedHashMap<>();
        JsonObject nativesObj = Json.object(obj, "natives");
        if (nativesObj != null) {
            for (String key : nativesObj.keySet()) {
                JsonElement value = nativesObj.get(key);
                if (value != null && !value.isJsonNull()) {
                    natives.put(key, value.getAsString());
                }
            }
        }

        String mavenUrl = Json.string(obj, "url", null);

        List<String> extractExclude = new ArrayList<>();
        JsonObject extract = Json.object(obj, "extract");
        if (extract != null) {
            JsonArray exclude = extract.getAsJsonArray("exclude");
            if (exclude != null) {
                for (JsonElement element : exclude) {
                    extractExclude.add(element.getAsString());
                }
            }
        }

        return new Library(name, rules, artifact, classifiers, natives, mavenUrl, extractExclude);
    }

    public boolean isAllowed(Rule.FeatureSet featureSet) {
        return Rule.allowed(rules, featureSet);
    }

    /** Является ли библиотека нативной (legacy-формат с блоком natives). */
    public boolean hasLegacyNatives() {
        return !natives.isEmpty();
    }

    /**
     * Является ли библиотека нативной в современном формате —
     * classifier "natives-*" прямо в maven-координатах.
     */
    public boolean isModernNative() {
        String[] parts = name.split(":");
        return parts.length > 3 && parts[3].startsWith("natives");
    }

    public boolean isNative() {
        return hasLegacyNatives() || isModernNative();
    }

    /** Возвращает classifier нативной библиотеки для текущей ОС (legacy-формат), либо null. */
    public String currentNativeClassifier() {
        if (natives.isEmpty()) {
            return null;
        }
        String template = natives.get(Platform.current().mojangName());
        if (template == null) {
            return null;
        }
        String arch = Platform.is64Bit() ? "64" : "32";
        return template.replace("${arch}", arch);
    }

    /**
     * Артефакт, который нужно скачать для текущей платформы.
     * Для legacy-нативов берётся classifier, для остальных — artifact.
     */
    public Artifact resolveArtifact() {
        String nativeClassifier = currentNativeClassifier();
        if (nativeClassifier != null) {
            Artifact fromClassifier = classifiers.get(nativeClassifier);
            if (fromClassifier != null) {
                return fromClassifier;
            }
            // некоторые манифесты Forge не содержат classifiers — соберём путь из координат
            return syntheticArtifact(nativeClassifier);
        }
        if (artifact != null) {
            return artifact;
        }
        return syntheticArtifact(null);
    }

    /**
     * Собирает артефакт из maven-координат, когда в манифесте нет блока downloads
     * (характерно для Fabric, Quilt, Forge, NeoForge).
     */
    private Artifact syntheticArtifact(String extraClassifier) {
        String coords = name;
        if (extraClassifier != null && !extraClassifier.isEmpty()) {
            coords = name + ":" + extraClassifier;
        }
        String relative = LauncherPaths.mavenToRelativePath(coords);
        String base = (mavenUrl != null && !mavenUrl.isBlank())
                ? mavenUrl
                : "https://libraries.minecraft.net/";
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return new Artifact(relative, base + relative, null, 0);
    }

    /** Относительный путь внутри каталога libraries. */
    public String relativePath() {
        Artifact resolved = resolveArtifact();
        if (resolved != null && resolved.path != null && !resolved.path.isBlank()) {
            return resolved.path;
        }
        return LauncherPaths.mavenToRelativePath(name);
    }

    /** group:artifact — ключ для дедупликации версий одной библиотеки. */
    public String groupArtifactKey() {
        String[] parts = name.split(":");
        if (parts.length >= 2) {
            String key = parts[0] + ":" + parts[1];
            if (parts.length > 3) {
                key = key + ":" + parts[3];
            }
            return key;
        }
        return name;
    }

    public String version() {
        String[] parts = name.split(":");
        return parts.length >= 3 ? parts[2] : "";
    }

    @Override
    public String toString() {
        return name;
    }
}
