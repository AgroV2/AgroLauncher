package by.agro.launcher.jvm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class ArchiveExtractorLongNamesTest {
    @TempDir Path temp;

    @Test void extractsGnuLongNameForExactlyOneEntry() throws Exception {
        String name = "horizon/" + "long-segment/".repeat(12) + "file.txt";
        Path archive = archive(new Entry("././@LongLink", 'L', (name + "\0").getBytes(StandardCharsets.UTF_8)),
                new Entry("short", '0', "hello".getBytes(StandardCharsets.UTF_8)),
                new Entry("next.txt", '0', new byte[]{42}));
        Path out = temp.resolve("out"); ArchiveExtractor.untarGz(archive, out);
        assertEquals("hello", Files.readString(out.resolve(name)));
        assertEquals(42, Files.readAllBytes(out.resolve("next.txt"))[0]);
    }

    @Test void extractsPaxLongPathAndParsesUtf8() throws Exception {
        String unicode = "horizon/длинное-имя/файл.txt";
        assertEquals(unicode, ArchiveExtractor.parsePaxPath(pax("path=" + unicode)));
        String name = "horizon/" + "long-segment/".repeat(12) + "file.txt";
        Path archive = archive(new Entry("PaxHeader", 'x', pax("path=" + name)),
                new Entry("short", '0', new byte[]{7}));
        Path out = temp.resolve("out"); ArchiveExtractor.untarGz(archive, out);
        assertEquals(7, Files.readAllBytes(out.resolve(name))[0]);
    }

    @Test void refusesTraversalFromBothLongNameFormats() throws Exception {
        for (char type : new char[]{'L', 'x'}) {
            byte[] metadata = type == 'L' ? "../escape\0".getBytes(StandardCharsets.UTF_8) : pax("path=../escape");
            Path archive = archive(new Entry("metadata", type, metadata), new Entry("safe", '0', new byte[]{1}));
            assertThrows(IOException.class, () -> ArchiveExtractor.untarGz(archive, temp.resolve("out-" + type)));
            assertFalse(Files.exists(temp.resolve("escape")));
        }
    }

    @Test void rejectsMalformedPaxLengthsAndTerminators() {
        assertThrows(IOException.class, () -> ArchiveExtractor.parsePaxPath("2147483647 path=x\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> ArchiveExtractor.parsePaxPath("NaN path=x\n".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IOException.class, () -> ArchiveExtractor.parsePaxPath("12 path=abc!".getBytes(StandardCharsets.UTF_8)));
        assertNull(assertDoesNotThrow(() -> ArchiveExtractor.parsePaxPath(pax("mtime=123"))));
    }

    private static byte[] pax(String record) {
        int length = record.getBytes(StandardCharsets.UTF_8).length + 3;
        while (true) {
            byte[] bytes = (length + " " + record + "\n").getBytes(StandardCharsets.UTF_8);
            if (bytes.length == length) return bytes;
            length = bytes.length;
        }
    }

    private Path archive(Entry... entries) throws IOException {
        Path file = Files.createTempFile(temp, "archive-", ".tar.gz");
        try (var out = new GZIPOutputStream(Files.newOutputStream(file))) {
            for (Entry entry : entries) {
                byte[] header = new byte[512];
                put(header, 0, entry.name);
                put(header, 100, "0000644");
                put(header, 124, String.format("%011o", entry.bytes.length));
                Arrays.fill(header, 148, 156, (byte) ' ');
                header[156] = (byte) entry.type;
                put(header, 257, "ustar");
                int checksum = 0; for (byte b : header) checksum += b & 255;
                put(header, 148, String.format("%06o", checksum));
                out.write(header); out.write(entry.bytes);
                out.write(new byte[(512 - entry.bytes.length % 512) % 512]);
            }
            out.write(new byte[1024]);
        }
        return file;
    }
    private void put(byte[] bytes, int offset, String text) {
        byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(encoded, 0, bytes, offset, encoded.length);
    }
    private record Entry(String name, char type, byte[] bytes) {}
}
