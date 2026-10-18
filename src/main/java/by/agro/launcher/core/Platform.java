package by.agro.launcher.core;


public enum Platform {
    WINDOWS("windows"),
    LINUX("linux"),
    OSX("osx"),
    UNKNOWN("unknown");

    private final String mojangName;

    Platform(String mojangName) {
        this.mojangName = mojangName;
    }


    public String mojangName() {
        return mojangName;
    }

    private static final Platform CURRENT = detect();

    public static Platform current() {
        return CURRENT;
    }

    private static Platform detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return WINDOWS;
        if (os.contains("mac") || os.contains("darwin")) return OSX;
        if (os.contains("nux") || os.contains("nix") || os.contains("aix") || os.contains("bsd")) return LINUX;
        return UNKNOWN;
    }

    public static boolean isWindows() {
        return CURRENT == WINDOWS;
    }

    public static boolean isLinux() {
        return CURRENT == LINUX;
    }

    public static boolean isOsx() {
        return CURRENT == OSX;
    }


    public static String classpathSeparator() {
        return isWindows() ? ";" : ":";
    }


    public static String arch() {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        if (arch.startsWith("arm")) return "arm32";
        if (arch.contains("64")) return "x64";
        return "x86";
    }


    public static String adoptiumArch() {
        String a = arch();
        switch (a) {
            case "arm64": return "aarch64";
            case "arm32": return "arm";
            case "x86": return "x86";
            default: return "x64";
        }
    }

    public static String adoptiumOs() {
        switch (CURRENT) {
            case WINDOWS: return "windows";
            case OSX: return "mac";
            default: return "linux";
        }
    }

    public static boolean is64Bit() {
        String arch = System.getProperty("os.arch", "");
        return arch.contains("64");
    }


    public static String javaExecutableName() {
        return isWindows() ? "javaw.exe" : "java";
    }


    public static String javaConsoleExecutableName() {
        return isWindows() ? "java.exe" : "java";
    }
}
