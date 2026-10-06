package by.agro.launcher.launch;

import by.agro.launcher.jvm.JavaSelection;

final class JavaRuntimeWarning {
    private JavaRuntimeWarning() {
    }

    static String forSelection(JavaSelection selection) {
        if (selection.source != JavaSelection.Mode.CUSTOM
                || selection.detectedMajor == selection.requiredMajor) {
            return null;
        }
        return "ПРЕДУПРЕЖДЕНИЕ: для выбранной версии Minecraft/Forge требуется Java "
                + selection.requiredMajor + ", но выбран пользовательский runtime Java "
                + selection.detectedMajor
                + ". Custom runtime может вызвать ошибку запуска; запуск будет продолжен.";
    }
}
