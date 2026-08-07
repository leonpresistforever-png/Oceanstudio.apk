package studio.ocean.nativeterminal;

import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Manages a PTY master fd for bidirectional shell I/O with proper TTY semantics.
 */
public class PtySession {
    private static final String TAG = "PtySession";

    private final int masterFd;
    private final int pid;
    private final FileInputStream input;
    private final FileOutputStream output;
    private volatile boolean running = true;

    PtySession(int masterFd, int pid, int rows, int cols) throws IOException {
        this.masterFd = masterFd;
        this.pid = pid;
        FileDescriptor fd = createFileDescriptor(masterFd);
        this.input = new FileInputStream(fd);
        this.output = new FileOutputStream(fd);
        PtyBridge.nativeSetWindowSize(masterFd, rows, cols);
    }

    public int getPid() { return pid; }

    public FileInputStream getInput() { return input; }

    public void write(String data) throws IOException {
        output.write(data.getBytes("UTF-8"));
        output.flush();
    }

    public void resize(int cols, int rows) {
        PtyBridge.nativeSetWindowSize(masterFd, cols, rows);
    }

    public void kill() {
        running = false;
        try {
            android.os.Process.killProcess(pid);
        } catch (Exception e) {
            Log.w(TAG, "killProcess failed: " + e.getMessage());
        }
        try { input.close(); } catch (IOException ignored) {}
        try { output.close(); } catch (IOException ignored) {}
    }

    public int waitFor() {
        return PtyBridge.nativeWaitPid(pid);
    }

    public boolean isRunning() { return running; }

    public void setRunning(boolean running) { this.running = running; }

    private static FileDescriptor createFileDescriptor(int fd) throws IOException {
        try {
            FileDescriptor fileDescriptor = new FileDescriptor();
            Field descriptorField = FileDescriptor.class.getDeclaredField("descriptor");
            descriptorField.setAccessible(true);
            descriptorField.setInt(fileDescriptor, fd);
            return fileDescriptor;
        } catch (Exception e) {
            throw new IOException("Failed to wrap PTY fd: " + e.getMessage(), e);
        }
    }
}
