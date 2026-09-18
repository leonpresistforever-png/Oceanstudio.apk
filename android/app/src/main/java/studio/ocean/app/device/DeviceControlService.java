package studio.ocean.app.device;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.util.Base64;
import android.view.accessibility.*;
import org.json.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in visible-device tools. No private-data privileges or secure-screen bypass. */
public final class DeviceControlService extends AccessibilityService {
    private static volatile DeviceControlService instance;
    private static final String PREF="ocean_device_access", STOP="studio.ocean.app.STOP_CONTROL";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,AccessibilityNodeInfo> refs=new HashMap<>();
    private long generation;
    public static boolean connected(){return instance!=null;}
    public static boolean enabled(Context c){return c.getSharedPreferences(PREF,0).getBoolean("live_control_enabled",false);}
    public static void setEnabled(Context c,boolean on){
        c.getSharedPreferences(PREF,0).edit().putBoolean("live_control_enabled",on).apply();
        DeviceControlService s=instance;if(s!=null)s.main.post(()->{s.clearRefs();s.notification();});
    }
    @Override protected void onServiceConnected(){instance=this;setEnabled(this,false);}
    @Override public void onDestroy(){setEnabled(this,false);clearRefs();instance=null;super.onDestroy();}
    @Override public void onInterrupt(){setEnabled(this,false);}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)clearRefs();}
    @Override public int onStartCommand(Intent i,int flags,int id){if(i!=null&&STOP.equals(i.getAction()))setEnabled(this,false);return START_NOT_STICKY;}
    private void clearRefs(){for(AccessibilityNodeInfo n:refs.values())n.recycle();refs.clear();generation++;}
    private void notification(){
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(!enabled(this)){manager.cancel(440);return;}
        manager.createNotificationChannel(new NotificationChannel("live_control","Live device control",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,0,new Intent(this,DeviceControlService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,DeviceAccessActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        manager.notify(440,new Notification.Builder(this,"live_control").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Ocean live control is enabled").setContentText("Tap Stop to revoke agent control").setContentIntent(open).addAction(new Notification.Action.Builder(null,"Stop",stop).build()).setOngoing(true).build());
    }
    private static JSONObject result(String key,Object value){try{return new JSONObject().put(key,value);}catch(Exception e){return new JSONObject();}}
    public static JSONObject execute(Context c,String tool,JSONObject a)throws Exception{
        if(tool.equals("device_status"))return new JSONObject().put("connected",connected()).put("live_control_enabled",enabled(c)).put("exit_code",0);
        DeviceControlService s=instance;if(s==null||!enabled(c))throw new IllegalStateException("Enable Accessibility and live control in Device Access first");
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Device tools must run on a worker");
        CountDownLatch done=new CountDownLatch(1);JSONObject[] answer={null};AtomicBoolean expired=new AtomicBoolean();
        s.main.post(()->{
            try{
                if(expired.get()||!enabled(s)||instance!=s)throw new IllegalStateException("Live control stopped");
                if(tool.equals("capture_android_screen")){s.capture(answer,done);return;}
                if(tool.equals("inspect_android_screen"))answer[0]=s.snapshot();
                else if(tool.equals("list_android_apps"))answer[0]=s.apps(a.optString("query",""));
                else if(tool.equals("open_android_app")){
                    String pkg=a.getString("package_name");Intent launch=s.getPackageManager().getLaunchIntentForPackage(pkg);
                    if(launch==null)throw new IllegalArgumentException("No launchable app for package "+pkg);
                    s.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));answer[0]=result("launched",pkg);
                }else if(tool.equals("interact_android_screen")){if(s.interact(a,answer,done))return;}
                else throw new IllegalArgumentException("Unknown device tool");
            }catch(Exception e){answer[0]=result("error",e.getMessage());}
            done.countDown();
        });
        try{if(!done.await(12,TimeUnit.SECONDS))throw new IllegalStateException("Device action timed out; inspect before retrying");}
        finally{expired.set(true);}
        JSONObject out=answer[0]==null?result("error","No result"):answer[0];return out.put("exit_code",out.has("error")?-1:0);
    }
    private JSONObject apps(String query)throws Exception{
        JSONArray apps=new JSONArray();String q=query.toLowerCase(Locale.ROOT);
        for(ApplicationInfo app:getPackageManager().getInstalledApplications(0)){
            String label=String.valueOf(app.loadLabel(getPackageManager()));
            if((app.packageName.toLowerCase(Locale.ROOT).contains(q)||label.toLowerCase(Locale.ROOT).contains(q))&&getPackageManager().getLaunchIntentForPackage(app.packageName)!=null){
                apps.put(new JSONObject().put("package_name",app.packageName).put("label",label));if(apps.length()>=100)break;
            }
        }
        return new JSONObject().put("apps",apps).put("limit",100);
    }
    private JSONObject snapshot()throws Exception{
        clearRefs();AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)throw new IllegalStateException("No visible accessible window");
        JSONArray nodes=new JSONArray();ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(root);int count=0;
        while(!queue.isEmpty()){
            AccessibilityNodeInfo n=queue.remove();
            if(count++<400){
                for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)queue.add(child);}
                if(n.isVisibleToUser()&&!n.isPassword()){
                    String ref=generation+":"+nodes.length();Rect b=new Rect();n.getBoundsInScreen(b);
                    nodes.put(new JSONObject().put("ref",ref).put("text",String.valueOf(n.getText())).put("description",String.valueOf(n.getContentDescription())).put("view_id",n.getViewIdResourceName()).put("clickable",n.isClickable()).put("editable",n.isEditable()).put("bounds",new JSONArray().put(b.left).put(b.top).put(b.right).put(b.bottom)));
                    refs.put(ref,n);continue;
                }
            }
            n.recycle();
        }
        return new JSONObject().put("nodes",nodes).put("coordinate_space","native screen pixels");
    }
    private boolean interact(JSONObject a,JSONObject[] out,CountDownLatch done)throws Exception{
        String action=a.getString("action");
        if(action.equals("back")||action.equals("home")||action.equals("recents")||action.equals("notifications")||action.equals("quick_settings")){
            int global = action.equals("back") ? GLOBAL_ACTION_BACK
                    : action.equals("home") ? GLOBAL_ACTION_HOME
                    : action.equals("recents") ? GLOBAL_ACTION_RECENTS
                    : action.equals("notifications") ? GLOBAL_ACTION_NOTIFICATIONS
                    : GLOBAL_ACTION_QUICK_SETTINGS;
            boolean accepted=performGlobalAction(global);
            out[0]=accepted?result("accepted",true):result("error","Android rejected global action");return false;
        }
        if(action.equals("tap")||action.equals("long_press")||action.equals("swipe")){
            int x=a.getInt("x"),y=a.getInt("y"),tx=a.optInt("to_x",x),ty=a.optInt("to_y",y);
            android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
            if(x<0||y<0||tx<0||ty<0||x>=dm.widthPixels||tx>=dm.widthPixels||y>=dm.heightPixels||ty>=dm.heightPixels)throw new IllegalArgumentException("Coordinates outside screen");
            Path path=new Path();path.moveTo(x,y);if(action.equals("swipe"))path.lineTo(tx,ty);
            long duration=action.equals("tap")?70:action.equals("long_press")?650:350;
            boolean accepted=dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build(),new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription g){out[0]=result("completed",true);done.countDown();}
                @Override public void onCancelled(GestureDescription g){out[0]=result("error","Gesture cancelled");done.countDown();}
            },main);
            if(!accepted){out[0]=result("error","Gesture rejected");return false;}return true;
        }
        AccessibilityNodeInfo n=refs.get(a.getString("ref"));
        if(n==null||!n.refresh()||!n.isVisibleToUser()||n.isPassword())throw new IllegalArgumentException("Stale or protected target; inspect again");
        boolean ok;
        if(action.equals("click"))ok=n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        else if(action.equals("type")){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,a.getString("text"));ok=n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);}
        else if(action.equals("scroll"))ok=n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
        else throw new IllegalArgumentException("Unsupported action");
        out[0]=ok?result("completed",true):result("error","App rejected the action");return false;
    }
    private void capture(JSONObject[] out,CountDownLatch done){
        if(Build.VERSION.SDK_INT<30){out[0]=result("error","Accessibility screenshots require Android 11 or later");done.countDown();return;}
        takeScreenshot(0,getMainExecutor(),new TakeScreenshotCallback(){
            public void onFailure(int code){out[0]=result("error","Screen capture unavailable or protected ("+code+")");done.countDown();}
            public void onSuccess(ScreenshotResult shot){
                try(HardwareBuffer buffer=shot.getHardwareBuffer()){
                    if(!enabled(DeviceControlService.this))throw new IllegalStateException("Live control stopped");
                    Bitmap hardware=Bitmap.wrapHardwareBuffer(buffer,shot.getColorSpace());if(hardware==null)throw new IllegalStateException("Empty screenshot");
                    Bitmap bitmap=hardware.copy(Bitmap.Config.ARGB_8888,false);hardware.recycle();ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,75,bytes);
                    out[0]=new JSONObject().put("image_base64",Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)).put("media_type","image/jpeg").put("image_width",bitmap.getWidth()).put("image_height",bitmap.getHeight()).put("coordinate_space","native screen pixels");bitmap.recycle();
                }catch(Exception e){out[0]=result("error",e.getMessage());}done.countDown();
            }
        });
    }
}
