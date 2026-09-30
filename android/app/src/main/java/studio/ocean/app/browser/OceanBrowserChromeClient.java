package studio.ocean.app.browser;

import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Progress, title, and popup-window handling for Ocean Browser. */
public final class OceanBrowserChromeClient extends WebChromeClient {
    public interface Callback {
        void onProgressChanged(int progress);

        void onReceivedTitle(String title);

        void onCreateWindow(WebView view, String url);
    }

    private final Callback callback;

    public OceanBrowserChromeClient(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        callback.onProgressChanged(newProgress);
    }

    @Override
    public void onReceivedTitle(WebView view, String title) {
        callback.onReceivedTitle(title);
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
        WebView.HitTestResult result = view.getHitTestResult();
        String url = result.getExtra();
        if (url != null && !url.isEmpty()) {
            callback.onCreateWindow(view, url);
            return false;
        }
        WebView newWebView = new WebView(view.getContext());
        newWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView child, android.webkit.WebResourceRequest request) {
                callback.onCreateWindow(view, request.getUrl().toString());
                return true;
            }
        });
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(newWebView);
        resultMsg.sendToTarget();
        return true;
    }
}
