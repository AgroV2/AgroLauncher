package by.agro.launcher.diagnostics;

import java.util.ArrayList;
import java.util.List;

public final class ModIdentity {
    public String fileName;
    public String id;
    public String name;
    public List<String> packageRoots = new ArrayList<>();

    public ModIdentity() {
    }

    public ModIdentity(String fileName, String id, String name, List<String> packageRoots) {
        this.fileName = fileName;
        this.id = id;
        this.name = name;
        if (packageRoots != null) this.packageRoots.addAll(packageRoots);
    }

    public String displayName() {
        if (name != null && !name.isBlank()) return name;
        if (id != null && !id.isBlank()) return id;
        return fileName == null ? "" : fileName;
    }
}
