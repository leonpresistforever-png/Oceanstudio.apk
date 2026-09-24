package studio.ocean.app;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudioCoreTest {
  private static void near(float expected,float actual){assertEquals(expected,actual,1e-4f);}

  @Test public void orbitProjectsTargetToScreenCenter(){
    StudioMath3D.Vec3 target=new StudioMath3D.Vec3(0,1,0);
    StudioMath3D.Camera camera=StudioMath3D.orbit(target,StudioMath3D.radians(-35),StudioMath3D.radians(27),13,52);
    float[] out=new float[3];
    assertTrue(StudioMath3D.project(target,camera,1000,600,out));
    near(500,out[0]);near(300,out[1]);assertTrue(out[2]>0);
  }

  @Test public void xyzEulerUsesDocumentedOrder(){
    StudioMath3D.Vec3 y=new StudioMath3D.Vec3(0,1,0);
    StudioMath3D.Vec3 rotated=StudioMath3D.rotateXYZ(y,90,0,0);
    near(0,rotated.x);near(0,rotated.y);near(1,rotated.z);

    StudioMath3D.Vec3 x=new StudioMath3D.Vec3(1,0,0);
    rotated=StudioMath3D.rotateXYZ(x,0,90,0);
    near(0,rotated.x);near(0,rotated.y);near(-1,rotated.z);
  }

  @Test public void sceneUndoRedoRestoresTransform(){
    StudioScene s=new StudioScene();
    StudioScene.Node n=s.selected();
    assertNotNull(n);
    float original=n.x;
    assertTrue(s.offsetSelected(2,0,0));
    near(original+2,s.selected().x);
    assertTrue(s.undo());near(original,s.selected().x);
    assertTrue(s.redo());near(original+2,s.selected().x);
  }

  @Test public void snapshotEscapesExternalMetadata() throws Exception {
    StudioScene s=new StudioScene();
    StudioScene.Node n=s.selected();
    s.setMetadata(n.id,"line 1\n\"quoted\"\\path");
    JSONObject parsed=new JSONObject(s.snapshot());
    assertEquals(3,parsed.getJSONArray("nodes").length());
  }

  @Test public void quickToolSectionContainsTenWorkingTools(){
    StudioQuickToolRegistry tools=new StudioQuickToolRegistry();
    assertEquals(10,tools.all().size());
    StudioScene s=new StudioScene();
    StudioQuickToolRegistry.Result r=tools.run("ground",s);
    assertTrue(r.changed);
    near(0,s.selected().y);
  }
}
