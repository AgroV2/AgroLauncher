package by.agro.launcher.jvm;

final class JavaCompatibility {
    private JavaCompatibility() {
    }

    static boolean isAutoCompatible(int required, int detected) {
        return required == 8 ? detected == 8 : detected >= required;
    }
}
