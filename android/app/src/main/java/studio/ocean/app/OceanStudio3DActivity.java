package studio.ocean.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import java.io.File;

public final class OceanStudio3DActivity extends Activity {
  static final int BLACK=0xff050505, PANEL=0xee101010, BORDER=0xff303030, WHITE=0xfff3f3f3, MUTED=0xff9a9a9a;
  FrameLayout root; StudioViewport viewport; LinearLayout rail, bottom, inspector; EditText aiInput; TextView selection;
  final ArrayList<String> history=new ArrayList<>(); final ArrayList<String> objects=new ArrayList<>(); StudioScene scene; StudioExtensionRegistry extensionRegistry; StudioProjectStore projectStore; StudioPluginRegistry pluginRegistry; StudioExternalPluginRegistry externalPluginRegistry; StudioExternalExtensionRegistry externalExtensionRegistry; final ExecutorService externalExecutor=Executors.newSingleThreadExecutor();
  static final int REQ_IMPORT=771;
  int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  GradientDrawable bg(int c,float r,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
  TextView button(String s){TextView v=new TextView(this);v.setText(s);v.setTextColor(WHITE);v.setTextSize(12);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setBackground(bg(PANEL,12,BORDER));return v;}
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BLACK);getWindow().setNavigationBarColor(BLACK);scene=new StudioScene();extensionRegistry=new StudioExtensionRegistry(this);projectStore=new StudioProjectStore(this);pluginRegistry=new StudioPluginRegistry();externalPluginRegistry=new StudioExternalPluginRegistry();externalExtensionRegistry=new StudioExternalExtensionRegistry(this);applySystemBars();objects.add("Baseplate");objects.add("Spawn");build();}
  void applySystemBars(){
    boolean dark=extensionRegistry!=null&&extensionRegistry.isEnabled("dark-system-bars");
    getWindow().setStatusBarColor(dark?BLACK:0xff202020);
    getWindow().setNavigationBarColor(dark?BLACK:0xff202020);
  }
  void build(){
    root=new FrameLayout(this);root.setBackgroundColor(BLACK);setContentView(root);
    viewport=new StudioViewport(this);root.addView(viewport,new FrameLayout.LayoutParams(-1,-1));
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(10),dp(7),dp(10),dp(7));top.setBackgroundColor(0xbb050505);
    FrameLayout.LayoutParams tl=new FrameLayout.LayoutParams(-1,dp(54),Gravity.TOP);root.addView(top,tl);
    TextView ai=button("✦");ai.setTextSize(21);ai.setContentDescription("Ocean AI");ai.setOnClickListener(v->showAI());top.addView(ai,new LinearLayout.LayoutParams(dp(44),dp(40)));
    TextView name=new TextView(this);name.setText("  OCEAN STUDIO  ·  Scene 1");name.setTextColor(WHITE);name.setTextSize(13);name.setTypeface(null,Typeface.BOLD);top.addView(name,new LinearLayout.LayoutParams(0,-1,1));
    for(String s:new String[]{"↶","↷","▶","□","⋮"}){TextView b=button(s);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(42),dp(38));lp.leftMargin=dp(5);top.addView(b,lp);b.setOnClickListener(v->handleTop(((TextView)v).getText().toString()));}
    buildRail();buildBottom();buildInspector();
    TextView hint=new TextView(this);hint.setText("1 finger orbit  •  2 fingers pan/zoom  •  tap object to select");hint.setVisibility(extensionRegistry.isEnabled("gesture-hints")?View.VISIBLE:View.GONE);hint.setTextColor(0xffb0b0b0);hint.setTextSize(10);hint.setGravity(Gravity.CENTER);hint.setBackground(bg(0xaa0b0b0b,10,BORDER));
    FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,dp(28),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.bottomMargin=dp(64);root.addView(hint,hp);
  }
  void buildRail(){
    rail=new LinearLayout(this);rail.setOrientation(LinearLayout.VERTICAL);rail.setPadding(dp(6),dp(6),dp(6),dp(6));rail.setBackground(bg(0xe80b0b0b,16,BORDER));
    String[][] tools={{"↖","Select"},{"✥","Move"},{"⟳","Rotate"},{"↔","Scale"},{"▣","Box"},{"●","Sphere"},{"▲","Mesh"},{"✎","Draw"},{"⌁","Curve"},{"▦","Grid"},{"☀","Light"},{"◉","Camera"},{"⚓","Anchor"},{"⛓","Joint"},{"◫","Material"},{"▤","Texture"},{"♨","Physics"},{"☁","World"},{"⌖","Snap"},{"⊕","Add"}};
    for(String[] t:tools){TextView b=button(t[0]);b.setContentDescription(t[1]);b.setTextSize(17);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(40),dp(36));lp.bottomMargin=dp(4);rail.addView(b,lp);b.setOnClickListener(v->{viewport.tool=t[1];Toast.makeText(this,t[1],Toast.LENGTH_SHORT).show();});}
    ScrollView sc=new ScrollView(this);sc.setVerticalScrollBarEnabled(false);sc.addView(rail);FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(54),-2,Gravity.TOP|Gravity.RIGHT);rp.topMargin=dp(62);rp.rightMargin=dp(8);rp.bottomMargin=dp(70);root.addView(sc,rp);
  }
  void buildBottom(){
    bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER);bottom.setPadding(dp(8),dp(6),dp(8),dp(6));bottom.setBackgroundColor(0xdd070707);
    String[] tools={"＋ Add","Asset","Import","Export","Scene","Layers","Outliner","Inspector","Transform","Material","Texture","UV","Terrain","Sculpt","Paint","Vertex","Edge","Face","Rig","Bones","Weights","Animate","Timeline","Keyframe","Physics","Collision","Constraint","Lighting","World","Camera","Audio","Particles","Nodes","Shader","Measure","Mirror","Array","Boolean","Remesh","Decimate","Normals","Origin","Pivot","Code","Console","Plugin","Extensions","Settings"};
    for(String s:tools){TextView b=button(s);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));lp.rightMargin=dp(6);bottom.addView(b,lp);b.setOnClickListener(v->toolAction(s));}
    HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.addView(bottom);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,dp(52),Gravity.BOTTOM);root.addView(hs,bp);
  }
  void buildInspector(){
    inspector=new LinearLayout(this);inspector.setOrientation(LinearLayout.VERTICAL);inspector.setPadding(dp(12),dp(10),dp(12),dp(10));inspector.setBackground(bg(0xe80a0a0a,14,BORDER));inspector.setVisibility(View.GONE);
    selection=new TextView(this);selection.setTextColor(WHITE);selection.setTextSize(14);selection.setTypeface(null,Typeface.BOLD);inspector.addView(selection,new LinearLayout.LayoutParams(-1,dp(32)));
    String[] rows={"Transform  X 0  Y 0  Z 0","Rotation  0°  0°  0°","Scale  1  1  1","Material  Default","Physics  Static","Visibility  Visible"};
    for(String r:rows){TextView x=button(r);x.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(38));lp.bottomMargin=dp(5);inspector.addView(x,lp);}
    TextView close=button("Close Inspector");close.setOnClickListener(v->inspector.setVisibility(View.GONE));inspector.addView(close,new LinearLayout.LayoutParams(-1,dp(38)));
    FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(230),-2,Gravity.TOP|Gravity.LEFT);ip.topMargin=dp(64);ip.leftMargin=dp(10);root.addView(inspector,ip);
  }
  void toolAction(String s){
    if(s.equals("Plugin"))showPlugins();
    else if(s.equals("Extensions"))showExtensions();
    else if(s.equals("Settings"))showCatalog("Studio Settings",new String[]{"Renderer","Quality","Grid & Snapping","Autosave","Input & Gestures","Performance","Extensions Sources","AI Permissions","Memory Budget","Thermal Mode","Touch Sensitivity","Project Units"});
    else if(s.equals("＋ Add"))showCatalog("Add Object",new String[]{"Cube","Sphere","Cylinder","Plane","Cone","Text","Light","Camera","Spawn","Empty"});
    else if(s.equals("Scene")||s.equals("Outliner")||s.equals("Layers"))showOutliner();
    else if(s.equals("Import")||s.equals("Asset"))openImporter();
    else {viewport.tool=s;Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  }
  void openImporter(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
    i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"model/gltf-binary","model/gltf+json","model/obj","application/octet-stream","application/json","text/plain","image/*"});
    startActivityForResult(i,REQ_IMPORT);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request!=REQ_IMPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri uri=data.getData();if(extensionRegistry.isEnabled("persistent-import-access")){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    String name=StudioOpenSourceTools.displayName(this,uri);
    String low=name.toLowerCase(Locale.US);
    String type=(low.endsWith(".glb")||low.endsWith(".gltf"))?"gltf":low.endsWith(".obj")?"obj":(low.endsWith(".png")||low.endsWith(".jpg")||low.endsWith(".jpeg")||low.endsWith(".bmp")||low.endsWith(".tga")||low.endsWith(".hdr"))?"image":"asset";
    if(!extensionRegistry.isEnabled("import-scene-nodes")){Toast.makeText(this,"Import node extension is disabled",Toast.LENGTH_SHORT).show();return;}
    try{
      File local=StudioOpenSourceTools.materialize(this,uri,name);
      StudioScene.Node node=scene.addImported(name,type,local.getAbsolutePath());
      objects.add(name);viewport.invalidate();
      Toast.makeText(this,"Imported "+name+" · analyzing open-source tools…",Toast.LENGTH_SHORT).show();
      runExternalImportExtensions(node);
    }catch(Exception ex){Toast.makeText(this,"Import failed: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
  }
  void runExternalImportExtensions(StudioScene.Node node){
    externalExecutor.execute(()->{
      StringBuilder meta=new StringBuilder();
      try{
        if(node.type.equals("gltf")){
          String inspect=null;
          boolean needInspect=externalExtensionRegistry.isEnabled("auto-cgltf-summary")||externalExtensionRegistry.isEnabled("auto-cgltf-validation")||externalExtensionRegistry.isEnabled("auto-cgltf-mesh-stats")||externalExtensionRegistry.isEnabled("auto-cgltf-material-stats")||externalExtensionRegistry.isEnabled("auto-cgltf-animation-stats");
          if(needInspect)inspect=StudioOpenSourceTools.inspectGltf(node.sourcePath);
          if(externalExtensionRegistry.isEnabled("auto-cgltf-summary"))meta.append("cgltf summary: ").append(inspect).append('\n');
          if(inspect!=null){
            JSONObject j=new JSONObject(inspect);
            if(externalExtensionRegistry.isEnabled("auto-cgltf-validation"))meta.append("validation: valid=").append(j.optBoolean("valid")).append(" buffers=").append(j.optBoolean("buffersLoaded")).append('\n');
            if(externalExtensionRegistry.isEnabled("auto-cgltf-mesh-stats"))meta.append("mesh: ").append(j.optInt("meshes")).append(" meshes, ").append(j.optInt("primitives")).append(" primitives, ").append(j.optLong("vertices")).append(" vertices, ").append(j.optLong("indices")).append(" indices\n");
            if(externalExtensionRegistry.isEnabled("auto-cgltf-material-stats"))meta.append("materials: ").append(j.optInt("materials")).append(" · textures ").append(j.optInt("textures")).append(" · images ").append(j.optInt("images")).append('\n');
            if(externalExtensionRegistry.isEnabled("auto-cgltf-animation-stats"))meta.append("animation: ").append(j.optInt("animations")).append(" · cameras ").append(j.optInt("cameras")).append(" · lights ").append(j.optInt("lights")).append('\n');
          }
          if(externalExtensionRegistry.isEnabled("auto-meshopt-lod75"))meta.append("LOD75: ").append(StudioOpenSourceTools.simplifyGltf(node.sourcePath,.75f)).append('\n');
          if(externalExtensionRegistry.isEnabled("auto-meshopt-lod50"))meta.append("LOD50: ").append(StudioOpenSourceTools.simplifyGltf(node.sourcePath,.50f)).append('\n');
          if(externalExtensionRegistry.isEnabled("auto-meshopt-lod25"))meta.append("LOD25: ").append(StudioOpenSourceTools.simplifyGltf(node.sourcePath,.25f)).append('\n');
          if(externalExtensionRegistry.isEnabled("auto-meshopt-cache"))meta.append("cache: ").append(StudioOpenSourceTools.vertexCacheGltf(node.sourcePath)).append('\n');
        }else if(node.type.equals("obj")){
          String inspect=StudioOpenSourceTools.inspectObj(node.sourcePath);
          JSONObject j=new JSONObject(inspect);
          if(externalExtensionRegistry.isEnabled("auto-obj-summary"))meta.append("tinyobj: ").append(inspect).append('\n');
          if(externalExtensionRegistry.isEnabled("auto-obj-face-stats"))meta.append("faces: ").append(j.optLong("faces")).append(" · indices ").append(j.optLong("indices")).append('\n');
          if(externalExtensionRegistry.isEnabled("auto-obj-material-stats"))meta.append("materials: ").append(j.optInt("materials")).append(" · shapes ").append(j.optInt("shapes")).append('\n');
        }else if(node.type.equals("image")){
          String inspect=StudioOpenSourceTools.inspectImage(node.sourcePath);
          JSONObject j=new JSONObject(inspect);
          if(externalExtensionRegistry.isEnabled("auto-texture-probe"))meta.append("stb: ").append(inspect).append('\n');
          int w=j.optInt("width"),h=j.optInt("height"),ch=Math.max(1,j.optInt("channels"));
          if(externalExtensionRegistry.isEnabled("warn-4k-textures")&&(w>4096||h>4096))meta.append("warning: texture exceeds 4096px on one axis\n");
          if(externalExtensionRegistry.isEnabled("estimate-texture-memory"))meta.append("texture memory ≈ ").append(String.format(Locale.US,"%.2f",((long)w*h*ch)/1048576.0)).append(" MiB base level\n");
        }
      }catch(Throwable t){meta.append("external analysis error: ").append(t.getClass().getSimpleName()).append(" · ").append(t.getMessage());}
      final String result=meta.toString().trim();
      runOnUiThread(()->{
        scene.setMetadata(node.id,result);
        if(!result.isEmpty())Toast.makeText(this,"Open-source analysis attached to "+node.name,Toast.LENGTH_SHORT).show();
      });
    });
  }
  void showOutliner(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));
    for(StudioScene.Node n:scene.all()){TextView row=button((n.visible?"◉ ":"○ ")+n.name+"   ·   "+n.type);row.setGravity(Gravity.CENTER_VERTICAL);row.setOnClickListener(v->{scene.select(n.id);select(n.name);viewport.invalidate();});row.setOnLongClickListener(v->{if(n.metadata!=null&&!n.metadata.isEmpty())new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(n.name+" · analysis").setMessage(n.metadata).setPositiveButton("Done",null).show();else Toast.makeText(this,"No analysis metadata yet",Toast.LENGTH_SHORT).show();return true;});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42));lp.bottomMargin=dp(5);box.addView(row,lp);}
    ScrollView sv=new ScrollView(this);sv.addView(box);new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Scene Outliner").setView(sv).setNegativeButton("Close",null).show();
  }
  void showPlugins(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(8),dp(14),dp(8));
    TextView nativeState=new TextView(this);nativeState.setText(StudioOpenSourceTools.available()?"External native toolchain: loaded":"External native toolchain: unavailable · "+StudioOpenSourceTools.unavailableReason());nativeState.setTextColor(StudioOpenSourceTools.available()?0xff9ad7b1:0xffff9b8f);nativeState.setTextSize(11);box.addView(nativeState,new LinearLayout.LayoutParams(-1,dp(36)));
    TextView builtTitle=new TextView(this);builtTitle.setText("OCEAN BUILT-IN · 15");builtTitle.setTextColor(MUTED);builtTitle.setTextSize(10);builtTitle.setLetterSpacing(.14f);box.addView(builtTitle,new LinearLayout.LayoutParams(-1,dp(28)));
    for(StudioPluginRegistry.Plugin p:pluginRegistry.all()){
      LinearLayout row=pluginRow(p.name,p.description,"Built-in");
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.bottomMargin=dp(6);box.addView(row,lp);
      row.setOnClickListener(v->runPlugin(p));
    }
    TextView extTitle=new TextView(this);extTitle.setText("EXTERNAL OPEN SOURCE · 15");extTitle.setTextColor(MUTED);extTitle.setTextSize(10);extTitle.setLetterSpacing(.14f);box.addView(extTitle,new LinearLayout.LayoutParams(-1,dp(34)));
    for(StudioExternalPluginRegistry.Plugin p:externalPluginRegistry.all()){
      LinearLayout row=pluginRow(p.name,p.description,p.sourceRepo+" · "+p.tool);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(68));lp.bottomMargin=dp(6);box.addView(row,lp);
      row.setOnClickListener(v->runExternalPlugin(p));
    }
    ScrollView sv=new ScrollView(this);sv.addView(box);new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Studio Plugins · 30").setView(sv).setNegativeButton("Close",null).show();
  }
  LinearLayout pluginRow(String nameText,String descText,String sourceText){
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(12),dp(5),dp(12),dp(5));row.setBackground(bg(PANEL,12,BORDER));
    TextView name=new TextView(this);name.setText(nameText);name.setTextColor(WHITE);name.setTextSize(13);name.setTypeface(null,Typeface.BOLD);
    TextView desc=new TextView(this);desc.setText(descText);desc.setTextColor(MUTED);desc.setTextSize(10);
    TextView source=new TextView(this);source.setText(sourceText);source.setTextColor(0xff727c86);source.setTextSize(9);
    row.addView(name,new LinearLayout.LayoutParams(-1,dp(20)));row.addView(desc,new LinearLayout.LayoutParams(-1,dp(20)));row.addView(source,new LinearLayout.LayoutParams(-1,dp(18)));
    return row;
  }
  void runExternalPlugin(StudioExternalPluginRegistry.Plugin p){
    StudioScene.Node node=scene.selected();
    if(node==null){Toast.makeText(this,"Select an imported asset first",Toast.LENGTH_SHORT).show();return;}
    if(!p.accepts.equals(node.type)){Toast.makeText(this,p.name+" expects "+p.accepts+" · selected "+node.type,Toast.LENGTH_LONG).show();return;}
    Toast.makeText(this,"Running "+p.name+"…",Toast.LENGTH_SHORT).show();
    externalExecutor.execute(()->{
      String result=externalPluginRegistry.run(p,node);
      runOnUiThread(()->new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(p.name).setMessage(result).setPositiveButton("Done",null).show());
    });
  }
  void runPlugin(StudioPluginRegistry.Plugin p){
    if(p.id.equals("delete")&&extensionRegistry.isEnabled("confirm-destructive")){
      new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Delete selected object?").setMessage("This plugin will remove the selected scene node. Undo remains available.").setPositiveButton("Delete",(d,w)->executePlugin(p)).setNegativeButton("Cancel",null).show();
    }else executePlugin(p);
  }
  void executePlugin(StudioPluginRegistry.Plugin p){
    StudioPluginRegistry.Result r=pluginRegistry.run(p.id,scene);if(r.changed)viewport.invalidate();Toast.makeText(this,r.message,Toast.LENGTH_SHORT).show();
  }
  void showExtensions(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(8),dp(14),dp(8));
    TextView note=new TextView(this);note.setText("Built-ins are packaged with Ocean Studio. External sources are metadata-only until reviewed and explicitly enabled.");note.setTextColor(MUTED);note.setTextSize(12);box.addView(note,new LinearLayout.LayoutParams(-1,dp(52)));
    for(StudioExtensionRegistry.Entry e:extensionRegistry.builtins()){
      CheckBox row=new CheckBox(this);row.setText(e.name+"   "+e.version+"\n"+e.description);row.setTextColor(WHITE);row.setTextSize(12);row.setChecked(extensionRegistry.isEnabled(e.id,e.enabled));row.setOnCheckedChangeListener((b,on)->{extensionRegistry.setEnabled(e.id,on);if(e.id.equals("dark-system-bars"))applySystemBars();viewport.invalidate();});box.addView(row,new LinearLayout.LayoutParams(-1,dp(58)));
    }
    TextView externalTitle=new TextView(this);externalTitle.setText("EXTERNAL OPEN SOURCE · 15");externalTitle.setTextColor(MUTED);externalTitle.setTextSize(10);externalTitle.setLetterSpacing(.14f);box.addView(externalTitle,new LinearLayout.LayoutParams(-1,dp(34)));
    for(StudioExternalExtensionRegistry.Extension e:externalExtensionRegistry.all()){
      CheckBox row=new CheckBox(this);row.setText(e.name+"\n"+e.sourceRepo+" · "+e.tool+" · "+e.description);row.setTextColor(WHITE);row.setTextSize(11);row.setChecked(externalExtensionRegistry.isEnabled(e.id));row.setOnCheckedChangeListener((b,on)->externalExtensionRegistry.setEnabled(e.id,on));box.addView(row,new LinearLayout.LayoutParams(-1,dp(70)));
    }
    TextView source=button("＋ Add HTTPS extension source");box.addView(source,new LinearLayout.LayoutParams(-1,dp(42)));
    source.setOnClickListener(v->promptSource());
    ScrollView sv=new ScrollView(this);sv.addView(box);new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Extensions").setView(sv).setNegativeButton("Close",null).show();
  }
  void promptSource(){
    EditText e=new EditText(this);e.setHint("https://example.com/ocean-extension-index.json");e.setSingleLine(true);
    new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Add extension source").setMessage("Sources are not executed automatically. Ocean stores the index location; installation must be explicitly approved.").setView(e).setPositiveButton("Add",(d,w)->{try{extensionRegistry.addSource(e.getText().toString());Toast.makeText(this,"Source added",Toast.LENGTH_SHORT).show();}catch(Exception ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}}).setNegativeButton("Cancel",null).show();
  }
  void showCatalog(String title,String[] items){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(8),dp(14),dp(8));
    for(String s:items){TextView b=button(s);b.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.bottomMargin=dp(6);box.addView(b,lp);b.setOnClickListener(v->{if(title.equals("Add Object")){objects.add(s);scene.add(s,s.toLowerCase(Locale.US));viewport.addPrimitive(s);}else Toast.makeText(this,s+" ready",Toast.LENGTH_SHORT).show();});}
    ScrollView sv=new ScrollView(this);sv.addView(box);new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(title).setView(sv).setNegativeButton("Close",null).show();
  }
  void showAI(){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(12),dp(14),dp(12));
    TextView h=new TextView(this);h.setText("✦  Ocean AI\nScene-aware studio agent");h.setTextColor(WHITE);h.setTextSize(18);h.setTypeface(null,Typeface.BOLD);panel.addView(h,new LinearLayout.LayoutParams(-1,dp(58)));
    TextView scope=new TextView(this);scope.setText("Can inspect the scene graph, selection, transforms, materials and Studio tools. Scene context is generated locally; changes require an explicit command.");scope.setTextColor(MUTED);scope.setTextSize(12);panel.addView(scope,new LinearLayout.LayoutParams(-1,dp(52)));
    aiInput=new EditText(this);aiInput.setHint("Build or edit this scene…");aiInput.setHintTextColor(0xff777777);aiInput.setTextColor(WHITE);aiInput.setBackground(bg(0xff151515,16,BORDER));aiInput.setPadding(dp(12),dp(8),dp(12),dp(8));panel.addView(aiInput,new LinearLayout.LayoutParams(-1,dp(70)));
    LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.RIGHT);TextView inspect=button("Inspect scene");TextView send=button("Run ↗");actions.addView(inspect,new LinearLayout.LayoutParams(-2,dp(40)));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,dp(40));sp.leftMargin=dp(8);actions.addView(send,sp);panel.addView(actions,new LinearLayout.LayoutParams(-1,dp(48)));
    AlertDialog d=new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setView(panel).create();
    inspect.setOnClickListener(v->Toast.makeText(this,"Scene: "+scene.size()+" objects · tool "+viewport.tool,Toast.LENGTH_LONG).show());
    send.setOnClickListener(v->{String q=aiInput.getText().toString().trim();if(q.isEmpty())return;history.add(q+(extensionRegistry.isEnabled("ai-scene-context")?"\nCONTEXT "+scene.snapshot():""));Toast.makeText(this,extensionRegistry.isEnabled("ai-scene-context")?"Agent command queued with scene context":"Agent command queued",Toast.LENGTH_SHORT).show();});
    d.show();
  }
  void handleTop(String s){
    if(s.equals("↶")){if(scene.undo()){viewport.invalidate();Toast.makeText(this,"Undo",Toast.LENGTH_SHORT).show();}}
    else if(s.equals("↷")){if(scene.redo()){viewport.invalidate();Toast.makeText(this,"Redo",Toast.LENGTH_SHORT).show();}}
    else if(s.equals("▶"))Toast.makeText(this,"Preview mode",Toast.LENGTH_SHORT).show();
    else if(s.equals("□"))finish();
    else showProjectMenu();
  }
  void showProjectMenu(){
    String[] items={"Save project","Project info","Reset camera"};
    new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Scene 1").setItems(items,(d,w)->{
      if(w==0){try{java.io.File f=projectStore.save("Scene_1",scene.snapshot());Toast.makeText(this,"Saved "+f.getName(),Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
      else if(w==1)Toast.makeText(this,scene.size()+" scene nodes · "+projectStore.list().length+" saved projects",Toast.LENGTH_LONG).show();
      else {viewport.zoom=1;viewport.panX=viewport.panY=0;viewport.invalidate();}
    }).show();
  }
  void select(String s){selection.setText(s+"  ·  Inspector");if(extensionRegistry.isEnabled("auto-inspector"))inspector.setVisibility(View.VISIBLE);}

  @Override protected void onDestroy(){super.onDestroy();externalExecutor.shutdownNow();}\n\n  @Override protected void onPause(){super.onPause();if(extensionRegistry!=null&&extensionRegistry.isEnabled("autosave-background")&&projectStore!=null&&scene!=null){try{projectStore.save("Scene_1_autosave",scene.snapshot());}catch(Exception ignored){}}}

  final class StudioViewport extends View {
    Paint p=new Paint(3); Paint line=new Paint(3); float yaw=-.45f,pitch=.52f,zoom=1f,panX=0,panY=0; String tool="Select"; float lastX,lastY; int pointers;
    StudioViewport(Context c){super(c);line.setStrokeWidth(dp(1));setBackgroundColor(0xff161b20);}
    void proj(Canvas c,float x,float z,float y,Paint paint){ }
    @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();float horizon=h*.36f+panY;
      LinearGradient sky=new LinearGradient(0,0,0,horizon,0xff11171d,0xff29343d,Shader.TileMode.CLAMP);p.setShader(sky);c.drawRect(0,0,w,horizon,p);p.setShader(null);
      p.setColor(0xff262b31);c.drawRect(0,horizon,w,h,p);
      float spacing=dp(34)*zoom;float vanX=w*.50f+panX;
      if(extensionRegistry.isEnabled("grid-overlay")){line.setColor(0xff3a4148);for(int i=-20;i<=20;i++){float bx=vanX+i*spacing;c.drawLine(vanX,horizon,bx,h,line);}for(int i=1;i<22;i++){float t=i/22f;float yy=horizon+(h-horizon)*(t*t);c.drawLine(0,yy,w,yy,line);}}
      if(extensionRegistry.isEnabled("axis-guides")){line.setColor(0xff785454);c.drawLine(0,horizon+(h-horizon)*.62f,w,horizon+(h-horizon)*.62f,line);line.setColor(0xff506d82);c.drawLine(vanX,horizon,vanX,h,line);}
      if(extensionRegistry.isEnabled("spawn-marker"))drawSpawn(c,w*.5f+panX,h*.62f+panY);
      if(extensionRegistry.isEnabled("primitive-preview"))drawCube(c,w*.64f+panX,h*.57f+panY,dp(58)*zoom);
      if(extensionRegistry.isEnabled("viewport-hud")){p.setColor(0xcc0a0a0a);c.drawRoundRect(dp(12),dp(66),dp(205),dp(102),dp(10),dp(10),p);p.setColor(WHITE);p.setTextSize(dp(12));c.drawText("Perspective  ·  "+tool,dp(24),dp(89),p);}
    }
    void drawCube(Canvas c,float x,float y,float s){p.setColor(0xff778fa4);c.drawRect(x-s/2,y-s/2,x+s/2,y+s/2,p);p.setColor(0xff92a8ba);Path q=new Path();q.moveTo(x-s/2,y-s/2);q.lineTo(x-s*.25f,y-s*.72f);q.lineTo(x+s*.75f,y-s*.72f);q.lineTo(x+s/2,y-s/2);q.close();c.drawPath(q,p);p.setColor(0xff52697d);Path r=new Path();r.moveTo(x+s/2,y-s/2);r.lineTo(x+s*.75f,y-s*.72f);r.lineTo(x+s*.75f,y+s*.28f);r.lineTo(x+s/2,y+s/2);r.close();c.drawPath(r,p);}
    void drawSpawn(Canvas c,float x,float y){float ww=dp(120)*zoom,hh=dp(54)*zoom;p.setColor(0xffd8dadd);c.drawRoundRect(x-ww/2,y-hh/2,x+ww/2,y+hh/2,dp(9),dp(9),p);p.setColor(0xff202226);p.setStrokeWidth(dp(4));for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(x,y,x+(float)Math.cos(a)*ww*.28f,y+(float)Math.sin(a)*hh*.34f,p);}c.drawCircle(x,y,dp(5),p);}
    void addPrimitive(String s){invalidate();Toast.makeText(OceanStudio3DActivity.this,s+" added to scene",Toast.LENGTH_SHORT).show();}
    @Override public boolean onTouchEvent(android.view.MotionEvent e){pointers=e.getPointerCount();if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();if(e.getY()>getHeight()*.48f&&e.getX()>getWidth()*.54f)select("Cube");else if(e.getY()>getHeight()*.48f)select("Spawn");return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-lastX,dy=e.getY()-lastY;if(pointers>1){if(extensionRegistry.isEnabled("multitouch-pan")){panX+=dx*.5f;panY+=dy*.5f;}}else if(extensionRegistry.isEnabled("touch-orbit")){yaw+=dx*.006f;pitch+=dy*.006f;panX+=dx*.12f;}lastX=e.getX();lastY=e.getY();invalidate();return true;}return true;}
  }
}