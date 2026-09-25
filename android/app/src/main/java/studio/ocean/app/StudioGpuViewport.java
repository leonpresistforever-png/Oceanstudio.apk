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

    private static final class MsaaChooser implements GLSurfaceView.EGLConfigChooser {
        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] attribs = {
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_STENCIL_SIZE, 8,
                    EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4,
                    EGL10.EGL_NONE
            };
            int[] count = new int[1];
            egl.eglChooseConfig(display, attribs, null, 0, count);
            if (count[0] > 0) {
                EGLConfig[] configs = new EGLConfig[count[0]];
                egl.eglChooseConfig(display, attribs, configs, configs.length, count);
                return configs[0];
            }
            int[] fallback = {
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_STENCIL_SIZE, 8, EGL10.EGL_NONE};
            egl.eglChooseConfig(display, fallback, null, 0, count);
            EGLConfig[] configs = new EGLConfig[Math.max(1, count[0])];
            if (!egl.eglChooseConfig(display, fallback, configs, configs.length, count) || configs[0] == null)
                throw new IllegalArgumentException("No suitable EGL config");
            return configs[0];
        }
    }

    private static final class GpuRenderer implements GLSurfaceView.Renderer {
        volatile List<StudioScene.Node> scene = Collections.emptyList();
        volatile float yaw = StudioMath3D.radians(-35f), pitch = StudioMath3D.radians(27f), distance = 13f, tx = 0, ty = .8f, tz = 0;
        volatile boolean showGrid = true, showAxes = true, showSpawn = true, showPrimitives = true;
        int meshProgram, lineProgram, cubeVbo, planeVbo, gridVbo, axisVbo;
        int gridVertexCount;
        final HashMap<String, GpuMesh> meshCache = new HashMap<>();

        static final class GpuMesh {
            int vbo, ibo, indexCount;
            float minx, miny, minz, maxx, maxy, maxz;
        }
        final float[] projection = new float[16], view = new float[16], model = new float[16], vp = new float[16], mvp = new float[16], normal = new float[16], inverse = new float[16];
        float cameraX, cameraY, cameraZ;

        private static final float[] CUBE = {
            // front
            -.5f,-.5f,.5f, 0,0,1,  .5f,-.5f,.5f, 0,0,1,  .5f,.5f,.5f, 0,0,1,
            -.5f,-.5f,.5f, 0,0,1,  .5f,.5f,.5f, 0,0,1, -.5f,.5f,.5f, 0,0,1,
            // back
             .5f,-.5f,-.5f, 0,0,-1, -.5f,-.5f,-.5f, 0,0,-1, -.5f,.5f,-.5f, 0,0,-1,
             .5f,-.5f,-.5f, 0,0,-1, -.5f,.5f,-.5f, 0,0,-1,  .5f,.5f,-.5f, 0,0,-1,
            // left
            -.5f,-.5f,-.5f, -1,0,0, -.5f,-.5f,.5f, -1,0,0, -.5f,.5f,.5f, -1,0,0,
            -.5f,-.5f,-.5f, -1,0,0, -.5f,.5f,.5f, -1,0,0, -.5f,.5f,-.5f, -1,0,0,
            // right
             .5f,-.5f,.5f, 1,0,0,  .5f,-.5f,-.5f, 1,0,0,  .5f,.5f,-.5f, 1,0,0,
             .5f,-.5f,.5f, 1,0,0,  .5f,.5f,-.5f, 1,0,0,  .5f,.5f,.5f, 1,0,0,
            // top
            -.5f,.5f,.5f, 0,1,0,   .5f,.5f,.5f, 0,1,0,   .5f,.5f,-.5f, 0,1,0,
            -.5f,.5f,.5f, 0,1,0,   .5f,.5f,-.5f, 0,1,0,  -.5f,.5f,-.5f, 0,1,0,
            // bottom
            -.5f,-.5f,-.5f, 0,-1,0,  .5f,-.5f,-.5f, 0,-1,0,  .5f,-.5f,.5f, 0,-1,0,
            -.5f,-.5f,-.5f, 0,-1,0,  .5f,-.5f,.5f, 0,-1,0, -.5f,-.5f,.5f, 0,-1,0
        };

        // Enlarge baseplate to 100x100
        private static final float[] PLANE = {
            -100,0,-100,0,1,0,  100,0,-100,0,1,0,  100,0,100,0,1,0,
            -100,0,-100,0,1,0,  100,0,100,0,1,0, -100,0,100,0,1,0
        };

        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            // Bright sky-blue background clear color matching outdoor horizon
            GLES30.glClearColor(.38f, .62f, .88f, 1f);
            GLES30.glEnable(GLES30.GL_DEPTH_TEST);
            GLES30.glDepthFunc(GLES30.GL_LEQUAL);
            GLES30.glEnable(GLES30.GL_CULL_FACE);
            GLES30.glCullFace(GLES30.GL_BACK);
            meshCache.clear();
            meshProgram = program(MESH_VS, MESH_FS);
            lineProgram = program(LINE_VS, LINE_FS);
            cubeVbo = buffer(CUBE);
            planeVbo = buffer(PLANE);
            float[] grid = buildGrid(40);
            gridVertexCount = grid.length / 3;
            gridVbo = buffer(grid);
            axisVbo = buffer(new float[]{-40,.012f,0, 40,.012f,0,  0,0,0, 0,8,0,  0,.012f,-40, 0,.012f,40});
        }

        @Override public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES30.glViewport(0, 0, width, height);
            float aspect = height == 0 ? 1f : (float) width / (float) height;
            Matrix.perspectiveM(projection, 0, 52f, aspect, .05f, 300f);
        }

        @Override public void onDrawFrame(GL10 gl) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
            float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch), sy = (float) Math.sin(yaw), cy = (float) Math.cos(yaw);
            float cx = tx + distance * cp * sy, cyPos = ty + distance * sp, cz = tz + distance * cp * cy;
            cameraX = cx; cameraY = cyPos; cameraZ = cz;
            Matrix.setLookAtM(view, 0, cx, cyPos, cz, tx, ty, tz, 0, 1, 0);
            Matrix.multiplyMM(vp, 0, projection, 0, view, 0);

            // Draw baseplate (dark slate ground)
            drawMesh(planeVbo, 6, 0, 0, 0, 0, 0, 0, 1, 1, 1, .14f, .17f, .20f);

            GLES30.glUseProgram(lineProgram);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(lineProgram, "uVP"), 1, false, vp, 0);
            GLES30.glEnableVertexAttribArray(0);
            if (showGrid) {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, gridVbo);
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0);
                GLES30.glUniform4f(GLES30.glGetUniformLocation(lineProgram, "uColor"), .25f, .30f, .36f, 1);
                GLES30.glDrawArrays(GLES30.GL_LINES, 0, gridVertexCount);
            }
            if (showAxes) {
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, axisVbo);
                GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0);
                int color = GLES30.glGetUniformLocation(lineProgram, "uColor");
                GLES30.glUniform4f(color, .80f, .28f, .28f, 1); GLES30.glDrawArrays(GLES30.GL_LINES, 0, 2);
                GLES30.glUniform4f(color, .30f, .75f, .38f, 1); GLES30.glDrawArrays(GLES30.GL_LINES, 2, 2);
                GLES30.glUniform4f(color, .30f, .55f, .85f, 1); GLES30.glDrawArrays(GLES30.GL_LINES, 4, 2);
            }
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);

            List<StudioScene.Node> copy = scene;
            for (StudioScene.Node n : copy) {
                if (n == null || !n.visible || "plane".equals(n.type)) continue;
                if ("spawn".equals(n.type)) {
                    if (!showSpawn) continue;
                    drawMesh(cubeVbo, 36, n.x, n.y + .08f, n.z, n.rx, n.ry, n.rz, 2.9f * n.sx, .16f * n.sy, 1.6f * n.sz, .77f, .79f, .82f);
                } else if ("light".equals(n.type)) {
                    drawMesh(cubeVbo, 36, n.x, n.y, n.z, n.rx, n.ry, n.rz, .35f * n.sx, .35f * n.sy, .35f * n.sz, 1f, .85f, .35f);
                } else if ("camera".equals(n.type)) {
                    drawMesh(cubeVbo, 36, n.x, n.y, n.z, n.rx, n.ry, n.rz, .42f * n.sx, .30f * n.sy, .58f * n.sz, .8f, .7f, .3f);
                } else if (!"empty".equals(n.type)) {
                    if ("cube".equals(n.type) && !showPrimitives) continue;
                    float r = .38f, g = .49f, b = .60f;
                    if (n.tintColor != 0) {
                        r = ((n.tintColor >> 16) & 0xff) / 255f;
                        g = ((n.tintColor >> 8) & 0xff) / 255f;
                        b = (n.tintColor & 0xff) / 255f;
                    } else if ("gltf".equals(n.type) || "obj".equals(n.type) || "model".equals(n.type)) {
                        r = .32f; g = .52f; b = .66f;
                        GpuMesh gm = n.previewPath == null || n.previewPath.isEmpty() ? null : getPreviewMesh(n.previewPath);
                        if (gm != null) {
                            drawIndexedMesh(gm, n.x, n.y, n.z, n.rx, n.ry, n.rz, n.sx, n.sy, n.sz, r, g, b);
                            continue;
                        }
                    }
                    drawMesh(cubeVbo, 36, n.x, n.y, n.z, n.rx, n.ry, n.rz, n.sx, n.sy, n.sz, r, g, b);
                }
            }
        }

        private GpuMesh getPreviewMesh(String path) {
            GpuMesh cached = meshCache.get(path); if (cached != null) return cached;
            File file = new File(path); if (!file.isFile() || file.length() < 40 || file.length() > 128L * 1024L * 1024L) return null;
            try (FileInputStream in = new FileInputStream(file); FileChannel channel = in.getChannel()) {
                int size = (int) channel.size();
                ByteBuffer bb = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
                channel.read(bb); bb.position(0);
                byte[] magic = new byte[4]; bb.get(magic);
                if (magic[0] != 'O' || magic[1] != 'M' || magic[2] != 'S' || magic[3] != 'H') return null;
                int version = bb.getInt(), vertexCount = bb.getInt(), indexCount = bb.getInt();
                if (version != 1 || vertexCount <= 0 || indexCount <= 0) return null;
                GpuMesh m = new GpuMesh();
                m.minx = bb.getFloat(); m.miny = bb.getFloat(); m.minz = bb.getFloat();
                m.maxx = bb.getFloat(); m.maxy = bb.getFloat(); m.maxz = bb.getFloat();
                m.indexCount = indexCount;
                int vertexBytes = vertexCount * 6 * 4;
                int indexBytes = indexCount * 4;
                if (bb.remaining() < vertexBytes + indexBytes) return null;
                byte[] vBytes = new byte[vertexBytes]; bb.get(vBytes);
                ByteBuffer vBuf = ByteBuffer.allocateDirect(vertexBytes).order(ByteOrder.LITTLE_ENDIAN);
                vBuf.put(vBytes); vBuf.position(0);
                byte[] iBytes = new byte[indexBytes]; bb.get(iBytes);
                ByteBuffer iBuf = ByteBuffer.allocateDirect(indexBytes).order(ByteOrder.LITTLE_ENDIAN);
                iBuf.put(iBytes); iBuf.position(0);

                int[] ids = new int[2]; GLES30.glGenBuffers(2, ids, 0);
                m.vbo = ids[0]; m.ibo = ids[1];
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, m.vbo);
                GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexBytes, vBuf, GLES30.GL_STATIC_DRAW);
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.ibo);
                GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBytes, iBuf, GLES30.GL_STATIC_DRAW);
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
                GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
                meshCache.put(path, m);
                return m;
            } catch (Throwable t) {
                return null;
            }
        }

        private void drawMesh(int vbo, int count, float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz, float r, float g, float b) {
            setupModel(x, y, z, rx, ry, rz, sx, sy, sz, r, g, b);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12);
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
        }

        private void drawIndexedMesh(GpuMesh m, float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz, float r, float g, float b) {
            setupModel(x, y, z, rx, ry, rz, sx, sy, sz, r, g, b);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, m.vbo);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 24, 0);
            GLES30.glEnableVertexAttribArray(1);
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 24, 12);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.ibo);
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.indexCount, GLES30.GL_UNSIGNED_INT, 0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0);
        }

        private void setupModel(float x, float y, float z, float rx, float ry, float rz, float sx, float sy, float sz, float r, float g, float b) {
            Matrix.setIdentityM(model, 0);
            Matrix.translateM(model, 0, x, y, z);
            Matrix.rotateM(model, 0, rx, 1, 0, 0);
            Matrix.rotateM(model, 0, ry, 0, 1, 0);
            Matrix.rotateM(model, 0, rz, 0, 0, 1);
            Matrix.scaleM(model, 0, sx, sy, sz);
            Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);
            Matrix.invertM(inverse, 0, model, 0);
            Matrix.transposeM(normal, 0, inverse, 0);

            GLES30.glUseProgram(meshProgram);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram, "uMVP"), 1, false, mvp, 0);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram, "uNormal"), 1, false, normal, 0);
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(meshProgram, "uModel"), 1, false, model, 0);
            GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProgram, "uColor"), r, g, b);
            GLES30.glUniform3f(GLES30.glGetUniformLocation(meshProgram, "uCamPos"), cameraX, cameraY, cameraZ);
        }

        private static float[] buildGrid(int r) {
            ArrayList<Float> list = new ArrayList<>();
            for (int i = -r; i <= r; i += 2) {
                list.add((float) i); list.add(0f); list.add((float) -r);
                list.add((float) i); list.add(0f); list.add((float) r);
                list.add((float) -r); list.add(0f); list.add((float) i);
                list.add((float) r); list.add(0f); list.add((float) i);
            }
            float[] res = new float[list.size()];
            for (int i = 0; i < list.size(); i++) res[i] = list.get(i);
            return res;
        }

        private static int buffer(float[] data) {
            int[] id = new int[1]; GLES30.glGenBuffers(1, id, 0);
            FloatBuffer fb = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            fb.put(data); fb.position(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, id[0]);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.length * 4, fb, GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
            return id[0];
        }

        private static int compileShader(int type, String src) {
            int s = GLES30.glCreateShader(type);
            GLES30.glShaderSource(s, src);
            GLES30.glCompileShader(s);
            int[] status = new int[1];
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES30.glGetShaderInfoLog(s);
                GLES30.glDeleteShader(s);
                throw new RuntimeException("Shader compile error: " + log);
            }
            return s;
        }

        private static int program(String vs, String fs) {
            int v = compileShader(GLES30.GL_VERTEX_SHADER, vs);
            int f = compileShader(GLES30.GL_FRAGMENT_SHADER, fs);
            int p = GLES30.glCreateProgram();
            GLES30.glAttachShader(p, v);
            GLES30.glAttachShader(p, f);
            GLES30.glLinkProgram(p);
            int[] status = new int[1];
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                String log = GLES30.glGetProgramInfoLog(p);
                GLES30.glDeleteProgram(p);
                throw new RuntimeException("Program link error: " + log);
            }
            return p;
        }

        private static final String MESH_VS =
            "#version 300 es\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec3 aNormal;\n" +
            "uniform mat4 uMVP, uNormal, uModel;\n" +
            "out vec3 vNormal, vWorldPos;\n" +
            "void main() {\n" +
            "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "  vNormal = normalize(mat3(uNormal) * aNormal);\n" +
            "  vWorldPos = vec3(uModel * vec4(aPos, 1.0));\n" +
            "}";

        private static final String MESH_FS =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec3 vNormal, vWorldPos;\n" +
            "uniform vec3 uColor, uCamPos;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "  vec3 lightDir1 = normalize(vec3(0.5, 0.9, 0.4));\n" +
            "  vec3 lightDir2 = normalize(vec3(-0.4, 0.3, -0.6));\n" +
            "  float diff1 = max(dot(vNormal, lightDir1), 0.0);\n" +
            "  float diff2 = max(dot(vNormal, lightDir2), 0.0) * 0.3;\n" +
            "  vec3 viewDir = normalize(uCamPos - vWorldPos);\n" +
            "  vec3 halfDir = normalize(lightDir1 + viewDir);\n" +
            "  float spec = pow(max(dot(vNormal, halfDir), 0.0), 32.0) * 0.25;\n" +
            "  vec3 ambient = vec3(0.25, 0.28, 0.32);\n" +
            "  vec3 col = uColor * (ambient + vec3(diff1 + diff2)) + vec3(spec);\n" +
            "  fragColor = vec4(col, 1.0);\n" +
            "}";

        private static final String LINE_VS =
            "#version 300 es\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "uniform mat4 uVP;\n" +
            "void main() { gl_Position = uVP * vec4(aPos, 1.0); }";

        private static final String LINE_FS =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "out vec4 fragColor;\n" +
            "void main() { fragColor = uColor; }";
    }
}
