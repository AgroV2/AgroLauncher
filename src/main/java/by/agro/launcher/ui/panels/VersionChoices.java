package by.agro.launcher.ui.panels;

import by.agro.launcher.core.Settings;
import by.agro.launcher.horizon.HorizonManager;
import by.agro.launcher.loaders.LoaderType;
import by.agro.launcher.version.RemoteVersion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


final class VersionChoices {
    private VersionChoices() { }

    static RemoteVersion rust() {
        return new RemoteVersion(HorizonManager.INSTANCE_NAME, "horizon", null, null, null);
    }

    static boolean isRust(RemoteVersion version) {
        return version != null && "horizon".equals(version.type);
    }
    static List<RemoteVersion> withRust(List<RemoteVersion> versions) {
        List<RemoteVersion> result = new ArrayList<>();
        boolean inserted = false;
        for (RemoteVersion version : versions) {
            if (isRust(version)) continue;
            if (!inserted && HorizonManager.MINECRAFT_VERSION.equals(version.id)) {
                result.add(rust());
                inserted = true;
            }
            result.add(version);
        }
        if (!inserted) result.add(rust());
        return result;
    }

    static List<LoaderType> javaLoaders() {
        return Arrays.stream(LoaderType.values()).filter(type -> type != LoaderType.HORIZON).toList();
    }

    static boolean matches(RemoteVersion choice, Settings settings) {
        boolean rust = LoaderType.fromId(settings.selectedLoader) == LoaderType.HORIZON;
        return rust ? isRust(choice) && HorizonManager.MINECRAFT_VERSION.equals(settings.selectedVersion)
                : !isRust(choice) && choice.id.equals(settings.selectedVersion);
    }

    static void select(RemoteVersion choice, Settings settings) {
        settings.selectedModBuildId = "";
        if (isRust(choice)) {
            settings.selectedVersion = HorizonManager.MINECRAFT_VERSION;
            settings.selectedLoader = LoaderType.HORIZON.id();
            settings.selectedLoaderVersion = "nightly";
        } else {
            settings.selectedVersion = choice.id;
            if (LoaderType.fromId(settings.selectedLoader) == LoaderType.HORIZON) {
                settings.selectedLoader = LoaderType.VANILLA.id();
                settings.selectedLoaderVersion = "";
            }
        }
    }
}
