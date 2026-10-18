package by.agro.launcher.launch;

import by.agro.launcher.core.Platform;
import by.agro.launcher.core.SystemInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MemoryAdvisor {
    private MemoryAdvisor() {
    }

    public static Advice advise(String minecraftVersion, String loader, int enabledMods,
                                int minMb, int maxMb) {
        long total = SystemInfo.totalRamMb();
        int reserve = total > 0 ? (int) Math.max(1536, Math.min(4096, total / 4)) : 2048;
        int minecraftMinor = minecraftMinor(minecraftVersion);
        int base = minecraftMinor >= 18 ? 3072 : minecraftMinor >= 13 ? 2048 : 1536;
        if (loader != null && !loader.isBlank() && !"vanilla".equalsIgnoreCase(loader)) base += 512;
        base += Math.min(4096, Math.max(0, enabledMods) * 32);
        int recommendedMin = align512(Math.max(1024, base - 512));
        int recommendedMax = align512(Math.max(recommendedMin, base + 1024));
        if (total > 0) recommendedMax = Math.min(recommendedMax, align512((int) Math.max(512, total - reserve)));

        List<String> errors = new ArrayList<>();
        if (maxMb < 512) errors.add("Maximum RAM must be at least 512 MB");
        if (minMb < 0) errors.add("Minimum RAM cannot be negative");
        if (minMb > maxMb) errors.add("Minimum RAM cannot exceed maximum RAM");
        if (total > 0 && maxMb > total - reserve) {
            errors.add("Selected RAM leaves less than " + reserve + " MB for the operating system");
        }
        if (!Platform.is64Bit() && maxMb > 1536) {
            errors.add("32-bit Java is limited to about 1536 MB of heap");
        }
        return new Advice(recommendedMin, recommendedMax, reserve, errors);
    }

    private static int minecraftMinor(String version) {
        if (version == null) return 0;
        String[] parts = version.split("\\.");
        if (parts.length < 2 || !"1".equals(parts[0])) return 99;
        try { return Integer.parseInt(parts[1].replaceAll("[^0-9].*$", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    private static int align512(int value) {
        return Math.max(512, (value / 512) * 512);
    }

    public static final class Advice {
        public final int recommendedMinMb;
        public final int recommendedMaxMb;
        public final int osReserveMb;
        public final List<String> blockingErrors;

        Advice(int recommendedMinMb, int recommendedMaxMb, int osReserveMb, List<String> errors) {
            this.recommendedMinMb = recommendedMinMb;
            this.recommendedMaxMb = recommendedMaxMb;
            this.osReserveMb = osReserveMb;
            this.blockingErrors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        public boolean valid() { return blockingErrors.isEmpty(); }
    }
}
