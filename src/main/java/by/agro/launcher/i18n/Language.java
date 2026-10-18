package by.agro.launcher.i18n;

import java.util.Locale;

public enum Language {

    RUSSIAN("ru", "Русский", new Locale("ru")),
    ENGLISH("en", "English", Locale.ENGLISH);

    private final String code;
    private final String displayName;
    private final Locale locale;

    Language(String code, String displayName, Locale locale) {
        this.code = code;
        this.displayName = displayName;
        this.locale = locale;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public Locale locale() {
        return locale;
    }

    public static Language fromCode(String code) {
        if (code != null) {
            for (Language language : values()) {
                if (language.code.equalsIgnoreCase(code)) {
                    return language;
                }
            }
        }
        return detectSystem();
    }


    public static Language detectSystem() {
        String language = Locale.getDefault().getLanguage();
        if ("ru".equals(language) || "be".equals(language) || "uk".equals(language)
                || "kk".equals(language)) {
            return RUSSIAN;
        }
        return ENGLISH;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
