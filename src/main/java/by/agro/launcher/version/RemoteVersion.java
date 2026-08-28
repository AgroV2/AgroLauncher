package by.agro.launcher.version;

/** Запись о версии из version_manifest_v2.json. */
public final class RemoteVersion {

    public final String id;
    public final String type;      // release / snapshot / old_beta / old_alpha
    public final String url;
    public final String sha1;
    public final String releaseTime;

    public RemoteVersion(String id, String type, String url, String sha1, String releaseTime) {
        this.id = id;
        this.type = type;
        this.url = url;
        this.sha1 = sha1;
        this.releaseTime = releaseTime;
    }

    public boolean isRelease() {
        return "release".equals(type);
    }

    public boolean isSnapshot() {
        return "snapshot".equals(type);
    }

    public boolean isOld() {
        return type != null && type.startsWith("old_");
    }

    @Override
    public String toString() {
        return id;
    }
}
