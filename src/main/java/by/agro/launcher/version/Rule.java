package by.agro.launcher.version;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.Platform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Правило применимости библиотеки или аргумента (блок "rules").
 *
 * Формат: { "action": "allow"|"disallow", "os": {...}, "features": {...} }
 */
public final class Rule {

    public final boolean allow;
    public final String osName;
    public final String osArch;
    public final String osVersionRegex;
    public final Map<String, Boolean> features;

    public Rule(boolean allow, String osName, String osArch, String osVersionRegex, Map<String, Boolean> features) {
        this.allow = allow;
        this.osName = osName;
        this.osArch = osArch;
        this.osVersionRegex = osVersionRegex;
        this.features = features;
    }

    public static List<Rule> parseList(JsonArray array) {
        List<Rule> rules = new ArrayList<>();
        if (array == null) {
            return rules;
        }
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                rules.add(parse(element.getAsJsonObject()));
            }
        }
        return rules;
    }

    public static Rule parse(JsonObject obj) {
        boolean allow = !"disallow".equals(Json.string(obj, "action", "allow"));

        String osName = null;
        String osArch = null;
        String osVersion = null;
        JsonObject os = Json.object(obj, "os");
        if (os != null) {
            osName = Json.string(os, "name", null);
            osArch = Json.string(os, "arch", null);
            osVersion = Json.string(os, "version", null);
        }

        Map<String, Boolean> features = new LinkedHashMap<>();
        JsonObject featuresObj = Json.object(obj, "features");
        if (featuresObj != null) {
            for (String key : featuresObj.keySet()) {
                JsonElement value = featuresObj.get(key);
                if (value != null && !value.isJsonNull() && value.isJsonPrimitive()) {
                    features.put(key, value.getAsBoolean());
                }
            }
        }

        return new Rule(allow, osName, osArch, osVersion, features);
    }

    /** Совпадает ли условие правила с текущим окружением. */
    private boolean matches(FeatureSet featureSet) {
        if (osName != null) {
            // "osx-arm64" встречается в некоторых сторонних манифестах
            String current = Platform.current().mojangName();
            if (!osName.equals(current)) {
                return false;
            }
        }
        if (osArch != null) {
            String arch = Platform.arch();
            // Mojang использует "x86" для 32-бит; сверяем напрямую и с сырым os.arch
            String raw = System.getProperty("os.arch", "").toLowerCase();
            if (!osArch.equals(arch) && !osArch.equals(raw)) {
                return false;
            }
        }
        if (osVersionRegex != null) {
            String version = System.getProperty("os.version", "");
            try {
                if (!Pattern.compile(osVersionRegex).matcher(version).find()) {
                    return false;
                }
            } catch (PatternSyntaxException e) {
                // некорректный regex не должен ломать запуск
                return false;
            }
        }
        if (!features.isEmpty()) {
            for (Map.Entry<String, Boolean> entry : features.entrySet()) {
                boolean actual = featureSet != null && featureSet.isEnabled(entry.getKey());
                if (actual != Boolean.TRUE.equals(entry.getValue())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Вычисляет итоговую применимость по списку правил.
     * Логика Mojang: если список пуст — разрешено. Иначе действует последнее совпавшее правило,
     * а при отсутствии совпадений — запрещено.
     */
    public static boolean allowed(List<Rule> rules, FeatureSet featureSet) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        boolean result = false;
        for (Rule rule : rules) {
            if (rule.matches(featureSet)) {
                result = rule.allow;
            }
        }
        return result;
    }

    /** Набор включённых "features" (например, is_demo_user, has_custom_resolution). */
    public interface FeatureSet {
        boolean isEnabled(String feature);
    }
}
