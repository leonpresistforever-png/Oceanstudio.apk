package studio.ocean.app.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Interactive edge-borderless 3D Robotic Eye component for the OceanStudio landing surface.
 * Embeds the official Sketchfab 3D WebGL model with transparent canvas, hardware acceleration,
 * touch disallow-interception for smooth orbiting, and strict navigation locking.
 */
public class RoboticEyeView extends WebView {

    private static final String ROBOTIC_EYE_HTML = "<!DOCTYPE html>\n"
            + "<html>\n"
            + "<head>\n"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no, maximum-scale=1.0\">\n"
            + "<style>\n"
            + "  * { margin: 0; padding: 0; box-sizing: border-box; }\n"
            + "  html, body { width: 100%; height: 100%; overflow: hidden; background: transparent; display: flex; align-items: center; justify-content: center; }\n"
            + "  .sketchfab-embed-wrapper { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; position: relative; }\n"
            + "  iframe { width: 100%; height: 100%; border: 0; outline: none; background: transparent; }\n"
            + "  p, .credit, a { display: none !important; pointer-events: none !important; opacity: 0 !important; }\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<div class=\"sketchfab-embed-wrapper\">\n"
            + "  <iframe title=\"Robotic Eye\" frameborder=\"0\" allowfullscreen mozallowfullscreen=\"true\" webkitallowfullscreen=\"true\" allow=\"autoplay; fullscreen; xr-spatial-tracking\" xr-spatial-tracking execution-while-out-of-viewport execution-while-not-rendered web-share src=\"https://sketchfab.com/models/7b7ecc70526a4261a705f64655c13aa5/embed?autostart=1&preload=1&transparent=1&ui_infos=0&ui_watermark=0&ui_help=0&ui_settings=0&ui_inspector=0&ui_annotations=0&ui_stop=0&ui_vr=0&ui_fullscreen=0\">\n"
            + "  </iframe>\n"
            + "  <p style=\"font-size: 13px; font-weight: normal; margin: 5px; color: #4A4A4A;\">\n"
            + "    <a href=\"https://sketchfab.com/3d-models/robotic-eye-7b7ecc70526a4261a705f64655c13aa5\" target=\"_blank\" rel=\"nofollow\" style=\"font-weight: bold; color: #1CAAD9;\"> Robotic Eye </a> by <a href=\"https://sketchfab.com/Oleksii.Rozumnyi\" target=\"_blank\" rel=\"nofollow\" style=\"font-weight: bold; color: #1CAAD9;\"> Oleksii Rozumnyi </a> on <a href=\"https://sketchfab.com\" target=\"_blank\" rel=\"nofollow\" style=\"font-weight: bold; color: #1CAAD9;\">Sketchfab</a>\n"
            + "  </p>\n"
            + "</div>\n"
            + "</body>\n"
            + "</html>";

    public RoboticEyeView(Context context) {
        super(context);
        init();
    }

    public RoboticEyeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RoboticEyeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);

        // Lock navigation so clicking anywhere on the 3D model does NOT redirect the user
        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true; // Disallow external redirects
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true; // Disallow external redirects
            }
        });

        loadDataWithBaseURL("https://sketchfab.com", ROBOTIC_EYE_HTML, "text/html", "UTF-8", null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Prevent parent ScrollView from stealing touch/drag gestures while interacting with 3D eye
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }
        return super.onTouchEvent(event);
    }
}
