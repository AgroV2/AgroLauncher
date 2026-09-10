package by.agro.launcher.jvm;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Platform;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.Settings;
import by.agro.launcher.version.JavaRequirement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public final class JavaManager {

    private static final String ADOPTIUM_ASSETS =
            "https://api.adoptium.net/v3/assets/latest/%d/hotspot?image_type=jre&os=%s&architecture=%s";

    private final LauncherPaths paths;
    private final Downloader downloader;
    private final Settings settings;

    public JavaManager(LauncherPaths paths, Downloader downloader, Settings settings) {
        this.paths = paths;
        this.downloader = downloader;
        this.settings = settings;
    }

    public String resolveJavaForGame(int requiredMajorVersion, ProgressListener listener) throws IOException {
        return selectJava(requiredMajorVersion, listener, settings.offlineMode).executableText();
    }

    public JavaSelection selectJava(int requiredMajorVersion, ProgressListener listener,
                                    boolean localOnly) throws IOException {
        int required = JavaRequirement.normalize(requiredMajorVersion);
        JavaSelection.Mode mode;
        try {
            mode = JavaSelection.Mode.valueOf(settings.javaMode == null
                    ? "AUTO" : settings.javaMode.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = JavaSelection.Mode.AUTO;
        }

        if (mode == JavaSelection.Mode.CUSTOM) {
            if (settings.javaPath == null || settings.javaPath.isBlank()) {
                throw new IOException("CUSTOM Java mode requires an executable path");
            }
            return validate(Path.of(settings.javaPath), required, mode, "configured custom runtime");
        }
        if (mode == JavaSelection.Mode.SYSTEM) {
            JavaInstallation system = detectSystemJava();
            if (system == null) throw new IOException("SYSTEM Java mode could not find a Java executable");
            return validate(system.executable, required, mode, system.source);
        }

        Path managed = managedJavaPath(required);
        if (mode == JavaSelection.Mode.MANAGED) {
            if (managed == null) {
                if (localOnly) throw new IOException("Managed Java " + required + " is not installed for offline launch");
                managed = downloadJre(required, listener);
            }
            return validate(managed, required, mode, "managed runtime");
        }

        JavaInstallation system = detectSystemJava();
        if (system != null && system.majorVersion >= required) {
            return validate(system.executable, required, JavaSelection.Mode.SYSTEM, "AUTO selected " + system.source);
        }
        if (managed != null) {
            return validate(managed, required, JavaSelection.Mode.MANAGED, "AUTO selected managed runtime");
        }
        if (localOnly) throw new IOException("No compatible local Java " + required + "+ runtime is available offline");
        Path downloaded = downloadJre(required, listener);
        return validate(downloaded, required, JavaSelection.Mode.MANAGED, "AUTO installed managed runtime");
    }

    private JavaSelection validate(Path executable, int required, JavaSelection.Mode source,
                                   String reason) throws IOException {
        if (executable == null || !Files.isExecutable(executable)) {
            throw new IOException(source + " Java executable is missing or not executable: " + executable);
        }
        int detected = JavaDetector.queryMajorVersion(executable);
        if (detected <= 0) throw new IOException("Could not determine Java major version: " + executable);
        if (detected < required) {
            throw new IOException(source + " Java " + detected + " is incompatible; Java " + required + "+ is required");
        }
        return new JavaSelection(executable.toAbsolutePath().normalize(), detected, required, source, reason);
    }


    public String resolveJavaForInstaller() throws IOException {
        JavaInstallation system = detectSystemJava();
        if (system != null && system.majorVersion >= 8) {
            return system.executable.toString();
        }
        for (int version : new int[]{JavaRequirement.JAVA_21, JavaRequirement.JAVA_17,
                JavaRequirement.JAVA_25, JavaRequirement.JAVA_8}) {
            Path managed = managedJavaPath(version);
            if (managed != null) {
                return managed.toString();
            }
        }
        return downloadJre(17, ProgressListener.NOOP).toString();
    }

    public Path managedJavaPath(int majorVersion) {
        Path dir = paths.runtimeDir(majorVersion);
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Path executable = findJavaExecutable(dir);
        return (executable != null && Files.isExecutable(executable)) ? executable : null;
    }


    private Path findJavaExecutable(Path root) {
        String exeName = Platform.javaConsoleExecutableName();
        Path direct = root.resolve("bin").resolve(exeName);
        if (Files.exists(direct)) {
            return direct;
        }
        Path macPath = root.resolve("Contents").resolve("Home").resolve("bin").resolve(exeName);
        if (Files.exists(macPath)) {
            return macPath;
        }
        try (var stream = Files.list(root)) {
            for (Path child : stream.filter(Files::isDirectory).toList()) {
                Path nested = child.resolve("bin").resolve(exeName);
                if (Files.exists(nested)) {
                    return nested;
                }
                Path nestedMac = child.resolve("Contents").resolve("Home").resolve("bin").resolve(exeName);
                if (Files.exists(nestedMac)) {
                    return nestedMac;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    public Path downloadJre(int majorVersion, ProgressListener listener) throws IOException {
        String url = String.format(ADOPTIUM_ASSETS, majorVersion,
                Platform.adoptiumOs(), Platform.adoptiumArch());

        listener.onProgress("Java " + majorVersion, 0, 3, "поиск сборки");
        String body = downloader.getString(url);
        JsonArray assets = Json.parse(body).getAsJsonArray();
        if (assets.isEmpty()) {
            throw new IOException("Adoptium returned no JRE " + majorVersion + " builds for "
                    + Platform.adoptiumOs() + "/" + Platform.adoptiumArch());
        }

        JsonObject binary = Json.object(assets.get(0).getAsJsonObject(), "binary");
        JsonObject pkg = binary != null ? Json.object(binary, "package") : null;
        if (pkg == null) {
            throw new IOException("Invalid Adoptium response for JRE " + majorVersion);
        }
        String link = Json.string(pkg, "link", null);
        String checksum = Json.string(pkg, "checksum", null);
        String name = Json.string(pkg, "name", "jre-" + majorVersion);
        if (link == null) {
            throw new IOException("The Adoptium response contains no link to the JRE " + majorVersion + " archive");
        }
        if (checksum == null || checksum.isBlank()) {
            throw new IOException("Adoptium response contains no SHA-256 checksum for JRE " + majorVersion);
        }

        Path archive = paths.cacheDir().resolve("runtimes").resolve(name);
        listener.onProgress("Java " + majorVersion, 1, 3, "загрузка " + name);
        Files.createDirectories(archive.getParent());
        downloader.downloadVerified(link, archive, "SHA-256", checksum, 512L * 1024 * 1024);

        Path target = paths.runtimeDir(majorVersion);
        Path staging = paths.runtimesDir().resolve(".jre-" + majorVersion + "-" + java.util.UUID.randomUUID() + ".tmp");
        Path backup = paths.runtimesDir().resolve(".jre-" + majorVersion + ".previous");
        listener.onProgress("Java " + majorVersion, 2, 3, "распаковка");
        Files.createDirectories(staging);
        try {
            if (name.endsWith(".zip")) ArchiveExtractor.unzip(archive, staging);
            else ArchiveExtractor.untarGz(archive, staging);
            Path stagedExecutable = findJavaExecutable(staging);
            if (stagedExecutable == null) throw new IOException("No Java executable was found after extraction");
            ArchiveExtractor.makeExecutable(stagedExecutable);
            int detected = JavaDetector.queryMajorVersion(stagedExecutable);
            if (detected < majorVersion) {
                throw new IOException("Downloaded Java " + detected + " does not satisfy required Java " + majorVersion);
            }
            deleteRecursively(backup);
            if (Files.exists(target)) Files.move(target, backup, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            try {
                Files.move(staging, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException switchFailure) {
                if (Files.exists(backup) && !Files.exists(target)) {
                    Files.move(backup, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                }
                throw switchFailure;
            }
            deleteRecursively(backup);
        } finally {
            deleteRecursively(staging);
        }
        Path executable = findJavaExecutable(target);
        listener.onProgress("Java " + majorVersion, 3, 3, "готово");
        listener.onMessage("Java " + majorVersion + " установлена: " + executable);
        return executable;
    }
    public JavaInstallation detectSystemJava() {
        List<Path> candidates = new ArrayList<>();

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            candidates.add(Path.of(javaHome, "bin", Platform.javaConsoleExecutableName()));
        }
        String ownHome = System.getProperty("java.home");
        if (ownHome != null && !ownHome.isBlank()) {
            candidates.add(Path.of(ownHome, "bin", Platform.javaConsoleExecutableName()));
        }

        for (Path candidate : candidates) {
            if (Files.isExecutable(candidate)) {
                int major = JavaDetector.queryMajorVersion(candidate);
                if (major > 0) {
                    return new JavaInstallation(candidate, major, "система");
                }
            }
        }

        Path fromPath = JavaDetector.findInPath();
        if (fromPath != null) {
            int major = JavaDetector.queryMajorVersion(fromPath);
            if (major > 0) {
                return new JavaInstallation(fromPath, major, "PATH");
            }
        }
        return null;
    }
    public List<JavaInstallation> listInstallations() {
        List<JavaInstallation> result = new ArrayList<>();
        JavaInstallation system = detectSystemJava();
        if (system != null) {
            result.add(system);
        }
        for (int version : JavaRequirement.supportedVersions()) {
            Path managed = managedJavaPath(version);
            if (managed != null) {
                result.add(new JavaInstallation(managed, version, "встроенная"));
            }
        }
        return result;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }

    public static final class JavaInstallation {
        public final Path executable;
        public final int majorVersion;
        public final String source;

        public JavaInstallation(Path executable, int majorVersion, String source) {
            this.executable = executable;
            this.majorVersion = majorVersion;
            this.source = source;
        }

        @Override
        public String toString() {
            return "Java " + majorVersion + " (" + source + ") — " + executable;
        }
    }
}
