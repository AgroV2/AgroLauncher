package by.agro.launcher.core;


public interface ProgressListener {

    void onProgress(String stage, long current, long total, String detail);

    default void onMessage(String message) {
    }

    ProgressListener NOOP = (stage, current, total, detail) -> {
    };
}
