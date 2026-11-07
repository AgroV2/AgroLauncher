package by.agro.launcher.auth;

import by.agro.launcher.core.Platform;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;


final class BrowserOpener {
    @FunctionalInterface interface DesktopLauncher { boolean open(URI uri) throws Exception; }
    @FunctionalInterface interface CommandLauncher { boolean open(URI uri) throws Exception; }
    private BrowserOpener() { }

    static void open(URI uri) throws IOException {
        open(uri, u -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) return false;
            Desktop.getDesktop().browse(u);
            return true;
        }, u -> {
            Process process = new ProcessBuilder("xdg-open", u.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();

            return !process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() == 0;
        }, Platform.isLinux());
    }

    static void open(URI uri, DesktopLauncher desktop, CommandLauncher command, boolean linux) throws IOException {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
            throw new IOException("Invalid browser URL");
        try { if (desktop.open(uri)) return; } catch (Exception ignored) { }
        if (linux) {
            try { if (command.open(uri)) return; }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Browser opening interrupted", e); }
            catch (Exception ignored) { }
        }
        throw new IOException("Browser is unavailable");
    }
}
