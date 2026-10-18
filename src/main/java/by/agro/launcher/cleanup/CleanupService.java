package by.agro.launcher.cleanup;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.SecureFiles;
import by.agro.launcher.core.Settings;
import by.agro.launcher.mods.ModBuildManager;
import by.agro.launcher.version.Library;
import by.agro.launcher.version.ResolvedVersion;
import by.agro.launcher.version.VersionResolver;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CleanupService {
    public enum Category {
        LIBRARY,
        ASSET_OBJECT,
        ASSET_INDEX,
        ASSET_VIRTUAL,
        VERSION_METADATA,
        VERSION_JAR,
        MANAGED_RUNTIME,
        CACHE
    }

    public record Candidate(Category category, Path path, long bytes) {
    }

    public record CategoryTotal(long items, long bytes) {
    }

    public static final class Plan {
        private final List<Candidate> candidates;
        private final Map<Category, CategoryTotal> totals;
        private final Instant createdAt;

        private Plan(List<Candidate> candidates) {
            this.candidates = List.copyOf(candidates);
            EnumMap<Category, CategoryTotal> calculated = new EnumMap<>(Category.class);
            for (Candidate candidate : candidates) {
                CategoryTotal current = calculated.getOrDefault(candidate.category(), new CategoryTotal(0, 0));
                calculated.put(candidate.category(), new CategoryTotal(current.items() + 1, current.bytes() + candidate.bytes()));
            }
            this.totals = Map.copyOf(calculated);
            this.createdAt = Instant.now();
        }

        public List<Candidate> candidates() {
            return candidates;
        }

        public Map<Category, CategoryTotal> totals() {
            return totals;
        }

        public long totalItems() {
            return candidates.size();
        }

        public long reclaimableBytes() {
            return candidates.stream().mapToLong(Candidate::bytes).sum();
        }

        public Instant createdAt() {
            return createdAt;
        }
    }

    public record Failure(Path path, String message) {
    }

    public static final class Result {
        private final Map<Category, CategoryTotal> reclaimed;
        private final List<Failure> failures;

        private Result(Map<Category, CategoryTotal> reclaimed, List<Failure> failures) {
            this.reclaimed = Map.copyOf(reclaimed);
            this.failures = List.copyOf(failures);
        }

        public Map<Category, CategoryTotal> reclaimed() {
            return reclaimed;
        }

        public List<Failure> failures() {
            return failures;
        }

        public long reclaimedBytes() {
            return reclaimed.values().stream().mapToLong(CategoryTotal::bytes).sum();
        }

        public long deletedItems() {
            return reclaimed.values().stream().mapToLong(CategoryTotal::items).sum();
        }
    }

    public record CachePolicy(Duration minimumAge, long maximumBytes) {
        public CachePolicy {
            if (minimumAge == null || minimumAge.isNegative()) throw new IllegalArgumentException("minimumAge");
            if (maximumBytes < 0) throw new IllegalArgumentException("maximumBytes");
        }
    }

    private final LauncherPaths paths;
    private final VersionResolver resolver;
    private final Settings settings;

    public CleanupService(LauncherPaths paths, VersionResolver resolver, Settings settings) {
        this.paths = java.util.Objects.requireNonNull(paths, "paths");
        this.resolver = java.util.Objects.requireNonNull(resolver, "resolver");
        this.settings = java.util.Objects.requireNonNull(settings, "settings");
    }

    public Plan scanReachability() throws IOException {
        validateManagedRoots();
        Set<String> retainedVersions = retainedVersions();
        Set<Path> libraries = new HashSet<>();
        Set<Path> indexes = new HashSet<>();
        Set<Path> objects = new HashSet<>();
        Set<Path> virtualRoots = new HashSet<>();
        Set<Path> versionFiles = new HashSet<>();
        Set<Integer> javaMajors = new HashSet<>();

        for (String versionId : retainedVersions) {
            ResolvedVersion version = resolver.resolve(versionId, null, ProgressListener.NOOP, true);
            if (version.chain == null || version.chain.isEmpty()) {
                throw new IOException("Cannot resolve retained version chain: " + versionId);
            }
            for (String chainId : version.chain) {
                versionFiles.add(validatedChild(paths.versionsDir(), paths.versionJson(chainId)));
                Path jar = paths.versionJar(chainId);
                if (Files.exists(jar, LinkOption.NOFOLLOW_LINKS)) versionFiles.add(validatedChild(paths.versionsDir(), jar));
            }
            for (Library library : version.libraries) {
                String relative = library.relativePath();
                if (relative == null || relative.isBlank()) throw new IOException("Cannot resolve library for " + versionId);
                libraries.add(validatedChild(paths.librariesDir(), paths.librariesDir().resolve(relative)));
            }
            String indexId = version.assetIndex != null && version.assetIndex.id != null && !version.assetIndex.id.isBlank()
                    ? version.assetIndex.id : version.assetsId;
            if (indexId == null || indexId.isBlank()) {
                throw new IOException("Cannot resolve asset index for retained version: " + versionId);
            }
            Path index = validatedChild(paths.assetIndexesDir(), paths.assetIndexesDir().resolve(indexId + ".json"));
            if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(index)) {
                throw new IOException("Asset index is unavailable for retained version: " + versionId);
            }
            indexes.add(index);
            readAssetObjects(index, objects);
            Path virtual = validatedChild(paths.assetsDir().resolve("virtual"), paths.assetsVirtualDir(indexId));
            if (Files.exists(virtual, LinkOption.NOFOLLOW_LINKS)) virtualRoots.add(virtual);
            if (version.javaMajorVersion > 0) javaMajors.add(version.javaMajorVersion);
        }

        List<Candidate> candidates = new ArrayList<>();
        collectUnreachableFiles(paths.librariesDir(), libraries, Category.LIBRARY, candidates);
        collectUnreachableFiles(paths.assetObjectsDir(), objects, Category.ASSET_OBJECT, candidates);
        collectUnreachableFiles(paths.assetIndexesDir(), indexes, Category.ASSET_INDEX, candidates);
        collectUnreachableChildren(paths.assetsDir().resolve("virtual"), virtualRoots, Category.ASSET_VIRTUAL, candidates);
        collectVersionFiles(versionFiles, candidates);
        collectRuntimes(javaMajors, candidates);
        candidates.sort(Comparator.comparing(candidate -> candidate.path().toString()));
        return new Plan(candidates);
    }

    public Plan scanCache(CachePolicy policy) throws IOException {
        java.util.Objects.requireNonNull(policy, "policy");
        Path root = paths.cacheDir().toAbsolutePath().normalize();
        validateRoot(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return new Plan(List.of());
        long cutoff = System.currentTimeMillis() - policy.minimumAge().toMillis();
        List<Path> files = regularFiles(root);
        files.removeIf(this::isPartialDownload);
        files.sort(Comparator.comparingLong(this::lastModified));
        long total = files.stream().mapToLong(this::safeSize).sum();
        List<Candidate> candidates = new ArrayList<>();
        for (Path file : files) {
            long size = safeSize(file);
            boolean old = lastModified(file) <= cutoff;
            boolean overLimit = total > policy.maximumBytes();
            if (old || overLimit) {
                candidates.add(new Candidate(Category.CACHE, file, size));
                total -= size;
            }
        }
        return new Plan(candidates);
    }

    public Result execute(Plan plan) throws IOException {
        java.util.Objects.requireNonNull(plan, "plan");
        validateManagedRoots();
        EnumMap<Category, CategoryTotal> reclaimed = new EnumMap<>(Category.class);
        List<Failure> failures = new ArrayList<>();
        for (Candidate candidate : plan.candidates()) {
            try {
                validateCandidate(candidate);
                if (!Files.exists(candidate.path(), LinkOption.NOFOLLOW_LINKS)) continue;
                long bytes = treeSize(candidate.path());
                delete(candidate.path());
                CategoryTotal current = reclaimed.getOrDefault(candidate.category(), new CategoryTotal(0, 0));
                reclaimed.put(candidate.category(), new CategoryTotal(current.items() + 1, current.bytes() + bytes));
            } catch (IOException | RuntimeException e) {
                failures.add(new Failure(candidate.path(), e.getMessage()));
            }
        }
        return new Result(reclaimed, failures);
    }

    private Set<String> retainedVersions() throws IOException {
        LinkedHashSet<String> retained = new LinkedHashSet<>(resolver.installedVersions());
        if (settings.selectedVersion != null && !settings.selectedVersion.isBlank()) retained.add(settings.selectedVersion);
        ModBuildManager builds = new ModBuildManager(paths);
        Path buildsRoot = builds.buildsDir().toAbsolutePath().normalize();
        validateRoot(buildsRoot);
        if (Files.isDirectory(buildsRoot, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.list(buildsRoot)) {
                for (Path directory : stream.toList()) {
                    if (Files.isSymbolicLink(directory)) throw new IOException("Symbolic-link mod build is not allowed: " + directory);
                    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;
                    Path metadata = directory.resolve("build.json");
                    if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(metadata)) {
                        throw new IOException("Cannot resolve mod build metadata: " + directory.getFileName());
                    }
                    ModBuildManager.ModBuild build = Json.read(metadata, ModBuildManager.ModBuild.class);
                    if (build == null || build.minecraftVersion == null || build.minecraftVersion.isBlank()) {
                        throw new IOException("Cannot resolve Minecraft version for mod build: " + directory.getFileName());
                    }
                    retained.add(build.minecraftVersion);
                }
            }
        }
        if (retained.isEmpty()) throw new IOException("No retained versions could be resolved");
        return retained;
    }

    private void readAssetObjects(Path index, Set<Path> objects) throws IOException {
        JsonObject root = Json.readObject(index);
        JsonObject entries = Json.object(root, "objects");
        if (entries == null) throw new IOException("Asset index has no objects: " + index);
        for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
            if (!entry.getValue().isJsonObject()) throw new IOException("Invalid asset entry in " + index);
            String hash = by.agro.launcher.core.Json.string(entry.getValue().getAsJsonObject(), "hash", null);
            if (hash == null || !hash.matches("[0-9a-fA-F]{40}")) throw new IOException("Invalid asset hash in " + index);
            Path object = paths.assetObjectsDir().resolve(hash.substring(0, 2).toLowerCase(Locale.ROOT)).resolve(hash.toLowerCase(Locale.ROOT));
            objects.add(validatedChild(paths.assetObjectsDir(), object));
        }
    }

    private void collectUnreachableFiles(Path root, Set<Path> retained, Category category, List<Candidate> candidates) throws IOException {
        for (Path file : regularFiles(root)) {
            if (!retained.contains(file.toAbsolutePath().normalize()) && !isPartialDownload(file)) {
                candidates.add(new Candidate(category, file, safeSize(file)));
            }
        }
    }

    private void collectUnreachableChildren(Path root, Set<Path> retained, Category category, List<Candidate> candidates) throws IOException {
        validateRoot(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var stream = Files.list(root)) {
            for (Path child : stream.toList()) {
                if (Files.isSymbolicLink(child)) throw new IOException("Symbolic link is not allowed: " + child);
                Path normalized = child.toAbsolutePath().normalize();
                if (!retained.contains(normalized) && !isPartialDownload(child)) {
                    candidates.add(new Candidate(category, normalized, treeSize(normalized)));
                }
            }
        }
    }

    private void collectVersionFiles(Set<Path> retained, List<Candidate> candidates) throws IOException {
        Path root = paths.versionsDir();
        for (Path file : regularFiles(root)) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if ((name.endsWith(".json") || name.endsWith(".jar")) && !retained.contains(file.toAbsolutePath().normalize()) && !isPartialDownload(file)) {
                candidates.add(new Candidate(name.endsWith(".json") ? Category.VERSION_METADATA : Category.VERSION_JAR, file, safeSize(file)));
            }
        }
    }

    private void collectRuntimes(Set<Integer> retainedMajors, List<Candidate> candidates) throws IOException {
        Path root = paths.runtimesDir();
        validateRoot(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var stream = Files.list(root)) {
            for (Path child : stream.toList()) {
                if (Files.isSymbolicLink(child)) throw new IOException("Symbolic-link runtime is not allowed: " + child);
                String name = child.getFileName().toString();
                if (!name.matches("jre-[0-9]+")) continue;
                int major = Integer.parseInt(name.substring(4));
                if (!retainedMajors.contains(major)) {
                    candidates.add(new Candidate(Category.MANAGED_RUNTIME, child.toAbsolutePath().normalize(), treeSize(child)));
                }
            }
        }
    }

    private List<Path> regularFiles(Path root) throws IOException {
        validateRoot(root);
        List<Path> files = new ArrayList<>();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return files;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) throw new IOException("Symbolic link is not allowed: " + dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) throw new IOException("Symbolic link is not allowed: " + file);
                if (attrs.isRegularFile()) files.add(file.toAbsolutePath().normalize());
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private void validateCandidate(Candidate candidate) throws IOException {
        Path root = rootFor(candidate.category());
        Path path = validatedChild(root, candidate.path());
        if (path.equals(root.toAbsolutePath().normalize())) throw new IOException("Cleanup root cannot be deleted: " + path);
        if (isPartialDownload(path)) throw new IOException("Active partial download is protected: " + path);
        SecureFiles.rejectSymlinkParents(path);
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic link is not allowed: " + path);
    }

    private Path rootFor(Category category) {
        return switch (category) {
            case LIBRARY -> paths.librariesDir();
            case ASSET_OBJECT -> paths.assetObjectsDir();
            case ASSET_INDEX -> paths.assetIndexesDir();
            case ASSET_VIRTUAL -> paths.assetsDir().resolve("virtual");
            case VERSION_METADATA, VERSION_JAR -> paths.versionsDir();
            case MANAGED_RUNTIME -> paths.runtimesDir();
            case CACHE -> paths.cacheDir();
        };
    }

    private void validateManagedRoots() throws IOException {
        validateRoot(paths.root());
        validateRoot(paths.gameDir());
        validateRoot(paths.librariesDir());
        validateRoot(paths.assetsDir());
        validateRoot(paths.versionsDir());
        validateRoot(paths.runtimesDir());
        validateRoot(paths.cacheDir());
    }

    private void validateRoot(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        SecureFiles.rejectSymlinkParents(normalized);
        if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(normalized)) {
            throw new IOException("Cleanup root must not be a symbolic link: " + normalized);
        }
    }

    private Path validatedChild(Path root, Path child) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedChild = child.toAbsolutePath().normalize();
        if (!normalizedChild.startsWith(normalizedRoot) || normalizedChild.equals(normalizedRoot)) {
            throw new IOException("Path escapes cleanup root: " + child);
        }
        return normalizedChild;
    }

    private boolean isPartialDownload(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".partial") || name.endsWith(".part") || name.contains(".part-") || name.endsWith(".tmp");
    }

    private long treeSize(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic link is not allowed: " + path);
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return Files.size(path);
        final long[] total = {0};
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) throw new IOException("Symbolic link is not allowed: " + dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) throw new IOException("Symbolic link is not allowed: " + file);
                total[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return total[0];
    }

    private void delete(Path path) throws IOException {
        if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(path);
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) throw new IOException("Symbolic link is not allowed: " + dir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(file)) throw new IOException("Symbolic link is not allowed: " + file);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }
}
