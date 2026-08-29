package studio.ocean.app.terminal;

/** JNI boundary for a real Linux pseudoterminal and child process. */
public final class NativePty {
    static { System.loadLibrary("oceanpty"); }
    private NativePty() {}
    static native int[] create(String[] command, String[] environment, String cwd, int rows, int columns);
    static native int read(int fd, byte[] destination);
    static native int write(int fd, byte[] source);
    static native void resize(int fd, int rows, int columns);
    static native int waitFor(int pid);
    static native int signal(int pid, int signalNumber);
    static native void close(int fd);
}
