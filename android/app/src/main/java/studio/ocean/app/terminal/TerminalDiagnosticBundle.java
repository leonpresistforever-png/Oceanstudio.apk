package studio.ocean.app.terminal;

import android.content.Context;
import android.os.Process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

/** Crash-resistant, credential-free black-box record of terminal startup. */
public final class TerminalDiagnosticBundle {
    private static final Object LOCK=new Object();
    private static final String[] FILES={"startup.log","native-pty.log","runtime-validation.log","previous-process-exit.log","previous-process-trace.txt","java-crash.log","native-crash-marker.log","session-state.log"};
    private static File root;
    private TerminalDiagnosticBundle(){}
    public static void initialize(Context c){synchronized(LOCK){root=new File(c.getFilesDir(),"logs/terminal-diagnostics");root.mkdirs();}}
    public static void beginAttempt(Context c){initialize(c);synchronized(LOCK){File startup=file("startup.log");if(startup.length()>0){File attempts=new File(root,"attempts");attempts.mkdirs();File archived=new File(attempts,new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date()));archived.mkdirs();for(String n:FILES){File source=file(n);if(source.isFile()&&!n.startsWith("previous-process"))source.renameTo(new File(archived,n));}File[] old=attempts.listFiles();if(old!=null&&old.length>8){Arrays.sort(old,Comparator.comparing(File::getName));for(int i=0;i<old.length-8;i++)delete(old[i]);}}}append("startup.log","[ATTEMPT] begin");}
    public static void log(String name,String message){append(name,message);}
    public static void state(String oldState,String next,String details){append("session-state.log",oldState+" -> "+next+" "+details);}
    public static File nativeLog(Context c){initialize(c);return file("native-pty.log");}
    public static void javaCrash(Thread thread,Throwable error){StringWriter text=new StringWriter();PrintWriter out=new PrintWriter(text);out.println("thread="+thread.getName()+" pid="+Process.myPid()+" tid="+Process.myTid());printThrowable(error,out,"");out.flush();append("java-crash.log",text.toString());}
    private static void printThrowable(Throwable t,PrintWriter out,String indent){if(t==null)return;out.println(indent+t);for(StackTraceElement e:t.getStackTrace())out.println(indent+"  at "+e);for(Throwable s:t.getSuppressed()){out.println(indent+"Suppressed:");printThrowable(s,out,indent+"  ");}if(t.getCause()!=null){out.println(indent+"Caused by:");printThrowable(t.getCause(),out,indent+"  ");}}
    public static String combine(Context c){initialize(c);StringBuilder all=new StringBuilder("Ocean Terminal Diagnostics\nGenerated: "+new Date()+"\n\n");for(String n:FILES){all.append("===== ").append(n).append(" =====\n");try{File f=file(n);all.append(f.isFile()?new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):"(unavailable)\n");}catch(Exception e){all.append("(unreadable: ").append(e).append(")\n");}all.append('\n');}String value=all.toString();write(file("last-terminal-diagnostic.txt"),value);return value;}
    public static File combinedFile(Context c){combine(c);return file("last-terminal-diagnostic.txt");}
    public static boolean hasStartupCrash(Context c){initialize(c);try{File state=file("session-state.log"),exit=file("previous-process-exit.log");if(!state.isFile()||!exit.isFile())return false;String s=new String(Files.readAllBytes(state.toPath()),StandardCharsets.UTF_8),e=new String(Files.readAllBytes(exit.toPath()),StandardCharsets.UTF_8);boolean starting=s.contains("VALIDATING")||s.contains("STARTING")||s.contains("PTY_CREATING")||s.contains("FORKED")||s.contains("EXECUTING");return starting&&(e.contains("REASON_CRASH")||e.contains("REASON_CRASH_NATIVE")||e.contains("REASON_SIGNALED")||e.contains("REASON_ANR"));}catch(Exception ignored){return false;}}
    private static void append(String name,String message){synchronized(LOCK){if(root==null)return;String line=System.currentTimeMillis()+" pid="+Process.myPid()+" tid="+Process.myTid()+" "+message+(message.endsWith("\n")?"":"\n");try(FileOutputStream o=new FileOutputStream(file(name),true)){o.write(line.getBytes(StandardCharsets.UTF_8));o.getFD().sync();}catch(Exception ignored){}}}
    private static void write(File f,String value){try(FileOutputStream o=new FileOutputStream(f,false)){o.write(value.getBytes(StandardCharsets.UTF_8));o.getFD().sync();}catch(Exception ignored){}}
    private static File file(String n){return new File(root,n);}
    private static void delete(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)delete(c);f.delete();}
}
