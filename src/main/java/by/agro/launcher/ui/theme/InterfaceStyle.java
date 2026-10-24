package by.agro.launcher.ui.theme;

public enum InterfaceStyle {

    STANDARD("standard"),
    MATERIAL_YOU("materialYou"),
    LIQUID_GLASS("liquidGlass");

    private final String id;

    InterfaceStyle(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static InterfaceStyle fromId(String id) {
        if (id != null) {
            for (InterfaceStyle style : values()) {
                if (style.id.equalsIgnoreCase(id) || style.name().equalsIgnoreCase(id)) {
                    return style;
                }
            }
        }
        return STANDARD;
    }
}
