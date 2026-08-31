package studio.ocean.app.terminal;

import android.content.Context;
import android.os.Process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;
import studio.ocean.app.BuildConfig;

/** Durable, credential-free PTY/session evidence and export support. */
public final class TerminalDiagnosticBundle {
 private static final Object LOCK=new Object();
 public static final String[] LOGS={"startup.log","native-pty.log","runtime-validation.log","previous-process-exit.log","previous-process-trace.txt","java-crash.log","native-crash-marker.log","session-state.log","fd-ownership.log","shell-validation.log","test-a-system-shell.log","test-b-ocean-bash.log","test-c-ocean-pty-ok.log"};
 private static File root;
 private static Context appContext;
 private TerminalDiagnosticBundle(){}
 public static void init(Context c){synchronized(LOCK){appContext=c.getApplicationContext();root=new File(c.getFilesDir(),"logs/terminal-diagnostics");root.mkdirs();}}
 public static void initialize(Context c){init(c);}
 public static void beginAttempt(Context c){init(c);synchronized(LOCK){File current=new File(root,"startup.log");if(current.length()>0){File attempts=new File(root,"attempts");attempts.mkdirs();File archive=new File(attempts,new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date()));archive.mkdirs();for(String n:LOGS){File f=new File(root,n);if(f.isFile()&&!n.startsWith("previous-process"))f.renameTo(new File(archive,n));}File[] old=attempts.listFiles();if(old!=null&&old.length>8){Arrays.sort(old,Comparator.comparing(File::getName));for(int i=0;i<old.length-8;i++)deleteTree(old[i]);}}}log(c,"startup.log","[ATTEMPT] begin");}
 public static void log(String name,String value){if(appContext!=null)log(appContext,name,value);}
 public static void state(String oldState,String next,String detail){log("session-state.log",oldState+" -> "+next+" "+detail);}
 public static void javaCrash(Thread thread,Throwable error){if(appContext==null)return;StringWriter s=new StringWriter();error.printStackTrace(new PrintWriter(s));log(appContext,"java-crash.log","thread="+thread.getName()+"\n"+s);}
 public static void log(Context c,String name,String value){init(c);synchronized(LOCK){String line=System.currentTimeMillis()+" pid="+Process.myPid()+" tid="+Process.myTid()+" "+value+"\n";try(FileOutputStream out=new FileOutputStream(new File(root,name),true)){out.write(line.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}catch(Exception ignored){}}}
 public static File nativeLog(Context c){init(c);return new File(root,"native-pty.log");}
 public static String combined(Context c){init(c);StringBuilder out=new StringBuilder(summary(c));for(String name:LOGS){out.append("\n===== ").append(name).append(" =====\n");try{File f=new File(root,name);out.append(f.isFile()?new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):"(unavailable)\n");}catch(Exception e){out.append("(unreadable: ").append(e).append(")\n");}}String text=out.toString();write(new File(root,"last-terminal-diagnostic.txt"),text);return text;}
 public static String summary(Context c){init(c);String session=read("session-state.log"),nativeLog=read("native-pty.log"),exit=read("previous-process-exit.log"),javaCrash=read("java-crash.log"),fatal=read("native-crash-marker.log");return "===== OCEAN TERMINAL DIAGNOSTIC SUMMARY =====\nBuild: "+BuildConfig.OCEAN_BUILD_COMMIT+"\nBootstrap: "+BuildConfig.OCEAN_BOOTSTRAP_VERSION+" "+BuildConfig.OCEAN_BOOTSTRAP_SHA256+"\nDevice API: "+android.os.Build.VERSION.SDK_INT+"\nABI: "+OceanEnvironment.architecture()+"\nLast test: "+lastContaining(session,"activeDiagnosticTest=")+"\nLast session state: "+lastLine(session)+"\nPrevious process exit: "+firstContaining(exit,"Reason:")+" / "+firstContaining(exit,"Status:")+"\nLast native stage: "+lastLine(nativeLog)+"\nShell exec reached: "+yes(nativeLog.contains("execve"))+"\nOCEAN_PTY_OK observed: "+yes(read("test-c-ocean-pty-ok.log").contains("observed"))+"\nChild exited: "+yes(session.contains("child exit"))+"\nReader last result: "+lastContaining(session,"reader=")+"\nDouble-close detected: "+yes(read("fd-ownership.log").contains("DOUBLE"))+"\nDouble-finalize detected: "+yes(session.contains("DOUBLE_FINALIZE"))+"\nNative fatal marker: "+(fatal.isEmpty()?"none":lastLine(fatal))+"\nJava uncaught exception: "+yes(!javaCrash.isEmpty())+"\nAndroid tombstone: "+(new File(root,"previous-process-trace.txt").isFile()?"available":"not available")+"\n";}
 public static void writeText(Context c,OutputStream output)throws IOException{output.write(combined(c).getBytes(StandardCharsets.UTF_8));output.flush();}
 public static void writeZip(Context c,OutputStream output)throws IOException{init(c);combined(c);try(ZipOutputStream zip=new ZipOutputStream(output)){for(String name:LOGS){add(zip,new File(root,name),name);}add(zip,new File(root,"last-terminal-diagnostic.txt"),"last-terminal-diagnostic.txt");File attempts=new File(root,"attempts");addTree(zip,attempts,"attempts/");}}
 public static File shareZip(Context c)throws IOException{File exports=new File(c.getCacheDir(),"diagnostics");exports.mkdirs();File file=new File(exports,fileName("zip"));try(FileOutputStream out=new FileOutputStream(file)){writeZip(c,out);}return file;}
 public static String fileName(String extension){return "Ocean-Terminal-Diagnostic-"+new SimpleDateFormat("yyyy-MM-dd-HHmm",Locale.US).format(new Date())+"."+extension;}
 private static void addTree(ZipOutputStream z,File f,String prefix)throws IOException{File[] children=f.listFiles();if(children==null)return;for(File child:children){String n=prefix+child.getName();if(child.isDirectory())addTree(z,child,n+"/");else add(z,child,n);}}
 private static void add(ZipOutputStream z,File f,String name)throws IOException{if(!f.isFile())return;z.putNextEntry(new ZipEntry(name));try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)z.write(b,0,n);}z.closeEntry();}
 private static void write(File f,String s){try(FileOutputStream out=new FileOutputStream(f,false)){out.write(s.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}catch(Exception ignored){}}
 private static String read(String n){try{File f=new File(root,n);return f.isFile()?new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):"";}catch(Exception e){return "";}}
 private static String lastLine(String s){String[] l=s.trim().split("\\R");return l.length==0?"unavailable":l[l.length-1];}
 private static String firstContaining(String s,String q){for(String l:s.split("\\R"))if(l.contains(q))return l.trim();return "unavailable";}
 private static String lastContaining(String s,String q){String found="unavailable";for(String l:s.split("\\R"))if(l.contains(q))found=l.trim();return found;}
 private static String yes(boolean v){return v?"yes":"no";}
 private static void deleteTree(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)deleteTree(c);f.delete();}

 public static boolean hasStartupCrash(Context c){init(c);String s=read("session-state.log"),e=read("previous-process-exit.log");return (s.contains("PTY_CREATING")||s.contains("FORKED")||s.contains("EXECUTING")||s.contains("RUNNING"))&&(e.contains("REASON_CRASH")||e.contains("REASON_SIGNALED")||e.contains("REASON_ANR"));}
}
