package by.agro.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public final class LauncherPaths {

    private static final String DIR_NAME = "agrolauncher";

    private static final String LEGACY_DIR_NAME = "emeraldlauncher";

    private final Path root;

    private static LauncherPaths instance;

    private LauncherPaths(Path root) {
        this.root = root;
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
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(path, destination,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
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
        return versionsDir().resolve(versionId);
    }

    public Path versionJson(String versionId) {
        return versionDir(versionId).resolve(versionId + ".json");
    }

    public Path versionJar(String versionId) {
        return versionDir(versionId).resolve(versionId + ".jar");
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
        return assetsDir().resolve("virtual").resolve(assetIndexId);
    }

    public Path nativesDir(String versionId) {
        return versionDir(versionId).resolve("natives-" + Platform.current().mojangName() + "-" + Platform.arch());
    }

    public Path modsDir() {
        return gameDir().resolve("mods");
    }


    public Path profileModsDir(String profileId) {
        return instancesDir().resolve(profileId).resolve("mods");
    }

    public Path instancesDir() {
        return root.resolve("instances");
    }

    public Path instanceDir(String profileId) {
        return instancesDir().resolve(profileId);
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

        String[] parts = coords.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Некорректные maven-координаты: " + mavenCoords);
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? parts[3] : null;

        StringBuilder fileName = new StringBuilder(artifact).append('-').append(version);
        if (classifier != null && !classifier.isEmpty()) {
            fileName.append('-').append(classifier);
        }
        fileName.append('.').append(extension);

        return group + "/" + artifact + "/" + version + "/" + fileName;
    }
}
