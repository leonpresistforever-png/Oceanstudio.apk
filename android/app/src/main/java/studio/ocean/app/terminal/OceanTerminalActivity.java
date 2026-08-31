package studio.ocean.app.terminal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import studio.ocean.app.BuildConfig;
import studio.ocean.app.OceanPaths;
import studio.ocean.app.R;

/** Native Ocean Terminal UI connected to a service-owned real PTY. */
public final class OceanTerminalActivity extends AppCompatActivity implements TerminalSession.Listener {
    private TextView output,status; private EditText input; private TerminalSession session;
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
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);diagnosticMode=getIntent().getStringExtra("diagnostic_test");TerminalDiagnosticBundle.beginAttempt(this);String attemptName=diagnosticMode==null?"PRODUCTION TERMINAL":"PTY TEST "+diagnosticMode;TerminalDiagnosticBundle.setTest(this,attemptName);TerminalDiagnosticBundle.log("session-state.log","activeDiagnosticTest="+attemptName);TerminalStartupLog.initialize(this);previousCrashHandler=TerminalStartupLog.installCrashCapture();
        setContentView(R.layout.activity_terminal);output=findViewById(R.id.terminal_output);status=findViewById(R.id.terminal_status);input=findViewById(R.id.terminal_input);failureActions=findViewById(R.id.terminal_failure_actions);
        findViewById(R.id.terminal_back).setOnClickListener(v->finish());
        findViewById(R.id.terminal_ctrl_c).setOnClickListener(v->{if(session!=null)session.interrupt();});
        findViewById(R.id.terminal_tab).setOnClickListener(v->write("\t"));findViewById(R.id.terminal_escape).setOnClickListener(v->write("\u001b"));
        findViewById(R.id.terminal_retry).setOnClickListener(v->startOceanSession());
        findViewById(R.id.terminal_details).setOnClickListener(v->showDiagnosticLog());
        findViewById(R.id.terminal_recovery).setOnClickListener(v->startRecoverySession());
        input.setOnEditorActionListener((v,action,event)->{boolean ime=action==EditorInfo.IME_ACTION_SEND;boolean enter=event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN;if(!ime&&!enter)return false;String command=input.getText().toString();if(command.isEmpty())return true;input.setText("");write(command+"\r");return true;});
        TerminalStartupLog.stage("02","bind runtime service");TerminalDiagnosticBundle.log("startup.log","[J02] service bind requested");
        if(!bindService(new Intent(this,OceanTerminalRuntimeService.class),connection,Context.BIND_AUTO_CREATE))showFailure("Runtime service binding failed",null);
    }
    private void startOceanSession(){
        if(service==null)return;failureActions.setVisibility(View.GONE);status.setText(R.string.terminal_connecting);TerminalDiagnosticBundle.log("startup.log","[J07] createSession requested");
        try{TerminalSession candidate=service.firstRunning();attach(candidate==null?service.createSession(24,80):candidate,true);}
        catch(Throwable error){showFailure("Ocean runtime failed validation: "+safeMessage(error),error);}
    }
    private void startRecoverySession(){
        if(service==null)return;failureActions.setVisibility(View.GONE);
        try{attach(service.createRecoverySession(24,80),false);}
        catch(Throwable error){showFailure("Recovery shell failed: "+safeMessage(error),error);}
    }
    private void startDiagnosticOceanSession(){if(service==null)return;failureActions.setVisibility(View.GONE);try{attach(service.createDiagnosticOceanSession(24,80),true);if("C".equals(diagnosticMode)){TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log","waiting for RUNNING");writeWhenRunning("echo OCEAN_PTY_OK\r",0);}}catch(Throwable error){showFailure("Ocean Bash diagnostic failed: "+safeMessage(error),error);}}
    private void writeWhenRunning(String value,int attempt){if(session==null||attempt>50)return;if(session.state()==TerminalSession.State.RUNNING){TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log","script write echo exactly once");write(value);}else input.postDelayed(()->writeWhenRunning(value,attempt+1),50);}
    private void attach(TerminalSession candidate,boolean installed){
        if(session!=null)session.removeListener(this);session=candidate;session.addListener(this);input.setEnabled(session.isRunning());
        LocalProcessDiagnostics.Snapshot process=session.diagnostics(new OceanPaths(this).prefix().getAbsolutePath());
        status.setText((installed?getString(R.string.terminal_runtime_ready):getString(R.string.terminal_recovery_mode))+buildIdentity(installed)+"\n"+process.summary());
        status.append("\nSession="+session.state()+" PID="+session.pid()+" masterFd="+session.masterFd());
    }
    private void showFailure(String message,Throwable error){TerminalStartupLog.failure(message,error);status.setText(message+buildIdentity(false));failureActions.setVisibility(View.VISIBLE);input.setEnabled(false);}
    private void showDiagnosticLog(){startActivity(new Intent(this,OceanTerminalDiagnosticsActivity.class));}
    private static String safeMessage(Throwable error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    private void write(String value){if(session==null)return;if(session.state()==TerminalSession.State.RUNNING&&"exit".equals(value.replace("\r","").trim()))TerminalDiagnosticBundle.markCritical(this,"EXIT_COMMAND_SENT");TerminalSession.WriteResult result=session.write(value);if(result==TerminalSession.WriteResult.NATIVE_WRITE_FAILED)status.setText(R.string.terminal_write_failed);else if(result==TerminalSession.WriteResult.SESSION_ALREADY_EXITED)TerminalDiagnosticBundle.log("session-state.log","late UI write safely rejected state="+session.state());}
    private String buildIdentity(boolean installed){return "\nBuild commit: "+BuildConfig.OCEAN_BUILD_COMMIT+"\nBootstrap build: "+BuildConfig.OCEAN_BOOTSTRAP_BUILD_COMMIT+"\nBootstrap version: "+BuildConfig.OCEAN_BOOTSTRAP_VERSION+"\nBootstrap SHA-256: "+BuildConfig.OCEAN_BOOTSTRAP_SHA256+"\nRuntime installed: "+(installed?"yes":"no");}
    @Override public void onOutput(byte[] bytes,int length){String value=new String(bytes,0,length,StandardCharsets.UTF_8);if("C".equals(diagnosticMode)&&!scriptedExitSent){synchronized(diagnosticOutput){diagnosticOutput.append(value);if(diagnosticOutput.length()>4096)diagnosticOutput.delete(0,diagnosticOutput.length()-4096);String normalized=diagnosticOutput.toString().replace("\r\n","\n");if(normalized.contains("\nOCEAN_PTY_OK\n")){scriptedExitSent=true;TerminalDiagnosticBundle.log("test-c-ocean-pty-ok.log","OCEAN_PTY_OK result line observed; sending exit exactly once; no later writes scheduled");input.post(()->write("exit\r"));}}}TerminalDiagnosticBundle.log("startup.log","[J12] UI callback received bytes="+length);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;output.append(value);TerminalDiagnosticBundle.log("startup.log","[J13] PTY bytes rendered bytes="+length);if(output.length()>200000)output.setText(output.getText().subSequence(output.length()-150000,output.length()));});}
    @Override public void onExit(int code){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){input.setEnabled(false);status.setText(getString(R.string.terminal_exited,code)+buildIdentity(OceanRuntimeState.isInstalled(this)));}});}
    @Override protected void onDestroy(){if(session!=null)session.removeListener(this);if(bound)unbindService(connection);TerminalStartupLog.restoreCrashCapture(previousCrashHandler);super.onDestroy();}
}
