package studio.ocean.app.device;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

public final class AppAccessProfilesActivity extends AppCompatActivity {
    private static final int INK=0xff181818;
    private static final int MUTED=0xff747474;
    private static final int BORDER=0xffe2e2e2;
    private static final int PLATE=0xfff4f4f4;
    private AppAccessPolicy policy;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        policy=new AppAccessPolicy(this);
        getWindow().setStatusBarColor(0xffffffff);
        getWindow().setNavigationBarColor(0xffffffff);

        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xffffffff);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(22),dp(26),dp(22),dp(42));
        scroll.addView(root);setContentView(scroll);

        root.addView(text("App access profiles",28,true,INK));
        TextView help=text("Limit visible-screen control to apps you explicitly select. Inspection, interaction and screenshots are separate permissions here, so an app can be readable without being clickable or capturable.",14,false,MUTED);
        help.setLineSpacing(dp(2),1.06f);
        LinearLayout.LayoutParams helpP=new LinearLayout.LayoutParams(-1,-2);helpP.topMargin=dp(8);helpP.bottomMargin=dp(18);
        root.addView(help,helpP);

        LinearLayout master=card();
        root.addView(master,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout masterRow=new LinearLayout(this);masterRow.setGravity(Gravity.CENTER_VERTICAL);
        master.addView(masterRow,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        masterRow.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        copy.addView(text("Restrict live control",16,true,INK));
        TextView desc=text("When this is off, normal live-control rules apply. Turn it on to make the per-app profiles below an additional allow-list boundary.",12,false,MUTED);
        LinearLayout.LayoutParams descP=new LinearLayout.LayoutParams(-1,-2);descP.topMargin=dp(4);copy.addView(desc,descP);
        Switch restrict=new Switch(this);restrict.setChecked(policy.restrictionEnabled());tintSwitch(restrict);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(58),dp(44));rp.leftMargin=dp(12);masterRow.addView(restrict,rp);
        restrict.setOnCheckedChangeListener((v,on)->policy.setRestrictionEnabled(on));

        TextView heading=text("Installed apps",18,true,INK);
        LinearLayout.LayoutParams headingP=new LinearLayout.LayoutParams(-1,-2);headingP.topMargin=dp(26);
        root.addView(heading,headingP);
        TextView note=text("Only launchable apps are shown. These controls do not override Android secure windows, private files or app-level authentication.",12,false,MUTED);
        LinearLayout.LayoutParams noteP=new LinearLayout.LayoutParams(-1,-2);noteP.topMargin=dp(4);noteP.bottomMargin=dp(8);
        root.addView(note,noteP);

        List<ApplicationInfo> apps=getPackageManager().getInstalledApplications(0);
        apps.sort(Comparator.comparing(a->String.valueOf(a.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT)));
        int shown=0;
        for(ApplicationInfo app:apps){
            if(getPackageManager().getLaunchIntentForPackage(app.packageName)==null)continue;
            addApp(root,app);if(++shown>=100)break;
        }
    }

    private void addApp(LinearLayout root,ApplicationInfo app){
        String pkg=app.packageName;
        LinearLayout card=card();
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);root.addView(card,cp);

        card.addView(text(String.valueOf(app.loadLabel(getPackageManager())),16,true,INK));
        TextView packageText=text(pkg,11,false,MUTED);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(2);pp.bottomMargin=dp(8);
        card.addView(packageText,pp);

        Switch inspect=permissionRow(card,"Screen inspection","Allow Ocean to read visible labels and accessibility structure for this app.",policy.profileInspect(pkg));
        divider(card);
        Switch interact=permissionRow(card,"Interaction","Allow taps, typing, scrolling and other visible-screen actions in this app.",policy.profileInteract(pkg));
        divider(card);
        Switch screenshot=permissionRow(card,"Screenshots","Allow this app's visible surface to be included in screenshots used for the task.",policy.profileScreenshot(pkg));

        CompoundButton.OnCheckedChangeListener listener=(v,on)->policy.set(pkg,inspect.isChecked(),interact.isChecked(),screenshot.isChecked());
        inspect.setOnCheckedChangeListener(listener);interact.setOnCheckedChangeListener(listener);screenshot.setOnCheckedChangeListener(listener);
    }

    private Switch permissionRow(LinearLayout card,String heading,String detail,boolean checked){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));
        card.addView(row,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));
        copy.addView(text(heading,14,true,INK));
        TextView d=text(detail,11,false,MUTED);d.setLineSpacing(0,1.04f);
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,-2);dpv.topMargin=dp(2);copy.addView(d,dpv);
        Switch s=new Switch(this);s.setChecked(checked);tintSwitch(s);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(58),dp(42));sp.leftMargin=dp(12);row.addView(s,sp);
        return s;
    }

    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(15),dp(13),dp(15),dp(13));
        c.setBackground(roundRect(PLATE,18,BORDER));return c;
    }

    private void divider(LinearLayout parent){
        View v=new View(this);v.setBackgroundColor(BORDER);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void tintSwitch(Switch s){
        if(Build.VERSION.SDK_INT>=21){
            int[][] states={new int[]{android.R.attr.state_checked},new int[]{-android.R.attr.state_checked}};
            s.setThumbTintList(new ColorStateList(states,new int[]{0xff111111,0xff808080}));
            s.setTrackTintList(new ColorStateList(states,new int[]{0xffa8a8a8,0xffdddddd}));
        }
    }

    private GradientDrawable roundRect(int color,int radius,int stroke){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;
    }

    private TextView text(String s,int size,boolean bold,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
}
