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
import androidx.appcompat.app.AlertDialog;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileOutputStream;
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

        Button bootstrap=findViewById(R.id.forge_bootstrap);
        Button sdkStatus=findViewById(R.id.forge_sdk_status);
        Button configureSdk=findViewById(R.id.forge_configure_sdk);
        Button init=findViewById(R.id.forge_init);
        Button clone=findViewById(R.id.forge_clone);
        Button checkpoint=findViewById(R.id.forge_checkpoint);
        Button diff=findViewById(R.id.forge_diff);
        Button rollback=findViewById(R.id.forge_rollback);
        Button tools=findViewById(R.id.forge_tools);
        Button status=findViewById(R.id.forge_status);
        Button test=findViewById(R.id.forge_test);
        Button build=findViewById(R.id.forge_build);
        Button verify=findViewById(R.id.forge_verify);
        Button install=findViewById(R.id.forge_install);
        Button buildSigned=findViewById(R.id.forge_build_signed);
        commandButtons=new Button[]{bootstrap,sdkStatus,configureSdk,init,clone,checkpoint,diff,rollback,tools,status,test,build,verify,install,buildSigned};

        bootstrap.setOnClickListener(v->runForge("ocean-forge bootstrap",1800));
        sdkStatus.setOnClickListener(v->runForge("ocean-forge sdk-status",120));
        configureSdk.setOnClickListener(v->{
            String sdk=((EditText)findViewById(R.id.forge_sdk_root)).getText().toString().trim();
            String aapt2=((EditText)findViewById(R.id.forge_aapt2_path)).getText().toString().trim();
            if(!sdk.startsWith("/")||!aapt2.startsWith("/")){state.setText("Enter absolute SDK and aapt2 paths.");return;}
            runForge("ocean-forge configure-sdk "+shellQuote(sdk)+" "+shellQuote(aapt2),120);
        });

        init.setOnClickListener(v->{
            String path=((EditText)findViewById(R.id.forge_source_path)).getText().toString().trim();
            if(path.isEmpty()||!path.startsWith("/")){state.setText("Enter an absolute source directory first.");return;}
            runForge("ocean-forge init "+shellQuote(path),300);
        });
        checkpoint.setOnClickListener(v->runForge("ocean-forge checkpoint manual",120));
        diff.setOnClickListener(v->runForge("ocean-forge diff",120));
        rollback.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Rollback Forge workspace?")
                .setMessage("Restore the last Forge checkpoint and discard newer workspace changes.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Rollback",(d,w)->runForge("ocean-forge rollback",180))
                .show());

        clone.setOnClickListener(v->{
            String url=((EditText)findViewById(R.id.forge_repo_url)).getText().toString().trim();
            String branch=((EditText)findViewById(R.id.forge_repo_branch)).getText().toString().trim();
            if(url.isEmpty()){state.setText("Enter a Git repository URL.");return;}
            String command="ocean-forge clone "+shellQuote(url);
            if(!branch.isEmpty())command+=" "+shellQuote(branch);
            runForge(command,1800);
        });

        tools.setOnClickListener(v->runForge("ocean-forge tools",120));
        status.setOnClickListener(v->runForge("ocean-forge status",120));
        test.setOnClickListener(v->runForge("ocean-forge test",1800));
        build.setOnClickListener(v->runForge("ocean-forge build",1800));
        verify.setOnClickListener(v->runForge("ocean-forge verify",120));
        buildSigned.setOnClickListener(v->buildSignedCandidate());

        install.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Update Ocean?")
                .setMessage("Forge will verify the candidate signature first. Android PackageInstaller will handle the final update approval.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Verify & update",(d,w)->runForge("ocean-forge install",120))
                .show());

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

    private void buildSignedCandidate(){
        String keystore=((EditText)findViewById(R.id.forge_keystore_path)).getText().toString().trim();
        String alias=((EditText)findViewById(R.id.forge_key_alias)).getText().toString().trim();
        String store=((EditText)findViewById(R.id.forge_store_password)).getText().toString();
        String key=((EditText)findViewById(R.id.forge_key_password)).getText().toString();
        if(!keystore.startsWith("/")||alias.isEmpty()||store.isEmpty()||key.isEmpty()){
            state.setText("Enter keystore path, alias and both passwords.");
            return;
        }
        try{
            File stateDir=new File(getFilesDir(),"home/ocean-forge/state");
            if(!stateDir.isDirectory()&&!stateDir.mkdirs())throw new IllegalStateException("Could not create Forge state directory");
            File storeFile=new File(stateDir,"store-pass.tmp");
            File keyFile=new File(stateDir,"key-pass.tmp");
            writeSecret(storeFile,store);
            writeSecret(keyFile,key);
            ((EditText)findViewById(R.id.forge_store_password)).setText("");
            ((EditText)findViewById(R.id.forge_key_password)).setText("");
            runForge("ocean-forge build-signed "+shellQuote(keystore)+" "+shellQuote(alias)+" "+shellQuote(storeFile.getAbsolutePath())+" "+shellQuote(keyFile.getAbsolutePath()),1800);
        }catch(Exception error){
            state.setText("Could not prepare signing credentials");
            output.append("\n"+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));
        }
    }

    private static void writeSecret(File file,String value) throws Exception{
        try(FileOutputStream out=new FileOutputStream(file,false)){out.write(value.getBytes(StandardCharsets.UTF_8));out.flush();}
        file.setReadable(false,false);file.setWritable(false,false);
        file.setReadable(true,true);file.setWritable(true,true);
    }

    private void setButtons(boolean enabled){for(Button button:commandButtons)button.setEnabled(enabled);}
    private static String shellQuote(String value){return "'"+value.replace("'","'\\''")+"'";}

    @Override protected void onDestroy(){
        if(bound){unbindService(connection);bound=false;}
        super.onDestroy();
    }
}
