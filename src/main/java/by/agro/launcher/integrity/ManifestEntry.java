package by.agro.launcher.integrity;


public final class ManifestEntry {
    public String path;
    public String sha256;
    public long size;
    public String ownership = "launcher";
    public String provenance = "observed";
    public String sourceUrl;

    public ManifestEntry() {
    }

    public ManifestEntry(String path, String sha256, long size, String ownership,
                         String provenance, String sourceUrl) {
        this.path = path;
        this.sha256 = sha256;
        this.size = size;
        this.ownership = ownership;
        this.provenance = provenance;
        this.sourceUrl = sourceUrl;
    }

    public boolean trusted() {
        return "trusted".equalsIgnoreCase(provenance);
    }
}
