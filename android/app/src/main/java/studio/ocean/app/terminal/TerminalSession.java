package studio.ocean.app.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** A live PTY session. Native ownership ends only after the reader has stopped. */
public final class TerminalSession {
    public interface Listener { void onOutput(byte[] bytes, int length); void onExit(int exitCode); }
    public final String id=UUID.randomUUID().toString(); public final long startedAt=System.currentTimeMillis();
    private final long handle; private final Thread reader; private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream scrollback=new ByteArrayOutputStream();
    private final AtomicBoolean disposed=new AtomicBoolean(false);
    private volatile boolean running=true; private volatile int exitCode=-1;

    TerminalSession(long handle) {
        if(handle==0)throw new IllegalArgumentException("Native PTY handle is zero");
        this.handle=handle;
        reader=new Thread(this::readLoop,"ocean-pty-reader-"+id);
        TerminalDiagnosticBundle.log("startup.log","[J11] reader startup requested session="+id+" handle=0x"+Long.toHexString(handle));
        reader.start();
    }
    private void readLoop(){
        TerminalStartupLog.stage("14","parent PTY read loop started pid="+pid());TerminalDiagnosticBundle.state("EXECUTING","RUNNING","reader running session="+id+" pid="+pid());
        byte[] buffer=new byte[8192]; boolean first=true;
        try{
            while(running){
                int count=NativePty.read(handle,buffer);
                if(count>0){
                    if(first){first=false;TerminalStartupLog.stage("15","first PTY bytes received count="+count);}
                    appendScrollback(buffer,count);
                    for(Listener listener:listeners)listener.onOutput(buffer,count);
                }else break;
            }
            int code=NativePty.pollExit(handle);exitCode=code<0?255:code;
        }catch(Throwable error){
            exitCode=255;TerminalStartupLog.failure("PTY reader failed",error);
        }finally{
            running=false;
            TerminalStartupLog.stage("16","PTY reader stopped exit="+exitCode);TerminalDiagnosticBundle.state("RUNNING","EXITING","session="+id+" exit="+exitCode);
            for(Listener listener:listeners)listener.onExit(exitCode);
        }
    }
    public void addListener(Listener listener){listeners.add(listener);byte[] snapshot;synchronized(scrollback){snapshot=scrollback.toByteArray();}if(snapshot.length>0)listener.onOutput(snapshot,snapshot.length);}
    public void removeListener(Listener listener){listeners.remove(listener);}
    public boolean write(String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);return running&&!disposed.get()&&NativePty.write(handle,bytes,bytes.length)==bytes.length;}
    public void resize(int rows,int columns,int width,int height){if(running&&!disposed.get())NativePty.resize(handle,rows,columns,width,height);}
    public void interrupt(){if(running&&!disposed.get())NativePty.signal(handle,2);}
    public void close(){
        if(!disposed.compareAndSet(false,true)){TerminalDiagnosticBundle.log("session-state.log","double close ignored session="+id);return;}
        TerminalDiagnosticBundle.state(running?"RUNNING":"EXITED","CLOSING","session="+id+" caller="+Thread.currentThread().getName());
        running=false;NativePty.signal(handle,15);NativePty.close(handle);
        if(Thread.currentThread()!=reader)try{reader.join(3000);}catch(InterruptedException error){Thread.currentThread().interrupt();}
        if(!reader.isAlive()){NativePty.destroy(handle);TerminalDiagnosticBundle.state("CLOSING","CLOSED","session="+id+" handle destroyed");}
        else TerminalStartupLog.stage("16F","reader did not stop; native handle retained to prevent use-after-free");
    }
    public int pid(){return disposed.get()?-1:NativePty.pid(handle);}
    public LocalProcessDiagnostics.Snapshot diagnostics(String oceanPrefix){return LocalProcessDiagnostics.inspect(pid(),oceanPrefix);}
    public boolean isRunning(){return running&&!disposed.get();} public int getExitCode(){return exitCode;}
    private void appendScrollback(byte[] bytes,int length){synchronized(scrollback){if(scrollback.size()+length>200000){byte[] old=scrollback.toByteArray();scrollback.reset();int keep=Math.min(old.length,150000);scrollback.write(old,old.length-keep,keep);}scrollback.write(bytes,0,length);}}
}
