package studio.ocean.app.terminal;

import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import studio.ocean.app.BuildConfig;
import studio.ocean.app.OceanPaths;

/** Persistent, credential-free breadcrumbs for terminal startup failures. */
public final class TerminalStartupLog {
    private static final Object LOCK = new Object();
    private static volatile String lastStage = "not-started";
    private static File file;
    private static Thread.UncaughtExceptionHandler installedHandler;
    private TerminalStartupLog() {}

    public static void initialize(Context context) {
        TerminalDiagnosticBundle.initialize(context);
        synchronized (LOCK) {
            File directory = new File(context.getFilesDir(), "logs");
            if (!directory.isDirectory()) directory.mkdirs();
            file = new File(directory, "terminal-startup.log");
        }
        stage("01", "TerminalActivity.onCreate build=" + BuildConfig.OCEAN_BUILD_COMMIT
            + " bootstrap=" + BuildConfig.OCEAN_BOOTSTRAP_VERSION
            + " bootstrapBuild=" + BuildConfig.OCEAN_BOOTSTRAP_BUILD_COMMIT
            + " sha256=" + BuildConfig.OCEAN_BOOTSTRAP_SHA256
            + " api=" + Build.VERSION.SDK_INT + " abi=" + OceanEnvironment.architecture());
    }

    public static void environment(Context context, String shell) {
        OceanPaths p = new OceanPaths(context);
        stage("06", "filesDir=" + context.getFilesDir() + " HOME=" + p.home()
            + " PREFIX=" + p.prefix() + " TMPDIR=" + p.temp()
            + " PATH=" + p.prefix() + "/bin:/system/bin:/system/xbin shell=" + shell);
    }

    public static void stage(String number, String message) {
        lastStage = number + " " + message;
        append("[" + number + "] " + message + "\n");
        TerminalDiagnosticBundle.log("startup.log","[J"+number+"] "+message);
    }

    public static void failure(String message, Throwable error) {
        StringWriter trace = new StringWriter();
        if (error != null) error.printStackTrace(new PrintWriter(trace));
        append("[FAIL] lastStage=" + lastStage + " message=" + message + "\n" + trace + "\n");
    }

    public static Thread.UncaughtExceptionHandler installCrashCapture() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        installedHandler = (thread, error) -> {
            TerminalDiagnosticBundle.javaCrash(thread,error);
            failure("uncaught thread=" + thread.getName() + " class=" + error.getClass().getName()
                + " message=" + error.getMessage(), error);
            if (previous != null) previous.uncaughtException(thread, error);
        };
        Thread.setDefaultUncaughtExceptionHandler(installedHandler);
        return previous;
    }

    public static void restoreCrashCapture(Thread.UncaughtExceptionHandler previous) {
        if (Thread.getDefaultUncaughtExceptionHandler() == installedHandler)
            Thread.setDefaultUncaughtExceptionHandler(previous);
        installedHandler = null;
    }

    public static File file(Context context) {
        return new File(new File(context.getFilesDir(), "logs"), "terminal-startup.log");
    }

    private static void append(String text) {
        synchronized (LOCK) {
            if (file == null) return;
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            } catch (Exception ignored) { /* Diagnostics must never crash startup. */ }
        }
    }
}
