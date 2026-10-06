package by.agro.launcher.diagnostics;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Redactor {
    private static final String HIDDEN = "<redacted>";
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_SECRET = Pattern.compile("(?i)(\\\"?(?:access[_-]?token|refresh[_-]?token|client[_-]?token|id[_-]?token|api[_-]?key|client[_-]?secret|password|passphrase|secret|cookie|authorization|totp|otp|code[_-]?verifier)\\\"?\\s*[:=]\\s*\\\"?)[^\\\"\\s,}]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern UUID = Pattern.compile("(?i)\\b[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}\\b");
    private static final Pattern UNIX_HOME = Pattern.compile("(?i)(?:/home/|/users/)[^/\\s]+(?:/[^\\s:;,'\\\"]*)?");
    private static final Pattern WINDOWS_HOME = Pattern.compile("(?i)[a-z]:\\\\users\\\\[^\\\\\\s]+(?:\\\\[^\\s:;,'\\\"]*)?");

    private Redactor() {
    }

    public static String redact(String value) {
        if (value == null) return "";
        String result = BEARER.matcher(value).replaceAll("$1" + HIDDEN);
        result = JSON_SECRET.matcher(result).replaceAll("$1" + HIDDEN);
        result = UUID.matcher(result).replaceAll("<uuid>");
        result = UNIX_HOME.matcher(result).replaceAll("<home>");
        result = WINDOWS_HOME.matcher(result).replaceAll("<home>");
        return result;
    }

    public static String renderCommand(List<String> command) {
        if (command == null) return "";
        List<String> safe = new ArrayList<>(command.size());
        boolean hideNext = false;
        for (String raw : command) {
            String arg = raw == null ? "" : raw;
            String lower = arg.toLowerCase(Locale.ROOT);
            if (hideNext) {
                safe.add(HIDDEN);
                hideNext = false;
                continue;
            }
            if (isSensitiveSwitch(lower)) {
                safe.add(arg);
                hideNext = true;
            } else if (containsSensitiveAssignment(lower) || lower.startsWith("-javaagent:")) {
                int equals = arg.indexOf('=');
                safe.add(equals >= 0 ? arg.substring(0, equals + 1) + HIDDEN : HIDDEN);
            } else if ("--username".equals(lower)) {
                safe.add(arg);
                hideNext = true;
            } else {
                safe.add(redactPathArgument(redact(arg)));
            }
        }
        return String.join(" ", safe);
    }

    private static boolean isSensitiveSwitch(String value) {
        return value.equals("--accesstoken") || value.equals("--access-token")
                || value.equals("--uuid") || value.equals("--userproperties")
                || value.equals("--clientid") || value.equals("--xuid")
                || value.equals("--password") || value.equals("--passphrase")
                || value.equals("--authorization") || value.equals("--cookie")
                || value.equals("--refreshtoken") || value.equals("--refresh-token")
                || value.equals("--clientsecret") || value.equals("--client-secret")
                || value.equals("--apikey") || value.equals("--api-key")
                || value.equals("--totp") || value.equals("--otp");
    }

    private static boolean containsSensitiveAssignment(String value) {
        return value.contains("accesstoken=") || value.contains("access_token=")
                || value.contains("refreshtoken=") || value.contains("refresh_token=")
                || value.contains("clienttoken=") || value.contains("client_token=")
                || value.contains("clientsecret=") || value.contains("client_secret=")
                || value.contains("apikey=") || value.contains("api_key=")
                || value.contains("password=") || value.contains("passphrase=")
                || value.contains("authorization=") || value.contains("cookie=")
                || value.contains("totp=") || value.contains("otp=") || value.contains("token:");
    }

    private static String redactPathArgument(String value) {
        try {
            Path path = Path.of(value);
            if (path.isAbsolute()) return "<absolute-path>/" + path.getFileName();
        } catch (RuntimeException ignored) {
        }
        return value;
    }
}
