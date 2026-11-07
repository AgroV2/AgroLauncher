package by.agro.launcher.horizon;

import by.agro.launcher.auth.Account;
import by.agro.launcher.core.*;
import by.agro.launcher.diagnostics.LaunchSession;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.jvm.ArchiveExtractor;
import by.agro.launcher.jvm.JavaSelection;
import by.agro.launcher.launch.GameLauncher;
import by.agro.launcher.launch.GameProcess;
import by.agro.launcher.version.VersionManifest;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

public final class HorizonManager {
    public static final String MINECRAFT_VERSION = "26.3";
    public static final String INSTANCE_NAME = "26.3 Rust";
    private static final String RELEASE = "https://github.com/horizon-rust/horizon/releases/download/nightly/";
    private static final String PREPARED_MARKER = ".agrolauncher-prepared";
    private static final Pattern BUILD_ID = Pattern.compile("nightly-[0-9a-f]{12}");
    private final LauncherPaths paths;
    private final Downloader downloader;
    private final Settings settings;
    private final GameLauncher vanilla;
    private final HorizonNativeMods nativeMods;

    public HorizonManager(LauncherPaths paths, Downloader downloader, Settings settings, GameLauncher vanilla) {
        this.paths = paths;
        this.downloader = downloader;
        this.settings = settings;
        this.vanilla = vanilla;
        this.nativeMods = new HorizonNativeMods(root().resolve("native-selection"), root());
    }

    public Path root() { return paths.root().resolve("horizon"); }
    public Path instanceDir() { return paths.installationGameDir(MINECRAFT_VERSION, "horizon", "default"); }
    public HorizonNativeMods nativeMods(Path gameDir) { return new HorizonNativeMods(gameDir, root()); }
    public HorizonNativeMods nativeMods() { return nativeMods; }

    static String platformArchive(Platform platform, String arch) throws IOException {
        if (platform == Platform.LINUX && "x64".equals(arch)) return "horizon-linux-x86_64.tar.gz";
        if (platform == Platform.WINDOWS && "x64".equals(arch)) return "horizon-windows-x64.zip";
        if (platform == Platform.OSX && "arm64".equals(arch)) return "horizon-macos-arm64.zip";
        if (platform == Platform.OSX && "x64".equals(arch)) return "horizon-macos-x64.zip";
        throw new IOException(Strings.get("horizon.platformUnsupported", platform, arch));
    }

    public synchronized Path ensureInstalled(ProgressListener listener) throws IOException {
        String archiveName = platformArchive(Platform.current(), Platform.arch());
        SecureFiles.rejectSymlinkParents(root().resolve("builds"));
        Files.createDirectories(root().resolve("builds"));
        Path installed = currentBuild();
        if (settings.offlineMode) {
            if (installed == null) throw new IOException(Strings.get("horizon.notInstalledOffline"));
            return installed;
        }
        listener.onMessage(Strings.get("horizon.checking"));
        String sums;
        String signatures;
        try {
            SafeNetwork.ensureAllowedDownloadHost(RELEASE + "SHA256SUMS");
            sums = downloader.getString(RELEASE + "SHA256SUMS");
            signatures = downloader.getString(RELEASE + "SHA256SUMS.sig");
        } catch (IOException e) {
            if (installed == null) throw e;
            listener.onMessage(Strings.get("horizon.cachedBuild", installed.getFileName()));
            return installed;
        }
        if (!HorizonSignature.verify("nightly", sums.getBytes(StandardCharsets.UTF_8), signatures)) {
            throw new IOException(Strings.get("horizon.badSignature"));
        }
        String hash = HorizonSignature.parseSums(sums).get(archiveName);
        if (hash == null) throw new IOException(Strings.get("horizon.missingArchive", archiveName));
        String id = "nightly-" + hash.substring(0, 12);
        Path destination = root().resolve("builds").resolve(id);
        SecureFiles.rejectSymlinkParents(destination);
        if (!validBuild(destination) || !hash.equals(readText(destination.resolve(".archive-sha256")))) {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(Strings.get("horizon.damagedBuild", destination));
            }
            Path staging = Files.createTempDirectory(root().resolve("builds"), ".install-");
            try {
                Path archive = staging.resolve(archiveName);
                listener.onMessage(Strings.get("horizon.downloading", id));
                downloader.downloadVerified(RELEASE + archiveName, archive, "SHA-256", hash, 512L * 1024 * 1024);
                Path unpacked = staging.resolve("unpacked");
                listener.onMessage(Strings.get("horizon.extracting"));
                if (archiveName.endsWith(".zip")) ArchiveExtractor.unzip(archive, unpacked);
                else ArchiveExtractor.untarGz(archive, unpacked);
                Path payload;
                try (var children = Files.list(unpacked)) {
                    payload = children.filter(HorizonManager::validBuild).findFirst()
                            .orElseThrow(() -> new IOException(Strings.get("horizon.invalidArchive")));
                }
                for (String binary : List.of("horizon-client", "horizon-prepare", "horizon-ctl", "horizon-server")) {
                    ArchiveExtractor.makeExecutable(payload.resolve(executable(binary)));
                }
                readPins(payload);
                Files.writeString(payload.resolve(".archive-sha256"), hash, StandardCharsets.UTF_8);
                Files.move(payload, destination);
            } finally {
                deleteStaging(staging);
            }
        }
        Path marker = root().resolve("current");
        Path temp = SecureFiles.createSiblingTemp(marker, ".tmp");
        try {
            Files.writeString(temp, id, StandardCharsets.UTF_8);
            SecureFiles.atomicReplace(temp, marker);
        } finally {
            Files.deleteIfExists(temp);
        }
        return destination;
    }

    private Path currentBuild() throws IOException {
        String id = readText(root().resolve("current"));
        if (id == null || !BUILD_ID.matcher(id).matches()) return null;
        Path build = root().resolve("builds").resolve(id);
        SecureFiles.rejectSymlinkParents(build);
        return validBuild(build) ? build : null;
    }

    private static boolean validBuild(Path build) {
        return Files.isDirectory(build, LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(build.resolve("research-lock.json"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(build.resolve(executable("horizon-client")), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(build.resolve(executable("horizon-prepare")), LinkOption.NOFOLLOW_LINKS);
    }

    private static String executable(String name) { return name + (Platform.isWindows() ? ".exe" : ""); }
    private static String readText(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
        return Files.readString(file, StandardCharsets.UTF_8).trim();
    }

    static Pins readPins(Path build) throws IOException {
        try {
            JsonObject mc = Json.object(Json.readObject(build.resolve("research-lock.json")), "minecraft");
            String version = Json.string(mc, "version", "");
            String client = Json.string(mc, "client_sha1", "");
            String server = Json.string(mc, "server_sha1", "");
            int javaMajor = Json.intValue(mc, "java_major", 0);
            String source = Files.readString(build.resolve("crates/horizon-prepare/src/lib.rs"));
            var match = Pattern.compile("pub const OUTPUT_VERSION: u32 = (\\d+);").matcher(source);
            if (!MINECRAFT_VERSION.equals(version) || !client.matches("[0-9a-f]{40}")
                    || !server.matches("[0-9a-f]{40}") || javaMajor != 25 || !match.find()) {
                throw new IOException(Strings.get("horizon.incompatibleBuild"));
            }
            return new Pins(version, client, server, javaMajor, Integer.parseInt(match.group(1)));
        } catch (RuntimeException e) {
            throw new IOException(Strings.get("horizon.incompatibleBuild"), e);
        }
    }

    record Pins(String version, String clientSha1, String serverSha1, int javaMajor, int outputVersion) {
        String key() { return version + ":" + clientSha1 + ":" + serverSha1 + ":" + javaMajor + ":" + outputVersion; }
    }

    static Path resources(Path build) { return build.resolve(".local/horizon-client/" + MINECRAFT_VERSION); }

    static boolean prepared(Path build, Pins pins) throws IOException {
        return pins.key().equals(readText(build.resolve(PREPARED_MARKER)))
                && Files.isRegularFile(resources(build).resolve("horizon-resources.json"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(resources(build).resolve("assets/minecraft/sounds.json"), LinkOption.NOFOLLOW_LINKS);
    }

    private Path ensurePrepared(Path build, VersionManifest manifest, ProgressListener listener) throws IOException {
        Pins pins = readPins(build);
        SecureFiles.rejectSymlinkParents(resources(build));
        if (prepared(build, pins)) return resources(build);
        Files.deleteIfExists(build.resolve(PREPARED_MARKER));
        try (var older = Files.list(root().resolve("builds"))) {
            for (Path candidate : older.filter(p -> !p.equals(build) && !Files.isSymbolicLink(p)).toList()) {
                if (validBuild(candidate) && prepared(candidate, pins) && readPins(candidate).equals(pins)) {
                    listener.onMessage(Strings.get("horizon.reusing"));
                    copyTree(resources(candidate), resources(build));
                    Files.writeString(build.resolve(PREPARED_MARKER), pins.key());
                    return resources(build);
                }
            }
        }
        if (manifest == null && !Files.isRegularFile(paths.versionJson(MINECRAFT_VERSION))) {
            manifest = VersionManifest.fetchCached(downloader, paths.versionManifestCacheFile(),
                    java.time.Duration.ofHours(6), settings.offlineMode);
        }
        GameLauncher.VanillaInstall installation = vanilla.ensureVanillaInstalled(MINECRAFT_VERSION, manifest, listener);
        if (installation.version().clientDownload == null
                || !pins.clientSha1.equalsIgnoreCase(installation.version().clientDownload.sha1)) {
            throw new IOException(Strings.get("horizon.minecraftMismatch"));
        }
        JsonObject metadata = vanilla.resolver().readLocal(MINECRAFT_VERSION).raw;
        JsonObject server = Json.object(Json.object(metadata, "downloads"), "server");
        String serverSha1 = Json.string(server, "sha1", "");
        String serverUrl = Json.string(server, "url", "");
        if (!pins.serverSha1.equals(serverSha1) || !serverUrl.startsWith("https://")) {
            throw new IOException(Strings.get("horizon.minecraftMismatch"));
        }
        Path jar = root().resolve("server/" + MINECRAFT_VERSION + "-server.jar");
        SecureFiles.rejectSymlinkParents(jar);
        if (settings.offlineMode) {
            if (!HashUtil.verify(jar, pins.serverSha1)) throw new IOException(Strings.get("horizon.serverMissingOffline"));
        } else {
            listener.onMessage(Strings.get("horizon.serverDownload"));
            downloader.download(serverUrl, jar, pins.serverSha1);
        }
        Map<String, String> environment = new HashMap<>(vanilla.userEnvironment());
        JavaSelection selectedJava = installation.java();
        Path javaHome = selectedJava.executable.toRealPath().getParent().getParent();
        environment.put("JAVA_HOME", javaHome.toString());
        environment.put("PATH", javaHome.resolve("bin") + java.io.File.pathSeparator
                + System.getenv().getOrDefault("PATH", ""));
        ensureKnownManifest(build, paths.versionJson(MINECRAFT_VERSION), settings.offlineMode,
                (url, target, sha1) -> downloader.downloadVerified(url, target, "SHA-1", sha1, 4L * 1024 * 1024), listener);
        listener.onMessage(Strings.get("horizon.preparing"));
        Files.deleteIfExists(build.resolve(PREPARED_MARKER));
        runPrepare(build, List.of("--prepare", "--server-jar", jar.toString(), "--minecraft-dir", paths.gameDir().toString(),
                "--root", build.toString()), environment, listener);
        runPrepare(build, List.of("sounds", "--minecraft-dir", paths.gameDir().toString(), "--root", build.toString()),
                environment, listener);
        if (!Files.isRegularFile(resources(build).resolve("horizon-resources.json"))
                || !Files.isRegularFile(resources(build).resolve("assets/minecraft/sounds.json"))) {
            throw new IOException(Strings.get("horizon.resourcesIncomplete"));
        }
        Files.writeString(build.resolve(PREPARED_MARKER), pins.key());
        return resources(build);
    }

    @FunctionalInterface
    interface ManifestDownload {
        void download(String url, Path target, String sha1) throws IOException;
    }
    static void ensureKnownManifest(Path build, Path vanillaManifest, boolean offline,
                                    ManifestDownload download, ProgressListener listener) throws IOException {
        final String pinned;
        Set<String> accepted = new HashSet<>();
        try {
            JsonObject mc = Json.object(Json.readObject(build.resolve("research-lock.json")), "minecraft");
            pinned = Json.string(mc, "manifest_sha1", "");
            if (!pinned.matches("[0-9a-f]{40}")) throw new IOException(Strings.get("horizon.incompatibleBuild"));
            accepted.add(pinned);
            if (mc.has("accepted_manifest_sha1")) {
                for (JsonElement item : mc.getAsJsonArray("accepted_manifest_sha1")) {
                    String hash = item.getAsString();
                    if (!hash.matches("[0-9a-f]{40}")) throw new IOException(Strings.get("horizon.incompatibleBuild"));
                    accepted.add(hash);
                }
            }
        } catch (RuntimeException e) {
            throw new IOException(Strings.get("horizon.incompatibleBuild"), e);
        }
        SecureFiles.rejectSymlinkParents(vanillaManifest);
        if (Files.isRegularFile(vanillaManifest, LinkOption.NOFOLLOW_LINKS)
                && accepted.contains(HashUtil.sha1(vanillaManifest))) return;
        Path destination = build.resolve(".local/minecraft/" + MINECRAFT_VERSION + "/manifest.json");
        SecureFiles.rejectSymlinkParents(destination);
        if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                && accepted.contains(HashUtil.sha1(destination))) return;
        if (offline) throw new IOException(Strings.get("horizon.manifestMissingOffline"));
        listener.onMessage(Strings.get("horizon.manifestDownload"));
        String url = "https://piston-meta.mojang.com/v1/packages/" + pinned + "/" + MINECRAFT_VERSION + ".json";
        download.download(url, destination, pinned);

        if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || !HashUtil.verify(destination, pinned)) {
            throw new IOException(Strings.get("horizon.manifestMismatch"));
        }
    }

    private static void runPrepare(Path build, List<String> arguments, Map<String, String> env,
                                   ProgressListener listener) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(build.resolve(executable("horizon-prepare")).toString());
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).directory(build.toFile()).redirectErrorStream(true);
        builder.environment().putAll(env);
        Process process = builder.start();
        Deque<String> tail = new ArrayDeque<>();
        try {
            process.getOutputStream().close();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    listener.onMessage("[Horizon] " + line);
                    if (tail.size() == 12) tail.removeFirst();
                    tail.addLast(line);
                }
            }
            int exit = process.waitFor();
            if (exit != 0) throw new IOException(Strings.get("horizon.prepareFailed", exit) + "\n" + String.join("\n", tail));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(Strings.get("horizon.interrupted"), e);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }


    public Path downloadNativeBuilder(Path destination, ProgressListener listener) throws IOException {
        final String tag = "launcher@0.2.44";
        final String url = "https://github.com/horizon-rust/horizon/releases/download/launcher%400.2.44/";
        String arch = Platform.arch(); Platform platform = Platform.current(); String archive;
        if (platform == Platform.LINUX && "x64".equals(arch)) archive = "horizon-launcher-linux-x86_64.tar.gz";
        else if (platform == Platform.WINDOWS && "x64".equals(arch)) archive = "horizon-launcher-windows-x64.zip";
        else if (platform == Platform.OSX && "arm64".equals(arch)) archive = "horizon-launcher-macos-arm64.zip";
        else if (platform == Platform.OSX && "x64".equals(arch)) archive = "horizon-launcher-macos-x64.zip";
        else throw new IOException(Strings.get("horizon.platformUnsupported", platform, arch));
        SecureFiles.rejectSymlinkParents(destination); Files.createDirectories(destination);
        listener.onMessage(Strings.get("hnmod.builderDownload"));
        String sums = downloader.getString(url + "SHA256SUMS"), signatures = downloader.getString(url + "SHA256SUMS.sig");
        if (!HorizonSignature.verify(tag, sums.getBytes(StandardCharsets.UTF_8), signatures)) throw new IOException(Strings.get("horizon.badSignature"));
        String hash = HorizonSignature.parseSums(sums).get(archive);
        if (hash == null) throw new IOException(Strings.get("horizon.missingArchive", archive));
        Path file = destination.resolve(archive);
        downloader.downloadVerified(url + archive, file, "SHA-256", hash, 512L * 1024 * 1024);
        Path unpacked = destination.resolve("unpacked");
        if (archive.endsWith(".zip")) ArchiveExtractor.unzip(file, unpacked); else ArchiveExtractor.untarGz(file, unpacked);
        String expected = executable("horizon-launcher");
        Path tool;
        try (var files = Files.walk(unpacked)) {
            var matches = files.filter(p -> p.getFileName().toString().equals(expected)
                    && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList();
            if (matches.size() != 1) throw new IOException(Strings.get("hnmod.launcherInvalid"));
            tool = matches.get(0);
        }
        SecureFiles.rejectSymlinkParents(tool); ArchiveExtractor.makeExecutable(tool);
        return tool;
    }

    public synchronized Path prepareNativeBase(VersionManifest manifest, ProgressListener listener) throws IOException {
        Path base = ensureInstalled(listener);
        ensurePrepared(base, manifest, listener);
        return base;
    }

    public synchronized GameProcess launch(Account account, VersionManifest manifest, ProgressListener listener,
                                           Consumer<String> onLine, IntConsumer onExit, String server) throws IOException {
        if (account == null || account.username == null || account.username.isBlank()) {
            throw new IOException(Strings.get("play.needAccount"));
        }
        boolean microsoft = account.type == Account.Type.MICROSOFT;
        if (microsoft) {
            listener.onMessage(Strings.get("microsoft.checkingOwnership"));
            vanilla.requireMicrosoftOwnership(account);
        }
        by.agro.launcher.mods.ModBuildManager manager = new by.agro.launcher.mods.ModBuildManager(paths);
        var active = manager.list().stream().filter(b -> b.id.equals(settings.selectedModBuildId)
                && b.loaderType() == by.agro.launcher.loaders.LoaderType.HORIZON).findFirst().orElse(null);
        if (active == null && settings.selectedModBuildId != null && !settings.selectedModBuildId.isBlank())
            throw new IOException(Strings.get("hnmod.instanceMissing"));
        Path gameDir = active == null ? instanceDir() : manager.gameDir(active);
        Path build = active == null ? null : nativeMods(gameDir).builtClient();
        if (active != null && build == null) throw new IOException(Strings.get("hnmod.needsBuild"));
        Path prepared;
        if (build == null) {
            build = ensureInstalled(listener);
            prepared = ensurePrepared(build, manifest, listener);
        } else {
            prepared = resources(build);
            listener.onMessage(Strings.get("hnmod.usingBuild"));
        }
        SecureFiles.rejectSymlinkParents(gameDir);
        for (String folder : List.of("saves", "resourcepacks", "screenshots", "horizon")) {
            Files.createDirectories(gameDir.resolve(folder));
        }
        List<String> command;
        byte[] sessionBytes = null;
        if (microsoft) {
            command = authenticatedLaunchCommand(build, prepared, gameDir, settings, server);
            HorizonSession.requireOptions(HorizonSession.supportedOptions(build.resolve(executable("horizon-client")), build), command.subList(1, command.size()));
            sessionBytes = HorizonSession.session(account);
            listener.onMessage(Strings.get("horizon.onlineNotice"));
        } else {
            listener.onMessage(Strings.get("horizon.offlineNotice"));
            command = launchCommand(build, prepared, gameDir, account.username, settings, server);
        }
        Map<String, String> environment = new HashMap<>(vanilla.userEnvironment());
        if (Platform.isLinux() && !System.getenv().containsKey("PIPEWIRE_PROPS")) {
            environment.putIfAbsent("PIPEWIRE_PROPS", "{ application.name = \"Horizon\" media.role = \"Game\" }");
        }
        LaunchSession session = new LaunchSession(paths.logsDir(), MINECRAFT_VERSION, "Horizon (Rust)",
                null, "Native Rust client", 0, command, gameDir);
        try {
            long startedNanos = System.nanoTime();
            GameProcess process = GameProcess.start(command, gameDir, onLine, code -> {
                vanilla.recordPlayTime(gameDir, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - startedNanos));
                session.close();
                if (onExit != null) onExit.accept(code);
            }, session, environment);
            try (var input = process.raw().getOutputStream()) {
                if (sessionBytes != null) { input.write(sessionBytes); input.flush(); }
            } catch (IOException e) {
                process.kill();
                throw new IOException(Strings.get("horizon.sessionUnsupported"), e);
            } finally {
                if (sessionBytes != null) java.util.Arrays.fill(sessionBytes, (byte) 0);
            }
            return process;
        } catch (IOException e) {
            session.close();
            throw e;
        }
    }

    static List<String> launchCommand(Path build, Path resources, Path gameDir, String name, Settings settings, String server) {
        List<String> command = new ArrayList<>(List.of(build.resolve(executable("horizon-client")).toString(),
                "--resources", resources.toString(), "--game-dir", gameDir.toString(),
                "--home", gameDir.resolve("horizon").toString(), "--name", name));
        if (settings.fullscreen) command.add("--fullscreen");
        else command.addAll(List.of("--width", Integer.toString(Math.max(320, settings.windowWidth)),
                "--height", Integer.toString(Math.max(240, settings.windowHeight))));
        if (server != null && !server.isBlank()) command.addAll(List.of("--server", server.trim()));
        return command;
    }

    static List<String> authenticatedLaunchCommand(Path build, Path resources, Path gameDir, Settings settings, String server) {
        List<String> command = new ArrayList<>(List.of(build.resolve(executable("horizon-client")).toString(),
                "--resources", resources.toString(), "--game-dir", gameDir.toString(),
                "--home", gameDir.resolve("horizon").toString(), "--session-stdin"));
        if (settings.fullscreen) command.add("--fullscreen");
        else command.addAll(List.of("--width", Integer.toString(Math.max(320, settings.windowWidth)),
                "--height", Integer.toString(Math.max(240, settings.windowHeight))));
        if (server != null && !server.isBlank()) command.addAll(List.of("--quickPlayMultiplayer", server.trim()));
        return command;
    }

    static void copyTree(Path source, Path destination) throws IOException {
        SecureFiles.rejectSymlinkParents(source);
        SecureFiles.rejectSymlinkParents(destination);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path target = destination.resolve(source.relativize(dir));
                SecureFiles.rejectSymlinkParents(target);
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("Unsafe resource file: " + file);
                Path target = destination.resolve(source.relativize(file));
                SecureFiles.rejectSymlinkParents(target);
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteStaging(Path staging) throws IOException {
        if (!staging.getParent().equals(root().resolve("builds")) || !staging.getFileName().toString().startsWith(".install-")) {
            throw new IOException("Unsafe staging directory: " + staging);
        }
        SecureFiles.rejectSymlinkParents(staging.getParent());
        Files.walkFileTree(staging, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
