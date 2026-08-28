package by.agro.launcher.auth;


public final class Account {

    public enum Type {
        OFFLINE("accounts.offline"),
        ELY_BY(null);

        private final String nameKey;

        Type(String nameKey) {
            this.nameKey = nameKey;
        }

        
        public String displayName() {
            return nameKey == null ? "Ely.by" : by.agro.launcher.i18n.Strings.get(nameKey);
        }
    }

    
    public String id;

    public Type type = Type.OFFLINE;

    
    public String username = "";

    
    public String uuid = "";

    
    public String accessToken = "";

    
    public String clientToken = "";

    
    public String login = "";

    
    public long tokenUpdatedAt;

    public Account() {
    }

    public static Account offline(String username) {
        Account account = new Account();
        account.id = "offline-" + username.toLowerCase();
        account.type = Type.OFFLINE;
        account.username = username;
        account.uuid = OfflineAuth.uuidFor(username);
        account.accessToken = OfflineAuth.PLACEHOLDER_TOKEN;
        account.clientToken = "";
        return account;
    }

    public boolean isOffline() {
        return type == Type.OFFLINE;
    }

    
    public String userType() {
        return isOffline() ? "legacy" : "mojang";
    }

    
    public String uuidDashed() {
        if (uuid == null || uuid.length() != 32) {
            return uuid;
        }
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-"
                + uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20);
    }

    @Override
    public String toString() {
        return username + " (" + type.displayName() + ")";
    }
}
