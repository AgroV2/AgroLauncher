package by.agro.launcher.version;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Аргумент запуска из блока "arguments" (формат 1.13+).
 * Может быть простой строкой либо объектом с правилами и списком значений.
 */
public final class Argument {

    public final List<String> values;
    public final List<Rule> rules;

    public Argument(List<String> values, List<Rule> rules) {
        this.values = values;
        this.rules = rules;
    }

    public static List<Argument> parseList(JsonArray array) {
        List<Argument> result = new ArrayList<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                result.add(new Argument(
                        Collections.singletonList(element.getAsString()),
                        Collections.emptyList()));
            } else if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                List<Rule> rules = Rule.parseList(obj.getAsJsonArray("rules"));
                List<String> values = new ArrayList<>();
                JsonElement value = obj.get("value");
                if (value != null && !value.isJsonNull()) {
                    if (value.isJsonArray()) {
                        for (JsonElement item : value.getAsJsonArray()) {
                            values.add(item.getAsString());
                        }
                    } else {
                        values.add(value.getAsString());
                    }
                }
                result.add(new Argument(values, rules));
            }
        }
        return result;
    }

    public boolean isAllowed(Rule.FeatureSet featureSet) {
        return Rule.allowed(rules, featureSet);
    }
}
