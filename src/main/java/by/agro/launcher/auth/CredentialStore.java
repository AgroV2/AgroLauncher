package by.agro.launcher.auth;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public interface CredentialStore {
    String SERVICE = "AgroLauncher";

    void put(String accountId, String secret) throws Exception;
    String get(String accountId) throws Exception;
    void remove(String accountId) throws Exception;

    static CredentialStore system() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return new WindowsStore();
        if (os.contains("mac")) return new MacStore();
        if (os.contains("linux")) return new LinuxStore();
        return new UnsupportedStore(os);
    }

    final class WindowsStore implements CredentialStore {
        private static final int CRED_TYPE_GENERIC = 1;
        private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
        private static final int ERROR_NOT_FOUND = 1168;

        private interface Advapi32 extends Library {
            Advapi32 INSTANCE = Native.load("Advapi32", Advapi32.class);
            boolean CredWriteW(CREDENTIAL credential, int flags);
            boolean CredReadW(WString target, int type, int flags, PointerByReference credential);
            boolean CredDeleteW(WString target, int type, int flags);
            void CredFree(Pointer buffer);
        }

        @Structure.FieldOrder({"Flags", "Type", "TargetName", "Comment", "LastWritten", "CredentialBlobSize",
                "CredentialBlob", "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName"})
        public static final class CREDENTIAL extends Structure {
            public int Flags;
            public int Type;
            public WString TargetName;
            public WString Comment;
            public long LastWritten;
            public int CredentialBlobSize;
            public Pointer CredentialBlob;
            public int Persist;
            public int AttributeCount;
            public Pointer Attributes;
            public WString TargetAlias;
            public WString UserName;

            public CREDENTIAL() { }
            public CREDENTIAL(Pointer pointer) { super(pointer); read(); }
        }

        private String target(String id) { return SERVICE + "/" + id; }
        private IOException failure(String operation) {
            return new IOException(operation + " failed with Windows error " + Native.getLastError());
        }

        @Override
        public void put(String id, String secret) throws Exception {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_16LE);
            Memory blob = new Memory(Math.max(1, bytes.length));
            if (bytes.length > 0) blob.write(0, bytes, 0, bytes.length);
            try {
                CREDENTIAL credential = new CREDENTIAL();
                credential.Type = CRED_TYPE_GENERIC;
                credential.TargetName = new WString(target(id));
                credential.CredentialBlobSize = bytes.length;
                credential.CredentialBlob = blob;
                credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
                credential.UserName = new WString(id);
                credential.write();
                if (!Advapi32.INSTANCE.CredWriteW(credential, 0)) throw failure("CredWriteW");
            } finally {
                blob.clear();
            }
        }

        @Override
        public String get(String id) throws Exception {
            PointerByReference reference = new PointerByReference();
            if (!Advapi32.INSTANCE.CredReadW(new WString(target(id)), CRED_TYPE_GENERIC, 0, reference)) {
                if (Native.getLastError() == ERROR_NOT_FOUND) return null;
                throw failure("CredReadW");
            }
            Pointer pointer = reference.getValue();
            try {
                CREDENTIAL credential = new CREDENTIAL(pointer);
                if (credential.CredentialBlob == null || credential.CredentialBlobSize == 0) return "";
                byte[] bytes = credential.CredentialBlob.getByteArray(0, credential.CredentialBlobSize);
                try {
                    return new String(bytes, StandardCharsets.UTF_16LE);
                } finally {
                    java.util.Arrays.fill(bytes, (byte) 0);
                }
            } finally {
                Advapi32.INSTANCE.CredFree(pointer);
            }
        }

        @Override
        public void remove(String id) throws Exception {
            if (!Advapi32.INSTANCE.CredDeleteW(new WString(target(id)), CRED_TYPE_GENERIC, 0)
                    && Native.getLastError() != ERROR_NOT_FOUND) {
                throw failure("CredDeleteW");
            }
        }
    }

    final class MacStore implements CredentialStore {
        private IOException unavailable() {
            return new IOException("macOS Keychain storage is disabled: no backend is configured that can write secrets without exposing them in process arguments");
        }

        @Override
        public void put(String id, String secret) throws Exception { throw unavailable(); }

        @Override
        public String get(String id) throws Exception { throw unavailable(); }

        @Override
        public void remove(String id) throws Exception { throw unavailable(); }
    }

    final class LinuxStore implements CredentialStore {
        @Override
        public void put(String id, String secret) throws Exception {
            run(List.of("secret-tool", "store", "--label=" + SERVICE + " " + id,
                    "service", SERVICE, "account", id), secret);
        }

        @Override
        public String get(String id) throws Exception {
            Result result = runAllowMissing(List.of("secret-tool", "lookup", "service", SERVICE, "account", id));
            if (result.exitCode() == 0) return result.output().stripTrailing();
            if (result.exitCode() == 1) return null;
            throw new IOException("Secret Service lookup failed (exit " + result.exitCode() + ")");
        }

        @Override
        public void remove(String id) throws Exception {
            Result result = runAllowMissing(List.of("secret-tool", "clear", "service", SERVICE, "account", id));
            if (result.exitCode() != 0 && result.exitCode() != 1) {
                throw new IOException("Secret Service delete failed (exit " + result.exitCode() + ")");
            }
        }
    }

    final class UnsupportedStore implements CredentialStore {
        private final String os;
        UnsupportedStore(String os) { this.os = os; }
        private IOException unavailable() {
            return new IOException("No supported system credential backend is available for " + os);
        }
        public void put(String id, String secret) throws Exception { throw unavailable(); }
        public String get(String id) throws Exception { throw unavailable(); }
        public void remove(String id) throws Exception { throw unavailable(); }
    }

    private static Result run(List<String> command, String stdin, String... environment) throws Exception {
        Result result = execute(command, stdin, environment);
        if (result.exitCode() != 0) {
            throw new IOException("System credential backend failed (exit " + result.exitCode() + ")");
        }
        return result;
    }

    private static Result runAllowMissing(List<String> command) throws Exception {
        return execute(command, null);
    }

    private static Result execute(List<String> command, String stdin, String... environment) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(command));
        builder.redirectErrorStream(true);
        for (int i = 0; i + 1 < environment.length; i += 2) {
            builder.environment().put(environment[i], environment[i + 1]);
        }
        final Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("System credential backend is unavailable: " + command.get(0), e);
        }
        try (OutputStream output = process.getOutputStream()) {
            if (stdin != null) output.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (InputStream input = process.getInputStream()) {
            input.transferTo(captured);
        }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("System credential backend timed out");
        }
        return new Result(process.exitValue(), captured.toString(StandardCharsets.UTF_8));
    }

    record Result(int exitCode, String output) { }
}
