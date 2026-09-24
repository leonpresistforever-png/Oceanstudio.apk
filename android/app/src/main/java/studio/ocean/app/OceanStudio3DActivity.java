package studio.ocean.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

public final class OceanStudio3DActivity extends Activity {
  static final int BLACK=0xff050505, PANEL=0xee101010, BORDER=0xff303030, WHITE=0xfff3f3f3, MUTED=0xff9a9a9a;
  FrameLayout root; StudioViewport viewport; LinearLayout rail, bottom, inspector; EditText aiInput; TextView selection;
  final ArrayList<String> history=new ArrayList<>(); final ArrayList<String> objects=new ArrayList<>(); StudioScene scene; StudioExtensionRegistry extensionRegistry; StudioProjectStore projectStore; StudioPluginRegistry pluginRegistry; StudioExternalPluginRegistry externalPluginRegistry; StudioExternalExtensionRegistry externalExtensionRegistry; StudioEngineTargetRegistry engineTargetRegistry; StudioQuickToolRegistry quickToolRegistry; final ExecutorService externalExecutor=Executors.newSingleThreadExecutor();
  StudioScene.Node pendingExportNode; String pendingExportFormatId="",pendingExportExt="",pendingExportDataExt=""; boolean pendingExportZip;
  static final int REQ_IMPORT=771,REQ_EXPORT=772;
  int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  GradientDrawable bg(int c,float r,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
  TextView button(String s){TextView v=new TextView(this);v.setText(s);v.setTextColor(WHITE);v.setTextSize(12);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setBackground(bg(PANEL,12,BORDER));return v;}
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(BLACK);getWindow().setNavigationBarColor(BLACK);scene=new StudioScene();extensionRegistry=new StudioExtensionRegistry(this);projectStore=new StudioProjectStore(this);pluginRegistry=new StudioPluginRegistry();externalPluginRegistry=new StudioExternalPluginRegistry();externalExtensionRegistry=new StudioExternalExtensionRegistry(this);engineTargetRegistry=new StudioEngineTargetRegistry(this);quickToolRegistry=new StudioQuickToolRegistry();applySystemBars();objects.add("Baseplate");objects.add("Spawn");build();}
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
    String[] tools={"＋ Add","Asset","Import","Engine","Export","Tools","Scene","Layers","Outliner","Inspector","Transform","Material","Texture","UV","Terrain","Sculpt","Paint","Vertex","Edge","Face","Rig","Bones","Weights","Animate","Timeline","Keyframe","Physics","Collision","Constraint","Lighting","World","Camera","Audio","Particles","Nodes","Shader","Measure","Mirror","Array","Boolean","Remesh","Decimate","Normals","Origin","Pivot","Code","Console","Add-ons","Plugin","Extensions","Settings"};
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
    if(s.equals("Add-ons"))showAddons();
    else if(s.equals("Plugin"))showPlugins();
    else if(s.equals("Extensions"))showExtensions();
    else if(s.equals("Settings"))showCatalog("Studio Settings",new String[]{"Renderer","Quality","Grid & Snapping","Autosave","Input & Gestures","Performance","Extensions Sources","AI Permissions","Memory Budget","Thermal Mode","Touch Sensitivity","Project Units"});
    else if(s.equals("＋ Add"))showCatalog("Add Object",new String[]{"Cube","Sphere","Cylinder","Plane","Cone","Text","Light","Camera","Spawn","Empty"});
    else if(s.equals("Scene")||s.equals("Outliner")||s.equals("Layers"))showOutliner();
    else if(s.equals("Import")||s.equals("Asset"))openImporter();
    else if(s.equals("Engine"))showEngineTargets();
    else if(s.equals("Export"))showExportCenter();
    else if(s.equals("Tools"))showQuickTools();
    else {viewport.tool=s;Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
  }
  void openImporter(){
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
    i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"model/gltf-binary","model/gltf+json","model/obj","application/octet-stream","application/json","text/plain","image/*"});
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
    startActivityForResult(i,REQ_IMPORT);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==REQ_EXPORT){
      if(result==RESULT_OK&&data!=null&&data.getData()!=null)performPendingExport(data.getData());
      return;
    }
    if(request!=REQ_IMPORT||result!=RESULT_OK||data==null)return;
    ArrayList<Uri> uris=new ArrayList<>();
    if(data.getData()!=null)uris.add(data.getData());
    ClipData clips=data.getClipData();
    if(clips!=null)for(int i=0;i<clips.getItemCount();i++){Uri u=clips.getItemAt(i).getUri();if(u!=null&&!uris.contains(u))uris.add(u);}
    if(uris.isEmpty())return;
    if(!extensionRegistry.isEnabled("import-scene-nodes")){Toast.makeText(this,"Import node extension is disabled",Toast.LENGTH_SHORT).show();return;}
    try{
      File session=StudioOpenSourceTools.createImportSession(this);
      ArrayList<File> locals=new ArrayList<>();
      ArrayList<String> names=new ArrayList<>();
      for(Uri uri:uris){
        if(extensionRegistry.isEnabled("persistent-import-access")){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
        String name=StudioOpenSourceTools.displayName(this,uri);
        locals.add(StudioOpenSourceTools.materializeInto(this,uri,name,session));
        names.add(name);
      }
      int sceneNodes=0;
      for(int i=0;i<locals.size();i++){
        String name=names.get(i),low=name.toLowerCase(Locale.US);
        String type=(low.endsWith(".glb")||low.endsWith(".gltf"))?"gltf":low.endsWith(".obj")?"obj":(low.endsWith(".png")||low.endsWith(".jpg")||low.endsWith(".jpeg")||low.endsWith(".bmp")||low.endsWith(".tga")||low.endsWith(".hdr"))?"image":"sidecar";
        if(type.equals("sidecar"))continue;
        StudioScene.Node node=scene.addImported(name,type,locals.get(i).getAbsolutePath());
        objects.add(name);sceneNodes++;runExternalImportExtensions(node);
      }
      viewport.invalidate();
      Toast.makeText(this,"Imported "+uris.size()+" files · "+sceneNodes+" scene assets · sidecars preserved",Toast.LENGTH_SHORT).show();
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
  void showEngineTargets(){
    List<StudioEngineTargetRegistry.Target> all=engineTargetRegistry.all();
    String[] labels=new String[all.size()];
    StudioEngineTargetRegistry.Target selected=engineTargetRegistry.selected();
    for(int i=0;i<all.size();i++){
      StudioEngineTargetRegistry.Target t=all.get(i);
      labels[i]=(t.id.equals(selected.id)?"✓  ":"")+t.name+"\n"+t.description;
    }
    new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Target Engine").setItems(labels,(d,w)->{
      StudioEngineTargetRegistry.Target t=all.get(w);engineTargetRegistry.select(t.id);
      Toast.makeText(this,"Target: "+t.name+" · "+t.upAxis+" up · "+t.forwardAxis+" forward · "+t.handedness+"-handed",Toast.LENGTH_LONG).show();
    }).setNegativeButton("Close",null).show();
  }

  void showQuickTools(){
    List<StudioQuickToolRegistry.Tool> all=quickToolRegistry.all();
    String[] labels=new String[all.size()];
    for(int i=0;i<all.size();i++)labels[i]=all.get(i).name+"\n"+all.get(i).description;
    new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Quick Tools · 10").setItems(labels,(d,w)->{
      StudioQuickToolRegistry.Result r=quickToolRegistry.run(all.get(w).id,scene);
      if(r.changed)viewport.invalidate();Toast.makeText(this,r.message,Toast.LENGTH_SHORT).show();
    }).setNegativeButton("Close",null).show();
  }

  void showExportCenter(){
    StudioScene.Node node=scene.selected();
    if(node==null||node.sourcePath==null||node.sourcePath.isEmpty()){Toast.makeText(this,"Select an imported model first",Toast.LENGTH_SHORT).show();return;}
    Toast.makeText(this,"Reading compiled export formats…",Toast.LENGTH_SHORT).show();
    externalExecutor.execute(()->{
      try{
        JSONObject rootJson=new JSONObject(StudioOpenSourceTools.assimpExportFormats());
        if(!rootJson.optBoolean("ok"))throw new IllegalStateException(rootJson.optString("error","Assimp unavailable"));
        JSONArray formats=rootJson.getJSONArray("formats");
        ArrayList<JSONObject> options=new ArrayList<>();LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(int i=0;i<formats.length();i++){JSONObject o=formats.getJSONObject(i);options.add(o);ids.add(o.optString("id"));}
        String preferred=engineTargetRegistry.choosePreferred(ids);
        StudioEngineTargetRegistry.Target target=engineTargetRegistry.selected();
        String[] labels=new String[options.size()];
        for(int i=0;i<options.size();i++){
          JSONObject o=options.get(i);String id=o.optString("id"),ext=o.optString("extension");
          labels[i]=(id.equals(preferred)?"★  ":"")+o.optString("description")+"  ·  ."+ext+"  ["+id+"]"+(formatNeedsPackage(id)?" · ZIP keeps sidecars":"");
        }
        runOnUiThread(()->new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK)
          .setTitle("Export · "+target.name+" · "+options.size()+" formats")
          .setMessage("Target profile: "+target.upAxis+" up, "+target.forwardAxis+" forward, "+target.handedness+"-handed. Ocean does not pre-rotate portable formats; target importers handle their documented coordinate conversion.")
          .setItems(labels,(d,w)->{
            JSONObject o=options.get(w);beginExport(node,o.optString("id"),o.optString("extension"));
          }).setNegativeButton("Close",null).show());
      }catch(Throwable t){runOnUiThread(()->Toast.makeText(this,"Export formats unavailable: "+t.getMessage(),Toast.LENGTH_LONG).show());}
    });
  }

  boolean formatNeedsPackage(String id){return id.equals("gltf2")||id.equals("obj")||id.equals("collada");}

  void beginExport(StudioScene.Node node,String formatId,String extension){
    pendingExportNode=node;pendingExportFormatId=formatId;pendingExportDataExt=extension==null||extension.isEmpty()?"bin":extension;pendingExportZip=formatNeedsPackage(formatId);pendingExportExt=pendingExportZip?"zip":pendingExportDataExt;
    String base=node.name.replaceAll("[^A-Za-z0-9._-]","_");int dot=base.lastIndexOf('.');if(dot>0)base=base.substring(0,dot);
    String title=pendingExportZip?base+"-"+formatId+"-package.zip":base+"."+pendingExportExt;
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(pendingExportZip?"application/zip":"application/octet-stream");i.putExtra(Intent.EXTRA_TITLE,title);startActivityForResult(i,REQ_EXPORT);
  }

  void performPendingExport(Uri destination){
    final StudioScene.Node node=pendingExportNode;final String format=pendingExportFormatId,dataExt=pendingExportDataExt;final boolean zip=pendingExportZip;
    if(node==null||format==null||format.isEmpty())return;
    externalExecutor.execute(()->{
      File workspace=null;
      try{
        File root=new File(getCacheDir(),"studio-export");if(!root.exists()&&!root.mkdirs())throw new java.io.IOException("Cannot create export workspace");
        workspace=new File(root,"job-"+System.currentTimeMillis());if(!workspace.mkdirs())throw new java.io.IOException("Cannot create export job");
        File main=new File(workspace,"asset."+dataExt);
        String result=StudioOpenSourceTools.assimpConvert(node.sourcePath,main.getAbsolutePath(),format);
        JSONObject j=new JSONObject(result);if(!j.optBoolean("ok"))throw new java.io.IOException(j.optString("error","Assimp export failed"));
        File deliver=main;
        if(zip){
          deliver=new File(root,"package-"+System.currentTimeMillis()+".zip");
          zipDirectory(workspace,deliver);
        }
        try(FileInputStream in=new FileInputStream(deliver);OutputStream out=getContentResolver().openOutputStream(destination,"w")){
          if(out==null)throw new java.io.IOException("Cannot open destination");
          byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)out.write(buf,0,n);out.flush();
        }
        if(zip&&deliver.exists())deliver.delete();
        runOnUiThread(()->Toast.makeText(this,"Exported "+format+(zip?" package":"")+" for "+engineTargetRegistry.selected().name,Toast.LENGTH_LONG).show());
      }catch(Throwable t){runOnUiThread(()->Toast.makeText(this,"Export failed: "+t.getMessage(),Toast.LENGTH_LONG).show());}
      finally{if(workspace!=null)deleteRecursive(workspace);pendingExportNode=null;pendingExportFormatId="";pendingExportExt="";pendingExportDataExt="";pendingExportZip=false;}
    });
  }

  void zipDirectory(File dir,File zipFile) throws java.io.IOException {
    try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(zipFile))){
      File[] files=dir.listFiles();if(files==null)return;
      byte[] buf=new byte[65536];
      for(File f:files){
        if(!f.isFile())continue;
        zip.putNextEntry(new ZipEntry(f.getName()));
        try(FileInputStream in=new FileInputStream(f)){int n;while((n=in.read(buf))>0)zip.write(buf,0,n);}
        zip.closeEntry();
      }
    }
  }

  void deleteRecursive(File f){
    if(f==null||!f.exists())return;if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File x:children)deleteRecursive(x);}f.delete();
  }

  void showOutliner(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));
    for(StudioScene.Node n:scene.all()){TextView row=button((n.visible?"◉ ":"○ ")+n.name+"   ·   "+n.type);row.setGravity(Gravity.CENTER_VERTICAL);row.setOnClickListener(v->{scene.select(n.id);select(n.name);viewport.invalidate();});row.setOnLongClickListener(v->{if(n.metadata!=null&&!n.metadata.isEmpty())new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(n.name+" · analysis").setMessage(n.metadata).setPositiveButton("Done",null).show();else Toast.makeText(this,"No analysis metadata yet",Toast.LENGTH_SHORT).show();return true;});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(42));lp.bottomMargin=dp(5);box.addView(row,lp);}
    ScrollView sv=new ScrollView(this);sv.addView(box);new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Scene Outliner").setView(sv).setNegativeButton("Close",null).show();
  }
  void showAddons(){
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(8),dp(14),dp(8));
    TextView note=new TextView(this);note.setText("Open-source add-ons below call real native libraries. Process add-ons create new files/nodes instead of only changing UI state.");note.setTextColor(MUTED);note.setTextSize(11);box.addView(note,new LinearLayout.LayoutParams(-1,dp(52)));
    String last="";
    for(StudioExternalPluginRegistry.Plugin p:externalPluginRegistry.all()){
      String category=p.tool.equals("Assimp")?"MODELING & GEOMETRY":p.tool.equals("meshoptimizer")?"OPTIMIZATION":p.tool.equals("xatlas")?"UV & TEXTURING":p.tool.equals("stb_image")?"TEXTURES":"IMPORT / INSPECTION";
      if(!category.equals(last)){
        TextView h=new TextView(this);h.setText(category);h.setTextColor(0xff7f878e);h.setTextSize(10);h.setLetterSpacing(.14f);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(32));hp.topMargin=dp(8);box.addView(h,hp);last=category;
      }
      LinearLayout row=pluginRow(p.name,p.description,p.sourceRepo+" · "+p.tool);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(68));lp.bottomMargin=dp(6);box.addView(row,lp);row.setOnClickListener(v->runExternalPlugin(p));
    }
    ScrollView sv=new ScrollView(this);sv.addView(box);new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Open-source Add-ons · "+externalPluginRegistry.all().size()).setView(sv).setNegativeButton("Close",null).show();
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
    TextView extTitle=new TextView(this);extTitle.setText("EXTERNAL OPEN SOURCE · "+externalPluginRegistry.all().size());extTitle.setTextColor(MUTED);extTitle.setTextSize(10);extTitle.setLetterSpacing(.14f);box.addView(extTitle,new LinearLayout.LayoutParams(-1,dp(34)));
    for(StudioExternalPluginRegistry.Plugin p:externalPluginRegistry.all()){
      LinearLayout row=pluginRow(p.name,p.description,p.sourceRepo+" · "+p.tool);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(68));lp.bottomMargin=dp(6);box.addView(row,lp);
      row.setOnClickListener(v->runExternalPlugin(p));
    }
    ScrollView sv=new ScrollView(this);sv.addView(box);new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Studio Plugins · "+(pluginRegistry.all().size()+externalPluginRegistry.all().size())).setView(sv).setNegativeButton("Close",null).show();
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
    if(node==null||node.sourcePath==null||node.sourcePath.isEmpty()){Toast.makeText(this,"Select an imported asset first",Toast.LENGTH_SHORT).show();return;}
    boolean accepts=p.accepts.equals(node.type)||(p.accepts.equals("*model")&&!node.type.equals("image")&&!node.type.equals("sidecar"));
    if(!accepts){Toast.makeText(this,p.name+" expects "+p.accepts+" · selected "+node.type,Toast.LENGTH_LONG).show();return;}
    Toast.makeText(this,"Running "+p.name+"…",Toast.LENGTH_SHORT).show();
    if(p.id.equals("xatlas-auto-uv")){runXatlasPlugin(p,node);return;}
    if(p.processMode>0){runAssimpProcessPlugin(p,node);return;}
    externalExecutor.execute(()->{
      String result=externalPluginRegistry.run(p,node);
      runOnUiThread(()->new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(p.name).setMessage(result).setPositiveButton("Done",null).show());
    });
  }
  void runXatlasPlugin(StudioExternalPluginRegistry.Plugin p,StudioScene.Node node){
    externalExecutor.execute(()->{
      try{
        File dir=new File(getFilesDir(),"studio/processed");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("Cannot create processed output directory");
        String safe=node.name.replaceAll("[^A-Za-z0-9._-]","_");int dot=safe.lastIndexOf('.');if(dot>0)safe=safe.substring(0,dot);
        File out=new File(dir,System.currentTimeMillis()+"-"+safe+"-xatlas.obj");
        String result=StudioOpenSourceTools.xatlasUvObj(node.sourcePath,out.getAbsolutePath());
        JSONObject j=new JSONObject(result);
        if(!j.optBoolean("ok"))throw new java.io.IOException(j.optString("error","xatlas failed"));
        runOnUiThread(()->{
          StudioScene.Node created=scene.addImported(out.getName(),"obj",out.getAbsolutePath());objects.add(created.name);viewport.invalidate();runExternalImportExtensions(created);
          new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(p.name).setMessage("Created UV-unwrapped OBJ.\n\n"+result).setPositiveButton("Done",null).show();
        });
      }catch(Throwable t){runOnUiThread(()->Toast.makeText(this,"xatlas failed: "+t.getMessage(),Toast.LENGTH_LONG).show());}
    });
  }

  void runAssimpProcessPlugin(StudioExternalPluginRegistry.Plugin p,StudioScene.Node node){
    externalExecutor.execute(()->{
      try{
        File dir=new File(getFilesDir(),"studio/processed");if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("Cannot create processed output directory");
        String safe=node.name.replaceAll("[^A-Za-z0-9._-]","_");
        int dot=safe.lastIndexOf('.');if(dot>0)safe=safe.substring(0,dot);
        File out=new File(dir,System.currentTimeMillis()+"-"+safe+"-"+p.id+".glb");
        String result=StudioOpenSourceTools.assimpProcess(node.sourcePath,out.getAbsolutePath(),p.processMode);
        JSONObject j=new JSONObject(result);
        if(j.optBoolean("ok")){
          runOnUiThread(()->{
            StudioScene.Node created=scene.addImported(out.getName(),"gltf",out.getAbsolutePath());
            objects.add(created.name);viewport.invalidate();runExternalImportExtensions(created);
            new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(p.name).setMessage("Created a processed GLB copy.\n\n"+result).setPositiveButton("Done",null).show();
          });
        }else runOnUiThread(()->new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(p.name+" failed").setMessage(result).setPositiveButton("Done",null).show());
      }catch(Throwable t){runOnUiThread(()->Toast.makeText(this,"Process failed: "+t.getMessage(),Toast.LENGTH_LONG).show());}
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
      else viewport.resetCamera();
    }).show();
  }
  void select(String s){selection.setText(s+"  ·  Inspector");if(extensionRegistry.isEnabled("auto-inspector"))inspector.setVisibility(View.VISIBLE);}

  @Override protected void onDestroy(){super.onDestroy();externalExecutor.shutdownNow();}\n\n  @Override protected void onPause(){super.onPause();if(extensionRegistry!=null&&extensionRegistry.isEnabled("autosave-background")&&projectStore!=null&&scene!=null){try{projectStore.save("Scene_1_autosave",scene.snapshot());}catch(Exception ignored){}}}

  final class StudioViewport extends View {
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);
    final LinkedHashMap<Long,RectF> hitRects=new LinkedHashMap<>();
    float yaw=StudioMath3D.radians(-35f),pitch=StudioMath3D.radians(27f),distance=13f;
    float targetX=0f,targetY=.8f,targetZ=0f;
    String tool="Select";
    float lastX,lastY,lastSpan; boolean moved;
    StudioMath3D.Camera camera;

    final class Face {
      int[] idx; float depth; int color;
      Face(int[] idx,float depth,int color){this.idx=idx;this.depth=depth;this.color=color;}
    }

    StudioViewport(Context c){super(c);line.setStrokeWidth(dp(1));setBackgroundColor(0xff12161b);}

    void resetCamera(){yaw=StudioMath3D.radians(-35f);pitch=StudioMath3D.radians(27f);distance=13f;targetX=0;targetY=.8f;targetZ=0;invalidate();}

    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);
      int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
      camera=StudioMath3D.orbit(new StudioMath3D.Vec3(targetX,targetY,targetZ),yaw,pitch,distance,52f);

      LinearGradient sky=new LinearGradient(0,0,0,h,0xff10151a,0xff262c32,Shader.TileMode.CLAMP);
      p.setShader(sky);canvas.drawRect(0,0,w,h,p);p.setShader(null);

      drawBaseplate(canvas,w,h);
      if(extensionRegistry.isEnabled("grid-overlay"))drawGrid(canvas,w,h);
      if(extensionRegistry.isEnabled("axis-guides"))drawAxes(canvas,w,h);

      hitRects.clear();
      for(StudioScene.Node n:scene.all()){
        if(!n.visible||n.type.equals("plane"))continue;
        if(n.type.equals("spawn")){
          if(extensionRegistry.isEnabled("spawn-marker"))drawSpawn3D(canvas,n,w,h);
        }else if(n.type.equals("camera"))drawMarkerBox(canvas,n,w,h,0xffd8c46c,.35f,.35f,.55f);
        else if(n.type.equals("light"))drawMarkerBox(canvas,n,w,h,0xffffe8a3,.3f,.3f,.3f);
        else if(n.type.equals("empty"))drawGizmo(canvas,n,w,h);
        else if(extensionRegistry.isEnabled("primitive-preview"))drawNodeBox(canvas,n,w,h);
      }

      if(extensionRegistry.isEnabled("viewport-hud")){
        p.setColor(0xd20a0a0a);canvas.drawRoundRect(dp(12),dp(66),dp(250),dp(110),dp(10),dp(10),p);
        p.setColor(WHITE);p.setTextSize(dp(12));canvas.drawText("Perspective · "+tool,dp(24),dp(86),p);
        p.setColor(0xff9b9b9b);p.setTextSize(dp(10));canvas.drawText("FOV 52° · "+String.format(Locale.US,"%.1f",distance)+" m",dp(24),dp(102),p);
      }
    }

    boolean project(StudioMath3D.Vec3 world,float[] out,int w,int h){return StudioMath3D.project(world,camera,w,h,out);}

    void drawBaseplate(Canvas c,int w,int h){
      StudioMath3D.Vec3[] q={new StudioMath3D.Vec3(-9,-.025f,-9),new StudioMath3D.Vec3(9,-.025f,-9),new StudioMath3D.Vec3(9,-.025f,9),new StudioMath3D.Vec3(-9,-.025f,9)};
      float[][] s=new float[4][3];for(int i=0;i<4;i++)if(!project(q[i],s[i],w,h))return;
      Path path=new Path();path.moveTo(s[0][0],s[0][1]);for(int i=1;i<4;i++)path.lineTo(s[i][0],s[i][1]);path.close();
      p.setColor(0xff242a30);c.drawPath(path,p);
    }

    void drawGrid(Canvas c,int w,int h){
      line.setStrokeWidth(dp(.7f));line.setColor(0xff3b4249);
      final int r=10;
      float[] a=new float[3],b=new float[3];
      for(int x=-r;x<=r;x++){
        for(int z=-r;z<r;z++){
          if(project(new StudioMath3D.Vec3(x,0,z),a,w,h)&&project(new StudioMath3D.Vec3(x,0,z+1),b,w,h))c.drawLine(a[0],a[1],b[0],b[1],line);
        }
      }
      for(int z=-r;z<=r;z++){
        for(int x=-r;x<r;x++){
          if(project(new StudioMath3D.Vec3(x,0,z),a,w,h)&&project(new StudioMath3D.Vec3(x+1,0,z),b,w,h))c.drawLine(a[0],a[1],b[0],b[1],line);
        }
      }
    }

    void drawAxes(Canvas c,int w,int h){
      drawWorldLine(c,new StudioMath3D.Vec3(-10,.012f,0),new StudioMath3D.Vec3(10,.012f,0),0xffa75454,w,h,dp(1.4f));
      drawWorldLine(c,new StudioMath3D.Vec3(0,.012f,-10),new StudioMath3D.Vec3(0,.012f,10),0xff507ca0,w,h,dp(1.4f));
      drawWorldLine(c,new StudioMath3D.Vec3(0,0,0),new StudioMath3D.Vec3(0,4,0),0xff63a66d,w,h,dp(1.4f));
    }

    void drawWorldLine(Canvas c,StudioMath3D.Vec3 a,StudioMath3D.Vec3 b,int color,int w,int h,float stroke){
      float[] pa=new float[3],pb=new float[3];if(!project(a,pa,w,h)||!project(b,pb,w,h))return;
      line.setColor(color);line.setStrokeWidth(stroke);c.drawLine(pa[0],pa[1],pb[0],pb[1],line);
    }

    StudioMath3D.Vec3[] boxCorners(StudioScene.Node n,float sx,float sy,float sz){
      StudioMath3D.Vec3[] local={
        new StudioMath3D.Vec3(-sx,-sy,-sz),new StudioMath3D.Vec3(sx,-sy,-sz),
        new StudioMath3D.Vec3(sx,sy,-sz),new StudioMath3D.Vec3(-sx,sy,-sz),
        new StudioMath3D.Vec3(-sx,-sy,sz),new StudioMath3D.Vec3(sx,-sy,sz),
        new StudioMath3D.Vec3(sx,sy,sz),new StudioMath3D.Vec3(-sx,sy,sz)};
      StudioMath3D.Vec3[] world=new StudioMath3D.Vec3[8];
      for(int i=0;i<8;i++)world[i]=StudioMath3D.transformPoint(local[i],n.x,n.y,n.z,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz);
      return world;
    }

    void drawNodeBox(Canvas c,StudioScene.Node n,int w,int h){drawBox(c,n,w,h,0xff73879a,.5f,.5f,.5f);}
    void drawMarkerBox(Canvas c,StudioScene.Node n,int w,int h,int color,float sx,float sy,float sz){drawBox(c,n,w,h,color,sx,sy,sz);}

    void drawBox(Canvas c,StudioScene.Node n,int w,int h,int base,float sx,float sy,float sz){
      StudioMath3D.Vec3[] world=boxCorners(n,sx,sy,sz);
      float[][] s=new float[8][3];boolean[] visible=new boolean[8];
      float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
      for(int i=0;i<8;i++){visible[i]=project(world[i],s[i],w,h);if(visible[i]){minX=Math.min(minX,s[i][0]);minY=Math.min(minY,s[i][1]);maxX=Math.max(maxX,s[i][0]);maxY=Math.max(maxY,s[i][1]);}}
      int[][] fi={{0,1,2,3},{4,5,6,7},{0,4,7,3},{1,5,6,2},{0,1,5,4},{3,2,6,7}};
      int[] shade={0xff5f7181,0xff8094a6,0xff65798b,0xff526676,0xff485966,0xff91a4b4};
      ArrayList<Face> faces=new ArrayList<>();
      for(int k=0;k<fi.length;k++){
        boolean ok=true;float depth=0;for(int idx:fi[k]){ok&=visible[idx];depth+=s[idx][2];}
        if(ok)faces.add(new Face(fi[k],depth/4f,shade[k]));
      }
      Collections.sort(faces,(a,b)->Float.compare(b.depth,a.depth));
      for(Face face:faces){
        Path path=new Path();path.moveTo(s[face.idx[0]][0],s[face.idx[0]][1]);for(int j=1;j<face.idx.length;j++)path.lineTo(s[face.idx[j]][0],s[face.idx[j]][1]);path.close();
        p.setColor(blendColor(face.color,base,.45f));c.drawPath(path,p);line.setColor(0xff27323a);line.setStrokeWidth(dp(.8f));c.drawPath(path,line);
      }
      if(minX!=Float.MAX_VALUE){
        RectF rect=new RectF(minX-dp(8),minY-dp(8),maxX+dp(8),maxY+dp(8));hitRects.put(n.id,rect);
        StudioScene.Node selected=scene.selected();
        if(selected!=null&&selected.id==n.id){
          line.setColor(0xfff0f0f0);line.setStrokeWidth(dp(1.7f));c.drawRect(rect,line);drawGizmo(c,n,w,h);
        }
      }
    }

    int blendColor(int a,int b,float t){
      int ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255,br=(b>>16)&255,bg=(b>>8)&255,bb=b&255;
      int r=(int)(ar*(1-t)+br*t),g=(int)(ag*(1-t)+bg*t),bl=(int)(ab*(1-t)+bb*t);
      return 0xff000000|(r<<16)|(g<<8)|bl;
    }

    void drawGizmo(Canvas c,StudioScene.Node n,int w,int h){
      StudioMath3D.Vec3 o=new StudioMath3D.Vec3(n.x,n.y,n.z);
      drawWorldLine(c,o,new StudioMath3D.Vec3(n.x+1.25f,n.y,n.z),0xffe05a5a,w,h,dp(2));
      drawWorldLine(c,o,new StudioMath3D.Vec3(n.x,n.y+1.25f,n.z),0xff63c173,w,h,dp(2));
      drawWorldLine(c,o,new StudioMath3D.Vec3(n.x,n.y,n.z+1.25f),0xff5f91e8,w,h,dp(2));
    }

    void drawSpawn3D(Canvas c,StudioScene.Node n,int w,int h){
      StudioScene.Node fake=new StudioScene.Node(n.id,n.name,n.type);fake.x=n.x;fake.y=.08f+n.y;fake.z=n.z;fake.rx=n.rx;fake.ry=n.ry;fake.rz=n.rz;fake.sx=n.sx;fake.sy=n.sy;fake.sz=n.sz;
      drawBox(c,fake,w,h,0xffd5d7da,1.45f,.08f,.8f);
      float[] center=new float[3],tip=new float[3];
      StudioMath3D.Vec3 wc=new StudioMath3D.Vec3(n.x,n.y+.18f,n.z);
      if(project(wc,center,w,h)){
        line.setColor(0xff202327);line.setStrokeWidth(dp(2));
        for(int i=0;i<8;i++){double a=i*Math.PI/4;StudioMath3D.Vec3 wt=new StudioMath3D.Vec3(n.x+(float)Math.cos(a)*.65f,n.y+.18f,n.z+(float)Math.sin(a)*.65f);if(project(wt,tip,w,h))c.drawLine(center[0],center[1],tip[0],tip[1],line);}
      }
    }

    void addPrimitive(String s){invalidate();Toast.makeText(OceanStudio3DActivity.this,s+" added to scene",Toast.LENGTH_SHORT).show();}

    float span(MotionEvent e){if(e.getPointerCount()<2)return 0;float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return (float)Math.sqrt(dx*dx+dy*dy);}

    @Override public boolean onTouchEvent(MotionEvent e){
      int action=e.getActionMasked();
      if(action==MotionEvent.ACTION_DOWN){
        lastX=e.getX();lastY=e.getY();lastSpan=0;moved=false;return true;
      }
      if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()>=2){
        lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;lastSpan=span(e);moved=true;return true;
      }
      if(action==MotionEvent.ACTION_MOVE){
        if(e.getPointerCount()>=2){
          float mx=(e.getX(0)+e.getX(1))*.5f,my=(e.getY(0)+e.getY(1))*.5f,dx=mx-lastX,dy=my-lastY;
          if(extensionRegistry.isEnabled("multitouch-pan")){
            StudioMath3D.Vec3 right=camera.right,up=camera.up;float scale=distance*.0018f;
            targetX-=right.x*dx*scale;targetY+=up.y*dy*scale;targetZ-=right.z*dx*scale;
          }
          float now=span(e);if(lastSpan>1&&now>1){distance=StudioMath3D.clamp(distance*(lastSpan/now),2f,60f);}lastSpan=now;lastX=mx;lastY=my;moved=true;invalidate();return true;
        }
        float dx=e.getX()-lastX,dy=e.getY()-lastY;
        if(Math.abs(dx)+Math.abs(dy)>dp(2))moved=true;
        if(extensionRegistry.isEnabled("touch-orbit")){
          yaw-=dx*.007f;pitch=StudioMath3D.clamp(pitch-dy*.0055f,StudioMath3D.radians(-80f),StudioMath3D.radians(80f));
        }
        lastX=e.getX();lastY=e.getY();invalidate();return true;
      }
      if(action==MotionEvent.ACTION_UP){
        if(!moved){
          ArrayList<Map.Entry<Long,RectF>> entries=new ArrayList<>(hitRects.entrySet());
          for(int i=entries.size()-1;i>=0;i--){
            Map.Entry<Long,RectF> entry=entries.get(i);
            if(entry.getValue().contains(e.getX(),e.getY())){
              scene.select(entry.getKey());StudioScene.Node n=scene.selected();if(n!=null)select(n.name);invalidate();return true;
            }
          }
        }
        return true;
      }
      return true;
    }
  }
}