package by.agro.launcher.version;

/**
 * Определение требуемой версии Java по идентификатору версии Minecraft.
 * Используется как резерв, когда в version.json нет блока javaVersion (старые версии).
 *
 * Соответствие (по официальным требованиям Mojang):
 *  1.16.5 и ниже  → Java 8
 *  1.17 – 1.20.4  → Java 17
 *  1.20.5 – 1.21  → Java 21
 *  новее          → Java 25 и выше
 */
public final class JavaRequirement {

    public static final int JAVA_8 = 8;
    public static final int JAVA_17 = 17;
    public static final int JAVA_21 = 21;
    public static final int JAVA_25 = 25;

    /**
     * Версии Java, которые лаунчер умеет скачивать.
     *
     * Список соответствует LTS-выпускам Adoptium. Требование версии из манифеста
     * округляется вверх до ближайшего значения: если Mojang попросит Java 24,
     * подойдёт установленная 25 — обратной совместимости достаточно.
     */
    private static final int[] SUPPORTED = {JAVA_8, JAVA_17, JAVA_21, JAVA_25};

    private JavaRequirement() {
    }

    public static int guessByVersionId(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return JAVA_8;
        }
        int[] parsed = parseNumeric(versionId);
        if (parsed == null) {
            // Снапшоты и новая схема нумерации (26.2 и подобные) — самая свежая Java.
            // Точное требование всё равно приходит из манифеста, это лишь резерв.
            return JAVA_25;
        }
        int minor = parsed[0];
        int patch = parsed[1];

        if (minor < 17) {
            return JAVA_8;
        }
        if (minor == 20 && patch >= 5) {
            return JAVA_21;
        }
        if (minor >= 21) {
            return JAVA_21;
        }
        return JAVA_17;
    }

    /**
     * Извлекает пару (minor, patch) из строк вида "1.21.1", "1.20", "1.8.9".
     * Возвращает null, если строка не похожа на релизную версию.
     */
    private static int[] parseNumeric(String versionId) {
        String[] parts = versionId.split("\\.");
        if (parts.length < 2 || !"1".equals(parts[0].trim())) {
            return null;
        }
        try {
            int minor = Integer.parseInt(parts[1].replaceAll("[^0-9].*$", "").trim());
            int patch = 0;
            if (parts.length > 2) {
                String patchRaw = parts[2].replaceAll("[^0-9].*$", "").trim();
                if (!patchRaw.isEmpty()) {
                    patch = Integer.parseInt(patchRaw);
                }
            }
            return new int[]{minor, patch};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Приводит требование к версии, которую лаунчер умеет скачивать.
     *
     * Округление идёт вверх: Java 24 из манифеста превращается в 25, потому что
     * запуск на более новой версии безопаснее, чем на старой — там просто может
     * не быть нужных флагов. Так, Minecraft 26.2 передаёт
     * --sun-misc-unsafe-memory-access, который появился только в Java 24,
     * и на Java 21 виртуальная машина откажется стартовать.
     *
     * Требования выше последнего известного выпуска не занижаются: если Mojang
     * попросит Java 30, лаунчер попробует скачать именно её.
     */
    public static int normalize(int majorVersion) {
        if (majorVersion <= 0) {
            return JAVA_21;
        }
        for (int supported : SUPPORTED) {
            if (majorVersion <= supported) {
                return supported;
            }
        }
        // требование новее всех известных — используем как есть
        return majorVersion;
    }

    /** Версии Java, доступные для скачивания через интерфейс настроек. */
    public static int[] supportedVersions() {
        return SUPPORTED.clone();
    }
}
