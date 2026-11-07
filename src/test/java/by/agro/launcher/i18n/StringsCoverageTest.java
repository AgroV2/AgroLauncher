package by.agro.launcher.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringsCoverageTest {

    private static final Pattern GET = Pattern.compile("Strings\\.get\\(\"([^\"]+)\"");
    private static final Pattern KEY_LITERAL = Pattern.compile(
            "\"((?:clone|compat|worlds|stats|disk|units|cli)\\.[A-Za-z0-9.]+)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");

    @Test
    void everyKeyUsedInSourcesIsTranslated() throws IOException {
        List<String> missing = new ArrayList<>();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.endsWith("Strings.java")) continue;
                String source = Files.readString(file);
                for (Pattern pattern : new Pattern[]{GET, KEY_LITERAL}) {
                    Matcher matcher = pattern.matcher(source);
                    while (matcher.find()) {
                        String key = matcher.group(1);
                        if (key.endsWith(".")) continue;
                        if (!Strings.has(key)) missing.add(file.getFileName() + ": " + key);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing translations: " + missing);
    }

    @Test
    void russianAndEnglishVariantsAreCompleteAndConsistent() {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : Strings.all().entrySet()) {
            String ru = entry.getValue()[0];
            String en = entry.getValue()[1];
            if (ru == null || ru.isBlank() || en == null || en.isBlank()) {
                problems.add(entry.getKey() + ": empty translation");
                continue;
            }
            if (!placeholders(ru).equals(placeholders(en))) problems.add(entry.getKey() + ": placeholders differ");
            if (!placeholders(ru).isEmpty()) {
                try {
                    new MessageFormat(ru);
                    new MessageFormat(en);
                } catch (IllegalArgumentException e) {
                    problems.add(entry.getKey() + ": " + e.getMessage());
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void switchingLanguageChangesText() {
        Language before = Strings.language();
        try {
            Strings.setLanguage(Language.RUSSIAN);
            assertEquals("Клонировать", Strings.get("clone.button"));
            Strings.setLanguage(Language.ENGLISH);
            assertEquals("Clone", Strings.get("clone.button"));
            assertEquals("Worlds: 3 · 1 used · 2 free", Strings.get("worlds.summary", 3, "1", "2"));
        } finally {
            Strings.setLanguage(before);
        }
    }

    private static TreeSet<String> placeholders(String text) {
        TreeSet<String> result = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }
}
