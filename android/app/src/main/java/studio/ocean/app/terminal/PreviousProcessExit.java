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
    private static final String SUMMARY = "previous-process-exit.log";
    private static final String TRACE = "previous-process-trace.txt";
    private PreviousProcessExit() {}

    public static void capture(Context context) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ApplicationExitInfo> exits = manager.getHistoricalProcessExitReasons(
                    context.getPackageName(), 0, 1);
            if (exits.isEmpty()) return;
            ApplicationExitInfo exit = exits.get(0);
            TerminalDiagnosticBundle.initialize(context);
            File logs = new File(context.getFilesDir(), "logs/terminal-diagnostics");
            if (!logs.isDirectory()) logs.mkdirs();
            boolean traceAvailable = false;
            try (InputStream trace = exit.getTraceInputStream()) {
                if (trace != null) {
                    traceAvailable = true;
                    copyAll(trace, new File(logs, TRACE));
                }
            }
            String summary = "Previous OceanStudio process exit:\n"
                    + "Reason: " + reasonName(exit.getReason()) + " (" + exit.getReason() + ")\n"
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
            File file = new File(new File(context.getFilesDir(), "logs/terminal-diagnostics"), SUMMARY);
            return file.isFile() ? new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                    : "Previous OceanStudio process exit: unavailable\n";
        } catch (Exception error) {
            return "Previous OceanStudio process exit: unreadable: " + error + "\n";
        }
    }

    private static void copyAll(InputStream input, File output) throws Exception {
        try (FileOutputStream destination = new FileOutputStream(output, false)) {
            byte[] buffer = new byte[8192];
            while (true) {
                int count = input.read(buffer);
                if (count < 0) break;
                destination.write(buffer, 0, count);
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
    private static String reasonName(int r){switch(r){case ApplicationExitInfo.REASON_CRASH:return "REASON_CRASH";case ApplicationExitInfo.REASON_CRASH_NATIVE:return "REASON_CRASH_NATIVE";case ApplicationExitInfo.REASON_SIGNALED:return "REASON_SIGNALED";case ApplicationExitInfo.REASON_ANR:return "REASON_ANR";case ApplicationExitInfo.REASON_LOW_MEMORY:return "REASON_LOW_MEMORY";case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:return "REASON_EXCESSIVE_RESOURCE_USAGE";case ApplicationExitInfo.REASON_USER_REQUESTED:return "REASON_USER_REQUESTED";case ApplicationExitInfo.REASON_EXIT_SELF:return "REASON_EXIT_SELF";case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:return "REASON_INITIALIZATION_FAILURE";case ApplicationExitInfo.REASON_PERMISSION_CHANGE:return "REASON_PERMISSION_CHANGE";case ApplicationExitInfo.REASON_DEPENDENCY_DIED:return "REASON_DEPENDENCY_DIED";default:return "REASON_"+r;}}
}
