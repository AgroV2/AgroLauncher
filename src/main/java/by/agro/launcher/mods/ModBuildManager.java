package by.agro.launcher.mods;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.repair.RepairService;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
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
        List<String> copied = new ArrayList<>();
        if (Files.isDirectory(paths.modsDir(), LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(paths.modsDir())) {
            try (var stream = Files.list(paths.modsDir())) {
                for (Path source : stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(path)).toList()) {
                    String fileName = source.getFileName().toString().toLowerCase();
                    if (fileName.endsWith(".jar") || fileName.endsWith(".jar.disabled")) {
                        Files.copy(source, targetMods.resolve(source.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES,
                                LinkOption.NOFOLLOW_LINKS);
                        copied.add(source.getFileName().toString());
                        build.files++;
                    }
                }
            }
        }
        copyManagedMetadataSnapshot(gameDir(build), copied);
        Json.write(buildDir(build).resolve(METADATA), build);
        return build;
    }

    private void copyManagedMetadataSnapshot(Path targetGameDir, List<String> copied) throws IOException {
        Path source = paths.gameDir().resolve(".agrolauncher-managed-mods.json");
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(source)) return;
        RepairService.ManagedMods original = Json.read(source, RepairService.ManagedMods.class);
        if (original == null || original.mods == null) return;
        RepairService.ManagedMods filtered = new RepairService.ManagedMods();
        for (RepairService.ManagedMod mod : original.mods) {
            if (mod == null || mod.fileName == null) continue;
            String enabled = mod.fileName;
            String disabled = enabled + ".disabled";
            String actual = copied.stream().filter(name -> name.equals(enabled) || name.equals(disabled))
                    .findFirst().orElse(null);
            if (actual == null) continue;
            RepairService.ManagedMod snapshot = new RepairService.ManagedMod();
            snapshot.fileName = mod.fileName;
            snapshot.url = mod.url;
            snapshot.sha1 = mod.sha1;
            snapshot.size = mod.size;
            snapshot.disabled = actual.endsWith(".disabled");
            filtered.mods.add(snapshot);
        }
        if (!filtered.mods.isEmpty()) {
            Json.write(targetGameDir.resolve(".agrolauncher-managed-mods.json"), filtered);
        }
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
        Path dir = validatedBuildDir(build);
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path validatedBuildDir(ModBuild build) throws IOException {
        if (build == null || build.id == null || build.id.isBlank()) {
            throw new IOException("Build ID is not specified");
        }
        Path id;
        try {
            id = Paths.get(build.id);
        } catch (RuntimeException e) {
            throw new IOException("Invalid build ID", e);
        }
        if (id.isAbsolute() || id.getNameCount() != 1
                || ".".equals(build.id) || "..".equals(build.id)
                || build.id.indexOf('/') >= 0 || build.id.indexOf('\\') >= 0) {
            throw new IOException("Unsafe build ID");
        }
        Path root = buildsDir().toAbsolutePath().normalize();
        Path dir = root.resolve(id).normalize();
        if (!root.equals(dir.getParent())) {
            throw new IOException("Build directory must be directly inside mod-builds");
        }
        return dir;
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
