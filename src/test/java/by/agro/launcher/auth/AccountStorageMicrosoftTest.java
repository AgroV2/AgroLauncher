package by.agro.launcher.auth;

import by.agro.launcher.core.LauncherPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AccountStorageMicrosoftTest {
    @TempDir Path root;
    static class Vault implements CredentialStore {
        final Map<String, String> entries = new HashMap<>();
        boolean fail;
        public void put(String id, String value) throws IOException {
            if (fail) throw new IOException("unavailable");
            entries.put(id, value);
        }
        public String get(String id) { return entries.get(id); }
        public void remove(String id) { entries.remove(id); }
    }
    private Account account() {
        Account a = new Account(); a.type = Account.Type.MICROSOFT;
        a.id = "microsoft-test"; a.username = "TestUser";
        a.uuid = "0123456789abcdef0123456789abcdef";
        a.accessToken = "sensitive-minecraft-token"; a.clientToken = "sensitive-refresh-token";
        return a;
    }
    @Test void secretsStayInVaultAndRestore() throws Exception {
        var paths = LauncherPaths.forRoot(root); var vault = new Vault();
        var storage = new AccountStorage(paths, vault); storage.save(account());
        String metadata = Files.readString(paths.accountsFile());
        assertFalse(metadata.contains("sensitive-minecraft-token"));
        assertFalse(metadata.contains("sensitive-refresh-token"));
        var restored = new AccountStorage(paths, vault); restored.awaitSecrets();
        Account a = restored.byId("microsoft-test"); assertNotNull(a);
        assertEquals("sensitive-minecraft-token", a.accessToken);
        assertEquals("sensitive-refresh-token", a.clientToken);
    }
    @Test void vaultFailureDoesNotAddAccount() {
        var vault = new Vault(); vault.fail = true;
        var storage = new AccountStorage(LauncherPaths.forRoot(root), vault);
        assertThrows(IllegalStateException.class, () -> storage.save(account()));
        assertTrue(storage.accounts().isEmpty());
    }
}
