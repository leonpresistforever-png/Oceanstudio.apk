package studio.ocean.app.capture;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.media.projection.MediaProjectionManager;
import android.os.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * Visible user-confirmation bridge for camera and MediaProjection operations.
 * Ocean never bypasses Android's consent UI.
 */
public final class OceanCaptureActivity extends AppCompatActivity {
    private static final int REQ_PHOTO=7101, REQ_RECORD=7102;
    public static final String PREF="ocean_capture_state";
    private String mode;
    private String outputPath;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        mode=getIntent().getStringExtra("mode");
        if("record".equals(mode)) startRecordConsent();
        else if("selfie".equals(mode)||"photo".equals(mode)) startPhoto();
        else { setResult(RESULT_CANCELED); finish(); }
    }

    private File outputDir(String kind){
        File base=getExternalFilesDir(kind);
        if(base==null)base=getFilesDir();
        if(!base.exists())base.mkdirs();
        return base;
    }

    private void startPhoto(){
        try{
            File out=new File(outputDir(Environment.DIRECTORY_PICTURES),
                    ("selfie".equals(mode)?"selfie-":"photo-")+System.currentTimeMillis()+".jpg");
            outputPath=out.getAbsolutePath();
            getSharedPreferences(PREF,0).edit()
                    .putString("photo_status","awaiting_confirmation")
                    .putString("photo_path",outputPath).apply();
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".diagnostics",out);
            Intent i=new Intent("android.media.action.IMAGE_CAPTURE");
            i.putExtra("output",uri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if("selfie".equals(mode)){
                i.putExtra("android.intent.extras.CAMERA_FACING",1);
                i.putExtra("android.intent.extra.USE_FRONT_CAMERA",true);
                i.putExtra("android.intent.extras.LENS_FACING_FRONT",1);
            }
            startActivityForResult(i,REQ_PHOTO);
        }catch(Throwable t){
            getSharedPreferences(PREF,0).edit().putString("photo_status","error").putString("photo_error",String.valueOf(t.getMessage())).apply();
            finish();
        }
    }

    private void startRecordConsent(){
        try{
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            getSharedPreferences(PREF,0).edit().putString("record_status","awaiting_confirmation").apply();
            startActivityForResult(m.createScreenCaptureIntent(),REQ_RECORD);
        }catch(Throwable t){
            getSharedPreferences(PREF,0).edit().putString("record_status","error").putString("record_error",String.valueOf(t.getMessage())).apply();
            finish();
        }
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==REQ_PHOTO){
            boolean ok=result==RESULT_OK && outputPath!=null && new File(outputPath).exists();
            getSharedPreferences(PREF,0).edit()
                    .putString("photo_status",ok?"ready":"cancelled")
                    .putString("photo_path",outputPath==null?"":outputPath).apply();
            finish();
        }else if(request==REQ_RECORD){
            if(result!=RESULT_OK||data==null){
                getSharedPreferences(PREF,0).edit().putString("record_status","cancelled").apply();finish();return;
            }
            Intent i=new Intent(this,OceanScreenRecordService.class)
                    .setAction(OceanScreenRecordService.ACTION_START)
                    .putExtra("result_code",result)
                    .putExtra("result_data",data);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i); else startService(i);
            finish();
        }
    }
}
