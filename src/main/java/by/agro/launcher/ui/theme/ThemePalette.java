package by.agro.launcher.ui.theme;

import java.awt.Color;
public final class ThemePalette {

    
    
    public final Color bgDeep;
    
    public final Color bgBase;
    
    public final Color bgPanel;
    
    public final Color bgElevated;
    
    public final Color bgHover;

    
    
    public final Color accent;
    
    public final Color accentLight;
    
    public final Color accentDark;
    
    public final Color accentDeep;
    
    public final Color accentGlow;
    
    public final Color textOnAccent;

    
    public final Color textPrimary;
    public final Color textSecondary;
    public final Color textMuted;

    
    public final Color border;
    public final Color borderLight;

    
    public final Color error;
    public final Color warning;
    public final Color info;

    public ThemePalette(Color bgDeep, Color bgBase, Color bgPanel, Color bgElevated, Color bgHover,
                        Color accent, Color accentLight, Color accentDark, Color accentDeep,
                        Color accentGlow, Color textOnAccent,
                        Color textPrimary, Color textSecondary, Color textMuted,
                        Color border, Color borderLight,
                        Color error, Color warning, Color info) {
        this.bgDeep = bgDeep;
        this.bgBase = bgBase;
        this.bgPanel = bgPanel;
        this.bgElevated = bgElevated;
        this.bgHover = bgHover;
        this.accent = accent;
        this.accentLight = accentLight;
        this.accentDark = accentDark;
        this.accentDeep = accentDeep;
        this.accentGlow = accentGlow;
        this.textOnAccent = textOnAccent;
        this.textPrimary = textPrimary;
        this.textSecondary = textSecondary;
        this.textMuted = textMuted;
        this.border = border;
        this.borderLight = borderLight;
        this.error = error;
        this.warning = warning;
        this.info = info;
    }

    
    public Color success() {
        return accent;
    }
}
