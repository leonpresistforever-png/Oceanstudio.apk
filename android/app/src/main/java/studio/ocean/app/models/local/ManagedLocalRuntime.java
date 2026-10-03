package studio.ocean.app.models.local;

import java.io.*;
import java.util.concurrent.TimeUnit;

/** Owns one real server. Late exit events can never clear a replacement server. */
public final class ManagedLocalRuntime {
    public interface ExitListener { void exited(String diagnostic); }
    private Process process;
    private volatile String lastExit;

    public synchronized Process start(ProcessBuilder builder, File log, ExitListener listener) throws IOException {
        stop();
        log.getParentFile().mkdirs();
        File previous = new File(log.getParentFile(), log.getName() + ".previous");
        if (log.exists()) { previous.delete(); log.renameTo(previous); }
        Process started = builder.redirectErrorStream(true).start();
        process = started; lastExit = null;
        Thread output = new Thread(() -> {
            try (InputStream in = started.getInputStream(); OutputStream out = new FileOutputStream(log)) {
                byte[] bytes = new byte[8192]; int count; long written = 0;
                while ((count = in.read(bytes)) != -1) {
                    if (written < 2 * 1024 * 1024) { out.write(bytes, 0, count); out.flush(); written += count; }
                }
            } catch (IOException ignored) { }
        }, "ocean-inference-output");
        output.setDaemon(true); output.start();
        Thread monitor = new Thread(() -> {
            try {
                int exit = started.waitFor();
                output.join(1000);
                synchronized (ManagedLocalRuntime.this) {
                    if (process != started) return;
                    process = null;
                    lastExit = "Inference server exited (code " + exit + "). " + tail(log);
                }
                if (listener != null) listener.exited(lastExit);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }, "ocean-inference-monitor");
        monitor.setDaemon(true); monitor.start();
        return started;
    }

    public synchronized boolean isRunning() { return process != null && process.isAlive(); }
    public synchronized Process process() { return process; }
    public String lastExit() { return lastExit; }
    public synchronized void stop() {
        Process old = process; process = null;
        if (old == null) return;
        old.destroy();
        try { if (!old.waitFor(3, TimeUnit.SECONDS)) old.destroyForcibly(); }
        catch (InterruptedException interrupted) { old.destroyForcibly(); Thread.currentThread().interrupt(); }
    }
    public static String tail(File log) {
        try (RandomAccessFile input = new RandomAccessFile(log, "r")) {
            input.seek(Math.max(0, input.length() - 2048));
            byte[] bytes = new byte[(int) (input.length() - input.getFilePointer())]; input.readFully(bytes);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException error) { return "Runtime log is unavailable."; }
    }
}
