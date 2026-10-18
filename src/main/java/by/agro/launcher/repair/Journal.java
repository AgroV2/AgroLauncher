package by.agro.launcher.repair;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Journal {
    public int schemaVersion = 1;
    public String transactionId;
    public String state = "STAGING";
    public String createdAt = Instant.now().toString();
    public List<String> replaced = new ArrayList<>();
}
