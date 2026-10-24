package by.agro.launcher.launch;

import by.agro.launcher.auth.Account;
import by.agro.launcher.core.Settings;
import by.agro.launcher.version.ResolvedVersion;

import java.nio.file.Path;


public final class LaunchOptions {

    public ResolvedVersion version;
    public Account account;
    public Settings settings;

    public Path gameDir;

    public Path assetsDir;

    public Path nativesDir;

    public String javaExecutable;

    public String loaderType = "vanilla";

    public Path authlibInjectorJar;

    public String authlibServer;

    public String launcherName = "AgroLauncher";
    public String launcherVersion = by.agro.launcher.core.LauncherVersion.VERSION;

    public String quickPlayServer = "";

    public LaunchOptions() {
    }

    public boolean useAuthlibInjector() {
        return authlibInjectorJar != null && authlibServer != null && !authlibServer.isBlank();
    }
}
