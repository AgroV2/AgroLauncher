package by.agro.launcher.backup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class BackupManifest {
    public int schemaVersion = 1;
    public String backupId;
    public String createdAt = Instant.now().toString();
    public List<Entry> entries = new ArrayList<>();

    public static final class Entry {
        public String path;
        public String sha256;
        public long size;

        public Entry() { }
        public Entry(String path, String sha256, long size) {
            this.path = path; this.sha256 = sha256; this.size = size;
        }
    }
}
