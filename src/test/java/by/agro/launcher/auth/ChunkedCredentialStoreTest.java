package by.agro.launcher.auth;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ChunkedCredentialStoreTest {
    static class Fake implements CredentialStore {
        final Map<String,String> entries = new HashMap<>(); boolean failHeader;
        public void put(String id,String value) throws Exception { if (failHeader && id.equals("a")) throw new IOException("write failed"); entries.put(id,value); }
        public String get(String id) { return entries.get(id); }
        public void remove(String id) { entries.remove(id); }
    }
    @Test void supportsLegacyAndLargeRotatedSecrets() throws Exception {
        Fake raw = new Fake(); raw.entries.put("a", "old");
        var s = new CredentialStore.ChunkedStore(raw); assertEquals("old", s.get("a"));
        String value = "x".repeat(899) + "😀" + "y".repeat(4000);
        s.put("a", value); assertEquals(value, s.get("a"));
        s.put("a", "short"); assertEquals("short", s.get("a")); assertEquals(1,raw.entries.size());
        s.remove("a"); assertTrue(raw.entries.isEmpty());
    }
    @Test void preservesPriorTokenOnFailedCommit() throws Exception {
        Fake raw = new Fake(); var s = new CredentialStore.ChunkedStore(raw);
        s.put("a", "old".repeat(1000)); Map<String,String> before = new HashMap<>(raw.entries);
        raw.failHeader = true; assertThrows(IOException.class, () -> s.put("a", "new".repeat(1000)));
        assertEquals(before, raw.entries); assertEquals("old".repeat(1000), s.get("a"));
    }
    @Test void rejectsMissingOrInvalidChunks() throws Exception {
        Fake raw = new Fake(); var s = new CredentialStore.ChunkedStore(raw);
        s.put("a", "x".repeat(3000)); raw.entries.keySet().removeIf(k -> k.endsWith("/0"));
        assertThrows(IOException.class, () -> s.get("a"));
        raw.entries.put("a", "agro-chunks-v1:garbage:2"); assertThrows(IOException.class, () -> s.get("a"));
    }
}
