package studio.ocean.app.device;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

/** User-controlled permissions; Android remains the authority for every grant. */
public final class DeviceAccessActivity extends AppCompatActivity {
    private static final int INK=0xff171717;
    private static final int MUTED=0xff737373;
    private static final int BORDER=0xffe2e2e2;
    private static final int PLATE=0xfff3f3f3;
    private static final int WHITE=0xffffffff;

    private TextView status;
    private Switch liveSwitch;
    private TextView stopControl;

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(WHITE);
        getWindow().setNavigationBarColor(WHITE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(WHITE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(42));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("Device access", 29, true, INK);
        root.addView(title);
        TextView intro = text(
                "Choose which Android-facing capabilities Ocean may request. Permissions stay under Android's control, and enabling one category never silently grants another. Visible-screen control, shared files, APK installation and system settings remain separate boundaries.",
                14, false, MUTED);
        intro.setLineSpacing(dp(2),1.07f);
        LinearLayout.LayoutParams introP=new LinearLayout.LayoutParams(-1,-2);
        introP.topMargin=dp(8);introP.bottomMargin=dp(20);
        root.addView(intro,introP);

        status = text("",13,false,INK);
        status.setLineSpacing(dp(3),1.04f);
        status.setPadding(dp(16),dp(14),dp(16),dp(14));
        status.setBackground(roundRect(PLATE,18,BORDER));
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        section(root,"Live agent control",
                "This is the only control that lets the agent interact with the visible Android screen. It still cannot bypass secure screens, private app storage or permissions Android has not granted.");

        LinearLayout liveCard=card();
        root.addView(liveCard,cardParams());

        LinearLayout liveRow=new LinearLayout(this);
        liveRow.setGravity(Gravity.CENTER_VERTICAL);
        liveCard.addView(liveRow,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout liveCopy=new LinearLayout(this);
        liveCopy.setOrientation(LinearLayout.VERTICAL);
        liveRow.addView(liveCopy,new LinearLayout.LayoutParams(0,-2,1f));
        liveCopy.addView(text("Allow live agent control",16,true,INK));
        TextView liveDetail=text(
                "When enabled, Ocean may use the Accessibility service for tasks you explicitly give it. Screenshots or captured visible text are only used when a task requires them and may be sent to your configured model provider.",
                12,false,MUTED);
        liveDetail.setLineSpacing(dp(1),1.06f);
        LinearLayout.LayoutParams ldP=new LinearLayout.LayoutParams(-1,-2);ldP.topMargin=dp(4);
        liveCopy.addView(liveDetail,ldP);

        liveSwitch=new Switch(this);
        liveSwitch.setChecked(DeviceControlService.enabled(this));
        tintSwitch(liveSwitch);
        LinearLayout.LayoutParams swP=new LinearLayout.LayoutParams(dp(58),dp(44));swP.leftMargin=dp(12);
        liveRow.addView(liveSwitch,swP);
        liveSwitch.setOnCheckedChangeListener((v,on)->{
            DeviceControlService.setEnabled(this,on);
            refresh();
        });

        stopControl=actionButton("Stop current live-control session",true);
        LinearLayout.LayoutParams stopP=new LinearLayout.LayoutParams(-1,dp(48));stopP.topMargin=dp(14);
        liveCard.addView(stopControl,stopP);
        stopControl.setOnClickListener(v->{
            DeviceControlService.setEnabled(this,false);
            liveSwitch.setChecked(false);
            refresh();
        });

        section(root,"Android permissions",
                "These open Android's own permission screens. They are actions rather than master switches because Android decides the final state and can revoke access independently at any time.");

        LinearLayout permissions=card();
        root.addView(permissions,cardParams());
        actionRow(permissions,"Shared files",
                "Allow Ocean projects and terminal tools to work with user-selected/shared storage. This does not expose other apps' private data.",
                "Open",()->{
                    if(Build.VERSION.SDK_INT>=30)
                        launch(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+getPackageName())));
                    else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},81);
                });
        divider(permissions);
        actionRow(permissions,"Modify system settings",
                "Opens Android's special-access page for settings such as brightness or other values Android permits this app to change.",
                "Open",()->launch(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:"+getPackageName()))));
        if(Build.VERSION.SDK_INT>=26){
            divider(permissions);
            actionRow(permissions,"Install APK files",
                    "Allows Ocean's APK workflows to hand a package to Android's installer. Android still shows the normal installation confirmation and security UI.",
                    "Open",()->launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()))));
        }
        divider(permissions);
        actionRow(permissions,"Accessibility service",
                "Android's service page controls whether Ocean can inspect and interact with the visible interface. App-specific limits can be configured separately below.",
                "Open",()->launch(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        section(root,"Scope & runtime",
                "Use these pages to narrow what live control can do or inspect internal development sessions. They do not grant new Android privileges by themselves.");

        LinearLayout scope=card();
        root.addView(scope,cardParams());
        actionRow(scope,"App access profiles",
                "Restrict inspection, interaction and screenshots to selected installed apps, with separate controls for each capability.",
                "Configure",()->startActivity(new Intent(this,AppAccessProfilesActivity.class)));
        divider(scope);
        actionRow(scope,"Runtime Ports",
                "Inspect Ocean-local HTTP, noVNC and development listeners using the internal browser and interaction tools.",
                "Open",()->startActivity(new Intent(this,studio.ocean.app.runtime.RuntimePortsActivity.class)));

        TextView note=text(
                "Ocean never turns these permissions into root access. Android sandboxing, SELinux, secure-window protection and each app's private storage boundary continue to apply.",
                12,false,MUTED);
        note.setLineSpacing(dp(2),1.06f);
        LinearLayout.LayoutParams noteP=new LinearLayout.LayoutParams(-1,-2);noteP.topMargin=dp(20);
        root.addView(note,noteP);
    }

    private void section(LinearLayout root,String heading,String detail){
        TextView h=text(heading,18,true,INK);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(28);
        root.addView(h,hp);
        TextView d=text(detail,13,false,MUTED);
        d.setLineSpacing(dp(1),1.05f);
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,-2);dpv.topMargin=dp(5);dpv.bottomMargin=dp(10);
        root.addView(d,dpv);
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16),dp(4),dp(16),dp(4));
        c.setBackground(roundRect(PLATE,20,BORDER));
        return c;
    }

    private LinearLayout.LayoutParams cardParams(){
        return new LinearLayout.LayoutParams(-1,-2);
    }

    private void actionRow(LinearLayout parent,String heading,String detail,String action,Runnable run){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(14),0,dp(14));
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        copy.addView(text(heading,15,true,INK));
        TextView d=text(detail,12,false,MUTED);
        d.setLineSpacing(dp(1),1.04f);
        LinearLayout.LayoutParams dP=new LinearLayout.LayoutParams(-1,-2);dP.topMargin=dp(3);
        copy.addView(d,dP);

        TextView a=text(action+"  ›",12,true,INK);
        a.setGravity(Gravity.CENTER);
        a.setPadding(dp(10),0,dp(10),0);
        a.setBackground(roundRect(WHITE,12,BORDER));
        LinearLayout.LayoutParams aP=new LinearLayout.LayoutParams(-2,dp(38));aP.leftMargin=dp(12);
        row.addView(a,aP);
        row.setOnClickListener(v->run.run());
        a.setOnClickListener(v->run.run());
    }

    private TextView actionButton(String label,boolean destructive){
        TextView v=text(label,13,true,destructive?0xff8a2e25:INK);
        v.setGravity(Gravity.CENTER);
        v.setBackground(roundRect(destructive?0xffffefed:WHITE,14,destructive?0xffffc9c2:BORDER));
        return v;
    }

    private TextView text(String value,int size,boolean bold,int color){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }

    private GradientDrawable roundRect(int color,int radius,int stroke){
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(radius));
        if((stroke>>>24)!=0)g.setStroke(dp(1),stroke);
        return g;
    }

    private void divider(LinearLayout parent){
        View line=new View(this);line.setBackgroundColor(BORDER);
        parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void tintSwitch(Switch s){
        if(Build.VERSION.SDK_INT>=21){
            int[][] states={new int[]{android.R.attr.state_checked},new int[]{-android.R.attr.state_checked}};
            s.setThumbTintList(new ColorStateList(states,new int[]{0xff111111,0xff808080}));
            s.setTrackTintList(new ColorStateList(states,new int[]{0xffa8a8a8,0xffdddddd}));
        }
    }

    private void launch(Intent intent){
        try{startActivity(intent);}
        catch(Exception e){Toast.makeText(this,"This settings page is unavailable on this device",Toast.LENGTH_LONG).show();}
    }

    @Override public void onResume(){super.onResume();refresh();}

    private void refresh(){
        if(status==null)return;
        boolean files=Build.VERSION.SDK_INT>=30
                ? Environment.isExternalStorageManager()
                : androidx.core.content.ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)==0;
        boolean installs=Build.VERSION.SDK_INT<26 || getPackageManager().canRequestPackageInstalls();
        boolean control=DeviceControlService.enabled(this);
        status.setText(
                "Current access\n"+
                "Accessibility   "+(DeviceControlService.connected()?"Connected":"Off")+"\n"+
                "Live control     "+(control?"Allowed":"Off")+"\n"+
                "System settings  "+(Settings.System.canWrite(this)?"Allowed":"Off")+"\n"+
                "Install APKs     "+(installs?"Allowed":"Off")+"\n"+
                "Shared files     "+(files?"Allowed":"Off"));
        if(liveSwitch!=null && liveSwitch.isChecked()!=control)liveSwitch.setChecked(control);
        if(stopControl!=null){
            stopControl.setAlpha(control?1f:.45f);
            stopControl.setEnabled(control);
        }
    }
}
