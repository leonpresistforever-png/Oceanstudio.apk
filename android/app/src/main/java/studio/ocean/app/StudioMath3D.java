package studio.ocean.app;

/**
 * Small allocation-free 3D math core for Ocean Studio's mobile viewport.
 * Convention: right-handed world, Y up. Camera-space forward is positive Z
 * only inside projection math; this avoids ambiguous sign handling in UI code.
 */
public final class StudioMath3D {
  private StudioMath3D(){}

  public static final class Vec3 {
    public float x,y,z;
    public Vec3(float x,float y,float z){this.x=x;this.y=y;this.z=z;}
    public Vec3 add(Vec3 o){return new Vec3(x+o.x,y+o.y,z+o.z);}
    public Vec3 sub(Vec3 o){return new Vec3(x-o.x,y-o.y,z-o.z);}
    public Vec3 mul(float s){return new Vec3(x*s,y*s,z*s);}
  }

  public static float dot(Vec3 a,Vec3 b){return a.x*b.x+a.y*b.y+a.z*b.z;}
  public static Vec3 cross(Vec3 a,Vec3 b){return new Vec3(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x);}
  public static float length(Vec3 a){return (float)Math.sqrt(dot(a,a));}
  public static Vec3 normalize(Vec3 a){
    float len=length(a);if(len<1e-7f)return new Vec3(0,0,0);return a.mul(1f/len);
  }
  public static float radians(float degrees){return degrees*(float)Math.PI/180f;}
  public static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
  public static float snap(float value,float step){return step<=0?value:Math.round(value/step)*step;}

  /** Rotate a vector by local X, then Y, then Z Euler rotations in degrees. */
  public static Vec3 rotateXYZ(Vec3 v,float rxDeg,float ryDeg,float rzDeg){
    float rx=radians(rxDeg),ry=radians(ryDeg),rz=radians(rzDeg);
    float cx=(float)Math.cos(rx),sx=(float)Math.sin(rx);
    float cy=(float)Math.cos(ry),sy=(float)Math.sin(ry);
    float cz=(float)Math.cos(rz),sz=(float)Math.sin(rz);
    float x1=v.x,y1=v.y*cx-v.z*sx,z1=v.y*sx+v.z*cx;
    float x2=x1*cy+z1*sy,y2=y1,z2=-x1*sy+z1*cy;
    return new Vec3(x2*cz-y2*sz,x2*sz+y2*cz,z2);
  }

  public static Vec3 transformPoint(Vec3 local,float px,float py,float pz,float rx,float ry,float rz,float sx,float sy,float sz){
    Vec3 scaled=new Vec3(local.x*sx,local.y*sy,local.z*sz);
    Vec3 rotated=rotateXYZ(scaled,rx,ry,rz);
    return new Vec3(rotated.x+px,rotated.y+py,rotated.z+pz);
  }

  public static final class Camera {
    public Vec3 position,target,right,up,forward;
    public float fovYRadians,near;
  }

  public static Camera orbit(Vec3 target,float yaw,float pitch,float distance,float fovYDegrees){
    pitch=clamp(pitch,radians(-85f),radians(85f));
    distance=Math.max(.2f,distance);
    float cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch);
    float sy=(float)Math.sin(yaw),cy=(float)Math.cos(yaw);
    Vec3 position=new Vec3(target.x+distance*cp*sy,target.y+distance*sp,target.z+distance*cp*cy);
    Vec3 forward=normalize(target.sub(position));
    Vec3 worldUp=new Vec3(0,1,0);
    Vec3 right=normalize(cross(forward,worldUp));
    if(length(right)<1e-6f)right=new Vec3(1,0,0);
    Vec3 up=normalize(cross(right,forward));
    Camera c=new Camera();c.position=position;c.target=target;c.forward=forward;c.right=right;c.up=up;c.fovYRadians=radians(fovYDegrees);c.near=.05f;return c;
  }

  /**
   * Project world point to pixels. out[0]=x, out[1]=y, out[2]=positive camera depth.
   * Returns false for points on/behind the near plane.
   */
  public static boolean project(Vec3 p,Camera c,int width,int height,float[] out){
    Vec3 d=p.sub(c.position);
    float z=dot(d,c.forward);
    if(z<=c.near)return false;
    float x=dot(d,c.right),y=dot(d,c.up);
    float focal=(height*.5f)/(float)Math.tan(c.fovYRadians*.5f);
    out[0]=width*.5f+(x/z)*focal;
    out[1]=height*.5f-(y/z)*focal;
    out[2]=z;
    return Float.isFinite(out[0])&&Float.isFinite(out[1])&&Float.isFinite(out[2]);
  }

  /**
   * Unproject screen pixel coordinates into a normalized world-space ray direction.
   */
  public static Vec3 screenToRayDir(float screenX, float screenY, int width, int height, Camera c) {
    float focal = (height * 0.5f) / (float) Math.tan(c.fovYRadians * 0.5f);
    float x = (screenX - width * 0.5f) / focal;
    float y = -(screenY - height * 0.5f) / focal;
    Vec3 camRay = c.right.mul(x).add(c.up.mul(y)).add(c.forward);
    return normalize(camRay);
  }

  /**
   * Raycast from world rayOrigin along rayDir against horizontal plane Y = planeY.
   * Returns intersection point in world space, or null if ray is parallel or pointing away.
   */
  public static Vec3 raycastPlaneY(Vec3 rayOrigin, Vec3 rayDir, float planeY) {
    if (Math.abs(rayDir.y) < 1e-6f) return null;
    float t = (planeY - rayOrigin.y) / rayDir.y;
    if (t < 0.01f || t > 1000f) return null;
    return rayOrigin.add(rayDir.mul(t));
  }

  /**
   * Shortest distance from a 3D ray to a 3D point.
   */
  public static float distanceRayToPoint(Vec3 rayOrigin, Vec3 rayDir, Vec3 point) {
    Vec3 diff = point.sub(rayOrigin);
    float t = dot(diff, rayDir);
    if (t < 0) return length(diff);
    Vec3 projection = rayOrigin.add(rayDir.mul(t));
    return length(point.sub(projection));
  }
}
