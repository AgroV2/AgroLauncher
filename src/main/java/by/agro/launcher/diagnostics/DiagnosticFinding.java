package by.agro.launcher.diagnostics;

import java.util.ArrayList;
import java.util.List;

public final class DiagnosticFinding {
    public enum Severity { INFO, WARNING, ERROR }

    public final String code;
    public final Severity severity;
    public final String title;
    public final String advice;
    public final List<String> implicatedMods;

    public DiagnosticFinding(String code, Severity severity, String title, String advice) {
        this(code, severity, title, advice, List.of());
    }

    public DiagnosticFinding(String code, Severity severity, String title, String advice,
                             List<String> implicatedMods) {
        this.code = code;
        this.severity = severity;
        this.title = title;
        this.advice = advice;
        this.implicatedMods = implicatedMods == null ? new ArrayList<>() : new ArrayList<>(implicatedMods);
    }

    @Override
    public String toString() {
        String mods = implicatedMods.isEmpty() ? "" : " — Моды: " + String.join(", ", implicatedMods);
        return severity + ": " + title + mods
                + (advice == null || advice.isBlank() ? "" : " — " + advice);
    }
}
