package by.agro.launcher.diagnostics;

public final class DiagnosticFinding {
    public enum Severity { INFO, WARNING, ERROR }

    public final String code;
    public final Severity severity;
    public final String title;
    public final String advice;

    public DiagnosticFinding(String code, Severity severity, String title, String advice) {
        this.code = code;
        this.severity = severity;
        this.title = title;
        this.advice = advice;
    }

    @Override
    public String toString() {
        return severity + ": " + title + (advice == null || advice.isBlank() ? "" : " — " + advice);
    }
}
