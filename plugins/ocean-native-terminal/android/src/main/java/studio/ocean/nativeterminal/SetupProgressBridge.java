package studio.ocean.nativeterminal;

/**
 * Bridges bootstrap progress messages to Capacitor listeners.
 */
public interface SetupProgressBridge {
    void onProgress(String message, int percent);
}
