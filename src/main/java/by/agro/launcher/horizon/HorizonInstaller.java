package by.agro.launcher.horizon;

import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.loaders.LoaderInstaller;
import by.agro.launcher.loaders.LoaderType;
import java.io.IOException;
import java.util.List;


public final class HorizonInstaller implements LoaderInstaller {
    private final HorizonManager manager;
    public HorizonInstaller(HorizonManager manager) { this.manager = manager; }
    @Override public LoaderType type() { return LoaderType.HORIZON; }
    @Override public List<LoaderVersion> availableVersions(String minecraftVersion) {
        return supports(minecraftVersion) ? List.of(new LoaderVersion("nightly", false, true)) : List.of();
    }
    @Override public boolean supports(String minecraftVersion) {
        return HorizonManager.MINECRAFT_VERSION.equals(minecraftVersion);
    }
    @Override public String install(String minecraftVersion, String loaderVersion, ProgressListener listener) throws IOException {
        if (!supports(minecraftVersion)) throw new IOException(Strings.get("horizon.onlyVersion"));
        if (loaderVersion != null && !loaderVersion.isBlank() && !"nightly".equals(loaderVersion)) {
            throw new IOException(Strings.get("horizon.incompatibleBuild"));
        }
        manager.ensureInstalled(listener);
        return minecraftVersion;
    }
}
