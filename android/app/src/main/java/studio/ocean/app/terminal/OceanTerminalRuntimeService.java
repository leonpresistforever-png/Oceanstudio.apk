package studio.ocean.app.terminal;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import studio.ocean.app.OceanPaths;

/** Service-owned PTY registry; sessions survive Activity recreation while the service lives. */
public final class OceanTerminalRuntimeService extends Service {
    public final class LocalBinder extends Binder { public OceanTerminalRuntimeService service(){return OceanTerminalRuntimeService.this;} }
    private final LocalBinder binder=new LocalBinder(); private final Map<String,TerminalSession> sessions=new LinkedHashMap<>();
    @Override public void onCreate(){super.onCreate();TerminalStartupLog.stage("02","OceanTerminalRuntimeService.onCreate");}
    @Override public IBinder onBind(Intent intent){TerminalStartupLog.stage("03","runtime service bound");return binder;}
    public synchronized TerminalSession firstRunning(){for(TerminalSession session:sessions.values())if(session.isRunning())return session;return null;}
    public synchronized TerminalSession createSession(int rows,int columns) throws IOException {
        OceanPaths paths=new OceanPaths(this);
        TerminalStartupLog.stage("04","bootstrap marker present="+paths.runtimeMarker().isFile());
        if(!OceanRuntimeState.isInstalled(this)){TerminalStartupLog.stage("05","bootstrap install begin");new OceanBootstrapInstaller(this).install();}
        // Repair the directory contract for runtimes installed by older APKs,
        // which activated PREFIX without ever creating the child working HOME.
        paths.ensureDirectoryContract();
        File oceanShell=new File(paths.prefix(),"bin/bash");
        TerminalStartupLog.environment(this,oceanShell.getAbsolutePath());
        OceanRuntimeValidator.Result validation=OceanRuntimeValidator.validate(paths);validation.requireValid();
        return startSession(oceanShell.getAbsolutePath(),paths.home(),rows,columns,false);
    }
    public synchronized TerminalSession createRecoverySession(int rows,int columns) throws IOException {
        OceanPaths paths=new OceanPaths(this);File shell=new File("/system/bin/sh");
        if(!shell.isFile()||!shell.canExecute())throw new IOException("Android recovery shell unavailable");
        TerminalStartupLog.stage("R1","explicit recovery shell requested");
        return startSession(shell.getAbsolutePath(),paths.home(),rows,columns,true);
    }
    private TerminalSession startSession(String shell,File cwd,int rows,int columns,boolean recovery) throws IOException {
        String[] argv={shell,"-i"};
        TerminalStartupLog.stage("10","create PTY begin rows="+rows+" columns="+columns);
        long handle=NativePty.create(shell,argv,OceanEnvironment.create(this,shell),cwd.getAbsolutePath(),rows,columns);
        if(handle==0){int error=NativePty.lastErrno();TerminalStartupLog.stage("10F","PTY create failed errno="+error);throw new IOException("PTY creation failed, errno="+error);}
        int pid=NativePty.pid(handle);TerminalStartupLog.stage("12","fork success pid="+pid);
        TerminalSession session;
        try{session=new TerminalSession(handle);}catch(Throwable error){NativePty.close(handle);NativePty.destroy(handle);throw new IOException("Cannot start PTY reader",error);}
        sessions.put(session.id,session);TerminalStartupLog.stage("13","child exec requested shell="+shell+" recovery="+recovery);return session;
    }
    public synchronized void closeSession(String id){TerminalSession session=sessions.remove(id);if(session!=null)session.close();}
    @Override public void onDestroy(){for(TerminalSession session:sessions.values())session.close();sessions.clear();super.onDestroy();}
}
