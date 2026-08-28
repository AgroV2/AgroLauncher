package by.agro.launcher.mods;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.loaders.LoaderType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ModBuildManager {

    private static final String METADATA = "build.json";
    private final LauncherPaths paths;

    public ModBuildManager(LauncherPaths paths) {
        this.paths = paths;
    }

    public static final class ModBuild {
        public String id;
        public String name;
        public String minecraftVersion;
        public String loader;
        public String loaderVersion;
        public String createdAt;
        public int files;

        public LoaderType loaderType() {
            return LoaderType.fromId(loader);
        }

        @Override
        public String toString() {
            return name + " · " + minecraftVersion + " · " + loaderType().displayName()
                    + (loaderVersion == null || loaderVersion.isBlank() ? "" : " " + loaderVersion)
                    + " · " + Strings.get("builds.modCount", files);
        }
    }

    public Path buildsDir() {
        return paths.root().resolve("mod-builds");
    }

    public ModBuild create(String name, String minecraftVersion, LoaderType loader,
                           String loaderVersion) throws IOException {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IOException(Strings.get("builds.selectVersion"));
        }
        if (loader == null || !loader.supportsMods()) {
            throw new IOException(Strings.get("builds.loaderUnsupported"));
        }
        ModBuild build = new ModBuild();
        build.id = UUID.randomUUID().toString();
        build.name = name == null || name.isBlank()
                ? Strings.get("builds.defaultName", minecraftVersion) : name.trim();
        build.minecraftVersion = minecraftVersion;
        build.loader = loader.id();
        build.loaderVersion = loaderVersion == null ? "" : loaderVersion;
        build.createdAt = Instant.now().toString();

        Path targetMods = modsDir(build);
        Files.createDirectories(targetMods);
        if (Files.isDirectory(paths.modsDir())) {
            try (var stream = Files.list(paths.modsDir())) {
                for (Path source : stream.filter(Files::isRegularFile).toList()) {
                    String fileName = source.getFileName().toString().toLowerCase();
                    if (fileName.endsWith(".jar") || fileName.endsWith(".jar.disabled")) {
                        Files.copy(source, targetMods.resolve(source.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        build.files++;
                    }
                }
            }
        }
        Json.write(buildDir(build).resolve(METADATA), build);
        return build;
    }

    public List<ModBuild> list() {
        List<ModBuild> result = new ArrayList<>();
        if (!Files.isDirectory(buildsDir())) {
            return result;
        }
        try (var stream = Files.list(buildsDir())) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                Path metadata = dir.resolve(METADATA);
                if (!Files.exists(metadata)) continue;
                try {
                    ModBuild build = Json.read(metadata, ModBuild.class);
                    if (build != null && build.id != null) result.add(build);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
            return result;
        }
        result.sort(Comparator.comparing((ModBuild b) -> b.createdAt == null ? "" : b.createdAt).reversed());
        return result;
    }

    public void delete(ModBuild build) throws IOException {
        Path dir = buildDir(build);
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public Path gameDir(ModBuild build) {
        return buildDir(build).resolve("game");
    }

    public Path modsDir(ModBuild build) {
        return gameDir(build).resolve("mods");
    }

    private Path buildDir(ModBuild build) {
        return buildsDir().resolve(build.id);
    }
}
