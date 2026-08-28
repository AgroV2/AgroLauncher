package by.agro.launcher.version;

import java.util.ArrayList;
import java.util.List;

/**
 * Итоговая версия после склейки цепочки inheritsFrom.
 *
 * Пример цепочки: fabric-loader-0.16.9-1.21.1 → 1.21.1
 * Профиль загрузчика задаёт mainClass и свои библиотеки, родитель — ассеты, jar клиента и базовые библиотеки.
 */
public final class ResolvedVersion {

    /** Идентификатор запускаемой версии (имя профиля). */
    public final String id;

    /** Версия, из которой берётся клиентский jar (корень цепочки). */
    public final String jarVersionId;

    public final String mainClass;

    /** Тип версии: release / snapshot / old_beta / old_alpha. */
    public final String type;

    public final String assetsId;
    public final VersionJson.AssetIndexInfo assetIndex;
    public final VersionJson.DownloadInfo clientDownload;
    public final int javaMajorVersion;

    /** Библиотеки: дочерние переопределяют родительские по ключу group:artifact[:classifier]. */
    public final List<Library> libraries;

    public final List<Argument> gameArguments;
    public final List<Argument> jvmArguments;
    public final String minecraftArguments;
    public final boolean legacyArguments;
    public final boolean preV16Assets;

    /** Вся цепочка от дочерней к корневой — для отладки. */
    public final List<String> chain;

    ResolvedVersion(String id, String jarVersionId, String mainClass, String type, String assetsId,
                    VersionJson.AssetIndexInfo assetIndex, VersionJson.DownloadInfo clientDownload,
                    int javaMajorVersion, List<Library> libraries, List<Argument> gameArguments,
                    List<Argument> jvmArguments, String minecraftArguments, boolean legacyArguments,
                    boolean preV16Assets, List<String> chain) {
        this.id = id;
        this.jarVersionId = jarVersionId;
        this.mainClass = mainClass;
        this.type = type;
        this.assetsId = assetsId;
        this.assetIndex = assetIndex;
        this.clientDownload = clientDownload;
        this.javaMajorVersion = javaMajorVersion;
        this.libraries = libraries;
        this.gameArguments = gameArguments;
        this.jvmArguments = jvmArguments;
        this.minecraftArguments = minecraftArguments;
        this.legacyArguments = legacyArguments;
        this.preV16Assets = preV16Assets;
        this.chain = chain;
    }

    /** Библиотеки, применимые к текущей платформе. */
    public List<Library> applicableLibraries(Rule.FeatureSet featureSet) {
        List<Library> result = new ArrayList<>();
        for (Library library : libraries) {
            if (library.isAllowed(featureSet)) {
                result.add(library);
            }
        }
        return result;
    }

    /** Только нативные библиотеки, применимые к текущей платформе. */
    public List<Library> nativeLibraries(Rule.FeatureSet featureSet) {
        List<Library> result = new ArrayList<>();
        for (Library library : applicableLibraries(featureSet)) {
            if (library.isNative()) {
                result.add(library);
            }
        }
        return result;
    }

    /** Библиотеки для classpath: всё, кроме legacy-нативов (современные natives тоже идут в cp). */
    public List<Library> classpathLibraries(Rule.FeatureSet featureSet) {
        List<Library> result = new ArrayList<>();
        for (Library library : applicableLibraries(featureSet)) {
            if (library.hasLegacyNatives()) {
                continue;
            }
            result.add(library);
        }
        return result;
    }
}
