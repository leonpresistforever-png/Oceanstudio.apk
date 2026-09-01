package studio.ocean.app.terminal;

import jackpal.androidterm.emulatorview.TermSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Bridges the Apache-2.0 VT/xterm emulator to Ocean's service-owned local PTY. */
final class OceanEmulatorSession extends TermSession {
    private final Object pendingLock = new Object();
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private final BlockingInput idleInput = new BlockingInput();
    private TerminalSession transport;

    OceanEmulatorSession() {
        super(false);
        setDefaultUTF8Mode(true);
        setTermIn(idleInput);
        setTermOut(new OutputStream() { @Override public void write(int value) {} });
    }

    void attachTransport(TerminalSession value) { transport = value; }

    /** Must run on the UI thread, as required by the emulator screen model. */
    void feed(byte[] bytes, int length) {
        synchronized (pendingLock) {
            if (!isRunning()) { pending.write(bytes, 0, length); return; }
            flushPendingLocked();
            appendToEmulator(bytes, 0, length);
            notifyUpdate();
        }
    }

    @Override public void updateSize(int columns, int rows) {
        super.updateSize(columns, rows);
        synchronized (pendingLock) { flushPendingLocked(); }
        TerminalSession active = transport;
        if (active != null) active.resize(rows, columns, 0, 0);
    }

    @Override public void write(byte[] data, int offset, int count) {
        TerminalSession active = transport;
        if (active == null || count <= 0) return;
        byte[] exact = new byte[count];
        System.arraycopy(data, offset, exact, 0, count);
        active.write(exact);
    }

    private void flushPendingLocked() {
        if (!isRunning() || pending.size() == 0) return;
        byte[] bytes = pending.toByteArray();
        pending.reset();
        appendToEmulator(bytes, 0, bytes.length);
        notifyUpdate();
    }

    @Override public void finish() {
        idleInput.closeQuietly();
        if (isRunning()) super.finish();
    }

    private static final class BlockingInput extends InputStream {
        private boolean closed;
        @Override public synchronized int read() throws IOException {
            while (!closed) {
                try { wait(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
            }
            return -1;
        }
        synchronized void closeQuietly() { closed = true; notifyAll(); }
        @Override public void close() { closeQuietly(); }
    }
}
