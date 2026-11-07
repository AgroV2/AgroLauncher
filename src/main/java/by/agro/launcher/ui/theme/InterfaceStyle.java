package by.agro.launcher.ui.theme;


public enum InterfaceStyle {
    STANDARD("standard", 10, 10, 12, 9, 10, 8, 8, 5, 10, 1),
    MATERIAL_YOU("materialYou", 22, 26, 30, 24, 999, 18, 999, 8, 18, 2),
    LIQUID_GLASS("liquidGlass", 24, 28, 34, 26, 999, 18, 999, 8, 18, 2),
    FLUENT("fluent", 8, 8, 12, 8, 8, 8, 8, 4, 8, 2),
    APPLE("apple", 12, 12, 20, 12, 12, 10, 999, 6, 12, 2),
    GNOME("gnome", 12, 14, 22, 14, 16, 10, 999, 6, 14, 2),
    BREEZE("breeze", 6, 6, 8, 6, 6, 6, 6, 4, 6, 1),
    FROSTED_GLASS("frostedGlass", 18, 20, 26, 18, 20, 16, 999, 7, 16, 2),
    CRYSTAL_GLASS("crystalGlass", 12, 14, 16, 12, 12, 10, 10, 5, 12, 2),
    GLASSMORPHISM("glassmorphism", 24, 24, 32, 22, 24, 18, 999, 8, 20, 2);

    private final String id;
    private final int componentArc, menuArc, cardArc, navigationArc, buttonArc;
    private final int textArc, progressArc, checkArc, tabArc, focusWidth;

    InterfaceStyle(String id, int componentArc, int menuArc, int cardArc, int navigationArc,
                   int buttonArc, int textArc, int progressArc, int checkArc, int tabArc, int focusWidth) {
        this.id = id;
        this.componentArc = componentArc;
        this.menuArc = menuArc;
        this.cardArc = cardArc;
        this.navigationArc = navigationArc;
        this.buttonArc = buttonArc;
        this.textArc = textArc;
        this.progressArc = progressArc;
        this.checkArc = checkArc;
        this.tabArc = tabArc;
        this.focusWidth = focusWidth;
    }

    public boolean isGlass() {
        return this == LIQUID_GLASS || this == FROSTED_GLASS
                || this == CRYSTAL_GLASS || this == GLASSMORPHISM;
    }

    public String id() { return id; }
    public int componentArc() { return componentArc; }
    public int menuArc() { return menuArc; }
    public int cardArc() { return cardArc; }
    public int navigationArc() { return navigationArc; }
    public int buttonArc() { return buttonArc; }
    public int textArc() { return textArc; }
    public int progressArc() { return progressArc; }
    public int checkArc() { return checkArc; }
    public int tabArc() { return tabArc; }
    public int focusWidth() { return focusWidth; }

    public static InterfaceStyle fromId(String id) {
        if (id != null) {
            for (InterfaceStyle style : values()) {
                if (style.id.equalsIgnoreCase(id) || style.name().equalsIgnoreCase(id)) return style;
            }
        }
        return STANDARD;
    }
}
