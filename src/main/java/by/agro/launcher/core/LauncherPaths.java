package by.agro.launcher.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;


public final class LauncherPaths {

    private static final String DIR_NAME = "agrolauncher";

    private static final String LEGACY_DIR_NAME = "emeraldlauncher";

    private final Path root;

    private static LauncherPaths instance;

    private LauncherPaths(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public static LauncherPaths forRoot(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("Root directory is not specified");
        }
        return new LauncherPaths(root);
    }

    private static String safePathSegment(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(description + " is not specified");
        }
        Path path;
        try {
            path = Paths.get(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid " + description + ": " + value, e);
        }
        if (path.isAbsolute() || path.getNameCount() != 1
                || ".".equals(value) || "..".equals(value)
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Unsafe " + description + ": " + value);
        }
        return value;
    }

    public static synchronized LauncherPaths get() {
        if (instance == null) {
            Path root = defaultRoot();
            migrateLegacyData(root);
            instance = new LauncherPaths(root);
        }
        return instance;
    }


    private static void migrateLegacyData(Path newRoot) {
        try {
            if (Files.exists(newRoot)) {
                return;
            }
            Path legacyRoot = legacyRoot();
            if (legacyRoot == null || !Files.isDirectory(legacyRoot)) {
                return;
            }
            System.out.println("Перенос данных из " + legacyRoot + " в " + newRoot);
            copyRecursively(legacyRoot, newRoot);
            System.out.println("Перенос завершён. Старый каталог оставлен без изменений.");
        } catch (IOException | RuntimeException e) {
            System.err.println("Не удалось перенести старые данные: " + e.getMessage());
        }
    }


    private static Path legacyRoot() {
        String home = System.getProperty("user.home", ".");
        switch (Platform.current()) {
            case WINDOWS: {
                String appData = System.getenv("APPDATA");
                Path base = (appData != null && !appData.isBlank())
                        ? Paths.get(appData)
                        : Paths.get(home, "AppData", "Roaming");
                return base.resolve("." + LEGACY_DIR_NAME);
            }
            case OSX:
                return Paths.get(home, "Library", "Application Support", LEGACY_DIR_NAME);
            default: {
                String xdg = System.getenv("XDG_DATA_HOME");
                if (xdg != null && !xdg.isBlank()) {
                    return Paths.get(xdg).resolve(LEGACY_DIR_NAME);
                }
                return Paths.get(home, "." + LEGACY_DIR_NAME);
            }
        }
    }


    private static void copyRecursively(Path source, Path target) throws IOException {
        Path sourceRoot = source.toAbsolutePath().normalize();
        Path targetRoot = target.toAbsolutePath().normalize();
        SecureFiles.rejectSymlinkParents(sourceRoot);
        SecureFiles.rejectSymlinkParents(targetRoot);
        SecureFiles.rejectSymbolicLink(sourceRoot, "Migration source");
        if (Files.exists(targetRoot, LinkOption.NOFOLLOW_LINKS)) {
            SecureFiles.rejectSymbolicLink(targetRoot, "Migration destination");
        }
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) {
                    throw new IOException("Symbolic links are not allowed during migration: " + dir);
                }
                Path destination = targetRoot.resolve(sourceRoot.relativize(dir)).normalize();
                if (!destination.startsWith(targetRoot)) {
                    throw new IOException("Migration path escapes destination: " + destination);
                }
                SecureFiles.rejectSymlinkParents(destination);
                Files.createDirectories(destination);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || !attrs.isRegularFile() || Files.isSymbolicLink(file)) {
                    throw new IOException("Only regular files may be migrated: " + file);
                }
                Path destination = targetRoot.resolve(sourceRoot.relativize(file)).normalize();
                if (!destination.startsWith(targetRoot)) {
                    throw new IOException("Migration path escapes destination: " + destination);
                }
                SecureFiles.rejectSymlinkParents(destination);
                if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                    SecureFiles.rejectSymbolicLink(destination, "Migration destination");
                }
                Files.copy(file, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS);
                return FileVisitResult.CONTINUE;
            }
        });
    }


    public static synchronized void override(Path customRoot) {
        instance = new LauncherPaths(customRoot.toAbsolutePath());
    }

    private static Path defaultRoot() {
        String home = System.getProperty("user.home", ".");
        switch (Platform.current()) {
            case WINDOWS: {
                String appData = System.getenv("APPDATA");
                Path base = (appData != null && !appData.isBlank())
                        ? Paths.get(appData)
                        : Paths.get(home, "AppData", "Roaming");
                return base.resolve("." + DIR_NAME);
            }
            case OSX:
                return Paths.get(home, "Library", "Application Support", DIR_NAME);
            default: {
                String xdg = System.getenv("XDG_DATA_HOME");
                if (xdg != null && !xdg.isBlank()) {
                    return Paths.get(xdg).resolve(DIR_NAME);
                }
                return Paths.get(home, "." + DIR_NAME);
            }
        }
    }

    public Path root() {
        return root;
    }


    public Path gameDir() {
        return root.resolve("minecraft");
    }

    public Path versionsDir() {
        return gameDir().resolve("versions");
    }

    public Path versionDir(String versionId) {
        return versionsDir().resolve(safePathSegment(versionId, "version ID"));
    }

    public Path versionJson(String versionId) {
        String safeVersionId = safePathSegment(versionId, "version ID");
        return versionDir(safeVersionId).resolve(safeVersionId + ".json");
    }

    public Path versionJar(String versionId) {
        String safeVersionId = safePathSegment(versionId, "version ID");
        return versionDir(safeVersionId).resolve(safeVersionId + ".jar");
    }

    public Path librariesDir() {
        return gameDir().resolve("libraries");
    }

    public Path assetsDir() {
        return gameDir().resolve("assets");
    }

    public Path assetIndexesDir() {
        return assetsDir().resolve("indexes");
    }

    public Path assetObjectsDir() {
        return assetsDir().resolve("objects");
    }


    public Path assetsVirtualDir(String assetIndexId) {
        return assetsDir().resolve("virtual").resolve(safePathSegment(assetIndexId, "asset index ID"));
    }

    public Path nativesDir(String versionId) {
        return versionDir(versionId).resolve("natives-" + Platform.current().mojangName() + "-" + Platform.arch());
    }

    public Path modsDir() {
        return gameDir().resolve("mods");
    }


    public Path profileModsDir(String profileId) {
        return instancesDir().resolve(safePathSegment(profileId, "profile ID")).resolve("mods");
    }

    public Path instancesDir() {
        return root.resolve("instances");
    }

    public Path instanceDir(String profileId) {
        return instancesDir().resolve(safePathSegment(profileId, "profile ID"));
    }


    public Path runtimesDir() {
        return root.resolve("runtimes");
    }

    public Path runtimeDir(int majorVersion) {
        return runtimesDir().resolve("jre-" + majorVersion);
    }

    public Path authlibDir() {
        return root.resolve("authlib");
    }

    public Path authlibInjectorJar() {
        return authlibDir().resolve("authlib-injector.jar");
    }

    public Path settingsFile() {
        return root.resolve("settings.json");
    }

    public Path accountsFile() {
        return root.resolve("accounts.json");
    }

    public Path profilesFile() {
        return root.resolve("profiles.json");
    }

    public Path cacheDir() {
        return root.resolve("cache");
    }

    public Path versionManifestCacheFile() {
        return cacheDir().resolve("version_manifest_v2.json");
    }

    public Path logsDir() {
        return root.resolve("logs");
    }

    public Path installersDir() {
        return cacheDir().resolve("installers");
    }


    public void ensureDirectories() throws IOException {
        Files.createDirectories(gameDir());
        Files.createDirectories(versionsDir());
        Files.createDirectories(librariesDir());
        Files.createDirectories(assetIndexesDir());
        Files.createDirectories(assetObjectsDir());
        Files.createDirectories(instancesDir());
        Files.createDirectories(runtimesDir());
        Files.createDirectories(authlibDir());
        Files.createDirectories(installersDir());
        Files.createDirectories(logsDir());
    }


    public Path libraryPath(String mavenCoords) {
        return librariesDir().resolve(mavenToRelativePath(mavenCoords));
    }


    public static String mavenToRelativePath(String mavenCoords) {
        String coords = mavenCoords;
        String extension = "jar";


        int at = coords.indexOf('@');
        if (at >= 0) {
            extension = coords.substring(at + 1);
            coords = coords.substring(0, at);
        }

        String[] parts = coords.split(":", -1);
        if (parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException("Invalid Maven coordinates: " + mavenCoords);
        }
        String groupId = safeMavenPart(parts[0], "groupId", true);
        String artifact = safeMavenPart(parts[1], "artifactId", false);
        String version = safeMavenPart(parts[2], "version", false);
        String classifier = parts.length > 3 ? safeMavenPart(parts[3], "classifier", false) : null;
        extension = safeMavenPart(extension, "extension", false);
        String group = groupId.replace('.', '/');

        StringBuilder fileName = new StringBuilder(artifact).append('-').append(version);
        if (classifier != null && !classifier.isEmpty()) {
            fileName.append('-').append(classifier);
        }
        fileName.append('.').append(extension);

        return group + "/" + artifact + "/" + version + "/" + fileName;
    }

    private static String safeMavenPart(String value, String description, boolean allowDots) {
        if (value == null || value.isBlank() || value.contains("..")
                || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Unsafe " + description + ": " + value);
        }
        String pattern = allowDots ? "[A-Za-z0-9_.-]+" : "[A-Za-z0-9_-]+";
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException("Invalid " + description + ": " + value);
        }
        return value;
    }
}
