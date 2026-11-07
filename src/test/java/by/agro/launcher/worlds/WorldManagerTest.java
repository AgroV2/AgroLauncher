package by.agro.launcher.worlds;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldManagerTest {

    @TempDir
    Path temp;

    static byte[] levelDat(String name) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            out.writeByte(10);
            out.writeUTF("Data");
            out.writeByte(3);
            out.writeUTF("version");
            out.writeInt(19133);
            out.writeByte(9);
            out.writeUTF("ServerBrands");
            out.writeByte(8);
            out.writeInt(1);
            out.writeUTF("fabric");
            out.writeByte(10);
            out.writeUTF("GameRules");
            out.writeByte(8);
            out.writeUTF("doDaylightCycle");
            out.writeUTF("true");
            out.writeByte(0);
            out.writeByte(8);
            out.writeUTF("LevelName");
            out.writeUTF(name);
            out.writeByte(0);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private Path world(Path saves, String folder, String name) throws IOException {
        Path world = saves.resolve(folder);
        Files.createDirectories(world.resolve("region"));
        Files.write(world.resolve("level.dat"), levelDat(name));
        Files.writeString(world.resolve("region/r.0.0.mca"), "chunk");
        Files.writeString(world.resolve("session.lock"), "lock");
        return world;
    }

    @Test
    void listsDuplicatesExportsAndImportsWorlds() throws IOException {
        Path saves = temp.resolve("saves");
        Path world = world(saves, "New World", "Мой мир");
        Files.createDirectories(saves.resolve("not-a-world"));
        WorldManager manager = new WorldManager(saves);

        var list = manager.list();
        assertEquals(1, list.size());
        assertEquals("Мой мир", list.get(0).displayName());
        assertTrue(list.get(0).sizeBytes() > 0);

        Path copy = manager.duplicate(world);
        assertTrue(WorldManager.isWorld(copy));
        assertFalse(Files.exists(copy.resolve("session.lock")));
        assertEquals("chunk", Files.readString(copy.resolve("region/r.0.0.mca")));

        Path zip = temp.resolve("export/world.zip");
        manager.exportZip(world, zip);
        assertTrue(Files.size(zip) > 0);

        WorldManager other = new WorldManager(temp.resolve("other/saves"));
        Path imported = other.importWorld(zip);
        assertEquals("New World", imported.getFileName().toString());
        assertEquals("chunk", Files.readString(imported.resolve("region/r.0.0.mca")));
        Path second = other.importWorld(zip);
        assertEquals("New World (2)", second.getFileName().toString());

        Path folderImport = other.importWorld(copy);
        assertTrue(WorldManager.isWorld(folderImport));

        manager.delete(copy);
        assertFalse(Files.exists(copy));
    }

    @Test
    void importsArchiveWithLevelDatAtRootAndRejectsTraversal() throws IOException {
        Path rootZip = temp.resolve("Flat.zip");
        try (OutputStream out = Files.newOutputStream(rootZip); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("level.dat"));
            zip.write(levelDat("Flat"));
            zip.closeEntry();
        }
        WorldManager manager = new WorldManager(temp.resolve("saves"));
        assertEquals("Flat", manager.importWorld(rootZip).getFileName().toString());

        Path evil = temp.resolve("evil.zip");
        try (OutputStream out = Files.newOutputStream(evil); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("w/level.dat"));
            zip.write(levelDat("w"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("w/../../escape.txt"));
            zip.write(1);
            zip.closeEntry();
        }
        assertThrows(IOException.class, () -> manager.importWorld(evil));
        assertFalse(Files.exists(temp.resolve("escape.txt")));

        Path empty = temp.resolve("empty.zip");
        try (OutputStream out = Files.newOutputStream(empty); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.closeEntry();
        }
        assertThrows(IOException.class, () -> manager.importWorld(empty));
    }

    @Test
    void refusesWorldsOutsideSaves() throws IOException {
        Path outside = world(temp.resolve("elsewhere"), "W", "W");
        WorldManager manager = new WorldManager(temp.resolve("saves"));
        assertThrows(IOException.class, () -> manager.duplicate(outside));
        assertThrows(IOException.class, () -> manager.delete(outside));
    }
}
