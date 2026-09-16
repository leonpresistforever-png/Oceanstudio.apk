package studio.ocean.app.terminal;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Coordinates one PTY reader, one blocking child reaper, and one native finalizer. */
public final class TerminalSession {
    public enum State { NEW, STARTING, RUNNING, EXITING, EXITED, CLOSING, CLOSED }
    public enum WriteResult { WRITTEN, SESSION_ALREADY_EXITED, NATIVE_WRITE_FAILED }
    public interface Listener { void onOutput(byte[] bytes,int length); void onExit(int exitCode); }
    public interface CompletionListener { void onCompleted(TerminalSession session); }

    public final String id=UUID.randomUUID().toString();
    public final long startedAt=System.currentTimeMillis();
    private final Context context;
    private final long handle;
    private final CompletionListener completionListener;
    private final Thread reader;
    private final Thread waiter;
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    private final ByteArrayOutputStream scrollback=new ByteArrayOutputStream();
    private final ReentrantReadWriteLock nativeLifetime=new ReentrantReadWriteLock();
    private final AtomicBoolean readerDone=new AtomicBoolean();
    private final AtomicBoolean waiterDone=new AtomicBoolean();
    private final AtomicBoolean finalizeClaimed=new AtomicBoolean();
    private final AtomicBoolean masterCloseRequested=new AtomicBoolean();
    private final AtomicBoolean exitDelivered=new AtomicBoolean();
    private volatile boolean nativeDestroyed;
    private volatile State state=State.NEW;
    private volatile int exitCode=-1;
    private volatile String closeReason;

    TerminalSession(Context context,long handle,CompletionListener completionListener) {
        if(handle==0)throw new IllegalArgumentException("Native PTY handle is zero");
        this.context=context.getApplicationContext();this.handle=handle;this.completionListener=completionListener;
        transition(State.STARTING,"session constructed");
        reader=new Thread(this::readLoop,"ocean-pty-reader-"+id);
        waiter=new Thread(this::waitLoop,"ocean-child-waiter-"+id);
    }
    void startWorkers(){synchronized(this){if(state!=State.STARTING)return;transition(State.RUNNING,"reader and blocking waiter starting");reader.start();waiter.start();}}

    private void readLoop(){byte[] buffer=new byte[8192];boolean first=true;try{while(state==State.RUNNING||state==State.EXITING){int count;nativeLifetime.readLock().lock();try{if(state!=State.RUNNING&&state!=State.EXITING)break;count=NativePty.read(handle,buffer);}finally{nativeLifetime.readLock().unlock();}if(count>0){if(first){first=false;TerminalDiagnosticBundle.markStable(context,"first PTY bytes received");}TerminalDiagnosticBundle.log(context,"session-state.log","reader=bytes count="+count);append(buffer,count);for(Listener listener:listeners)listener.onOutput(buffer,count);continue;}if(count==0){TerminalDiagnosticBundle.log(context,"session-state.log","[F02/F03] reader=PTY_EOF_EIO_CHILD_EXIT");beginExit("PTY EOF/EIO");break;}if(count==-11){TerminalDiagnosticBundle.log(context,"session-state.log","reader=EAGAIN retry");continue;}TerminalDiagnosticBundle.log(context,"session-state.log","reader=errno "+(-count));beginExit("PTY read errno="+(-count));break;}}catch(Throwable error){TerminalStartupLog.failure("PTY reader failed",error);beginExit("reader exception="+error);}finally{readerDone.set(true);TerminalDiagnosticBundle.log(context,"session-state.log","[F07] reader thread stopped");maybeFinalize("reader");}}

    private void waitLoop(){int code;nativeLifetime.readLock().lock();try{code=NativePty.waitExit(handle);}finally{nativeLifetime.readLock().unlock();}exitCode=code<0?255:code;TerminalDiagnosticBundle.log(context,"session-state.log","[F01/F08] child exit observed status="+exitCode);beginExit("child wait complete");waiterDone.set(true);maybeFinalize("waiter");}

    private void beginExit(String reason){synchronized(this){if(state==State.RUNNING||state==State.STARTING){state=State.EXITING;TerminalDiagnosticBundle.markCritical(context,"EXITING "+reason);TerminalDiagnosticBundle.log(context,"session-state.log","RUNNING -> EXITING reason="+reason+" session="+id);}}}

    private void maybeFinalize(String caller){if(!readerDone.get()||!waiterDone.get())return;if(!finalizeClaimed.compareAndSet(false,true)){return;}synchronized(this){state=State.CLOSING;}TerminalDiagnosticBundle.log(context,"session-state.log","[F04/F10] finalizer claimed caller="+caller);nativeLifetime.writeLock().lock();try{if(!masterCloseRequested.get())closeMasterOnce("finalizer");else TerminalDiagnosticBundle.log(context,"fd-ownership.log","finalizer observed master already closed; no second native close");nativeDestroyed=true;NativePty.destroy(handle);TerminalDiagnosticBundle.log(context,"session-state.log","[F11] native session destroyed");}finally{nativeLifetime.writeLock().unlock();}synchronized(this){state=State.EXITED;}String exitKind=exitCode==143&&closeReason!=null?"TEST_TERMINATED_BY_USER_OR_HARNESS reason="+closeReason:"natural";TerminalDiagnosticBundle.log(context,"session-state.log","[F12] state EXITED code="+exitCode+" kind="+exitKind);TerminalDiagnosticBundle.completeAttempt(context,"exit code="+exitCode+" kind="+exitKind);if(exitDelivered.compareAndSet(false,true))for(Listener listener:listeners)listener.onExit(exitCode);completionListener.onCompleted(this);}

    private void closeMasterOnce(String caller){if(!masterCloseRequested.compareAndSet(false,true)){TerminalDiagnosticBundle.log(context,"fd-ownership.log","DOUBLE_CLOSE_ATTEMPT caller="+caller);return;}int fd=masterFd();TerminalDiagnosticBundle.log(context,"fd-ownership.log","[F05] master close requested caller="+caller+" fd="+fd);NativePty.close(handle);TerminalDiagnosticBundle.log(context,"fd-ownership.log","[F06] master close completed fd="+fd);}

    public WriteResult write(String value){nativeLifetime.readLock().lock();try{if(state!=State.RUNNING){TerminalDiagnosticBundle.log(context,"session-state.log","write rejected state="+state);return WriteResult.SESSION_ALREADY_EXITED;}byte[] bytes=value.getBytes(StandardCharsets.UTF_8);return NativePty.write(handle,bytes,bytes.length)==bytes.length?WriteResult.WRITTEN:WriteResult.NATIVE_WRITE_FAILED;}finally{nativeLifetime.readLock().unlock();}}
    public WriteResult write(byte[] value){nativeLifetime.readLock().lock();try{if(state!=State.RUNNING){TerminalDiagnosticBundle.log(context,"session-state.log","write rejected state="+state);return WriteResult.SESSION_ALREADY_EXITED;}return NativePty.write(handle,value,value.length)==value.length?WriteResult.WRITTEN:WriteResult.NATIVE_WRITE_FAILED;}finally{nativeLifetime.readLock().unlock();}}
    public void close(){synchronized(this){if(state==State.EXITED||state==State.CLOSED||state==State.CLOSING)return;state=State.CLOSING;closeReason="service/activity teardown by "+Thread.currentThread().getName();}TerminalDiagnosticBundle.markCritical(context,"explicit close");TerminalDiagnosticBundle.log(context,"session-state.log","CLOSING requested caller="+Thread.currentThread().getName());NativePty.signal(handle,15);closeMasterOnce("explicit-close");}
    public void addListener(Listener listener){listeners.add(listener);byte[] snapshot;synchronized(scrollback){snapshot=scrollback.toByteArray();}if(snapshot.length>0)listener.onOutput(snapshot,snapshot.length);if(state==State.EXITED)listener.onExit(exitCode);}
    public void removeListener(Listener listener){listeners.remove(listener);TerminalDiagnosticBundle.log(context,"session-state.log","[F09] Java listener detached session="+id);}
    /** Hard deadline/cancellation for headless commands; never applied to interactive sessions. */
    public void terminateCommand(){
        nativeLifetime.readLock().lock();
        try { if(!nativeDestroyed && state!=State.EXITED && state!=State.CLOSED) NativePty.signal(handle,9); }
        finally { nativeLifetime.readLock().unlock(); }
    }
    public void interrupt(){nativeLifetime.readLock().lock();try{if(state==State.RUNNING&&!nativeDestroyed)NativePty.signal(handle,2);}finally{nativeLifetime.readLock().unlock();}}
    public void resize(int rows,int columns,int width,int height){nativeLifetime.readLock().lock();try{if(state==State.RUNNING&&!nativeDestroyed)NativePty.resize(handle,rows,columns,width,height);}finally{nativeLifetime.readLock().unlock();}}
    public int pid(){nativeLifetime.readLock().lock();try{return nativeDestroyed?-1:NativePty.pid(handle);}finally{nativeLifetime.readLock().unlock();}}
    public int masterFd(){nativeLifetime.readLock().lock();try{return nativeDestroyed?-1:NativePty.masterFd(handle);}finally{nativeLifetime.readLock().unlock();}}
    public boolean isRunning(){return state==State.RUNNING||state==State.STARTING;}
    public State state(){return state;}
    public int getExitCode(){return exitCode;}
    public LocalProcessDiagnostics.Snapshot diagnostics(String prefix){return LocalProcessDiagnostics.inspect(pid(),prefix);}
    private void transition(State next,String detail){State old=state;state=next;TerminalDiagnosticBundle.log(context,"session-state.log",old+" -> "+next+" session="+id+" pid="+pid()+" masterFd="+masterFd()+" "+detail);}
    private void append(byte[] bytes,int length){synchronized(scrollback){if(scrollback.size()+length>200000){byte[] old=scrollback.toByteArray();scrollback.reset();int keep=Math.min(old.length,150000);scrollback.write(old,old.length-keep,keep);}scrollback.write(bytes,0,length);}}
}
