package by.agro.launcher.repair;

import by.agro.launcher.core.Json;
import by.agro.launcher.core.SecureFiles;
import by.agro.launcher.integrity.IntegrityService;
import by.agro.launcher.integrity.ManifestEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public final class RepairTransaction implements AutoCloseable {
    private final Path liveRoot;
    private final Path transactionRoot;
    private final Path stagedRoot;
    private final Path rollbackRoot;
    private final IntegrityService integrity;
    private final Journal journal = new Journal();
    private final List<ManifestEntry> staged = new ArrayList<>();
    private boolean committed;

    public RepairTransaction(Path liveRoot, Path transactionsRoot, IntegrityService integrity) throws IOException {
        this.liveRoot = liveRoot.toAbsolutePath().normalize();
        this.integrity = integrity;
        journal.transactionId = UUID.randomUUID().toString();
        transactionRoot = transactionsRoot.toAbsolutePath().normalize().resolve(journal.transactionId);
        stagedRoot = transactionRoot.resolve("stage");
        rollbackRoot = transactionRoot.resolve("rollback");
        Files.createDirectories(stagedRoot);
        Files.createDirectories(rollbackRoot);
        writeJournal();
    }

    public String id() { return journal.transactionId; }

    public Path stagePath(ManifestEntry entry) throws IOException {
        return integrity.resolve(stagedRoot, entry.path);
    }

    public void addStaged(ManifestEntry entry) throws IOException {
        if (!integrity.verify(stagedRoot, entry)) {
            throw new IOException("Staged SHA-256/size verification failed: " + entry.path);
        }
        staged.add(entry);
    }

    public void commit() throws IOException {
        journal.state = "COMMITTING";
        writeJournal();
        try {
            for (ManifestEntry entry : staged) {
                Path source = integrity.resolve(stagedRoot, entry.path);
                Path target = integrity.resolve(liveRoot, entry.path);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Managed target is not a regular file: " + target);
                    }
                    Path old = integrity.resolve(rollbackRoot, entry.path);
                    Files.createDirectories(old.getParent());
                    Files.copy(target, old, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                }
                Files.createDirectories(target.getParent());
                SecureFiles.atomicReplace(source, target);
                journal.replaced.add(entry.path);
                writeJournal();
            }
            journal.state = "COMMITTED";
            committed = true;
            writeJournal();
        } catch (IOException | RuntimeException failure) {
            try { rollback(); } catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            throw failure;
        }
    }

    public void rollback() throws IOException {
        journal.state = "ROLLING_BACK";
        writeJournal();
        IOException failure = null;
        for (int i = journal.replaced.size() - 1; i >= 0; i--) {
            String relative = journal.replaced.get(i);
            try {
                Path target = integrity.resolve(liveRoot, relative);
                Path old = integrity.resolve(rollbackRoot, relative);
                if (Files.isRegularFile(old, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(old)) {
                    Path temp = SecureFiles.createSiblingTemp(target, ".rollback");
                    Files.copy(old, temp, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                    SecureFiles.atomicReplace(temp, target);
                } else {
                    Files.deleteIfExists(target);
                }
            } catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        journal.state = failure == null ? "ROLLED_BACK" : "ROLLBACK_FAILED";
        writeJournal();
        if (failure != null) throw failure;
    }

    private void writeJournal() throws IOException { Json.write(transactionRoot.resolve("journal.json"), journal); }

    @Override public void close() throws IOException {
        if (!committed && !journal.replaced.isEmpty() && !"ROLLED_BACK".equals(journal.state)) rollback();
    }
}
