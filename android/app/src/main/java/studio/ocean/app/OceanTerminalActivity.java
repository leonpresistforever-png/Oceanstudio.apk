package studio.ocean.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.nio.charset.StandardCharsets;
import studio.ocean.app.terminal.OceanTerminalRuntimeService;
import studio.ocean.app.terminal.TerminalSession;

/** Native Ocean Terminal surface backed by the runtime service and JNI PTY. */
public final class OceanTerminalActivity extends AppCompatActivity implements TerminalSession.Listener {
    private TextView output;
    private ScrollView scroll;
    private EditText input;
    private TerminalSession session;
    private OceanTerminalRuntimeService runtime;
    private boolean bound;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){bound=true;runtime=((OceanTerminalRuntimeService.RuntimeBinder)binder).service();startTerminal();}
        @Override public void onServiceDisconnected(ComponentName name){bound=false;runtime=null;}
    };

    @Override protected void onCreate(Bundle state){super.onCreate(state);setContentView(R.layout.activity_terminal);output=findViewById(R.id.terminal_output);scroll=findViewById(R.id.terminal_scroll);input=findViewById(R.id.terminal_input);findViewById(R.id.terminal_back).setOnClickListener(v->finish());findViewById(R.id.terminal_send).setOnClickListener(v->sendLine());input.setOnEditorActionListener((v,action,event)->{sendLine();return true;});bindKey(R.id.key_ctrl_c,new byte[]{3});bindKey(R.id.key_tab,new byte[]{9});bindKey(R.id.key_escape,new byte[]{27});bindKey(R.id.key_up,"\u001b[A".getBytes(StandardCharsets.UTF_8));bindKey(R.id.key_down,"\u001b[B".getBytes(StandardCharsets.UTF_8));bindKey(R.id.key_left,"\u001b[D".getBytes(StandardCharsets.UTF_8));bindKey(R.id.key_right,"\u001b[C".getBytes(StandardCharsets.UTF_8));Intent intent=new Intent(this,OceanTerminalRuntimeService.class);startService(intent);bindService(intent,connection,Context.BIND_AUTO_CREATE);}
    private void startTerminal(){try{session=runtime.createRecoverySession(30,100);session.attach(this);}catch(Exception error){output.setText(getString(R.string.terminal_start_failed,error.getMessage()));}}
    private void sendLine(){String line=input.getText().toString();if(session==null)return;try{session.write(line+"\n");input.setText("");}catch(Exception error){byte[] message=("\r\nwrite failed: "+error.getMessage()+"\r\n").getBytes(StandardCharsets.UTF_8);append(message,message.length);}}
    private void bindKey(int id,byte[] bytes){findViewById(id).setOnClickListener(v->{if(session!=null)try{session.write(bytes);}catch(Exception ignored){}});}
    @Override public void onOutput(byte[] data,int length){byte[] copy=java.util.Arrays.copyOf(data,length);runOnUiThread(()->append(copy,copy.length));}
    private void append(byte[] data,int length){output.append(new String(data,0,length,StandardCharsets.UTF_8));scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    @Override public void onExit(int exitCode){runOnUiThread(()->output.append("\r\n[process exited "+exitCode+"]\r\n"));}
    @Override protected void onDestroy(){if(session!=null)session.detach(this);if(bound)unbindService(connection);super.onDestroy();}
}
