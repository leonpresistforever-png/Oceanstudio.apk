package studio.ocean.nativeterminal;

import android.util.Log;

import java.io.IOException;

/**
 * JNI bridge allocating a real pseudoterminal (PTY) via openpty().
 * Enables interactive shells (bash, ssh, htop, nano) unlike ProcessBuilder pipes.
 */
public final class PtyBridge {
    private static final String TAG = "PtyBridge";
    private static volatile boolean loaded = false;
    private static volatile boolean available = false;

    static {
        try {
            System.loadLibrary("oceanpty");
            loaded = true;
            available = true;
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "oceanpty native library not available, falling back to pipes: " + e.getMessage());
            loaded = false;
            available = false;
        }
    }

    private PtyBridge() {}

    public static boolean isAvailable() {
        return available;
    }

    /** @return int[]{masterFd, pid} or null on failure */
    public static native int[] nativeCreatePty(
        String[] cmd,
        String[] env,
        String cwd,
        int rows,
        int cols
    );

    public static native void nativeSetWindowSize(int masterFd, int rows, int cols);

    public static native int nativeWaitPid(int pid);

    public static PtySession createSession(
        String[] cmd,
        String[] env,
        String cwd,
        int rows,
        int cols
    ) {
        if (!available) return null;
        int[] result = nativeCreatePty(cmd, env, cwd, rows, cols);
        if (result == null || result.length < 2) return null;
        try {
            return new PtySession(result[0], result[1], rows, cols);
        } catch (IOException e) {
            Log.e(TAG, "Failed to open PTY session: " + e.getMessage());
            return null;
        }
    }
}
