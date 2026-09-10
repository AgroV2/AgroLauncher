package by.agro.launcher.jvm;

import java.nio.file.Path;


public final class JavaSelection {
    public enum Mode { AUTO, MANAGED, SYSTEM, CUSTOM }

    public final Path executable;
    public final int detectedMajor;
    public final int requiredMajor;
    public final Mode source;
    public final String reason;

    public JavaSelection(Path executable, int detectedMajor, int requiredMajor,
                         Mode source, String reason) {
        this.executable = executable;
        this.detectedMajor = detectedMajor;
        this.requiredMajor = requiredMajor;
        this.source = source;
        this.reason = reason;
    }

    public String executableText() {
        return executable.toAbsolutePath().normalize().toString();
    }

    @Override
    public String toString() {
        return "Java " + detectedMajor + " (" + source + ") — " + executable;
    }
}
