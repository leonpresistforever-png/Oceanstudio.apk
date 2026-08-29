package studio.ocean.app.terminal;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.io.IOException;

/** Bound owner of real PTY sessions, independent of MainActivity recreation. */
public final class OceanTerminalRuntimeService extends Service {
    public final class RuntimeBinder extends Binder { public OceanTerminalRuntimeService service(){ return OceanTerminalRuntimeService.this; } }
    private final RuntimeBinder binder = new RuntimeBinder();
    private final TerminalSessionRegistry registry = new TerminalSessionRegistry();
    private OceanEnvironment environment;
    @Override public void onCreate() { super.onCreate(); environment=new OceanEnvironment(this); }
    @Override public IBinder onBind(Intent intent) { return binder; }

    /** Launches the honest Android recovery shell until a signed Ocean bootstrap exists. */
    public TerminalSession createRecoverySession(int rows,int columns) throws IOException {
        environment.ensureFilesystem();
        String shell="/system/bin/sh";
        TerminalSession session=new TerminalSession(new String[]{shell},environment.variables(shell),environment.paths().home().getAbsolutePath(),rows,columns);
        registry.add(session); return session;
    }
    public TerminalSession session(String id) { return registry.get(id); }
    public void closeSession(String id) { registry.remove(id); }
    @Override public void onDestroy() { registry.closeAll(); super.onDestroy(); }
}
