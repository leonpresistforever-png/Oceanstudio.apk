package studio.ocean.app;

import android.os.Bundle;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public final class PluginCenterActivity extends AppCompatActivity {
    private android.content.SharedPreferences prefs;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setContentView(R.layout.activity_plugins);
        prefs=getSharedPreferences("ocean_plugin_state",MODE_PRIVATE);
        try{OceanForgeInstaller.ensure(this);}catch(Exception ignored){}
        LinearLayout list=findViewById(R.id.plugin_list);

        add(list,"terminal","Ocean Terminal","Local shell, packages and project automation.",exists("usr/bin/bash"));
        add(list,"runtime","Runtime Ports","Local HTTP and noVNC browser/desktop sessions.",true);
        add(list,"device","Device Access","User-authorized visible Android interaction.",true);
        add(list,"apk","APK Lab","Inspect, rebuild, align, sign and install APK workspaces.",exists("usr/bin/ocean-apk-lab"));
        add(list,"forge","Ocean Forge","Local source workspace, tests, candidate builds, signature verification and update staging.",exists("usr/bin/ocean-forge"));
        add(list,"python","Python Tools","Python and pip workflows.",exists("usr/bin/python")||exists("usr/bin/python3"));
        add(list,"git","Git Tools","Local Git repository workflows.",exists("usr/bin/git"));
        add(list,"browser","Browser Tools","curl and local browser helpers.",exists("usr/bin/curl"));

        File directory=new File(getFilesDir(),"home/.ocean/plugins");
        File[] manifests=directory.listFiles((dir,name)->name.endsWith(".plugin"));
        if(manifests!=null){
            java.util.Arrays.sort(manifests,java.util.Comparator.comparing(File::getName));
            for(File manifest:manifests){
                try(FileInputStream input=new FileInputStream(manifest)){
                    Properties p=new Properties(); p.load(input);
                    String id=p.getProperty("id",manifest.getName().replace(".plugin","")).replaceAll("[^a-zA-Z0-9._-]","");
                    if(id.isEmpty())continue;
                    String name=p.getProperty("name",id);
                    String command=p.getProperty("command","");
                    String description=p.getProperty("description","Terminal-registered Ocean plugin.");
                    boolean available=command.isEmpty()||exists("usr/bin/"+command);
                    add(list,id,name,description+(command.isEmpty()?"":" · "+command),available);
                }catch(Exception ignored){}
            }
        }
    }

    private void add(LinearLayout root,String id,String name,String description,boolean installed){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(14),dp(16),dp(14));
        card.setBackgroundResource(R.drawable.auth_field_background);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.topMargin=dp(10);
        root.addView(card,cp);

        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row,new LinearLayout.LayoutParams(-1,-2));

        TextView title=new TextView(this);
        title.setText(name); title.setTextSize(16); title.setTextColor(0xff191817); title.setTypeface(null,Typeface.BOLD);
        row.addView(title,new LinearLayout.LayoutParams(0,-2,1f));

        Switch connected=new Switch(this);
        connected.setEnabled(installed);
        connected.setChecked(installed && prefs.getBoolean("connected_"+id,true));
        row.addView(connected);
        connected.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("connected_"+id,on).apply());

        TextView desc=new TextView(this);
        desc.setText(description+"\n"+(installed?"Installed":"Not installed"));
        desc.setTextSize(13); desc.setTextColor(0xff7b7873); desc.setPadding(0,dp(6),0,0);
        card.addView(desc);
    }

    private boolean exists(String relative){
        File direct=new File(getFilesDir(),relative);
        if(direct.exists())return true;
        String prefix="usr/bin/";
        if(relative.startsWith(prefix)){
            File overlay=new File(new File(getFilesDir(),"forge-tools/bin"),relative.substring(prefix.length()));
            return overlay.exists();
        }
        return false;
    }
}
