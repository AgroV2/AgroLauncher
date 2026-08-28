

package by.agro.launcher.ui.theme;

import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.SystemColor;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class SystemPalette {


    static final String PROFILE_QTCT = "qtct";
    static final String PROFILE_KDE = "kde";
    static final String PROFILE_GTK = "gtk";
    static final String PROFILE_AUTO = "auto";


    private static final int ROLE_WINDOW_TEXT = 0;
    private static final int ROLE_TEXT = 6;
    private static final int ROLE_BASE = 9;
    private static final int ROLE_WINDOW = 10;
    private static final int ROLE_HIGHLIGHT = 12;
    private static final int ROLE_ACCENT = 21;

    private static final Pattern PORTAL_RGB = Pattern.compile(
            "\\(\\s*([0-9]*\\.?[0-9]+)\\s*,\\s*([0-9]*\\.?[0-9]+)\\s*,\\s*([0-9]*\\.?[0-9]+)\\s*\\)");
    private static final Pattern PORTAL_UINT = Pattern.compile("uint32\\s+(\\d+)");
    private static final Pattern GTK_DEFINE_COLOR = Pattern.compile(
            "@define-color\\s+([A-Za-z0-9_\\-]+)\\s+([^;]+);");

    private SystemPalette() {
    }


    public static ThemePalette read() {
        Resolved r = resolve();
        boolean dark = Boolean.TRUE.equals(r.dark);

        Color background = r.background != null
                ? r.background
                : (r.dark != null ? (dark ? new Color(0x1E1E1E) : new Color(0xF5F5F5)) : SystemColor.window);
        Color foreground = r.foreground != null
                ? r.foreground
                : (r.dark != null ? (dark ? new Color(0xEDEDED) : new Color(0x1A1A1A)) : SystemColor.windowText);
        Color accent = r.accent != null ? r.accent : SystemColor.textHighlight;
        Color disabled = r.disabled != null ? r.disabled : SystemColor.textInactiveText;

        return PaletteFactory.buildSystem(background, accent, foreground, disabled);
    }


    public static String diagnostics() {
        StringBuilder out = new StringBuilder();
        out.append("os.name              = ").append(System.getProperty("os.name")).append('\n');
        out.append("XDG_CURRENT_DESKTOP  = ").append(System.getenv("XDG_CURRENT_DESKTOP")).append('\n');
        out.append("XDG_SESSION_DESKTOP  = ").append(System.getenv("XDG_SESSION_DESKTOP")).append('\n');
        out.append("XDG_SESSION_TYPE     = ").append(System.getenv("XDG_SESSION_TYPE")).append('\n');
        out.append("QT_QPA_PLATFORMTHEME = ").append(System.getenv("QT_QPA_PLATFORMTHEME")).append('\n');
        out.append("XDG_CONFIG_HOME      = ").append(System.getenv("XDG_CONFIG_HOME")).append('\n');
        out.append("agro.theme.source    = ").append(System.getProperty("agro.theme.source")).append('\n');
        Resolved r = resolve();
        out.append("профиль окружения    = ").append(r.profile).append('\n');
        out.append("\n-- кандидаты конфигов --\n");
        List<Path> candidates = qtConfigCandidates(r);
        if (candidates.isEmpty()) {
            out.append("  (в этом профиле Qt-конфиги не опрашиваются)\n");
        }
        for (Path p : candidates) {
            out.append(Files.isRegularFile(p) ? "  [есть]     " : "  [нет]      ").append(p).append('\n');
        }
        out.append("\n-- шаги резолвера --\n");
        for (String line : r.log) {
            out.append("  ").append(line).append('\n');
        }
        out.append("\n-- итог --\n");
        out.append("  dark        = ").append(r.dark).append('\n');
        out.append("  background  = ").append(hex(r.background)).append('\n');
        out.append("  foreground  = ").append(hex(r.foreground)).append('\n');
        out.append("  accent      = ").append(hex(r.accent)).append('\n');
        out.append("  disabled    = ").append(hex(r.disabled)).append('\n');
        return out.toString();
    }





    private static Resolved resolve() {
        Resolved r = new Resolved();
        r.profile = themeProfile();
        if (isLinux()) {
            if (PROFILE_GTK.equals(r.profile)) {


                r.log.add("профиль gtk: Qt-конфиги и kdeglobals исключены из опроса");
                readGtk(r);
                readPortal(r);
            } else {
                readQtConfigs(r);
                readPortal(r);
                readGtk(r);
            }
        }





        if (!isLinux() || (r.background == null && r.accent == null && r.dark == null)) {
            readDesktopProperties(r);
            readSystemLookAndFeel(r);
        } else if (isLinux()) {
            r.log.add("linux: desktop-properties и look-and-feel пропущены (дали бы палитру Metal)");
        }
        harmonize(r);
        return r;
    }



    private static void harmonize(Resolved r) {
        if (r.background != null && r.dark == null) {
            r.dark = PaletteFactory.relativeLuminance(r.background) < 0.35;
        }


        if (r.dark != null && r.background != null) {
            boolean backgroundIsDark = PaletteFactory.relativeLuminance(r.background) < 0.35;
            if (backgroundIsDark != r.dark) {
                r.log.add("harmonize: фон " + hex(r.background) + " из " + r.backgroundSource
                        + " противоречит режиму dark=" + r.dark + " — отбрасываю фон и текст");
                r.background = null;
                r.foreground = null;
                r.disabled = null;
                r.backgroundSource = null;
                r.foregroundSource = null;
            }
        }
        if (r.background == null || r.foreground == null) {
            return;
        }
        double delta = Math.abs(PaletteFactory.relativeLuminance(r.background)
                - PaletteFactory.relativeLuminance(r.foreground));
        if (delta < 0.15) {
            r.log.add("harmonize: текст слился с фоном (Δlum=" + String.format(Locale.ROOT, "%.3f", delta)
                    + "), сбрасываю foreground/disabled");
            r.foreground = null;
            r.disabled = null;
        }
    }





    private static void readQtConfigs(Resolved r) {
        for (Path config : qtConfigCandidates(r)) {
            if (r.isComplete()) {
                return;
            }
            Map<String, Map<String, String>> ini = readIni(config);
            if (ini.isEmpty()) {
                continue;
            }
            String name = config.getFileName() == null ? "" : config.getFileName().toString();
            boolean kde = name.startsWith("kdeglobals") || name.startsWith("Trolltech");

            if (kde) {
                readKdeIni(r, config, ini);
            } else {
                readQtCtIni(r, config, ini);
            }
        }
    }


    private static void readQtCtIni(Resolved r, Path config, Map<String, Map<String, String>> ini) {
        String custom = firstValue(ini, "Appearance", "custom_palette");
        if (custom != null && !Boolean.parseBoolean(custom.trim())) {
            r.log.add(config + ": custom_palette=false — палитру задаёт стиль Qt, схему не читаю");
            return;
        }
        String schemePath = firstValue(ini, "Appearance", "color_scheme_path");
        if (schemePath == null) {
            schemePath = firstValue(ini, "General", "color_scheme_path");
        }
        if (schemePath == null || schemePath.isBlank()) {
            r.log.add(config + ": color_scheme_path не задан");
            return;
        }
        for (Path scheme : schemeCandidates(schemePath.trim(), config)) {
            Map<String, Map<String, String>> schemeIni = readIni(scheme);
            if (schemeIni.isEmpty()) {
                continue;
            }
            Color[] active = parseQtColorList(value(schemeIni, "ColorScheme", "active_colors"));
            Color[] disabled = parseQtColorList(value(schemeIni, "ColorScheme", "disabled_colors"));
            if (active == null) {
                continue;
            }
            Color bg = role(active, ROLE_WINDOW);
            if (bg == null) {
                bg = role(active, ROLE_BASE);
            }
            Color fg = role(active, ROLE_WINDOW_TEXT);
            if (fg == null) {
                fg = role(active, ROLE_TEXT);
            }
            Color ac = role(active, ROLE_ACCENT);
            if (ac == null) {
                ac = role(active, ROLE_HIGHLIGHT);
            }
            Color dis = disabled == null ? null : role(disabled, ROLE_WINDOW_TEXT);
            if (dis == null && disabled != null) {
                dis = role(disabled, ROLE_TEXT);
            }
            r.fill("qt-ct:" + scheme, bg, fg, ac, dis);
            return;
        }
        r.log.add(config + ": схема '" + schemePath + "' не найдена ни по одному пути");
    }


    private static void readKdeIni(Resolved r, Path config, Map<String, Map<String, String>> ini) {
        Color accent = kdeAccent(ini);
        r.fill(config + " (inline)",
                kdeColor(ini, "Colors:Window", "BackgroundNormal", "Colors:View"),
                kdeColor(ini, "Colors:Window", "ForegroundNormal", "Colors:View"),
                accent,
                kdeColor(ini, "Colors:Window", "ForegroundInactive", "Colors:View"));
        if (r.isComplete()) {
            return;
        }
        String schemeName = firstValue(ini, "General", "ColorScheme");
        if (schemeName == null || schemeName.isBlank()) {
            return;
        }
        for (Path scheme : schemeCandidates(schemeName.trim(), config)) {
            Map<String, Map<String, String>> schemeIni = readIni(scheme);
            if (schemeIni.isEmpty()) {
                continue;
            }
            r.fill("kde-scheme:" + scheme,
                    kdeColor(schemeIni, "Colors:Window", "BackgroundNormal", "Colors:View"),
                    kdeColor(schemeIni, "Colors:Window", "ForegroundNormal", "Colors:View"),
                    kdeAccent(schemeIni),
                    kdeColor(schemeIni, "Colors:Window", "ForegroundInactive", "Colors:View"));
            return;
        }
    }

    private static Color kdeAccent(Map<String, Map<String, String>> ini) {

        Color accent = parseTupleOrHex(firstValue(ini, "General", "AccentColor", "accent_color"));
        if (accent == null) {
            accent = parseTupleOrHex(value(ini, "Colors:Selection", "BackgroundNormal"));
        }
        return accent;
    }

    private static Color kdeColor(Map<String, Map<String, String>> ini,
                                  String section, String key, String fallbackSection) {
        Color color = parseTupleOrHex(value(ini, section, key));
        return color != null ? color : parseTupleOrHex(value(ini, fallbackSection, key));
    }


    private static Color[] parseQtColorList(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(",");
        List<Color> colors = new ArrayList<>();
        for (String part : parts) {
            colors.add(parseArgbHex(part.trim()));
        }
        boolean any = colors.stream().anyMatch(c -> c != null);
        return any ? colors.toArray(new Color[0]) : null;
    }

    private static Color role(Color[] colors, int index) {
        return colors != null && index < colors.length ? colors[index] : null;
    }


    private static Color parseArgbHex(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() == 8 && value.matches("[0-9a-fA-F]{8}")) {
            return new Color((int) (Long.parseLong(value, 16) & 0xFFFFFF));
        }
        return PaletteFactory.parseHex(text);
    }


    private static Color parseTupleOrHex(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(",");
        if (parts.length >= 3) {
            try {
                return new Color(channel(parts[0]), channel(parts[1]), channel(parts[2]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return parseArgbHex(raw);
    }

    private static int channel(String value) {
        return Math.max(0, Math.min(255, Integer.parseInt(value.trim())));
    }





    private static void readPortal(Resolved r) {
        String accentRaw = portalSetting("accent-color");
        Color accent = null;
        if (accentRaw != null) {
            Matcher m = PORTAL_RGB.matcher(accentRaw);
            if (m.find()) {
                accent = new Color(
                        clampByte(Double.parseDouble(m.group(1))),
                        clampByte(Double.parseDouble(m.group(2))),
                        clampByte(Double.parseDouble(m.group(3))));
            }
        }
        String schemeRaw = portalSetting("color-scheme");
        Boolean dark = null;
        if (schemeRaw != null) {
            Matcher m = PORTAL_UINT.matcher(schemeRaw);
            if (m.find()) {
                int mode = Integer.parseInt(m.group(1));
                if (mode == 1) {
                    dark = Boolean.TRUE;
                } else if (mode == 2) {
                    dark = Boolean.FALSE;
                }
            }
        }
        if (accent != null || dark != null) {
            r.log.add("portal: accent=" + hex(accent) + " dark=" + dark);
        }
        if (r.dark == null) {
            r.dark = dark;
        }
        r.fill("portal", null, null, accent, null);
    }

    private static String portalSetting(String key) {
        String[] readOne = {
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.portal.Desktop",
                "--object-path", "/org/freedesktop/portal/desktop",
                "--method", "org.freedesktop.portal.Settings.ReadOne",
                "org.freedesktop.appearance", key};
        String result = exec(readOne);
        if (result == null || result.isBlank()) {
            String[] legacy = readOne.clone();
            legacy[8] = "org.freedesktop.portal.Settings.Read";
            result = exec(legacy);
        }
        return result;
    }

    private static int clampByte(double normalized) {
        return (int) Math.round(Math.max(0, Math.min(1, normalized)) * 255);
    }





    private static void readGtk(Resolved r) {
        if (r.isComplete()) {
            return;
        }
        Map<String, Color> defs = new LinkedHashMap<>();
        String home = System.getProperty("user.home", "");
        Path configHome = configHome();
        for (String dir : new String[]{"gtk-4.0", "gtk-3.0"}) {
            readGtkCss(configHome.resolve(dir).resolve("gtk.css"), defs);
            readGtkCss(configHome.resolve(dir).resolve("colors.css"), defs);
        }
        readGtkCss(Paths.get(home, ".gtkrc-2.0"), defs);

        Color bg = firstOf(defs, "window_bg_color", "theme_bg_color", "view_bg_color", "theme_base_color");
        Color fg = firstOf(defs, "window_fg_color", "theme_fg_color", "view_fg_color", "theme_text_color");
        Color ac = firstOf(defs, "accent_bg_color", "accent_color", "theme_selected_bg_color");
        Color dis = firstOf(defs, "insensitive_fg_color", "theme_unfocused_fg_color");
        if (bg != null || fg != null || ac != null) {
            r.fill("gtk-css", bg, fg, ac, dis);
        }

        if (r.dark == null) {
            String scheme = gsettings("org.gnome.desktop.interface", "color-scheme");
            if (scheme != null) {
                if (scheme.contains("prefer-dark")) {
                    r.dark = Boolean.TRUE;
                } else if (scheme.contains("prefer-light") || scheme.contains("default")) {
                    r.dark = Boolean.FALSE;
                }
            }
        }
        if (r.accent == null) {
            Color named = namedGnomeAccent(gsettings("org.gnome.desktop.interface", "accent-color"));
            if (named != null) {
                r.fill("gsettings-accent", null, null, named, null);
            }
        }
    }

    private static void readGtkCss(Path path, Map<String, Color> into) {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            Matcher m = GTK_DEFINE_COLOR.matcher(content);
            while (m.find()) {
                Color color = PaletteFactory.parseHex(m.group(2).trim());
                if (color != null) {
                    into.putIfAbsent(m.group(1).trim(), color);
                }
            }
        } catch (IOException | RuntimeException ignored) {

        }
    }

    private static Color firstOf(Map<String, Color> defs, String... names) {
        for (String name : names) {
            Color color = defs.get(name);
            if (color != null) {
                return color;
            }
        }
        return null;
    }

    private static Color namedGnomeAccent(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.replace("'", "").trim().toLowerCase(Locale.ROOT);
        switch (name) {
            case "blue": return new Color(0x35, 0x84, 0xE4);
            case "teal": return new Color(0x21, 0x90, 0xA4);
            case "green": return new Color(0x3A, 0x94, 0x4A);
            case "yellow": return new Color(0xC8, 0x8B, 0x02);
            case "orange": return new Color(0xED, 0x58, 0x21);
            case "red": return new Color(0xE6, 0x27, 0x2E);
            case "pink": return new Color(0xD5, 0x6B, 0xA1);
            case "purple": return new Color(0x91, 0x41, 0xAC);
            case "slate": return new Color(0x6F, 0x81, 0x95);
            default: return null;
        }
    }

    private static String gsettings(String schema, String key) {
        return exec("gsettings", "get", schema, key);
    }





    private static void readDesktopProperties(Resolved r) {
        if (r.isComplete()) {
            return;
        }
        r.fill("desktop-properties",
                desktopColor("win.3d.backgroundColor"),
                desktopColor("win.text.textColor"),
                desktopColor("win.item.highlightColor", "win.frame.activeCaptionColor"),
                desktopColor("win.text.grayedTextColor"));
    }

    private static void readSystemLookAndFeel(Resolved r) {
        if (r.isComplete()) {
            return;
        }
        LookAndFeel system = null;
        try {
            system = (LookAndFeel) Class.forName(UIManager.getSystemLookAndFeelClassName())
                    .getDeclaredConstructor().newInstance();
            system.initialize();
            UIDefaults defaults = system.getDefaults();
            r.fill("look-and-feel",
                    defaultColor(defaults, "Panel.background", "control", "window"),
                    defaultColor(defaults, "Label.foreground", "textText", "windowText"),
                    defaultColor(defaults, "List.selectionBackground",
                            "Table.selectionBackground", "TextField.selectionBackground", "textHighlight"),
                    defaultColor(defaults, "Label.disabledForeground", "textInactiveText"));
        } catch (ReflectiveOperationException | RuntimeException ignored) {

        } finally {
            if (system != null) {
                system.uninitialize();
            }
        }
    }

    private static Color defaultColor(UIDefaults defaults, String... keys) {
        for (String key : keys) {
            Color value = defaults.getColor(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Color desktopColor(String... keys) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (String key : keys) {
            Object value = toolkit.getDesktopProperty(key);
            if (value instanceof Color) {
                return (Color) value;
            }
        }
        return null;
    }





    private static Path configHome() {
        String home = System.getProperty("user.home", "");
        String xdg = System.getenv("XDG_CONFIG_HOME");
        return xdg == null || xdg.isBlank() ? Paths.get(home, ".config") : Paths.get(xdg);
    }


    static String themeProfile() {
        String override = System.getProperty("agro.theme.source");
        if (override != null && !override.isBlank()) {
            return override.trim().toLowerCase(Locale.ROOT);
        }
        String platformTheme = System.getenv("QT_QPA_PLATFORMTHEME");
        if (platformTheme != null && !platformTheme.isBlank()) {
            String value = platformTheme.toLowerCase(Locale.ROOT);
            if (value.contains("qt6ct") || value.contains("qt5ct") || value.contains("qtct")) {
                return PROFILE_QTCT;
            }
            if (value.contains("kde") || value.contains("plasma")) {
                return PROFILE_KDE;
            }
            if (value.startsWith("gtk")) {
                return PROFILE_GTK;
            }
        }
        String desktop = (System.getenv("XDG_CURRENT_DESKTOP") + ":"
                + System.getenv("XDG_SESSION_DESKTOP")).toLowerCase(Locale.ROOT);
        if (desktop.contains("kde") || desktop.contains("plasma")) {
            return PROFILE_KDE;
        }
        if (desktop.contains("gnome") || desktop.contains("cinnamon") || desktop.contains("xfce")
                || desktop.contains("mate") || desktop.contains("budgie")) {
            return PROFILE_GTK;
        }
        return PROFILE_AUTO;
    }

    private static List<Path> qtConfigCandidates(Resolved r) {
        String profile = r.profile == null ? themeProfile() : r.profile;
        List<Path> result = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        Path configHome = configHome();

        if (PROFILE_GTK.equals(profile)) {
            return result;
        }

        String platformTheme = System.getenv("QT_QPA_PLATFORMTHEME");
        if (platformTheme != null && (platformTheme.contains("/") || platformTheme.endsWith(".conf"))) {
            result.add(Paths.get(platformTheme));
        }
        if (platformTheme != null) {
            String active = platformTheme.toLowerCase(Locale.ROOT);
            if (active.contains("qt6ct")) {
                result.add(configHome.resolve("qt6ct/qt6ct.conf"));
            } else if (active.contains("qt5ct")) {
                result.add(configHome.resolve("qt5ct/qt5ct.conf"));
            } else if (active.contains("qtct")) {
                result.add(configHome.resolve("qt6ct/qt6ct.conf"));
                result.add(configHome.resolve("qt5ct/qt5ct.conf"));
            } else if (active.contains("kde")) {
                result.add(configHome.resolve("kdeglobals"));
            }
        }

        String desktop = (System.getenv("XDG_CURRENT_DESKTOP") + ":"
                + System.getenv("XDG_SESSION_DESKTOP")).toLowerCase(Locale.ROOT);
        if (desktop.contains("kde") || desktop.contains("plasma")) {
            result.add(configHome.resolve("kdeglobals"));
        }




        result.add(configHome.resolve("qt6ct/qt6ct.conf"));
        result.add(configHome.resolve("qt5ct/qt5ct.conf"));
        if (!PROFILE_QTCT.equals(profile)) {
            result.add(configHome.resolve("kdeglobals"));
            result.add(configHome.resolve("Trolltech.conf"));
            result.add(Paths.get(home, ".kde/share/config/kdeglobals"));
            result.add(Paths.get(home, ".kde4/share/config/kdeglobals"));
        }
        return new ArrayList<>(new LinkedHashSet<>(result));
    }


    private static List<Path> schemeCandidates(String name, Path config) {
        List<Path> result = new ArrayList<>();
        String home = System.getProperty("user.home", "");
        Path explicit = Paths.get(name);
        if (explicit.isAbsolute() || name.contains("/")) {
            result.add(explicit);
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String fileName = lower.endsWith(".colors") || lower.endsWith(".conf") ? name : name + ".colors";

        Path parent = config.getParent();
        result.add(parent == null ? Paths.get(fileName) : parent.resolve(fileName));

        String dataHome = System.getenv("XDG_DATA_HOME");
        Path userData = dataHome == null || dataHome.isBlank()
                ? Paths.get(home, ".local/share") : Paths.get(dataHome);
        result.add(userData.resolve("color-schemes").resolve(fileName));
        result.add(userData.resolve("qt6ct/colors").resolve(fileName));
        result.add(userData.resolve("qt5ct/colors").resolve(fileName));
        result.add(configHome().resolve("qt6ct/colors").resolve(fileName));
        result.add(configHome().resolve("qt5ct/colors").resolve(fileName));
        result.add(Paths.get(home, ".kde/share/apps/color-schemes", fileName));
        result.add(Paths.get(home, ".kde4/share/apps/color-schemes", fileName));

        String dataDirs = System.getenv("XDG_DATA_DIRS");
        if (dataDirs == null || dataDirs.isBlank()) {
            dataDirs = "/usr/local/share:/usr/share";
        }
        for (String dir : dataDirs.split(":")) {
            if (!dir.isBlank()) {
                result.add(Paths.get(dir, "color-schemes", fileName));
                result.add(Paths.get(dir, "qt6ct/colors", fileName));
                result.add(Paths.get(dir, "qt5ct/colors", fileName));
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(result));
    }





    private static Map<String, Map<String, String>> readIni(Path path) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (path == null || !Files.isRegularFile(path)) {
            return result;
        }
        String section = "General";
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                    continue;
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    section = trimmed.substring(1, trimmed.length() - 1).trim();
                    continue;
                }
                int equals = trimmed.indexOf('=');
                if (equals > 0) {
                    result.computeIfAbsent(section, ignored -> new LinkedHashMap<>())
                            .put(trimmed.substring(0, equals).trim(), trimmed.substring(equals + 1).trim());
                }
            }
        } catch (IOException | SecurityException ignored) {
            result.clear();
        }
        return result;
    }

    private static String value(Map<String, Map<String, String>> ini, String section, String key) {
        Map<String, String> values = ini.get(section);
        return values == null ? null : values.get(key);
    }

    private static String firstValue(Map<String, Map<String, String>> ini, String section, String... keys) {
        for (String key : keys) {
            String candidate = value(ini, section, key);
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }


    private static String exec(String... command) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(false);
            process = builder.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!process.waitFor(1500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? out.toString() : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static String hex(Color color) {
        return color == null ? "null" : PaletteFactory.toHex(color);
    }


    private static final class Resolved {
        private Color background;
        private Color foreground;
        private Color accent;
        private Color disabled;
        private Boolean dark;
        private String profile;
        private String backgroundSource;
        private String foregroundSource;
        private final List<String> log = new ArrayList<>();

        private void fill(String source, Color bg, Color fg, Color ac, Color dis) {
            List<String> taken = new ArrayList<>();
            if (background == null && bg != null) {
                background = bg;
                backgroundSource = source;
                taken.add("bg=" + hex(bg));
            }


            boolean sameSource = backgroundSource == null || backgroundSource.equals(source);
            if (foreground == null && fg != null && sameSource) {
                foreground = fg;
                foregroundSource = source;
                taken.add("fg=" + hex(fg));
            } else if (foreground == null && fg != null) {
                log.add(source + ": foreground=" + hex(fg) + " отвергнут — фон пришёл из " + backgroundSource);
            }
            if (accent == null && ac != null) {
                accent = ac;
                taken.add("accent=" + hex(ac));
            }
            if (disabled == null && dis != null
                    && (foregroundSource == null || foregroundSource.equals(source))) {
                disabled = dis;
                taken.add("disabled=" + hex(dis));
            }
            if (!taken.isEmpty()) {
                log.add(source + " → " + String.join(", ", taken));
            }
        }

        private boolean isComplete() {
            return background != null && foreground != null && accent != null && disabled != null;
        }
    }
}

