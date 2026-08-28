package by.agro.launcher.launch;

import by.agro.launcher.auth.Account;
import by.agro.launcher.auth.AuthlibInjector;
import by.agro.launcher.auth.ElyByAuth;
import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.Settings;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.jvm.JavaManager;
import by.agro.launcher.version.AssetManager;
import by.agro.launcher.version.Library;
import by.agro.launcher.version.NativesExtractor;
import by.agro.launcher.version.ResolvedVersion;
import by.agro.launcher.version.Rule;
import by.agro.launcher.version.VersionManifest;
import by.agro.launcher.version.VersionResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class GameLauncher {

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final Settings settings;
    private final VersionResolver resolver;
    private final AssetManager assetManager;
    private final NativesExtractor nativesExtractor;
    private final JavaManager javaManager;
    private final AuthlibInjector authlibInjector;
    private final LaunchCommandBuilder commandBuilder;

    public GameLauncher(LauncherPaths paths, Downloader downloader, Settings settings, JavaManager javaManager) {
        this.paths = paths;
        this.downloader = downloader;
        this.settings = settings;
        this.resolver = new VersionResolver(paths, downloader);
        this.assetManager = new AssetManager(paths, downloader);
        this.nativesExtractor = new NativesExtractor(paths);
        this.javaManager = javaManager;
        this.authlibInjector = new AuthlibInjector(paths, downloader);
        this.commandBuilder = new LaunchCommandBuilder(paths);
    }
    
    private Rule.FeatureSet featureSet() {
        return feature -> "has_custom_resolution".equals(feature) && !settings.fullscreen;
    }
    public List<String> prepare(String versionId, Account account, VersionManifest manifest,
                               ProgressListener listener) throws IOException {
        return prepare(versionId, account, manifest, listener, paths.gameDir());
    }

    public List<String> prepare(String versionId, Account account, VersionManifest manifest,
                               ProgressListener listener, Path launchGameDir) throws IOException {
        paths.ensureDirectories();

        listener.onMessage(Strings.get("progress.versionPrepare", versionId));
        ResolvedVersion version = resolver.resolve(versionId, manifest, listener);
        listener.onMessage(Strings.get("progress.versionChain", String.join(" → ", version.chain)));

        downloadClientJar(version, listener);

        downloadLibraries(version, listener);

        Path assetsDir = assetManager.downloadAssets(version, listener);

        Path nativesDir = nativesExtractor.extract(version, featureSet(), listener);

        listener.onProgress(Strings.get("progress.javaCheck"), 0, 1, Strings.get("progress.javaRequired", version.javaMajorVersion));
        String javaExecutable = javaManager.resolveJavaForGame(version.javaMajorVersion, listener);
        listener.onMessage(Strings.get("progress.javaUsing", javaExecutable));

        
        LaunchOptions options = new LaunchOptions();
        options.version = version;
        options.account = account;
        options.settings = settings;
        options.gameDir = launchGameDir != null ? launchGameDir : paths.gameDir();
        options.assetsDir = assetsDir;
        options.nativesDir = nativesDir;
        options.javaExecutable = javaExecutable;

        if (!account.isOffline()) {
            listener.onProgress(Strings.get("progress.auth"), 0, 1, account.username);
            ElyByAuth.ensureValidToken(account);
            options.authlibInjectorJar = authlibInjector.ensureInstalled(listener);
            options.authlibServer = ElyByAuth.INJECTOR_ARGUMENT;
            listener.onProgress(Strings.get("progress.auth"), 1, 1, Strings.get("progress.tokenValid"));
        }

        
        Files.createDirectories(options.gameDir.resolve("mods"));

        List<String> command = commandBuilder.build(options);
        listener.onProgress(Strings.get("progress.done"), 1, 1, Strings.get("progress.commandReady"));
        return command;
    }

    

    public GameProcess launch(String versionId, Account account, VersionManifest manifest,
                              ProgressListener listener, Consumer<String> onLine, IntConsumer onExit)
            throws IOException {
        return launch(versionId, account, manifest, listener, onLine, onExit, paths.gameDir());
    }

    public GameProcess launch(String versionId, Account account, VersionManifest manifest,
                              ProgressListener listener, Consumer<String> onLine, IntConsumer onExit,
                              Path launchGameDir) throws IOException {
        Path gameDir = launchGameDir != null ? launchGameDir : paths.gameDir();
        List<String> command = prepare(versionId, account, manifest, listener, gameDir);

        listener.onMessage("Запуск: " + String.join(" ", command));
        return GameProcess.start(command, gameDir, onLine, onExit);
    }

    private void downloadClientJar(ResolvedVersion version, ProgressListener listener) throws IOException {
        Path clientJar = paths.versionJar(version.jarVersionId);
        if (version.clientDownload == null || version.clientDownload.url == null) {
            if (!Files.exists(clientJar)) {
                throw new IOException("Нет ссылки на клиентский jar и файл отсутствует: " + clientJar);
            }
            return;
        }
        listener.onProgress(Strings.get("progress.clientJar"), 0, 1, version.jarVersionId + ".jar");
        Files.createDirectories(clientJar.getParent());
        downloader.download(version.clientDownload.url, clientJar, version.clientDownload.sha1);
        listener.onProgress(Strings.get("progress.clientJar"), 1, 1, Strings.get("progress.done"));
    }

    private void downloadLibraries(ResolvedVersion version, ProgressListener listener) throws IOException {
        List<Downloader.Task> tasks = new ArrayList<>();
        List<Library> libraries = version.applicableLibraries(featureSet());

        for (Library library : libraries) {
            Library.Artifact artifact = library.resolveArtifact();
            if (artifact == null || artifact.url == null || artifact.url.isBlank()) {
                continue;
            }
            Path target = paths.librariesDir().resolve(library.relativePath());
            tasks.add(new Downloader.Task(artifact.url, target, artifact.sha1, artifact.size));
        }

        listener.onMessage(Strings.get("progress.librariesCount", tasks.size()));
        downloader.downloadAll(tasks, Strings.get("progress.libraries"), listener);
    }

    public VersionResolver resolver() {
        return resolver;
    }
}
