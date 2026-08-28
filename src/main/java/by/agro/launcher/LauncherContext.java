package by.agro.launcher;

import by.agro.launcher.auth.AccountStorage;
import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Settings;
import by.agro.launcher.jvm.JavaManager;
import by.agro.launcher.launch.GameLauncher;
import by.agro.launcher.loaders.FabricInstaller;
import by.agro.launcher.loaders.ForgeInstaller;
import by.agro.launcher.loaders.LoaderInstaller;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.loaders.NeoForgeInstaller;
import by.agro.launcher.loaders.OptiFineInstaller;
import by.agro.launcher.loaders.QuiltInstaller;
import by.agro.launcher.version.VersionManifest;
import by.agro.launcher.version.VersionResolver;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;


public final class LauncherContext {

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final Settings settings;
    private final AccountStorage accounts;
    private final JavaManager javaManager;
    private final GameLauncher gameLauncher;
    private final VersionResolver versionResolver;
    private final Map<LoaderType, LoaderInstaller> installers = new EnumMap<>(LoaderType.class);

    private volatile VersionManifest manifest;

    public LauncherContext() throws IOException {
        this(Settings.load());
    }


    public LauncherContext(Settings settings) throws IOException {
        this.paths = LauncherPaths.get();
        this.paths.ensureDirectories();
        this.downloader = new Downloader();
        this.settings = settings != null ? settings : Settings.load();
        this.accounts = new AccountStorage(paths);
        this.javaManager = new JavaManager(paths, downloader, settings);
        this.gameLauncher = new GameLauncher(paths, downloader, settings, javaManager);
        this.versionResolver = gameLauncher.resolver();

        installers.put(LoaderType.FABRIC, new FabricInstaller(paths, downloader));
        installers.put(LoaderType.QUILT, new QuiltInstaller(paths, downloader));
        installers.put(LoaderType.FORGE, new ForgeInstaller(paths, downloader, javaManager));
        installers.put(LoaderType.NEOFORGE, new NeoForgeInstaller(paths, downloader, javaManager));
        installers.put(LoaderType.OPTIFINE, new OptiFineInstaller(paths, downloader, javaManager));
    }

    public LauncherPaths paths() {
        return paths;
    }

    public Downloader downloader() {
        return downloader;
    }

    public Settings settings() {
        return settings;
    }

    public AccountStorage accounts() {
        return accounts;
    }

    public JavaManager javaManager() {
        return javaManager;
    }

    public GameLauncher gameLauncher() {
        return gameLauncher;
    }

    public VersionResolver versionResolver() {
        return versionResolver;
    }

    public LoaderInstaller installer(LoaderType type) {
        return installers.get(type);
    }

    public OptiFineInstaller optiFineInstaller() {
        return (OptiFineInstaller) installers.get(LoaderType.OPTIFINE);
    }


    public VersionManifest manifest() throws IOException {
        VersionManifest local = manifest;
        if (local == null) {
            synchronized (this) {
                local = manifest;
                if (local == null) {
                    local = VersionManifest.fetch(downloader);
                    manifest = local;
                }
            }
        }
        return local;
    }


    public VersionManifest manifestIfLoaded() {
        return manifest;
    }


    public VersionManifest reloadManifest() throws IOException {
        synchronized (this) {
            manifest = VersionManifest.fetch(downloader);
            return manifest;
        }
    }
}
