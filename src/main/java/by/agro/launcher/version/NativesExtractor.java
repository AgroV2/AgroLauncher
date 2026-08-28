package by.agro.launcher.version;

import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.i18n.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Распаковка нативных библиотек (.dll / .so / .dylib) в каталог natives версии.
 *
 * Обрабатывает оба формата:
 *  - legacy: библиотека с блоком natives и classifier natives-windows-64
 *  - современный (1.19+): отдельная библиотека с classifier natives-windows в maven-координатах
 */
public final class NativesExtractor {

    private final LauncherPaths paths;

    public NativesExtractor(LauncherPaths paths) {
        this.paths = paths;
    }

    /**
     * Распаковывает все нативные библиотеки версии.
     *
     * @return каталог natives, который передаётся в -Djava.library.path
     */
    public Path extract(ResolvedVersion version, Rule.FeatureSet featureSet, ProgressListener listener)
            throws IOException {
        Path nativesDir = paths.nativesDir(version.id);
        Files.createDirectories(nativesDir);

        List<Library> natives = version.nativeLibraries(featureSet);
        if (natives.isEmpty()) {
            return nativesDir;
        }

        int index = 0;
        for (Library library : natives) {
            index++;
            Path jar = paths.librariesDir().resolve(library.relativePath());
            if (!Files.exists(jar)) {
                listener.onMessage("Нативная библиотека не найдена, пропуск: " + jar.getFileName());
                continue;
            }
            listener.onProgress(Strings.get("progress.natives"), index, natives.size(),
                    jar.getFileName().toString());
            extractJar(jar, nativesDir, library.extractExclude);
        }
        return nativesDir;
    }

    private void extractJar(Path jar, Path targetDir, List<String> excludes) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (isExcluded(name, excludes)) {
                    continue;
                }
                // Берём только бинарные библиотеки, служебные файлы не нужны
                if (!isNativeBinary(name)) {
                    continue;
                }
                // Плоская распаковка: имя файла без каталогов
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (fileName.isEmpty()) {
                    continue;
                }
                Path target = targetDir.resolve(fileName);
                // Защита от path traversal
                if (!target.normalize().startsWith(targetDir.normalize())) {
                    continue;
                }
                if (Files.exists(target) && Files.size(target) == entry.getSize()) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                makeExecutableIfNeeded(target);
            }
        }
    }

    private boolean isNativeBinary(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".dll")
                || lower.endsWith(".so")
                || lower.endsWith(".dylib")
                || lower.endsWith(".jnilib")
                || lower.contains(".so.");
    }

    private boolean isExcluded(String name, List<String> excludes) {
        if (name.startsWith("META-INF/")) {
            return true;
        }
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        for (String exclude : excludes) {
            if (name.startsWith(exclude)) {
                return true;
            }
        }
        return false;
    }

    private void makeExecutableIfNeeded(Path file) {
        if (by.agro.launcher.core.Platform.isWindows()) {
            return;
        }
        try {
            var perms = Files.getPosixFilePermissions(file);
            perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (IOException | UnsupportedOperationException ignored) {
            // не критично
        }
    }
}
