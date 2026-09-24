package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public final class StudioExternalExtensionRegistry {
  public static final class Extension {
    public final String id,name,sourceRepo,tool,description,accepts;
    public final boolean defaultEnabled;
    Extension(String id,String name,String sourceRepo,String tool,String description,String accepts,boolean defaultEnabled){
      this.id=id;this.name=name;this.sourceRepo=sourceRepo;this.tool=tool;this.description=description;this.accepts=accepts;this.defaultEnabled=defaultEnabled;
    }
  }
  private static final String PREF="ocean_studio_external_extensions";
  private final SharedPreferences prefs;
  private final List<Extension> extensions=Arrays.asList(
    new Extension("auto-cgltf-summary","Auto glTF Summary","jkuhlmann/cgltf","cgltf","Analyze imported glTF/GLB scene structure automatically.","gltf",true),
    new Extension("auto-cgltf-validation","Auto glTF Validation","jkuhlmann/cgltf","cgltf","Validate imported glTF/GLB structure and buffers.","gltf",true),
    new Extension("auto-cgltf-mesh-stats","Auto glTF Mesh Stats","jkuhlmann/cgltf","cgltf","Attach real mesh, primitive, vertex and index counts to imported nodes.","gltf",true),
    new Extension("auto-cgltf-material-stats","Auto glTF Material Stats","jkuhlmann/cgltf","cgltf","Attach material, texture and image counts.","gltf",false),
    new Extension("auto-cgltf-animation-stats","Auto glTF Animation Stats","jkuhlmann/cgltf","cgltf","Attach animation, camera and light counts.","gltf",false),
    new Extension("auto-meshopt-lod75","Auto LOD 75% Analysis","zeux/meshoptimizer","meshoptimizer","Run meshoptimizer simplification analysis at 75% target indices.","gltf",false),
    new Extension("auto-meshopt-lod50","Auto LOD 50% Analysis","zeux/meshoptimizer","meshoptimizer","Run meshoptimizer simplification analysis at 50% target indices.","gltf",false),
    new Extension("auto-meshopt-lod25","Auto LOD 25% Analysis","zeux/meshoptimizer","meshoptimizer","Run meshoptimizer simplification analysis at 25% target indices.","gltf",false),
    new Extension("auto-meshopt-cache","Auto Vertex Cache Analysis","zeux/meshoptimizer","meshoptimizer","Compare vertex-cache statistics before and after meshoptimizer reordering.","gltf",false),
    new Extension("auto-obj-summary","Auto OBJ Summary","tinyobjloader/tinyobjloader","tinyobjloader","Parse imported OBJ geometry and attach scene counts.","obj",true),
    new Extension("auto-obj-face-stats","Auto OBJ Face Stats","tinyobjloader/tinyobjloader","tinyobjloader","Attach OBJ face and index counts.","obj",false),
    new Extension("auto-obj-material-stats","Auto OBJ Material Stats","tinyobjloader/tinyobjloader","tinyobjloader","Attach OBJ material and shape counts.","obj",false),
    new Extension("auto-texture-probe","Auto Texture Probe","nothings/stb","stb_image","Read imported texture dimensions, channels and megapixels.","image",true),
    new Extension("warn-4k-textures","Warn On >4K Textures","nothings/stb","stb_image","Flag textures whose width or height exceeds 4096 pixels.","image",true),
    new Extension("estimate-texture-memory","Texture Memory Estimate","nothings/stb","stb_image","Estimate uncompressed base-level texture memory on import.","image",false)
  );
  public StudioExternalExtensionRegistry(Context c){prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
  public List<Extension> all(){return extensions;}
  public boolean isEnabled(String id){
    for(Extension e:extensions)if(e.id.equals(id))return prefs.getBoolean("enabled."+id,e.defaultEnabled);
    return false;
  }
  public void setEnabled(String id,boolean enabled){prefs.edit().putBoolean("enabled."+id,enabled).apply();}
}