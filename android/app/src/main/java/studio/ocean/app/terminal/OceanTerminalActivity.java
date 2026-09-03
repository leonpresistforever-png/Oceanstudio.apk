package studio.ocean.app.terminal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.KeyEvent;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import jackpal.androidterm.emulatorview.ColorScheme;
import jackpal.androidterm.emulatorview.EmulatorView;
import java.nio.charset.StandardCharsets;
import studio.ocean.app.R;

/** Native Ocean Terminal UI connected to a service-owned real PTY. */
public final class OceanTerminalActivity extends AppCompatActivity implements TerminalSession.Listener {
    private TextView status; private EmulatorView terminalView; private OceanEmulatorSession emulatorSession; private TerminalSession session;
    private OceanTerminalRuntimeService service; private boolean bound;
    private View failureActions; private Thread.UncaughtExceptionHandler previousCrashHandler;
    private String diagnosticMode;private boolean scriptedExitSent;private final StringBuilder diagnosticOutput=new StringBuilder();
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder binder){
            TerminalDiagnosticBundle.log("startup.log","[J04] service connected BEGIN");
            bound=true;service=((OceanTerminalRuntimeService.LocalBinder)binder).service();
            TerminalStartupLog.stage("03","TerminalActivity service connected");TerminalDiagnosticBundle.log("startup.log","[J04] service connected END");if("A".equals(diagnosticMode))startRecoverySession();else if("B".equals(diagnosticMode)||"C".equals(diagnosticMode))startDiagnosticOceanSession();else startOceanSession();
        }
        public void onServiceDisconnected(ComponentName n){bound=false;service=null;session=null;TerminalStartupLog.stage("17","runtime service disconnected");}
    };
    private TextView outputView; private android.widget.EditText inputView;
    private void safeClick(int id, View.OnClickListener l){ View v = findViewById(id); if (v != null) v.setOnClickListener(l); }
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        try {
            diagnosticMode=getIntent().getStringExtra("diagnostic_test");
            TerminalDiagnosticBundle.beginAttempt(this);
            String attemptName=diagnosticMode==null?"PRODUCTION TERMINAL":"PTY TEST "+diagnosticMode;
            TerminalDiagnosticBundle.setTest(this,attemptName);
            TerminalDiagnosticBundle.log("session-state.log","activeDiagnosticTest="+attemptName);
            TerminalStartupLog.initialize(this);
            previousCrashHandler=TerminalStartupLog.installCrashCapture();
            setContentView(R.layout.activity_terminal);
            status=findViewById(R.id.terminal_status);
            terminalView=findViewById(R.id.terminal_emulator);
            failureActions=findViewById(R.id.terminal_failure_actions);
            outputView=findViewById(R.id.terminal_output);
            inputView=findViewById(R.id.terminal_input);

            safeClick(R.id.terminal_back, v->finish());
            safeClick(R.id.terminal_ctrl, v->{ if(terminalView!=null) terminalView.sendControlKey(); });
            safeClick(R.id.terminal_alt, v->{ if(terminalView!=null) terminalView.sendAltKey(); });
            safeClick(R.id.terminal_ctrl_c, v->{ if(session!=null) write("\u0003"); });
            safeClick(R.id.terminal_tab, v->write("\t"));
            safeClick(R.id.terminal_escape, v->write("\u001b"));
            safeClick(R.id.terminal_left, v->sendTerminalKey(KeyEvent.KEYCODE_DPAD_LEFT));
            safeClick(R.id.terminal_down, v->sendTerminalKey(KeyEvent.KEYCODE_DPAD_DOWN));
            safeClick(R.id.terminal_up, v->sendTerminalKey(KeyEvent.KEYCODE_DPAD_UP));
            safeClick(R.id.terminal_right, v->sendTerminalKey(KeyEvent.KEYCODE_DPAD_RIGHT));
            safeClick(R.id.terminal_home, v->sendTerminalKey(KeyEvent.KEYCODE_MOVE_HOME));
            safeClick(R.id.terminal_end, v->sendTerminalKey(KeyEvent.KEYCODE_MOVE_END));
            safeClick(R.id.terminal_page_up, v->sendTerminalKey(KeyEvent.KEYCODE_PAGE_UP));
            safeClick(R.id.terminal_page_down, v->sendTerminalKey(KeyEvent.KEYCODE_PAGE_DOWN));
            safeClick(R.id.terminal_retry, v->startOceanSession());
            safeClick(R.id.terminal_details, v->showDiagnosticLog());
            safeClick(R.id.terminal_recovery, v->startRecoverySession());

            if (inputView != null) {
                inputView.setOnEditorActionListener((v, actionId, event) -> {
                    String cmd = inputView.getText().toString();
                    write(cmd + "\n");
                    inputView.setText("");
                    return true;
                });
            }

            // Check if a previous crash was recorded
            java.io.File crashLog = new java.io.File(getFilesDir(), "logs/last-crash.log");
            java.io.File javaCrash = new java.io.File(getFilesDir(), "logs/terminal-diagnostics/java-crash.log");
            if ((crashLog.isFile() && crashLog.length() > 0) || (javaCrash.isFile() && javaCrash.length() > 0)) {
                if (status != null) {
                    status.setText("⚠️ Crash recorded in previous session! Tap here to inspect live logs.");
                    status.setVisibility(View.VISIBLE);
                    status.setOnClickListener(v -> showDiagnosticLog());
                }
            } else if (status != null) {
                status.setOnClickListener(v -> showDiagnosticLog());
            }

            TerminalStartupLog.stage("02","bind runtime service");
            TerminalDiagnosticBundle.log("startup.log","[J02] service bind requested");
            startService(new Intent(this,OceanTerminalRuntimeService.class));
            if(!bindService(new Intent(this,OceanTerminalRuntimeService.class),connection,Context.BIND_AUTO_CREATE)) {
                showFailure("Runtime service binding failed",null);
            }
        } catch (Throwable t) {
            TerminalStartupLog.failure("Activity.onCreate crashed", t);
            showFailure("Terminal initialization failed: " + t.getMessage(), t);
        }
    }
    private void startOceanSession(){
        if(service==null)return;if(failureActions!=null)failureActions.setVisibility(View.GONE);if(status!=null){status.setText(R.string.terminal_connecting);status.setVisibility(View.VISIBLE);}TerminalDiagnosticBundle.log("startup.log","[J07] createSession requested");
        TerminalSession candidate=service.firstRunning();if(candidate!=null){attach(candidate,true);return;}
        service.requestTerminalSession(24,80,new OceanTerminalRuntimeService.SessionCallback(){
            @Override public void onProgress(OceanTerminalRuntimeService.RuntimeState state,String detail,long completed,long total){if(isFinishing()||isDestroyed())return;String progress=total>0?"\n"+completed+" / "+total:"";if(status!=null){status.setText(detail+progress);status.setVisibility(View.VISIBLE);}}
            @Override public void onReady(TerminalSession ready){if(isFinishing()||isDestroyed())return;attach(ready,true);}
            @Override public void onFailure(Throwable error){if(isFinishing()||isDestroyed())return;showFailure("Ocean runtime setup failed: "+safeMessage(error),error);}
        });
    }
    private void startRecoverySession(){
        if(service==null)return;if(failureActions!=null)failureActions.setVisibility(View.GONE);
        try{attach(service.createRecoverySession(24,80),false);}
        catch(Throwable error){showFailure("Recovery shell failed: "+safeMessage(error),error);}
    }
    private void startDiagnosticOceanSession(){if(service==null)return;if(failureActions!=null)failureActions.setVisibility(View.GONE);service.requestDiagnosticOceanSession(24,80,new OceanTerminalRuntimeService.SessionCallback(){public void onProgress(OceanTerminalRuntimeService.RuntimeState state,String detail,long done,long total){if(!isFinishing()&&!isDestroyed()&&status!=null){status.setText(detail);status.setVisibility(View.VISIBLE);}}public void onReady(TerminalSession ready){if(isFinishing()||isDestroyed())return;attach(ready,true);if("C".equals(diagnosticMode)){TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log","waiting for RUNNING");writeWhenRunning("echo OCEAN_PTY_OK\r",0);}}public void onFailure(Throwable error){if(!isFinishing()&&!isDestroyed())showFailure("Ocean Bash diagnostic failed: "+safeMessage(error)+". Use Install/Repair Ocean Runtime from the production terminal.",error);}});}
    private void writeWhenRunning(String value,int attempt){if(session==null||attempt>50)return;if(session.state()==TerminalSession.State.RUNNING){TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log","script write echo exactly once");write(value);}else if(terminalView!=null) terminalView.postDelayed(()->writeWhenRunning(value,attempt+1),50);}
    private void attach(TerminalSession candidate,boolean installed){
        if(session!=null)session.removeListener(this);if(emulatorSession!=null)emulatorSession.finish();session=candidate;
        if(terminalView!=null){
            emulatorSession=new OceanEmulatorSession();emulatorSession.attachTransport(session);emulatorSession.setColorScheme(new ColorScheme(0xffe9e8e3,0xff11110f,0xff11110f,0xffd8d6cf));
            terminalView.setDensity(getResources().getDisplayMetrics());terminalView.attachSession(emulatorSession);terminalView.setTextSize(14);terminalView.setUseCookedIME(true);terminalView.setAltSendsEsc(true);terminalView.setTermType("xterm-256color");
            terminalView.requestFocus();terminalView.onResume();
        }
        session.addListener(this);if(status!=null)status.setVisibility(View.GONE);
    }
    private void showFailure(String message,Throwable error){TerminalStartupLog.failure(message,error);if(status!=null){status.setText(message+"\n(Tap here to view full diagnostic logs)");status.setVisibility(View.VISIBLE);status.setOnClickListener(v->showDiagnosticLog());}if(failureActions!=null)failureActions.setVisibility(View.VISIBLE);}
    private void showDiagnosticLog(){startActivity(new Intent(this,OceanTerminalDiagnosticsActivity.class));}
    private void sendTerminalKey(int keyCode){if(terminalView==null)return;long now=android.os.SystemClock.uptimeMillis();terminalView.onKeyDown(keyCode,new KeyEvent(now,now,KeyEvent.ACTION_DOWN,keyCode,0));terminalView.onKeyUp(keyCode,new KeyEvent(now,now,KeyEvent.ACTION_UP,keyCode,0));}
    private static String safeMessage(Throwable error){
        if(error==null)return "unknown error";
        StringBuilder sb=new StringBuilder();Throwable curr=error;
        while(curr!=null){
            if(sb.length()>0)sb.append(" -> ");
            sb.append(curr.getClass().getSimpleName()).append(": ").append(curr.getMessage()!=null?curr.getMessage():"");
            curr=curr.getCause();
        }
        return sb.toString();
    }
    private void write(String value){if(session==null)return;if(session.state()==TerminalSession.State.RUNNING&&"exit".equals(value.replace("\r","").trim()))TerminalDiagnosticBundle.markCritical(this,"EXIT_COMMAND_SENT");TerminalSession.WriteResult result=session.write(value);if(result==TerminalSession.WriteResult.NATIVE_WRITE_FAILED)status.setText(R.string.terminal_write_failed);else if(result==TerminalSession.WriteResult.SESSION_ALREADY_EXITED)TerminalDiagnosticBundle.log("session-state.log","late UI write safely rejected state="+session.state());}
    @Override public void onOutput(byte[] bytes,int length){byte[] copy=java.util.Arrays.copyOf(bytes,length);String value=new String(copy,StandardCharsets.UTF_8);if("C".equals(diagnosticMode)&&!scriptedExitSent){synchronized(diagnosticOutput){diagnosticOutput.append(value);if(diagnosticOutput.length()>4096)diagnosticOutput.delete(0,diagnosticOutput.length()-4096);String normalized=diagnosticOutput.toString().replace("\r\n","\n");if(normalized.contains("\nOCEAN_PTY_OK\n")){scriptedExitSent=true;TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log","OCEAN_PTY_OK result line observed; sending exit exactly once; no later writes scheduled");terminalView.post(()->write("exit\r"));}}}runOnUiThread(()->{if(isFinishing()||isDestroyed()||emulatorSession==null)return;emulatorSession.feed(copy,copy.length);});}
    @Override public void onExit(int code){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){status.setText(getString(R.string.terminal_exited,code));status.setVisibility(View.VISIBLE);}});}
    @Override protected void onResume(){super.onResume();if(terminalView!=null&&emulatorSession!=null)terminalView.onResume();}
    @Override protected void onPause(){if(terminalView!=null&&emulatorSession!=null)terminalView.onPause();super.onPause();}
    @Override protected void onDestroy(){if(session!=null)session.removeListener(this);if(emulatorSession!=null)emulatorSession.finish();if(bound)unbindService(connection);TerminalStartupLog.restoreCrashCapture(previousCrashHandler);super.onDestroy();}
}
