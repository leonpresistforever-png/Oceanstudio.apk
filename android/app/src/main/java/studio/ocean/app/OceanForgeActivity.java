package studio.ocean.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.nio.charset.StandardCharsets;
import studio.ocean.app.terminal.OceanTerminalRuntimeService;

public final class OceanForgeActivity extends AppCompatActivity {
    private OceanTerminalRuntimeService runtime;
    private boolean bound;
    private boolean running;
    private TextView output;
    private TextView state;
    private Button[] commandButtons;

    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name, IBinder binder){
            runtime=((OceanTerminalRuntimeService.LocalBinder)binder).service();
            bound=true;
            state.setText("Forge runtime connected");
        }
        @Override public void onServiceDisconnected(ComponentName name){
            bound=false; runtime=null; state.setText("Forge runtime disconnected");
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocean_forge);
        output=findViewById(R.id.forge_output);
        state=findViewById(R.id.forge_state);

        Button init=findViewById(R.id.forge_init);
        Button tools=findViewById(R.id.forge_tools);
        Button status=findViewById(R.id.forge_status);
        Button test=findViewById(R.id.forge_test);
        Button build=findViewById(R.id.forge_build);
        Button verify=findViewById(R.id.forge_verify);
        Button install=findViewById(R.id.forge_install);
        commandButtons=new Button[]{init,tools,status,test,build,verify,install};

        init.setOnClickListener(v->{
            String path=((EditText)findViewById(R.id.forge_source_path)).getText().toString().trim();
            if(path.isEmpty()||!path.startsWith("/")){state.setText("Enter an absolute source directory first.");return;}
            runForge("ocean-forge init "+shellQuote(path),300);
        });
        tools.setOnClickListener(v->runForge("ocean-forge tools",120));
        status.setOnClickListener(v->runForge("ocean-forge status",120));
        test.setOnClickListener(v->runForge("ocean-forge test",300));
        build.setOnClickListener(v->runForge("ocean-forge build",300));
        verify.setOnClickListener(v->runForge("ocean-forge verify",120));
        install.setOnClickListener(v->runForge("ocean-forge install",120));

        bindService(new Intent(this,OceanTerminalRuntimeService.class),connection,Context.BIND_AUTO_CREATE);
    }

    private void runForge(String command,int timeout){
        if(running){state.setText("A Forge command is already running.");return;}
        if(!bound||runtime==null){state.setText("Forge runtime is still connecting.");return;}
        running=true; setButtons(false);
        output.setText("$ "+command+"\n");
        state.setText("Running…");

        runtime.requestCommand(command,null,timeout,new OceanTerminalRuntimeService.CommandCallback(){
            @Override public void onOutput(byte[] bytes,int length){
                String chunk=new String(bytes,0,length,StandardCharsets.UTF_8);
                output.append(chunk);
            }
            @Override public void onExit(int code){
                running=false;setButtons(true);
                state.setText(code==0?"Completed":"Exited with code "+code);
            }
            @Override public void onFailure(Throwable error){
                running=false;setButtons(true);
                state.setText("Forge failed");
                output.append("\n"+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));
            }
        });
    }

    private void setButtons(boolean enabled){for(Button button:commandButtons)button.setEnabled(enabled);}
    private static String shellQuote(String value){return "'"+value.replace("'","'\\''")+"'";}

    @Override protected void onDestroy(){
        if(bound){unbindService(connection);bound=false;}
        super.onDestroy();
    }
}
