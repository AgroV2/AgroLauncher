package by.agro.launcher.auth;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.SecureFiles;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;



public final class AccountStorage {

    private final LauncherPaths paths;
    private final CredentialStore credentials = CredentialStore.system();
    private final List<Account> accounts = new ArrayList<>();

    public AccountStorage(LauncherPaths paths) {
        this.paths = paths;
        load();
    }

    public List<Account> accounts() {
        return accounts;
    }

    public Account byId(String id) {
        if (id == null) {
            return null;
        }
        for (Account account : accounts) {
            if (id.equals(account.id)) {
                return account;
            }
        }
        return null;
    }

    
    public void save(Account account) {
        if (account.id == null || account.id.isBlank()) {
            account.id = account.type.name().toLowerCase() + "-" + account.username.toLowerCase();
        }
        Account existing = byId(account.id);
        if (existing != null) {
            accounts.set(accounts.indexOf(existing), account);
        } else {
            accounts.add(account);
        }
        storeSecrets(account);
        persist();
    }

    public void remove(Account account) {
        accounts.removeIf(a -> a.id != null && a.id.equals(account.id));
        try { credentials.remove(account.id); } catch (Exception e) {
            System.err.println("Не удалось удалить токен из системного хранилища: " + e.getMessage());
        }
        persist();
    }

    private void load() {
        accounts.clear();
        try {
            SecureFiles.rejectSymlinkParents(paths.accountsFile());
            if (!Files.exists(paths.accountsFile())) {
                return;
            }
            SecureFiles.rejectSymbolicLink(paths.accountsFile(), "Account storage file");
            try (Reader reader = Files.newBufferedReader(paths.accountsFile(), StandardCharsets.UTF_8)) {
                List<Account> loaded = Json.GSON.fromJson(reader,
                        new TypeToken<List<Account>>() {
                        }.getType());
                if (loaded != null) {
                    for (Account account : loaded) {
                        if (account != null && account.username != null && !account.username.isBlank()) {
                            restoreOrMigrateSecrets(account);
                            accounts.add(account);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Не удалось прочитать accounts.json: " + e.getMessage());
        }
    }

    private void storeSecrets(Account account) {
        if (account.isOffline()) return;
        try {
            credentials.put(account.id, Json.GSON.toJson(new String[]{account.accessToken, account.clientToken}));
        } catch (Exception e) {
            throw new IllegalStateException("Системное хранилище секретов недоступно", e);
        }
    }

    private void restoreOrMigrateSecrets(Account account) {
        if (account.isOffline()) return;
        String legacyAccessToken = account.accessToken;
        String legacyClientToken = account.clientToken;
        try {
            String stored = credentials.get(account.id);
            if (stored != null) {
                String[] values = Json.GSON.fromJson(stored, String[].class);
                if (values == null) throw new IOException("Повреждена запись токена в системном хранилище");
                account.accessToken = values.length > 0 && values[0] != null ? values[0] : "";
                account.clientToken = values.length > 1 && values[1] != null ? values[1] : "";
                return;
            }
            boolean hasLegacySecret = (legacyAccessToken != null && !legacyAccessToken.isBlank())
                    || (legacyClientToken != null && !legacyClientToken.isBlank());
            if (hasLegacySecret) {
                credentials.put(account.id, Json.GSON.toJson(new String[]{legacyAccessToken, legacyClientToken}));
                String verified = credentials.get(account.id);
                if (verified == null) throw new IOException("Системное хранилище не подтвердило запись токена");
                String[] values = Json.GSON.fromJson(verified, String[].class);
                if (values == null) throw new IOException("Системное хранилище вернуло повреждённый токен");
                account.accessToken = values.length > 0 && values[0] != null ? values[0] : "";
                account.clientToken = values.length > 1 && values[1] != null ? values[1] : "";
            }
        } catch (Exception e) {
            account.accessToken = legacyAccessToken;
            account.clientToken = legacyClientToken;
            System.err.println("Не удалось безопасно перенести токен; plaintext сохранён до успешной миграции: "
                    + e.getMessage());
        }
    }

    private void persist() {
        try {
            SecureFiles.rejectSymlinkParents(paths.accountsFile());
            if (Files.exists(paths.accountsFile())) {
                SecureFiles.rejectSymbolicLink(paths.accountsFile(), "Account storage file");
            }
            List<Account> metadata = new ArrayList<>();
            for (Account source : accounts) {
                Account copy = Json.GSON.fromJson(Json.GSON.toJson(source), Account.class);
                if (!copy.isOffline()) { copy.accessToken = ""; copy.clientToken = ""; }
                metadata.add(copy);
            }
            Json.write(paths.accountsFile(), metadata);
            SecureFiles.setOwnerOnly(paths.accountsFile());
        } catch (IOException e) {
            System.err.println("Не удалось сохранить аккаунты: " + e.getMessage());
        }
    }
}
