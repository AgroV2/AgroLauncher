package by.agro.launcher.auth;

import by.agro.launcher.core.Downloader;
import by.agro.launcher.core.HashUtil;
import by.agro.launcher.core.Json;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.ProgressListener;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


public final class AuthlibInjector {

    private static final String LATEST_METADATA = "https://authlib-injector.yushi.moe/artifact/latest.json";

    private final LauncherPaths paths;
    private final Downloader downloader;

    public AuthlibInjector(LauncherPaths paths, Downloader downloader) {
        this.paths = paths;
        this.downloader = downloader;
    }


    public Path ensureInstalled(ProgressListener listener) throws IOException {
        Path jar = paths.authlibInjectorJar();
        Path versionMarker = paths.authlibDir().resolve("version.txt");

        String metadata;
        try {
            metadata = downloader.getString(LATEST_METADATA);
        } catch (IOException e) {

            if (Files.exists(jar)) {
                listener.onMessage("Не удалось проверить обновление authlib-injector, используется локальная копия");
                return jar;
            }
            throw new IOException("Не удалось получить authlib-injector: " + e.getMessage(), e);
        }

        JsonObject info = Json.parseObject(metadata);
        String version = Json.string(info, "version", "unknown");
        String url = Json.string(info, "download_url", null);
        String sha256 = null;
        JsonObject checksums = Json.object(info, "checksums");
        if (checksums != null) {
            sha256 = Json.string(checksums, "sha256", null);
        }
        if (url == null) {
            throw new IOException("В метаданных authlib-injector нет ссылки на файл");
        }

        boolean upToDate = Files.exists(jar)
                && Files.exists(versionMarker)
                && version.equals(Files.readString(versionMarker).trim())
                && (sha256 == null || HashUtil.verifySha256(jar, sha256));

        if (upToDate) {
            return jar;
        }

        listener.onProgress("authlib-injector", 0, 1, "загрузка " + version);
        Files.createDirectories(paths.authlibDir());
        downloader.download(url, jar);

        if (sha256 != null && !HashUtil.verifySha256(jar, sha256)) {
            Files.deleteIfExists(jar);
            throw new IOException("Контрольная сумма authlib-injector не совпала");
        }
        Files.writeString(versionMarker, version);

        listener.onProgress("authlib-injector", 1, 1, version);
        listener.onMessage("authlib-injector " + version + " готов");
        return jar;
    }


    public static String javaAgentArgument(Path injectorJar, String authServer) {
        return "-javaagent:" + injectorJar.toAbsolutePath() + "=" + authServer;
    }


    public static String javaAgentArgumentForElyBy(Path injectorJar) {
        return javaAgentArgument(injectorJar, ElyByAuth.INJECTOR_ARGUMENT);
    }
}
