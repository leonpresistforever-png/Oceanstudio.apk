package studio.ocean.app.render;

import android.content.*;
import android.opengl.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Lightweight in-app OBJ viewer used by ocean-3d / ocean-render tooling. */
public final class Ocean3DActivity extends AppCompatActivity {
    private static final AtomicReference<Ocean3DActivity> ACTIVE = new AtomicReference<>();
    private ModelView view;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ACTIVE.set(this);

        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff8f8f6);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView title=new TextView(this);title.setText("Ocean 3D");title.setTextSize(18);title.setTextColor(0xff181818);title.setTypeface(null,1);
        bar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button reset=button("Reset");reset.setOnClickListener(v->view.reset());
        Button wire=button("Wire");wire.setOnClickListener(v->view.toggleWire());
        bar.addView(wire);bar.addView(reset);root.addView(bar);

        status=new TextView(this);status.setTextColor(0xff6f7175);status.setTextSize(12);status.setPadding(dp(12),dp(4),dp(12),dp(6));root.addView(status);
        view=new ModelView(this);root.addView(view,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);

        String path=getIntent().getStringExtra("path");
        if(path!=null&&!path.isEmpty())load(path); else status.setText("Pass an OBJ path with ocean-3d-open <file.obj>");
    }

    @Override protected void onDestroy(){ACTIVE.compareAndSet(this,null);super.onDestroy();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);b.setMinWidth(0);b.setMinimumWidth(0);return b;}

    private void load(String path){
        new Thread(()->{
            try{
                ObjData d=ObjData.read(new File(path));
                runOnUiThread(()->{view.setModel(d);status.setText(new File(path).getName()+" · "+d.triangles+" triangles · "+d.sourceVertices+" vertices");});
            }catch(Throwable t){runOnUiThread(()->status.setText("3D load failed: "+String.valueOf(t.getMessage())));}
        },"ocean-3d-load").start();
    }

    public static void open(Context c,String path){
        c.startActivity(new Intent(c,Ocean3DActivity.class).putExtra("path",path).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    public static JSONObject control(String action,JSONObject args){
        JSONObject o=new JSONObject();
        try{
            Ocean3DActivity a=ACTIVE.get();
            if("status".equals(action))return o.put("open",a!=null).put("loaded",a!=null&&a.view!=null&&a.view.renderer.hasModel());
            if(a==null)return o.put("success",false).put("error","3D renderer is not open");
            a.runOnUiThread(()->{
                if("reset".equals(action))a.view.reset();
                else if("wireframe".equals(action))a.view.setWire(true);
                else if("solid".equals(action))a.view.setWire(false);
                else if("rotate".equals(action))a.view.rotate((float)args.optDouble("x",0),(float)args.optDouble("y",0));
                else if("zoom".equals(action))a.view.zoom((float)args.optDouble("delta",0));
            });
            return o.put("success",true);
        }catch(Throwable t){try{return o.put("success",false).put("error",String.valueOf(t.getMessage()));}catch(Throwable ignored){return new JSONObject();}}
    }

    private static final class ObjData{
        final float[] vertices;final int triangles,sourceVertices;
        ObjData(float[] v,int t,int s){vertices=v;triangles=t;sourceVertices=s;}
        static ObjData read(File f)throws Exception{
            ArrayList<float[]> vs=new ArrayList<>();ArrayList<Float> out=new ArrayList<>();
            try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f)))){
                String line;
                while((line=r.readLine())!=null){
                    line=line.trim();
                    if(line.startsWith("v ")){
                        String[] p=line.split("\\s+");if(p.length>=4)vs.add(new float[]{Float.parseFloat(p[1]),Float.parseFloat(p[2]),Float.parseFloat(p[3])});
                    }else if(line.startsWith("f ")){
                        String[] p=line.substring(2).trim().split("\\s+");if(p.length<3)continue;
                        int[] ids=new int[p.length];
                        for(int i=0;i<p.length;i++){
                            String s=p[i].split("/")[0];int id=Integer.parseInt(s);if(id<0)id=vs.size()+id+1;ids[i]=id-1;
                        }
                        for(int i=1;i<p.length-1;i++){append(out,vs.get(ids[0]));append(out,vs.get(ids[i]));append(out,vs.get(ids[i+1]));}
                    }
                }
            }
            if(out.isEmpty()&&!vs.isEmpty())for(float[] v:vs)append(out,v);
            float[] arr=new float[out.size()];for(int i=0;i<arr.length;i++)arr[i]=out.get(i);
            normalize(arr);
            return new ObjData(arr,arr.length/9,vs.size());
        }
        static void append(ArrayList<Float> o,float[] v){o.add(v[0]);o.add(v[1]);o.add(v[2]);}
        static void normalize(float[] a){
            if(a.length<3)return;
            float minX=a[0],maxX=a[0],minY=a[1],maxY=a[1],minZ=a[2],maxZ=a[2];
            for(int i=0;i+2<a.length;i+=3){minX=Math.min(minX,a[i]);maxX=Math.max(maxX,a[i]);minY=Math.min(minY,a[i+1]);maxY=Math.max(maxY,a[i+1]);minZ=Math.min(minZ,a[i+2]);maxZ=Math.max(maxZ,a[i+2]);}
            float cx=(minX+maxX)/2,cy=(minY+maxY)/2,cz=(minZ+maxZ)/2;float s=Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ));if(s<=0)s=1;
            for(int i=0;i+2<a.length;i+=3){a[i]=(a[i]-cx)*2/s;a[i+1]=(a[i+1]-cy)*2/s;a[i+2]=(a[i+2]-cz)*2/s;}
        }
    }

    private static final class ModelView extends GLSurfaceView{
        final ModelRenderer renderer=new ModelRenderer();float lastX,lastY;
        ModelView(Context c){super(c);setEGLContextClientVersion(2);setRenderer(renderer);setRenderMode(RENDERMODE_CONTINUOUSLY);}
        void setModel(ObjData d){queueEvent(()->renderer.setModel(d));}
        void reset(){renderer.rx=20;renderer.ry=-25;renderer.zoom=-3f;}
        void toggleWire(){renderer.wire=!renderer.wire;}
        void setWire(boolean x){renderer.wire=x;}
        void rotate(float x,float y){renderer.rx+=x;renderer.ry+=y;}
        void zoom(float d){renderer.zoom=Math.max(-12,Math.min(-1,renderer.zoom+d));}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getPointerCount()>1){
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE)zoom((e.getY(0)-e.getY(1))/getHeight()*.02f);
                return true;
            }
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=e.getX()-lastX,dy=e.getY()-lastY;renderer.ry+=dx*.4f;renderer.rx+=dy*.4f;lastX=e.getX();lastY=e.getY();return true;}
            return true;
        }
    }

    private static final class ModelRenderer implements GLSurfaceView.Renderer{
        volatile float rx=20,ry=-25,zoom=-3f;volatile boolean wire;
        private FloatBuffer vertices;private int count,program,posLoc,mvpLoc;private final float[] proj=new float[16],view=new float[16],model=new float[16],mv=new float[16],mvp=new float[16];
        boolean hasModel(){return count>0;}
        void setModel(ObjData d){ByteBuffer bb=ByteBuffer.allocateDirect(d.vertices.length*4).order(ByteOrder.nativeOrder());vertices=bb.asFloatBuffer();vertices.put(d.vertices).position(0);count=d.vertices.length/3;}
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,javax.microedition.khronos.egl.EGLConfig cfg){
            GLES20.glClearColor(.96f,.96f,.95f,1f);GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            String vs="uniform mat4 uMVP; attribute vec3 aPosition; void main(){ gl_Position=uMVP*vec4(aPosition,1.0); }";
            String fs="precision mediump float; void main(){ gl_FragColor=vec4(0.16,0.18,0.21,1.0); }";
            program=link(vs,fs);posLoc=GLES20.glGetAttribLocation(program,"aPosition");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");
        }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);float a=h==0?1:w/(float)h;Matrix.perspectiveM(proj,0,45,a,.1f,100f);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);FloatBuffer v=vertices;if(v==null||count==0)return;
            Matrix.setLookAtM(view,0,0,0,zoom,0,0,0,0,1,0);Matrix.setIdentityM(model,0);Matrix.rotateM(model,0,rx,1,0,0);Matrix.rotateM(model,0,ry,0,1,0);Matrix.multiplyMM(mv,0,view,0,model,0);Matrix.multiplyMM(mvp,0,proj,0,mv,0);
            GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glEnableVertexAttribArray(posLoc);v.position(0);GLES20.glVertexAttribPointer(posLoc,3,GLES20.GL_FLOAT,false,12,v);
            GLES20.glDrawArrays(wire?GLES20.GL_LINE_STRIP:GLES20.GL_TRIANGLES,0,count);GLES20.glDisableVertexAttribArray(posLoc);
        }
        static int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
        static int link(String v,String f){int p=GLES20.glCreateProgram();int vs=shader(GLES20.GL_VERTEX_SHADER,v),fs=shader(GLES20.GL_FRAGMENT_SHADER,f);GLES20.glAttachShader(p,vs);GLES20.glAttachShader(p,fs);GLES20.glLinkProgram(p);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);return p;}
    }
}
