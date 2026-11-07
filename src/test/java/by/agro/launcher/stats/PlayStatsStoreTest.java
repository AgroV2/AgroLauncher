package by.agro.launcher.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayStatsStoreTest {

    @TempDir
    Path root;

    @Test
    void splitsSessionsAtMidnightAndAggregatesPerInstance() {
        ZoneId zone = ZoneId.of("Europe/Minsk");
        PlayStatsStore store = new PlayStatsStore(root, root.resolve("stats/s.json"), zone);
        Path a = root.resolve("instances/installations/1.20.1/fabric/recommended");
        Path b = root.resolve("mod-builds/x/game");
        long start = ZonedDateTime.of(2026, 3, 1, 23, 30, 0, 0, zone).toInstant().toEpochMilli();
        store.record(a, start, start + 60 * 60_000L);
        long second = ZonedDateTime.of(2026, 3, 2, 12, 0, 0, 0, zone).toInstant().toEpochMilli();
        store.record(b, second, second + 15 * 60_000L);

        var data = store.load();
        var days = PlayStatsStore.byDay(data, LocalDate.of(2026, 3, 2), 3);
        assertEquals(3, days.size());
        assertEquals(0, days.get(0).ms());
        assertEquals(30 * 60_000L, days.get(1).ms());
        assertEquals(1, days.get(1).sessions());
        assertEquals(45 * 60_000L, days.get(2).ms());
        assertEquals(1, days.get(2).sessions());

        var instances = PlayStatsStore.byInstance(data, null);
        assertEquals(2, instances.size());
        assertEquals("instances/installations/1.20.1/fabric/recommended", instances.get(0).key());
        assertEquals(60 * 60_000L, instances.get(0).ms());
        assertEquals(LocalDate.of(2026, 3, 2), instances.get(0).lastPlayed());
        assertEquals(a.toAbsolutePath().normalize(), store.pathFor(instances.get(0).key()));

        assertEquals(15 * 60_000L, PlayStatsStore.total(data, LocalDate.of(2026, 3, 2)) - 30 * 60_000L);
    }
}
