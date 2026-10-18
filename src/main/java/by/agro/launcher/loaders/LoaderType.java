package by.agro.launcher.loaders;

public enum LoaderType {
    VANILLA("vanilla", "Vanilla", false),
    FABRIC("fabric", "Fabric", true),
    QUILT("quilt", "Quilt", true),
    FORGE("forge", "Forge", true),
    NEOFORGE("neoforge", "NeoForge", true),
    OPTIFINE("optifine", "OptiFine", false);

    private final String id;
    private final String displayName;
    private final boolean supportsMods;

    LoaderType(String id, String displayName, boolean supportsMods) {
        this.id = id;
        this.displayName = displayName;
        this.supportsMods = supportsMods;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public boolean supportsMods() {
        return supportsMods;
    }

    public static LoaderType fromId(String id) {
        if (id == null) {
            return VANILLA;
        }
        for (LoaderType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return VANILLA;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
