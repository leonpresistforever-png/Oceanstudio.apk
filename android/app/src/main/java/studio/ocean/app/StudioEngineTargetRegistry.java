package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public final class StudioEngineTargetRegistry {
  public static final class Target {
    public final String id,name,description;
    public final String[] preferredFormatIds;
    public final float metersPerUnit;
    public final String upAxis,forwardAxis,handedness;
    Target(String id,String name,String description,String[] preferred,float metersPerUnit,String upAxis,String forwardAxis,String handedness){
      this.id=id;this.name=name;this.description=description;this.preferredFormatIds=preferred;
      this.metersPerUnit=metersPerUnit;this.upAxis=upAxis;this.forwardAxis=forwardAxis;this.handedness=handedness;
    }
  }

  private static final String PREF="ocean_studio_engine_target";
  private final SharedPreferences prefs;
  private final List<Target> targets=Arrays.asList(
    new Target("ocean","Ocean Native","Ocean Studio portable asset pipeline.",new String[]{"glb2","gltf2","obj"},1f,"Y","-Z","right"),
    new Target("godot4","Godot 4","Godot-ready 3D asset export; GLB/glTF is preferred and Godot performs its importer conversion.",new String[]{"glb2","gltf2","obj"},1f,"Y","-Z","right"),
    new Target("unity","Unity","Unity asset target. Prefer FBX when the compiled Assimp exporter provides it, otherwise GLB/glTF.",new String[]{"fbx","glb2","gltf2","obj"},1f,"Y","+Z","left"),
    new Target("unreal","Unreal Engine","Unreal asset target. Prefer FBX when available, with GLB/glTF as portable fallback.",new String[]{"fbx","glb2","gltf2","obj"},0.01f,"Z","+X","left"),
    new Target("blender","Blender","Blender interoperability target using portable scene formats.",new String[]{"glb2","gltf2","collada","obj","ply"},1f,"Z","-Y","right"),
    new Target("threejs","Three.js","Web runtime target; binary glTF is preferred.",new String[]{"glb2","gltf2"},1f,"Y","+Z","right"),
    new Target("babylon","Babylon.js","Web runtime target; binary glTF is preferred.",new String[]{"glb2","gltf2"},1f,"Y","+Z","right"),
    new Target("roblox","Roblox Studio","Mesh asset interoperability target; FBX/OBJ are preferred when available.",new String[]{"fbx","obj","glb2"},1f,"Y","-Z","right"),
    new Target("generic","Generic / DCC","No engine-specific assumptions; choose any compiled Assimp exporter.",new String[]{"glb2","gltf2","obj","ply","stl"},1f,"Y","+Z","right")
  );

  public StudioEngineTargetRegistry(Context c){prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
  public List<Target> all(){return targets;}
  public Target selected(){
    String id=prefs.getString("selected","godot4");
    for(Target t:targets)if(t.id.equals(id))return t;
    return targets.get(0);
  }
  public void select(String id){prefs.edit().putString("selected",id).apply();}
  public String choosePreferred(Set<String> available){
    Target t=selected();
    for(String id:t.preferredFormatIds)if(available.contains(id))return id;
    return available.isEmpty()?null:available.iterator().next();
  }
}