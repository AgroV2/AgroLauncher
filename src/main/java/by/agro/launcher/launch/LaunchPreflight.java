package by.agro.launcher.launch;

import by.agro.launcher.jvm.JavaSelection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LaunchPreflight {
    public final JavaSelection javaSelection;
    public final MemoryAdvisor.Advice memory;
    public final List<String> blockingErrors;

    public LaunchPreflight(JavaSelection javaSelection, MemoryAdvisor.Advice memory,
                           List<String> additionalErrors) {
        this.javaSelection = javaSelection;
        this.memory = memory;
        List<String> errors = new ArrayList<>(memory.blockingErrors);
        if (additionalErrors != null) errors.addAll(additionalErrors);
        this.blockingErrors = Collections.unmodifiableList(errors);
    }

    public void requireValid() throws IOException {
        if (!blockingErrors.isEmpty()) throw new IOException("Launch preflight failed: " + String.join("; ", blockingErrors));
    }
}
