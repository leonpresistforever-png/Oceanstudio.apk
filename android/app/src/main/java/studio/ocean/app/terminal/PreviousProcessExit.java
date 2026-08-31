package studio.ocean.app.terminal;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Persists Android's account of the previous app-process death for device-only diagnosis. */
public final class PreviousProcessExit {
    private static final String SUMMARY = "previous-process-exit.txt";
    private static final String TRACE = "previous-process-crash.txt";
    private PreviousProcessExit() {}

    public static void capture(Context context) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(
                    context.getPackageName(), 0, 1);
            if (exits.isEmpty()) return;
            ApplicationExitInfo exit = exits.get(0);
            File logs = new File(context.getFilesDir(), "logs");
            if (!logs.isDirectory()) logs.mkdirs();
            boolean traceAvailable = false;
            try (InputStream trace = exit.getTraceInputStream()) {
                if (trace != null) {
                    traceAvailable = true;
                    copyLimited(trace, new File(logs, TRACE), 2 * 1024 * 1024);
                }
            }
            String summary = "Previous OceanStudio process exit:\n"
                    + "Reason: " + exit.getReason() + "\n"
                    + "Status: " + exit.getStatus() + "\n"
                    + "Description: " + safe(exit.getDescription()) + "\n"
                    + "Timestamp: " + exit.getTimestamp() + "\n"
                    + "Importance: " + exit.getImportance() + "\n"
                    + "PSS: " + exit.getPss() + " kB\n"
                    + "RSS: " + exit.getRss() + " kB\n"
                    + "Native trace available: " + (traceAvailable ? "yes" : "no") + "\n";
            write(new File(logs, SUMMARY), summary.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable error) {
            // Exit inspection is diagnostic-only and must never prevent normal startup.
            TerminalStartupLog.failure("Cannot inspect previous process exit", error);
        }
    }

    public static String read(Context context) {
        try {
            File file = new File(new File(context.getFilesDir(), "logs"), SUMMARY);
            return file.isFile() ? new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                    : "Previous OceanStudio process exit: unavailable\n";
        } catch (Exception error) {
            return "Previous OceanStudio process exit: unreadable: " + error + "\n";
        }
    }

    private static void copyLimited(InputStream input, File output, int maximum) throws Exception {
        try (FileOutputStream destination = new FileOutputStream(output, false)) {
            byte[] buffer = new byte[8192];
            int total = 0;
            while (total < maximum) {
                int count = input.read(buffer, 0, Math.min(buffer.length, maximum - total));
                if (count < 0) break;
                destination.write(buffer, 0, count);
                total += count;
            }
            destination.getFD().sync();
        }
    }

    private static void write(File file, byte[] value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(value);
            output.getFD().sync();
        }
    }

    private static String safe(String value) { return value == null ? "" : value.replace('\n', ' '); }
}
