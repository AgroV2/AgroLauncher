package by.agro.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


public final class SystemInfo {

    private static long cachedTotalMb = -1;

    private SystemInfo() {
    }


    public static synchronized long totalRamMb() {
        if (cachedTotalMb >= 0) {
            return cachedTotalMb;
        }
        cachedTotalMb = detectTotalRamMb();
        return cachedTotalMb;
    }

    private static long detectTotalRamMb() {

        try {
            java.lang.management.OperatingSystemMXBean os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            for (String method : new String[]{"getTotalMemorySize", "getTotalPhysicalMemorySize"}) {
                try {
                    var m = os.getClass().getMethod(method);
                    m.setAccessible(true);
                    Object value = m.invoke(os);
                    if (value instanceof Number number) {
                        long bytes = number.longValue();
                        if (bytes > 0) {
                            return bytes / (1024 * 1024);
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {

                }
            }
        } catch (Throwable ignored) {

        }


        if (Platform.isLinux()) {
            try {
                Path meminfo = Paths.get("/proc/meminfo");
                if (Files.exists(meminfo)) {
                    List<String> lines = Files.readAllLines(meminfo, StandardCharsets.UTF_8);
                    for (String line : lines) {
                        if (line.startsWith("MemTotal:")) {
                            String digits = line.replaceAll("[^0-9]", "");
                            if (!digits.isEmpty()) {
                                return Long.parseLong(digits) / 1024;
                            }
                        }
                    }
                }
            } catch (IOException | NumberFormatException ignored) {

            }
        }


        if (Platform.isWindows()) {
            try {
                Process process = new ProcessBuilder("wmic", "computersystem", "get", "TotalPhysicalMemory")
                        .redirectErrorStream(true)
                        .start();
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                process.waitFor();
                for (String line : output.split("\\R")) {
                    String trimmed = line.trim();
                    if (trimmed.matches("\\d+")) {
                        return Long.parseLong(trimmed) / (1024 * 1024);
                    }
                }
            } catch (IOException | NumberFormatException ignored) {

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return 0;
    }


    public static int ramSliderMaxMb() {
        long total = totalRamMb();
        if (total <= 0) {
            return 8192;
        }

        long max = Math.max(4096, total);
        return (int) Math.min(max, 65536);
    }

    public static String formatMb(int mb) {
        String gigabytes = by.agro.launcher.i18n.Strings.get("unit.gb");
        String megabytes = by.agro.launcher.i18n.Strings.get("unit.mb");
        if (mb >= 1024) {
            double gb = mb / 1024.0;
            if (Math.abs(gb - Math.rint(gb)) < 0.01) {
                return String.format("%.0f %s", gb, gigabytes);
            }
            return String.format("%.1f %s", gb, gigabytes);
        }
        return mb + " " + megabytes;
    }
}
