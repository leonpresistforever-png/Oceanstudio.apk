package studio.ocean.app;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.io.File;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/**
 * Real GPU-backed Ocean Studio viewport. The Android Canvas viewport remains
 * available as a compatibility fallback and interaction/HUD overlay.
 */
public final class StudioGpuViewport extends GLSurfaceView {
    private final GpuRenderer renderer;

    public StudioGpuViewport(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(new MsaaChooser());
        getHolder().setFormat(PixelFormat.OPAQUE);
        setPreserveEGLContextOnPause(true);
        renderer = new GpuRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void updateScene(List<StudioScene.Node> nodes) {
        renderer.scene = nodes == null ? Collections.emptyList() : nodes;
        requestRender();
    }

    public void updateOptions(boolean grid,boolean axes,boolean spawn,boolean primitives) {
        renderer.showGrid=grid;renderer.showAxes=axes;renderer.showSpawn=spawn;renderer.showPrimitives=primitives;requestRender();
    }

    public void updateCamera(float yaw,float pitch,float distance,float tx,float ty,float tz) {
        renderer.yaw=yaw;renderer.pitch=pitch;renderer.distance=distance;
        renderer.tx=tx;renderer.ty=ty;renderer.tz=tz;
        requestRender();
    }

    private static final class MsaaChooser implements EGLConfigChooser {
        @Override public EGLConfig chooseConfig(EGL10 egl,EGLDisplay display) {
            int[] count={0};
            int[] msaa={
                    EGL10.EGL_RED_SIZE,8,EGL10.EGL_GREEN_SIZE,8,EGL10.EGL_BLUE_SIZE,8,EGL10.EGL_ALPHA_SIZE,8,
                    EGL10.EGL_DEPTH_SIZE,24,EGL10.EGL_STENCIL_SIZE,8,
                    EGL10.EGL_SAMPLE_BUFFERS,1,EGL10.EGL_SAMPLES,4,EGL10.EGL_NONE};
            if(egl.eglChooseConfig(display,msaa,null,0,count)&&count[0]>0){
                EGLConfig[] configs=new EGLConfig[count[0]];
                if(egl.eglChooseConfig(display,msaa,configs,configs.length,count)&&configs[0]!=null)return configs[0];
            }
            int[] fallback={
                    EGL10.EGL_RED_SIZE,8,EGL10.EGL_GREEN_SIZE,8,EGL10.EGL_BLUE_SIZE,8,EGL10.EGL_ALPHA_SIZE,8,
                    EGL10.EGL_DEPTH_SIZE,24,EGL10.EGL_STENCIL_SIZE,8,EGL10.EGL_NONE};
            egl.eglChooseConfig(display,fallback,null,0,count);
            EGLConfig[] configs=new EGLConfig[Math.max(1,count[0])];
            if(!egl.eglChooseConfig(display,fallback,configs,configs.length,count)||configs[0]==null)
                throw new IllegalArgumentException("No suitable EGL config");
            return configs[0];
        }
    }

    private static final class GpuRenderer implements GLSurfaceView.Renderer {
        volatile List<StudioScene.Node> scene=Collections.emptyList();
        volatile float yaw=StudioMath3D.radians(-35f),pitch=StudioMath3D.radians(27f),distance=13f,tx=0,ty=.8f,tz=0;
        volatile boolean showGrid=true,showAxes=true,showSpawn=true,showPrimitives=true;
        int meshProgram,lineProgram,cubeVbo,planeVbo,gridVbo,axisVbo;
        int gridVertexCount;
        final HashMap<String,GpuMesh> meshCache=new HashMap<>();

        static final class GpuMesh {
            int vbo,ibo,indexCount;
            float minx,miny,minz,maxx,maxy,maxz;
        }
        final float[] projection=new float[16],view=new float[16],model=new float[16],vp=new float[16],mvp=new float[16],normal=new float[16],inverse=new float[16];

        private static final float[] CUBE={
            // front
            -.5f,-.5f,.5f, 0,0,1,  .5f,-.5f,.5f, 0,0,1,  .5f,.5f,.5f, 0,0,1,
            -.5f,-.5f,.5f, 0,0,1,  .5f,.5f,.5f, 0,0,1, -.5f,.5f,.5f, 0,0,1,
            // back
             .5f,-.5f,-.5f, 0,0,-1, -.5f,-.5f,-.5f, 0,0,-1, -.5f,.5f,-.5f, 0,0,-1,
             .5f,-.5f,-.5f, 0,0,-1, -.5f,.5f,-.5f, 0,0,-1, .5f,.5f,-.5f, 0,0,-1,
            // left
            -.5f,-.5f,-.5f, -1,0,0, -.5f,-.5f,.5f, -1,0,0, -.5f,.5f,.5f, -1,0,0,
            -.5f,-.5f,-.5f, -1,0,0, -.5f,.5f,.5f, -1,0,0, -.5f,.5f,-.5f, -1,0,0,
            // right
             .5f,-.5f,.5f, 1,0,0, .5f,-.5f,-.5f, 1,0,0, .5f,.5f,-.5f, 1,0,0,
             .5f,-.5f,.5f, 1,0,0, .5f,.5f,-.5f, 1,0,0, .5f,.5f,.5f, 1,0,0,
            // top
            -.5f,.5f,.5f, 0,1,0, .5f,.5f,.5f, 0,1,0, .5f,.5f,-.5f, 0,1,0,
            -.5f,.5f,.5f, 0,1,0, .5f,.5f,-.5f, 0,1,0, -.5f,.5f,-.5f, 0,1,0,
            // bottom
            -.5f,-.5f,-.5f, 0,-1,0, .5f,-.5f,-.5f, 0,-1,0, .5f,-.5f,.5f, 0,-1,0,
            -.5f,-.5f,-.5f, 0,-1,0, .5f,-.5f,.5f, 0,-1,0, -.5f,-.5f,.5f, 0,-1,0
        };

        private static final float[] PLANE={
            -10,0,-10,0,1,0, 10,0,-10,0,1,0, 10,0,10,0,1,0,
            -10,0,-10,0,1,0, 10,0,10,0,1,0, -10,0,10,0,1,0
        };

        @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
            GLES30.glClearColor(.055f,.07f,.085f,1f);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            GLES30.glDepthFunc(GLES30.GL_LEQUAL);
            GLES30.glEnable(GLES30.GL_CULL_FACE);
            GLES30.glCullFace(GLES30.GL_BACK);
            GLES30.glEnable(GLES30.GL_MULTISAMPLE);
            meshCache.clear();
            meshProgram=program(MESH_VS,MESH_FS);
            lineProgram=program(LINE_VS,LINE_FS);
            cubeVbo=buffer(CUBE);
            planeVbo=buffer(PLANE);
            float[] grid=buildGrid(10);
            gridVertexCount=grid.length/3;
            gridVbo=buffer(grid);
            axisVbo=buffer(new float[]{-10,.012f,0,10,.012f,0, 0,0,0,0,4,0, 0,.012f,-10,0,.012f,10});
        }

        @Override public void onSurfaceChanged(GL10 gl,int width,int height) {
            GLES30.glViewport(0,0,width,height);
            float aspect=height==0?1f:(float)width/(float)height;
            Matrix.perspectiveM(projection,0,52f,aspect,.05f,200f);
        }

        @Override public void onDrawFrame(GL10 gl) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT|GLES30.GL_DEPTH_BUFFER_BIT);
            float cp=(float)Math.cos(pitch),sp=(float)Math.sin(pitch),sy=(float)Math.sin(yaw),cy=(float)Math.cos(yaw);
            float cx=tx+distance*cp*sy,cyPos=ty+distance*sp,cz=tz+distance*cp*cy;
            Matrix.setLookAtM(view,0,cx,cyPos,cz,tx,ty,tz,0,1,0);
            Matrix.multiplyMM(vp,0,projection,0,view,0);

            drawMesh(planeVbo,6,0,0,0,0,0,0,1,1,1,.12f,.15f,.18f);

            GLES30.glUseProgram(lineProgram);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(lineProgram,"uVP"),1,false,vp,0);
            GLES30.glEnableVertexAttribArray(0);
            if(showGrid){
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,gridVbo);
                GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,12,0);
                GLES30.glUniform4f(GLES30.glGetUniformLocation(lineProgram,"uColor"),.23f,.26f,.29f,1);
                GLES30.glDrawArrays(GLES30.GL_LINES,0,gridVertexCount);
            }
            if(showAxes){
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,axisVbo);
                GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,12,0);
                int color=GLES30.glGetUniformLocation(lineProgram,"uColor");
                GLES30.glUniform4f(color,.70f,.28f,.28f,1);GLES30.glDrawArrays(GLES30.GL_LINES,0,2);
                GLES30.glUniform4f(color,.30f,.66f,.38f,1);GLES30.glDrawArrays(GLES30.GL_LINES,2,2);
                GLES30.glUniform4f(color,.30f,.48f,.72f,1);GLES30.glDrawArrays(GLES30.GL_LINES,4,2);
            }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);

            List<StudioScene.Node> copy=scene;
            for(StudioScene.Node n:copy){
                if(n==null||!n.visible||"plane".equals(n.type))continue;
                if("spawn".equals(n.type)){
                    if(!showSpawn)continue;
                    drawMesh(cubeVbo,36,n.x,n.y+.08f,n.z,n.rx,n.ry,n.rz,2.9f*n.sx,.16f*n.sy,1.6f*n.sz,.77f,.79f,.82f);
                }else if("light".equals(n.type)){
                    drawMesh(cubeVbo,36,n.x,n.y,n.z,n.rx,n.ry,n.rz,.35f*n.sx,.35f*n.sy,.35f*n.sz,1f,.85f,.35f);
                }else if("camera".equals(n.type)){
                    drawMesh(cubeVbo,36,n.x,n.y,n.z,n.rx,n.ry,n.rz,.42f*n.sx,.30f*n.sy,.58f*n.sz,.8f,.7f,.3f);
                }else if(!"empty".equals(n.type)){
                    if("cube".equals(n.type)&&!showPrimitives)continue;
                    float r=.38f,g=.49f,b=.60f;
                    if("gltf".equals(n.type)||"obj".equals(n.type)||"model".equals(n.type)){
                        r=.32f;g=.52f;b=.66f;
                        GpuMesh gm=n.previewPath==null||n.previewPath.isEmpty()?null:getPreviewMesh(n.previewPath);
                        if(gm!=null){drawIndexedMesh(gm,n.x,n.y,n.z,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz,r,g,b);continue;}
                    }
                    drawMesh(cubeVbo,36,n.x,n.y,n.z,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz,r,g,b);
                }
            }
        }

        private GpuMesh getPreviewMesh(String path){
            GpuMesh cached=meshCache.get(path);if(cached!=null)return cached;
            File file=new File(path);if(!file.isFile()||file.length()<40||file.length()>128L*1024L*1024L)return null;
            try(FileInputStream in=new FileInputStream(file);FileChannel channel=in.getChannel()){
                int size=(int)channel.size();
                ByteBuffer all=ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
                while(all.hasRemaining()&&channel.read(all)>0){}
                all.flip();
                int magic=all.getInt(),version=all.getInt(),vertexCount=all.getInt(),indexCount=all.getInt();
                if(magic!=0x48534d4f||version!=1||vertexCount<=0||indexCount<=0)return null;
                GpuMesh gm=new GpuMesh();
                gm.minx=all.getFloat();gm.miny=all.getFloat();gm.minz=all.getFloat();gm.maxx=all.getFloat();gm.maxy=all.getFloat();gm.maxz=all.getFloat();
                long expected=16L+24L+(long)vertexCount*24L+(long)indexCount*4L;
                if(expected>size)return null;

                ByteBuffer vb=all.slice().order(ByteOrder.LITTLE_ENDIAN);vb.limit(vertexCount*24);
                int[] ids=new int[2];GLES30.glGenBuffers(2,ids,0);gm.vbo=ids[0];gm.ibo=ids[1];gm.indexCount=indexCount;
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,gm.vbo);
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,vertexCount*24,vb,GLES30.GL_STATIC_DRAW);
                all.position(all.position()+vertexCount*24);
                ByteBuffer ib=all.slice().order(ByteOrder.LITTLE_ENDIAN);ib.limit(indexCount*4);
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,gm.ibo);
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER,indexCount*4,ib,GLES30.GL_STATIC_DRAW);
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,0);
                meshCache.put(path,gm);return gm;
            }catch(Throwable ignored){return null;}
        }

        private void drawIndexedMesh(GpuMesh gm,float x,float y,float z,float rx,float ry,float rz,float sx,float sy,float sz,float r,float g,float b){
            prepareModel(x,y,z,rx,ry,rz,sx,sy,sz,r,g,b);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,gm.vbo);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,gm.ibo);
            GLES30.glEnableVertexAttribArray(0);GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,24,0);
            GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,24,12);
            GLES30.glDrawElements(GLES30.GL_TRIANGLES,gm.indexCount,GLES30.GL_UNSIGNED_INT,0);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER,0);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
        }

        private void prepareModel(float x,float y,float z,float rx,float ry,float rz,float sx,float sy,float sz,float r,float g,float b){
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,x,y,z);
            Matrix.rotateM(model,0,rz,0,0,1);
            Matrix.rotateM(model,0,ry,0,1,0);
            Matrix.rotateM(model,0,rx,1,0,0);
            Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(mvp,0,vp,0,model,0);
            if(Matrix.invertM(inverse,0,model,0))Matrix.transposeM(normal,0,inverse,0);else Matrix.setIdentityM(normal,0);
            GLES30.glUseProgram(meshProgram);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram,"uMVP"),1,false,mvp,0);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram,"uModel"),1,false,model,0);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram,"uNormal"),1,false,normal,0);
            GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProgram,"uBase"),r,g,b);
        }

        private void drawMesh(int vbo,int vertices,float x,float y,float z,float rx,float ry,float rz,float sx,float sy,float sz,float r,float g,float b){
            prepareModel(x,y,z,rx,ry,rz,sx,sy,sz,r,g,b);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,vbo);
            GLES30.glEnableVertexAttribArray(0);GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(0,3,GLES30.GL_FLOAT,false,24,0);
            GLES30.glVertexAttribPointer(1,3,GLES30.GL_FLOAT,false,24,12);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES,0,vertices);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
        }

        private static float[] buildGrid(int radius){
            ArrayList<Float> v=new ArrayList<>();
            for(int i=-radius;i<=radius;i++){
                addLine(v,i,.006f,-radius,i,.006f,radius);
                addLine(v,-radius,.006f,i,radius,.006f,i);
            }
            float[] out=new float[v.size()];for(int i=0;i<out.length;i++)out[i]=v.get(i);return out;
        }
        private static void addLine(ArrayList<Float> v,float ax,float ay,float az,float bx,float by,float bz){
            v.add(ax);v.add(ay);v.add(az);v.add(bx);v.add(by);v.add(bz);
        }

        private static int buffer(float[] data){
            int[] id=new int[1];GLES30.glGenBuffers(1,id,0);
            FloatBuffer fb=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();fb.put(data).position(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,id[0]);GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER,data.length*4,fb,GLES30.GL_STATIC_DRAW);GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER,0);
            return id[0];
        }

        private static int shader(int type,String source){
            int s=GLES30.glCreateShader(type);GLES30.glShaderSource(s,source);GLES30.glCompileShader(s);
            int[] ok=new int[1];GLES30.glGetShaderiv(s,GLES30.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0){String log=GLES30.glGetShaderInfoLog(s);GLES30.glDeleteShader(s);throw new IllegalStateException("GL shader compile failed: "+log);}
            return s;
        }
        private static int program(String vs,String fs){
            int v=shader(GLES30.GL_VERTEX_SHADER,vs),f=shader(GLES30.GL_FRAGMENT_SHADER,fs),p=GLES30.glCreateProgram();
            GLES30.glAttachShader(p,v);GLES30.glAttachShader(p,f);GLES30.glLinkProgram(p);GLES30.glDeleteShader(v);GLES30.glDeleteShader(f);
            int[] ok=new int[1];GLES30.glGetProgramiv(p,GLES30.GL_LINK_STATUS,ok,0);
            if(ok[0]==0){String log=GLES30.glGetProgramInfoLog(p);GLES30.glDeleteProgram(p);throw new IllegalStateException("GL program link failed: "+log);}
            return p;
        }

        private static final String MESH_VS=
                "#version 300 es\n"+
                "layout(location=0) in vec3 aPos;\n"+
                "layout(location=1) in vec3 aNormal;\n"+
                "uniform mat4 uMVP; uniform mat4 uModel; uniform mat4 uNormal;\n"+
                "out vec3 vN; out vec3 vWorld;\n"+
                "void main(){ vec4 w=uModel*vec4(aPos,1.0); vWorld=w.xyz; vN=normalize(mat3(uNormal)*aNormal); gl_Position=uMVP*vec4(aPos,1.0); }";

        private static final String MESH_FS=
                "#version 300 es\nprecision highp float;\n"+
                "in vec3 vN; in vec3 vWorld; uniform vec3 uBase; out vec4 frag;\n"+
                "void main(){ vec3 n=normalize(vN); vec3 l=normalize(vec3(-0.42,0.86,0.30));"+
                "float diff=max(dot(n,l),0.0); float hemi=0.5+0.5*n.y;"+
                "vec3 c=uBase*(0.20+0.68*diff+0.12*hemi); frag=vec4(c,1.0); }";

        private static final String LINE_VS=
                "#version 300 es\nlayout(location=0) in vec3 aPos; uniform mat4 uVP; void main(){ gl_Position=uVP*vec4(aPos,1.0); }";
        private static final String LINE_FS=
                "#version 300 es\nprecision mediump float; uniform vec4 uColor; out vec4 frag; void main(){ frag=uColor; }";
    }
}
