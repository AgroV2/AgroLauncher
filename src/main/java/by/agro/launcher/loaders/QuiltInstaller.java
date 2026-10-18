package by.agro.launcher.loaders;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Settings;

public final class QuiltInstaller extends FabricInstaller {

    private static final String QUILT_META_BASE = "https://meta.quiltmc.org/v3";

    public QuiltInstaller(LauncherPaths paths, Downloader downloader) {
        super(paths, downloader);
    }

    public QuiltInstaller(LauncherPaths paths, Downloader downloader, Settings settings) {
        super(paths, downloader, settings);
    }

    @Override
    public LoaderType type() {
        return LoaderType.QUILT;
    }

    @Override
    protected String metaBase() {
        return QUILT_META_BASE;
    }

    @Override
    protected String loaderName() {
        return "quilt-loader";
    }
}
