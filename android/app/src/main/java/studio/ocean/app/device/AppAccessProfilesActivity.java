package studio.ocean.app.device;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.graphics.Typeface;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

public final class AppAccessProfilesActivity extends AppCompatActivity {
    private AppAccessPolicy policy;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        policy=new AppAccessPolicy(this);

        ScrollView scroll=new ScrollView(this); scroll.setBackgroundColor(0xffffffff);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(22),dp(24),dp(40));
        scroll.addView(root); setContentView(scroll);

        TextView title=text("App Access Profiles",26,true,0xff191817); root.addView(title);
        TextView help=text("Optionally restrict Ocean's visible-screen controls to apps you select. This scopes Accessibility/screenshot use; it does not change Android's private-file or secure-screen protections.",14,false,0xff7b7873);
        help.setPadding(0,dp(8),0,dp(16)); root.addView(help);

        Switch restrict=new Switch(this); restrict.setText("Restrict live control to selected apps"); restrict.setChecked(policy.restrictionEnabled()); root.addView(restrict);
        restrict.setOnCheckedChangeListener((v,on)->policy.setRestrictionEnabled(on));

        List<ApplicationInfo> apps=getPackageManager().getInstalledApplications(0);
        apps.sort(Comparator.comparing(a->String.valueOf(a.loadLabel(getPackageManager())).toLowerCase(Locale.ROOT)));
        int shown=0;
        for(ApplicationInfo app:apps){
            if(getPackageManager().getLaunchIntentForPackage(app.packageName)==null)continue;
            addApp(root,app); if(++shown>=100)break;
        }
    }

    private void addApp(LinearLayout root,ApplicationInfo app){
        String pkg=app.packageName;
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(14),dp(16),dp(14));
        card.setBackgroundResource(studio.ocean.app.R.drawable.auth_field_background);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);root.addView(card,cp);

        TextView name=text(String.valueOf(app.loadLabel(getPackageManager())),16,true,0xff191817);card.addView(name);
        TextView packageText=text(pkg,12,false,0xff7b7873);packageText.setPadding(0,dp(2),0,dp(8));card.addView(packageText);

        Switch inspect=new Switch(this);inspect.setText("Allow screen inspection");inspect.setChecked(policy.inspectAllowed(pkg)&&policy.restrictionEnabled());card.addView(inspect);
        Switch interact=new Switch(this);interact.setText("Allow interaction");interact.setChecked(policy.interactAllowed(pkg)&&policy.restrictionEnabled());card.addView(interact);
        Switch screenshot=new Switch(this);screenshot.setText("Allow screenshots");screenshot.setChecked(policy.screenshotAllowed(pkg)&&policy.restrictionEnabled());card.addView(screenshot);

        CompoundButton.OnCheckedChangeListener listener=(v,on)->policy.set(pkg,inspect.isChecked(),interact.isChecked(),screenshot.isChecked());
        inspect.setOnCheckedChangeListener(listener);interact.setOnCheckedChangeListener(listener);screenshot.setOnCheckedChangeListener(listener);
    }

    private TextView text(String s,int size,boolean bold,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,Typeface.BOLD);return t;
    }
}
