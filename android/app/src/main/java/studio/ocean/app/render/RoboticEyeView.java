package studio.ocean.app.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/** Offline GLB eye viewer. Only bundled assets are served; no external page or controls are loaded. */
public class RoboticEyeView extends WebView {
    private static final String ASSET_HOST = "oceanstudio.local";
    private static final String ASSET_ROOT = "ocean/robotic-eye/";
    private boolean pageReady;
    private boolean typing;

    public RoboticEyeView(Context context) { super(context); init(); }
    public RoboticEyeView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public RoboticEyeView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    @SuppressLint("SetJavaScriptEnabled")
    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                applyTypingState();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serveAsset(request.getUrl().getHost(), request.getUrl().getPath());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }
        });
        loadUrl("https://" + ASSET_HOST + "/" + ASSET_ROOT + "index.html");
    }

    private WebResourceResponse serveAsset(String host, String path) {
        if (!ASSET_HOST.equals(host) || path == null || path.contains("..")) return null;
        String relative = path.startsWith("/ocean/robotic-eye/")
                ? path.substring("/ocean/robotic-eye/".length()) : null;
        if (relative == null || relative.isEmpty()) return null;
        final String mime;
        if (relative.endsWith(".html")) mime = "text/html";
        else if (relative.endsWith(".js")) mime = "text/javascript";
        else if (relative.endsWith(".glb")) mime = "model/gltf-binary";
        else if (relative.endsWith(".txt")) mime = "text/plain";
        else return null;
        try {
            InputStream body = getContext().getAssets().open(ASSET_ROOT + relative);
            return new WebResourceResponse(mime, "UTF-8", 200, "OK", Collections.singletonMap("Access-Control-Allow-Origin", "https://" + ASSET_HOST), body);
        } catch (IOException missing) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(), null);
        }
    }

    /** The native prompt forwards focus and non-empty input so the model can lower its gaze. */
    public void setTyping(boolean typing) {
        this.typing = typing;
        if (pageReady) applyTypingState();
    }

    private void applyTypingState() {
        evaluateJavascript("window.oceanEyeSetTyping && window.oceanEyeSetTyping(" + typing + ")", null);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.onTouchEvent(event);
    }
}
