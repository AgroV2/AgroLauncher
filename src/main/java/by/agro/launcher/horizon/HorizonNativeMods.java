package by.agro.launcher.horizon;

import by.agro.launcher.core.DiskSpace;
import by.agro.launcher.core.FileTrees;
import by.agro.launcher.core.SecureFiles;
import by.agro.launcher.i18n.Strings;
import com.google.gson.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;


public final class HorizonNativeMods {
    private static final long MAX_ARCHIVE = 64L * 1024 * 1024;
    private final Path home, builder;
    private final java.util.concurrent.atomic.AtomicBoolean building = new java.util.concurrent.atomic.AtomicBoolean();
    public record Mod(Path file, String id, String version, boolean enabled) {
        @Override public String toString() { return (enabled ? "☑ " : "☐ ") + id + "  ·  " + version + "  ·  " + file.getFileName(); }
    }
    public HorizonNativeMods(Path gameDir, Path root) {
        home = gameDir.resolve("horizon"); builder = home.resolve(".agro-native-build");
    }
    public Path directory() { return home.resolve("horizon-native-mods"); }
    public Path builderDirectory() { return builder; }
    private Path flags() { return home.resolve("horizon-mods.txt"); }
    private static JsonObject json(String text) throws IOException {
        try { return JsonParser.parseString(text).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException(Strings.get("hnmod.invalid"), e); }
    }
    private static String field(JsonObject o, String key) throws IOException {
        try { return o.get(key).getAsString(); } catch (RuntimeException e) { throw new IOException(Strings.get("hnmod.invalid"), e); }
    }
    private Set<String> enabled() throws IOException {
        SecureFiles.rejectSymlinkParents(flags());
        Set<String> out = new HashSet<>();
        if (!Files.exists(flags(), LinkOption.NOFOLLOW_LINKS)) return out;
        if (Files.size(flags()) > 2 * 1024 * 1024) throw new IOException(Strings.get("hnmod.invalid"));
        for (String line : Files.readAllLines(flags(), StandardCharsets.UTF_8)) {
            int colon = line.indexOf(':');
            if (colon >= 0 && line.substring(0, colon).trim().startsWith("mod.") && line.substring(colon + 1).trim().equals("true"))
                out.add(line.substring(0, colon).trim().substring(4));
        }
        return out;
    }
    private static void atomicText(Path dest, String text) throws IOException {
        SecureFiles.rejectSymlinkParents(dest);
        Path tmp = SecureFiles.createSiblingTemp(dest, ".tmp");
        try { Files.writeString(tmp, text, StandardCharsets.UTF_8); SecureFiles.atomicReplace(tmp, dest); }
        finally { Files.deleteIfExists(tmp); }
    }

    static Mod inspect(Path file, Set<String> enabled) throws IOException {
        SecureFiles.rejectSymlinkParents(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_ARCHIVE)
            throw new IOException(Strings.get("hnmod.invalid"));
        try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
            Set<String> names = new HashSet<>(); long size = 0; int count = 0;
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements();) {
                ZipEntry z = e.nextElement(); String name = z.getName();
                if (z.isDirectory()) continue;
                if (++count > 512 || !names.add(name) || name.startsWith("/") || name.contains("\\") || name.contains(":")
                        || Arrays.stream(name.split("/", -1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals(".."))
                        || (z.getMethod() != ZipEntry.STORED && z.getMethod() != ZipEntry.DEFLATED)
                        || z.getSize() < 0 || z.getSize() > MAX_ARCHIVE - size)
                    throw new IOException(Strings.get("hnmod.invalid"));
                size += z.getSize();
            }
            ZipEntry entry = zip.getEntry("horizon-native.json");
            if (entry == null || entry.getSize() > 65536) throw new IOException(Strings.get("hnmod.invalid"));
            JsonObject m;
            try (InputStream in = zip.getInputStream(entry)) { m = json(new String(in.readNBytes(65537), StandardCharsets.UTF_8)); }
            String id = field(m, "id"), version = field(m, "version"), game = field(m, "game");
            if (!field(m, "schema").equals("1") || !id.matches("[a-z0-9_]{1,64}") || version.isBlank()
                    || !game.equals(HorizonManager.MINECRAFT_VERSION) || !field(m,"horizon").matches("[0-9a-f]{40}")
                    || !field(m,"mixins").matches("[0-9a-f]{40}") || !names.contains("mixins/" + id + "/horizon-mixin.toml"))
                throw new IOException(Strings.get("hnmod.invalid"));
            return new Mod(file, id, version, enabled.contains(id));
        } catch (ZipException e) { throw new IOException(Strings.get("hnmod.invalid"), e); }
    }
    public synchronized List<Mod> list() throws IOException {
        SecureFiles.rejectSymlinkParents(directory());
        if (!Files.exists(directory(), LinkOption.NOFOLLOW_LINKS)) return List.of();
        Set<String> on = enabled(), ids = new HashSet<>(); List<Mod> mods = new ArrayList<>();
        try (var files = Files.list(directory())) {
            for (Path p : files.filter(p -> p.getFileName().toString().endsWith(".hnmod")).sorted().toList()) {
                Mod m = inspect(p, on);
                if (!ids.add(m.id())) throw new IOException(Strings.get("hnmod.duplicate", m.id()));
                mods.add(m);
            }
        }
        return mods;
    }
    public synchronized void add(Path source) throws IOException {
        Mod m = inspect(source, Set.of());
        if (list().stream().anyMatch(v -> v.id().equals(m.id()))) throw new IOException(Strings.get("hnmod.duplicate", m.id()));
        Files.createDirectories(directory()); Path dest = directory().resolve(m.id() + ".hnmod");
        SecureFiles.rejectSymlinkParents(dest);
        if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) throw new IOException(Strings.get("hnmod.duplicate", m.id()));
        Path temp = SecureFiles.createSiblingTemp(dest, ".tmp");
        try { Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING); inspect(temp, Set.of()); Files.move(temp, dest); }
        finally { Files.deleteIfExists(temp); }
        try { setEnabled(m.id(), true); } catch (IOException e) { Files.deleteIfExists(dest); throw e; }
    }
    public synchronized void setEnabled(String id, boolean value) throws IOException {
        if (!id.matches("[a-z0-9_]{1,64}")) throw new IOException(Strings.get("hnmod.invalid"));
        SecureFiles.rejectSymlinkParents(flags());
        List<String> lines = Files.exists(flags()) ? Files.readAllLines(flags(), StandardCharsets.UTF_8) : new ArrayList<>();
        lines.removeIf(l -> { int i = l.indexOf(':'); return i >= 0 && l.substring(0, i).trim().equals("mod." + id); });
        if (value) lines.add("mod." + id + ":true");
        atomicText(flags(), lines.isEmpty() ? "" : String.join("\n", lines) + "\n");
    }
    public synchronized void remove(Mod mod) throws IOException {
        if (list().stream().noneMatch(m -> m.file().equals(mod.file()) && m.id().equals(mod.id()))) throw new IOException(Strings.get("hnmod.invalid"));
        setEnabled(mod.id(), false); Files.delete(mod.file());
    }
    private static String sha256(Path p) throws IOException {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(p)) { byte[] buf = new byte[32768]; int n; while ((n = in.read(buf)) != -1) d.update(buf, 0, n); }
            return HexFormat.of().formatHex(d.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private String snapshot(List<Mod> mods) throws IOException {
        StringBuilder b = new StringBuilder();
        for (Mod m : mods) b.append(m.file().getFileName()).append(':').append(m.id()).append(':')
                .append(m.enabled()).append(':').append(sha256(m.file())).append('\n');
        return b.toString();
    }

    public synchronized Path builtClient() throws IOException {
        if (building.get()) throw new IOException(Strings.get("hnmod.building"));
        List<Mod> mods = list();
        if (mods.stream().noneMatch(Mod::enabled)) return null;
        Path receipt = builder.resolve("agro-native-build.json"); SecureFiles.rejectSymlinkParents(receipt);
        if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS) || Files.size(receipt) > 1024 * 1024)
            throw new IOException(Strings.get("hnmod.needsBuild"));
        JsonObject r = json(Files.readString(receipt));
        String version = field(r, "version");
        if (!version.matches("native-[A-Za-z0-9._-]{1,150}") || !field(r, "snapshot").equals(snapshot(mods)))
            throw new IOException(Strings.get("hnmod.needsBuild"));
        Path dir = builder.resolve("versions").resolve(version); SecureFiles.rejectSymlinkParents(dir);
        SecureFiles.rejectSymlinkParents(HorizonManager.resources(dir).resolve("horizon-resources.json"));
        String exe = "horizon-client" + (by.agro.launcher.core.Platform.isWindows() ? ".exe" : "");
        if (!Files.isRegularFile(dir.resolve(exe), LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(dir.resolve(".installed"), LinkOption.NOFOLLOW_LINKS)
                || !Files.isRegularFile(HorizonManager.resources(dir).resolve("horizon-resources.json"), LinkOption.NOFOLLOW_LINKS)
                || !field(r, "binarySha256").equals(sha256(dir.resolve(exe)))) throw new IOException(Strings.get("hnmod.needsBuild"));
        return dir;
    }

    public void buildOfficial(Path launcher, Path base, Consumer<String> output) throws IOException {
        if (!building.compareAndSet(false, true)) throw new IOException(Strings.get("hnmod.building"));
        try {
            SecureFiles.rejectSymlinkParents(builder); SecureFiles.rejectSymlinkParents(base);
            Files.createDirectories(builder);
            Path lock = builder.resolve("build.lock"); SecureFiles.rejectSymlinkParents(lock);
            try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                java.nio.channels.FileLock held;
                try { held = channel.tryLock(); } catch (java.nio.channels.OverlappingFileLockException e) { held = null; }
                if (held == null) throw new IOException(Strings.get("hnmod.building"));
                try (java.nio.channels.FileLock ignored = held) {
                    List<Mod> mods = list();
                    if (mods.stream().noneMatch(Mod::enabled)) throw new IOException(Strings.get("hnmod.noEnabled"));
                    String before = snapshot(mods);
                    Path versions = builder.resolve("versions"); Files.createDirectories(versions);
                    Path dest = versions.resolve("nightly");
                    if (Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) throw new IOException(Strings.get("hnmod.baseInvalid"));
                    DiskSpace.require(versions, DiskSpace.directorySize(base));
                    Path staging = Files.createTempDirectory(versions, ".base-");
                    try {
                        FileTrees.copy(base, staging, null);
                        Files.writeString(staging.resolve(".installed"), "");
                        Files.move(staging, dest);
                    } finally { FileTrees.deleteQuietly(staging); }
                    ProcessBuilder pb = new ProcessBuilder(launcher.toAbsolutePath().toString(), "native-build", "--instance", "horizon", "--version", "nightly", "--trust-native-code");
                    pb.directory(builder.toFile()).redirectErrorStream(true);
                    pb.environment().put("HORIZON_LAUNCHER_HOME", builder.toAbsolutePath().toString());
                    pb.environment().put("HORIZON_HOME", home.toAbsolutePath().toString());
                    pb.environment().put("HORIZON_LAUNCHER_NO_SELF_UPDATE", "1");
                    Process process = pb.start(); process.getOutputStream().close();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line; while ((line = reader.readLine()) != null) output.accept(line.length() > 1000 ? line.substring(0, 1000) : line);
                        int status = process.waitFor(); if (status != 0) throw new IOException(Strings.get("hnmod.buildFailed", status));
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(Strings.get("horizon.interrupted"), e); }
                    finally { if (process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); } }
                    if (!before.equals(snapshot(list()))) throw new IOException(Strings.get("hnmod.changedDuringBuild"));
                    Path instances = builder.resolve("instances.json"); SecureFiles.rejectSymlinkParents(instances);
                    JsonObject store = json(Files.readString(instances)); String version;
                    try { version = store.getAsJsonObject("instances").getAsJsonObject("horizon").get("version").getAsString(); }
                    catch (RuntimeException e) { throw new IOException(Strings.get("hnmod.buildMissing"), e); }
                    if (!version.matches("native-[A-Za-z0-9._-]{1,150}")) throw new IOException(Strings.get("hnmod.buildMissing"));
                    Path dir = versions.resolve(version); SecureFiles.rejectSymlinkParents(dir);
                    String exe = "horizon-client" + (by.agro.launcher.core.Platform.isWindows() ? ".exe" : "");
                    Path recordPath = dir.resolve("native-mods.json"); SecureFiles.rejectSymlinkParents(recordPath);
                    if (!Files.isRegularFile(dir.resolve(".installed"), LinkOption.NOFOLLOW_LINKS)
                            || !Files.isRegularFile(recordPath, LinkOption.NOFOLLOW_LINKS) || Files.size(recordPath) > 1024*1024
                            || !Files.isRegularFile(dir.resolve(exe), LinkOption.NOFOLLOW_LINKS)
                            || !Files.isRegularFile(HorizonManager.resources(dir).resolve("horizon-resources.json"), LinkOption.NOFOLLOW_LINKS))
                        throw new IOException(Strings.get("hnmod.buildMissing"));
                    JsonObject record = json(Files.readString(recordPath));
                    try {
                        Map<String,String> expected = new HashMap<>(), actual = new HashMap<>();
                        for (Mod mod : mods) if (mod.enabled()) expected.put(mod.id(), mod.version());
                        for (JsonElement element : record.getAsJsonArray("mods")) {
                            JsonObject obj = element.getAsJsonObject();
                            if (actual.put(field(obj, "id"), field(obj, "version")) != null) throw new IOException(Strings.get("hnmod.omitted"));
                        }
                        if (!expected.equals(actual) || (record.has("target") && !field(record,"target").equals("client"))
                                || (record.has("left_out") && record.getAsJsonArray("left_out").size() > 0)) throw new IOException(Strings.get("hnmod.omitted"));
                        String rev = field(record, "horizon");
                        if (!rev.matches("[0-9a-f]{40}")) throw new IOException(Strings.get("hnmod.buildMissing"));
                        Path source = builder.resolve("native/source").resolve(rev);
                        SecureFiles.rejectSymlinkParents(source);
                        if (!HorizonManager.readPins(source).equals(HorizonManager.readPins(base))) throw new IOException(Strings.get("hnmod.resourceMismatch"));
                    } catch (RuntimeException e) { throw new IOException(Strings.get("hnmod.buildMissing"), e); }
                    JsonObject receipt = new JsonObject(); receipt.addProperty("version", version); receipt.addProperty("snapshot", before);
                    receipt.addProperty("binarySha256", sha256(dir.resolve(exe))); atomicText(builder.resolve("agro-native-build.json"), receipt.toString());
                }
            }
        } finally { building.set(false); }
    }
}
