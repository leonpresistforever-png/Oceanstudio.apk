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
    private Button stopButton;
    private OceanTerminalRuntimeService.CommandHandle activeHandle;
    private OceanForgeSigningStore signingStore;

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
        signingStore=new OceanForgeSigningStore(this);
        try{
            OceanForgeInstaller.ensure(this);
            OceanForgeInstaller.ensureSourceBundle(this);
        }catch(Exception error){
            state.setText("Forge setup incomplete");
            output.setText(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());
        }

        Button bootstrap=findViewById(R.id.forge_bootstrap);
        Button seed=findViewById(R.id.forge_seed);
        Button detectSdk=findViewById(R.id.forge_detect_sdk);
        Button sdkStatus=findViewById(R.id.forge_sdk_status);
        Button configureSdk=findViewById(R.id.forge_configure_sdk);
        Button init=findViewById(R.id.forge_init);
        Button clone=findViewById(R.id.forge_clone);
        Button checkpoint=findViewById(R.id.forge_checkpoint);
        Button diff=findViewById(R.id.forge_diff);
        Button rollback=findViewById(R.id.forge_rollback);
        Button doctor=findViewById(R.id.forge_doctor);
        Button tools=findViewById(R.id.forge_tools);
        Button status=findViewById(R.id.forge_status);
        Button test=findViewById(R.id.forge_test);
        Button build=findViewById(R.id.forge_build);
        Button verify=findViewById(R.id.forge_verify);
        Button install=findViewById(R.id.forge_install);
        Button saveSigning=findViewById(R.id.forge_save_signing);
        Button forgetSigning=findViewById(R.id.forge_forget_signing);
        Button buildSigned=findViewById(R.id.forge_build_signed);
        stopButton=findViewById(R.id.forge_stop);
        commandButtons=new Button[]{bootstrap,seed,detectSdk,sdkStatus,configureSdk,init,clone,checkpoint,diff,rollback,doctor,tools,status,test,build,verify,install,buildSigned};

        bootstrap.setOnClickListener(v->runForge("ocean-forge bootstrap && ocean-forge bootstrap-sdk",3600));
        seed.setOnClickListener(v->runForge("ocean-forge seed",300));
        detectSdk.setOnClickListener(v->runForge("ocean-forge detect-sdk",120));
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
            runForge(command,3600);
        });

        doctor.setOnClickListener(v->runForge("ocean-forge doctor",300));
        tools.setOnClickListener(v->runForge("ocean-forge tools",120));
        status.setOnClickListener(v->runForge("ocean-forge status",120));
        test.setOnClickListener(v->runForge("ocean-forge test",3600));
        build.setOnClickListener(v->runForge("ocean-forge build",3600));
        verify.setOnClickListener(v->runForge("ocean-forge verify",120));
        saveSigning.setOnClickListener(v->saveSigningIdentity());
        forgetSigning.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Forget saved signing identity?")
                .setMessage("Ocean will delete the private keystore copy and its encrypted saved credentials.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Forget",(d,w)->{
                    signingStore.clear();
                    updateSigningStatus();
                }).show());
        updateSigningStatus();

        buildSigned.setOnClickListener(v->buildSignedCandidate());
        stopButton.setOnClickListener(v->{
            OceanTerminalRuntimeService.CommandHandle handle=activeHandle;
            if(handle!=null){
                state.setText("Stopping Forge task…");
                handle.cancel();
            }
        });

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
        running=true; setButtons(false); stopButton.setEnabled(true);
        output.setText("$ "+command+"\n");
        state.setText("Running…");

        activeHandle=runtime.requestCommand(command,null,timeout,new OceanTerminalRuntimeService.CommandCallback(){
            @Override public void onOutput(byte[] bytes,int length){
                String chunk=new String(bytes,0,length,StandardCharsets.UTF_8);
                output.append(chunk);
            }
            @Override public void onExit(int code){
                running=false;activeHandle=null;setButtons(true);stopButton.setEnabled(false);
                state.setText(code==0?"Completed":code==130?"Stopped":"Exited with code "+code);
            }
            @Override public void onFailure(Throwable error){
                running=false;activeHandle=null;setButtons(true);stopButton.setEnabled(false);
                state.setText("Forge failed");
                output.append("\n"+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));
            }
        });
    }

    private void saveSigningIdentity(){
        String path=((EditText)findViewById(R.id.forge_keystore_path)).getText().toString().trim();
        String alias=((EditText)findViewById(R.id.forge_key_alias)).getText().toString().trim();
        String store=((EditText)findViewById(R.id.forge_store_password)).getText().toString();
        String key=((EditText)findViewById(R.id.forge_key_password)).getText().toString();
        if(!path.startsWith("/")||alias.isEmpty()||store.isEmpty()||key.isEmpty()){
            state.setText("Enter keystore path, alias and both passwords before saving.");
            return;
        }
        try{
            signingStore.save(new File(path),alias,store,key);
            ((EditText)findViewById(R.id.forge_store_password)).setText("");
            ((EditText)findViewById(R.id.forge_key_password)).setText("");
            updateSigningStatus();
            state.setText("Signing identity saved privately");
        }catch(Exception error){
            state.setText("Could not save signing identity");
            output.append("\n"+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()));
        }
    }

    private void updateSigningStatus(){
        TextView status=findViewById(R.id.forge_signing_status);
        if(signingStore!=null&&signingStore.isConfigured()){
            String sha=signingStore.sha256();
            if(sha.length()>12)sha=sha.substring(0,12);
            status.setText("Saved privately · alias "+signingStore.alias()+" · keystore SHA-256 "+sha+"…");
        }else status.setText("No signing identity saved");
    }

    private void buildSignedCandidate(){
        String keystore=((EditText)findViewById(R.id.forge_keystore_path)).getText().toString().trim();
        String alias=((EditText)findViewById(R.id.forge_key_alias)).getText().toString().trim();
        String store=((EditText)findViewById(R.id.forge_store_password)).getText().toString();
        String key=((EditText)findViewById(R.id.forge_key_password)).getText().toString();

        boolean manual=keystore.startsWith("/")&&!alias.isEmpty()&&!store.isEmpty()&&!key.isEmpty();
        if(!manual&&signingStore.isConfigured()){
            keystore=signingStore.keystoreFile().getAbsolutePath();
            alias=signingStore.alias();
            store=signingStore.storePassword();
            key=signingStore.keyPassword();
        }
        if(!keystore.startsWith("/")||alias.isEmpty()||store.isEmpty()||key.isEmpty()){
            state.setText("Enter signing credentials or save a private signing identity first.");
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
            runForge("ocean-forge build-signed "+shellQuote(keystore)+" "+shellQuote(alias)+" "+shellQuote(storeFile.getAbsolutePath())+" "+shellQuote(keyFile.getAbsolutePath()),3600);
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
