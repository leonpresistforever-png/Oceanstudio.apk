package studio.ocean.app.capture;

import android.app.*;
import android.content.*;
import android.hardware.display.*;
import android.media.MediaRecorder;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.File;

public final class OceanScreenRecordService extends Service {
    public static final String ACTION_START="studio.ocean.app.capture.START_RECORD";
    public static final String ACTION_STOP="studio.ocean.app.capture.STOP_RECORD";
    private MediaProjection projection;
    private MediaRecorder recorder;
    private VirtualDisplay display;
    private String outputPath;

    @Override public void onCreate(){
        super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel("ocean_record","Ocean screen recording",NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i==null)return START_NOT_STICKY;
        if(ACTION_STOP.equals(i.getAction())){stopRecording("stopped");stopSelf();return START_NOT_STICKY;}
        if(ACTION_START.equals(i.getAction())){
            startForeground(942,new Notification.Builder(this,"ocean_record").setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("Ocean screen recording").setContentText("Recording your screen with Android permission").setOngoing(true).build());
            try{startRecording(i);}catch(Throwable t){
                getSharedPreferences(OceanCaptureActivity.PREF,0).edit().putString("record_status","error").putString("record_error",String.valueOf(t.getMessage())).apply();
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    private void startRecording(Intent i)throws Exception{
        int code=i.getIntExtra("result_code",Activity.RESULT_CANCELED);
        Intent data;
        if(Build.VERSION.SDK_INT>=33)data=i.getParcelableExtra("result_data",Intent.class);
        else data=(Intent)i.getParcelableExtra("result_data");
        if(code!=Activity.RESULT_OK||data==null)throw new IllegalStateException("MediaProjection permission missing");

        WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm=new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int w=dm.widthPixels,h=dm.heightPixels;
        int max=1920;
        if(Math.max(w,h)>max){
            float s=max/(float)Math.max(w,h);w=Math.max(2,Math.round(w*s));h=Math.max(2,Math.round(h*s));
            w-=w%2;h-=h%2;
        }

        File dir=getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        if(dir==null)dir=getFilesDir();if(!dir.exists())dir.mkdirs();
        outputPath=new File(dir,"screen-"+System.currentTimeMillis()+".mp4").getAbsolutePath();

        recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setVideoSize(w,h);
        recorder.setVideoFrameRate(30);
        recorder.setVideoEncodingBitRate(Math.max(4_000_000,w*h*5));
        recorder.setOutputFile(outputPath);
        recorder.prepare();

        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        projection=m.getMediaProjection(code,data);
        if(projection==null)throw new IllegalStateException("Unable to create MediaProjection");
        display=projection.createVirtualDisplay("OceanRecord",w,h,dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,recorder.getSurface(),null,null);
        recorder.start();
        getSharedPreferences(OceanCaptureActivity.PREF,0).edit()
                .putString("record_status","recording").putString("record_path",outputPath).apply();
    }

    private void stopRecording(String state){
        try{if(recorder!=null)recorder.stop();}catch(Throwable ignored){}
        try{if(recorder!=null)recorder.reset();}catch(Throwable ignored){}
        try{if(recorder!=null)recorder.release();}catch(Throwable ignored){}
        recorder=null;
        try{if(display!=null)display.release();}catch(Throwable ignored){}
        display=null;
        try{if(projection!=null)projection.stop();}catch(Throwable ignored){}
        projection=null;
        boolean ok=outputPath!=null&&new File(outputPath).exists()&&new File(outputPath).length()>0;
        getSharedPreferences(OceanCaptureActivity.PREF,0).edit()
                .putString("record_status",ok?"ready":state)
                .putString("record_path",outputPath==null?"":outputPath).apply();
        stopForeground(true);
    }

    @Override public void onDestroy(){stopRecording("stopped");super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
