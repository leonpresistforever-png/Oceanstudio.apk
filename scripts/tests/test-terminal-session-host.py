#!/usr/bin/env python3
"""Test production TerminalSession lifecycle on the host with real child process pipes.

Android logging/context and NativePty are test adapters. This checks Java output
ordering, exit reporting and cancellation; Android JNI/device acceptance is separate.
No network, Android emulator, credentials or model API calls are needed.
"""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='ocean-session-test-') as temporary:
    work = Path(temporary)
    files = {
        'android/content/Context.java': 'package android.content; public class Context { public Context getApplicationContext(){return this;} }',
        'studio/ocean/app/terminal/TerminalDiagnosticBundle.java': '''package studio.ocean.app.terminal;
public class TerminalDiagnosticBundle {
 public static void markStable(android.content.Context c,String s){}
 public static void markCritical(android.content.Context c,String s){}
 public static void log(android.content.Context c,String a,String b){}
 public static void completeAttempt(android.content.Context c,String s){}
}''',
        'studio/ocean/app/terminal/TerminalStartupLog.java': '''package studio.ocean.app.terminal;
public class TerminalStartupLog { public static void failure(String s,Throwable e){throw new AssertionError(s,e);} }''',
        'studio/ocean/app/terminal/LocalProcessDiagnostics.java': '''package studio.ocean.app.terminal;
public class LocalProcessDiagnostics { public static class Snapshot {} public static Snapshot inspect(int pid,String prefix){return new Snapshot();} }''',
        'studio/ocean/app/terminal/NativePty.java': '''package studio.ocean.app.terminal;
import java.io.*;
public class NativePty {
 static Process child;
 static void launch(String command)throws Exception { child=new ProcessBuilder("/bin/sh","-c",command).redirectErrorStream(true).start(); }
 static int read(long h,byte[] buffer){
  // Make the child exit before the first read to reproduce the Java drain race reliably.
  try { Thread.sleep(25); int n=child.getInputStream().read(buffer); return n<0?0:n; } catch(Exception e){return -5;}
 }
 static int waitExit(long h){try{return child.waitFor();}catch(Exception e){throw new RuntimeException(e);}}
 static void signal(long h,int s){child.descendants().forEach(ProcessHandle::destroyForcibly);child.destroyForcibly();}
 static void close(long h){try{child.getInputStream().close();}catch(IOException e){throw new RuntimeException(e);}}
 static void destroy(long h){}
 static int pid(long h){return (int)child.pid();}
 static int masterFd(long h){return 12;}
 static int write(long h,byte[] b,int n){try{child.getOutputStream().write(b,0,n);return n;}catch(IOException e){return -1;}}
 static int resize(long h,int r,int c,int w,int t){return 0;}
}''',
        'studio/ocean/app/terminal/HostSessionCheck.java': '''package studio.ocean.app.terminal;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public class HostSessionCheck {
 public static void main(String[] args)throws Exception {
  for(int i=0;i<20;i++){
   NativePty.launch("printf 'FIRST\\n'; printf 'last-UTF8-✓\\n'; exit 7");
   CountDownLatch end=new CountDownLatch(1);ByteArrayOutputStream output=new ByteArrayOutputStream();AtomicInteger exits=new AtomicInteger();
   TerminalSession session=new TerminalSession(new android.content.Context(),1,s->{});
   session.addListener(new TerminalSession.Listener(){
    public void onOutput(byte[] b,int n){output.write(b,0,n);}
    public void onExit(int code){if(code!=7)throw new AssertionError("Exit="+code);exits.incrementAndGet();end.countDown();}
   });
   session.startWorkers();
   if(!end.await(5,TimeUnit.SECONDS))throw new AssertionError("Missing exit");
   String text=output.toString(StandardCharsets.UTF_8);
   if(!text.equals("FIRST\\nlast-UTF8-✓\\n"))throw new AssertionError("Lost output: "+text);
   if(exits.get()!=1)throw new AssertionError("Duplicate exit");
   if(session.write("late")!=TerminalSession.WriteResult.SESSION_ALREADY_EXITED)throw new AssertionError("Late write accepted");
  }
  NativePty.launch("printf 'STARTED\\n'; sleep 30");
  CountDownLatch start=new CountDownLatch(1),end=new CountDownLatch(1);
  TerminalSession session=new TerminalSession(new android.content.Context(),1,s->{});
  session.addListener(new TerminalSession.Listener(){public void onOutput(byte[] b,int n){start.countDown();}public void onExit(int c){if(c==0)throw new AssertionError("Cancelled command reported success");end.countDown();}});
  session.startWorkers();if(!start.await(5,TimeUnit.SECONDS))throw new AssertionError("Command did not start");
  session.terminateCommand();if(!end.await(5,TimeUnit.SECONDS))throw new AssertionError("Command did not stop");
  System.out.println("PASS: 20 fast exits preserve exact UTF-8 output and exit 7; cancellation reaps the child.");
 }
}'''
    }
    for name, content in files.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
    production = root / 'android/app/src/main/java/studio/ocean/app/terminal/TerminalSession.java'
    java = os.path.join(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-17-openjdk-amd64'), 'bin/java')
    subprocess.run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-d', str(work / 'classes'), str(production)] + [str(work / f) for f in files], check=True)
    subprocess.run([java, '-cp', str(work / 'classes'), 'studio.ocean.app.terminal.HostSessionCheck'], check=True, timeout=30)
