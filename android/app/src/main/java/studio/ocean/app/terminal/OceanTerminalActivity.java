package studio.ocean.app.terminal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.nio.charset.StandardCharsets;
import studio.ocean.app.OceanPaths;
import studio.ocean.app.R;

/** Native Ocean Terminal UI connected to a service-owned real PTY. */
public final class OceanTerminalActivity extends AppCompatActivity implements TerminalSession.Listener {
    private TextView output,status; private EditText input; private TerminalSession session; private boolean bound;
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder binder){bound=true;OceanTerminalRuntimeService service=((OceanTerminalRuntimeService.LocalBinder)binder).service();session=service.firstRunning();if(session==null)try{session=service.createSession(24,80);}catch(Exception error){status.setText(getString(R.string.terminal_start_failed,error.getMessage()));return;}session.addListener(OceanTerminalActivity.this);status.setText(OceanRuntimeState.isInstalled(OceanTerminalActivity.this)?R.string.terminal_runtime_ready:R.string.terminal_recovery_mode);}public void onServiceDisconnected(ComponentName n){bound=false;session=null;}};
    @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_terminal);output=findViewById(R.id.terminal_output);status=findViewById(R.id.terminal_status);input=findViewById(R.id.terminal_input);findViewById(R.id.terminal_back).setOnClickListener(v->finish());findViewById(R.id.terminal_ctrl_c).setOnClickListener(v->{if(session!=null)session.interrupt();});findViewById(R.id.terminal_tab).setOnClickListener(v->write("\t"));findViewById(R.id.terminal_escape).setOnClickListener(v->write("\u001b"));input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEND||event!=null){write(input.getText()+"\n");input.setText("");return true;}return false;});bindService(new Intent(this,OceanTerminalRuntimeService.class),connection,Context.BIND_AUTO_CREATE);}
    private void write(String value){if(session!=null&&!session.write(value))status.setText(R.string.terminal_write_failed);}
    @Override public void onOutput(byte[] bytes,int length){String value=new String(bytes,0,length,StandardCharsets.UTF_8);runOnUiThread(()->{output.append(value);if(output.length()>200000)output.setText(output.getText().subSequence(output.length()-150000,output.length()));});}
    @Override public void onExit(int code){runOnUiThread(()->status.setText(getString(R.string.terminal_exited,code)));}
    @Override protected void onDestroy(){if(session!=null)session.removeListener(this);if(bound)unbindService(connection);super.onDestroy();}
}
