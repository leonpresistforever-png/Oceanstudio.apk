package studio.ocean.app.device;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/** User-controlled permissions; Android remains the authority for every grant. */
public final class DeviceAccessActivity extends AppCompatActivity {
    private TextView status;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(0xffffffff);
        LinearLayout rows = new LinearLayout(this); rows.setOrientation(1);
        rows.setPadding(dp(24), dp(24), dp(24), dp(32)); scroll.addView(rows); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Device Access"); title.setTextSize(24); title.setTextColor(0xff202124); rows.addView(title);
        TextView help = new TextView(this);
        help.setText("Choose what Ocean can access. Live control acts on the visible screen; internal terminal tasks run separately. Screenshots and visible text used by the agent are sent to your configured model provider. Android still protects other apps’ private files and secure screens.");
        help.setTextSize(14); help.setLineSpacing(dp(3),1f); help.setPadding(0,dp(14),0,dp(20)); rows.addView(help);
        status = new TextView(this); status.setTextSize(14); status.setPadding(0,0,0,dp(20)); rows.addView(status);
        button(rows,"Shared files", () -> {
            if (Build.VERSION.SDK_INT >= 30) launch(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName())));
            else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},81);
        });
        button(rows,"Modify system settings", () -> launch(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName()))));
        if (Build.VERSION.SDK_INT >= 26) button(rows,"Install unknown apps", () -> launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))));
        button(rows,"Accessibility settings", () -> launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Switch live = new Switch(this); live.setText("Allow live agent control"); live.setPadding(0,dp(20),0,dp(20)); live.setChecked(DeviceControlService.enabled(this)); rows.addView(live);
        live.setOnCheckedChangeListener((v,on) -> { DeviceControlService.setEnabled(this,on); refresh(); });
        button(rows,"Stop live control", () -> {DeviceControlService.setEnabled(this,false);live.setChecked(false);refresh();});
        button(rows,"Runtime Ports · internal browser", () -> startActivity(new Intent(this,studio.ocean.app.runtime.RuntimePortsActivity.class)));
        button(rows,"Back",this::finish);
    }
    private void button(LinearLayout rows,String label,Runnable action) {
        View line=new View(this);line.setBackgroundColor(0xffe7e8eb);rows.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(0xff303238);b.setBackgroundColor(0xffffffff);
        rows.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));b.setOnClickListener(v->action.run());
    }
    private void launch(Intent intent) { try {startActivity(intent);}catch(Exception e){Toast.makeText(this,"This settings page is unavailable on this device",Toast.LENGTH_LONG).show();} }
    @Override public void onResume(){super.onResume();refresh();}
    private void refresh(){
        if(status==null)return;
        boolean files=Build.VERSION.SDK_INT>=30 ? Environment.isExternalStorageManager() : androidx.core.content.ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)==0;
        boolean installs=Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        status.setText("Accessibility: "+(DeviceControlService.connected()?"connected":"off")+"\nLive control: "+(DeviceControlService.enabled(this)?"allowed":"off")+"\nSystem settings: "+(Settings.System.canWrite(this)?"allowed":"off")+"\nInstall APKs: "+(installs?"allowed":"off")+"\nShared files: "+(files?"allowed":"off"));
    }
}
