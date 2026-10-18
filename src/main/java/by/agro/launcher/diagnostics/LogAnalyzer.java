package by.agro.launcher.diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LogAnalyzer {
    private final Map<String, FindingState> findings = new LinkedHashMap<>();
    private final Map<String, ModIdentity> implicated = new LinkedHashMap<>();
    private final List<ModIdentity> mods;

    public LogAnalyzer() {
        this(List.of());
    }

    public LogAnalyzer(List<ModIdentity> mods) {
        this.mods = mods == null ? List.of() : new ArrayList<>(mods);
    }

    public synchronized void accept(String line) {
        String text = line == null ? "" : line.toLowerCase(Locale.ROOT);
        List<ModIdentity> lineMods = matchMods(text);
        if (text.contains("unsupportedclassversionerror") || text.contains("compiled by a more recent version")
                || text.contains("only recognizes class file versions")) {
            add("JAVA_MISMATCH", "Несовместимая версия Java", "Выберите Java, требуемую этой версией Minecraft/loader.", lineMods);
        }
        if (text.contains("outofmemoryerror") || text.contains("java heap space") || text.contains("gc overhead limit")) {
            add("OUT_OF_MEMORY", "Недостаточно памяти Java", "Увеличьте RAM или уменьшите число модов/ресурспаков.", lineMods);
        }
        if (text.contains("classnotfoundexception") || text.contains("noclassdeffounderror")
                || text.contains("module not found") || text.contains("could not find or load main class")) {
            add("MISSING_CLASS", "Отсутствует класс или модуль", "Проверьте библиотеки, loader и совместимость модов.", lineMods);
        }
        if (text.contains("requires") && (text.contains("mod") || text.contains("fabric") || text.contains("forge"))
                || text.contains("missing mandatory dependencies") || text.contains("mod resolution encountered")) {
            add("MOD_DEPENDENCY", "Не выполнена зависимость мода", "Установите требуемую версию зависимости или удалите несовместимый мод.", lineMods);
        }
        if (text.contains("mixin") && (text.contains("failed") || text.contains("error") || text.contains("exception"))) {
            add("MIXIN_FAILURE", "Ошибка Mixin", "Проверьте совместимость модов и версий loader.", lineMods);
        }
        if (text.contains("opengl") || text.contains("lwjgl") && text.contains("error")
                || text.contains("unsatisfiedlinkerror") || text.contains("failed to load native")) {
            add("NATIVE_OPENGL", "Ошибка native/OpenGL", "Обновите видеодрайвер и проверьте архитектуру Java/native-библиотек.", lineMods);
        }
        if (text.contains("zipexception") || text.contains("invalid loc header")
                || text.contains("error in opening zip file") || text.contains("corrupt") && text.contains("jar")) {
            add("CORRUPT_ARCHIVE", "Повреждён JAR/ZIP", "Повторно загрузите повреждённый файл или выполните восстановление.", lineMods);
        }
        if (text.contains("authentication") && (text.contains("failed") || text.contains("error"))
                || text.contains("invalid token") || text.contains("unauthorized")
                || text.contains("unknownhostexception") || text.contains("connectexception")
                || text.contains("sockettimeoutexception") || text.contains("sslhandshakeexception")) {
            add("AUTH_NETWORK", "Ошибка авторизации или сети", "Проверьте подключение, время системы и повторите вход.", lineMods);
        }
    }

    private List<ModIdentity> matchMods(String text) {
        List<ModIdentity> matches = new ArrayList<>();
        for (ModIdentity mod : mods) {
            if (matches(mod, text)) {
                matches.add(mod);
                implicated.putIfAbsent(mod.fileName, mod);
            }
        }
        return matches;
    }

    private boolean matches(ModIdentity mod, String text) {
        if (mod.fileName != null && containsLiteral(text, mod.fileName.toLowerCase(Locale.ROOT))) return true;
        if (mod.id != null && mod.id.length() >= 3 && containsToken(text, mod.id.toLowerCase(Locale.ROOT))) return true;
        if (mod.name != null && mod.name.length() >= 4 && containsToken(text, mod.name.toLowerCase(Locale.ROOT))) return true;
        if (mod.packageRoots != null) {
            for (String root : mod.packageRoots) {
                if (root != null && root.length() >= 5 && containsPackage(text, root.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    private boolean containsLiteral(String text, String value) {
        return !value.isBlank() && text.contains(value);
    }

    private boolean containsToken(String text, String value) {
        if (value.isBlank()) return false;
        return Pattern.compile("(?<![a-z0-9_-])" + Pattern.quote(value) + "(?![a-z0-9_-])").matcher(text).find();
    }

    private boolean containsPackage(String text, String value) {
        return Pattern.compile("(?<![a-z0-9_$])" + Pattern.quote(value) + "(?:\\.|/)").matcher(text).find();
    }

    private void add(String code, String title, String advice, List<ModIdentity> mods) {
        FindingState state = findings.computeIfAbsent(code, key -> new FindingState(code, title, advice));
        for (ModIdentity mod : mods) state.mods.add(mod.displayName());
    }

    public synchronized List<DiagnosticFinding> findings() {
        List<DiagnosticFinding> result = new ArrayList<>();
        for (FindingState state : findings.values()) {
            result.add(new DiagnosticFinding(state.code, DiagnosticFinding.Severity.ERROR,
                    state.title, state.advice, new ArrayList<>(state.mods)));
        }
        return result;
    }

    public synchronized List<ModIdentity> implicatedMods() {
        return new ArrayList<>(implicated.values());
    }

    private static final class FindingState {
        private final String code;
        private final String title;
        private final String advice;
        private final Set<String> mods = new LinkedHashSet<>();

        private FindingState(String code, String title, String advice) {
            this.code = code;
            this.title = title;
            this.advice = advice;
        }
    }
}
