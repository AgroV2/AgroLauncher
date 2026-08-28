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
        if (settings.javaPath != null && !settings.javaPath.isBlank()) {
            Path custom = Path.of(settings.javaPath);
            if (Files.isExecutable(custom)) {
                return custom.toAbsolutePath().toString();
            }
            listener.onMessage("Указанный путь к Java недоступен, ищем альтернативу: " + settings.javaPath);
        }

        int normalized = JavaRequirement.normalize(requiredMajorVersion);

        if (settings.useManagedJava) {
            Path managed = managedJavaPath(normalized);
            if (managed != null) {
                return managed.toString();
            }
            listener.onMessage("Требуется Java " + normalized + " — скачиваем среду исполнения");
            return downloadJre(normalized, listener).toString();
        }

        JavaInstallation system = detectSystemJava();
        if (system != null && system.majorVersion >= normalized) {
            return system.executable.toString();
        }

        Path managed = managedJavaPath(normalized);
        if (managed != null) {
            return managed.toString();
        }
        listener.onMessage("Системная Java не подходит (нужна " + normalized + ") — скачиваем");
        return downloadJre(normalized, listener).toString();
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
            throw new IOException("Adoptium не вернул сборок JRE " + majorVersion
                    + " для " + Platform.adoptiumOs() + "/" + Platform.adoptiumArch());
        }

        JsonObject binary = Json.object(assets.get(0).getAsJsonObject(), "binary");
        JsonObject pkg = binary != null ? Json.object(binary, "package") : null;
        if (pkg == null) {
            throw new IOException("Некорректный ответ Adoptium для JRE " + majorVersion);
        }
        String link = Json.string(pkg, "link", null);
        String checksum = Json.string(pkg, "checksum", null);
        String name = Json.string(pkg, "name", "jre-" + majorVersion);
        if (link == null) {
            throw new IOException("В ответе Adoptium нет ссылки на архив JRE " + majorVersion);
        }

        Path archive = paths.cacheDir().resolve("runtimes").resolve(name);
        listener.onProgress("Java " + majorVersion, 1, 3, "загрузка " + name);
        Files.createDirectories(archive.getParent());
        downloader.download(link, archive);

        if (checksum != null && !by.agro.launcher.core.HashUtil.verifySha256(archive, checksum)) {
            Files.deleteIfExists(archive);
            throw new IOException("Контрольная сумма архива JRE " + majorVersion + " не совпала");
        }

        Path target = paths.runtimeDir(majorVersion);
        listener.onProgress("Java " + majorVersion, 2, 3, "распаковка");
        if (Files.exists(target)) {
            deleteRecursively(target);
        }
        Files.createDirectories(target);
        if (name.endsWith(".zip")) {
            ArchiveExtractor.unzip(archive, target);
        } else {
            ArchiveExtractor.untarGz(archive, target);
        }

        Path executable = findJavaExecutable(target);
        if (executable == null) {
            throw new IOException("После распаковки не найден исполняемый файл java в " + target);
        }
        ArchiveExtractor.makeExecutable(executable);
        Path binDir = executable.getParent();
        if (binDir != null && Files.isDirectory(binDir)) {
            try (var stream = Files.list(binDir)) {
                stream.forEach(ArchiveExtractor::makeExecutable);
            }
        }

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
