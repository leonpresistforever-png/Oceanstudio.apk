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
    @Override public IBinder onBind(Intent intent){return binder;}
    public synchronized TerminalSession firstRunning(){for(TerminalSession session:sessions.values())if(session.isRunning())return session;return null;}
    public synchronized TerminalSession createSession(int rows,int columns) throws IOException {
        OceanPaths paths=new OceanPaths(this);
        if(!OceanRuntimeState.isInstalled(this))new OceanBootstrapInstaller(this).install();
        File oceanShell=new File(paths.prefix(),"bin/bash");
        if(!OceanRuntimeState.isInstalled(this))throw new IOException("Ocean bootstrap verification failed");
        String shell=oceanShell.getAbsolutePath();
        String[] argv={shell,"-i"}; long handle=NativePty.create(shell,argv,OceanEnvironment.create(this,shell),paths.home().getAbsolutePath(),rows,columns);
        if(handle==0)throw new IOException("openpty/fork/exec failed"); TerminalSession session=new TerminalSession(handle);sessions.put(session.id,session);return session;
    }
    public synchronized void closeSession(String id){TerminalSession session=sessions.remove(id);if(session!=null)session.close();}
    @Override public void onDestroy(){for(TerminalSession session:sessions.values())session.close();sessions.clear();super.onDestroy();}
}
