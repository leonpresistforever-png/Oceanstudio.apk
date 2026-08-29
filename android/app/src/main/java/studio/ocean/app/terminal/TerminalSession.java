package studio.ocean.app.terminal;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** A live PTY session. Output is produced only by the child connected to the PTY. */
public final class TerminalSession {
    public interface Listener { void onOutput(byte[] bytes, int length); void onExit(int exitCode); }
    public final String id=UUID.randomUUID().toString(); public final long startedAt=System.currentTimeMillis();
    private final long handle; private final Thread reader; private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream scrollback=new ByteArrayOutputStream();
    private volatile boolean running=true; private volatile int exitCode=-1;

    TerminalSession(long handle) {
        this.handle=handle;
        reader=new Thread(() -> { byte[] buffer=new byte[8192]; while(running){int count=NativePty.read(handle,buffer);if(count>0){appendScrollback(buffer,count);for(Listener listener:listeners)listener.onOutput(buffer,count);}else break;} int code=NativePty.pollExit(handle);exitCode=code<0?255:code;running=false;NativePty.close(handle);for(Listener listener:listeners)listener.onExit(exitCode); },"ocean-pty-reader-"+id);
        reader.start();
    }
    public void addListener(Listener listener){listeners.add(listener);byte[] snapshot; synchronized(scrollback){snapshot=scrollback.toByteArray();}if(snapshot.length>0)listener.onOutput(snapshot,snapshot.length);}
    public void removeListener(Listener listener){listeners.remove(listener);}
    public boolean write(String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);return running&&NativePty.write(handle,bytes,bytes.length)==bytes.length;}
    public void resize(int rows,int columns,int width,int height){if(running)NativePty.resize(handle,rows,columns,width,height);}
    public void interrupt(){if(running)NativePty.signal(handle,2);}
    public void close(){if(!running)return;NativePty.signal(handle,15);}
    public int pid(){return NativePty.pid(handle);}
    public LocalProcessDiagnostics.Snapshot diagnostics(String oceanPrefix){return LocalProcessDiagnostics.inspect(pid(),oceanPrefix);}
    public boolean isRunning(){return running;} public int getExitCode(){return exitCode;}
    private void appendScrollback(byte[] bytes,int length){synchronized(scrollback){if(scrollback.size()+length>200000){byte[] old=scrollback.toByteArray();scrollback.reset();int keep=Math.min(old.length,150000);scrollback.write(old,old.length-keep,keep);}scrollback.write(bytes,0,length);}}
}
