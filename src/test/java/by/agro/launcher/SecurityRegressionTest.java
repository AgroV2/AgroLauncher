package by.agro.launcher;

import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.jvm.ArchiveExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityRegressionTest {

    @TempDir
    Path tempDir;
@Test
void zipSlipEntryCannotEscapeTargetDirectory() throws IOException {
    Path archive = tempDir.resolve("malicious.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
        zip.putNextEntry(new ZipEntry("../escaped.txt"));
        zip.write("bad".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    Path target = tempDir.resolve("target");

    assertThrows(IOException.class, () -> ArchiveExtractor.unzip(archive, target));
    assertFalse(Files.exists(tempDir.resolve("escaped.txt")));
}

    @Test
    void unsafeVersionIdentifiersAreRejected() {
        LauncherPaths paths = LauncherPaths.forRoot(tempDir.resolve("data"));
        assertThrows(IllegalArgumentException.class, () -> paths.versionDir("../outside"));
        assertThrows(IllegalArgumentException.class, () -> paths.versionDir("/tmp/outside"));
    }

    @Test
    void unsafeMavenCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> LauncherPaths.mavenToRelativePath("../evil:artifact:1.0"));
        assertThrows(IllegalArgumentException.class,
                () -> LauncherPaths.mavenToRelativePath("group:artifact:../../evil"));
    }
}
