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
 public static final String[] LOGS={"attempt.properties","startup.log","native-pty.log","runtime-validation.log","previous-process-exit.log","previous-process-trace.txt","java-crash.log","session-state.log","fd-ownership.log","shell-validation.log","test-a-system-shell.log","test-b-ocean-bash.log","test-c-ocean-pty-ok.log"};
 private static File root;
 private static Context appContext;
 private TerminalDiagnosticBundle(){}
 public static void init(Context c){synchronized(LOCK){appContext=c.getApplicationContext();root=new File(c.getFilesDir(),"logs/terminal-diagnostics");root.mkdirs();}}
 public static void initialize(Context c){init(c);}
 public static void beginAttempt(Context c){init(c);synchronized(LOCK){File metadata=new File(root,"attempt.properties");String abnormal=null;if(metadata.isFile()){Properties previous=load(metadata);File attempts=new File(root,"attempts");attempts.mkdirs();String id=previous.getProperty("id",new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date()));File archive=new File(attempts,id);archive.mkdirs();for(String n:LOGS){File f=new File(root,n);if(f.isFile())f.renameTo(new File(archive,n));}File combined=new File(root,"last-terminal-diagnostic.txt");if(combined.isFile())combined.renameTo(new File(archive,combined.getName()));if("true".equals(previous.getProperty("active")))abnormal=id;File[] old=attempts.listFiles();if(old!=null&&old.length>8){Arrays.sort(old,Comparator.comparing(File::getName));for(int i=0;i<old.length-8;i++)deleteTree(old[i]);}}Properties next=new Properties();next.setProperty("id",UUID.randomUUID().toString());next.setProperty("active","true");next.setProperty("build",BuildConfig.OCEAN_BUILD_COMMIT);next.setProperty("lastStage","ACTIVITY_CREATED");next.setProperty("test","PRODUCTION TERMINAL");if(abnormal!=null)next.setProperty("previousAbnormalAttempt",abnormal);storeAtomic(metadata,next);}log(c,"startup.log","[ATTEMPT] begin");}
 public static void setTest(Context c,String test){updateMetadata(c,"test",test);}
 public static void updateStage(Context c,String stage){updateMetadata(c,"lastStage",stage);}
 public static void markStable(Context c,String detail){updateMetadata(c,"lastStage","STABLE_RUNNING: "+detail);updateMetadata(c,"active","false");}
 public static void markCritical(Context c,String detail){updateMetadata(c,"active","true");updateMetadata(c,"lastStage",detail);}
 public static void completeAttempt(Context c,String detail){updateMetadata(c,"lastStage","NORMAL_EXIT: "+detail);updateMetadata(c,"active","false");}
 public static void log(String name,String value){if(appContext!=null)log(appContext,name,value);}
 public static void state(String oldState,String next,String detail){log("session-state.log",oldState+" -> "+next+" "+detail);}
 public static void javaCrash(Thread thread,Throwable error){if(appContext==null)return;StringWriter s=new StringWriter();error.printStackTrace(new PrintWriter(s));log(appContext,"java-crash.log","thread="+thread.getName()+"\n"+s);}
 public static void log(Context c,String name,String value){init(c);synchronized(LOCK){String line=System.currentTimeMillis()+" pid="+Process.myPid()+" tid="+Process.myTid()+" "+value+"\n";try(FileOutputStream out=new FileOutputStream(new File(root,name),true)){out.write(line.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}catch(Exception ignored){}}}
 public static File nativeLog(Context c){init(c);return new File(root,"native-pty.log");}
 public static String combined(Context c){init(c);File selected=selectedAttempt();StringBuilder out=new StringBuilder(summary(c));for(String name:LOGS){out.append("\n===== ").append(name).append(" =====\n");try{File f=new File(selected,name);out.append(f.isFile()?new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):"(unavailable)\n");}catch(Exception e){out.append("(unreadable: ").append(e).append(")\n");}}String text=out.toString();write(new File(selected,"last-terminal-diagnostic.txt"),text);return text;}
 public static String summary(Context c){init(c);File selected=selectedAttempt();String session=read(selected,"session-state.log"),nativeLog=read(selected,"native-pty.log"),exit=read(selected,"previous-process-exit.log"),javaCrash=read(selected,"java-crash.log");Properties attempt=load(new File(selected,"attempt.properties"));return "===== OCEAN TERMINAL DIAGNOSTIC SUMMARY =====\nBuild: "+attempt.getProperty("build",BuildConfig.OCEAN_BUILD_COMMIT)+"\nBootstrap: "+BuildConfig.OCEAN_BOOTSTRAP_VERSION+" "+BuildConfig.OCEAN_BOOTSTRAP_SHA256+"\nDevice API: "+android.os.Build.VERSION.SDK_INT+"\nABI: "+OceanEnvironment.architecture()+"\nAttempt: "+attempt.getProperty("id","unavailable")+" active="+attempt.getProperty("active","unknown")+"\nLast test: "+attempt.getProperty("test","unavailable")+"\nAttempt stage: "+attempt.getProperty("lastStage","unavailable")+"\nLast session state: "+lastLine(session)+"\nPrevious process exit: "+firstContaining(exit,"Reason:")+" / "+firstContaining(exit,"Status:")+"\nLast native stage: "+lastLine(nativeLog)+"\nShell exec reached: "+yes(nativeLog.contains("execve"))+"\nOCEAN_PTY_OK observed: "+yes(read(selected,"test-c-ocean-pty-ok.log").contains("observed"))+"\nChild exited: "+yes(session.contains("child exit"))+"\nReader last result: "+lastContaining(session,"reader=")+"\nDouble-close detected: "+yes(read(selected,"fd-ownership.log").contains("DOUBLE"))+"\nDouble-finalize detected: "+yes(session.contains("DOUBLE_FINALIZE"))+"\nJava uncaught exception: "+yes(!javaCrash.isEmpty())+"\nAndroid tombstone: "+(new File(selected,"previous-process-trace.txt").isFile()?"available":"not available")+"\n";}
 public static void writeText(Context c,OutputStream output)throws IOException{output.write(combined(c).getBytes(StandardCharsets.UTF_8));output.flush();}
 public static void writeZip(Context c,OutputStream output)throws IOException{init(c);File selected=selectedAttempt();combined(c);try(ZipOutputStream zip=new ZipOutputStream(output)){for(String name:LOGS)add(zip,new File(selected,name),name);add(zip,new File(selected,"last-terminal-diagnostic.txt"),"last-terminal-diagnostic.txt");}}
 public static File shareZip(Context c)throws IOException{File exports=new File(c.getCacheDir(),"diagnostics");exports.mkdirs();File file=new File(exports,fileName("zip"));try(FileOutputStream out=new FileOutputStream(file)){writeZip(c,out);}return file;}
 public static String fileName(String extension){return "Ocean-Terminal-Diagnostic-"+new SimpleDateFormat("yyyy-MM-dd-HHmm",Locale.US).format(new Date())+"."+extension;}
 private static void addTree(ZipOutputStream z,File f,String prefix)throws IOException{File[] children=f.listFiles();if(children==null)return;for(File child:children){String n=prefix+child.getName();if(child.isDirectory())addTree(z,child,n+"/");else add(z,child,n);}}
 private static void add(ZipOutputStream z,File f,String name)throws IOException{if(!f.isFile())return;z.putNextEntry(new ZipEntry(name));try(FileInputStream in=new FileInputStream(f)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)z.write(b,0,n);}z.closeEntry();}
 private static void write(File f,String s){try(FileOutputStream out=new FileOutputStream(f,false)){out.write(s.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}catch(Exception ignored){}}
 private static String read(String n){return read(root,n);}
 private static String read(File directory,String n){try{File f=new File(directory,n);return f.isFile()?new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8):"";}catch(Exception e){return "";}}
 private static String lastLine(String s){String[] l=s.trim().split("\\R");return l.length==0?"unavailable":l[l.length-1];}
 private static String firstContaining(String s,String q){for(String l:s.split("\\R"))if(l.contains(q))return l.trim();return "unavailable";}
 private static String lastContaining(String s,String q){String found="unavailable";for(String l:s.split("\\R"))if(l.contains(q))found=l.trim();return found;}
 private static String yes(boolean v){return v?"yes":"no";}
 private static void deleteTree(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)deleteTree(c);f.delete();}

 public static boolean hasStartupCrash(Context c){init(c);Properties p=load(new File(root,"attempt.properties"));if("true".equals(p.getProperty("active"))||p.getProperty("previousAbnormalAttempt")!=null)return true;File selected=selectedAttempt();String e=read(selected,"previous-process-exit.log");return e.contains("REASON_CRASH")||e.contains("REASON_SIGNALED")||e.contains("REASON_ANR");}
 private static void updateMetadata(Context c,String key,String value){init(c);synchronized(LOCK){File file=new File(root,"attempt.properties");Properties p=load(file);p.setProperty(key,value);storeAtomic(file,p);}}
 private static Properties diagnosticAttempt(){Properties current=load(new File(root,"attempt.properties"));if("true".equals(current.getProperty("active")))return current;File attempts=new File(root,"attempts");File[] dirs=attempts.listFiles(File::isDirectory);if(dirs!=null){Arrays.sort(dirs,Comparator.comparing(File::getName).reversed());for(File dir:dirs){Properties p=load(new File(dir,"attempt.properties"));if("true".equals(p.getProperty("active")))return p;}}return current;}
 private static File selectedAttempt(){Properties current=load(new File(root,"attempt.properties"));String previous=current.getProperty("previousAbnormalAttempt");if(previous!=null){File dir=new File(new File(root,"attempts"),previous);if(dir.isDirectory())return dir;}return root;}
 private static Properties load(File file){Properties p=new Properties();if(!file.isFile())return p;try(FileInputStream in=new FileInputStream(file)){p.load(in);}catch(IOException ignored){}return p;}
 private static void storeAtomic(File file,Properties p){File temp=new File(file.getParentFile(),file.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(temp,false)){p.store(out,"Ocean Terminal diagnostic attempt");out.getFD().sync();if(!temp.renameTo(file)){try(FileInputStream in=new FileInputStream(temp);FileOutputStream target=new FileOutputStream(file,false)){byte[] b=new byte[4096];for(int n;(n=in.read(b))>=0;)target.write(b,0,n);target.getFD().sync();}temp.delete();}}catch(IOException ignored){temp.delete();}}
}
