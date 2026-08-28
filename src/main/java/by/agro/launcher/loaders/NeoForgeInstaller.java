package by.agro.launcher.loaders;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.jvm.JavaManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class NeoForgeInstaller extends ForgeInstaller {

    private static final String MAVEN_BASE =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

    public NeoForgeInstaller(LauncherPaths paths, Downloader downloader, JavaManager javaManager) {
        super(paths, downloader, javaManager);
    }

    @Override
    public LoaderType type() {
        return LoaderType.NEOFORGE;
    }

    @Override
    public List<LoaderVersion> availableVersions(String minecraftVersion) throws IOException {
        String prefix = versionPrefix(minecraftVersion);
        if (prefix == null) {
            return List.of();
        }

        String xml = downloader.getString(MAVEN_BASE + "maven-metadata.xml");
        List<String> all = parseMavenVersions(xml);

        List<String> matching = new ArrayList<>();
        for (String version : all) {
            if (version.startsWith(prefix)) {
                matching.add(version);
            }
        }
        matching.sort(Comparator.comparing(NeoForgeInstaller::sortKey).reversed());

        List<LoaderVersion> result = new ArrayList<>();
        boolean firstStable = true;
        for (String version : matching) {
            boolean stable = !version.contains("beta");
            boolean recommended = stable && firstStable;
            if (recommended) {
                firstStable = false;
            }
            result.add(new LoaderVersion(version, stable, recommended));
        }
        return result;
    }

    private static String versionPrefix(String minecraftVersion) {
        if (minecraftVersion == null) {
            return null;
        }
        String[] parts = minecraftVersion.split("\\.");
        if (parts.length < 2 || !"1".equals(parts[0])) {
            return null;
        }
        try {
            int minor = Integer.parseInt(parts[1].replaceAll("[^0-9].*$", ""));
            int patch = 0;
            if (parts.length > 2) {
                String raw = parts[2].replaceAll("[^0-9].*$", "");
                if (!raw.isEmpty()) {
                    patch = Integer.parseInt(raw);
                }
            }
            if (minor < 20 || (minor == 20 && patch < 2)) {
                return null;
            }
            return minor + "." + patch + ".";
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String sortKey(String version) {
        String base = version.replace("-beta", "");
        String[] parts = base.split("\\.");
        StringBuilder key = new StringBuilder();
        for (String part : parts) {
            String digits = part.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) {
                digits = "0";
            }
            key.append(String.format("%06d", Long.parseLong(digits))).append('.');
        }
        key.append(version.contains("beta") ? '0' : '1');
        return key.toString();
    }

    @Override
    protected String installerUrl(String fullVersion) {
        return MAVEN_BASE + fullVersion + "/neoforge-" + fullVersion + "-installer.jar";
    }

    @Override
    protected String buildFullVersion(String minecraftVersion, String loaderVersion) {
        return loaderVersion;
    }

    @Override
    protected boolean requiresMcVersionInName() {
        return false;
    }
}
