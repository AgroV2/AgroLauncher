package by.agro.launcher.stats;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.SecureFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;





public final class PlayStatsStore {

    public static final String FILE_NAME = "playtime-stats.json";
    static final int KEEP_DAYS = 730;

    private final Path file;
    private final Path launcherRoot;
    private final ZoneId zone;

    public PlayStatsStore(Path launcherRoot) {
        this(launcherRoot, launcherRoot.resolve("stats").resolve(FILE_NAME), ZoneId.systemDefault());
    }

    PlayStatsStore(Path launcherRoot, Path file, ZoneId zone) {
        this.launcherRoot = launcherRoot.toAbsolutePath().normalize();
        this.file = file;
        this.zone = zone;
    }

    public static final class Entry {
        public long ms;
        public int sessions;
    }

    public static final class Data {
        public int version = 1;

        public Map<String, Map<String, Entry>> days = new TreeMap<>();
    }

    public record DayTotal(LocalDate day, long ms, int sessions) { }

    public record InstanceTotal(String key, long ms, int sessions, LocalDate lastPlayed) { }


    public String keyFor(Path gameDir) {
        Path normalized = gameDir.toAbsolutePath().normalize();
        if (normalized.startsWith(launcherRoot)) {
            return launcherRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }


    public Path pathFor(String key) {
        Path path = Path.of(key);
        return path.isAbsolute() ? path.normalize() : launcherRoot.resolve(key).normalize();
    }

    public synchronized void record(Path gameDir, long startMillis, long endMillis) {
        if (gameDir == null || endMillis <= startMillis) return;
        Data data = load();
        String key = keyFor(gameDir);
        ZonedDateTime cursor = Instant.ofEpochMilli(startMillis).atZone(zone);
        ZonedDateTime end = Instant.ofEpochMilli(endMillis).atZone(zone);
        boolean first = true;
        while (cursor.isBefore(end)) {
            ZonedDateTime nextDay = cursor.toLocalDate().plusDays(1).atStartOfDay(zone);
            ZonedDateTime sliceEnd = nextDay.isBefore(end) ? nextDay : end;
            long slice = sliceEnd.toInstant().toEpochMilli() - cursor.toInstant().toEpochMilli();
            Entry entry = data.days.computeIfAbsent(cursor.toLocalDate().toString(), d -> new TreeMap<>())
                    .computeIfAbsent(key, k -> new Entry());
            entry.ms += slice;
            if (first) entry.sessions++;
            first = false;
            cursor = sliceEnd;
        }
        prune(data, end.toLocalDate());
        save(data);
    }

    private static void prune(Data data, LocalDate today) {
        String oldest = today.minusDays(KEEP_DAYS).toString();
        data.days.keySet().removeIf(day -> day.compareTo(oldest) < 0);
    }

    public synchronized Data load() {
        try {
            if (Files.isRegularFile(file)) {
                Data data = Json.read(file, Data.class);
                if (data != null) {
                    if (data.days == null) data.days = new TreeMap<>();
                    else data.days = new TreeMap<>(data.days);
                    data.days.values().removeIf(java.util.Objects::isNull);
                    return data;
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return new Data();
    }

    private void save(Data data) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = SecureFiles.createSiblingTemp(file, ".tmp");
            try {
                Json.write(temporary, data);
                SecureFiles.atomicReplace(temporary, file);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException ignored) {
        }
    }


    public static List<DayTotal> byDay(Data data, LocalDate today, int days) {
        List<DayTotal> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            Map<String, Entry> entries = data.days.get(day.toString());
            long ms = 0;
            int sessions = 0;
            if (entries != null) {
                for (Entry entry : entries.values()) {
                    if (entry == null) continue;
                    ms += Math.max(0, entry.ms);
                    sessions += Math.max(0, entry.sessions);
                }
            }
            result.add(new DayTotal(day, ms, sessions));
        }
        return result;
    }


    public static List<InstanceTotal> byInstance(Data data, LocalDate from) {
        Map<String, long[]> totals = new HashMap<>();
        Map<String, String> last = new HashMap<>();
        String fromText = from == null ? null : from.toString();
        for (Map.Entry<String, Map<String, Entry>> day : data.days.entrySet()) {
            if (fromText != null && day.getKey().compareTo(fromText) < 0) continue;
            for (Map.Entry<String, Entry> instance : day.getValue().entrySet()) {
                if (instance.getValue() == null) continue;
                long[] total = totals.computeIfAbsent(instance.getKey(), k -> new long[2]);
                total[0] += Math.max(0, instance.getValue().ms);
                total[1] += Math.max(0, instance.getValue().sessions);
                last.merge(instance.getKey(), day.getKey(), (a, b) -> a.compareTo(b) >= 0 ? a : b);
            }
        }
        List<InstanceTotal> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            LocalDate lastPlayed = null;
            try {
                lastPlayed = LocalDate.parse(last.get(entry.getKey()));
            } catch (RuntimeException ignored) {
            }
            result.add(new InstanceTotal(entry.getKey(), entry.getValue()[0], (int) entry.getValue()[1], lastPlayed));
        }
        result.sort(Comparator.comparingLong(InstanceTotal::ms).reversed().thenComparing(InstanceTotal::key));
        return result;
    }


    public static long total(Data data, LocalDate from) {
        long sum = 0;
        for (InstanceTotal total : byInstance(data, from)) sum += total.ms();
        return sum;
    }
}
