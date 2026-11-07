package by.agro.launcher;

import by.agro.launcher.auth.AccountStorage;
import by.agro.launcher.horizon.HorizonManager;
import by.agro.launcher.horizon.HorizonInstaller;
import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.SafeNetwork;
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
import by.agro.launcher.repair.RepairService;
import by.agro.launcher.version.VersionManifest;
import by.agro.launcher.version.VersionResolver;

import java.io.IOException;
import java.time.Duration;
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
    private final RepairService repairService;
    private final HorizonManager horizon;
    private final Map<LoaderType, LoaderInstaller> installers = new EnumMap<>(LoaderType.class);

    private volatile VersionManifest manifest;

    public LauncherContext() throws IOException {
        this(Settings.load());
    }


    public LauncherContext(Settings settings) throws IOException {
        this.paths = LauncherPaths.get();
        this.settings = settings != null ? settings : Settings.load();
        if (this.settings.gameDataPath != null && !this.settings.gameDataPath.isBlank()) {
            java.nio.file.Path custom = java.nio.file.Path.of(this.settings.gameDataPath.trim());
            if (custom.isAbsolute()) {
                LauncherPaths.setGameDataDir(custom);
            }
        }
        this.paths.ensureDirectories();
        this.downloader = new Downloader(this.settings.downloadParallelism, this.settings.downloadPerHost,
                new by.agro.launcher.core.DownloadManager());
        Downloader.configureProxy(this.settings.proxyMode, this.settings.proxyHost, this.settings.proxyPort);
        SafeNetwork.configureAllowedDownloadHosts(this.settings.downloadHostAllowList);
        Downloader.setVerificationCacheFile(paths.cacheDir().resolve("download-verification.json"));
        this.accounts = new AccountStorage(paths);
        this.javaManager = new JavaManager(paths, downloader, settings);
        this.gameLauncher = new GameLauncher(paths, downloader, settings, javaManager, accounts);
        this.versionResolver = gameLauncher.resolver();
        this.repairService = new RepairService(paths, downloader);
        this.horizon = new HorizonManager(paths, downloader, this.settings, gameLauncher);

        installers.put(LoaderType.HORIZON, new HorizonInstaller(horizon));
        installers.put(LoaderType.FABRIC, new FabricInstaller(paths, downloader, settings));
        installers.put(LoaderType.QUILT, new QuiltInstaller(paths, downloader, settings));
        installers.put(LoaderType.FORGE, new ForgeInstaller(paths, downloader, javaManager, settings));
        installers.put(LoaderType.NEOFORGE, new NeoForgeInstaller(paths, downloader, javaManager, settings));
        installers.put(LoaderType.OPTIFINE, new OptiFineInstaller(paths, downloader, javaManager, settings));
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

    public RepairService repairService() {
        return repairService;
    }

    public LoaderInstaller installer(LoaderType type) {
        return installers.get(type);
    }

    public HorizonManager horizon() { return horizon; }

    public OptiFineInstaller optiFineInstaller() {
        return (OptiFineInstaller) installers.get(LoaderType.OPTIFINE);
    }


    public VersionManifest manifest() throws IOException {
        VersionManifest local = manifest;
        if (local == null) {
            synchronized (this) {
                local = manifest;
                if (local == null) {
                    local = VersionManifest.fetchCached(downloader, paths.versionManifestCacheFile(),
                            Duration.ofHours(6), settings.offlineMode);
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
            if (settings.offlineMode) {
                throw new IOException("Refreshing the version manifest requires a network connection; offline mode is active");
            }
            manifest = VersionManifest.fetchCached(downloader, paths.versionManifestCacheFile(),
                    Duration.ZERO, false);
            return manifest;
        }
    }
}
