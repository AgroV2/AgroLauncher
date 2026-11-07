package by.agro.launcher.worlds;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;


final class LevelDat {

    private static final int MAX_DEPTH = 64;
    private static final int MAX_ARRAY = 16 * 1024 * 1024;

    private LevelDat() {
    }

    static String levelName(Path levelDat) {
        try (InputStream raw = Files.newInputStream(levelDat);
             DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw)))) {
            int type = in.readUnsignedByte();
            if (type != 10) return null;
            in.readUTF();
            return findInCompound(in, 0, false);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }


    private static String findInCompound(DataInputStream in, int depth, boolean insideData) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT too deep");
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) return null;
            String name = in.readUTF();
            if (type == 10 && depth == 0 && "Data".equals(name)) {
                return findInCompound(in, depth + 1, true);
            }
            if (type == 8 && insideData && "LevelName".equals(name)) {
                return in.readUTF();
            }
            skip(in, type, depth + 1);
        }
    }

    private static void skip(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT too deep");
        switch (type) {
            case 1 -> skipBytes(in, 1);
            case 2 -> skipBytes(in, 2);
            case 3, 5 -> skipBytes(in, 4);
            case 4, 6 -> skipBytes(in, 8);
            case 7 -> skipBytes(in, length(in));
            case 8 -> skipBytes(in, in.readUnsignedShort());
            case 9 -> {
                int element = in.readUnsignedByte();
                int count = length(in);
                for (int i = 0; i < count; i++) skip(in, element, depth + 1);
            }
            case 10 -> {
                while (true) {
                    int child = in.readUnsignedByte();
                    if (child == 0) break;
                    skipBytes(in, in.readUnsignedShort());
                    skip(in, child, depth + 1);
                }
            }
            case 11 -> skipBytes(in, (long) length(in) * 4);
            case 12 -> skipBytes(in, (long) length(in) * 8);
            default -> throw new IOException("Unknown NBT tag " + type);
        }
    }

    private static int length(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_ARRAY) throw new IOException("Bad NBT length");
        return length;
    }

    private static void skipBytes(DataInputStream in, long count) throws IOException {
        long remaining = count;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) throw new EOFException();
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}
