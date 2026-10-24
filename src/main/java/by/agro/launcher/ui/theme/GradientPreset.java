package by.agro.launcher.ui.theme;

import by.agro.launcher.i18n.Strings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public enum GradientPreset {
    NONE("appearance.gradientNone", "gradient.none.desc"),
    BLAZING_FLAME("appearance.gradientBlazingFlame", "gradient.blazingFlame.desc",
            0xDC2626, 0xF97316, 0xFACC15),
    DEEP_SEA("appearance.gradientDeepSea", "gradient.deepSea.desc",
            0x0F172A, 0x0369A1, 0x22D3EE),
    RAINBOW("appearance.gradientRainbow", "gradient.rainbow.desc",
            0xEF4444, 0xF59E0B, 0x22C55E, 0x3B82F6, 0xA855F7),
    MONOCHROME("appearance.gradientMonochrome", "gradient.monochrome.desc",
            0x374151, 0x9CA3AF, 0xF3F4F6),
    AGED_WINE("appearance.gradientAgedWine", "gradient.agedWine.desc",
            0x773344, 0xC2A878, 0x191716),
    MOJITO("appearance.gradientMojito", "gradient.mojito.desc",
            0xB8C98A, 0x365447, 0x7650A8),
    NEON("appearance.gradientNeon", "gradient.neon.desc",
            0x00F5D4, 0xFE019A, 0x171717),
    STYLISH_ORANGE("appearance.gradientStylishOrange", "gradient.stylishOrange.desc",
            0xF5F0DC, 0xF0442E, 0x171717),
    ANCIENT("appearance.gradientAncient", "gradient.ancient.desc",
            0xF2C14E, 0x28666E, 0xF4EDE0),
    MATERIAL_YOU("appearance.gradientMaterialYou", "gradient.materialYou.desc",
            0xD8B4FE, 0xE9D5FF),
    CUSTOM("appearance.gradientCustom", "gradient.custom.desc");

    private final String nameKey;
    private final String descriptionKey;
    private final Color[] colors;

    GradientPreset(String nameKey, String descriptionKey, int... colors) {
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.colors = new Color[colors.length];
        for (int i = 0; i < colors.length; i++) this.colors[i] = new Color(colors[i]);
    }

    public String displayName() { return Strings.get(nameKey); }

    public String description() { return Strings.get(descriptionKey); }

    public Color[] colors(String customColors) {
        if (this != CUSTOM) return colors.clone();
        List<Color> parsed = new ArrayList<>();
        if (customColors != null) {
            for (String value : customColors.split(",")) {
                Color color = PaletteFactory.parseHex(value.trim());
                if (color != null && parsed.size() < 10) parsed.add(color);
            }
        }
        return parsed.toArray(Color[]::new);
    }

    public static GradientPreset fromId(String id) {
        if (id != null) {
            for (GradientPreset preset : values()) {
                if (preset.name().equalsIgnoreCase(id)) return preset;
            }
        }
        return NONE;
    }

    @Override public String toString() { return displayName(); }
}
