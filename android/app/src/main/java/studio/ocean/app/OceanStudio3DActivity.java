package studio.ocean.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

public final class OceanStudio3DActivity extends Activity {
  static final int BLACK=0xff050505, PANEL=0xee101010, BORDER=0xff303030, WHITE=0xfff3f3f3, MUTED=0xff9a9a9a;
  FrameLayout root; StudioViewport viewport; StudioGpuViewport gpuViewport; boolean gpuActive;
  LinearLayout bottom, inspector; TextView selection, inspectorTitle;
  TextView posXText, posYText, posZText, rotYText, scaleText, physicsToggleBtn;
  final ArrayList<String> history=new ArrayList<>(); final ArrayList<String> objects=new ArrayList<>();
  StudioScene scene; StudioExtensionRegistry extensionRegistry; StudioProjectStore projectStore;
  StudioPluginRegistry pluginRegistry; StudioExternalPluginRegistry externalPluginRegistry;
  StudioExternalExtensionRegistry externalExtensionRegistry; StudioEngineTargetRegistry engineTargetRegistry;
  StudioQuickToolRegistry quickToolRegistry; StudioRenderSettings renderSettings;
  final ExecutorService externalExecutor=Executors.newSingleThreadExecutor();
  StudioScene.Node pendingExportNode; String pendingExportFormatId="",pendingExportExt="",pendingExportDataExt="";
  boolean pendingExportZip; Set<String> assimpImportExtCache;
  static final int REQ_IMPORT=771,REQ_EXPORT=772;

  boolean physicsRunning=false;
  final Handler physicsHandler=new Handler(Looper.getMainLooper());
  final Runnable physicsRunnable=new Runnable() {
    @Override public void run() {
      if(physicsRunning) {
        scene.stepPhysics(0.016f);
        if(viewport!=null) viewport.invalidate();
        updateInspectorValues();
        physicsHandler.postDelayed(this, 16);
      }
    }
  };

  int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
  GradientDrawable bg(int c,float r,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
  TextView button(String s){TextView v=new TextView(this);v.setText(s);v.setTextColor(WHITE);v.setTextSize(12);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),0,dp(10),0);v.setBackground(bg(PANEL,12,BORDER));return v;}

  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    getWindow().setStatusBarColor(BLACK);
    getWindow().setNavigationBarColor(BLACK);
    scene=new StudioScene();
    extensionRegistry=new StudioExtensionRegistry(this);
    projectStore=new StudioProjectStore(this);
    pluginRegistry=new StudioPluginRegistry();
    externalPluginRegistry=new StudioExternalPluginRegistry();
    externalExtensionRegistry=new StudioExternalExtensionRegistry(this);
    engineTargetRegistry=new StudioEngineTargetRegistry(this);
    quickToolRegistry=new StudioQuickToolRegistry();
    renderSettings=new StudioRenderSettings(this);
    gpuActive=supportsGles3();
    applySystemBars();
    objects.add("Baseplate");
    objects.add("Spawn");
    build();
  }

  boolean supportsGles3(){
    try{
      ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
      ConfigurationInfo info=am==null?null:am.getDeviceConfigurationInfo();
      return info!=null&&info.reqGlEsVersion>=0x30000;
    }catch(Throwable ignored){return false;}
  }

  void applySystemBars(){
    boolean dark=extensionRegistry!=null&&extensionRegistry.isEnabled("dark-system-bars");
    getWindow().setStatusBarColor(dark?BLACK:0xff202020);
    getWindow().setNavigationBarColor(dark?BLACK:0xff202020);
  }

  void build(){
    root=new FrameLayout(this);
    root.setBackgroundColor(BLACK);
    setContentView(root);
    if(gpuActive){
      try{gpuViewport=new StudioGpuViewport(this);root.addView(gpuViewport,new FrameLayout.LayoutParams(-1,-1));}
      catch(Throwable t){gpuActive=false;gpuViewport=null;}
    }
    viewport=new StudioViewport(this);
    root.addView(viewport,new FrameLayout.LayoutParams(-1,-1));

    // Sleek top control bar
    LinearLayout top=new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(10),dp(6),dp(10),dp(6));
    top.setBackgroundColor(0xcc070707);
    FrameLayout.LayoutParams tl=new FrameLayout.LayoutParams(-1,dp(52),Gravity.TOP);
    root.addView(top,tl);

    TextView ai=button("✦");ai.setTextSize(18);ai.setContentDescription("Ocean AI");ai.setOnClickListener(v->showAI());
    top.addView(ai,new LinearLayout.LayoutParams(dp(38),dp(38)));

    TextView name=new TextView(this);name.setText("  OCEAN 3D STUDIO");name.setTextColor(WHITE);name.setTextSize(12);name.setTypeface(null,Typeface.BOLD);
    top.addView(name,new LinearLayout.LayoutParams(0,-1,1));

    // Direct workflow action buttons on top bar
    String[] topBtns={"↶","↷","▶ Sim","⌖ Frame","☁ Sky","▦ Grid","＋ Add","⋮"};
    for(String s:topBtns){
      TextView b=button(s);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(36));
      lp.leftMargin=dp(4);
      top.addView(b,lp);
      b.setOnClickListener(v->handleTopAction(s));
    }

    buildBottomBar();
    buildInspectorPanel();

    TextView hint=new TextView(this);
    hint.setText("Touch & Drag object to move  •  Swipe background to orbit  •  Pinch zoom");
    hint.setVisibility(extensionRegistry.isEnabled("gesture-hints")?View.VISIBLE:View.GONE);
    hint.setTextColor(0xffd0d4d8);
    hint.setTextSize(10);
    hint.setGravity(Gravity.CENTER);
    hint.setPadding(dp(10),dp(3),dp(10),dp(3));
    hint.setBackground(bg(0xbb000000,8,0x44ffffff));
    FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
    hp.bottomMargin=dp(62);
    root.addView(hint,hp);
  }

  void buildBottomBar(){
    bottom=new LinearLayout(this);
    bottom.setGravity(Gravity.CENTER_VERTICAL);
    bottom.setPadding(dp(10),dp(6),dp(10),dp(6));
    bottom.setBackgroundColor(0xdd080808);

    // Primary mobile 3D tools
    String[] tools={"✥ Move","⟳ Rotate","⤢ Scale","❐ Duplicate","🗑 Delete","⚙ Inspector","📥 Import","📤 Export","Settings"};
    for(String s:tools){
      TextView b=button(s);
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));
      lp.rightMargin=dp(6);
      bottom.addView(b,lp);
      b.setOnClickListener(v->toolAction(s));
    }
    HorizontalScrollView hs=new HorizontalScrollView(this);
    hs.setHorizontalScrollBarEnabled(false);
    hs.addView(bottom);
    FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,dp(52),Gravity.BOTTOM);
    root.addView(hs,bp);
  }

  void buildInspectorPanel(){
    inspector=new LinearLayout(this);
    inspector.setOrientation(LinearLayout.VERTICAL);
    inspector.setPadding(dp(12),dp(10),dp(12),dp(10));
    inspector.setBackground(bg(0xf0111417,14,BORDER));
    inspector.setVisibility(View.GONE);

    inspectorTitle=new TextView(this);
    inspectorTitle.setText("Object Inspector");
    inspectorTitle.setTextColor(WHITE);
    inspectorTitle.setTextSize(13);
    inspectorTitle.setTypeface(null,Typeface.BOLD);
    inspector.addView(inspectorTitle,new LinearLayout.LayoutParams(-1,dp(28)));

    // X, Y, Z controls
    inspector.addView(createAxisRow("X", (delta)->{scene.offsetSelected(delta,0,0);viewport.invalidate();updateInspectorValues();}));
    inspector.addView(createAxisRow("Y", (delta)->{scene.offsetSelected(0,delta,0);viewport.invalidate();updateInspectorValues();}));
    inspector.addView(createAxisRow("Z", (delta)->{scene.offsetSelected(0,0,delta);viewport.invalidate();updateInspectorValues();}));

    // Rotate & Scale rows
    inspector.addView(createRotateRow());
    inspector.addView(createScaleRow());

    // Physics dynamic toggle
    physicsToggleBtn=button("Physics: Static");
    physicsToggleBtn.setOnClickListener(v->{
      scene.toggleSelectedPhysics();
      updateInspectorValues();
    });
    LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(-1,dp(34));
    plp.topMargin=dp(6);
    inspector.addView(physicsToggleBtn,plp);

    // Material Color Tint Chips
    TextView matLabel=new TextView(this);
    matLabel.setText("Material Tint");
    matLabel.setTextColor(MUTED);
    matLabel.setTextSize(10);
    matLabel.setPadding(0,dp(6),0,dp(4));
    inspector.addView(matLabel);

    LinearLayout colorRow=new LinearLayout(this);
    colorRow.setOrientation(LinearLayout.HORIZONTAL);
    int[] colors={0xffe54d42, 0xff39b54a, 0xff0081ff, 0xfffbbd08, 0xff6739b6, 0xffffffff, 0xff73879a};
    for(int col:colors){
      View chip=new View(this);
      chip.setBackground(bg(col, 6, 0xffffffff));
      LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(dp(26),dp(26));
      clp.rightMargin=dp(6);
      colorRow.addView(chip,clp);
      chip.setOnClickListener(v->{
        scene.setSelectedTint(col);
        viewport.invalidate();
      });
    }
    inspector.addView(colorRow);

    // Quick align buttons
    LinearLayout alignRow=new LinearLayout(this);
    alignRow.setOrientation(LinearLayout.HORIZONTAL);
    alignRow.setPadding(0,dp(8),0,dp(6));
    TextView btnGround=button("Ground Y=0");
    btnGround.setOnClickListener(v->{scene.groundSelected();viewport.invalidate();updateInspectorValues();});
    LinearLayout.LayoutParams alp1=new LinearLayout.LayoutParams(0,dp(34),1);
    alp1.rightMargin=dp(4);
    alignRow.addView(btnGround,alp1);

    TextView btnCenter=button("Center XZ");
    btnCenter.setOnClickListener(v->{scene.centerSelectedXZ();viewport.invalidate();updateInspectorValues();});
    LinearLayout.LayoutParams alp2=new LinearLayout.LayoutParams(0,dp(34),1);
    alignRow.addView(btnCenter,alp2);
    inspector.addView(alignRow);

    TextView close=button("Close");
    close.setOnClickListener(v->inspector.setVisibility(View.GONE));
    inspector.addView(close,new LinearLayout.LayoutParams(-1,dp(34)));

    FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(250),-2,Gravity.TOP|Gravity.LEFT);
    ip.topMargin=dp(58);
    ip.leftMargin=dp(8);
    root.addView(inspector,ip);
  }

  interface DeltaCallback { void onDelta(float delta); }

  LinearLayout createAxisRow(String axis, DeltaCallback cb){
    LinearLayout row=new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0,dp(2),0,dp(2));

    TextView label=new TextView(this);
    label.setText("Pos "+axis+":");
    label.setTextColor(WHITE);
    label.setTextSize(11);
    row.addView(label,new LinearLayout.LayoutParams(dp(44),-2));

    TextView val=new TextView(this);
    val.setTextColor(0xff9ec7f5);
    val.setTextSize(11);
    val.setGravity(Gravity.CENTER);
    if("X".equals(axis)) posXText=val;
    else if("Y".equals(axis)) posYText=val;
    else posZText=val;
    row.addView(val,new LinearLayout.LayoutParams(0,-2,1));

    TextView minus=button(" - ");
    minus.setOnClickListener(v->cb.onDelta(-0.25f));
    row.addView(minus,new LinearLayout.LayoutParams(dp(36),dp(30)));

    TextView plus=button(" + ");
    LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(dp(36),dp(30));
    plp.leftMargin=dp(4);
    plus.setOnClickListener(v->cb.onDelta(0.25f));
    row.addView(plus,plp);
    return row;
  }

  LinearLayout createRotateRow(){
    LinearLayout row=new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0,dp(2),0,dp(2));
    TextView label=new TextView(this);
    label.setText("Rot Y:");
    label.setTextColor(WHITE);
    label.setTextSize(11);
    row.addView(label,new LinearLayout.LayoutParams(dp(44),-2));
    rotYText=new TextView(this);
    rotYText.setTextColor(0xff9ec7f5);
    rotYText.setTextSize(11);
    rotYText.setGravity(Gravity.CENTER);
    row.addView(rotYText,new LinearLayout.LayoutParams(0,-2,1));
    TextView minus=button(" - ");
    minus.setOnClickListener(v->{scene.rotateSelected(0,-15,0);scene.normalizeSelectedAngles();viewport.invalidate();updateInspectorValues();});
    row.addView(minus,new LinearLayout.LayoutParams(dp(36),dp(30)));
    TextView plus=button(" + ");
    LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(dp(36),dp(30));
    plp.leftMargin=dp(4);
    plus.setOnClickListener(v->{scene.rotateSelected(0,15,0);scene.normalizeSelectedAngles();viewport.invalidate();updateInspectorValues();});
    row.addView(plus,plp);
    return row;
  }

  LinearLayout createScaleRow(){
    LinearLayout row=new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0,dp(2),0,dp(2));
    TextView label=new TextView(this);
    label.setText("Scale:");
    label.setTextColor(WHITE);
    label.setTextSize(11);
    row.addView(label,new LinearLayout.LayoutParams(dp(44),-2));
    scaleText=new TextView(this);
    scaleText.setTextColor(0xff9ec7f5);
    scaleText.setTextSize(11);
    scaleText.setGravity(Gravity.CENTER);
    row.addView(scaleText,new LinearLayout.LayoutParams(0,-2,1));
    TextView minus=button(" - ");
    minus.setOnClickListener(v->{scene.scaleSelected(0.85f);viewport.invalidate();updateInspectorValues();});
    row.addView(minus,new LinearLayout.LayoutParams(dp(36),dp(30)));
    TextView plus=button(" + ");
    LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(dp(36),dp(30));
    plp.leftMargin=dp(4);
    plus.setOnClickListener(v->{scene.scaleSelected(1.15f);viewport.invalidate();updateInspectorValues();});
    row.addView(plus,plp);
    return row;
  }

  void updateInspectorValues(){
    StudioScene.Node n=scene.selected();
    if(n==null){
      if(inspector!=null) inspector.setVisibility(View.GONE);
      return;
    }
    if(inspectorTitle!=null) inspectorTitle.setText(n.name+" ("+n.type+")");
    if(posXText!=null) posXText.setText(String.format(Locale.US,"%.2f",n.x));
    if(posYText!=null) posYText.setText(String.format(Locale.US,"%.2f",n.y));
    if(posZText!=null) posZText.setText(String.format(Locale.US,"%.2f",n.z));
    if(rotYText!=null) rotYText.setText(String.format(Locale.US,"%.0f°",n.ry));
    if(scaleText!=null) scaleText.setText(String.format(Locale.US,"%.2f",n.sx));
    if(physicsToggleBtn!=null) {
      physicsToggleBtn.setText(n.isDynamic?"Physics: Dynamic (Drops/Bounces)":"Physics: Static (Fixed)");
      physicsToggleBtn.setTextColor(n.isDynamic?0xff9ad7b1:WHITE);
    }
  }

  void handleTopAction(String s){
    if(s.equals("↶")){scene.undo();viewport.invalidate();updateInspectorValues();}
    else if(s.equals("↷")){scene.redo();viewport.invalidate();updateInspectorValues();}
    else if(s.startsWith("▶")||s.startsWith("⏸")){
      physicsRunning=!physicsRunning;
      if(physicsRunning){
        physicsHandler.post(physicsRunnable);
        Toast.makeText(this,"Physics simulation: RUNNING",Toast.LENGTH_SHORT).show();
      }else{
        physicsHandler.removeCallbacks(physicsRunnable);
        Toast.makeText(this,"Physics simulation: PAUSED",Toast.LENGTH_SHORT).show();
      }
      for(int i=0;i<root.getChildCount();i++){
        View v=root.getChildAt(i);
        if(v instanceof LinearLayout){
          LinearLayout top=(LinearLayout)v;
          for(int j=0;j<top.getChildCount();j++){
            View btn=top.getChildAt(j);
            if(btn instanceof TextView && (((TextView)btn).getText().toString().contains("Sim")||((TextView)btn).getText().toString().contains("Pause"))){
              ((TextView)btn).setText(physicsRunning?"⏸ Pause":"▶ Sim");
              ((TextView)btn).setTextColor(physicsRunning?0xff9ad7b1:WHITE);
            }
          }
        }
      }
    }
    else if(s.equals("⌖ Frame")){
      StudioScene.Node n=scene.selected();
      if(n!=null){viewport.targetX=n.x;viewport.targetY=n.y;viewport.targetZ=n.z;viewport.distance=8f;}
      else{viewport.targetX=0;viewport.targetY=.8f;viewport.targetZ=0;viewport.distance=13f;}
      viewport.invalidate();
    }
    else if(s.equals("☁ Sky")){
      viewport.skyVisible=!viewport.skyVisible;
      viewport.invalidate();
      Toast.makeText(this,"Sky background: "+(viewport.skyVisible?"ON":"OFF"),Toast.LENGTH_SHORT).show();
    }
    else if(s.equals("▦ Grid")){
      boolean on=!extensionRegistry.isEnabled("grid-overlay");
      extensionRegistry.setEnabled("grid-overlay",on);
      viewport.invalidate();
      Toast.makeText(this,"Grid: "+(on?"ON":"OFF"),Toast.LENGTH_SHORT).show();
    }
    else if(s.equals("＋ Add")){
      showCatalog("Add Object",new String[]{"Cube","Sphere","Cylinder","Plane","Cone","Light","Camera","Spawn","Empty"});
    }
    else if(s.equals("⋮")){
      showProjectMenu();
    }
  }

  void toolAction(String s){
    if(s.equals("✥ Move")){viewport.tool="Move";Toast.makeText(this,"Move tool: Drag object on ground plane or gizmo arrows",Toast.LENGTH_SHORT).show();}
    else if(s.equals("⟳ Rotate")){viewport.tool="Rotate";Toast.makeText(this,"Rotate tool: Drag on object to rotate",Toast.LENGTH_SHORT).show();}
    else if(s.equals("⤢ Scale")){viewport.tool="Scale";Toast.makeText(this,"Scale tool: Drag vertically to scale",Toast.LENGTH_SHORT).show();}
    else if(s.equals("❐ Duplicate")){
      StudioScene.Node q=scene.duplicateSelected();
      if(q!=null){objects.add(q.name);select(q.name);viewport.invalidate();Toast.makeText(this,"Duplicated "+q.name,Toast.LENGTH_SHORT).show();}
      else Toast.makeText(this,"Select an object first",Toast.LENGTH_SHORT).show();
    }
    else if(s.equals("🗑 Delete")){
      StudioScene.Node n=scene.selected();
      if(n!=null){
        String name=n.name;
        scene.deleteSelected();
        viewport.invalidate();
        updateInspectorValues();
        Toast.makeText(this,"Deleted "+name,Toast.LENGTH_SHORT).show();
      }else Toast.makeText(this,"Select an object first",Toast.LENGTH_SHORT).show();
    }
    else if(s.equals("⚙ Inspector")){
      inspector.setVisibility(inspector.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
      updateInspectorValues();
    }
    else if(s.equals("📥 Import")){openImporter();}
    else if(s.equals("📤 Export")){showExportCenter();}
    else if(s.equals("Settings")){showStudioSettings();}
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
        String type=classifyImportedType(low);
        if(type.equals("sidecar"))continue;
        StudioScene.Node node=scene.addImported(name,type,locals.get(i).getAbsolutePath());
        objects.add(name);sceneNodes++;runExternalImportExtensions(node);
      }
      viewport.invalidate();
      Toast.makeText(this,"Imported "+uris.size()+" files · "+sceneNodes+" scene assets · sidecars preserved",Toast.LENGTH_SHORT).show();
    }catch(Exception ex){Toast.makeText(this,"Import failed: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
  }

  String classifyImportedType(String low){
    if(low.endsWith(".glb")||low.endsWith(".gltf"))return "gltf";
    if(low.endsWith(".obj"))return "obj";
    if(low.endsWith(".png")||low.endsWith(".jpg")||low.endsWith(".jpeg")||low.endsWith(".bmp")||low.endsWith(".tga")||low.endsWith(".hdr")||low.endsWith(".webp"))return "image";
    return isAssimpModelExtension(low)?"model":"sidecar";
  }

  boolean isAssimpModelExtension(String lowerName){
    if(assimpImportExtCache==null){
      assimpImportExtCache=new HashSet<>();
      try{
        JSONObject j=new JSONObject(StudioOpenSourceTools.assimpImportExtensions());
        String raw=j.optString("extensions","");
        for(String token:raw.split(";")){
          String x=token.trim().toLowerCase(Locale.US);
          if(x.startsWith("*."))x=x.substring(2);else if(x.startsWith("."))x=x.substring(1);
          if(!x.isEmpty())assimpImportExtCache.add(x);
        }
      }catch(Throwable ignored){}
    }
    int dot=lowerName.lastIndexOf('.');if(dot<0||dot==lowerName.length()-1)return false;
    return assimpImportExtCache.contains(lowerName.substring(dot+1));
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
          }
        }
      }catch(Throwable t){meta.append("analysis error: ").append(t.getMessage());}
      final String result=meta.toString().trim();
      runOnUiThread(()->{
        scene.setMetadata(node.id,result);
        viewport.invalidate();
      });
    });
  }

  void showStudioSettings(){
    String renderer=gpuActive?"OpenGL ES 3 GPU · depth test · MSAA":"Canvas compatibility renderer";
    String[] items={
      "Renderer: "+renderer,
      "Render quality: "+renderSettings.qualityLabel(),
      "Target engine: "+engineTargetRegistry.selected().name,
      "Extensions: "+externalExtensionRegistry.all().size()+" active"
    };
    new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Studio Settings").setItems(items,null).setNegativeButton("Close",null).show();
  }

  void showExportCenter(){
    StudioScene.Node node=scene.selected();
    if(node==null||node.sourcePath==null||node.sourcePath.isEmpty()){Toast.makeText(this,"Select an imported model to export",Toast.LENGTH_SHORT).show();return;}
    Toast.makeText(this,"Reading export formats…",Toast.LENGTH_SHORT).show();
    externalExecutor.execute(()->{
      try{
        JSONObject rootJson=new JSONObject(StudioOpenSourceTools.assimpExportFormats());
        if(!rootJson.optBoolean("ok"))throw new IllegalStateException(rootJson.optString("error","Assimp unavailable"));
        JSONArray formats=rootJson.getJSONArray("formats");
        ArrayList<JSONObject> options=new ArrayList<>();
        for(int i=0;i<formats.length();i++)options.add(formats.getJSONObject(i));
        String[] labels=new String[options.size()];
        for(int i=0;i<options.size();i++)labels[i]=options.get(i).optString("description","Format")+" (*."+options.get(i).optString("extension","bin")+")";
        runOnUiThread(()->new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Export '"+node.name+"'").setItems(labels,(d,w)->initiateExport(node,options.get(w))).setNegativeButton("Cancel",null).show());
      }catch(Throwable t){runOnUiThread(()->Toast.makeText(this,"Export error: "+t.getMessage(),Toast.LENGTH_LONG).show());}
    });
  }

  void initiateExport(StudioScene.Node node,JSONObject format){
    pendingExportNode=node;pendingExportFormatId=format.optString("id","");pendingExportExt=format.optString("extension","bin");
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/octet-stream");
    String safe=node.name.replaceAll("[^A-Za-z0-9._-]","_");
    intent.putExtra(Intent.EXTRA_TITLE,safe+"."+pendingExportExt);
    startActivityForResult(intent,REQ_EXPORT);
  }

  void performPendingExport(Uri targetUri){
    final StudioScene.Node node=pendingExportNode;
    final String formatId=pendingExportFormatId, ext=pendingExportExt;
    if(node==null||formatId.isEmpty())return;
    externalExecutor.execute(()->{
      try{
        File tempOut=new File(getFilesDir(),"export-"+System.currentTimeMillis()+"."+ext);
        String raw=StudioOpenSourceTools.assimpExport(node.sourcePath,tempOut.getAbsolutePath(),formatId);
        JSONObject j=new JSONObject(raw);
        if(!j.optBoolean("ok"))throw new java.io.IOException(j.optString("error","Export failed"));
        try(InputStream in=new FileInputStream(tempOut);OutputStream out=getContentResolver().openOutputStream(targetUri)){
          byte[] buf=new byte[16384];int len;
          while((len=in.read(buf))!=-1)out.write(buf,0,len);
        }
        tempOut.delete();
        runOnUiThread(()->Toast.makeText(this,"Exported successfully!",Toast.LENGTH_SHORT).show());
      }catch(Throwable t){runOnUiThread(()->Toast.makeText(this,"Export write failed: "+t.getMessage(),Toast.LENGTH_LONG).show());}
    });
  }

  void showCatalog(String title,String[] items){
    new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(title).setItems(items,(d,w)->{
      String type=items[w].toLowerCase(Locale.US);
      StudioScene.Node n=scene.add(items[w],type);
      n.x=0f; n.y=0.5f; n.z=0f;
      objects.add(n.name);
      select(n.name);
      viewport.invalidate();
    }).show();
  }

  void showAI(){
    final EditText input=new EditText(this);
    input.setHint("Describe what to model, place, or script…");
    input.setTextColor(WHITE);
    input.setHintTextColor(MUTED);
    new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK)
      .setTitle("Ocean 3D Intelligence")
      .setView(input)
      .setPositiveButton("Generate",(d,w)->{
        String prompt=input.getText().toString().trim();
        if(!prompt.isEmpty()){
          StudioScene.Node n=scene.add(prompt,"cube");
          n.x=(float)(Math.random()*4-2); n.y=0.5f; n.z=(float)(Math.random()*4-2);
          select(n.name);
          viewport.invalidate();
          Toast.makeText(this,"Created "+n.name+" in scene",Toast.LENGTH_SHORT).show();
        }
      })
      .setNegativeButton("Cancel",null)
      .show();
  }

  void showProjectMenu(){
    String[] items={"Save project","Project info","Reset camera"};
    new AlertDialog.Builder(this,AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Scene 1").setItems(items,(d,w)->{
      if(w==0){try{java.io.File f=projectStore.save("Scene_1",scene.snapshot());Toast.makeText(this,"Saved "+f.getName(),Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
      else if(w==1)Toast.makeText(this,scene.size()+" scene nodes · "+projectStore.list().length+" saved projects",Toast.LENGTH_LONG).show();
      else viewport.resetCamera();
    }).show();
  }

  void select(String s){
    if(selection!=null) selection.setText(s);
    updateInspectorValues();
  }

  @Override protected void onDestroy(){
    super.onDestroy();
    physicsRunning=false;
    physicsHandler.removeCallbacks(physicsRunnable);
    externalExecutor.shutdownNow();
  }

  @Override protected void onResume(){
    super.onResume();
    if(gpuActive&&gpuViewport!=null)gpuViewport.onResume();
  }

  @Override protected void onPause(){
    if(gpuActive&&gpuViewport!=null)gpuViewport.onPause();
    super.onPause();
    physicsRunning=false;
    physicsHandler.removeCallbacks(physicsRunnable);
  }

  // --- 3D VIEWPORT WITH DIRECT TOUCH MANIPULATION & CC0 SKY ---
  final class StudioViewport extends View {
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);
    final LinkedHashMap<Long,RectF> hitRects=new LinkedHashMap<>();
    float yaw=StudioMath3D.radians(-35f),pitch=StudioMath3D.radians(27f),distance=13f;
    float targetX=0f,targetY=.8f,targetZ=0f;
    String tool="Move";
    boolean skyVisible=true;
    Bitmap skyBitmap=null;
    float lastX,lastY,lastSpan; boolean moved;
    StudioMath3D.Camera camera;

    static final int DRAG_NONE=0, DRAG_CAMERA=1, DRAG_OBJECT_XZ=2, DRAG_AXIS_X=3, DRAG_AXIS_Y=4, DRAG_AXIS_Z=5, DRAG_OBJECT_ROT=6, DRAG_OBJECT_SCALE=7;
    int dragMode=DRAG_NONE;
    StudioMath3D.Vec3 dragStartHit=null, nodeStartPos=null;
    float nodeStartRotY=0f, nodeStartScale=1f, dragStartScreenX=0f, dragStartScreenY=0f;
    long draggedNodeId=-1;

    final class Face {
      int[] idx; float depth; int color;
      Face(int[] idx,float depth,int color){this.idx=idx;this.depth=depth;this.color=color;}
    }

    StudioViewport(Context c){
      super(c);
      line.setStrokeWidth(dp(1));
      setBackgroundColor(gpuActive?0x00000000:0xff12161b);
      try {
        InputStream is=c.getAssets().open("ocean/textures/sky.jpg");
        skyBitmap=BitmapFactory.decodeStream(is);
        is.close();
      } catch(Throwable ignored){}
    }

    void resetCamera(){
      yaw=StudioMath3D.radians(-35f);pitch=StudioMath3D.radians(27f);distance=13f;targetX=0;targetY=.8f;targetZ=0;invalidate();
    }

    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);
      int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
      camera=StudioMath3D.orbit(new StudioMath3D.Vec3(targetX,targetY,targetZ),yaw,pitch,distance,52f);

      if(gpuActive&&gpuViewport!=null){
        gpuViewport.updateCamera(yaw,pitch,distance,targetX,targetY,targetZ);
        gpuViewport.updateOptions(extensionRegistry.isEnabled("grid-overlay"),extensionRegistry.isEnabled("axis-guides"),extensionRegistry.isEnabled("spawn-marker"),extensionRegistry.isEnabled("primitive-preview"));
        gpuViewport.updateScene(scene.renderSnapshot());
        hitRects.clear();
        StudioScene.Node selectedNode=scene.selected();
        for(StudioScene.Node n:scene.all()){
          if(!n.visible||n.type.equals("plane"))continue;
          if(n.type.equals("spawn"))computeHitRect(n,1.45f,.08f,.8f,w,h);
          else if(n.type.equals("camera"))computeHitRect(n,.35f,.35f,.55f,w,h);
          else if(n.type.equals("light"))computeHitRect(n,.3f,.3f,.3f,w,h);
          else computeHitRect(n,.5f,.5f,.5f,w,h);
          if(selectedNode!=null&&selectedNode.id==n.id)drawGizmo(canvas,n,w,h);
        }
        return;
      }

      // Draw Sky (authentic CC0 panorama or sky gradient)
      if(skyVisible && skyBitmap!=null){
        float bmpW=skyBitmap.getWidth(), bmpH=skyBitmap.getHeight();
        float aspect=bmpW/Math.max(1f,bmpH);
        float destH=h*1.25f;
        float destW=destH*aspect;
        float normYaw=(yaw%(float)(2*Math.PI))/(float)(2*Math.PI);
        if(normYaw<0)normYaw+=1f;
        float offsetX=-normYaw*destW;
        float offsetY=(pitch/(float)Math.PI)*(h*0.4f);
        RectF d1=new RectF(offsetX,offsetY,offsetX+destW,offsetY+destH);
        canvas.drawBitmap(skyBitmap,null,d1,p);
        if(offsetX+destW<w){
          RectF d2=new RectF(offsetX+destW,offsetY,offsetX+destW*2f,offsetY+destH);
          canvas.drawBitmap(skyBitmap,null,d2,p);
        }
        if(offsetX>0){
          RectF d3=new RectF(offsetX-destW,offsetY,offsetX,offsetY+destH);
          canvas.drawBitmap(skyBitmap,null,d3,p);
        }
        LinearGradient haze=new LinearGradient(0,h*0.45f,0,h,0x00000000,0xaa202830,Shader.TileMode.CLAMP);
        p.setShader(haze);canvas.drawRect(0,h*0.45f,w,h,p);p.setShader(null);
      } else {
        LinearGradient sky=new LinearGradient(0,0,0,h,0xff10151a,0xff262c32,Shader.TileMode.CLAMP);
        p.setShader(sky);canvas.drawRect(0,0,w,h,p);p.setShader(null);
      }

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

      // Draw Selected Gizmo
      StudioScene.Node sel=scene.selected();
      if(sel!=null&&!sel.type.equals("plane"))drawGizmo(canvas,sel,w,h);
    }

    boolean project(StudioMath3D.Vec3 world,float[] out,int w,int h){return StudioMath3D.project(world,camera,w,h,out);}

    void drawBaseplate(Canvas c,int w,int h){
      StudioMath3D.Vec3[] q={
        new StudioMath3D.Vec3(-100,-.025f,-100),
        new StudioMath3D.Vec3(100,-.025f,-100),
        new StudioMath3D.Vec3(100,-.025f,100),
        new StudioMath3D.Vec3(-100,-.025f,100)
      };
      float[][] s=new float[4][3];for(int i=0;i<4;i++)if(!project(q[i],s[i],w,h))return;
      Path path=new Path();path.moveTo(s[0][0],s[0][1]);for(int i=1;i<4;i++)path.lineTo(s[i][0],s[i][1]);path.close();
      p.setColor(0xff222830);c.drawPath(path,p);
    }

    void drawGrid(Canvas c,int w,int h){
      float[] a=new float[3],b=new float[3];
      // Minor grid every 1 unit
      final int r=25;
      line.setStrokeWidth(dp(.6f));line.setColor(0x28506070);
      for(int x=-r;x<=r;x++){
        if(x%5==0)continue;
        if(project(new StudioMath3D.Vec3(x,0,-r),a,w,h)&&project(new StudioMath3D.Vec3(x,0,r),b,w,h))c.drawLine(a[0],a[1],b[0],b[1],line);
      }
      for(int z=-r;z<=r;z++){
        if(z%5==0)continue;
        if(project(new StudioMath3D.Vec3(-r,0,z),a,w,h)&&project(new StudioMath3D.Vec3(r,0,z),b,w,h))c.drawLine(a[0],a[1],b[0],b[1],line);
      }
      // Major grid every 5 units
      final int mr=45;
      line.setStrokeWidth(dp(1.1f));line.setColor(0x66687e96);
      for(int x=-mr;x<=mr;x+=5){
        if(project(new StudioMath3D.Vec3(x,0,-mr),a,w,h)&&project(new StudioMath3D.Vec3(x,0,mr),b,w,h))c.drawLine(a[0],a[1],b[0],b[1],line);
      }
      for(int z=-mr;z<=mr;z+=5){
        if(project(new StudioMath3D.Vec3(-mr,0,z),a,w,h)&&project(new StudioMath3D.Vec3(mr,0,z),b,w,h))c.drawLine(a[0],a[1],b[0],b[1],line);
      }
    }

    void drawAxes(Canvas c,int w,int h){
      drawWorldLine(c,new StudioMath3D.Vec3(-20,.012f,0),new StudioMath3D.Vec3(20,.012f,0),0xffd04444,w,h,dp(1.8f));
      drawWorldLine(c,new StudioMath3D.Vec3(0,.012f,-20),new StudioMath3D.Vec3(0,.012f,20),0xff4477d0,w,h,dp(1.8f));
      drawWorldLine(c,new StudioMath3D.Vec3(0,0,0),new StudioMath3D.Vec3(0,8,0),0xff44b055,w,h,dp(1.8f));
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

    void computeHitRect(StudioScene.Node n,float sx,float sy,float sz,int w,int h){
      StudioMath3D.Vec3[] world=boxCorners(n,sx,sy,sz);
      float[] s=new float[3];float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE;
      for(StudioMath3D.Vec3 v:world)if(project(v,s,w,h)){minX=Math.min(minX,s[0]);minY=Math.min(minY,s[1]);maxX=Math.max(maxX,s[0]);maxY=Math.max(maxY,s[1]);}
      if(minX!=Float.MAX_VALUE)hitRects.put(n.id,new RectF(minX-dp(12),minY-dp(12),maxX+dp(12),maxY+dp(12)));
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
      int activeBase=n.tintColor!=0?n.tintColor:base;
      for(Face face:faces){
        Path path=new Path();path.moveTo(s[face.idx[0]][0],s[face.idx[0]][1]);for(int j=1;j<face.idx.length;j++)path.lineTo(s[face.idx[j]][0],s[face.idx[j]][1]);path.close();
        p.setColor(blendColor(face.color,activeBase,.5f));c.drawPath(path,p);line.setColor(0xff27323a);line.setStrokeWidth(dp(.8f));c.drawPath(path,line);
      }
      if(minX!=Float.MAX_VALUE){
        RectF rect=new RectF(minX-dp(10),minY-dp(10),maxX+dp(10),maxY+dp(10));
        hitRects.put(n.id,rect);
        StudioScene.Node selected=scene.selected();
        if(selected!=null&&selected.id==n.id){
          line.setColor(0xff00d4ff);line.setStrokeWidth(dp(2f));c.drawRect(rect,line);
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
      drawWorldLine(c,o,new StudioMath3D.Vec3(n.x+1.5f,n.y,n.z),0xffe54d42,w,h,dp(3));
      drawWorldLine(c,o,new StudioMath3D.Vec3(n.x,n.y+1.5f,n.z),0xff39b54a,w,h,dp(3));
      drawWorldLine(c,o,new StudioMath3D.Vec3(n.x,n.y,n.z+1.5f),0xff0081ff,w,h,dp(3));
      float[] center=new float[3];
      if(project(o,center,w,h)){
        p.setColor(0xffffffff);c.drawCircle(center[0],center[1],dp(5),p);
      }
    }

    void drawSpawn3D(Canvas c,StudioScene.Node n,int w,int h){
      StudioScene.Node fake=new StudioScene.Node(n.id,n.name,n.type);fake.x=n.x;fake.y=.08f+n.y;fake.z=n.z;fake.rx=n.rx;fake.ry=n.ry;fake.rz=n.rz;fake.sx=n.sx;fake.sy=n.sy;fake.sz=n.sz;fake.tintColor=n.tintColor;
      drawBox(c,fake,w,h,0xffd5d7da,1.45f,.08f,.8f);
    }

    float span(MotionEvent e){if(e.getPointerCount()<2)return 0;float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return (float)Math.sqrt(dx*dx+dy*dy);}
    float dist(float x1,float y1,float x2,float y2){float dx=x1-x2,dy=y1-y2;return (float)Math.sqrt(dx*dx+dy*dy);}
    float distToSegment(float px,float py,float x1,float y1,float x2,float y2){
      float l2=(x2-x1)*(x2-x1)+(y2-y1)*(y2-y1);if(l2<1e-4f)return dist(px,py,x1,y1);
      float t=Math.max(0,Math.min(1,((px-x1)*(x2-x1)+(py-y1)*(y2-y1))/l2));
      return dist(px,py,x1+t*(x2-x1),y1+t*(y2-y1));
    }

    @Override public boolean onTouchEvent(MotionEvent e){
      int action=e.getActionMasked();
      int w=getWidth(),h=getHeight();
      if(action==MotionEvent.ACTION_DOWN){
        lastX=e.getX();lastY=e.getY();lastSpan=0;moved=false;
        dragMode=DRAG_NONE;
        dragStartScreenX=lastX; dragStartScreenY=lastY;

        StudioScene.Node sel=scene.selected();
        if(sel!=null&&camera!=null){
          float[] sCenter=new float[3], sX=new float[3], sY=new float[3], sZ=new float[3];
          StudioMath3D.Vec3 nPos=new StudioMath3D.Vec3(sel.x,sel.y,sel.z);
          if(project(nPos,sCenter,w,h)){
            project(new StudioMath3D.Vec3(sel.x+1.5f,sel.y,sel.z),sX,w,h);
            project(new StudioMath3D.Vec3(sel.x,sel.y+1.5f,sel.z),sY,w,h);
            project(new StudioMath3D.Vec3(sel.x,sel.y,sel.z+1.5f),sZ,w,h);

            float dCenter=dist(lastX,lastY,sCenter[0],sCenter[1]);
            float dX=distToSegment(lastX,lastY,sCenter[0],sCenter[1],sX[0],sX[1]);
            float dY=distToSegment(lastX,lastY,sCenter[0],sCenter[1],sY[0],sY[1]);
            float dZ=distToSegment(lastX,lastY,sCenter[0],sCenter[1],sZ[0],sZ[1]);
            float thresh=dp(28);

            if("Rotate".equals(tool)){
              if(dCenter<thresh*2f||dX<thresh||dY<thresh||dZ<thresh){
                dragMode=DRAG_OBJECT_ROT;draggedNodeId=sel.id;nodeStartRotY=sel.ry;return true;
              }
            }else if("Scale".equals(tool)){
              if(dCenter<thresh*2f||dX<thresh||dY<thresh||dZ<thresh){
                dragMode=DRAG_OBJECT_SCALE;draggedNodeId=sel.id;nodeStartScale=sel.sx;return true;
              }
            }else{
              if(dY<thresh){
                dragMode=DRAG_AXIS_Y;draggedNodeId=sel.id;nodeStartPos=new StudioMath3D.Vec3(sel.x,sel.y,sel.z);return true;
              }else if(dX<thresh){
                dragMode=DRAG_AXIS_X;draggedNodeId=sel.id;nodeStartPos=new StudioMath3D.Vec3(sel.x,sel.y,sel.z);
                StudioMath3D.Vec3 ray=StudioMath3D.screenToRayDir(lastX,lastY,w,h,camera);
                dragStartHit=StudioMath3D.raycastPlaneY(camera.position,ray,sel.y);return true;
              }else if(dZ<thresh){
                dragMode=DRAG_AXIS_Z;draggedNodeId=sel.id;nodeStartPos=new StudioMath3D.Vec3(sel.x,sel.y,sel.z);
                StudioMath3D.Vec3 ray=StudioMath3D.screenToRayDir(lastX,lastY,w,h,camera);
                dragStartHit=StudioMath3D.raycastPlaneY(camera.position,ray,sel.y);return true;
              }else if(dCenter<thresh*1.6f||(hitRects.containsKey(sel.id)&&hitRects.get(sel.id).contains(lastX,lastY))){
                dragMode=DRAG_OBJECT_XZ;draggedNodeId=sel.id;nodeStartPos=new StudioMath3D.Vec3(sel.x,sel.y,sel.z);
                StudioMath3D.Vec3 ray=StudioMath3D.screenToRayDir(lastX,lastY,w,h,camera);
                dragStartHit=StudioMath3D.raycastPlaneY(camera.position,ray,sel.y);return true;
              }
            }
          }
        }

        // Tap on another object
        ArrayList<Map.Entry<Long,RectF>> entries=new ArrayList<>(hitRects.entrySet());
        for(int i=entries.size()-1;i>=0;i--){
          Map.Entry<Long,RectF> entry=entries.get(i);
          if(entry.getValue().contains(lastX,lastY)){
            scene.select(entry.getKey());
            StudioScene.Node n=scene.selected();
            if(n!=null){
              select(n.name);
              dragMode=DRAG_OBJECT_XZ;
              draggedNodeId=entry.getKey();
              nodeStartPos=new StudioMath3D.Vec3(n.x,n.y,n.z);
              StudioMath3D.Vec3 ray=StudioMath3D.screenToRayDir(lastX,lastY,w,h,camera);
              dragStartHit=StudioMath3D.raycastPlaneY(camera.position,ray,n.y);
              invalidate();
              return true;
            }
          }
        }

        dragMode=DRAG_CAMERA;
        return true;
      }

      if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()>=2){
        lastX=(e.getX(0)+e.getX(1))*.5f;lastY=(e.getY(0)+e.getY(1))*.5f;lastSpan=span(e);moved=true;return true;
      }

      if(action==MotionEvent.ACTION_MOVE){
        float curX=e.getX(),curY=e.getY();
        float dx=curX-lastX,dy=curY-lastY;
        if(Math.abs(dx)+Math.abs(dy)>dp(2))moved=true;

        if(e.getPointerCount()>=2){
          float mx=(e.getX(0)+e.getX(1))*.5f,my=(e.getY(0)+e.getY(1))*.5f;
          float mdx=mx-lastX,mdy=my-lastY;
          StudioMath3D.Vec3 right=camera.right,up=camera.up;
          float scale=distance*.0018f;
          targetX-=right.x*mdx*scale;targetY+=up.y*mdy*scale;targetZ-=right.z*mdx*scale;
          float now=span(e);
          if(lastSpan>1&&now>1){distance=StudioMath3D.clamp(distance*(lastSpan/now),2f,150f);}
          lastSpan=now;lastX=mx;lastY=my;moved=true;invalidate();return true;
        }

        StudioScene.Node sel=scene.selected();
        if(sel!=null&&sel.id==draggedNodeId&&!sel.locked){
          if(dragMode==DRAG_OBJECT_XZ){
            StudioMath3D.Vec3 ray=StudioMath3D.screenToRayDir(curX,curY,w,h,camera);
            StudioMath3D.Vec3 hit=StudioMath3D.raycastPlaneY(camera.position,ray,nodeStartPos.y);
            if(hit!=null&&dragStartHit!=null){
              float nx=nodeStartPos.x+(hit.x-dragStartHit.x);
              float nz=nodeStartPos.z+(hit.z-dragStartHit.z);
              sel.x=nx;sel.z=nz;
              if(physicsRunning&&sel.isDynamic){
                sel.vx=(nx-nodeStartPos.x)*4f;
                sel.vz=(nz-nodeStartPos.z)*4f;
              }
              updateInspectorValues();invalidate();return true;
            }
          }else if(dragMode==DRAG_AXIS_Y){
            float dyY=-(curY-dragStartScreenY)*(distance*.0022f);
            sel.y=Math.max(0.5f*sel.sy,nodeStartPos.y+dyY);
            updateInspectorValues();invalidate();return true;
          }else if(dragMode==DRAG_AXIS_X){
            StudioMath3D.Vec3 ray=StudioMath3D.screenToRayDir(curX,curY,w,h,camera);
            StudioMath3D.Vec3 hit=StudioMath3D.raycastPlaneY(camera.position,ray,nodeStartPos.y);
            if(hit!=null&&dragStartHit!=null){sel.x=nodeStartPos.x+(hit.x-dragStartHit.x);updateInspectorValues();invalidate();return true;}
          }else if(dragMode==DRAG_AXIS_Z){
            StudioMath3D.Vec3 ray=StudioMath3D.screenToRayDir(curX,curY,w,h,camera);
            StudioMath3D.Vec3 hit=StudioMath3D.raycastPlaneY(camera.position,ray,nodeStartPos.y);
            if(hit!=null&&dragStartHit!=null){sel.z=nodeStartPos.z+(hit.z-dragStartHit.z);updateInspectorValues();invalidate();return true;}
          }else if(dragMode==DRAG_OBJECT_ROT){
            float dRot=(curX-dragStartScreenX)*0.5f;
            sel.ry=(nodeStartRotY+dRot)%360f;updateInspectorValues();invalidate();return true;
          }else if(dragMode==DRAG_OBJECT_SCALE){
            float dScale=-(curY-dragStartScreenY)*0.01f;
            float s=Math.max(0.1f,nodeStartScale+dScale);
            sel.sx=s;sel.sy=s;sel.sz=s;updateInspectorValues();invalidate();return true;
          }
        }

        // Camera Orbit
        if(dragMode==DRAG_CAMERA){
          yaw-=dx*.007f;
          pitch=StudioMath3D.clamp(pitch-dy*.0055f,StudioMath3D.radians(-85f),StudioMath3D.radians(85f));
          lastX=curX;lastY=curY;invalidate();return true;
        }
        return true;
      }

      if(action==MotionEvent.ACTION_UP){
        dragMode=DRAG_NONE;
        draggedNodeId=-1;
        return true;
      }
      return true;
    }
  }
}
