package by.agro.launcher.ui.theme;

import java.awt.Color;

public final class PaletteFactory {

    private PaletteFactory() {
    }

    public static ThemePalette build(Color base, Color accent) {
        float[] baseHsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        float[] accentHsb = Color.RGBtoHSB(accent.getRed(), accent.getGreen(), accent.getBlue(), null);

       
        float bgHue = mixHue(baseHsb[0], accentHsb[0], 0.15f);
        float bgSat = Math.min(0.22f, baseHsb[1] + 0.04f);
        float baseBrightness = Math.max(0.045f, baseHsb[2]);

        Color bgDeep = hsb(bgHue, bgSat, baseBrightness * 0.72f);
        Color bgBase = hsb(bgHue, bgSat, baseBrightness);
        Color bgPanel = hsb(bgHue, bgSat * 0.95f, baseBrightness * 1.42f);
        Color bgElevated = hsb(bgHue, bgSat * 0.9f, baseBrightness * 1.95f);
        Color bgHover = hsb(bgHue, bgSat * 0.85f, baseBrightness * 2.6f);


        Color accentLight = adjust(accent, 1.14f, 0.94f);
        Color accentDark = adjust(accent, 0.78f, 1.04f);

        Color accentDeep = hsb(accentHsb[0],
                Math.min(1f, accentHsb[1] * 1.05f),
                Math.max(0.16f, accentHsb[2] * 0.34f));
        Color accentGlow = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40);



        Color textOnAccent = relativeLuminance(accent) > 0.45
                ? darken(accent, 0.16f)
                : new Color(0xF7, 0xFA, 0xFC);


        Color textPrimary = hsb(bgHue, 0.06f, 0.93f);
        Color textSecondary = hsb(bgHue, 0.09f, 0.62f);
        Color textMuted = hsb(bgHue, 0.10f, 0.40f);

        Color border = hsb(bgHue, bgSat * 0.8f, baseBrightness * 3.1f);
        Color borderLight = hsb(bgHue, bgSat * 0.7f, baseBrightness * 4.0f);

        return new ThemePalette(
                bgDeep, bgBase, bgPanel, bgElevated, bgHover,
                accent, accentLight, accentDark, accentDeep, accentGlow, textOnAccent,
                textPrimary, textSecondary, textMuted,
                border, borderLight,
                new Color(0xF8, 0x51, 0x49),
                new Color(0xE3, 0xB3, 0x41),
                new Color(0x58, 0xA6, 0xFF)
        );
    }


    public static ThemePalette buildSystem(Color background, Color accent,
                                           Color foreground, Color disabledForeground) {
        Color bg = background != null ? background : new Color(0x202124);
        Color ac = accent != null ? accent : new Color(0x3DAEE9);
        boolean dark = relativeLuminance(bg) < 0.35;
        Color bgDeep = adjust(bg, dark ? 0.78f : 0.92f, 1f);
        Color bgPanel = adjust(bg, dark ? 1.08f : 0.98f, 1f);
        Color bgElevated = adjust(bg, dark ? 1.22f : 0.94f, 0.96f);
        Color bgHover = adjust(bg, dark ? 1.38f : 0.88f, 0.94f);
        Color primary = foreground != null ? foreground : (dark ? Color.WHITE : Color.BLACK);
        Color secondary = mix(primary, bg, 0.38f);
        Color muted = disabledForeground != null ? disabledForeground : mix(primary, bg, 0.58f);
        Color border = mix(primary, bg, dark ? 0.78f : 0.82f);
        Color borderLight = mix(primary, bg, dark ? 0.66f : 0.70f);
        Color textOnAccent = relativeLuminance(ac) > 0.45 ? new Color(0x151515) : Color.WHITE;
        return new ThemePalette(bgDeep, bg, bgPanel, bgElevated, bgHover,
                ac, adjust(ac, 1.14f, 0.94f), adjust(ac, 0.78f, 1.04f),
                mix(ac, bg, 0.62f), new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), 40),
                textOnAccent, primary, secondary, muted, border, borderLight,
                new Color(0xD94A4A), new Color(0xD39B2A), new Color(0x3B82F6));
    }

    private static Color mix(Color first, Color second, float secondWeight) {
        float w = clamp01(secondWeight);
        return new Color(Math.round(first.getRed() * (1 - w) + second.getRed() * w),
                Math.round(first.getGreen() * (1 - w) + second.getGreen() * w),
                Math.round(first.getBlue() * (1 - w) + second.getBlue() * w));
    }


    public static ThemePalette fromAccent(Color accent) {
        float[] hsb = Color.RGBtoHSB(accent.getRed(), accent.getGreen(), accent.getBlue(), null);
        Color base = hsb(hsb[0], 0.16f, 0.065f);
        return build(base, accent);
    }


    private static float mixHue(float from, float to, float weight) {
        float diff = to - from;
        if (diff > 0.5f) {
            diff -= 1f;
        } else if (diff < -0.5f) {
            diff += 1f;
        }
        float result = from + diff * weight;
        if (result < 0) {
            result += 1f;
        } else if (result > 1) {
            result -= 1f;
        }
        return result;
    }

    private static Color hsb(float hue, float saturation, float brightness) {
        return Color.getHSBColor(
                clamp01(hue),
                clamp01(saturation),
                clamp01(brightness));
    }


    public static Color adjust(Color color, float brightnessFactor, float saturationFactor) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return hsb(hsb[0], hsb[1] * saturationFactor, hsb[2] * brightnessFactor);
    }

    public static Color lighten(Color color, float amount) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return hsb(hsb[0], hsb[1], hsb[2] + amount);
    }

    public static Color darken(Color color, float amount) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        return hsb(hsb[0], hsb[1], hsb[2] - amount);
    }


    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }


    public static double relativeLuminance(Color color) {
        double r = channelLuminance(color.getRed());
        double g = channelLuminance(color.getGreen());
        double b = channelLuminance(color.getBlue());
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channelLuminance(int value) {
        double normalized = value / 255.0;
        return normalized <= 0.03928
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }


    public static String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }


    public static Color parseHex(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() == 3) {

            StringBuilder expanded = new StringBuilder();
            for (char c : value.toCharArray()) {
                expanded.append(c).append(c);
            }
            value = expanded.toString();
        }
        if (!value.matches("[0-9a-fA-F]{6}")) {
            return null;
        }
        try {
            return new Color(Integer.parseInt(value, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
