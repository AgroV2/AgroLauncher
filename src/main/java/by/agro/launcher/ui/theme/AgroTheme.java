package by.agro.launcher.ui.theme;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class AgroTheme {

    private static ThemePalette palette = ThemePreset.EMERALD_DARK.palette();
    private static ThemePreset activePreset = ThemePreset.EMERALD_DARK;
    private static Color[] gradientColors = new Color[0];

    private static final List<Consumer<ThemePalette>> listeners = new ArrayList<>();

    private AgroTheme() {
    }

    public static ThemePalette palette() {
        return palette;
    }

    public static ThemePreset activePreset() {
        return activePreset;
    }

    public static Color[] gradientColors() {
        return gradientColors.clone();
    }

    public static boolean hasGradient() {
        return gradientColors.length >= 2;
    }

    public static Color gradientAverageColor() {
        if (!hasGradient()) return accent();
        if (gradientColors.length == 7
                && gradientColors[0].equals(new Color(0xEF4444))
                && gradientColors[6].equals(new Color(0x8B5CF6))) {
            return new Color(0xEC4899);
        }
        long red = 0;
        long green = 0;
        long blue = 0;
        for (Color color : gradientColors) {
            red += color.getRed();
            green += color.getGreen();
            blue += color.getBlue();
        }
        int count = gradientColors.length;
        return new Color((int) (red / count), (int) (green / count), (int) (blue / count));
    }

    public static void setGradient(GradientPreset preset, String customColors) {
        gradientColors = (preset == null ? GradientPreset.NONE : preset).colors(customColors);
        applyDefaults();
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.repaint();
        }
        notifyListeners();
    }

    public static java.awt.Paint gradientPaint(int width, int height) {
        return gradientPaint(0, 0, width, height);
    }

    public static java.awt.Paint gradientPaint(int x, int y, int width, int height) {
        if (gradientColors.length < 2) return accent();
        float[] fractions = new float[gradientColors.length];
        for (int i = 0; i < fractions.length; i++) fractions[i] = (float) i / (fractions.length - 1);
        float startX = x;
        float startY = y;
        float endX = x + Math.max(1, width);
        float endY = y + Math.max(1, height);
        return new java.awt.LinearGradientPaint(startX, startY, endX, endY,
                fractions, gradientColors);
    }

    public static Color bgDeep() {
        return palette.bgDeep;
    }

    public static Color bgBase() {
        return palette.bgBase;
    }

    public static Color bgPanel() {
        return palette.bgPanel;
    }

    public static Color bgElevated() {
        return palette.bgElevated;
    }

    public static Color bgHover() {
        return palette.bgHover;
    }

    public static Color accent() {
        return hasGradient() ? gradientAverageColor() : palette.accent;
    }

    public static Color accentLight() {
        return hasGradient() ? mix(gradientAverageColor(), Color.WHITE, 0.28f) : palette.accentLight;
    }

    public static Color accentDark() {
        return hasGradient() ? mix(gradientAverageColor(), Color.BLACK, 0.22f) : palette.accentDark;
    }

    public static Color accentDeep() {
        return hasGradient() ? mix(gradientAverageColor(), palette.bgDeep, 0.68f) : palette.accentDeep;
    }

    public static Color accentGlow() {
        if (!hasGradient()) return palette.accentGlow;
        Color average = gradientAverageColor();
        return new Color(average.getRed(), average.getGreen(), average.getBlue(), palette.accentGlow.getAlpha());
    }

    private static Color mix(Color first, Color second, float secondWeight) {
        float weight = Math.max(0f, Math.min(1f, secondWeight));
        float firstWeight = 1f - weight;
        return new Color(
                Math.round(first.getRed() * firstWeight + second.getRed() * weight),
                Math.round(first.getGreen() * firstWeight + second.getGreen() * weight),
                Math.round(first.getBlue() * firstWeight + second.getBlue() * weight));
    }

    public static Color textOnAccent() {
        return palette.textOnAccent;
    }

    public static Color textPrimary() {
        return palette.textPrimary;
    }

    public static Color textSecondary() {
        return palette.textSecondary;
    }

    public static Color textMuted() {
        return palette.textMuted;
    }

    public static Color border() {
        return palette.border;
    }

    public static Color borderLight() {
        return palette.borderLight;
    }

    public static Color error() {
        return palette.error;
    }

    public static Color warning() {
        return palette.warning;
    }

    public static Color info() {
        return palette.info;
    }

    public static Color success() {
        return palette.success();
    }

    public static void install() {
        install(ThemePreset.EMERALD_DARK, null);
    }

    public static void install(ThemePreset preset, Color customColor) {
        activePreset = preset != null ? preset : ThemePreset.EMERALD_DARK;
        palette = resolvePalette(activePreset, customColor);

        System.setProperty("flatlaf.useWindowDecorations", "true");
        System.setProperty("flatlaf.menuBarEmbedded", "true");

        FlatDarkLaf.setup();
        applyDefaults();
    }

    public static void apply(ThemePreset preset, Color customColor) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> apply(preset, customColor));
            return;
        }

        ThemePalette previous = palette;
        activePreset = preset != null ? preset : ThemePreset.EMERALD_DARK;
        palette = resolvePalette(activePreset, customColor);

        applyDefaults();
        for (Window window : Window.getWindows()) {
            migratePaletteColors(window, previous, palette);
        }
        FlatLaf.updateUI();
        notifyListeners();

        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }

    private static void migratePaletteColors(Component component,
                                             ThemePalette previous,
                                             ThemePalette current) {
        if (component == null || previous == null || current == null) {
            return;
        }

        Color foreground = replacement(component.getForeground(), previous, current);
        if (foreground != null) {
            component.setForeground(foreground);
        }
        Color background = replacement(component.getBackground(), previous, current);
        if (background != null) {
            component.setBackground(background);
        }

        if (component instanceof JComponent swingComponent) {
            Border border = swingComponent.getBorder();
            if (border != null && !(border instanceof UIResource)) {
                swingComponent.setBorder(recolorBorder(border, previous, current));
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                migratePaletteColors(child, previous, current);
            }
        }
    }

    private static Color replacement(Color color, ThemePalette previous, ThemePalette current) {
        if (color == null || color instanceof ColorUIResource) {
            return null;
        }
        Color[] oldColors = paletteColors(previous);
        Color[] newColors = paletteColors(current);
        for (int i = 0; i < oldColors.length; i++) {
            if (color.equals(oldColors[i])) {
                return newColors[i];
            }
        }
        return null;
    }

    private static Color[] paletteColors(ThemePalette p) {
        return new Color[]{p.bgDeep, p.bgBase, p.bgPanel, p.bgElevated, p.bgHover,
                p.accent, p.accentLight, p.accentDark, p.accentDeep, p.accentGlow,
                p.textOnAccent, p.textPrimary, p.textSecondary, p.textMuted,
                p.border, p.borderLight, p.error, p.warning, p.info};
    }

    private static Border recolorBorder(Border border, ThemePalette previous,
                                        ThemePalette current) {
        if (border instanceof javax.swing.border.LineBorder line) {
            Color color = replacement(line.getLineColor(), previous, current);
            return color != null
                    ? javax.swing.BorderFactory.createLineBorder(color,
                    line.getThickness(), line.getRoundedCorners()) : border;
        }
        if (border instanceof javax.swing.border.MatteBorder matte) {
            Color color = replacement(matte.getMatteColor(), previous, current);
            return color != null
                    ? javax.swing.BorderFactory.createMatteBorder(
                    matte.getBorderInsets().top, matte.getBorderInsets().left,
                    matte.getBorderInsets().bottom, matte.getBorderInsets().right, color)
                    : border;
        }
        if (border instanceof javax.swing.border.CompoundBorder compound) {
            Border outside = recolorBorder(compound.getOutsideBorder(), previous, current);
            Border inside = recolorBorder(compound.getInsideBorder(), previous, current);
            return outside != compound.getOutsideBorder() || inside != compound.getInsideBorder()
                    ? javax.swing.BorderFactory.createCompoundBorder(outside, inside) : border;
        }
        return border;
    }

    private static ThemePalette resolvePalette(ThemePreset preset, Color customColor) {
        switch (preset) {
            case QT_SYSTEM:
                return SystemPalette.read();
            case CUSTOM:
                return ThemePreset.customPalette(customColor != null ? customColor : preset.accentColor());
            default:
                return preset.palette();
        }
    }

    public static void addThemeListener(Consumer<ThemePalette> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeThemeListener(Consumer<ThemePalette> listener) {
        listeners.remove(listener);
    }

    private static void notifyListeners() {
        for (Consumer<ThemePalette> listener : new ArrayList<>(listeners)) {
            try {
                listener.accept(palette);
            } catch (RuntimeException e) {
                System.err.println("Слушатель темы завершился ошибкой: " + e.getMessage());
            }
        }
    }

    private static void applyDefaults() {
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        UIManager.put("CheckBox.arc", 5);

        applyColors();
        applyFonts();
        applyComponentTweaks();
    }

    private static void applyColors() {
        ThemePalette p = palette;

        UIManager.put("control", p.bgPanel);
        UIManager.put("Panel.background", p.bgBase);
        UIManager.put("Viewport.background", p.bgBase);
        UIManager.put("text", p.textPrimary);
        UIManager.put("Label.foreground", p.textPrimary);
        UIManager.put("Label.disabledForeground", p.textMuted);

        UIManager.put("Component.accentColor", accent());
        UIManager.put("Component.focusColor", accent());
        UIManager.put("Component.focusedBorderColor", accentDark());
        UIManager.put("Component.borderColor", p.border);
        UIManager.put("Component.disabledBorderColor", p.border);
        UIManager.put("Component.innerFocusWidth", 1);

        UIManager.put("Button.background", p.bgElevated);
        UIManager.put("Button.foreground", p.textPrimary);
        UIManager.put("Button.hoverBackground", p.bgHover);
        UIManager.put("Button.pressedBackground", p.border);
        UIManager.put("Button.borderColor", p.border);
        UIManager.put("Button.default.background", accent());
        UIManager.put("Button.default.foreground", p.textOnAccent);
        UIManager.put("Button.default.hoverBackground", accentLight());
        UIManager.put("Button.default.pressedBackground", accentDark());
        UIManager.put("Button.default.borderColor", accentDark());
        UIManager.put("Button.default.focusedBorderColor", accentLight());
        UIManager.put("Button.default.boldText", true);

        UIManager.put("TextField.background", p.bgElevated);
        UIManager.put("TextField.foreground", p.textPrimary);
        UIManager.put("TextField.placeholderForeground", p.textMuted);
        UIManager.put("TextField.selectionBackground", accentDark());
        UIManager.put("TextField.selectionForeground", p.textPrimary);
        UIManager.put("PasswordField.background", p.bgElevated);
        UIManager.put("PasswordField.foreground", p.textPrimary);
        UIManager.put("PasswordField.selectionBackground", accentDark());
        UIManager.put("TextArea.background", p.bgElevated);
        UIManager.put("TextArea.foreground", p.textPrimary);
        UIManager.put("TextArea.selectionBackground", accentDark());
        UIManager.put("EditorPane.background", p.bgPanel);
        UIManager.put("EditorPane.foreground", p.textPrimary);

        UIManager.put("List.background", p.bgPanel);
        UIManager.put("List.foreground", p.textPrimary);
        UIManager.put("List.selectionBackground", accentDeep());
        UIManager.put("List.selectionForeground", p.textPrimary);
        UIManager.put("List.selectionInactiveBackground", p.bgHover);
        UIManager.put("List.hoverBackground", p.bgHover);

        UIManager.put("ComboBox.background", p.bgElevated);
        UIManager.put("ComboBox.foreground", p.textPrimary);
        UIManager.put("ComboBox.buttonBackground", p.bgElevated);
        UIManager.put("ComboBox.buttonArrowColor", accent());
        UIManager.put("ComboBox.buttonHoverArrowColor", accentLight());
        UIManager.put("ComboBox.selectionBackground", accentDeep());
        UIManager.put("ComboBox.selectionForeground", p.textPrimary);
        UIManager.put("ComboBox.popupBackground", p.bgElevated);

        UIManager.put("Slider.background", p.bgBase);
        UIManager.put("Slider.trackColor", p.border);
        UIManager.put("Slider.thumbColor", accent());
        UIManager.put("Slider.trackValueColor", accent());
        UIManager.put("Slider.focusedColor", accentGlow());
        UIManager.put("Slider.hoverThumbColor", accentLight());
        UIManager.put("Slider.pressedThumbColor", accentDark());
        UIManager.put("Slider.tickColor", p.textMuted);
        UIManager.put("Slider.trackWidth", 6);
        UIManager.put("Slider.thumbWidth", 18);
        UIManager.put("Slider.focusWidth", 3);

        UIManager.put("ProgressBar.background", p.bgElevated);
        UIManager.put("ProgressBar.foreground", accent());
        UIManager.put("ProgressBar.selectionForeground", p.textPrimary);
        UIManager.put("ProgressBar.selectionBackground", p.textPrimary);
        UIManager.put("ProgressBar.horizontalSize", new java.awt.Dimension(146, 8));

        UIManager.put("ScrollBar.track", p.bgBase);
        UIManager.put("ScrollBar.thumb", p.borderLight);
        UIManager.put("ScrollBar.hoverThumbColor", accentDark());
        UIManager.put("ScrollBar.pressedThumbColor", accent());
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollPane.background", p.bgBase);

        UIManager.put("CheckBox.icon.style", "filled");
        UIManager.put("CheckBox.icon.checkmarkColor", p.textOnAccent);
        UIManager.put("CheckBox.icon.selectedBackground", accent());
        UIManager.put("CheckBox.icon.selectedBorderColor", accentDark());
        UIManager.put("CheckBox.icon.background", p.bgElevated);
        UIManager.put("CheckBox.icon.borderColor", p.borderLight);
        UIManager.put("RadioButton.icon.centerDiameter", 8);

        UIManager.put("TabbedPane.background", p.bgBase);
        UIManager.put("TabbedPane.foreground", p.textSecondary);
        UIManager.put("TabbedPane.underlineColor", accent());
        UIManager.put("TabbedPane.selectedForeground", p.textPrimary);
        UIManager.put("TabbedPane.hoverColor", p.bgHover);
        UIManager.put("TabbedPane.focusColor", accentGlow());
        UIManager.put("TabbedPane.contentAreaColor", p.border);
        UIManager.put("TabbedPane.tabHeight", 34);
        UIManager.put("TabbedPane.tabSeparatorsFullHeight", false);
        UIManager.put("TabbedPane.showTabSeparators", false);

        UIManager.put("ToolTip.background", p.bgElevated);
        UIManager.put("ToolTip.foreground", p.textPrimary);
        UIManager.put("ToolTip.border", p.border);
        UIManager.put("PopupMenu.background", p.bgElevated);
        UIManager.put("MenuItem.selectionBackground", accentDeep());
        UIManager.put("OptionPane.background", p.bgPanel);
        UIManager.put("OptionPane.messageForeground", p.textPrimary);
        UIManager.put("Separator.foreground", p.border);

        UIManager.put("TitlePane.background", p.bgDeep);
        UIManager.put("TitlePane.foreground", p.textPrimary);
        UIManager.put("TitlePane.inactiveBackground", p.bgDeep);
        UIManager.put("TitlePane.inactiveForeground", p.textMuted);
        UIManager.put("TitlePane.buttonHoverBackground", p.bgHover);
        UIManager.put("TitlePane.closeHoverBackground", p.error);
        UIManager.put("TitlePane.unifiedBackground", true);

        UIManager.put("SplitPane.background", p.bgBase);
        UIManager.put("SplitPaneDivider.draggingColor", accent());
        UIManager.put("Table.background", p.bgPanel);
        UIManager.put("Table.foreground", p.textPrimary);
        UIManager.put("Table.selectionBackground", accentDeep());
    }

    private static void applyFonts() {
        UIManager.put("defaultFont", resolveUiFont());
    }

    private static Font resolveUiFont() {
        String[] preferred = {"Inter", "Segoe UI Variable", "Segoe UI", "Ubuntu", "Cantarell",
                "Noto Sans", "DejaVu Sans", "Liberation Sans", "SansSerif"};
        Set<String> available = availableFamilies();
        for (String candidate : preferred) {
            if (available.contains(candidate)) {
                return new Font(candidate, Font.PLAIN, 13);
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    public static Font monoFont(int size) {
        String[] preferred = {"JetBrains Mono", "Cascadia Code", "Consolas", "Ubuntu Mono",
                "DejaVu Sans Mono", "Liberation Mono", "Monospaced"};
        Set<String> available = availableFamilies();
        for (String candidate : preferred) {
            if (available.contains(candidate)) {
                return new Font(candidate, Font.PLAIN, size);
            }
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, size);
    }

    private static Set<String> availableFamilies() {
        Set<String> available = new HashSet<>();
        try {
            for (String name : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()) {
                available.add(name);
            }
        } catch (Throwable ignored) {
        }
        return available;
    }

    private static void applyComponentTweaks() {
        UIManager.put("Component.hideMnemonics", false);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("TitlePane.centerTitle", false);
    }

    public static Font font(int size, int style) {
        Font base = UIManager.getFont("defaultFont");
        if (base == null) {
            base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        }
        return base.deriveFont(style, size);
    }

    public static Font font(int size) {
        return font(size, Font.PLAIN);
    }

    public static Font boldFont(int size) {
        return font(size, Font.BOLD);
    }
}
