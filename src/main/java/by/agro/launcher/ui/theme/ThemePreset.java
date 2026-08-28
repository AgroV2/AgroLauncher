package by.agro.launcher.ui.theme;

import java.awt.Color;


public enum ThemePreset {

    EMERALD_DARK("Emerald Dark", 0x0D1117, 0x10B981,
            "theme.emeraldDark.desc"),

    MIDNIGHT_EMBER("Midnight Ember", 0x0B0E14, 0xF97316,
            "theme.midnightEmber.desc"),

    NOIR_ROSE("Noir Rose", 0x12100F, 0xF472B6,
            "theme.noirRose.desc"),

    VAULT_GOLD("Vault Gold", 0x14110A, 0xD4AF37,
            "theme.vaultGold.desc"),

    ABYSS_FROST("Abyss Frost", 0x0A1017, 0x38BDF8,
            "theme.abyssFrost.desc"),

    CRIMSON_CHALK("Crimson Chalk", 0x100C0C, 0xDC2626,
            "theme.crimsonChalk.desc"),

    ELECTRIC_PURPLE_CREAM("ElectricPurple Cream", 0x0F0B14, 0xA855F7,
            "theme.electricPurple.desc"),

    ROYAL_PURPLE_GOLD("RoyalPurple Gold", 0x120C1C, 0xFBBF24,
            "theme.royalPurple.desc"),

    DEEP_FOREST_FLAME("DeepForest Flame", 0x0A1210, 0xFB923C,
            "theme.deepForest.desc"),


    QT_SYSTEM("theme.qtSystem", 0x202124, 0x3DAEE9,
            "theme.qtSystem.desc"),


    CUSTOM("theme.custom", 0x0D1014, 0x10B981,
            "theme.customDesc");

    private final String displayName;
    private final Color base;
    private final Color accent;
    private final String descriptionKey;

    ThemePreset(String displayName, int baseRgb, int accentRgb, String descriptionKey) {
        this.displayName = displayName;
        this.base = new Color(baseRgb);
        this.accent = new Color(accentRgb);
        this.descriptionKey = descriptionKey;
    }

    public String displayName() {
        return isCustom() || isSystem()
                ? by.agro.launcher.i18n.Strings.get(displayName)
                : displayName;
    }

    public String description() {
        return by.agro.launcher.i18n.Strings.get(descriptionKey);
    }

    public Color baseColor() {
        return base;
    }

    public Color accentColor() {
        return accent;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public boolean isSystem() {
        return this == QT_SYSTEM;
    }
    public ThemePalette palette() {
        switch (this) {
            case QT_SYSTEM:
                return SystemPalette.read();
            case CUSTOM:
                return customPalette(accent);
            default:
                return PaletteFactory.build(base, accent);
        }
    }


    public static ThemePalette customPalette(Color accent) {
        return PaletteFactory.fromAccent(accent);
    }

    public static ThemePreset fromId(String id) {
        if (id == null || id.isBlank()) {
            return EMERALD_DARK;
        }
        for (ThemePreset preset : values()) {
            if (preset.name().equalsIgnoreCase(id) || preset.displayName.equalsIgnoreCase(id)) {
                return preset;
            }
        }
        return EMERALD_DARK;
    }


    public static ThemePreset[] builtIn() {
        ThemePreset[] all = values();
        ThemePreset[] result = new ThemePreset[all.length - 1];
        int index = 0;
        for (ThemePreset preset : all) {
            if (!preset.isCustom()) {
                result[index++] = preset;
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return displayName();
    }
}
