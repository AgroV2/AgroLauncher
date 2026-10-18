package by.agro.launcher.offline;

import by.agro.launcher.auth.Account;

import java.io.IOException;

public final class OfflinePolicy {
    private final ConnectivityState state;

    public OfflinePolicy(ConnectivityState state) {
        this.state = state == null ? ConnectivityState.ONLINE : state;
    }

    public ConnectivityState state() { return state; }
    public boolean offline() { return state == ConnectivityState.OFFLINE; }

    public void requireNetwork(String operation) throws IOException {
        if (offline()) throw new IOException(operation + " requires a network connection; offline mode is active");
    }

    public void requireLaunchAccount(Account account) throws IOException {
        if (!offline() || account == null || account.isOffline()) return;
        if (account.accessToken == null || account.accessToken.isBlank()
                || account.uuid == null || account.uuid.isBlank()
                || account.username == null || account.username.isBlank()) {
            throw new IOException("Network account has no valid local authentication state for offline launch");
        }
    }
}
