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
import android.widget.ScrollView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.mcp.McpClientManager;
import studio.ocean.app.mcp.McpServerConfig;
import studio.ocean.app.mcp.McpStatus;
import studio.ocean.app.mcp.McpTransportType;

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
    private OceanAgentHubStore hub;
    private LinearLayout list;
    private LinearLayout skillsList;
    private LinearLayout mcpsList;
    private ScrollView pluginsScroll;
    private ScrollView skillsScroll;
    private ScrollView mcpsScroll;
    private TextView hubTitle;
    private LinearLayout hubSegments;
    private String hubSection = "plugins";
    private String skillsFilter = "";
    private String skillsSortMode = "name";
    private String pluginStatusFilter = "all";
    private String skillStatusFilter = "all";
    private String mcpStatusFilter = "all";
    private String mcpsFilter = "";
    private ActivityResultLauncher<String> pluginImportPicker;
    private ActivityResultLauncher<String> skillImportPicker;
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
        hub=new OceanAgentHubStore(this);
        pluginImportPicker=registerForActivityResult(new ActivityResultContracts.GetContent(),uri->{
            if(uri==null)return;
            try( java.io.InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IllegalStateException("Could not read file");
                String name=uri.getLastPathSegment()==null?"":uri.getLastPathSegment().toLowerCase(java.util.Locale.ROOT);
                if(name.endsWith(".zip"))OceanPluginRegistrar.importFromZip(this,in);
                else{
                    String body=readAll(in);
                    if(name.endsWith(".plugin"))registerUploadedPluginManifest(body);
                    else OceanPluginRegistrar.importManifestJson(this,new JSONObject(body));
                }
                loadItems();render(search==null?"":search.getText().toString());
                Toast.makeText(this,"Plugin imported",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        skillImportPicker=registerForActivityResult(new ActivityResultContracts.GetContent(),uri->{
            if(uri==null)return;
            try( java.io.InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IllegalStateException("Could not read file");
                String body=readAll(in);
                hub.addSkill("Imported skill",body,"upload");
                renderHubSections();
                Toast.makeText(this,"Skill imported",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        list=findViewById(R.id.plugin_list);
        skillsList=findViewById(R.id.plugin_skills_list);
        mcpsList=findViewById(R.id.plugin_mcps_list);
        pluginsScroll=findViewById(R.id.plugin_plugins_scroll);
        skillsScroll=findViewById(R.id.plugin_skills_scroll);
        mcpsScroll=findViewById(R.id.plugin_mcps_scroll);
        hubTitle=findViewById(R.id.plugin_hub_title);
        hubSegments=findViewById(R.id.plugin_hub_segments);
        if(getIntent()!=null&&getIntent().hasExtra("hub_section"))hubSection=getIntent().getStringExtra("hub_section");
        buildHubSegments();
        installedStrip=findViewById(R.id.plugin_installed_strip);
        installedEmpty=findViewById(R.id.plugin_installed_empty);
        searchEmpty=findViewById(R.id.plugin_search_empty);
        search=findViewById(R.id.plugin_search);

        ImageButton back=findViewById(R.id.plugin_back);
        if(back!=null)back.setOnClickListener(v->finish());
        ImageButton add=findViewById(R.id.plugin_add);
        if(add!=null)add.setOnClickListener(v->showHubAddSheet());
        ImageButton filterBtn = findViewById(R.id.plugin_filter_btn);
        if (filterBtn != null) filterBtn.setOnClickListener(v -> showFilterBottomSheet());
        ImageButton sortBtn = findViewById(R.id.plugin_sort_btn);
        if (sortBtn != null) sortBtn.setOnClickListener(v -> showSortBottomSheet());

        try{OceanForgeInstaller.ensure(this);}catch(Exception ignored){}
        loadItems();
        render("");
        renderHubSections();
        showHubSection(hubSection);

        if(search!=null)search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                String q=s==null?"":s.toString();
                if("plugins".equals(hubSection))render(q);
                else if("skills".equals(hubSection)){skillsFilter=q;renderHubSections();}
                else{mcpsFilter=q;renderHubSections();}
            }
            @Override public void afterTextChanged(Editable s){}
        });
        if(getIntent()!=null&&getIntent().hasExtra("skill_id")){
            String skillId=getIntent().getStringExtra("skill_id");
            getIntent().removeExtra("skill_id");
            showHubSection("skills");
            uiHandlerOpenSkill(skillId);
        }
    }

    private void uiHandlerOpenSkill(String skillId){
        if(skillId==null||skillId.isEmpty())return;
        skillsList.post(()->openSkillDetailSheet(skillId));
    }

    private final android.os.Handler uiHandler=new android.os.Handler(android.os.Looper.getMainLooper());

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
            boolean isConnected = connected(item);
            if(isConnected){
                addInstalledTile(item);
                installed++;
            }
            if(!q.isEmpty()&&!item.name.toLowerCase(java.util.Locale.ROOT).contains(q)
                    &&!item.description.toLowerCase(java.util.Locale.ROOT).contains(q)
                    &&!item.id.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;

            boolean matchesStatus = "all".equals(pluginStatusFilter)
                    || ("connected".equals(pluginStatusFilter) && isConnected)
                    || ("available".equals(pluginStatusFilter) && item.available)
                    || ("unavailable".equals(pluginStatusFilter) && !item.available);
            if (!matchesStatus) continue;

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
        desc.setTextColor(MUTED);
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
        action.setTextColor(item.available?INK:MUTED);
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

    private void buildHubSegments(){
        if(hubSegments==null)return;
        hubSegments.removeAllViews();
        addSegment("plugins","Plugins");
        addSegment("skills","Skills");
        addSegment("mcps","MCPs");
    }

    private void addSegment(String id,String label){
        TextView tab=new TextView(this);
        tab.setText(label);
        tab.setGravity(Gravity.CENTER);
        tab.setTextSize(13);
        tab.setTag(id);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f);
        tab.setLayoutParams(lp);
        tab.setOnClickListener(v->showHubSection(id));
        hubSegments.addView(tab);
        styleSegment(tab,id.equals(hubSection));
    }

    private void styleSegment(TextView tab,boolean on){
        tab.setTextColor(on?INK:MUTED);
        tab.setTypeface(null,on?Typeface.BOLD:Typeface.NORMAL);
        tab.setBackground(on?roundRect(SURFACE,10,BORDER):null);
    }

    private void showHubSection(String section){
        hubSection=section;
        if(hubTitle!=null)hubTitle.setText(section.equals("skills")?"Skills":section.equals("mcps")?"MCPs":"Plugins");
        if(search!=null){
            String hint="plugins".equals(section)?"Search plugins":"skills".equals(section)?"Search skills":"Search MCPs";
            search.setHint(hint);
            if(!"plugins".equals(section))search.setText("");
        }
        if(hubSegments!=null)for(int i=0;i<hubSegments.getChildCount();i++){
            TextView t=(TextView)hubSegments.getChildAt(i);
            styleSegment(t,section.equals(t.getTag()));
        }
        if(pluginsScroll!=null)pluginsScroll.setVisibility("plugins".equals(section)?View.VISIBLE:View.GONE);
        if(skillsScroll!=null)skillsScroll.setVisibility("skills".equals(section)?View.VISIBLE:View.GONE);
        if(mcpsScroll!=null)mcpsScroll.setVisibility("mcps".equals(section)?View.VISIBLE:View.GONE);
        if("plugins".equals(section)) render(search != null ? search.getText().toString() : "");
        else renderHubSections();
    }

    private void renderHubSections(){
        if(skillsList==null||mcpsList==null)return;
        skillsList.removeAllViews();
        TextView intro=new TextView(this);
        intro.setText("Premium skills stored on-device. Connected skills inject sharp context into the agent. Agent-authored skills appear here automatically.");
        intro.setTextColor(MUTED);intro.setTextSize(13);intro.setPadding(0,0,0,dp(14));
        skillsList.addView(intro);
        String q=skillsFilter==null?"":skillsFilter.trim().toLowerCase(java.util.Locale.ROOT);
        JSONArray skills=hub.skillsSorted(skillsSortMode);
        int shown=0;
        for(int i=0;i<skills.length();i++){
            JSONObject s=skills.optJSONObject(i);if(s==null)continue;
            String title=s.optString("title");
            String desc=s.optString("description");
            String id=s.optString("id");
            if(!q.isEmpty()&&!title.toLowerCase(java.util.Locale.ROOT).contains(q)
                    &&!desc.toLowerCase(java.util.Locale.ROOT).contains(q)
                    &&!id.toLowerCase(java.util.Locale.ROOT).contains(q))continue;
            boolean connected="connected".equals(s.optString("status"));
            boolean draft="draft".equals(s.optString("status"));
            if("connected".equals(skillStatusFilter)&&!connected)continue;
            if("draft".equals(skillStatusFilter)&&!draft)continue;
            LinearLayout card=skillRow(s,connected,draft);
            card.setOnClickListener(v->openSkillDetailSheet(id));
            skillsList.addView(card);
            shown++;
        }
        if(shown==0){
            TextView empty=new TextView(this);
            empty.setText(q.isEmpty()?"No skills on device yet.":"No matching skills.");
            empty.setTextColor(MUTED);empty.setTextSize(13);
            skillsList.addView(empty);
        }
        mcpsList.removeAllViews();
        TextView mIntro=new TextView(this);
        mIntro.setText("MCP servers created or saved by you or the agent. Connect to perform real protocol handshake and discover capabilities.");
        mIntro.setTextColor(MUTED);mIntro.setTextSize(13);mIntro.setPadding(0,0,0,dp(14));
        mcpsList.addView(mIntro);
        List<McpServerConfig> mcps=McpClientManager.getInstance(this).listServers();
        if(mcps.isEmpty()){
            TextView empty=new TextView(this);
            empty.setText("No MCP servers yet. Tap '+ Add MCP' below to register a server.");
            empty.setTextColor(MUTED);empty.setTextSize(13);
            mcpsList.addView(empty);
        }
        String mq=mcpsFilter==null?"":mcpsFilter.trim().toLowerCase(java.util.Locale.ROOT);
        int mShown=0;
        for(McpServerConfig m : mcps){
            if(m==null)continue;
            String name=m.name!=null?m.name:"";
            String cmd=m.command!=null?m.command:"";
            String ep=m.endpointUrl!=null?m.endpointUrl:"";
            if(!mq.isEmpty()&&!name.toLowerCase(java.util.Locale.ROOT).contains(mq)
                    &&!cmd.toLowerCase(java.util.Locale.ROOT).contains(mq)
                    &&!ep.toLowerCase(java.util.Locale.ROOT).contains(mq))continue;

            boolean connected=m.isConnected();
            if("connected".equals(mcpStatusFilter)&&!connected)continue;
            if("saved".equals(mcpStatusFilter)&&(connected||m.status==McpStatus.START_ERROR||m.status==McpStatus.PROTOCOL_ERROR))continue;
            if("error".equals(mcpStatusFilter)&&m.status!=McpStatus.START_ERROR&&m.status!=McpStatus.PROTOCOL_ERROR)continue;

            LinearLayout card=mcpRow(m);
            card.setOnClickListener(v->openMcpDetailSheet(m));
            mcpsList.addView(card);
            mShown++;
        }
        if(mShown==0&&!mcps.isEmpty()){
            TextView empty=new TextView(this);
            empty.setText("connected".equals(mcpStatusFilter)?"No connected MCP servers.":"No matching MCP servers.");
            empty.setTextColor(MUTED);empty.setTextSize(13);
            mcpsList.addView(empty);
        }
        TextView addMcp=OceanUi.outlinedPill(this,"+ Add MCP");
        LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(-2,-2);
        addLp.topMargin=dp(18);
        addMcp.setLayoutParams(addLp);
        addMcp.setOnClickListener(v->showMcpAddSheet());
        mcpsList.addView(addMcp);
    }

    private LinearLayout skillRow(JSONObject s,boolean connected,boolean draft){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16),dp(14),dp(16),dp(14));
        row.setBackground(roundRect(connected?0xfff4f4f5:SURFACE,16,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(10);
        row.setLayoutParams(lp);
        FrameLayout iconBox=new FrameLayout(this);
        iconBox.setBackground(roundRect(SURFACE_MUTED,14,BORDER));
        row.addView(iconBox,new LinearLayout.LayoutParams(dp(44),dp(44)));
        ImageView icon=new ImageView(this);
        int iconRes = OceanIconRegistry.getSkillIcon(s.optString("id"), s.optString("title"), s.optString("description"));
        icon.setImageResource(iconRes);
        icon.setColorFilter(INK);
        iconBox.addView(icon,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1f);
        cp.leftMargin=dp(12);
        row.addView(copy,cp);
        TextView t=new TextView(this);
        t.setText(s.optString("title")+(draft?" · Draft":""));
        t.setTextColor(INK);t.setTextSize(15);t.setTypeface(null,Typeface.BOLD);
        TextView d=new TextView(this);d.setText(s.optString("description"));d.setTextColor(MUTED);d.setTextSize(12);d.setMaxLines(2);
        copy.addView(t);copy.addView(d);
        TextView pill=new TextView(this);
        pill.setText(connected?"✓":draft?"Edit":"Connect");
        pill.setTextColor(connected?INK:0xFF52525B);
        pill.setTextSize(12);pill.setTypeface(null,Typeface.BOLD);
        pill.setPadding(dp(12),dp(8),dp(12),dp(8));
        pill.setBackground(roundRect(SURFACE_MUTED,999,BORDER));
        row.addView(pill);
        return row;
    }

    private LinearLayout mcpRow(McpServerConfig m){
        int iconRes = OceanIconRegistry.getSkillIcon(m.id, m.name, m.command);
        String subtitle = m.transport.label + " · " + m.getRedactedEndpointOrCommand();
        if (m.tools != null && m.tools.length() > 0) {
            subtitle += " · " + m.tools.length() + " tool" + (m.tools.length() > 1 ? "s" : "");
        }
        return hubCard(m.name, subtitle, m.status.label, m.isConnected(), iconRes);
    }

    private void openSkillDetailSheet(String id){
        JSONArray skills=hub.skills();
        JSONObject skill=null;
        for(int i=0;i<skills.length();i++){
            JSONObject s=skills.optJSONObject(i);
            if(s!=null&&id.equals(s.optString("id"))){skill=s;break;}
        }
        if(skill==null)return;
        final boolean connected="connected".equals(skill.optString("status"));
        final String skillId=id;
        showSkillEditorSheet(id,skill,connected);
    }

    private void showSkillEditorSheet(String skillId,JSONObject skill,boolean connected){
        String id=skillId;
        String fullMarkdown=hub.readSkillMarkdown(id);
        String fullBody=hub.skillBodyWithoutFrontMatter(fullMarkdown);
        boolean draft="draft".equals(skill.optString("status"));

        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(26),dp(12),dp(26),dp(28));
        sheet.setBackground(topSheet());
        View handle=new View(this);
        handle.setBackground(roundRect(0xff737373,999,0x00000000));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(52),dp(4));
        hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(20);
        sheet.addView(handle,hp);
        TextView desc=new TextView(this);
        desc.setText(skill.optString("description"));
        desc.setTextColor(MUTED);desc.setTextSize(13);
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,-2);dpv.topMargin=dp(6);dpv.bottomMargin=dp(12);
        sheet.addView(desc,dpv);
        EditText titleEdit=new EditText(this);
        titleEdit.setText(skill.optString("title"));
        titleEdit.setHint("Skill title");
        titleEdit.setBackgroundResource(R.drawable.auth_field_background);
        titleEdit.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.topMargin=dp(10);
        sheet.addView(titleEdit,titleLp);
        EditText body=new EditText(this);
        body.setText(fullBody);
        body.setHint("SKILL.md body (markdown)");
        body.setMinLines(12);
        body.setGravity(Gravity.TOP|Gravity.START);
        body.setBackgroundResource(R.drawable.auth_field_background);
        body.setPadding(dp(12),dp(10),dp(12),dp(10));
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(12);
        LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,dp(280));
        scrollLp.topMargin=dp(10);scrollLp.bottomMargin=dp(12);
        sheet.addView(body,scrollLp);
        TextView saveDraft=new TextView(this);
        saveDraft.setGravity(Gravity.CENTER);
        saveDraft.setText("Save draft");
        saveDraft.setTextColor(INK);
        saveDraft.setTypeface(null,Typeface.BOLD);
        saveDraft.setPadding(0,dp(14),0,dp(14));
        saveDraft.setBackground(roundRect(SURFACE_MUTED,999,BORDER));
        saveDraft.setOnClickListener(v->{
            try{
                hub.updateSkillMarkdown(id,titleEdit.getText().toString().trim(),skill.optString("description"),body.getText().toString(),true);
                dialog.dismiss();renderHubSections();
                Toast.makeText(this,"Draft saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        sheet.addView(saveDraft,new LinearLayout.LayoutParams(-1,-2));
        TextView publish=new TextView(this);
        publish.setGravity(Gravity.CENTER);
        publish.setText("Save & publish SKILL.md");
        publish.setTextColor(Color.WHITE);
        publish.setTypeface(null,Typeface.BOLD);
        publish.setBackground(roundRect(0xff111111,999,0x00000000));
        publish.setPadding(0,dp(16),0,dp(16));
        LinearLayout.LayoutParams pubLp=new LinearLayout.LayoutParams(-1,-2);
        pubLp.topMargin=dp(8);
        publish.setOnClickListener(v->{
            try{
                hub.updateSkillMarkdown(id,titleEdit.getText().toString().trim(),skill.optString("description"),body.getText().toString(),false);
                dialog.dismiss();renderHubSections();
                Toast.makeText(this,"Skill saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        sheet.addView(publish,pubLp);
        TextView toggle=new TextView(this);
        toggle.setGravity(Gravity.CENTER);
        toggle.setTextSize(15);
        toggle.setTypeface(null,Typeface.BOLD);
        toggle.setTextColor(Color.WHITE);
        toggle.setText(connected?"Disconnect skill":"Connect skill");
        toggle.setBackground(roundRect(0xff111111,999,0x00000000));
        toggle.setPadding(0,dp(16),0,dp(16));
        LinearLayout.LayoutParams toggleLp=new LinearLayout.LayoutParams(-1,-2);
        toggleLp.topMargin=dp(10);
        toggle.setOnClickListener(v->{
            hub.setSkillConnected(skillId,!connected);
            dialog.dismiss();
            renderHubSections();
        });
        sheet.addView(toggle,toggleLp);
        dialog.setContentView(sheet);
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
    }

    private void showHubAddSheet(){
        if("skills".equals(hubSection)){showSkillsAddSheet();return;}
        if("mcps".equals(hubSection)){showMcpAddSheet();return;}
        showPluginAddSheet();
    }

    private void showSkillsAddSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=hubBottomSheet("Add skill");
        addSheetAction(sheet,"Upload skill",R.drawable.ic_files,()->{dialog.dismiss();skillImportPicker.launch("*/*");});
        addSheetAction(sheet,"Create skill",R.drawable.ic_add,()->{dialog.dismiss();showCreateSkillSheet();});
        addSheetAction(sheet,"Search skill marketplace",R.drawable.ic_search,()->{
            dialog.dismiss();
            Toast.makeText(this,"Marketplace search opens when catalog is configured",Toast.LENGTH_SHORT).show();
        });
        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private void showPluginAddSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=hubBottomSheet("Add plugin");
        addSheetAction(sheet,"Create plugin manually",R.drawable.ic_add,()->{dialog.dismiss();showCreatePluginSheet();});
        addSheetAction(sheet,"Upload plugin file",R.drawable.ic_files,()->{dialog.dismiss();pluginImportPicker.launch("*/*");});
        addSheetAction(sheet,"Add from GitHub repo",R.drawable.ic_github,()->{dialog.dismiss();showGithubPluginSheet();});
        addSheetAction(sheet,"Import marketplace / local path",R.drawable.ic_tools,()->{dialog.dismiss();showCatalogPluginSheet();});
        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private LinearLayout hubBottomSheet(String heading){
        LinearLayout sheet=new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(26),dp(12),dp(26),dp(28));
        sheet.setBackground(topSheet());
        View handle=new View(this);
        handle.setBackground(roundRect(0xff737373,999,0x00000000));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(52),dp(4));
        hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(20);
        sheet.addView(handle,hp);
        TextView title=new TextView(this);
        title.setText(heading);
        title.setTextColor(INK);title.setTextSize(20);title.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.bottomMargin=dp(8);
        sheet.addView(title,titleLp);
        return sheet;
    }

    private void addSheetAction(LinearLayout sheet,String label,int iconRes,Runnable action){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0,dp(18),0,dp(18));
        ImageView icon=new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(INK);
        row.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView text=new TextView(this);
        text.setText(label);
        text.setTextColor(INK);
        text.setTextSize(16);
        text.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,-2);
        tp.leftMargin=dp(16);
        row.addView(text,tp);
        row.setOnClickListener(v->action.run());
        sheet.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }

    private void presentBottomSheet(Dialog dialog){
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
    }

    private void showSortBottomSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=hubBottomSheet("Sort capabilities");
        addSheetAction(sheet,"Name (A to Z)"+("name".equals(skillsSortMode)?"  ✓":""),R.drawable.ic_sort,()->{
            skillsSortMode="name";
            renderHubSections();
            dialog.dismiss();
        });
        addSheetAction(sheet,"Recently updated"+("recent".equals(skillsSortMode)?"  ✓":""),R.drawable.ic_sort,()->{
            skillsSortMode="recent";
            renderHubSections();
            dialog.dismiss();
        });
        addSheetAction(sheet,"Connected first"+("connected".equals(skillsSortMode)?"  ✓":""),R.drawable.ic_sort,()->{
            skillsSortMode="connected";
            renderHubSections();
            dialog.dismiss();
        });
        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private void showFilterBottomSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if ("plugins".equals(hubSection)) {
            LinearLayout sheet=hubBottomSheet("Filter plugins");
            addSheetAction(sheet,"All plugins"+("all".equals(pluginStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                pluginStatusFilter="all";
                render(search!=null?search.getText().toString():"");
                dialog.dismiss();
            });
            addSheetAction(sheet,"Connected only"+("connected".equals(pluginStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                pluginStatusFilter="connected";
                render(search!=null?search.getText().toString():"");
                dialog.dismiss();
            });
            addSheetAction(sheet,"Available only"+("available".equals(pluginStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                pluginStatusFilter="available";
                render(search!=null?search.getText().toString():"");
                dialog.dismiss();
            });
            addSheetAction(sheet,"Unavailable only"+("unavailable".equals(pluginStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                pluginStatusFilter="unavailable";
                render(search!=null?search.getText().toString():"");
                dialog.dismiss();
            });
            dialog.setContentView(sheet);
        } else if ("skills".equals(hubSection)) {
            LinearLayout sheet=hubBottomSheet("Filter skills");
            addSheetAction(sheet,"All skills"+("all".equals(skillStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                skillStatusFilter="all";
                renderHubSections();
                dialog.dismiss();
            });
            addSheetAction(sheet,"Connected only"+("connected".equals(skillStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                skillStatusFilter="connected";
                renderHubSections();
                dialog.dismiss();
            });
            addSheetAction(sheet,"Drafts only"+("draft".equals(skillStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                skillStatusFilter="draft";
                renderHubSections();
                dialog.dismiss();
            });
            dialog.setContentView(sheet);
        } else {
            LinearLayout sheet=hubBottomSheet("Filter MCP servers");
            addSheetAction(sheet,"All servers"+("all".equals(mcpStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                mcpStatusFilter="all";
                renderHubSections();
                dialog.dismiss();
            });
            addSheetAction(sheet,"Connected only"+("connected".equals(mcpStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                mcpStatusFilter="connected";
                renderHubSections();
                dialog.dismiss();
            });
            addSheetAction(sheet,"Saved / Not verified"+("saved".equals(mcpStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                mcpStatusFilter="saved";
                renderHubSections();
                dialog.dismiss();
            });
            addSheetAction(sheet,"Error only"+("error".equals(mcpStatusFilter)?"  ✓":""),R.drawable.ic_filter,()->{
                mcpStatusFilter="error";
                renderHubSections();
                dialog.dismiss();
            });
            dialog.setContentView(sheet);
        }
        presentBottomSheet(dialog);
    }

    private void showCreateSkillSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(26),dp(12),dp(26),dp(28));
        sheet.setBackground(topSheet());
        TextView title=new TextView(this);
        title.setText("Create skill");
        title.setTextColor(INK);title.setTextSize(20);title.setTypeface(null,Typeface.BOLD);
        sheet.addView(title);
        EditText name=new EditText(this);name.setHint("Skill title");
        name.setBackgroundResource(R.drawable.auth_field_background);
        name.setPadding(dp(12),dp(10),dp(12),dp(10));
        EditText body=new EditText(this);body.setHint("SKILL.md body (markdown)");
        body.setMinLines(10);
        body.setGravity(Gravity.TOP|Gravity.START);
        body.setBackgroundResource(R.drawable.auth_field_background);
        body.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,-2);
        flp.topMargin=dp(14);
        sheet.addView(name,flp);
        flp.topMargin=dp(10);
        sheet.addView(body,flp);
        TextView save=new TextView(this);
        save.setGravity(Gravity.CENTER);
        save.setText("Save SKILL.md");
        save.setTextColor(Color.WHITE);
        save.setTypeface(null,Typeface.BOLD);
        save.setBackground(roundRect(0xff111111,999,0x00000000));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(52));
        slp.topMargin=dp(20);
        sheet.addView(save,slp);
        save.setOnClickListener(v->{
            try{
                hub.addSkill(name.getText().toString().trim(),body.getText().toString().trim(),"manual");
                dialog.dismiss();
                showHubSection("skills");
                Toast.makeText(this,"Skill saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        TextView draft=new TextView(this);
        draft.setGravity(Gravity.CENTER);
        draft.setText("Save as draft");
        draft.setTextColor(INK);
        draft.setTypeface(null,Typeface.BOLD);
        draft.setPadding(0,dp(14),0,dp(14));
        draft.setBackground(roundRect(SURFACE_MUTED,999,BORDER));
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(48));
        dlp.topMargin=dp(10);
        draft.setOnClickListener(v->{
            try{
                hub.addSkillDraft(name.getText().toString().trim(),body.getText().toString().trim(),"manual");
                dialog.dismiss();
                showHubSection("skills");
                Toast.makeText(this,"Draft saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        sheet.addView(draft,dlp);
        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private void showCreatePluginSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=hubBottomSheet("Create plugin");
        EditText id=new EditText(this);id.setHint("Plugin id");
        EditText name=new EditText(this);name.setHint("Display name");
        EditText command=new EditText(this);command.setHint("Command (existing binary) or leave empty to scaffold");
        EditText description=new EditText(this);description.setHint("Description");
        for(EditText field:new EditText[]{id,name,command,description}){
            field.setBackgroundResource(R.drawable.auth_field_background);
            field.setPadding(dp(12),dp(10),dp(12),dp(10));
            LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,-2);
            flp.topMargin=dp(10);
            sheet.addView(field,flp);
        }
        TextView save=new TextView(this);
        save.setGravity(Gravity.CENTER);
        save.setText("Save plugin");
        save.setTextColor(Color.WHITE);
        save.setTypeface(null,Typeface.BOLD);
        save.setBackground(roundRect(0xff111111,999,0x00000000));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(52));
        slp.topMargin=dp(20);
        sheet.addView(save,slp);
        save.setOnClickListener(v->{
            try{
                String cmd=command.getText().toString().trim();
                if(cmd.isEmpty())OceanPluginRegistrar.scaffoldAndRegister(this,id.getText().toString().trim(),name.getText().toString().trim(),"bash",description.getText().toString().trim());
                else OceanPluginRegistrar.registerManifest(this,id.getText().toString().trim(),name.getText().toString().trim(),cmd,description.getText().toString().trim());
                dialog.dismiss();
                loadItems();render(search==null?"":search.getText().toString());
                Toast.makeText(this,"Plugin saved",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private void showGithubPluginSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=hubBottomSheet("GitHub plugin");
        EditText url=new EditText(this);
        url.setHint("https://github.com/owner/repo");
        url.setBackgroundResource(R.drawable.auth_field_background);
        url.setPadding(dp(12),dp(10),dp(12),dp(10));
        sheet.addView(url,new LinearLayout.LayoutParams(-1,-2));
        TextView save=new TextView(this);
        save.setGravity(Gravity.CENTER);
        save.setText("Import manifest");
        save.setTextColor(Color.WHITE);
        save.setTypeface(null,Typeface.BOLD);
        save.setBackground(roundRect(0xff111111,999,0x00000000));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(52));
        slp.topMargin=dp(20);
        sheet.addView(save,slp);
        save.setOnClickListener(v->{
            try{
                OceanPluginRegistrar.importFromGithubRepo(this,url.getText().toString().trim());
                dialog.dismiss();
                loadItems();render(search==null?"":search.getText().toString());
                Toast.makeText(this,"Plugins imported from GitHub",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private void showCatalogPluginSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=hubBottomSheet("Marketplace / path");
        EditText url=new EditText(this);
        url.setHint("https://catalog.example/plugins.json or /sdcard/path.json");
        url.setBackgroundResource(R.drawable.auth_field_background);
        url.setPadding(dp(12),dp(10),dp(12),dp(10));
        sheet.addView(url,new LinearLayout.LayoutParams(-1,-2));
        TextView save=new TextView(this);
        save.setGravity(Gravity.CENTER);
        save.setText("Import");
        save.setTextColor(Color.WHITE);
        save.setTypeface(null,Typeface.BOLD);
        save.setBackground(roundRect(0xff111111,999,0x00000000));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(52));
        slp.topMargin=dp(20);
        sheet.addView(save,slp);
        save.setOnClickListener(v->{
            try{
                OceanPluginRegistrar.importFromCatalogUrl(this,url.getText().toString().trim());
                dialog.dismiss();
                loadItems();render(search==null?"":search.getText().toString());
                Toast.makeText(this,"Catalog imported",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private void registerUploadedPluginManifest(String body)throws Exception{
        java.util.Properties p=new java.util.Properties();
        p.load(new java.io.StringReader(body));
        OceanPluginRegistrar.registerManifest(this,
                p.getProperty("id","plugin"),
                p.getProperty("name","Plugin"),
                p.getProperty("command",""),
                p.getProperty("description","Uploaded plugin."));
    }

    private static String readAll(java.io.InputStream in)throws Exception{
        StringBuilder sb=new StringBuilder();
        byte[] buf=new byte[8192];
        int n;
        while((n=in.read(buf))>0)sb.append(new String(buf,0,n,java.nio.charset.StandardCharsets.UTF_8));
        return sb.toString();
    }

    private void showMcpAddSheet(){
        Dialog dialog=new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet=new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(26),dp(12),dp(26),dp(28));
        sheet.setBackground(topSheet());

        View handle=new View(this);
        handle.setBackground(roundRect(0xff737373,999,0x00000000));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(dp(52),dp(4));
        hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=dp(16);
        sheet.addView(handle,hp);

        TextView title=new TextView(this);
        title.setText("Add MCP server");
        title.setTextColor(INK);title.setTextSize(20);title.setTypeface(null,Typeface.BOLD);
        sheet.addView(title);

        TextView subtitle=new TextView(this);
        subtitle.setText("Model Context Protocol server. Registered with status Saved; Connect to verify capabilities.");
        subtitle.setTextColor(MUTED);subtitle.setTextSize(13);
        LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-1,-2);
        subLp.topMargin=dp(4);subLp.bottomMargin=dp(14);
        sheet.addView(subtitle,subLp);

        // Transport selector buttons
        final String[] selectedTransport = {"stdio"};
        LinearLayout transportBar = new LinearLayout(this);
        transportBar.setOrientation(LinearLayout.HORIZONTAL);
        TextView stdioBtn = new TextView(this);
        stdioBtn.setText("STDIO (Local)");
        stdioBtn.setGravity(Gravity.CENTER);
        stdioBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        stdioBtn.setTextColor(INK);stdioBtn.setTypeface(null, Typeface.BOLD);
        stdioBtn.setBackground(roundRect(0xffe4e4e7, 8, BORDER));
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, -2, 1f);
        tLp.rightMargin = dp(4);
        transportBar.addView(stdioBtn, tLp);

        TextView httpBtn = new TextView(this);
        httpBtn.setText("HTTP (Remote)");
        httpBtn.setGravity(Gravity.CENTER);
        httpBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        httpBtn.setTextColor(MUTED);
        httpBtn.setBackground(roundRect(SURFACE, 8, BORDER));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(0, -2, 1f);
        hLp.rightMargin = dp(4);
        transportBar.addView(httpBtn, hLp);

        TextView jsonBtn = new TextView(this);
        jsonBtn.setText("Import JSON");
        jsonBtn.setGravity(Gravity.CENTER);
        jsonBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        jsonBtn.setTextColor(MUTED);
        jsonBtn.setBackground(roundRect(SURFACE, 8, BORDER));
        transportBar.addView(jsonBtn, new LinearLayout.LayoutParams(0, -2, 1f));
        sheet.addView(transportBar);

        EditText name=new EditText(this);name.setHint("Server name (e.g. SQLite / Cloudflare MCP)");
        name.setBackgroundResource(R.drawable.auth_field_background);
        name.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams nLp=new LinearLayout.LayoutParams(-1,-2);
        nLp.topMargin=dp(14);
        sheet.addView(name,nLp);

        EditText targetInput=new EditText(this);
        targetInput.setHint("Executable command (e.g. ocean-tool or python3 server.py)");
        targetInput.setBackgroundResource(R.drawable.auth_field_background);
        targetInput.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams cLp=new LinearLayout.LayoutParams(-1,-2);
        cLp.topMargin=dp(10);
        sheet.addView(targetInput,cLp);

        EditText jsonInput=new EditText(this);
        jsonInput.setHint("Paste Claude/Cursor, VS Code, or Antigravity MCP config JSON");
        jsonInput.setMinLines(5);
        jsonInput.setGravity(Gravity.TOP|Gravity.START);
        jsonInput.setBackgroundResource(R.drawable.auth_field_background);
        jsonInput.setPadding(dp(12),dp(10),dp(12),dp(10));
        jsonInput.setTypeface(Typeface.MONOSPACE);
        jsonInput.setTextSize(12);
        LinearLayout.LayoutParams jLp=new LinearLayout.LayoutParams(-1,-2);
        jLp.topMargin=dp(10);
        jsonInput.setVisibility(View.GONE);
        sheet.addView(jsonInput,jLp);

        stdioBtn.setOnClickListener(v -> {
            selectedTransport[0] = "stdio";
            stdioBtn.setTextColor(INK);stdioBtn.setTypeface(null, Typeface.BOLD);
            stdioBtn.setBackground(roundRect(0xffe4e4e7, 8, BORDER));
            httpBtn.setTextColor(MUTED);httpBtn.setTypeface(null, Typeface.NORMAL);
            httpBtn.setBackground(roundRect(SURFACE, 8, BORDER));
            jsonBtn.setTextColor(MUTED);jsonBtn.setTypeface(null, Typeface.NORMAL);
            jsonBtn.setBackground(roundRect(SURFACE, 8, BORDER));
            name.setVisibility(View.VISIBLE);
            targetInput.setVisibility(View.VISIBLE);
            jsonInput.setVisibility(View.GONE);
            targetInput.setHint("Executable command (e.g. ocean-tool or python3 server.py)");
        });

        httpBtn.setOnClickListener(v -> {
            selectedTransport[0] = "http";
            httpBtn.setTextColor(INK);httpBtn.setTypeface(null, Typeface.BOLD);
            httpBtn.setBackground(roundRect(0xffe4e4e7, 8, BORDER));
            stdioBtn.setTextColor(MUTED);stdioBtn.setTypeface(null, Typeface.NORMAL);
            stdioBtn.setBackground(roundRect(SURFACE, 8, BORDER));
            jsonBtn.setTextColor(MUTED);jsonBtn.setTypeface(null, Typeface.NORMAL);
            jsonBtn.setBackground(roundRect(SURFACE, 8, BORDER));
            name.setVisibility(View.VISIBLE);
            targetInput.setVisibility(View.VISIBLE);
            jsonInput.setVisibility(View.GONE);
            targetInput.setHint("Endpoint URL (e.g. https://mcp.cloudflare.com/mcp)");
        });

        jsonBtn.setOnClickListener(v -> {
            selectedTransport[0] = "json";
            jsonBtn.setTextColor(INK);jsonBtn.setTypeface(null, Typeface.BOLD);
            jsonBtn.setBackground(roundRect(0xffe4e4e7, 8, BORDER));
            stdioBtn.setTextColor(MUTED);stdioBtn.setTypeface(null, Typeface.NORMAL);
            stdioBtn.setBackground(roundRect(SURFACE, 8, BORDER));
            httpBtn.setTextColor(MUTED);httpBtn.setTypeface(null, Typeface.NORMAL);
            httpBtn.setBackground(roundRect(SURFACE, 8, BORDER));
            name.setVisibility(View.GONE);
            targetInput.setVisibility(View.GONE);
            jsonInput.setVisibility(View.VISIBLE);
        });

        TextView save=new TextView(this);
        save.setGravity(Gravity.CENTER);
        save.setText("Save MCP (Unverified)");
        save.setTextColor(Color.WHITE);
        save.setTypeface(null,Typeface.BOLD);
        save.setBackground(roundRect(0xff111111,999,0x00000000));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,dp(50));
        slp.topMargin=dp(20);
        sheet.addView(save,slp);

        save.setOnClickListener(v->{
            if ("json".equals(selectedTransport[0])) {
                String rawJson = jsonInput.getText().toString().trim();
                if (rawJson.isEmpty()) {
                    Toast.makeText(this, "Please paste valid JSON configuration", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    List<McpServerConfig> parsed = McpServerConfig.parseConfigDialects(rawJson);
                    if (parsed.isEmpty()) {
                        Toast.makeText(this, "No valid MCP servers recognized in JSON", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    for (McpServerConfig p : parsed) {
                        p.status = McpStatus.SAVED;
                        McpClientManager.getInstance(this).updateServerInStore(p);
                    }
                    dialog.dismiss();
                    renderHubSections();
                    Toast.makeText(this, "Imported " + parsed.size() + " MCP server(s). Tap card to verify.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "JSON parse error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                return;
            }

            String sName = name.getText().toString().trim();
            String sTarget = targetInput.getText().toString().trim();
            if (sName.isEmpty()) {
                Toast.makeText(this, "Please enter a server name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (sTarget.isEmpty()) {
                Toast.makeText(this, "Please enter a command or endpoint URL", Toast.LENGTH_SHORT).show();
                return;
            }
            try{
                McpServerConfig cfg = new McpServerConfig(sName, McpTransportType.fromString(selectedTransport[0]));
                if ("stdio".equals(selectedTransport[0])) {
                    cfg.command = sTarget;
                } else {
                    cfg.endpointUrl = sTarget;
                }
                cfg.status = McpStatus.SAVED;
                McpClientManager.getInstance(this).updateServerInStore(cfg);
                dialog.dismiss();
                renderHubSections();
                Toast.makeText(this, "MCP saved. Tap server card to verify connection.", Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_SHORT).show();}
        });
        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private void openMcpDetailSheet(McpServerConfig server) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(26), dp(12), dp(26), dp(28));
        sheet.setBackground(topSheet());

        View handle = new View(this);
        handle.setBackground(roundRect(0xff737373, 999, 0x00000000));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(52), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL; hp.bottomMargin = dp(16);
        sheet.addView(handle, hp);

        TextView title = new TextView(this);
        title.setText(server.name);
        title.setTextColor(INK); title.setTextSize(20); title.setTypeface(null, Typeface.BOLD);
        sheet.addView(title);

        TextView sub = new TextView(this);
        sub.setText(server.transport.label + " · Status: " + server.status.label);
        sub.setTextColor(server.isConnected() ? INK : (server.status == McpStatus.START_ERROR || server.status == McpStatus.PROTOCOL_ERROR ? 0xffdc2626 : MUTED));
        sub.setTextSize(13);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(4); subLp.bottomMargin = dp(14);
        sheet.addView(sub, subLp);

        // Technical details container
        LinearLayout infoBox = new LinearLayout(this);
        infoBox.setOrientation(LinearLayout.VERTICAL);
        infoBox.setBackground(roundRect(SURFACE_MUTED, 12, BORDER));
        infoBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        sheet.addView(infoBox);

        addInfoRow(infoBox, "Target", server.getRedactedEndpointOrCommand());
        addInfoRow(infoBox, "Protocol Version", server.protocolVersion != null ? server.protocolVersion : "Not negotiated");
        addInfoRow(infoBox, "Server Version", server.serverVersion != null ? server.serverVersion : "N/A");
        addInfoRow(infoBox, "Latency", server.latencyMs > 0 ? server.latencyMs + " ms" : "N/A");
        addInfoRow(infoBox, "Discovered Tools", String.valueOf(server.tools != null ? server.tools.length() : 0));

        // Discovered tools list
        if (server.tools != null && server.tools.length() > 0) {
            TextView toolsTitle = new TextView(this);
            toolsTitle.setText("Advertised Tools:");
            toolsTitle.setTextColor(INK); toolsTitle.setTextSize(14); toolsTitle.setTypeface(null, Typeface.BOLD);
            LinearLayout.LayoutParams ttLp = new LinearLayout.LayoutParams(-1, -2);
            ttLp.topMargin = dp(14); ttLp.bottomMargin = dp(6);
            sheet.addView(toolsTitle, ttLp);

            for (int i = 0; i < Math.min(server.tools.length(), 6); i++) {
                JSONObject t = server.tools.optJSONObject(i);
                if (t == null) continue;
                TextView toolItem = new TextView(this);
                toolItem.setText("• " + t.optString("name") + " — " + t.optString("description", "No description"));
                toolItem.setTextColor(MUTED); toolItem.setTextSize(12);
                toolItem.setPadding(0, dp(2), 0, dp(2));
                sheet.addView(toolItem);
            }
            if (server.tools.length() > 6) {
                TextView more = new TextView(this);
                more.setText("+ " + (server.tools.length() - 6) + " more tools");
                more.setTextColor(MUTED); more.setTextSize(12);
                sheet.addView(more);
            }
        }

        // Error message if present
        if (server.lastError != null && !server.lastError.isEmpty()) {
            TextView errView = new TextView(this);
            errView.setText("Last Error: " + server.lastError);
            errView.setTextColor(0xffdc2626); errView.setTextSize(12);
            errView.setPadding(dp(12), dp(8), dp(12), dp(8));
            errView.setBackground(roundRect(0xfffef2f2, 8, 0xfffca5a5));
            LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(-1, -2);
            epLp.topMargin = dp(12);
            sheet.addView(errView, epLp);
        }

        // Action Buttons Row
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(-1, dp(48));
        aLp.topMargin = dp(20);
        sheet.addView(actionsRow, aLp);

        TextView actionBtn = new TextView(this);
        actionBtn.setGravity(Gravity.CENTER);
        actionBtn.setTextSize(13); actionBtn.setTypeface(null, Typeface.BOLD);
        if (server.isConnected()) {
            actionBtn.setText("Disconnect");
            actionBtn.setTextColor(INK);
            actionBtn.setBackground(roundRect(SURFACE_MUTED, 999, BORDER));
            actionBtn.setOnClickListener(v -> {
                McpClientManager.getInstance(this).disconnect(server.id);
                server.status = McpStatus.DISCONNECTED;
                McpClientManager.getInstance(this).updateServerInStore(server);
                dialog.dismiss();
                renderHubSections();
                Toast.makeText(this, "Disconnected from " + server.name, Toast.LENGTH_SHORT).show();
            });
        } else {
            actionBtn.setText(server.status == McpStatus.START_ERROR || server.status == McpStatus.PROTOCOL_ERROR ? "Retry Handshake" : "Connect & Verify");
            actionBtn.setTextColor(Color.WHITE);
            actionBtn.setBackground(roundRect(0xff111111, 999, 0x00000000));
            actionBtn.setOnClickListener(v -> {
                actionBtn.setText("Connecting...");
                actionBtn.setEnabled(false);
                McpClientManager.getInstance(this).connect(server, new McpClientManager.HandshakeCallback() {
                    @Override
                    public void onProgress(McpStatus status) {
                        runOnUiThread(() -> actionBtn.setText(status.label));
                    }
                    @Override
                    public void onSuccess(McpServerConfig config) {
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            renderHubSections();
                            Toast.makeText(PluginCenterActivity.this, "Connected! Discovered " + config.tools.length() + " tools.", Toast.LENGTH_SHORT).show();
                        });
                    }
                    @Override
                    public void onFailure(McpStatus errorStatus, String error) {
                        runOnUiThread(() -> {
                            actionBtn.setEnabled(true);
                            if (errorStatus == McpStatus.AUTH_REQUIRED && server.oauthAuthorizationUrl != null) {
                                actionBtn.setText("Authorize in Browser");
                                actionBtn.setOnClickListener(av -> {
                                    try {
                                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(server.oauthAuthorizationUrl)));
                                    } catch (Exception ex) {
                                        Toast.makeText(PluginCenterActivity.this, "Browser launch error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else {
                                actionBtn.setText("Retry Handshake");
                            }
                            renderHubSections();
                        });
                    }
                });
            });
        }
        actionsRow.addView(actionBtn, new LinearLayout.LayoutParams(0, dp(44), 1f));

        TextView deleteBtn = new TextView(this);
        deleteBtn.setText("Delete");
        deleteBtn.setGravity(Gravity.CENTER);
        deleteBtn.setTextColor(0xffdc2626);
        deleteBtn.setTextSize(13); deleteBtn.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(dp(80), dp(44));
        delLp.leftMargin = dp(8);
        deleteBtn.setOnClickListener(v -> {
            McpClientManager.getInstance(this).deleteServer(server.id);
            dialog.dismiss();
            renderHubSections();
            Toast.makeText(this, "Server removed", Toast.LENGTH_SHORT).show();
        });
        actionsRow.addView(deleteBtn, delLp);

        dialog.setContentView(sheet);
        presentBottomSheet(dialog);
    }

    private void addInfoRow(LinearLayout container, String key, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(2));
        TextView kView = new TextView(this);
        kView.setText(key + ":");
        kView.setTextColor(MUTED); kView.setTextSize(12);
        kView.setLayoutParams(new LinearLayout.LayoutParams(dp(130), -2));
        row.addView(kView);
        TextView vView = new TextView(this);
        vView.setText(value);
        vView.setTextColor(INK); vView.setTextSize(12); vView.setTypeface(null, Typeface.BOLD);
        vView.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(vView);
        container.addView(row);
    }

    private LinearLayout hubCard(String title,String body,String action,boolean active,int iconRes){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16),dp(14),dp(16),dp(14));
        card.setBackground(roundRect(active?0xfff4f4f5:SURFACE,16,BORDER));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(10);
        card.setLayoutParams(lp);

        FrameLayout iconBox=new FrameLayout(this);
        iconBox.setBackground(roundRect(SURFACE_MUTED,14,BORDER));
        card.addView(iconBox,new LinearLayout.LayoutParams(dp(44),dp(44)));
        ImageView icon=new ImageView(this);
        icon.setImageResource(iconRes > 0 ? iconRes : R.drawable.ic_connections);
        icon.setColorFilter(INK);
        iconBox.addView(icon,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));

        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1f);
        cp.leftMargin=dp(12);
        card.addView(copy,cp);

        TextView t=new TextView(this);t.setText(title);t.setTextColor(INK);t.setTextSize(15);t.setTypeface(null,Typeface.BOLD);
        TextView d=new TextView(this);d.setText(body);d.setTextColor(MUTED);d.setTextSize(12);d.setLineSpacing(0,1.05f);d.setMaxLines(2);
        copy.addView(t);copy.addView(d);

        TextView a=new TextView(this);
        a.setText(action);
        a.setTextColor(active?INK:0xFF52525B);
        a.setTextSize(12);
        a.setTypeface(null,Typeface.BOLD);
        a.setPadding(dp(12),dp(8),dp(12),dp(8));
        a.setBackground(roundRect(SURFACE_MUTED,999,BORDER));
        card.addView(a);
        return card;
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
