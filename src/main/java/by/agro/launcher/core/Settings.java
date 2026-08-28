package by.agro.launcher.core;

import java.io.IOException;
import java.nio.file.Files;



public final class Settings {
    public int maxRamMb = defaultRam();
    public int minRamMb = 512;
    public String javaPath = "";
    public boolean useManagedJava = true;
    public String extraJvmArgs = "";
    public String extraGameArgs = "";
    public int windowWidth = 854;
    public int windowHeight = 480;
    public boolean fullscreen = false;
    public boolean closeOnLaunch = false;
    public boolean showConsole = true;
    public boolean keepLauncherOpen = true;

    
    public String selectedVersion = "";

    
    public String selectedLoader = "vanilla";

    
    public String selectedLoaderVersion = "";

    
    public String selectedModBuildId = "";

    
    public boolean showSnapshots = false;

    
    public boolean showOldVersions = false;

    
    public String activeAccountId = "";

    
    public boolean optimizedJvmFlags = true;

    

    
    public String language = "";

    
    public String themePreset = "EMERALD_DARK";

    
    public String customAccentColor = "#10B981";

    
    public String backgroundImagePath = "";

    
    public boolean backgroundBlur = false;

    
    public int backgroundBlurRadius = 12;

    
    public int backgroundDimPercent = 45;

    
    public int panelOpacityPercent = 88;

    

    
    public String modSearchSort = "downloads";

    
    public boolean installModDependencies = true;

    
    private static int defaultRam() {
        long totalMb = SystemInfo.totalRamMb();
        if (totalMb <= 0) {
            return 2048;
        }
        long half = totalMb / 2;
        long clamped = Math.max(1024, Math.min(8192, half));
        return (int) (clamped / 512 * 512);
    }

    public static Settings load() {
        LauncherPaths paths = LauncherPaths.get();
        try {
            if (Files.exists(paths.settingsFile())) {
                Settings loaded = Json.read(paths.settingsFile(), Settings.class);
                if (loaded != null) {
                    loaded.normalize();
                    return loaded;
                }
            }
        } catch (Exception e) {
            System.err.println("Не удалось прочитать settings.json, используются значения по умолчанию: " + e.getMessage());
        }
        return new Settings();
    }

    public void save() {
        try {
            normalize();
            Json.write(LauncherPaths.get().settingsFile(), this);
        } catch (IOException e) {
            System.err.println("Не удалось сохранить настройки: " + e.getMessage());
        }
    }

    
    private void normalize() {
        if (maxRamMb < 512) maxRamMb = 512;
        long limit = Math.max(2048, SystemInfo.totalRamMb());
        if (maxRamMb > limit) maxRamMb = (int) limit;
        if (minRamMb < 0) minRamMb = 0;
        if (minRamMb > maxRamMb) minRamMb = Math.min(512, maxRamMb);
        if (windowWidth < 320) windowWidth = 854;
        if (windowHeight < 240) windowHeight = 480;
        if (selectedLoader == null || selectedLoader.isBlank()) selectedLoader = "vanilla";
        if (selectedModBuildId == null) selectedModBuildId = "";

        
        if (themePreset == null || themePreset.isBlank()) themePreset = "EMERALD_DARK";
        if (customAccentColor == null || customAccentColor.isBlank()) customAccentColor = "#10B981";
        if (backgroundImagePath == null) backgroundImagePath = "";
        backgroundBlurRadius = clamp(backgroundBlurRadius, 1, 60);
        backgroundDimPercent = clamp(backgroundDimPercent, 0, 90);
        panelOpacityPercent = clamp(panelOpacityPercent, 40, 100);
        if (modSearchSort == null || modSearchSort.isBlank()) modSearchSort = "downloads";
        if (language == null) language = "";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
