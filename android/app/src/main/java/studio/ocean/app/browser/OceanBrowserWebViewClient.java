package studio.ocean.app.browser;

import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Handles navigation, external intents, and error reporting for Ocean Browser. */
public final class OceanBrowserWebViewClient extends WebViewClient {
    public interface Callback {
        void onPageStarted(String url);

        void onPageFinished(String url, boolean failed);

        void onReceivedError(String url, CharSequence description);

        boolean onExternalNavigation(Uri uri);

        void onSslError(SslErrorHandler handler, SslError error);
    }

    private final Callback callback;
    private boolean mainFrameFailed;

    public OceanBrowserWebViewClient(Callback callback) {
        this.callback = callback;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        Uri uri = request.getUrl();
        if (BrowserUrlHelper.shouldLeaveWebView(uri)) {
            return callback.onExternalNavigation(uri);
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Uri uri = Uri.parse(url);
        if (BrowserUrlHelper.shouldLeaveWebView(uri)) {
            return callback.onExternalNavigation(uri);
        }
        return false;
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        mainFrameFailed = false;
        callback.onPageStarted(url);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request.isForMainFrame()) {
            mainFrameFailed = true;
            CharSequence description = error.getDescription();
            callback.onReceivedError(request.getUrl().toString(), description == null ? "Load failed" : description);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
        mainFrameFailed = true;
        callback.onReceivedError(failingUrl, description == null ? "Load failed" : description);
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        callback.onPageFinished(url, mainFrameFailed);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        callback.onSslError(handler, error);
    }
}
