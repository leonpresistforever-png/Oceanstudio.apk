package studio.ocean.app;

import java.util.*;

public final class StudioPluginRegistry {
  public static final class Plugin {
    public final String id,name,description;
    Plugin(String id,String name,String description){this.id=id;this.name=name;this.description=description;}
  }
  public static final class Result {
    public final boolean changed; public final String message;
    Result(boolean changed,String message){this.changed=changed;this.message=message;}
  }
  private final List<Plugin> plugins=Arrays.asList(
    new Plugin("duplicate","Duplicate Selected","Duplicate the selected scene node with a +1 X offset."),
    new Plugin("delete","Delete Selected","Remove the selected scene node."),
    new Plugin("reset-transform","Reset Transform","Reset position, rotation and scale."),
    new Plugin("move-origin","Move To Origin","Move selected object to 0,0,0 without changing rotation or scale."),
    new Plugin("snap-grid","Snap To Grid","Round selected position to 1-unit grid."),
    new Plugin("mirror-x","Mirror X","Mirror selected object across the world X axis."),
    new Plugin("rotate-y-90","Rotate Y +90°","Rotate selected object ninety degrees around Y."),
    new Plugin("scale-double","Scale ×2","Double selected object scale."),
    new Plugin("scale-half","Scale ×0.5","Halve selected object scale."),
    new Plugin("toggle-visible","Toggle Visibility","Show or hide the selected node."),
    new Plugin("toggle-lock","Toggle Lock","Lock or unlock selected node editing."),
    new Plugin("add-camera","Add Camera","Create a camera node in the scene."),
    new Plugin("add-light","Add Light","Create a light node in the scene."),
    new Plugin("add-empty","Add Empty","Create an empty transform node."),
    new Plugin("scene-stats","Scene Statistics","Report live scene and selection statistics.")
  );
  public List<Plugin> all(){return plugins;}
  public Result run(String id,StudioScene scene){
    StudioScene.Node n=scene.selected();
    switch(id){
      case "duplicate": {StudioScene.Node q=scene.duplicateSelected();return new Result(q!=null,q!=null?"Duplicated "+q.name:"Select an object first");}
      case "delete": return new Result(scene.deleteSelected(),"Delete selected");
      case "reset-transform": return new Result(scene.resetSelectedTransform(),"Reset transform");
      case "move-origin": if(n==null)return new Result(false,"Select an object first");scene.transform(n.id,0,0,0,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz);return new Result(true,"Moved to origin");
      case "snap-grid": return new Result(scene.snapSelected(1f),"Snapped to 1-unit grid");
      case "mirror-x": return new Result(scene.mirrorSelectedX(),"Mirrored across X");
      case "rotate-y-90": return new Result(scene.rotateSelected(0,90,0),"Rotated Y +90°");
      case "scale-double": return new Result(scene.scaleSelected(2f),"Scale doubled");
      case "scale-half": return new Result(scene.scaleSelected(.5f),"Scale halved");
      case "toggle-visible": return new Result(scene.toggleSelectedVisibility(),"Visibility toggled");
      case "toggle-lock": return new Result(scene.toggleSelectedLock(),"Lock toggled");
      case "add-camera": scene.add("Camera","camera");return new Result(true,"Camera added");
      case "add-light": scene.add("Light","light");return new Result(true,"Light added");
      case "add-empty": scene.add("Empty","empty");return new Result(true,"Empty added");
      case "scene-stats":
        return new Result(false,scene.size()+" nodes"+(n==null?" · nothing selected":" · selected "+n.name+" ("+n.type+")"));
      default:return new Result(false,"Unknown plugin");
    }
  }
}