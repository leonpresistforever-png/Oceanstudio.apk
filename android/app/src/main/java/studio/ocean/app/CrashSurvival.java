package studio.ocean.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/** Temporary diagnostics only. Never swallows a crash or restarts failed agent work. */
public final class CrashSurvival {
    private static Context app;
    private static File directory;
    private static Properties previous = new Properties(), current = new Properties();
    private static volatile String stage = "APP_START";
    private static final AtomicBoolean requested = new AtomicBoolean();
    private static final AtomicBoolean savingCrash = new AtomicBoolean();
    private static long started;

    private CrashSurvival() {}
    public static void install(Context context) {
        app = context.getApplicationContext(); directory = new File(app.getFilesDir(), "crash-survival"); directory.mkdirs();
        previous = load(new File(directory, "session.properties")); started = System.currentTimeMillis();
        current.setProperty("started", Long.toString(started)); current.setProperty("pid", Integer.toString(Process.myPid()));
        current.setProperty("version", BuildConfig.VERSION_NAME); current.setProperty("build", BuildConfig.OCEAN_BUILD_COMMIT); current.setProperty("busy", "false");
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error) -> {
            if (savingCrash.compareAndSet(false,true)) {
                try { CrashReportStore.saveException(new File(directory,"uncaught.txt"),
                    "OCEAN JAVA CRASH\nTime: " + new Date() + "\nVersion: " + BuildConfig.VERSION_NAME
                    + "\nBuild: " + BuildConfig.OCEAN_BUILD_COMMIT + "\nPID: " + Process.myPid()
                    + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE
                    + "\nLast agent step: " + stage, thread, error); } catch (Throwable ignored) { }
            }
            // Keep Android's normal crash handling and termination; never continue a damaged process.
            if (original != null) original.uncaughtException(thread,error);
            Process.killProcess(Process.myPid()); System.exit(10);
        });
        mark("APP_START");
    }
    public static synchronized void begin() { if(app==null)return; current.setProperty("busy","true"); mark("PROMPT_SUBMITTED"); }
    public static synchronized void finished() { if(app==null)return; current.setProperty("busy","false"); mark("AGENT_RESPONSE_DISPLAYED"); }
    public static synchronized void mark(String value) {
        stage=value; if(directory==null)return;
        current.setProperty("stage",value); current.setProperty("stageTime",Long.toString(System.currentTimeMillis()));
        File target=new File(directory,"session.properties"), temp=new File(directory,"session.tmp");
        try (FileOutputStream out=new FileOutputStream(temp)) {
            current.store(out,"Ocean diagnostic step; no prompt, command or API key");out.getFD().sync();
            if(!temp.renameTo(target))temp.delete();
        } catch(Throwable ignored) { }
    }
    public static void showPending(Activity activity) {
        if(app==null||!requested.compareAndSet(false,true))return;
        new Thread(() -> {
            String report;
            try { report=prepareReport(); } catch(Throwable ignored) { report=""; }
            final String text=report;
            activity.runOnUiThread(() -> {
                if(activity.isFinishing()||activity.isDestroyed()){requested.set(false);return;}
                if(text.isEmpty()){requested.set(false);return;}
                int pad=Math.round(18*activity.getResources().getDisplayMetrics().density);
                TextView body=new TextView(activity);body.setText(text);body.setTextSize(12);body.setTypeface(Typeface.MONOSPACE);body.setTextIsSelectable(true);body.setPadding(pad,pad,pad,pad);
                ScrollView scroll=new ScrollView(activity);scroll.addView(body);
                AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Previous app crash / interruption")
                    .setView(scroll).setPositiveButton("Dismiss",(d,w)->app.getSharedPreferences("crash-survival",0).edit().putString("seen",CrashReportStore.identity(text)).apply())
                    .setNeutralButton("Copy report",null).create();
                dialog.setCanceledOnTouchOutside(false);dialog.setCancelable(false);dialog.show();
                dialog.getWindow().setLayout(-1,Math.round(activity.getResources().getDisplayMetrics().heightPixels*.82f));
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                    ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ocean crash report",text));
                    Toast.makeText(activity,"Crash report copied",Toast.LENGTH_SHORT).show();
                });
            });
        },"ocean-crash-report").start();
    }
    private static String prepareReport() throws Exception {
        long priorStart=number(previous.getProperty("started"),0); File fatal=new File(directory,"uncaught.txt");
        String javaCrash=fatal.lastModified()>=priorStart?CrashReportStore.read(fatal):"";
        String os=""; boolean abnormal=false;
        if(Build.VERSION.SDK_INT>=30) try {
            ActivityManager manager=app.getSystemService(ActivityManager.class);
            int priorPid=(int)number(previous.getProperty("pid"),0);
            for(ApplicationExitInfo exit:manager.getHistoricalProcessExitReasons(app.getPackageName(),priorPid,8)) {
                if(!app.getPackageName().equals(exit.getProcessName())||exit.getTimestamp()<priorStart||exit.getTimestamp()>=started)continue;
                int reason=exit.getReason();
                abnormal=reason==ApplicationExitInfo.REASON_CRASH||reason==ApplicationExitInfo.REASON_CRASH_NATIVE||reason==ApplicationExitInfo.REASON_ANR||reason==ApplicationExitInfo.REASON_SIGNALED||reason==ApplicationExitInfo.REASON_LOW_MEMORY||reason==ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE||reason==ApplicationExitInfo.REASON_INITIALIZATION_FAILURE;
                os="Android exit reason: "+reasonName(reason)+" ("+reason+")\nExit status / signal: "+exit.getStatus()+"\nExit time: "+new Date(exit.getTimestamp())+"\nDescription: "+exit.getDescription()+"\nMemory PSS/RSS: "+exit.getPss()+" / "+exit.getRss()+" kB\n";
                if(reason==ApplicationExitInfo.REASON_ANR)try(InputStream in=exit.getTraceInputStream()){
                    if(in!=null){byte[] bytes=new byte[16384];int n=in.read(bytes);if(n>0)os+="\nANR trace (first 16 KB):\n"+new String(bytes,0,n,StandardCharsets.UTF_8);}
                }catch(Exception ignored){}
                break;
            }
        }
        catch(Exception unavailable) { os="Android exit inspection unavailable: "+unavailable.getClass().getSimpleName()+"\n"; }
        boolean unfinished=Boolean.parseBoolean(previous.getProperty("busy","false"));
        File pending=new File(directory,"report.txt");
        if(!javaCrash.isEmpty()||abnormal||unfinished){
            String report="Temporary Ocean crash diagnostics\n\n"+(javaCrash.isEmpty()?"No Java exception was captured. An interrupted request alone does not prove a crash.\n":javaCrash+"\n")
                +"\nPrevious version/build: "+previous.getProperty("version","unknown")+" / "+previous.getProperty("build","unknown")
                +"\nLast recorded step: "+previous.getProperty("stage","unavailable")+"\nRequest unfinished: "+unfinished+"\n\n"
                +(os.isEmpty()?"Android exit details unavailable (Android 11+ required; some system kills provide no trace).\n":os)
                +"\nCopy this report and send it back. Diagnostics stay on this device until you copy them. Prompt text, commands and API keys are not deliberately recorded.\n";
            CrashReportStore.write(pending,report);
        }
        String report=CrashReportStore.read(pending);
        return CrashReportStore.identity(report).equals(app.getSharedPreferences("crash-survival",0).getString("seen",""))?"":report;
    }
    public static synchronized String diagnosticSnapshot(Context context) {
        if(app==null) install(context.getApplicationContext());
        StringBuilder out=new StringBuilder();
        out.append("Ocean local crash diagnostics\n");
        out.append("Version: ").append(BuildConfig.VERSION_NAME).append("\n");
        out.append("Build: ").append(BuildConfig.OCEAN_BUILD_COMMIT).append("\n");
        out.append("Current stage: ").append(stage).append("\n");
        out.append("Directory: ").append(directory==null?"unavailable":directory.getAbsolutePath()).append("\n\n");
        try {
            String prepared=prepareReport();
            if(!prepared.isEmpty()) out.append("=== PREVIOUS CRASH / INTERRUPTION ===\n").append(prepared).append("\n");
        } catch(Throwable error) {
            out.append("Could not prepare Android exit report: ").append(error.getClass().getSimpleName()).append("\n");
        }
        if(directory!=null){
            String uncaught=CrashReportStore.read(new File(directory,"uncaught.txt"));
            if(!uncaught.isEmpty()) out.append("\n=== LAST JAVA UNCAUGHT EXCEPTION ===\n").append(uncaught).append("\n");
            Properties last=load(new File(directory,"session.properties"));
            if(!last.isEmpty()){
                out.append("\n=== CURRENT/PREVIOUS SESSION JOURNAL ===\n");
                out.append("started=").append(last.getProperty("started","unknown")).append("\n");
                out.append("pid=").append(last.getProperty("pid","unknown")).append("\n");
                out.append("version=").append(last.getProperty("version","unknown")).append("\n");
                out.append("build=").append(last.getProperty("build","unknown")).append("\n");
                out.append("busy=").append(last.getProperty("busy","unknown")).append("\n");
                out.append("stage=").append(last.getProperty("stage","unknown")).append("\n");
                out.append("stageTime=").append(last.getProperty("stageTime","unknown")).append("\n");
            }
        }
        return out.toString();
    }

    private static Properties load(File file){Properties p=new Properties();try(FileInputStream in=new FileInputStream(file)){p.load(in);}catch(Exception ignored){}return p;}
    private static long number(String text,long fallback){try{return Long.parseLong(text);}catch(Exception ignored){return fallback;}}
    private static String reasonName(int reason){switch(reason){case ApplicationExitInfo.REASON_CRASH:return "JAVA_CRASH";case ApplicationExitInfo.REASON_CRASH_NATIVE:return "NATIVE_CRASH";case ApplicationExitInfo.REASON_ANR:return "APP_NOT_RESPONDING";case ApplicationExitInfo.REASON_SIGNALED:return "SIGNAL";case ApplicationExitInfo.REASON_LOW_MEMORY:return "LOW_MEMORY";case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:return "EXCESSIVE_RESOURCE_USAGE";case ApplicationExitInfo.REASON_USER_REQUESTED:return "USER_REQUESTED_STOP";case ApplicationExitInfo.REASON_EXIT_SELF:return "EXIT_SELF";case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:return "INITIALIZATION_FAILURE";default:return "REASON_"+reason;}}
}
