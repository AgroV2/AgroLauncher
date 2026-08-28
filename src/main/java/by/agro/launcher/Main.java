package by.agro.launcher;

import by.agro.launcher.auth.Account;
import by.agro.launcher.core.LauncherPaths;
import by.agro.launcher.core.Platform;
import by.agro.launcher.core.ProgressListener;
import by.agro.launcher.core.Settings;
import by.agro.launcher.i18n.Language;
import by.agro.launcher.i18n.Strings;
import by.agro.launcher.ui.MainWindow;
import by.agro.launcher.ui.theme.AgroTheme;
import by.agro.launcher.ui.theme.PaletteFactory;
import by.agro.launcher.ui.theme.ThemePreset;

import java.awt.Color;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;
import java.util.List;


public final class Main {

    public static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        Path customDataDir = extractDataDir(args);
        if (customDataDir != null) {
            LauncherPaths.override(customDataDir);
        }

        if (hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printHelp();
            return;
        }
        if (hasFlag(args, "--version")) {
            System.out.println("AgroLauncher " + VERSION);
            return;
        }
        int dryRunIndex = indexOf(args, "--dry-run");
        if (dryRunIndex >= 0) {
            runDryRun(args, dryRunIndex);
            return;
        }

        startGui();
    }

    private static void startGui() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        if (Platform.isLinux()) {
            System.setProperty("sun.java2d.xrender", "true");
            System.setProperty("awt.appClassName", "AgroLauncher");
        }
        System.setProperty("apple.awt.application.name", "AgroLauncher");
        Settings settings = Settings.load();
        Strings.setLanguage(Language.fromCode(settings.language));

        SwingUtilities.invokeLater(() -> {
            try {
                ThemePreset preset = ThemePreset.fromId(settings.themePreset);
                Color customAccent = PaletteFactory.parseHex(settings.customAccentColor);
                AgroTheme.install(preset, customAccent);
            } catch (Exception e) {
                System.err.println("Не удалось применить тему: " + e.getMessage());
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                }
            }

            try {
                LauncherContext context = new LauncherContext(settings);
                MainWindow window = new MainWindow(context);
                window.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        Strings.get("error.startup") + "\n" + e.getMessage(),
                        Strings.get("error.startup.title"), JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    private static void runDryRun(String[] args, int dryRunIndex) {
        if (dryRunIndex + 1 >= args.length) {
            System.err.println("Укажите версию: --dry-run 1.20.1 [ник]");
            System.exit(2);
            return;
        }
        String versionId = args[dryRunIndex + 1];
        String nickname = (dryRunIndex + 2 < args.length && !args[dryRunIndex + 2].startsWith("--"))
                ? args[dryRunIndex + 2]
                : "Player";

        try {
            LauncherContext context = new LauncherContext();
            ProgressListener listener = new ProgressListener() {
                @Override
                public void onProgress(String stage, long current, long total, String detail) {
                    System.out.println("[" + stage + "] " + current + (total > 0 ? "/" + total : "")
                            + (detail != null ? " — " + detail : ""));
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("> " + message);
                }
            };

            Account account = Account.offline(nickname);
            System.out.println("Оффлайн-профиль: " + nickname + " (UUID " + account.uuid + ")");

            List<String> command = context.gameLauncher()
                    .prepare(versionId, account, context.manifest(), listener);

            System.out.println();
            System.out.println("Команда запуска (" + command.size() + " аргументов):");
            System.out.println(String.join(" ", command));
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Path extractDataDir(String[] args) {
        int index = indexOf(args, "--data-dir");
        if (index >= 0 && index + 1 < args.length) {
            return Path.of(args[index + 1]);
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String flag) {
        return indexOf(args, flag) >= 0;
    }

    private static int indexOf(String[] args, String flag) {
        for (int i = 0; i < args.length; i++) {
            if (flag.equals(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private static void printHelp() {
        System.out.println("AgroLauncher " + VERSION);
        System.out.println("Лаунчер Minecraft с поддержкой Forge, Fabric, Quilt, NeoForge, OptiFine и Ely.by");
        System.out.println();
        System.out.println("Запуск без аргументов открывает графический интерфейс.");
        System.out.println();
        System.out.println("Аргументы:");
        System.out.println("  --help, -h                 показать эту справку");
        System.out.println("  --version                  показать версию");
        System.out.println("  --data-dir <путь>          свой каталог данных (портативный режим)");
        System.out.println("  --dry-run <версия> [ник]   подготовить версию и показать команду запуска");
        System.out.println();
        System.out.println("Каталог данных по умолчанию: " + LauncherPaths.get().root());
    }
}
