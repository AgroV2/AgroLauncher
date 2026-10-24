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
        return by.agro.launcher.i18n.Strings.get("launch.customJavaWarning", selection.requiredMajor, selection.detectedMajor);
    }
}
