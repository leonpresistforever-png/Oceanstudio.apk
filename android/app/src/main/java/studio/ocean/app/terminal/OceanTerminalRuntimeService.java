package studio.ocean.app.terminal;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import studio.ocean.app.OceanPaths;

/** Owns bootstrap installation and PTY sessions independently of Activity lifetimes. */
public final class OceanTerminalRuntimeService extends Service {
    public enum RuntimeState {
        UNINITIALIZED, CHECKING, INSTALL_REQUIRED, INSTALLING,
        VERIFYING_STAGING, ACTIVATING, VERIFYING_ACTIVE, READY, FAILED
    }
    public interface SessionCallback {
        void onProgress(RuntimeState state, String detail, long completed, long total);
        void onReady(TerminalSession session);
        void onFailure(Throwable error);
    }
    public interface CommandCallback {
        void onOutput(byte[] bytes, int length);
        void onExit(int exitCode);
        void onFailure(Throwable error);
    }
    public final class LocalBinder extends Binder {
        public OceanTerminalRuntimeService service() { return OceanTerminalRuntimeService.this; }
    }

    private final Object stateLock = new Object();
    private final LocalBinder binder = new LocalBinder();
    private final Map<String, TerminalSession> sessions = new LinkedHashMap<>();
    private final ExecutorService bootstrapWorker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ocean-bootstrap-worker");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Handler main = new Handler(Looper.getMainLooper());
    private RuntimeState runtimeState = RuntimeState.UNINITIALIZED;
    private boolean installScheduled;
    private OceanIpcServer ipcServer;

    @Override public void onCreate() {
        super.onCreate();
        TerminalDiagnosticBundle.initialize(this);
        TerminalDiagnosticBundle.log("startup.log", "[J03] RuntimeService.onCreate");
        TerminalDiagnosticBundle.state("NEW", "CHECKING", "service created");
        TerminalStartupLog.stage("02", "OceanTerminalRuntimeService.onCreate");
        try {
            ipcServer = new OceanIpcServer(this);
            ipcServer.start();
        } catch (Throwable t) {
            TerminalDiagnosticBundle.log("startup.log", "[IPC] Failed to start IPC server: " + t.getMessage());
        }
    }
    @Override public IBinder onBind(Intent intent) {
        TerminalStartupLog.stage("03", "runtime service bound");
        return binder;
    }

    public TerminalSession firstRunning() {
        synchronized (stateLock) {
            for (TerminalSession session : sessions.values()) if (session.isRunning()) return session;
            return null;
        }
    }

    /** Returns immediately. All hashing, extraction, cleanup, and validation run on bootstrapWorker. */
    public void requestTerminalSession(int rows, int columns, SessionCallback callback) {
        TerminalSession existing = firstRunning();
        if (existing != null) { main.post(() -> callback.onReady(existing)); return; }
        synchronized (stateLock) {
            if (installScheduled) {
                // The single worker serializes requests; the later request observes READY rather than extracting again.
                bootstrapWorker.execute(() -> completeRequest(rows, columns, callback));
                return;
            }
            installScheduled = true;
            runtimeState = RuntimeState.CHECKING;
        }
        try { startService(new Intent(this, OceanTerminalRuntimeService.class)); }
        catch (Throwable ignored) {}
        bootstrapWorker.execute(() -> {
            try { completeRequest(rows, columns, callback); }
            finally {
                synchronized (stateLock) { installScheduled = false; }
            }
        });
    }

    private void completeRequest(int rows, int columns, SessionCallback callback) {
        try {
            TerminalSession existing = firstRunning();
            if (existing != null) { main.post(() -> callback.onReady(existing)); return; }
            OceanPaths paths = new OceanPaths(this);
            progress(callback, RuntimeState.CHECKING, "Checking Ocean runtime", 0, 0);
            TerminalDiagnosticBundle.log("runtime-validation.log", "[J05] runtime validation begin marker=" + paths.runtimeMarker().isFile());
            if (!OceanRuntimeState.isInstalled(this)) {
                progress(callback, RuntimeState.INSTALL_REQUIRED, "Ocean runtime installation required", 0, 0);
                OceanBootstrapInstaller installer = new OceanBootstrapInstaller(this);
                installer.install((stage, detail, completed, total) -> progress(callback, map(stage), detail, completed, total));
            }
            progress(callback, RuntimeState.VERIFYING_ACTIVE, "Verifying installed runtime", 0, 0);
            paths.ensureDirectoryContract();
            File shell = new File(paths.prefix(), "bin/bash");
            TerminalStartupLog.environment(this, shell.getAbsolutePath());
            OceanRuntimeValidator.validate(paths).requireValid();
            progress(callback, RuntimeState.READY, "Starting Ocean Bash", 0, 0);
            TerminalSession session = startSession(shell.getAbsolutePath(), new String[]{shell.getAbsolutePath(), "-i"}, paths.home(), rows, columns, false);
            main.post(() -> callback.onReady(session));
        } catch (Throwable error) {
            synchronized (stateLock) { runtimeState = RuntimeState.FAILED; }
            TerminalStartupLog.failure("Async Ocean runtime setup failed", error);
            main.post(() -> callback.onFailure(error));
        }
    }

    private RuntimeState map(OceanBootstrapInstaller.Stage stage) {
        switch (stage) {
            case INSTALLING: return RuntimeState.INSTALLING;
            case VERIFYING_STAGING: return RuntimeState.VERIFYING_STAGING;
            case ACTIVATING: return RuntimeState.ACTIVATING;
            case VERIFYING_ACTIVE: return RuntimeState.VERIFYING_ACTIVE;
            default: return RuntimeState.CHECKING;
        }
    }
    private void progress(SessionCallback callback, RuntimeState state, String detail, long completed, long total) {
        synchronized (stateLock) { runtimeState = state; }
        TerminalDiagnosticBundle.updateStage(this, state + ": " + detail);
        main.post(() -> callback.onProgress(state, detail, completed, total));
    }

    public TerminalSession createRecoverySession(int rows, int columns) throws IOException {
        OceanPaths paths = new OceanPaths(this); File shell = new File("/system/bin/sh");
        if (!shell.isFile() || !shell.canExecute()) throw new IOException("Android recovery shell unavailable");
        paths.ensureDirectoryContract();
        return startSession(shell.getAbsolutePath(), new String[]{shell.getAbsolutePath(), "-i"}, paths.home(), rows, columns, true);
    }
    public TerminalSession createDiagnosticOceanSession(int rows, int columns) throws IOException {
        OceanPaths paths = new OceanPaths(this); OceanRuntimeValidator.validate(paths).requireValid();
        File shell = new File(paths.prefix(), "bin/bash");
        return startSession(shell.getAbsolutePath(), new String[]{shell.getAbsolutePath(), "--noprofile", "--norc"}, paths.home(), rows, columns, false);
    }
    public void requestDiagnosticOceanSession(int rows, int columns, SessionCallback callback) {
        bootstrapWorker.execute(() -> {
            try {
                if (!OceanRuntimeState.isInstalled(this)) throw new IOException("Ocean runtime is not ready; install or repair it first");
                progress(callback, RuntimeState.VERIFYING_ACTIVE, "Verifying Ocean runtime", 0, 0);
                TerminalSession session = createDiagnosticOceanSession(rows, columns);
                main.post(() -> callback.onReady(session));
            } catch (Throwable error) { main.post(() -> callback.onFailure(error)); }
        });
    }
    /** Runs an agent-approved command through the same native PTY/runtime owner as the terminal UI. */
    public void requestCommand(String command, CommandCallback callback) {
        bootstrapWorker.execute(() -> {
            try {
                OceanPaths paths=new OceanPaths(this);
                if(!OceanRuntimeState.isInstalled(this))new OceanBootstrapInstaller(this).install();
                paths.ensureDirectoryContract(); OceanRuntimeValidator.validate(paths).requireValid();
                File shell=new File(paths.prefix(),"bin/bash");
                TerminalSession session=startSession(shell.getAbsolutePath(),new String[]{shell.getAbsolutePath(),"-lc",command},paths.home(),24,80,false);
                session.addListener(new TerminalSession.Listener(){
                    @Override public void onOutput(byte[] bytes,int length){
                        // TerminalSession reuses its native read buffer. Copy before crossing
                        // threads so a later PTY read cannot corrupt agent tool output.
                        byte[] output=java.util.Arrays.copyOf(bytes,length);
                        main.post(()->callback.onOutput(output,output.length));
                    }
                    @Override public void onExit(int exitCode){main.post(()->callback.onExit(exitCode));}
                });
            } catch(Throwable error){main.post(()->callback.onFailure(error));}
        });
    }
    private TerminalSession startSession(String shell, String[] argv, File cwd, int rows, int columns, boolean recovery) throws IOException {
        TerminalDiagnosticBundle.state("VALIDATING", "STARTING", "shell=" + shell + " recovery=" + recovery);
        long handle = NativePty.create(shell, argv, OceanEnvironment.create(this, shell, recovery), cwd.getAbsolutePath(), rows, columns, TerminalDiagnosticBundle.nativeLog(this).getAbsolutePath());
        if (handle == 0) {
            String detail = NativePty.lastError();
            int err = NativePty.lastErrno();
            throw new IOException("PTY start failed: " + (detail != null && !detail.isEmpty() && !"None".equals(detail) ? detail : ("errno=" + err)));
        }
        TerminalSession session;
        try { session = new TerminalSession(this, handle, this::removeCompletedSession); }
        catch (Throwable error) { NativePty.signal(handle, 15); NativePty.close(handle); NativePty.waitExit(handle); NativePty.destroy(handle); throw new IOException("Cannot start PTY workers", error); }
        synchronized (stateLock) { sessions.put(session.id, session); }
        session.startWorkers();
        return session;
    }
    public void closeSession(String id) { TerminalSession session; synchronized (stateLock) { session = sessions.remove(id); } if (session != null) session.close(); }
    private void removeCompletedSession(TerminalSession session) {
        boolean empty;
        synchronized (stateLock) { if (sessions.get(session.id) == session) sessions.remove(session.id); empty = sessions.isEmpty() && !installScheduled; }
        if (empty) stopSelf();
    }
    @Override public void onDestroy() {
        if (ipcServer != null) {
            try { ipcServer.stop(); } catch (Throwable ignored) {}
            ipcServer = null;
        }
        TerminalSession[] active;
        synchronized (stateLock) { active = sessions.values().toArray(new TerminalSession[0]); sessions.clear(); }
        for (TerminalSession session : active) session.close();
        bootstrapWorker.shutdown();
        super.onDestroy();
    }
}
