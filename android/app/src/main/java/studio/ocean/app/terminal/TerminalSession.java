package studio.ocean.app.terminal;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** A real PTY-backed process. Output and exit state always originate from the child. */
public final class TerminalSession implements Closeable {
    public interface Listener { void onOutput(byte[] data, int length); void onExit(int exitCode); }
    private final String id = UUID.randomUUID().toString();
    private final int readFd, writeFd, pid;
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean readerStarted = new AtomicBoolean();
    private volatile boolean running = true;

    public TerminalSession(String[] command, String[] environment, String cwd, int rows, int columns) throws IOException {
        int[] descriptors = NativePty.create(command, environment, cwd, rows, columns);
        if (descriptors == null || descriptors.length != 3) throw new IOException("PTY creation failed");
        readFd=descriptors[0]; writeFd=descriptors[1]; pid=descriptors[2];
    }
    public String id() { return id; }
    public int pid() { return pid; }
    public boolean isRunning() { return running; }
    public void attach(Listener listener) { listeners.addIfAbsent(listener); if(readerStarted.compareAndSet(false,true)) reader.execute(() -> { byte[] buffer=new byte[8192]; try { int count; while(running && (count=NativePty.read(readFd,buffer))>0) for(Listener item:listeners)item.onOutput(buffer,count); } finally { running=false; int exit=NativePty.waitFor(pid); for(Listener item:listeners)item.onExit(exit); } }); }
    public void detach(Listener listener) { listeners.remove(listener); }
    public void write(String value) throws IOException { write(value.getBytes(StandardCharsets.UTF_8)); }
    public void write(byte[] value) throws IOException { if(!running || NativePty.write(writeFd,value)<0) throw new IOException("PTY write failed"); }
    public void resize(int rows,int columns) { if(running) NativePty.resize(readFd,rows,columns); }
    public void interrupt() { if(running) NativePty.signal(pid,2); }
    @Override public void close() { if(!running)return; running=false; NativePty.signal(pid,15); NativePty.close(readFd); NativePty.close(writeFd); reader.shutdownNow(); }
}
