package studio.ocean.app;

import org.json.JSONObject;
import java.util.*;

public final class StudioExternalPluginRegistry {
  public static final class Plugin {
    public final String id,name,sourceRepo,tool,description,accepts;
    public final int processMode;
    Plugin(String id,String name,String sourceRepo,String tool,String description,String accepts){
      this(id,name,sourceRepo,tool,description,accepts,0);
    }
    Plugin(String id,String name,String sourceRepo,String tool,String description,String accepts,int processMode){
      this.id=id;this.name=name;this.sourceRepo=sourceRepo;this.tool=tool;this.description=description;this.accepts=accepts;this.processMode=processMode;
    }
  }
  private final List<Plugin> plugins=Arrays.asList(
    new Plugin("cgltf-summary","glTF Scene Summary","jkuhlmann/cgltf","cgltf","Parse the selected glTF/GLB and report scene, node, mesh, material, image, animation, camera and light counts.","gltf"),
    new Plugin("cgltf-validate","glTF Validator","jkuhlmann/cgltf","cgltf","Run cgltf structural validation and buffer loading checks.","gltf"),
    new Plugin("cgltf-mesh-count","glTF Mesh Counter","jkuhlmann/cgltf","cgltf","Count meshes, primitives, vertices and indices in the selected glTF/GLB.","gltf"),
    new Plugin("cgltf-material-count","glTF Material Counter","jkuhlmann/cgltf","cgltf","Inspect material, texture and image counts.","gltf"),
    new Plugin("cgltf-animation-count","glTF Animation Counter","jkuhlmann/cgltf","cgltf","Inspect animation, camera and light counts.","gltf"),
    new Plugin("cgltf-node-count","glTF Node Counter","jkuhlmann/cgltf","cgltf","Count nodes in the selected glTF/GLB scene.","gltf"),
    new Plugin("meshopt-lod75","Meshoptimizer LOD 75%","zeux/meshoptimizer","meshoptimizer","Run real meshoptimizer simplification targeting 75% of the first triangle primitive indices.","gltf"),
    new Plugin("meshopt-lod50","Meshoptimizer LOD 50%","zeux/meshoptimizer","meshoptimizer","Run real meshoptimizer simplification targeting 50% of the first triangle primitive indices.","gltf"),
    new Plugin("meshopt-lod25","Meshoptimizer LOD 25%","zeux/meshoptimizer","meshoptimizer","Run real meshoptimizer simplification targeting 25% of the first triangle primitive indices.","gltf"),
    new Plugin("meshopt-cache","Meshoptimizer Vertex Cache","zeux/meshoptimizer","meshoptimizer","Optimize and compare simulated GPU vertex-cache ACMR/ATVR statistics.","gltf"),
    new Plugin("tinyobj-summary","OBJ Scene Summary","tinyobjloader/tinyobjloader","tinyobjloader","Parse the selected OBJ and report vertices, normals, UVs, shapes, faces and materials.","obj"),
    new Plugin("tinyobj-materials","OBJ Material Counter","tinyobjloader/tinyobjloader","tinyobjloader","Count materials in the selected OBJ.","obj"),
    new Plugin("tinyobj-faces","OBJ Face Counter","tinyobjloader/tinyobjloader","tinyobjloader","Count faces and indices in the selected OBJ.","obj"),
    new Plugin("stb-texture","Texture Probe","nothings/stb","stb_image","Inspect selected texture dimensions, channel count and megapixels.","image"),
    new Plugin("stb-memory","Texture Memory Estimate","nothings/stb","stb_image","Estimate uncompressed base-level texture memory from real image dimensions and channels.","image"),
    new Plugin("assimp-summary","Universal Model Inspector","assimp/assimp","Assimp","Inspect any model format supported by the compiled Assimp importer and report meshes, vertices, faces, materials, animation, cameras, lights and bones.","*model"),
    new Plugin("assimp-triangulate","Triangulate Geometry","assimp/assimp","Assimp","Convert polygonal faces to triangles and write a processed GLB copy.","*model",1),
    new Plugin("assimp-normals","Rebuild Smooth Normals","assimp/assimp","Assimp","Generate smooth vertex normals and write a processed GLB copy.","*model",2),
    new Plugin("assimp-tangents","Build Tangent Space","assimp/assimp","Assimp","Generate tangent/bitangent data for normal-mapped materials.","*model",3),
    new Plugin("assimp-weld","Weld Identical Vertices","assimp/assimp","Assimp","Join identical vertices to reduce duplicate geometry.","*model",4),
    new Plugin("assimp-cache","Improve Cache Locality","assimp/assimp","Assimp","Reorder mesh data for improved post-transform vertex cache locality.","*model",5),
    new Plugin("assimp-optimize-mesh","Optimize Mesh Batches","assimp/assimp","Assimp","Optimize mesh batches while preserving scene content.","*model",6),
    new Plugin("assimp-optimize-graph","Optimize Scene Graph","assimp/assimp","Assimp","Optimize node graph and mesh structure for runtime use.","*model",7),
    new Plugin("assimp-clean-materials","Clean Redundant Materials","assimp/assimp","Assimp","Remove duplicate/redundant material slots and export a cleaned GLB.","*model",8),
    new Plugin("assimp-flip-uv","Flip UV Coordinates","assimp/assimp","Assimp","Flip texture V coordinates for pipelines that require opposite UV origin.","*model",9),
    new Plugin("assimp-repair","Repair Invalid Geometry","assimp/assimp","Assimp","Find degenerate/invalid mesh data, weld vertices and export repaired GLB.","*model",10),
    new Plugin("xatlas-auto-uv","Auto UV Unwrap 2048","jpcy/xatlas","xatlas","Generate real UV charts and packing with xatlas, then write a UV-unwrapped OBJ copy.","gltf")
  );

  public List<Plugin> all(){return plugins;}

  public String run(Plugin p,StudioScene.Node node){
    if(node==null)return "Select an imported asset first.";
    if(node.sourcePath==null||node.sourcePath.isEmpty())return "Selected node has no imported source file.";
    try{
      JSONObject j;
      switch(p.id){
        case "cgltf-summary":
          return pretty(StudioOpenSourceTools.inspectGltf(node.sourcePath));
        case "cgltf-validate":
          j=new JSONObject(StudioOpenSourceTools.inspectGltf(node.sourcePath));
          return j.optBoolean("ok")?"valid="+j.optBoolean("valid")+" · buffersLoaded="+j.optBoolean("buffersLoaded")+" · validationCode="+j.optInt("validationCode") : j.optString("error","glTF validation failed");
        case "cgltf-mesh-count":
          j=new JSONObject(StudioOpenSourceTools.inspectGltf(node.sourcePath));
          return j.optBoolean("ok")?"meshes="+j.optInt("meshes")+" · primitives="+j.optInt("primitives")+" · vertices="+j.optLong("vertices")+" · indices="+j.optLong("indices"):j.optString("error","glTF parse failed");
        case "cgltf-material-count":
          j=new JSONObject(StudioOpenSourceTools.inspectGltf(node.sourcePath));
          return j.optBoolean("ok")?"materials="+j.optInt("materials")+" · textures="+j.optInt("textures")+" · images="+j.optInt("images"):j.optString("error","glTF parse failed");
        case "cgltf-animation-count":
          j=new JSONObject(StudioOpenSourceTools.inspectGltf(node.sourcePath));
          return j.optBoolean("ok")?"animations="+j.optInt("animations")+" · cameras="+j.optInt("cameras")+" · lights="+j.optInt("lights"):j.optString("error","glTF parse failed");
        case "cgltf-node-count":
          j=new JSONObject(StudioOpenSourceTools.inspectGltf(node.sourcePath));
          return j.optBoolean("ok")?"nodes="+j.optInt("nodes"):j.optString("error","glTF parse failed");
        case "meshopt-lod75": return pretty(StudioOpenSourceTools.simplifyGltf(node.sourcePath,.75f));
        case "meshopt-lod50": return pretty(StudioOpenSourceTools.simplifyGltf(node.sourcePath,.50f));
        case "meshopt-lod25": return pretty(StudioOpenSourceTools.simplifyGltf(node.sourcePath,.25f));
        case "meshopt-cache": return pretty(StudioOpenSourceTools.vertexCacheGltf(node.sourcePath));
        case "tinyobj-summary": return pretty(StudioOpenSourceTools.inspectObj(node.sourcePath));
        case "tinyobj-materials":
          j=new JSONObject(StudioOpenSourceTools.inspectObj(node.sourcePath));
          return j.optBoolean("ok")?"materials="+j.optInt("materials")+" · shapes="+j.optInt("shapes"):j.optString("error","OBJ parse failed");
        case "tinyobj-faces":
          j=new JSONObject(StudioOpenSourceTools.inspectObj(node.sourcePath));
          return j.optBoolean("ok")?"faces="+j.optLong("faces")+" · indices="+j.optLong("indices")+" · vertices="+j.optLong("vertices"):j.optString("error","OBJ parse failed");
        case "assimp-summary": return pretty(StudioOpenSourceTools.assimpInspect(node.sourcePath));
        case "stb-texture": return pretty(StudioOpenSourceTools.inspectImage(node.sourcePath));
        case "stb-memory":
          j=new JSONObject(StudioOpenSourceTools.inspectImage(node.sourcePath));
          if(!j.optBoolean("ok"))return j.optString("error","Texture probe failed");
          long bytes=(long)j.optInt("width")*(long)j.optInt("height")*(long)Math.max(1,j.optInt("channels"));
          return "base level ≈ "+String.format(Locale.US,"%.2f",bytes/1048576.0)+" MiB · "+j.optInt("width")+"×"+j.optInt("height")+" · "+j.optInt("channels")+" channels";
        default:return "Unknown external plugin.";
      }
    }catch(Throwable t){return "External tool failed: "+t.getClass().getSimpleName()+" · "+t.getMessage();}
  }

  private String pretty(String raw){
    try{return new JSONObject(raw).toString(2);}catch(Throwable t){return raw;}
  }
}