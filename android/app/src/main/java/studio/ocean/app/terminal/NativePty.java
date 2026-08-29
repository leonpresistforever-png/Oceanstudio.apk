package studio.ocean.app.terminal;

/** JNI ownership boundary for a real Linux pseudoterminal and child process. */
public final class NativePty {
    static { System.loadLibrary("oceanpty"); }
    private NativePty() {}
    public static native long create(String executable, String[] arguments, String[] environment, String cwd, int rows, int columns);
    public static native int read(long handle, byte[] buffer);
    public static native int write(long handle, byte[] buffer, int length);
    public static native int resize(long handle, int rows, int columns, int pixelWidth, int pixelHeight);
    public static native int pollExit(long handle);
    public static native void signal(long handle, int signal);
    public static native void close(long handle);
}
