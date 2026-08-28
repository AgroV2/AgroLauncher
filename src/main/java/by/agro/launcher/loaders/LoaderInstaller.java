package by.agro.launcher.loaders;

import by.agro.launcher.core.ProgressListener;

import java.io.IOException;
import java.util.List;

public interface LoaderInstaller {

    LoaderType type();

    List<LoaderVersion> availableVersions(String minecraftVersion) throws IOException;

    String install(String minecraftVersion, String loaderVersion, ProgressListener listener) throws IOException;

    default boolean supports(String minecraftVersion) {
        try {
            return !availableVersions(minecraftVersion).isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    final class LoaderVersion {
        public final String version;
        public final boolean stable;
        public final boolean recommended;

        public LoaderVersion(String version, boolean stable, boolean recommended) {
            this.version = version;
            this.stable = stable;
            this.recommended = recommended;
        }

        public LoaderVersion(String version, boolean stable) {
            this(version, stable, false);
        }

        @Override
        public String toString() {
            if (recommended) {
                return version + " (" + by.agro.launcher.i18n.Strings.get("loader.recommended") + ")";
            }
            return stable ? version : version + " (beta)";
        }
    }
}
