package by.agro.launcher.mods;

import by.agro.launcher.core.LauncherPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ModManager {

    public static final String DISABLED_SUFFIX = ".disabled";

    private final LauncherPaths paths;

    public ModManager(LauncherPaths paths) {
        this.paths = paths;
    }

    public static final class ModFile {
        public final Path path;
        public final String fileName;
        public final boolean enabled;
        public final long size;

        ModFile(Path path, boolean enabled, long size) {
            this.path = path;
            this.fileName = path.getFileName().toString();
            this.enabled = enabled;
            this.size = size;
        }

        public String displayName() {
            String name = fileName;
            if (name.endsWith(DISABLED_SUFFIX)) {
                name = name.substring(0, name.length() - DISABLED_SUFFIX.length());
            }
            if (name.endsWith(".jar")) {
                name = name.substring(0, name.length() - 4);
            }
            return name;
        }

        public String formattedSize() {
            if (size >= 1024 * 1024) {
                return String.format("%.1f %s", size / (1024.0 * 1024.0),
                        by.agro.launcher.i18n.Strings.get("unit.mb"));
            }
            if (size >= 1024) {
                return (size / 1024) + " " + by.agro.launcher.i18n.Strings.get("unit.kb");
            }
            return size + " " + by.agro.launcher.i18n.Strings.get("unit.b");
        }

        @Override
        public String toString() {
            return displayName();
        }
    }

    public Path modsDir() {
        return paths.modsDir();
    }

    public List<ModFile> list() {
        List<ModFile> result = new ArrayList<>();
        Path dir = modsDir();
        if (!Files.isDirectory(dir)) {
            return result;
        }
        try (var stream = Files.list(dir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString().toLowerCase();
                boolean isJar = name.endsWith(".jar");
                boolean isDisabled = name.endsWith(".jar" + DISABLED_SUFFIX);
                if (!isJar && !isDisabled) {
                    continue;
                }
                long size;
                try {
                    size = Files.size(path);
                } catch (IOException e) {
                    size = 0;
                }
                result.add(new ModFile(path, isJar, size));
            }
        } catch (IOException e) {
            System.err.println("Не удалось прочитать каталог модов: " + e.getMessage());
        }
        result.sort(Comparator.comparing(ModFile::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public void setEnabled(ModFile mod, boolean enabled) throws IOException {
        if (mod.enabled == enabled) {
            return;
        }
        Path target;
        if (enabled) {
            String name = mod.fileName;
            if (!name.endsWith(DISABLED_SUFFIX)) {
                return;
            }
            target = mod.path.resolveSibling(name.substring(0, name.length() - DISABLED_SUFFIX.length()));
        } else {
            target = mod.path.resolveSibling(mod.fileName + DISABLED_SUFFIX);
        }
        Files.move(mod.path, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void add(Path source) throws IOException {
        Path dir = modsDir();
        Files.createDirectories(dir);
        Path target = dir.resolve(source.getFileName().toString());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void delete(ModFile mod) throws IOException {
        Files.deleteIfExists(mod.path);
    }

    public long enabledCount() {
        return list().stream().filter(m -> m.enabled).count();
    }
}
