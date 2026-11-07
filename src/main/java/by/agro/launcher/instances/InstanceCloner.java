package by.agro.launcher.instances;

import by.agro.launcher.core.DiskSpace;
import by.agro.launcher.core.FileTrees;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.mods.ModBuildManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;





public final class InstanceCloner {

    public enum Part {
        WORLDS("clone.part.worlds", List.of("saves")),
        MODS("clone.part.mods", List.of("mods", ".agrolauncher-managed-mods.json")),
        SETTINGS("clone.part.settings", List.of("options.txt", "optionsof.txt", "optionsshaders.txt",
                "servers.dat", "servers.dat_old", "config", "defaultconfigs", "kubejs")),
        RESOURCES("clone.part.resources", List.of("resourcepacks", "shaderpacks"));

        private final String labelKey;
        private final List<String> entries;

        Part(String labelKey, List<String> entries) {
            this.labelKey = labelKey;
            this.entries = entries;
        }

        public String label() {
            return Strings.get(labelKey);
        }

        public List<String> entries() {
            return entries;
        }
    }

    public record Source(Path gameDir, String minecraftVersion, String loaderId, String loaderVersion) {
    }

    private final ModBuildManager builds;

    public InstanceCloner(LauncherPaths paths) {
        this.builds = new ModBuildManager(paths);
    }

    InstanceCloner(ModBuildManager builds) {
        this.builds = builds;
    }

    public static boolean supported(Source source) {
        return source != null && LoaderType.fromId(source.loaderId()) != LoaderType.HORIZON;
    }


    public static long estimateBytes(Path gameDir, Set<Part> parts) {
        long total = 0;
        for (Part part : parts) {
            for (String entry : part.entries()) total += DiskSpace.directorySize(gameDir.resolve(entry));
        }
        return total;
    }

    public ModBuildManager.ModBuild cloneToBuild(Source source, String name, Set<Part> parts) throws IOException {
        if (!supported(source)) throw new IOException(Strings.get("clone.horizonUnsupported"));
        Path from = source.gameDir().toAbsolutePath().normalize();
        if (!Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(from)) {
            throw new IOException(Strings.get("clone.sourceMissing", from));
        }
        Set<Part> selected = parts == null || parts.isEmpty() ? EnumSet.noneOf(Part.class) : EnumSet.copyOf(parts);
        String loaderVersion = source.loaderVersion();
        if (loaderVersion == null || "default".equals(loaderVersion) || "recommended".equals(loaderVersion)) {
            loaderVersion = "";
        }
        ModBuildManager.ModBuild build = builds.newBuild(name, source.minecraftVersion(), source.loaderId(), loaderVersion);
        Path buildDir = builds.buildDirectory(build);
        Path target = builds.gameDir(build);
        DiskSpace.require(buildDir, estimateBytes(from, selected));
        try {
            Files.createDirectories(target.resolve("mods"));
            for (Part part : selected) {
                for (String entry : part.entries()) {
                    Path sourceEntry = from.resolve(entry);
                    if (Files.isSymbolicLink(sourceEntry)) continue;
                    FileTrees.copy(sourceEntry, target.resolve(entry), relative ->
                            !"session.lock".equals(String.valueOf(relative.getFileName())));
                    if (part == Part.MODS && "mods".equals(entry)) build.files = countMods(target.resolve("mods"));
                }
            }
            builds.writeMetadata(build);
        } catch (IOException | RuntimeException e) {
            FileTrees.deleteQuietly(buildDir);
            throw e;
        }
        return build;
    }

    private static int countMods(Path mods) {
        if (!Files.isDirectory(mods)) return 0;
        try (var stream = Files.list(mods)) {
            return (int) stream.filter(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && (name.endsWith(".jar") || name.endsWith(".jar.disabled"));
            }).count();
        } catch (IOException e) {
            return 0;
        }
    }
}
