package studio.ocean.app;

import java.util.*;

public final class StudioQuickToolRegistry {
  public static final class Tool {
    public final String id,name,description;
    Tool(String id,String name,String description){this.id=id;this.name=name;this.description=description;}
  }
  public static final class Result {
    public final boolean changed; public final String message;
    Result(boolean changed,String message){this.changed=changed;this.message=message;}
  }

  private final List<Tool> tools=Arrays.asList(
    new Tool("ground","Ground To Y=0","Move the selected origin to the world ground plane."),
    new Tool("center-xz","Center On World XZ","Set X and Z to zero while preserving height."),
    new Tool("snap-010","Snap 0.10 m","Round position to a 10 cm grid."),
    new Tool("snap-025","Snap 0.25 m","Round position to a 25 cm grid."),
    new Tool("rot-x90","Rotate X +90°","Apply an exact quarter-turn around X."),
    new Tool("rot-y90","Rotate Y +90°","Apply an exact quarter-turn around Y."),
    new Tool("rot-z90","Rotate Z +90°","Apply an exact quarter-turn around Z."),
    new Tool("scale-half","Uniform Scale ×0.5","Halve X/Y/Z scale uniformly."),
    new Tool("scale-double","Uniform Scale ×2","Double X/Y/Z scale uniformly."),
    new Tool("duplicate-offset","Duplicate +X 1 m","Duplicate selection and offset it one meter on X.")
  );

  public List<Tool> all(){return tools;}

  public Result run(String id,StudioScene scene){
    StudioScene.Node n=scene.selected();
    if(n==null)return new Result(false,"Select an object first");
    boolean changed;
    switch(id){
      case "ground": changed=scene.groundSelected(); break;
      case "center-xz": changed=scene.centerSelectedXZ(); break;
      case "snap-010": changed=scene.snapSelected(.10f); break;
      case "snap-025": changed=scene.snapSelected(.25f); break;
      case "rot-x90": changed=scene.rotateSelected(90f,0f,0f);scene.normalizeSelectedAngles();break;
      case "rot-y90": changed=scene.rotateSelected(0f,90f,0f);scene.normalizeSelectedAngles();break;
      case "rot-z90": changed=scene.rotateSelected(0f,0f,90f);scene.normalizeSelectedAngles();break;
      case "scale-half": changed=scene.scaleSelected(.5f);break;
      case "scale-double": changed=scene.scaleSelected(2f);break;
      case "duplicate-offset": {
        StudioScene.Node q=scene.duplicateSelected();
        changed=q!=null;
        return new Result(changed,changed?"Created "+q.name+" at +1 m X":"Duplicate failed");
      }
      default:return new Result(false,"Unknown tool");
    }
    String name=id;for(Tool t:tools)if(t.id.equals(id)){name=t.name;break;}
    return new Result(changed,changed?name+" applied":"Tool could not modify selection");
  }
}