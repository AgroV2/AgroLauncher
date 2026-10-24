package by.agro.launcher.launch;

import by.agro.launcher.auth.AuthlibInjector;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Platform;
import by.agro.launcher.version.Argument;
import by.agro.launcher.version.Library;
import by.agro.launcher.version.ResolvedVersion;
import by.agro.launcher.version.Rule;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class LaunchCommandBuilder {

    private final LauncherPaths paths;

    public LaunchCommandBuilder(LauncherPaths paths) {
        this.paths = paths;
    }

    private static final class Features implements Rule.FeatureSet {
        private final boolean customResolution;
        private final boolean demo;

        private final boolean quickPlayMultiplayer;

        Features(boolean customResolution, boolean demo, boolean quickPlayMultiplayer) {
            this.customResolution = customResolution;
            this.demo = demo;
            this.quickPlayMultiplayer = quickPlayMultiplayer;
        }

        @Override
        public boolean isEnabled(String feature) {
            switch (feature) {
                case "has_custom_resolution":
                    return customResolution;
                case "is_demo_user":
                    return demo;
                case "has_quick_plays_support":
                case "is_quick_play_multiplayer":
                    return quickPlayMultiplayer;
                case "is_quick_play_singleplayer":
                case "is_quick_play_realms":
                    return false;
                default:
                    return false;
            }
        }
    }

    
    public List<String> build(LaunchOptions options) {
        ResolvedVersion version = options.version;
        boolean customResolution = !options.settings.fullscreen;
        boolean quickPlayMultiplayer = options.quickPlayServer != null && !options.quickPlayServer.isBlank();
        Rule.FeatureSet features = new Features(customResolution, false, quickPlayMultiplayer);

        Map<String, String> placeholders = buildPlaceholders(options, features);
        List<String> command = new ArrayList<>();

        
        command.add(options.javaExecutable);

        
        command.add("-Xmx" + options.settings.maxRamMb + "M");
        if (options.settings.minRamMb > 0) {
            command.add("-Xms" + options.settings.minRamMb + "M");
        }

        
        if (options.useAuthlibInjector()) {
            command.add(AuthlibInjector.javaAgentArgument(options.authlibInjectorJar, options.authlibServer));
        }

        
        if (options.settings.optimizedJvmFlags) {
            command.addAll(optimizedFlags());
        }

        
        command.addAll(platformFlags(options));

        
        if (version.jvmArguments != null && !version.jvmArguments.isEmpty()) {
            boolean modulePathValue = false;
            for (Argument argument : version.jvmArguments) {
                if (!argument.isAllowed(features)) {
                    continue;
                }
                for (String value : argument.values) {
                    String substituted = substitute(value, placeholders);
                    if (modulePathValue) {
                        substituted = filterModulePath(substituted, version);
                        modulePathValue = false;
                    }
                    command.add(substituted);
                    if ("-p".equals(substituted) || "--module-path".equals(substituted)) {
                        modulePathValue = true;
                    }
                }
            }
        } else {
            
            command.add("-Djava.library.path=" + options.nativesDir.toAbsolutePath());
            command.add("-cp");
            command.add(placeholders.get("classpath"));
        }

        
        command.addAll(splitArguments(options.settings.extraJvmArgs));

        
        command.add(version.mainClass);

        
        command.addAll(buildGameArguments(options, features, placeholders));

        
        if (options.settings.fullscreen) {
            command.add("--fullscreen");
        } else if (!containsArgument(command, "--width")) {
            command.add("--width");
            command.add(String.valueOf(options.settings.windowWidth));
            command.add("--height");
            command.add(String.valueOf(options.settings.windowHeight));
        }

        addServerArguments(command, options.quickPlayServer);

        command.addAll(splitArguments(options.settings.extraGameArgs));

        return command;
    }

    private void addServerArguments(List<String> command, String rawAddress) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return;
        }

        String address = rawAddress.trim();
        if (containsArgument(command, "--quickPlayMultiplayer")) {
            return;
        }

        ServerAddress server = ServerAddress.parse(address);
        if (!containsArgument(command, "--server")) {
            command.add("--server");
            command.add(server.host);
        }
        if (server.port != null && !containsArgument(command, "--port")) {
            command.add("--port");
            command.add(server.port);
        }
    }

    private static final class ServerAddress {
        private final String host;
        private final String port;

        private ServerAddress(String host, String port) {
            this.host = host;
            this.port = port;
        }

        private static ServerAddress parse(String address) {
            if (address.startsWith("[")) {
                int closingBracket = address.indexOf(']');
                if (closingBracket > 0) {
                    String host = address.substring(1, closingBracket);
                    String suffix = address.substring(closingBracket + 1);
                    return new ServerAddress(host, validPortSuffix(suffix));
                }
            }

            int firstColon = address.indexOf(':');
            int lastColon = address.lastIndexOf(':');
            if (firstColon > 0 && firstColon == lastColon) {
                String port = validPortSuffix(address.substring(lastColon));
                if (port != null) {
                    return new ServerAddress(address.substring(0, lastColon), port);
                }
            }
            return new ServerAddress(address, null);
        }

        private static String validPortSuffix(String suffix) {
            if (suffix == null || suffix.length() < 2 || suffix.charAt(0) != ':') {
                return null;
            }
            String port = suffix.substring(1);
            for (int i = 0; i < port.length(); i++) {
                if (!Character.isDigit(port.charAt(i))) {
                    return null;
                }
            }
            try {
                int value = Integer.parseInt(port);
                return value >= 1 && value <= 65535 ? port : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private List<String> buildGameArguments(LaunchOptions options, Rule.FeatureSet features,
                                            Map<String, String> placeholders) {
        ResolvedVersion version = options.version;
        List<String> result = new ArrayList<>();

        if (version.gameArguments != null && !version.gameArguments.isEmpty()) {
            for (Argument argument : version.gameArguments) {
                if (!argument.isAllowed(features)) {
                    continue;
                }
                for (String value : argument.values) {
                    result.add(substitute(value, placeholders));
                }
            }
            return result;
        }

        
        if (version.minecraftArguments != null) {
            for (String token : version.minecraftArguments.trim().split("\\s+")) {
                if (!token.isEmpty()) {
                    result.add(substitute(token, placeholders));
                }
            }
        }
        return result;
    }

    
    private Map<String, String> buildPlaceholders(LaunchOptions options, Rule.FeatureSet features) {
        ResolvedVersion version = options.version;
        Map<String, String> map = new HashMap<>();

        String classpath = buildClasspath(version, features, options.loaderType);
        String librariesPath = paths.librariesDir().toAbsolutePath().toString();

        
        map.put("auth_player_name", options.account.username);
        map.put("auth_uuid", options.account.uuid);
        map.put("auth_access_token", options.account.accessToken);
        map.put("auth_session", "token:" + options.account.accessToken + ":" + options.account.uuid);
        map.put("user_type", options.account.userType());
        map.put("user_properties", "{}");

        
        map.put("version_name", version.id);
        map.put("version_type", version.type != null ? version.type : "release");
        map.put("profile_name", options.launcherName);
        map.put("launcher_name", options.launcherName);
        map.put("launcher_version", options.launcherVersion);

        
        map.put("game_directory", options.gameDir.toAbsolutePath().toString());
        map.put("assets_root", paths.assetsDir().toAbsolutePath().toString());
        map.put("game_assets", options.assetsDir.toAbsolutePath().toString());
        map.put("assets_index_name", version.assetIndex != null && version.assetIndex.id != null
                ? version.assetIndex.id
                : String.valueOf(version.assetsId));
        map.put("natives_directory", options.nativesDir.toAbsolutePath().toString());
        map.put("classpath", classpath);

        
        map.put("library_directory", librariesPath);
        map.put("libraries_directory", librariesPath);
        map.put("classpath_separator", Platform.classpathSeparator());

        
        map.put("resolution_width", String.valueOf(options.settings.windowWidth));
        map.put("resolution_height", String.valueOf(options.settings.windowHeight));

        
        map.put("clientid", "");
        map.put("auth_xuid", "");
        map.put("quickPlayPath", "");
        map.put("quickPlaySingleplayer", "");
        map.put("quickPlayMultiplayer", options.quickPlayServer == null
                ? "" : options.quickPlayServer.trim());
        map.put("quickPlayRealms", "");

        return map;
    }

    
    private String buildClasspath(ResolvedVersion version, Rule.FeatureSet features, String optionsLoaderType) {
        Set<String> entries = new LinkedHashSet<>();
        List<Library> classpathLibraries = version.classpathLibraries(features);

        Path clientJar = paths.versionJar(version.jarVersionId).toAbsolutePath().normalize();
        boolean neoForge = "neoforge".equalsIgnoreCase(optionsLoaderType);
        for (Library library : classpathLibraries) {
            Path jar = paths.librariesDir().resolve(library.relativePath()).toAbsolutePath().normalize();
            if (isDuplicateMinecraftClient(library, jar, clientJar)) {
                continue;
            }
            entries.add(jar.toString());
        }

        clientJar = paths.versionJar(version.jarVersionId);
        if (!neoForge && Files.exists(clientJar)) {
            entries.add(clientJar.toAbsolutePath().normalize().toString());
        } else if (!neoForge) {
            Path fallback = paths.versionJar(version.id);
            if (Files.exists(fallback)) {
                entries.add(fallback.toAbsolutePath().normalize().toString());
            }
        }

        return String.join(Platform.classpathSeparator(), entries);
    }

    private boolean isDuplicateMinecraftClient(Library library, Path jar, Path clientJar) {
        if (jar.equals(clientJar)) {
            return true;
        }
        String name = library.name == null ? "" : library.name.toLowerCase(java.util.Locale.ROOT);
        String file = jar.getFileName() == null ? "" : jar.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.startsWith("com.mojang:minecraft:")
                || name.startsWith("net.minecraft:client:")
                || (file.startsWith("client-") && file.endsWith(".jar")
                    && jar.toString().replace('\\', '/').contains("/net/minecraft/client/"));
    }

    private String filterModulePath(String modulePath, ResolvedVersion version) {
        if (modulePath == null || modulePath.isBlank()) {
            return modulePath;
        }
        Path vanillaClient = paths.versionJar(version.jarVersionId).toAbsolutePath().normalize();
        Path selectedProfile = paths.versionJar(version.id).toAbsolutePath().normalize();
        List<String> filtered = new ArrayList<>();
        for (String entry : modulePath.split(java.util.regex.Pattern.quote(Platform.classpathSeparator()))) {
            if (entry == null || entry.isBlank()) continue;
            Path candidate;
            try {
                candidate = Path.of(entry).toAbsolutePath().normalize();
            } catch (RuntimeException ignored) {
                filtered.add(entry);
                continue;
            }
            if (candidate.equals(vanillaClient) || candidate.equals(selectedProfile)
                    || isMinecraftVersionJar(candidate)) {
                continue;
            }
            filtered.add(candidate.toString());
        }
        return String.join(Platform.classpathSeparator(), filtered);
    }

    private boolean isMinecraftVersionJar(Path candidate) {
        Path versions = paths.versionsDir().toAbsolutePath().normalize();
        if (!candidate.startsWith(versions) || candidate.getFileName() == null) {
            return false;
        }
        String file = candidate.getFileName().toString();
        return file.endsWith(".jar") && candidate.getParent() != null
                && file.equals(candidate.getParent().getFileName() + ".jar");
    }

    
    private List<String> optimizedFlags() {
        List<String> flags = new ArrayList<>();
        flags.add("-XX:+UnlockExperimentalVMOptions");
        flags.add("-XX:+UseG1GC");
        flags.add("-XX:G1NewSizePercent=20");
        flags.add("-XX:G1ReservePercent=20");
        flags.add("-XX:MaxGCPauseMillis=50");
        flags.add("-XX:G1HeapRegionSize=32M");
        flags.add("-XX:-OmitStackTraceInFastThrow");
        flags.add("-Dfile.encoding=UTF-8");
        flags.add("-Dstdout.encoding=UTF-8");
        flags.add("-Dstderr.encoding=UTF-8");
        return flags;
    }

    
    private List<String> platformFlags(LaunchOptions options) {
        List<String> flags = new ArrayList<>();
        switch (Platform.current()) {
            case WINDOWS:
                
                flags.add("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump");
                break;
            case OSX:
                flags.add("-XstartOnFirstThread");
                flags.add("-Dapple.awt.application.name=" + options.launcherName);
                break;
            case LINUX:
                
                flags.add("-Dorg.lwjgl.util.NoChecks=true");
                break;
            default:
                break;
        }
        return flags;
    }

    
    private String substitute(String value, Map<String, String> placeholders) {
        if (value == null || value.indexOf('$') < 0) {
            return value;
        }
        String result = value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String token = "${" + entry.getKey() + "}";
            if (result.contains(token)) {
                result = result.replace(token, entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result;
    }

    
    static List<String> splitArguments(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (char c : raw.trim().toCharArray()) {
            if (inQuotes) {
                if (c == quoteChar) {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                inQuotes = true;
                quoteChar = c;
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private boolean containsArgument(List<String> command, String argument) {
        return command.contains(argument);
    }
}
