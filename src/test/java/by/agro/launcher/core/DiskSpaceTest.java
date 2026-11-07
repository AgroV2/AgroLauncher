package by.agro.launcher.core;

import by.agro.launcher.i18n.Language;
import by.agro.launcher.i18n.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskSpaceTest {

    @TempDir
    Path temp;

    @Test
    void requireUsesNearestExistingParentAndReserve() {
        Path missing = temp.resolve("a/b/c/file.bin");
        assertDoesNotThrow(() -> DiskSpace.require(missing, 1024));
        assertTrue(DiskSpace.usableBytes(missing) > 0);
        assertThrows(DiskSpace.InsufficientSpaceException.class,
                () -> DiskSpace.require(missing, Long.MAX_VALUE / 2));
        assertThrows(DiskSpace.InsufficientSpaceException.class,
                () -> DiskSpace.requireAll(Map.of(temp.resolve("x"), Long.MAX_VALUE / 4,
                        temp.resolve("y"), Long.MAX_VALUE / 4)));
    }

    @Test
    void downloaderRejectsTasksThatDoNotFit() throws IOException {
        Path target = temp.resolve("lib.jar");
        Files.writeString(temp.resolve("lib.jar.partial"), "12345");
        assertEquals(95, Downloader.remainingBytes(new Downloader.Task("https://x", target, null, 100)));
        assertEquals(195, Downloader.ensureSpaceFor(List.of(new Downloader.Task("https://x", target, null, 100),
                new Downloader.Task("https://x", temp.resolve("b"), null, 100))));
        assertThrows(DiskSpace.InsufficientSpaceException.class, () -> Downloader.ensureSpaceFor(
                List.of(new Downloader.Task("https://x", temp.resolve("huge"), null, Long.MAX_VALUE / 2))));
    }

    @Test
    void sizesAndFormatting() throws IOException {
        Files.createDirectories(temp.resolve("d/e"));
        Files.write(temp.resolve("d/e/f"), new byte[2048]);
        Files.write(temp.resolve("d/g"), new byte[1024]);
        assertEquals(3072, DiskSpace.directorySize(temp.resolve("d")));
        Language before = Strings.language();
        try {
            Strings.setLanguage(Language.ENGLISH);
            assertEquals("512 B", DiskSpace.format(512));
            assertEquals("1.5 KB", DiskSpace.format(1536));
            assertEquals("2.0 GB", DiskSpace.format(2L * 1024 * 1024 * 1024));
        } finally {
            Strings.setLanguage(before);
        }
    }
}
