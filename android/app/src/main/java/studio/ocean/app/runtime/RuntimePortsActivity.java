package studio.ocean.app.runtime;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.R;

/** Loopback-only runtime browser. It intentionally exposes no JavaScript-to-native bridge. */
public final class RuntimePortsActivity extends AppCompatActivity {
    public static final String EXTRA_PORT = "runtime_port", EXTRA_PATH = "runtime_path";
    private static volatile WeakReference<RuntimePortsActivity> active = new WeakReference<>(null);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout portsList;
    private ScrollView listScroll;
    private WebView webView;
    private View browserBar;
    private TextView scanStatus, subtitle, urlView;
    private volatile boolean pageLoaded;
    private volatile String currentUrl = "";
    private volatile int currentPort;
    private volatile boolean pageFailed;
    private float captureScaleX = 1f, captureScaleY = 1f;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_runtime_ports);
        portsList = findViewById(R.id.runtime_ports_list);
        listScroll = findViewById(R.id.runtime_list_scroll);
        webView = findViewById(R.id.runtime_webview);
        browserBar = findViewById(R.id.runtime_browser_bar);
        scanStatus = findViewById(R.id.runtime_scan_status);
        subtitle = findViewById(R.id.runtime_subtitle);
        urlView = findViewById(R.id.runtime_url);
        configureWebView();
        findViewById(R.id.runtime_back).setOnClickListener(v -> navigateBack());
        findViewById(R.id.runtime_refresh).setOnClickListener(v -> { if (webView.getVisibility() == View.VISIBLE) webView.reload(); else refreshPorts(); });
        findViewById(R.id.runtime_browser_back).setOnClickListener(v -> showPortList());
        findViewById(R.id.runtime_browser_reload).setOnClickListener(v -> webView.reload());
        findViewById(R.id.runtime_manual_open).setOnClickListener(v -> openManual());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { navigateBack(); }
        });
        int port = getIntent().getIntExtra(EXTRA_PORT, 0);
        if (port > 0) openPort(port, getIntent().getStringExtra(EXTRA_PATH)); else refreshPorts();
    }

    @Override protected void onResume() { super.onResume(); active = new WeakReference<>(this); }
    @Override protected void onPause() {
        RuntimePortsActivity value = active.get();
        if (value == this) active = new WeakReference<>(null);
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            if (webView.getParent() instanceof ViewGroup) ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isLoopback(request.getUrl());
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pageLoaded = false; pageFailed = false;
                currentUrl = url; urlView.setText(url);
                subtitle.setText("Loading local service…");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if(request.isForMainFrame()){pageFailed=true; subtitle.setText("Local service unavailable");}
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoaded = !pageFailed;
                if(pageFailed)return;
                currentUrl = url; urlView.setText(url);
                subtitle.setText(view.getTitle() == null || view.getTitle().trim().isEmpty() ? "Connected to localhost:" + currentPort : view.getTitle());
            }
        });
    }

    private void refreshPorts() {
        scanStatus.setText("Scanning local listeners…");
        portsList.removeAllViews();
        new Thread(() -> {
            List<Integer> ports = RuntimePortScanner.scan();
            runOnUiThread(() -> renderPorts(ports));
        }, "ocean-port-scan").start();
    }

    private void renderPorts(List<Integer> ports) {
        portsList.removeAllViews();
        scanStatus.setText(ports.isEmpty() ? "No local listeners found" : ports.size() + (ports.size() == 1 ? " listener ready" : " listeners ready"));
        if (ports.isEmpty()) {
            TextView empty = text("Start a server in Ocean Terminal, then refresh. Android may hide the full port table; common ports are probed and any other port can be opened manually.", 14, 0xFF7B7873);
            empty.setPadding(0, dp(8), 0, dp(12));
            portsList.addView(empty);
            return;
        }
        for (int port : ports) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(13), dp(10), dp(13));
            card.setBackgroundResource(R.drawable.auth_field_background);
            LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
            TextView title = text("localhost:" + port, 15, 0xFF191817); title.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView kind = text(RuntimePortScanner.kind(port), 12, 0xFF7B7873); kind.setPadding(0, dp(3), 0, 0);
            copy.addView(title); copy.addView(kind);
            card.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            Button open = new Button(this); open.setText("Open"); open.setAllCaps(false); open.setTextColor(0xFF191817); open.setBackgroundResource(android.R.drawable.list_selector_background);
            open.setOnClickListener(v -> openPort(port, null));
            card.addView(open, new LinearLayout.LayoutParams(dp(76), dp(44)));
            portsList.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View divider = new View(this); divider.setBackgroundColor(0xFFE5E3E0);
            LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)); d.setMargins(dp(14), dp(8), dp(14), dp(8));
            portsList.addView(divider, d);
        }
    }

    private TextView text(String value, int size, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void openManual() {
        EditText field = findViewById(R.id.runtime_manual_port);
        try { openPort(Integer.parseInt(field.getText().toString().trim()), null); }
        catch (Exception error) { Toast.makeText(this, "Enter a port from 1 to 65535", Toast.LENGTH_SHORT).show(); }
    }

    private void openPort(int port, String path) {
        if (port < 1 || port > 65535) { Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show(); return; }
        if (path == null || path.isEmpty()) path = "/";
        if (!path.startsWith("/") || path.contains("\u0000")) { Toast.makeText(this, "Invalid local path", Toast.LENGTH_SHORT).show(); return; }
        currentPort = port;
        pageLoaded = false; pageFailed = false;
        listScroll.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        browserBar.setVisibility(View.VISIBLE);
        String url = "http://localhost:" + port + path;
        currentUrl = url; urlView.setText(url);
        webView.loadUrl(url);
    }

    private void showPortList() {
        webView.stopLoading();
        webView.setVisibility(View.GONE);
        browserBar.setVisibility(View.GONE);
        listScroll.setVisibility(View.VISIBLE);
        subtitle.setText(R.string.runtime_ports_subtitle);
        currentPort = 0;
        refreshPorts();
    }

    private void navigateBack() {
        if (webView.getVisibility() == View.VISIBLE && webView.canGoBack()) webView.goBack();
        else if (webView.getVisibility() == View.VISIBLE) showPortList();
        else finish();
    }

    private static boolean isLoopback(Uri uri) {
        String scheme = uri.getScheme(), host = uri.getHost();
        return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                && host != null && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1"));
    }

    public static JSONObject listForAgent() throws Exception {
        JSONArray values = new JSONArray();
        for (int port : RuntimePortScanner.scan()) values.put(new JSONObject().put("port", port).put("url", "http://localhost:" + port + "/").put("kind", RuntimePortScanner.kind(port)));
        return new JSONObject().put("ports", values).put("count", values.length()).put("exit_code", 0);
    }

    public static JSONObject openForAgent(Context context, int port, String path) throws Exception {
        if (path == null || path.isEmpty()) path = "/";
        final String requestedPath = path;
        Throwable[] launchFailure = new Throwable[1];
        java.util.concurrent.atomic.AtomicBoolean launchExpired = new java.util.concurrent.atomic.AtomicBoolean();
        CountDownLatch launched = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (launchExpired.get()) return;
                context.startActivity(new Intent(context, RuntimePortsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_PORT, port).putExtra(EXTRA_PATH, requestedPath));
            } catch (Exception error) { launchFailure[0] = error; }
            finally { launched.countDown(); }
        });
        try { if (!launched.await(10, TimeUnit.SECONDS)) throw new IOException("Runtime Ports did not open"); }
        finally { launchExpired.set(true); }
        if (launchFailure[0] != null) throw new IOException("Runtime Ports could not open", launchFailure[0]);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        RuntimePortsActivity activity = null;
        while (System.nanoTime() < deadline) {
            activity = active.get();
            if (activity != null && activity.currentPort == port && activity.pageLoaded) break;
            Thread.sleep(100);
        }
        if (activity == null || activity.currentPort != port || !activity.pageLoaded) throw new IOException("Runtime Ports screen is not active");
        JSONObject result = activity.perform(new JSONObject().put("action", "snapshot"));
        result.put("opened", true).put("port", port).put("exit_code", result.has("error") ? -1 : 0);
        return result;
    }

    public static JSONObject interactForAgent(JSONObject arguments) throws Exception {
        RuntimePortsActivity activity = active.get();
        if (activity == null || activity.currentPort == 0 || !activity.pageLoaded)
            throw new IOException("Open a runtime port before interacting with it");
        return activity.perform(arguments);
    }

    private JSONObject perform(JSONObject arguments) throws Exception {
        String action = arguments.getString("action");
        if (action.equals("wait")) Thread.sleep(arguments.optInt("wait_ms", 750));
        CountDownLatch complete = new CountDownLatch(1);
        JSONObject[] result = new JSONObject[1]; Throwable[] failure = new Throwable[1];
        java.util.concurrent.atomic.AtomicBoolean expired = new java.util.concurrent.atomic.AtomicBoolean();
        handler.post(() -> {
            try {
                if (expired.get() || active.get() != this || currentPort == 0) throw new IOException("Runtime page is no longer active");
                if (action.equals("snapshot") || action.equals("wait")) evaluateSnapshot(value -> { result[0] = value; complete.countDown(); });
                else if (action.equals("screenshot")) { result[0] = capture(); complete.countDown(); }
                else if (action.equals("reload")) { webView.reload(); result[0] = ok("Reloaded " + webView.getUrl()); complete.countDown(); }
                else if (action.equals("scroll")) { webView.scrollBy(0, arguments.optInt("delta_y", 600)); result[0] = ok("Scrolled"); complete.countDown(); }
                else if ((action.equals("click") || action.equals("double_click")) && arguments.has("x")) { click(arguments.getInt("x"), arguments.getInt("y")); if (action.equals("double_click")) click(arguments.getInt("x"), arguments.getInt("y")); result[0] = ok(action.equals("click") ? "Clicked viewport coordinates" : "Double-clicked viewport coordinates"); complete.countDown(); }
                else if (action.equals("drag")) { drag(arguments.getInt("x"), arguments.getInt("y"), arguments.getInt("to_x"), arguments.getInt("to_y")); result[0] = ok("Dragged across viewport"); complete.countDown(); }
                else if (action.equals("key")) { sendKey(arguments.getString("text")); result[0] = ok("Key sent"); complete.countDown(); }
                else if (action.equals("type") && !arguments.has("target")) { typeNative(arguments.getString("text")); result[0] = ok("Text sent to focused control"); complete.countDown(); }
                else runDomAction(action, arguments, value -> { result[0] = value; complete.countDown(); });
            } catch (Throwable error) { failure[0] = error; complete.countDown(); }
        });
        try { if (!complete.await(15, TimeUnit.SECONDS)) throw new IOException("Runtime page action timed out"); }
        finally { expired.set(true); }
        if (failure[0] != null) throw new IOException(failure[0].getMessage(), failure[0]);
        if (result[0] == null) throw new IOException("Runtime page returned no result");
        return result[0].put("url", currentUrl).put("exit_code", result[0].has("error") ? -1 : 0);
    }

    private interface JsonCallback { void complete(JSONObject value); }
    private void evaluateSnapshot(JsonCallback callback) {
        String script = "(()=>{const q='a,button,input,textarea,select,[role=button],[onclick],canvas';const es=[...document.querySelectorAll(q)].slice(0,120);const stamp=Date.now().toString(36);const controls=es.map((e,i)=>{let r='ref_'+stamp+'_'+(i+1);e.setAttribute('data-ocean-ref',r);const b=e.getBoundingClientRect();return {ref:r,tag:e.tagName.toLowerCase(),type:e.type||'',text:(e.type==='password'?'[protected]':(e.innerText||e.value||e.getAttribute('aria-label')||e.title||'')).trim().slice(0,180),x:Math.round(b.x),y:Math.round(b.y),width:Math.round(b.width),height:Math.round(b.height),disabled:!!e.disabled};});return JSON.stringify({title:document.title,url:location.href,text:(document.body?.innerText||'').slice(0,16000),controls});})()";
        webView.evaluateJavascript(script, raw -> {
            try { callback.complete(new JSONObject(decodeJavascript(raw)).put("page_loaded", pageLoaded)); }
            catch (Exception error) { callback.complete(failure("Could not inspect page: " + error.getMessage())); }
        });
    }

    private void runDomAction(String action, JSONObject args, JsonCallback callback) throws Exception {
        String target = JSONObject.quote(args.optString("target", ""));
        String text = JSONObject.quote(args.optString("text", ""));
        String script = "(()=>{try{const t=" + target + ";let e=Array.from(document.querySelectorAll('[data-ocean-ref]')).find(n=>n.getAttribute('data-ocean-ref')===t);if(!e&&t)e=document.querySelector(t);if(!e)return JSON.stringify({error:'Target not found'});"
                + (action.equals("click") || action.equals("double_click") ? "e.click();" + (action.equals("double_click") ? "e.click();e.dispatchEvent(new MouseEvent('dblclick',{bubbles:true}));" : "") : "e.focus();const v=" + text + ";const p=Object.getPrototypeOf(e);const s=Object.getOwnPropertyDescriptor(p,'value')?.set;if(s)s.call(e,v);else e.value=v;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));")
                + "return JSON.stringify({ok:true,target:t});}catch(x){return JSON.stringify({error:String(x)});}})()";
        webView.evaluateJavascript(script, raw -> {
            try { callback.complete(new JSONObject(decodeJavascript(raw))); }
            catch (Exception error) { callback.complete(failure(error.getMessage())); }
        });
    }

    private static String decodeJavascript(String raw) throws Exception {
        if (raw == null || raw.equals("null")) return "{}";
        return new JSONArray("[" + raw + "]").getString(0);
    }

    private JSONObject capture() throws Exception {
        int sourceWidth = Math.max(1, webView.getWidth()), sourceHeight = Math.max(1, webView.getHeight());
        float scale = Math.min(1f, Math.min(1200f / Math.max(sourceWidth, sourceHeight), (float)Math.sqrt(1100000d / ((double)sourceWidth * sourceHeight))));
        int width = Math.max(1, Math.round(sourceWidth * scale)), height = Math.max(1, Math.round(sourceHeight * scale));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.scale(scale, scale); webView.draw(canvas);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 70, bytes); bitmap.recycle();
        captureScaleX = (float) sourceWidth / width; captureScaleY = (float) sourceHeight / height;
        return new JSONObject().put("image_base64", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP))
                .put("media_type", "image/jpeg").put("image_width", width).put("image_height", height)
                .put("instruction", "Use coordinates in this image for the next click; Ocean maps them to the live viewport.");
    }

    private void click(int x, int y) {
        float px = x * captureScaleX, py = y * captureScaleY; long now = android.os.SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,px,py,0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,px,py,0);
        webView.dispatchTouchEvent(down); webView.dispatchTouchEvent(up); down.recycle(); up.recycle();
    }

    private void drag(int x, int y, int toX, int toY) {
        float sx=x*captureScaleX, sy=y*captureScaleY, ex=toX*captureScaleX, ey=toY*captureScaleY; long now=android.os.SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,sx,sy,0);webView.dispatchTouchEvent(event);event.recycle();
        for(int i=1;i<=8;i++){event=MotionEvent.obtain(now,now+i*25L,MotionEvent.ACTION_MOVE,sx+(ex-sx)*i/8f,sy+(ey-sy)*i/8f,0);webView.dispatchTouchEvent(event);event.recycle();}
        event=MotionEvent.obtain(now,now+225,MotionEvent.ACTION_UP,ex,ey,0);webView.dispatchTouchEvent(event);event.recycle();
    }

    private void typeNative(String value) {
        KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(value.toCharArray());
        if (events != null) for (KeyEvent event : events) webView.dispatchKeyEvent(event);
        else webView.evaluateJavascript("(()=>{const e=document.activeElement;if(!e)return;e.value=(e.value||'')+" + JSONObject.quote(value) + ";e.dispatchEvent(new Event('input',{bubbles:true}));})()", null);
    }

    private void sendKey(String value) {
        String[] parts = value.toLowerCase(Locale.ROOT).split("\\+");
        int meta = 0; String key = parts[parts.length - 1];
        for (int i = 0; i < parts.length - 1; i++) { if (parts[i].equals("ctrl")) meta |= KeyEvent.META_CTRL_ON; if (parts[i].equals("alt")) meta |= KeyEvent.META_ALT_ON; if (parts[i].equals("shift")) meta |= KeyEvent.META_SHIFT_ON; }
        int code;
        switch (key) {
            case "enter": code=KeyEvent.KEYCODE_ENTER; break; case "tab": code=KeyEvent.KEYCODE_TAB; break; case "escape": case "esc": code=KeyEvent.KEYCODE_ESCAPE; break;
            case "backspace": code=KeyEvent.KEYCODE_DEL; break; case "delete": code=KeyEvent.KEYCODE_FORWARD_DEL; break; case "left": code=KeyEvent.KEYCODE_DPAD_LEFT; break;
            case "right": code=KeyEvent.KEYCODE_DPAD_RIGHT; break; case "up": code=KeyEvent.KEYCODE_DPAD_UP; break; case "down": code=KeyEvent.KEYCODE_DPAD_DOWN; break;
            default: code = key.length() == 1 ? KeyEvent.keyCodeFromString("KEYCODE_" + key.toUpperCase(Locale.ROOT)) : KeyEvent.KEYCODE_UNKNOWN;
        }
        if (code == KeyEvent.KEYCODE_UNKNOWN) throw new IllegalArgumentException("Unsupported key: " + value);
        long now = android.os.SystemClock.uptimeMillis();
        webView.dispatchKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta));
        webView.dispatchKeyEvent(new KeyEvent(now, now + 30, KeyEvent.ACTION_UP, code, 0, meta));
    }

    private static JSONObject ok(String text) { try { return new JSONObject().put("ok", true).put("result", text); } catch (Exception impossible) { return new JSONObject(); } }
    private static JSONObject failure(String message) { try { return new JSONObject().put("error", message); } catch (Exception e) { return new JSONObject(); } }

}
