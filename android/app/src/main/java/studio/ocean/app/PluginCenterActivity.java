package studio.ocean.app;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Native capability center for tools the Ocean Agent can use.
 *
 * This screen does not invent connector access and does not install wrappers.
 * A connection only enables an already available local capability for the agent.
 */
public final class PluginCenterActivity extends AppCompatActivity {
    private static final int INK=0xff18181b;
    private static final int MUTED=0xff71717a;
    private static final int BORDER=0xffe7e5e4;
    private static final int SURFACE=0xffffffff;
    private static final int SURFACE_MUTED=0xfff5f5f4;
    private static final int BACKGROUND=0xfffafafa;

    private static final class Item {
        final String id,name,description;
        final boolean available;
        final int icon;
        final boolean registered;
        Item(String id,String name,String description,boolean available,int icon,boolean registered){
            this.id=id;this.name=name;this.description=description;this.available=available;this.icon=icon;this.registered=registered;
        }
    }

    private SharedPreferences prefs;
    private LinearLayout list;
    private LinearLayout installedStrip;
    private TextView installedEmpty;
    private TextView searchEmpty;
    private EditText search;
    private final List<Item> items=new ArrayList<>();

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setContentView(R.layout.activity_plugins);
        getWindow().setStatusBarColor(SURFACE);
        getWindow().setNavigationBarColor(SURFACE);

        prefs=getSharedPreferences("ocean_plugin_state",MODE_PRIVATE);
        list=findViewById(R.id.plugin_list);
        installedStrip=findViewById(R.id.plugin_installed_strip);
        installedEmpty=findViewById(R.id.plugin_installed_empty);
        searchEmpty=findViewById(R.id.plugin_search_empty);
        search=findViewById(R.id.plugin_search);

        ImageButton back=findViewById(R.id.plugin_back);
        if(back!=null)back.setOnClickListener(v->finish());

        try{OceanForgeInstaller.ensure(this);}catch(Exception ignored){}
        loadItems();
        render("");

        if(search!=null)search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){render(s==null?"":s.toString());}
            @Override public void afterTextChanged(Editable s){}
        });
    }

    private void loadItems(){
        items.clear();
        HashSet<String> ids=new HashSet<>();

        addBuiltin(ids,new Item("terminal","Ocean Terminal","Run local shell commands, packages and project workflows.",exists("usr/bin/bash"),R.drawable.ic_terminal,false));
        addBuiltin(ids,new Item("runtime","Runtime Ports","Inspect and control real local HTTP or noVNC sessions.",true,R.drawable.ic_ports,false));
        addBuiltin(ids,new Item("device","Device Access","Use visible Android controls only through permissions you grant.",true,R.drawable.ic_agent,false));
        addBuiltin(ids,new Item("apk","APK Lab","Inspect, rebuild, align and sign local APK workspaces.",exists("usr/bin/ocean-apk-lab"),R.drawable.ic_tools,false));
        addBuiltin(ids,new Item("python","Python","Run Python and pip workflows in the Ocean runtime.",exists("usr/bin/python")||exists("usr/bin/python3"),R.drawable.ic_terminal_tools,false));
        addBuiltin(ids,new Item("git","Git","Read and operate local Git repositories with the installed Git CLI.",exists("usr/bin/git"),R.drawable.ic_github,false));
        addBuiltin(ids,new Item("browser","Browser Tools","Use curl and local browser helpers for web/runtime workflows.",exists("usr/bin/curl"),R.drawable.ic_browser,false));

        try{
            JSONObject result=OceanPluginRuntime.list(this);
            JSONArray arr=result.optJSONArray("plugins");
            if(arr!=null)for(int i=0;i<arr.length();i++){
                JSONObject p=arr.optJSONObject(i);
                if(p==null)continue;
                String id=p.optString("id","").trim();
                if(id.isEmpty()||ids.contains(id))continue;
                ids.add(id);
                String name=p.optString("name",id);
                String description=p.optString("description","Registered local Ocean capability.");
                boolean available=p.optBoolean("available",false);
                items.add(new Item(id,name,description,available,R.drawable.ic_tools,true));
            }
        }catch(Exception ignored){}
    }

    private void addBuiltin(HashSet<String> ids,Item item){
        ids.add(item.id);items.add(item);
    }

    private boolean connected(Item item){
        return item.available&&prefs.getBoolean("connected_"+item.id,true);
    }

    private void render(String query){
        String q=query==null?"":query.trim().toLowerCase(java.util.Locale.ROOT);
        list.removeAllViews();
        installedStrip.removeAllViews();

        int visible=0,installed=0;
        for(Item item:items){
            if(connected(item)){
                addInstalledTile(item);
                installed++;
            }
            if(!q.isEmpty()&&!item.name.toLowerCase(java.util.Locale.ROOT).contains(q)
                    &&!item.description.toLowerCase(java.util.Locale.ROOT).contains(q)
                    &&!item.id.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            addRow(item);
            visible++;
        }

        installedEmpty.setVisibility(installed==0?View.VISIBLE:View.GONE);
        searchEmpty.setVisibility(visible==0?View.VISIBLE:View.GONE);
    }

    private void addInstalledTile(Item item){
        LinearLayout tile=new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(dp(2),0,dp(2),0);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(76),dp(92));
        tp.rightMargin=dp(8);
        installedStrip.addView(tile,tp);

        FrameLayout iconBox=new FrameLayout(this);
        iconBox.setBackground(roundRect(SURFACE,16,BORDER));
        iconBox.setElevation(dp(1));
        tile.addView(iconBox,new LinearLayout.LayoutParams(dp(58),dp(58)));

        ImageView icon=new ImageView(this);
        icon.setImageResource(item.icon);
        icon.setColorFilter(INK);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER);
        iconBox.addView(icon,ip);

        TextView label=new TextView(this);
        label.setText(item.name);
        label.setTextColor(INK);
        label.setTextSize(11);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(72),dp(26));
        lp.topMargin=dp(6);
        tile.addView(label,lp);

        tile.setOnClickListener(v->showConnectionSheet(item));
    }

    private void addRow(Item item){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(14),0,dp(14));
        row.setMinimumHeight(dp(82));
        list.addView(row,new LinearLayout.LayoutParams(-1,-2));

        FrameLayout iconBox=new FrameLayout(this);
        iconBox.setBackground(roundRect(SURFACE_MUTED,15,BORDER));
        LinearLayout.LayoutParams ibp=new LinearLayout.LayoutParams(dp(52),dp(52));
        ibp.rightMargin=dp(14);
        row.addView(iconBox,ibp);

        ImageView icon=new ImageView(this);
        icon.setImageResource(item.icon);
        icon.setColorFilter(INK);
        iconBox.addView(icon,new FrameLayout.LayoutParams(dp(26),dp(26),Gravity.CENTER));

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1f));

        LinearLayout titleRow=new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        copy.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));

        TextView title=new TextView(this);
        title.setText(item.name);
        title.setTextColor(INK);
        title.setTextSize(16);
        title.setTypeface(null,Typeface.NORMAL);
        titleRow.addView(title,new LinearLayout.LayoutParams(-2,-2));

        if(item.registered){
            TextView badge=new TextView(this);
            badge.setText(" LOCAL ");
            badge.setTextColor(MUTED);
            badge.setTextSize(9);
            badge.setTypeface(null,Typeface.BOLD);
            badge.setBackground(roundRect(SURFACE_MUTED,999,0x00000000));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(20));
            bp.leftMargin=dp(8);
            titleRow.addView(badge,bp);
        }

        TextView desc=new TextView(this);
        desc.setText(item.description+(item.available?"":" · Required command not installed"));
        desc.setTextColor(item.available?MUTED:0xff9a3d32);
        desc.setTextSize(13);
        desc.setMaxLines(2);
        desc.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,-2);
        dpv.topMargin=dp(3);
        copy.addView(desc,dpv);

        TextView action=new TextView(this);
        boolean on=connected(item);
        action.setText(item.available?(on?"•••":"+"):"!");
        action.setTextSize(on?19:26);
        action.setTextColor(item.available?INK:0xff9a3d32);
        action.setGravity(Gravity.CENTER);
        action.setTypeface(null,Typeface.BOLD);
        action.setBackground(roundRect(SURFACE_MUTED,999,item.available?0x00000000:BORDER));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(44),dp(44));
        ap.leftMargin=dp(12);
        row.addView(action,ap);

        View.OnClickListener open=v->showConnectionSheet(item);
        row.setOnClickListener(open);
        action.setOnClickListener(open);

        View divider=new View(this);
        divider.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(-1,dp(1));
        vp.leftMargin=dp(66);
        list.addView(divider,vp);
    }

    private void showConnectionSheet(Item item){
        final Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet=new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(26),dp(12),dp(26),dp(28));
        sheet.setBackground(topSheet());

        View handle=new View(this);
        handle.setBackground(roundRect(0xff737373,999,0x00000000));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(52),dp(4));
        hp.gravity=Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin=dp(26);
        sheet.addView(handle,hp);

        LinearLayout hero=new LinearLayout(this);
        hero.setGravity(Gravity.CENTER);
        sheet.addView(hero,new LinearLayout.LayoutParams(-1,-2));

        FrameLayout iconBox=new FrameLayout(this);
        iconBox.setBackground(roundRect(SURFACE,16,BORDER));
        iconBox.setElevation(dp(2));
        hero.addView(iconBox,new LinearLayout.LayoutParams(dp(64),dp(64)));
        ImageView icon=new ImageView(this);
        icon.setImageResource(item.icon);icon.setColorFilter(INK);
        iconBox.addView(icon,new FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));

        TextView dots=new TextView(this);
        dots.setText("•••");
        dots.setTextColor(0xffc4c4c4);
        dots.setTextSize(22);
        dots.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotsP=new LinearLayout.LayoutParams(dp(52),dp(64));
        hero.addView(dots,dotsP);

        FrameLayout agentBox=new FrameLayout(this);
        agentBox.setBackground(roundRect(SURFACE,16,BORDER));
        agentBox.setElevation(dp(2));
        hero.addView(agentBox,new LinearLayout.LayoutParams(dp(64),dp(64)));
        ImageView agent=new ImageView(this);
        agent.setImageResource(R.drawable.ic_ocean_mark);agent.setColorFilter(INK);
        agentBox.addView(agent,new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.CENTER));

        boolean on=connected(item);
        TextView title=new TextView(this);
        title.setText((on?"Manage ":"Connect ")+item.name);
        title.setTextColor(INK);
        title.setTextSize(27);
        title.setTypeface(null,Typeface.NORMAL);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams titleP=new LinearLayout.LayoutParams(-1,-2);
        titleP.topMargin=dp(28);
        sheet.addView(title,titleP);

        TextView intro=new TextView(this);
        intro.setText(item.description);
        intro.setTextColor(MUTED);
        intro.setTextSize(14);
        intro.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams introP=new LinearLayout.LayoutParams(-1,-2);
        introP.topMargin=dp(10);
        introP.bottomMargin=dp(22);
        sheet.addView(intro,introP);

        LinearLayout info=new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(18),dp(8),dp(18),dp(8));
        info.setBackground(roundRect(SURFACE,18,BORDER));
        sheet.addView(info,new LinearLayout.LayoutParams(-1,-2));

        addInfo(info,"Agent access","When connected, Ocean Agent may use this capability only when a task calls for it.");
        addInfo(info,"Permission boundary",permissionText(item));
        addInfo(info,"You're in control","Disconnecting blocks future agent use without uninstalling packages or deleting your files.");

        TextView primary=new TextView(this);
        primary.setGravity(Gravity.CENTER);
        primary.setTextSize(16);
        primary.setTypeface(null,Typeface.BOLD);
        primary.setTextColor(Color.WHITE);
        primary.setText(item.available?(on?"Disconnect "+item.name:"Connect "+item.name):"Required capability is not installed");
        primary.setEnabled(item.available);
        primary.setAlpha(item.available?1f:0.42f);
        primary.setBackground(roundRect(0xff111111,999,0x00000000));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(58));
        pp.topMargin=dp(24);
        sheet.addView(primary,pp);

        primary.setOnClickListener(v->{
            if(!item.available)return;
            prefs.edit().putBoolean("connected_"+item.id,!on).apply();
            dialog.dismiss();
            render(search==null?"":search.getText().toString());
            Toast.makeText(this,!on?item.name+" connected to Ocean Agent":item.name+" disconnected",Toast.LENGTH_SHORT).show();
        });

        TextView cancel=new TextView(this);
        cancel.setText("Not now");
        cancel.setTextColor(MUTED);
        cancel.setTextSize(14);
        cancel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(44));
        cp.topMargin=dp(8);
        sheet.addView(cancel,cp);
        cancel.setOnClickListener(v->dialog.dismiss());

        dialog.setContentView(sheet);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp=w.getAttributes();
            lp.dimAmount=0.34f;
            w.setAttributes(lp);
        }
    }

    private void addInfo(LinearLayout root,String heading,String body){
        LinearLayout block=new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0,dp(12),0,dp(12));
        root.addView(block,new LinearLayout.LayoutParams(-1,-2));

        TextView h=new TextView(this);
        h.setText(heading);
        h.setTextColor(INK);
        h.setTextSize(14);
        h.setTypeface(null,Typeface.BOLD);
        block.addView(h);

        TextView b=new TextView(this);
        b.setText(body);
        b.setTextColor(MUTED);
        b.setTextSize(13);
        b.setLineSpacing(0,1.08f);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.topMargin=dp(3);
        block.addView(b,bp);
    }

    private String permissionText(Item item){
        if("device".equals(item.id))
            return "Connecting does not grant Accessibility, screenshot, camera or MediaProjection permission. Android still asks you separately when a feature needs it.";
        if("runtime".equals(item.id))
            return "The agent can inspect Ocean-local listeners and pages only through the existing Runtime Ports controls; connection alone does not create a server.";
        return "Connecting grants no new Android permission. The agent can only use the command/runtime that is already installed inside Ocean.";
    }

    private GradientDrawable roundRect(int fill,int radius,int stroke){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if((stroke>>>24)!=0)d.setStroke(dp(1),stroke);
        return d;
    }

    private GradientDrawable topSheet(){
        GradientDrawable d=new GradientDrawable();
        d.setColor(SURFACE);
        float r=dp(28);
        d.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});
        return d;
    }

    private boolean exists(String relative){
        java.io.File direct=new java.io.File(getFilesDir(),relative);
        if(direct.exists())return true;
        String prefix="usr/bin/";
        if(relative.startsWith(prefix)){
            java.io.File overlay=new java.io.File(new java.io.File(getFilesDir(),"forge-tools/bin"),relative.substring(prefix.length()));
            return overlay.exists();
        }
        return false;
    }
}
