package by.agro.launcher.repair;

import by.agro.launcher.integrity.ManifestEntry;

public final class RepairAction {
    public enum Kind { REPLACE }

    public Kind kind = Kind.REPLACE;
    public ManifestEntry entry;

    public RepairAction() {
    }

    public RepairAction(ManifestEntry entry) {
        this.entry = entry;
    }
}
