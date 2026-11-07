package by.agro.launcher.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;

class VerificationCacheTest {
    @TempDir Path temp;
    @Test void changedOrDeletedFileNeverHitsSessionCache() throws Exception {
        Path p = temp.resolve("asset.dat"); Files.writeString(p, "original");
        String sha = HashUtil.sha1(p); Downloader d = new Downloader();
        assertTrue(d.verifyOnce(p, sha)); Files.writeString(p, "different value");
        assertFalse(d.verifyOnce(p, sha)); Files.delete(p); assertFalse(d.verifyOnce(p, sha));
    }
    @Test void executableIsRehashedEvenWhenSizeAndTimestampMatch() throws Exception {
        Path p = temp.resolve("client.jar"); Files.writeString(p, "good");
        String sha = HashUtil.sha1(p); var mtime = Files.getLastModifiedTime(p); Downloader d = new Downloader();
        assertTrue(d.verifyOnce(p, sha)); Files.writeString(p, "evil"); Files.setLastModifiedTime(p, mtime);
        assertFalse(d.verifyOnce(p, sha));
    }
    @Test void oldCacheRecordsWithoutIdentityAreNotAccepted() throws Exception {
        Path p = temp.resolve("asset.dat"); Files.writeString(p, "data");
        var attrs = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
        var old = new Downloader.PersistentEntry(); old.size=attrs.size(); old.mtime=attrs.lastModifiedTime().toMillis(); old.sha1=HashUtil.sha1(p);
        assertFalse(old.matches(attrs, old.sha1));
    }
    @Test void identifiesNativeAndJavaExecutables() {
        assertTrue(Downloader.isExecutableArtifact(Path.of("CLIENT.JAR")));
        assertTrue(Downloader.isExecutableArtifact(Path.of("libthing.so.2")));
        assertFalse(Downloader.isExecutableArtifact(Path.of("asset.json")));
    }
}
