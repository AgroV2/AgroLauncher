package by.agro.launcher.horizon;

import by.agro.launcher.auth.Account;
import by.agro.launcher.core.Json;
import by.agro.launcher.i18n.Strings;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;


final class HorizonSession {
    private HorizonSession() { }
    static Set<String> supportedOptions(Path executable, Path workingDir) throws IOException {
        Process process = null;
        try {
            process = new ProcessBuilder(executable.toString(), "--launch-api").directory(workingDir.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            Process p = process;
            FutureTask<byte[]> output = new FutureTask<>(() -> {
                try (var in = p.getInputStream()) { return in.readNBytes(262145); }
            });
            Thread reader = new Thread(output, "horizon-api-probe"); reader.setDaemon(true); reader.start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IOException("Horizon API timeout");
            if (process.exitValue() != 0) throw new IOException("Horizon API unavailable");
            byte[] data = output.get(2, TimeUnit.SECONDS);
            if (data.length > 262144) throw new IOException("Horizon API response too large");
            return parseOptions(new String(data, StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new IOException(Strings.get("horizon.interrupted"), e);
        } catch (Exception e) {
            throw new IOException(Strings.get("horizon.sessionUnsupported"), e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }
    static Set<String> parseOptions(String data) throws IOException {
        try {
            JsonObject obj = Json.parseObject(data);
            if (!Json.string(obj, "launch_api", "").matches("0\\.1\\.[0-9]+")
                    || !"26.3".equals(Json.string(obj, "minecraft", ""))) throw new IllegalArgumentException();
            Set<String> options = new HashSet<>();
            for (var item : obj.getAsJsonArray("options")) options.add(item.getAsJsonObject().get("name").getAsString());
            return options;
        } catch (RuntimeException e) { throw new IOException(Strings.get("horizon.sessionUnsupported"), e); }
    }
    static void requireOptions(Set<String> supported, java.util.List<String> command) throws IOException {
        for (String item : command) {
            if (item.startsWith("--") && !supported.contains(item))
                throw new IOException(Strings.get("horizon.sessionUnsupported"));
        }
    }
    static byte[] session(Account account) throws IOException {
        if (account == null || account.type != Account.Type.MICROSOFT || account.username == null
                || !account.username.matches("[A-Za-z0-9_]{3,16}") || account.uuid == null
                || !account.uuid.replace("-", "").matches("[0-9a-fA-F]{32}")
                || account.accessToken == null || account.accessToken.isBlank())
            throw new IOException(Strings.get("microsoft.signInAgain"));
        JsonObject obj = new JsonObject(); obj.addProperty("name", account.username);
        obj.addProperty("uuid", account.uuid); obj.addProperty("access_token", account.accessToken);
        obj.addProperty("user_type", "msa");
        return obj.toString().getBytes(StandardCharsets.UTF_8);
    }
}
