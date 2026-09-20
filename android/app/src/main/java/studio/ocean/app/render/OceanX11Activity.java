package studio.ocean.app.render;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Native Ocean X11/RFB renderer.
 *
 * This is not a WebView and does not depend on Termux:X11. It attaches to a
 * localhost RFB/VNC backend (normally started by ocean-x11-start) and renders
 * frames directly into an Android View while forwarding touch + keyboard input.
 */
public final class OceanX11Activity extends AppCompatActivity {
    public static final int DEFAULT_RFB_PORT = 5901;
    private static final AtomicReference<OceanX11Activity> ACTIVE = new AtomicReference<>();

    private RfbSurface surface;
    private TextView status;
    private Button fitButton;
    private int port = DEFAULT_RFB_PORT;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ACTIVE.set(this);
        port = getIntent().getIntExtra("port", DEFAULT_RFB_PORT);
        buildUi();
        connect();
    }

    @Override protected void onDestroy() {
        if (surface != null) surface.close();
        ACTIVE.compareAndSet(this, null);
        super.onDestroy();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff8f8f6);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(8));
        bar.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("X11");
        title.setTextColor(0xff181818);
        title.setTextSize(18);
        title.setTypeface(null, Typeface.BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        fitButton = button("Fit");
        fitButton.setOnClickListener(v -> {
            surface.toggleFit();
            fitButton.setText(surface.fitMode ? "1:1" : "Fit");
        });
        bar.addView(fitButton);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("Connecting to 127.0.0.1:" + port + " …");
        status.setTextColor(0xff6f7175);
        status.setTextSize(12);
        status.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView controlsScroll = new HorizontalScrollView(this);
        controlsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(8), 0, dp(8), dp(6));
        Button start = button("Start");
        start.setOnClickListener(v -> runBackend(true));
        Button stop = button("Stop");
        stop.setOnClickListener(v -> runBackend(false));
        Button reconnect = button("Reconnect");
        reconnect.setOnClickListener(v -> connect());
        Button keyboard = button("Keyboard");
        keyboard.setOnClickListener(v -> surface.showKeyboard());
        controls.addView(start); controls.addView(stop); controls.addView(reconnect); controls.addView(keyboard);
        controlsScroll.addView(controls);
        root.addView(controlsScroll, new LinearLayout.LayoutParams(-1, -2));

        surface = new RfbSurface(this);
        root.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(12);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(10), 0, dp(10), 0);
        return b;
    }

    private void connect() {
        if (surface == null) return;
        status.setText("Connecting to local display 127.0.0.1:" + port + " …");
        surface.connect("127.0.0.1", port, message -> runOnUiThread(() -> status.setText(message)));
    }

    private void runBackend(boolean start) {
        new Thread(() -> {
            String name = start ? "ocean-x11-start" : "ocean-x11-stop";
            File command = new File(getFilesDir(), "usr/bin/" + name);
            if (!command.canExecute()) {
                runOnUiThread(() -> status.setText(name + " is not installed yet. Install ocean-x11-runtime and " + name + "."));
                return;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(command.getAbsolutePath());
                java.util.Map<String,String> env = pb.environment();
                for (String entry : studio.ocean.app.terminal.OceanEnvironment.create(this, "/system/bin/sh", false)) {
                    int eq = entry.indexOf('=');
                    if (eq > 0) env.put(entry.substring(0, eq), entry.substring(eq + 1));
                }
                pb.redirectErrorStream(true);
                java.lang.Process p = pb.start();
                String output;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    StringBuilder b = new StringBuilder(); String line;
                    while ((line = r.readLine()) != null) b.append(line).append('\n');
                    output = b.toString().trim();
                }
                int code = p.waitFor();
                String finalOutput = output;
                runOnUiThread(() -> {
                    status.setText((start ? "X11 start" : "X11 stop") + " · exit " + code + (finalOutput.isEmpty() ? "" : " · " + finalOutput));
                    if (start && code == 0) status.postDelayed(this::connect, 700);
                });
            } catch (Throwable t) {
                runOnUiThread(() -> status.setText("X11 backend command failed: " + String.valueOf(t.getMessage())));
            }
        }, "ocean-x11-control").start();
    }

    public static JSONObject control(String action, JSONObject args) {
        JSONObject o = new JSONObject();
        try {
            OceanX11Activity a = ACTIVE.get();
            if ("status".equals(action)) {
                o.put("open", a != null);
                o.put("connected", a != null && a.surface != null && a.surface.isConnected());
                o.put("port", a != null ? a.port : DEFAULT_RFB_PORT);
                return o;
            }
            if (a == null) return o.put("success", false).put("error", "X11 renderer is not open");
            a.runOnUiThread(() -> {
                if ("fit".equals(action)) a.surface.setFit(true);
                else if ("pixel".equals(action)) a.surface.setFit(false);
                else if ("keyboard".equals(action)) a.surface.showKeyboard();
                else if ("reconnect".equals(action)) a.connect();
                else if ("start".equals(action)) a.runBackend(true);
                else if ("stop".equals(action)) a.runBackend(false);
            });
            return o.put("success", true);
        } catch (Throwable t) {
            try { return o.put("success", false).put("error", String.valueOf(t.getMessage())); }
            catch (Throwable ignored) { return new JSONObject(); }
        }
    }

    public static void open(Context context, int port) {
        Intent i = new Intent(context, OceanX11Activity.class)
                .putExtra("port", port)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }

    private interface StatusSink { void update(String message); }

    private static final class RfbSurface extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint bg = new Paint();
        private final Object frameLock = new Object();
        private volatile Bitmap frame;
        private volatile RfbClient client;
        private volatile boolean fitMode = true;
        private float drawScale = 1f, drawLeft, drawTop;
        private Thread thread;

        RfbSurface(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            bg.setColor(0xff202124);
        }

        void connect(String host, int port, StatusSink sink) {
            close();
            thread = new Thread(() -> {
                try {
                    RfbClient c = new RfbClient(host, port, (w, h, pixels) -> {
                        Bitmap next = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
                        synchronized (frameLock) {
                            Bitmap old = frame;
                            frame = next;
                            if (old != null && old != next && !old.isRecycled()) old.recycle();
                        }
                        postInvalidate();
                    }, sink);
                    client = c;
                    c.run();
                } catch (Throwable t) {
                    sink.update("X11 backend unavailable: " + String.valueOf(t.getMessage()) +
                            " · start ocean-x11-session then Reconnect");
                }
            }, "ocean-x11-rfb");
            thread.setDaemon(true);
            thread.start();
        }

        boolean isConnected() { RfbClient c = client; return c != null && c.connected; }

        void close() {
            RfbClient c = client;
            client = null;
            if (c != null) c.close();
            Thread t = thread;
            thread = null;
            if (t != null) t.interrupt();
        }

        void toggleFit() { setFit(!fitMode); }
        void setFit(boolean fit) { fitMode = fit; invalidate(); }

        void showKeyboard() {
            requestFocus();
            InputMethodManager imm = (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, getWidth(), getHeight(), bg);
            Bitmap b;
            synchronized (frameLock) { b = frame; }
            if (b == null || b.isRecycled()) {
                paint.setColor(0xffb8babd);
                paint.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
                canvas.drawText("Waiting for X11 framebuffer…", 24, 48, paint);
                return;
            }
            if (fitMode) {
                float s = Math.min(getWidth() / (float)b.getWidth(), getHeight() / (float)b.getHeight());
                drawScale = s;
            } else drawScale = 1f;
            float dw = b.getWidth() * drawScale, dh = b.getHeight() * drawScale;
            drawLeft = (getWidth() - dw) * .5f;
            drawTop = (getHeight() - dh) * .5f;
            RectF dst = new RectF(drawLeft, drawTop, drawLeft + dw, drawTop + dh);
            canvas.drawBitmap(b, null, dst, paint);
        }

        private int remoteX(float x) {
            Bitmap b = frame;
            if (b == null) return 0;
            return Math.max(0, Math.min(b.getWidth() - 1, Math.round((x - drawLeft) / drawScale)));
        }
        private int remoteY(float y) {
            Bitmap b = frame;
            if (b == null) return 0;
            return Math.max(0, Math.min(b.getHeight() - 1, Math.round((y - drawTop) / drawScale)));
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent e) {
            RfbClient c = client;
            if (c == null) return true;
            int x = remoteX(e.getX()), y = remoteY(e.getY());
            int mask = (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) ? 0 : 1;
            c.pointer(mask, x, y);
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) requestFocus();
            return true;
        }

        @Override public boolean onCheckIsTextEditor() { return true; }

        @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT |
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI;
            return new BaseInputConnection(this, false) {
                @Override public boolean commitText(CharSequence text, int newCursorPosition) {
                    RfbClient c = client;
                    if (c != null && text != null) {
                        for (int i = 0; i < text.length(); i++) c.type(text.charAt(i));
                    }
                    return true;
                }
                @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    RfbClient c = client;
                    if (c != null) c.key(0xff08, true, true);
                    return true;
                }
            };
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
            RfbClient c = client;
            if (c == null) return super.onKeyDown(keyCode, event);
            int sym = keysym(keyCode, event);
            if (sym != 0) { c.key(sym, true, false); return true; }
            return super.onKeyDown(keyCode, event);
        }

        @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
            RfbClient c = client;
            if (c == null) return super.onKeyUp(keyCode, event);
            int sym = keysym(keyCode, event);
            if (sym != 0) { c.key(sym, false, false); return true; }
            return super.onKeyUp(keyCode, event);
        }

        private int keysym(int keyCode, KeyEvent e) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER: return 0xff0d;
                case KeyEvent.KEYCODE_DEL: return 0xff08;
                case KeyEvent.KEYCODE_TAB: return 0xff09;
                case KeyEvent.KEYCODE_ESCAPE: return 0xff1b;
                case KeyEvent.KEYCODE_DPAD_LEFT: return 0xff51;
                case KeyEvent.KEYCODE_DPAD_UP: return 0xff52;
                case KeyEvent.KEYCODE_DPAD_RIGHT: return 0xff53;
                case KeyEvent.KEYCODE_DPAD_DOWN: return 0xff54;
                default:
                    int u = e.getUnicodeChar();
                    return u > 0 ? u : 0;
            }
        }
    }

    private interface FrameSink { void frame(int width, int height, int[] pixels); }

    private static final class RfbClient {
        private final String host;
        private final int port;
        private final FrameSink sink;
        private final StatusSink status;
        private Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private int width, height;
        private int[] pixels;
        volatile boolean connected;

        RfbClient(String host, int port, FrameSink sink, StatusSink status) {
            this.host = host; this.port = port; this.sink = sink; this.status = status;
        }

        void run() throws Exception {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 2500);
            socket.setTcpNoDelay(true);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            byte[] serverVersion = new byte[12];
            in.readFully(serverVersion);
            String version = new String(serverVersion, StandardCharsets.US_ASCII);
            if (!version.startsWith("RFB ")) throw new IOException("Not an RFB server");
            out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII)); out.flush();

            int count = in.readUnsignedByte();
            if (count == 0) throw new IOException(readReason());
            byte[] types = new byte[count]; in.readFully(types);
            boolean none = false;
            for (byte t : types) if ((t & 0xff) == 1) none = true;
            if (!none) throw new IOException("RFB backend requires authentication; configure local no-auth socket");
            out.writeByte(1); out.flush();
            int sec = in.readInt();
            if (sec != 0) throw new IOException("RFB security failed: " + readReason());

            out.writeByte(1); out.flush(); // shared desktop
            width = in.readUnsignedShort();
            height = in.readUnsignedShort();
            byte[] pf = new byte[16]; in.readFully(pf);
            int nameLen = in.readInt();
            if (nameLen < 0 || nameLen > 1024 * 1024) throw new IOException("Bad desktop name length");
            byte[] name = new byte[nameLen]; in.readFully(name);
            pixels = new int[Math.max(1, width * height)];

            setPixelFormat();
            setEncodings();
            connected = true;
            status.update("Connected · " + width + "×" + height + " · " +
                    new String(name, StandardCharsets.UTF_8));
            framebufferRequest(false);

            while (connected && !socket.isClosed()) {
                int type = in.readUnsignedByte();
                if (type == 0) readFramebufferUpdate();
                else if (type == 2) { /* bell */ }
                else if (type == 3) {
                    in.skipBytes(3);
                    int len = in.readInt();
                    if (len < 0 || len > 16 * 1024 * 1024) throw new IOException("Bad server clipboard");
                    byte[] clip = new byte[len]; in.readFully(clip);
                } else throw new IOException("Unsupported RFB server message " + type);
                framebufferRequest(true);
            }
        }

        private String readReason() throws IOException {
            int n = in.readInt();
            if (n < 0 || n > 1024 * 1024) return "unknown";
            byte[] b = new byte[n]; in.readFully(b);
            return new String(b, StandardCharsets.UTF_8);
        }

        private void setPixelFormat() throws IOException {
            synchronized (this) {
                out.writeByte(0); out.write(new byte[3]);
                out.writeByte(32); out.writeByte(24); out.writeByte(0); out.writeByte(1);
                out.writeShort(255); out.writeShort(255); out.writeShort(255);
                out.writeByte(16); out.writeByte(8); out.writeByte(0);
                out.write(new byte[3]); out.flush();
            }
        }

        private void setEncodings() throws IOException {
            synchronized (this) {
                out.writeByte(2); out.writeByte(0); out.writeShort(2);
                out.writeInt(0);       // Raw
                out.writeInt(-223);    // DesktopSize pseudo-encoding
                out.flush();
            }
        }

        private void framebufferRequest(boolean incremental) throws IOException {
            synchronized (this) {
                out.writeByte(3); out.writeByte(incremental ? 1 : 0);
                out.writeShort(0); out.writeShort(0); out.writeShort(width); out.writeShort(height);
                out.flush();
            }
        }

        private void readFramebufferUpdate() throws IOException {
            in.readUnsignedByte();
            int rects = in.readUnsignedShort();
            for (int r = 0; r < rects; r++) {
                int x=in.readUnsignedShort(), y=in.readUnsignedShort(),
                        w=in.readUnsignedShort(), h=in.readUnsignedShort();
                int enc=in.readInt();
                if (enc == -223) {
                    width=w; height=h; pixels=new int[Math.max(1,w*h)];
                    continue;
                }
                if (enc != 0) throw new IOException("RFB encoding " + enc + " not supported");
                byte[] row = new byte[w * 4];
                for (int yy=0; yy<h; yy++) {
                    in.readFully(row);
                    int dst=(y+yy)*width+x;
                    for (int xx=0; xx<w && dst+xx<pixels.length; xx++) {
                        int i=xx*4;
                        int b=row[i]&255,g=row[i+1]&255,rr=row[i+2]&255;
                        pixels[dst+xx]=0xff000000|(rr<<16)|(g<<8)|b;
                    }
                }
            }
            sink.frame(width,height,pixels.clone());
        }

        void pointer(int mask,int x,int y) {
            try {
                synchronized (this) {
                    if (!connected || out == null) return;
                    out.writeByte(5); out.writeByte(mask); out.writeShort(x); out.writeShort(y); out.flush();
                }
            } catch (Throwable ignored) {}
        }

        void type(char c) { key(c,true,true); }

        void key(int sym, boolean down, boolean tap) {
            try {
                synchronized (this) {
                    if (!connected || out == null) return;
                    out.writeByte(4); out.writeByte(down?1:0); out.writeShort(0); out.writeInt(sym);
                    if (tap) { out.writeByte(4); out.writeByte(0); out.writeShort(0); out.writeInt(sym); }
                    out.flush();
                }
            } catch (Throwable ignored) {}
        }

        void close() {
            connected=false;
            try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
        }
    }
}
