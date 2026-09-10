package by.agro.launcher.diagnostics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LogAnalyzer {
    private final Map<String, DiagnosticFinding> findings = new LinkedHashMap<>();

    public synchronized void accept(String line) {
        String text = line == null ? "" : line.toLowerCase(Locale.ROOT);
        if (text.contains("unsupportedclassversionerror") || text.contains("compiled by a more recent version")
                || text.contains("only recognizes class file versions")) {
            add("JAVA_MISMATCH", "Несовместимая версия Java", "Выберите Java, требуемую этой версией Minecraft/loader.");
        }
        if (text.contains("outofmemoryerror") || text.contains("java heap space") || text.contains("gc overhead limit")) {
            add("OUT_OF_MEMORY", "Недостаточно памяти Java", "Увеличьте RAM или уменьшите число модов/ресурспаков.");
        }
        if (text.contains("classnotfoundexception") || text.contains("noclassdeffounderror")
                || text.contains("module not found") || text.contains("could not find or load main class")) {
            add("MISSING_CLASS", "Отсутствует класс или модуль", "Проверьте библиотеки, loader и совместимость модов.");
        }
        if (text.contains("requires") && (text.contains("mod") || text.contains("fabric") || text.contains("forge"))
                || text.contains("missing mandatory dependencies") || text.contains("mod resolution encountered")) {
            add("MOD_DEPENDENCY", "Не выполнена зависимость мода", "Установите требуемую версию зависимости или удалите несовместимый мод.");
        }
        if (text.contains("mixin") && (text.contains("failed") || text.contains("error") || text.contains("exception"))) {
            add("MIXIN_FAILURE", "Ошибка Mixin", "Проверьте совместимость модов и версий loader.");
        }
        if (text.contains("opengl") || text.contains("lwjgl") && text.contains("error")
                || text.contains("unsatisfiedlinkerror") || text.contains("failed to load native")) {
            add("NATIVE_OPENGL", "Ошибка native/OpenGL", "Обновите видеодрайвер и проверьте архитектуру Java/native-библиотек.");
        }
        if (text.contains("zipexception") || text.contains("invalid loc header")
                || text.contains("error in opening zip file") || text.contains("corrupt") && text.contains("jar")) {
            add("CORRUPT_ARCHIVE", "Повреждён JAR/ZIP", "Повторно загрузите повреждённый файл или выполните восстановление.");
        }
        if (text.contains("authentication") && (text.contains("failed") || text.contains("error"))
                || text.contains("invalid token") || text.contains("unauthorized")
                || text.contains("unknownhostexception") || text.contains("connectexception")
                || text.contains("sockettimeoutexception") || text.contains("sslhandshakeexception")) {
            add("AUTH_NETWORK", "Ошибка авторизации или сети", "Проверьте подключение, время системы и повторите вход.");
        }
    }

    private void add(String code, String title, String advice) {
        findings.putIfAbsent(code, new DiagnosticFinding(code, DiagnosticFinding.Severity.ERROR, title, advice));
    }

    public synchronized List<DiagnosticFinding> findings() {
        return new ArrayList<>(findings.values());
    }
}
