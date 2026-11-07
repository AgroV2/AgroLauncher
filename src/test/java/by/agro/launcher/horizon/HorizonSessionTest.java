package by.agro.launcher.horizon;

import by.agro.launcher.auth.Account;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class HorizonSessionTest {
    @TempDir Path temp;
    private Account account() {
        Account a = new Account(); a.type = Account.Type.MICROSOFT; a.username = "Alex";
        a.uuid = "0123456789abcdef0123456789abcdef"; a.accessToken = "test-secret-token";
        return a;
    }
    @Test void sessionAndCommandUseStdinNotArguments() throws Exception {
        var cmd = HorizonManager.authenticatedLaunchCommand(temp, temp, temp, new Settings(), "example.org:25565");
        assertTrue(cmd.contains("--session-stdin")); assertTrue(cmd.contains("--quickPlayMultiplayer"));
        assertFalse(cmd.contains("--name")); assertFalse(cmd.toString().contains("test-secret-token"));
        var json = Json.parseObject(new String(HorizonSession.session(account()), StandardCharsets.UTF_8));
        assertEquals("Alex",json.get("name").getAsString());
        assertEquals("msa",json.get("user_type").getAsString());
        assertEquals("test-secret-token",json.get("access_token").getAsString());
        Account offline = Account.offline("Alex"); assertThrows(IOException.class, () -> HorizonSession.session(offline));
    }
    @Test void capabilityParsingRejectsOldOrUnsupportedBuilds() throws Exception {
        String data = "{\"launch_api\":\"0.1.0\",\"minecraft\":\"26.3\",\"options\":[{\"name\":\"--session-stdin\"}]}";
        Set<String> options = HorizonSession.parseOptions(data);
        HorizonSession.requireOptions(options, java.util.List.of("--session-stdin"));
        assertThrows(IOException.class, () -> HorizonSession.requireOptions(options, java.util.List.of("--session-file")));
        assertThrows(IOException.class, () -> HorizonSession.parseOptions(data.replace("0.1.0", "0.2.0")));
        assertThrows(IOException.class, () -> HorizonSession.parseOptions("not json"));
    }
    @Test @EnabledOnOs(OS.LINUX) void launchesStubAndTransfersSessionOnlyThroughStdin() throws Exception {
        Path script = temp.resolve("horizon-client");
        Path received = temp.resolve("test-session.json");
        Files.writeString(script, "#!/bin/sh\ncat > test-session.json\n");
        assertTrue(script.toFile().setExecutable(true));
        var command = HorizonManager.authenticatedLaunchCommand(temp, temp, temp, new Settings(), "");
        var output = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var process = by.agro.launcher.launch.GameProcess.start(command, temp, output::add, c -> {});
        try (var stdin = process.raw().getOutputStream()) { stdin.write(HorizonSession.session(account())); }
        assertTrue(process.raw().waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, process.raw().exitValue());
        assertEquals("test-secret-token", Json.parseObject(Files.readString(received)).get("access_token").getAsString());
        assertFalse(command.toString().contains("test-secret-token"));
        assertFalse(output.toString().contains("test-secret-token"));
    }
    @Test @EnabledOnOs(OS.LINUX) void probesAnActualExecutable() throws Exception {
        Path script = temp.resolve("horizon-client");
        Files.writeString(script, "#!/bin/sh\n[ \"$1\" = \"--launch-api\" ] || exit 2\nprintf '%s' '{\"launch_api\":\"0.1.0\",\"minecraft\":\"26.3\",\"options\":[{\"name\":\"--session-stdin\"}]}'\n");
        assertTrue(script.toFile().setExecutable(true));
        assertTrue(HorizonSession.supportedOptions(script, temp).contains("--session-stdin"));
        Files.writeString(script, "#!/bin/sh\nexit 2\n");
        assertThrows(IOException.class, () -> HorizonSession.supportedOptions(script, temp));
    }
}
