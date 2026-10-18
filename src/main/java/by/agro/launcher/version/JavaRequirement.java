package by.agro.launcher.version;


public final class JavaRequirement {

    public static final int JAVA_8 = 8;
    public static final int JAVA_17 = 17;
    public static final int JAVA_21 = 21;
    public static final int JAVA_25 = 25;

    private static final int[] SUPPORTED = {JAVA_8, JAVA_17, JAVA_21, JAVA_25};

    private JavaRequirement() {
    }

    public static int guessByVersionId(String versionId) {
        if (versionId == null || versionId.isBlank()) {
            return JAVA_8;
        }
        int[] parsed = parseNumeric(versionId);
        if (parsed == null) {
        
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

  
    public static int normalize(int majorVersion) {
     
        return majorVersion > 0 ? majorVersion : JAVA_21;
    }

 
    public static int[] supportedVersions() {
        return SUPPORTED.clone();
    }
}
