package by.agro.launcher.integrity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class InstallationManifest {
    public static final int CURRENT_SCHEMA = 1;

    public int schemaVersion = CURRENT_SCHEMA;
    public String installationId = "default";
    public String createdAt = Instant.now().toString();
    public List<ManifestEntry> entries = new ArrayList<>();

    public void applyDefaults() {
        if (schemaVersion <= 0) schemaVersion = CURRENT_SCHEMA;
        if (installationId == null || installationId.isBlank()) installationId = "default";
        if (createdAt == null || createdAt.isBlank()) createdAt = Instant.now().toString();
        if (entries == null) entries = new ArrayList<>();
        for (ManifestEntry entry : entries) {
            if (entry == null) continue;
            if (entry.ownership == null || entry.ownership.isBlank()) entry.ownership = "launcher";
            if (entry.provenance == null || entry.provenance.isBlank()) entry.provenance = "observed";
        }
    }
}
