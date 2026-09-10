package by.agro.launcher.diagnostics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class DiagnosticReport {
    public String sessionId;
    public Instant startedAt;
    public Instant finishedAt;
    public String minecraftVersion;
    public String loader;
    public Integer javaMajor;
    public String javaSource;
    public int ramMb;
    public Integer exitCode;
    public String command;
    public List<DiagnosticFinding> findings = new ArrayList<>();
    public List<String> log = new ArrayList<>();
}
