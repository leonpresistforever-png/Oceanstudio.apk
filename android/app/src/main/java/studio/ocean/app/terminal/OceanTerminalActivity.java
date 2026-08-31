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
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder binder){
            bound=true;service=((OceanTerminalRuntimeService.LocalBinder)binder).service();
            TerminalStartupLog.stage("03","TerminalActivity service connected");startOceanSession();
        }
        public void onServiceDisconnected(ComponentName n){bound=false;service=null;session=null;TerminalStartupLog.stage("17","runtime service disconnected");}
    };
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);TerminalStartupLog.initialize(this);previousCrashHandler=TerminalStartupLog.installCrashCapture();
        setContentView(R.layout.activity_terminal);output=findViewById(R.id.terminal_output);status=findViewById(R.id.terminal_status);input=findViewById(R.id.terminal_input);failureActions=findViewById(R.id.terminal_failure_actions);
        findViewById(R.id.terminal_back).setOnClickListener(v->finish());
        findViewById(R.id.terminal_ctrl_c).setOnClickListener(v->{if(session!=null)session.interrupt();});
        findViewById(R.id.terminal_tab).setOnClickListener(v->write("\t"));findViewById(R.id.terminal_escape).setOnClickListener(v->write("\u001b"));
        findViewById(R.id.terminal_retry).setOnClickListener(v->startOceanSession());
        findViewById(R.id.terminal_details).setOnClickListener(v->showDiagnosticLog());
        findViewById(R.id.terminal_recovery).setOnClickListener(v->startRecoverySession());
        input.setOnEditorActionListener((v,action,event)->{boolean ime=action==EditorInfo.IME_ACTION_SEND;boolean enter=event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN;if(!ime&&!enter)return false;String command=input.getText().toString();if(command.isEmpty())return true;input.setText("");write(command+"\r");return true;});
        TerminalStartupLog.stage("02","bind runtime service");
        if(!bindService(new Intent(this,OceanTerminalRuntimeService.class),connection,Context.BIND_AUTO_CREATE))showFailure("Runtime service binding failed",null);
    }
    private void startOceanSession(){
        if(service==null)return;failureActions.setVisibility(View.GONE);status.setText(R.string.terminal_connecting);
        try{TerminalSession candidate=service.firstRunning();attach(candidate==null?service.createSession(24,80):candidate,true);}
        catch(Throwable error){showFailure("Ocean runtime failed validation: "+safeMessage(error),error);}
    }
    private void startRecoverySession(){
        if(service==null)return;failureActions.setVisibility(View.GONE);
        try{attach(service.createRecoverySession(24,80),false);}
        catch(Throwable error){showFailure("Recovery shell failed: "+safeMessage(error),error);}
    }
    private void attach(TerminalSession candidate,boolean installed){
        if(session!=null)session.removeListener(this);session=candidate;session.addListener(this);input.setEnabled(true);
        LocalProcessDiagnostics.Snapshot process=session.diagnostics(new OceanPaths(this).prefix().getAbsolutePath());
        status.setText((installed?getString(R.string.terminal_runtime_ready):getString(R.string.terminal_recovery_mode))+buildIdentity(installed)+"\n"+process.summary());
    }
    private void showFailure(String message,Throwable error){TerminalStartupLog.failure(message,error);status.setText(message+buildIdentity(false));failureActions.setVisibility(View.VISIBLE);input.setEnabled(false);}
    private void showDiagnosticLog(){try{output.setText(PreviousProcessExit.read(this)+"\n"+new String(Files.readAllBytes(TerminalStartupLog.file(this).toPath()),StandardCharsets.UTF_8));}catch(Exception error){output.setText(error.toString());}}
    private static String safeMessage(Throwable error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
    private void write(String value){if(session!=null&&!session.write(value))status.setText(R.string.terminal_write_failed);}
    private String buildIdentity(boolean installed){return "\nBuild commit: "+BuildConfig.OCEAN_BUILD_COMMIT+"\nBootstrap build: "+BuildConfig.OCEAN_BOOTSTRAP_BUILD_COMMIT+"\nBootstrap version: "+BuildConfig.OCEAN_BOOTSTRAP_VERSION+"\nBootstrap SHA-256: "+BuildConfig.OCEAN_BOOTSTRAP_SHA256+"\nRuntime installed: "+(installed?"yes":"no");}
    @Override public void onOutput(byte[] bytes,int length){String value=new String(bytes,0,length,StandardCharsets.UTF_8);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;output.append(value);if(output.length()>200000)output.setText(output.getText().subSequence(output.length()-150000,output.length()));});}
    @Override public void onExit(int code){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())status.setText(getString(R.string.terminal_exited,code)+buildIdentity(OceanRuntimeState.isInstalled(this)));});}
    @Override protected void onDestroy(){if(session!=null)session.removeListener(this);if(bound)unbindService(connection);TerminalStartupLog.restoreCrashCapture(previousCrashHandler);super.onDestroy();}
}
