package by.agro.launcher.auth;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;



public final class AccountStorage {

    private final LauncherPaths paths;
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
        persist();
    }

    public void remove(Account account) {
        accounts.removeIf(a -> a.id != null && a.id.equals(account.id));
        persist();
    }

    private void load() {
        accounts.clear();
        try {
            if (!Files.exists(paths.accountsFile())) {
                return;
            }
            try (Reader reader = Files.newBufferedReader(paths.accountsFile(), StandardCharsets.UTF_8)) {
                List<Account> loaded = Json.GSON.fromJson(reader,
                        new TypeToken<List<Account>>() {
                        }.getType());
                if (loaded != null) {
                    for (Account account : loaded) {
                        if (account != null && account.username != null && !account.username.isBlank()) {
                            accounts.add(account);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("Не удалось прочитать accounts.json: " + e.getMessage());
        }
    }

    private void persist() {
        try {
            Json.write(paths.accountsFile(), accounts);
        } catch (IOException e) {
            System.err.println("Не удалось сохранить аккаунты: " + e.getMessage());
        }
    }
}
