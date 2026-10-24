package by.agro.launcher.diagnostics;

import by.agro.launcher.i18n.Strings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class LogAnalyzer {
    private final Map<String, FindingState> findings = new LinkedHashMap<>();
    private final Map<String, ModIdentity> implicated = new LinkedHashMap<>();
    private final List<ModMatcher> matchers = new ArrayList<>();

    public LogAnalyzer() {
        this(List.of());
    }

    public LogAnalyzer(List<ModIdentity> mods) {
        List<ModIdentity> identities = mods == null ? List.of() : mods;
        for (ModIdentity mod : identities) {
            if (mod != null) {
                matchers.add(new ModMatcher(mod));
            }
        }
    }

    public synchronized void accept(String line) {
        String text = line == null ? "" : line.toLowerCase(Locale.ROOT);
        List<ModIdentity> lineMods = matchMods(text);
        if (text.contains("unsupportedclassversionerror") || text.contains("compiled by a more recent version")
                || text.contains("only recognizes class file versions")) {
            add("JAVA_MISMATCH", Strings.get("diag.JAVA_MISMATCH.title"), Strings.get("diag.JAVA_MISMATCH.advice"), lineMods);
        }
        if (text.contains("outofmemoryerror") || text.contains("java heap space") || text.contains("gc overhead limit")) {
            add("OUT_OF_MEMORY", Strings.get("diag.OUT_OF_MEMORY.title"), Strings.get("diag.OUT_OF_MEMORY.advice"), lineMods);
        }
        if (text.contains("classnotfoundexception") || text.contains("noclassdeffounderror")
                || text.contains("module not found") || text.contains("could not find or load main class")) {
            add("MISSING_CLASS", Strings.get("diag.MISSING_CLASS.title"), Strings.get("diag.MISSING_CLASS.advice"), lineMods);
        }
        if (text.contains("requires") && (text.contains("mod") || text.contains("fabric") || text.contains("forge"))
                || text.contains("missing mandatory dependencies") || text.contains("mod resolution encountered")) {
            add("MOD_DEPENDENCY", Strings.get("diag.MOD_DEPENDENCY.title"), Strings.get("diag.MOD_DEPENDENCY.advice"), lineMods);
        }
        if (text.contains("mixin") && (text.contains("failed") || text.contains("error") || text.contains("exception"))) {
            add("MIXIN_FAILURE", Strings.get("diag.MIXIN_FAILURE.title"), Strings.get("diag.MIXIN_FAILURE.advice"), lineMods);
        }
        if (text.contains("opengl") || text.contains("lwjgl") && text.contains("error")
                || text.contains("unsatisfiedlinkerror") || text.contains("failed to load native")) {
            add("NATIVE_OPENGL", Strings.get("diag.NATIVE_OPENGL.title"), Strings.get("diag.NATIVE_OPENGL.advice"), lineMods);
        }
        if (text.contains("zipexception") || text.contains("invalid loc header")
                || text.contains("error in opening zip file") || text.contains("corrupt") && text.contains("jar")) {
            add("CORRUPT_ARCHIVE", Strings.get("diag.CORRUPT_ARCHIVE.title"), Strings.get("diag.CORRUPT_ARCHIVE.advice"), lineMods);
        }
        if (text.contains("authentication") && (text.contains("failed") || text.contains("error"))
                || text.contains("invalid token") || text.contains("unauthorized")
                || text.contains("unknownhostexception") || text.contains("connectexception")
                || text.contains("sockettimeoutexception") || text.contains("sslhandshakeexception")) {
            add("AUTH_NETWORK", Strings.get("diag.AUTH_NETWORK.title"), Strings.get("diag.AUTH_NETWORK.advice"), lineMods);
        }
    }

    private List<ModIdentity> matchMods(String text) {
        List<ModIdentity> matches = new ArrayList<>();
        for (ModMatcher matcher : matchers) {
            if (matcher.matches(text)) {
                matches.add(matcher.mod);
                implicated.putIfAbsent(matcher.mod.fileName, matcher.mod);
            }
        }
        return matches;
    }

    private static final class ModMatcher {
        final ModIdentity mod;
        private final String fileNameLower;
        private final List<Pattern> patterns = new ArrayList<>();

        ModMatcher(ModIdentity mod) {
            this.mod = mod;
            this.fileNameLower = mod.fileName == null ? null : mod.fileName.toLowerCase(Locale.ROOT);
            String id = mod.id != null && mod.id.length() >= 3 ? mod.id.toLowerCase(Locale.ROOT) : null;
            if (id != null) {
                patterns.add(Pattern.compile("(?<![a-z0-9_-])" + Pattern.quote(id) + "(?![a-z0-9_-])"));
            }
            String name = mod.name != null && mod.name.length() >= 4 ? mod.name.toLowerCase(Locale.ROOT) : null;
            if (name != null) {
                patterns.add(Pattern.compile("(?<![a-z0-9_-])" + Pattern.quote(name) + "(?![a-z0-9_-])"));
            }
            if (mod.packageRoots != null) {
                for (String root : mod.packageRoots) {
                    if (root == null || root.length() < 5) continue;
                    String lower = root.toLowerCase(Locale.ROOT);
                    patterns.add(Pattern.compile("(?<![a-z0-9_$])" + Pattern.quote(lower) + "(?:\\.|/)"));
                }
            }
        }

        boolean matches(String text) {
            if (fileNameLower != null && !fileNameLower.isBlank() && text.contains(fileNameLower)) {
                return true;
            }
            for (Pattern pattern : patterns) {
                if (pattern.matcher(text).find()) {
                    return true;
                }
            }
            return false;
        }
    }

    private void add(String code, String title, String advice, List<ModIdentity> mods) {
        FindingState state = findings.computeIfAbsent(code, key -> new FindingState(code, title, advice));
        for (ModIdentity mod : mods) state.mods.add(mod.displayName());
    }

    public synchronized List<DiagnosticFinding> findings() {
        List<DiagnosticFinding> result = new ArrayList<>();
        for (FindingState state : findings.values()) {
            result.add(new DiagnosticFinding(state.code, DiagnosticFinding.Severity.ERROR,
                    state.title, state.advice, new ArrayList<>(state.mods)));
        }
        return result;
    }

    public synchronized List<ModIdentity> implicatedMods() {
        return new ArrayList<>(implicated.values());
    }

    private static final class FindingState {
        private final String code;
        private final String title;
        private final String advice;
        private final Set<String> mods = new LinkedHashSet<>();

        private FindingState(String code, String title, String advice) {
            this.code = code;
            this.title = title;
            this.advice = advice;
        }
    }
}
