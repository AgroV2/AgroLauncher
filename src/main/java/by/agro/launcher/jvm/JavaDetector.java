package by.agro.launcher.jvm;

import by.agro.launcher.core.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class JavaDetector {

    private JavaDetector() {
    }

    public static int queryMajorVersion(Path javaExecutable) {
        try {
            Process process = new ProcessBuilder(javaExecutable.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return 0;
            }
            return parseMajorVersion(output);
        } catch (IOException e) {
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }


    static int parseMajorVersion(String output) {
        if (output == null) {
            return 0;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("version \"([0-9]+)(?:\\.([0-9]+))?")
                .matcher(output);
        if (!matcher.find()) {
            return 0;
        }
        try {
            int first = Integer.parseInt(matcher.group(1));
            if (first == 1 && matcher.group(2) != null) {
                return Integer.parseInt(matcher.group(2));
            }
            return first;
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    public static Path findInPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return null;
        }
        String exeName = Platform.javaConsoleExecutableName();
        String separator = Platform.isWindows() ? ";" : ":";
        for (String entry : pathEnv.split(separator)) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path candidate = Path.of(entry.trim(), exeName);
                if (Files.isExecutable(candidate)) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }
}
