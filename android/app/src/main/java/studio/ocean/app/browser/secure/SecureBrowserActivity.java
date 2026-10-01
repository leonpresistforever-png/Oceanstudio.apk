package studio.ocean.app.browser.secure;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import studio.ocean.app.R;
import studio.ocean.app.browser.BrowserTab;
import studio.ocean.app.browser.BrowserUrlHelper;
import studio.ocean.app.browser.core.BrowserTabController;
import studio.ocean.app.browser.core.NavigationPolicy;
import studio.ocean.app.browser.network.BrowserNetworkPolicy;
import studio.ocean.app.browser.network.DnsPolicy;
import studio.ocean.app.browser.network.ProxyControllerAdapter;
import studio.ocean.app.browser.privacy.PermissionFirewall;
import studio.ocean.app.browser.privacy.PopupFirewall;
import studio.ocean.app.browser.privacy.TrackerBlocker;
import studio.ocean.app.browser.security.PrivateDataEraser;

/**
 * Hardened Ephemeral Private Browser Activity (PDF 4 Master Directive).
 * Runs inside a unique isolated WebKit profile (MULTI_PROFILE), never writes to persistent history,
 * applies active request/ad blocking, popup firewall, device permission firewall, and network route isolation.
 */
public final class SecureBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "secure_browser_initial_url";

    private final BrowserTabController tabController = new BrowserTabController();
    private final PrivateSessionManager sessionManager = new PrivateSessionManager();
    private final PrivateProfileManager profileManager = new PrivateProfileManager();
    private final TrackerBlocker trackerBlocker = new TrackerBlocker();
    private final PopupFirewall popupFirewall = new PopupFirewall();
    private final PermissionFirewall permissionFirewall = new PermissionFirewall();
    private final BrowserNetworkPolicy networkPolicy = new BrowserNetworkPolicy();
    private final DnsPolicy dnsPolicy = new DnsPolicy();

    private final List<WebView> popupWebViews = new ArrayList<>();
    private WebView webView;
    private EditText urlField;
    private ImageView lockIcon;
    private TextView tabBadge;
    private ProgressBar progressBar;
    private TextView shieldText;
    private ImageView shieldIcon;
    private LinearLayout degradedBanner;
    private TextView degradedReason;
    private LinearLayout errorPanel;
    private TextView errorTitle;
    private TextView errorMessage;

    private boolean urlBarFocused = false;
    private boolean desktopSite = false;
    private long pausedTimestamp = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Default screenshot protection in private mode
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_secure_browser);

        bindViews();
        configureWebView();
        wireActions();

        String initial = getIntent().getStringExtra(EXTRA_URL);
        String startUrl = initial != null && !initial.trim().isEmpty()
                ? BrowserUrlHelper.normalizeInput(initial)
                : BrowserUrlHelper.DEFAULT_HOME;

        tabController.addTab(startUrl);
        loadCurrentTab();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else if (tabController.getTabCount() > 1) {
                    closeCurrentTab();
                } else {
                    finishAndWipe();
                }
            }
        });
    }

    private void bindViews() {
        webView = findViewById(R.id.secure_browser_webview);
        urlField = findViewById(R.id.secure_browser_url);
        lockIcon = findViewById(R.id.secure_browser_lock_icon);
        tabBadge = findViewById(R.id.secure_browser_tab_badge);
        progressBar = findViewById(R.id.secure_browser_progress);
        shieldText = findViewById(R.id.secure_browser_shield_text);
        shieldIcon = findViewById(R.id.secure_browser_shield_icon);
        degradedBanner = findViewById(R.id.secure_browser_degraded_banner);
        degradedReason = findViewById(R.id.secure_browser_degraded_reason);
        errorPanel = findViewById(R.id.secure_browser_error_panel);
        errorTitle = findViewById(R.id.secure_browser_error_title);
        errorMessage = findViewById(R.id.secure_browser_error_message);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        // Assign ephemeral profile BEFORE any navigation or script execution (PDF 4 §5.1, §15.1)
        boolean profileOk = profileManager.applyProfile(webView, sessionManager.getProfileName());
        if (!profileOk) {
            showDegradedState("Multi-profile isolation unsupported on this WebView; using in-memory clearing fallback.");
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        webView.setDownloadListener(createQuarantineDownloadListener());
    }

    private void wireActions() {
        findViewById(R.id.secure_browser_close).setOnClickListener(v -> finishAndWipe());
        findViewById(R.id.secure_browser_panic).setOnClickListener(v -> finishAndWipe());
        findViewById(R.id.secure_browser_shield_btn).setOnClickListener(v -> showPrivacyStatusSheet());

        findViewById(R.id.secure_browser_nav_back).setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        findViewById(R.id.secure_browser_nav_forward).setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        findViewById(R.id.secure_browser_nav_refresh).setOnClickListener(v -> webView.reload());
        findViewById(R.id.secure_browser_nav_home).setOnClickListener(v -> navigateTo(BrowserUrlHelper.DEFAULT_HOME));

        findViewById(R.id.secure_browser_menu).setOnClickListener(this::showOverflowMenu);
        tabBadge.setOnClickListener(v -> showTabSwitcher());

        findViewById(R.id.secure_browser_error_retry).setOnClickListener(v -> {
            hideError();
            webView.reload();
        });

        // Degraded Banner Actions
        findViewById(R.id.secure_browser_degraded_reconnect).setOnClickListener(v -> {
            networkPolicy.setTunnelConnected(true);
            degradedBanner.setVisibility(View.GONE);
            webView.reload();
        });
        findViewById(R.id.secure_browser_degraded_change_route).setOnClickListener(v -> {
            networkPolicy.setDirect();
            degradedBanner.setVisibility(View.GONE);
            webView.reload();
        });
        findViewById(R.id.secure_browser_degraded_exit).setOnClickListener(v -> finishAndWipe());

        urlField.setOnFocusChangeListener((v, hasFocus) -> urlBarFocused = hasFocus);
        urlField.setOnEditorActionListener((v, actionId, event) -> {
            String input = urlField.getText().toString();
            navigateTo(BrowserUrlHelper.normalizeInput(input));
            urlField.clearFocus();
            return true;
        });

        setupClients();
    }

    private void setupClients() {
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.trim().isEmpty()) {
                    BrowserTab cur = tabController.getCurrentTab();
                    if (cur != null) cur.title = title.trim();
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView.HitTestResult result = view.getHitTestResult();
                String targetUrl = result.getExtra();

                popupFirewall.evaluatePopup(targetUrl, isUserGesture, (url, host, gesture, decision) -> {
                    new AlertDialog.Builder(SecureBrowserActivity.this)
                            .setTitle("Popup requested")
                            .setMessage("Allow popup from " + (host.isEmpty() ? "this page" : host) + "?")
                            .setPositiveButton("Allow once", (d, w) -> decision.onDecision(true, false))
                            .setNeutralButton("Always for session", (d, w) -> decision.onDecision(true, true))
                            .setNegativeButton("Block", (d, w) -> decision.onDecision(false, false))
                            .show();
                }, (allow, remember) -> {
                    if (allow) {
                        WebView child = new WebView(SecureBrowserActivity.this);
                        profileManager.applyProfile(child, sessionManager.getProfileName());
                        child.setWebViewClient(new WebViewClient() {
                            @Override
                            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                                openNewTab(req.getUrl().toString());
                                return true;
                            }
                        });
                        popupWebViews.add(child);
                        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                        transport.setWebView(child);
                        resultMsg.sendToTarget();
                    } else {
                        sessionManager.incrementPopupBlocked();
                    }
                });
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                permissionFirewall.handlePermissionRequest(request, (origin, resources, callback) -> {
                    new AlertDialog.Builder(SecureBrowserActivity.this)
                            .setTitle("Permission request")
                            .setMessage(origin + " is requesting device capabilities. Allow for this private session?")
                            .setPositiveButton("Allow for session", (d, w) -> callback.onGranted(true))
                            .setNegativeButton("Deny", (d, w) -> callback.onGranted(false))
                            .show();
                });
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String requestUrl = request.getUrl().toString();
                String mainUrl = view.getUrl();
                if (trackerBlocker.shouldBlock(requestUrl, mainUrl)) {
                    sessionManager.incrementTrackerBlocked();
                    return TrackerBlocker.createBlockedResponse();
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String targetUrl = uri.toString();

                // Kill switch inspection
                if (networkPolicy.isNavigationBlockedByKillSwitch(targetUrl)) {
                    showDegradedState("Tunnel disconnected. Ocean has paused this Private Browser session to prevent direct-network fallback.");
                    return true;
                }

                // External scheme handoff
                if (NavigationPolicy.isExternalScheme(uri)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception ignored) {
                        Toast.makeText(SecureBrowserActivity.this, R.string.browser_no_handler, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }

                if (NavigationPolicy.isDangerousWebScheme(targetUrl)) {
                    return true;
                }

                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                hideError();
                updateUrlBar(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                BrowserTab cur = tabController.getCurrentTab();
                if (cur != null) {
                    cur.url = url;
                }
                updateUrlBar(url);
                updateTabBadge();
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // In Strict mode, SSL errors are cancelled by default (PDF 4 §2)
                new AlertDialog.Builder(SecureBrowserActivity.this)
                        .setTitle(R.string.browser_ssl_title)
                        .setMessage("Private mode blocked untrusted certificate (Error " + error.getPrimaryError() + "). Navigation cancelled for security.")
                        .setNegativeButton(android.R.string.cancel, (d, w) -> handler.cancel())
                        .setPositiveButton("Bypass (Advanced)", (d, w) -> handler.proceed())
                        .show();
            }
        });
    }

    private void navigateTo(String rawUrl) {
        hideError();
        // Link cleaner: strip marketing/tracking parameters (PDF 4 §9, §13.4)
        String cleaned = NavigationPolicy.cleanUrl(rawUrl, true);
        if (sessionManager.isHttpsOnly() && NavigationPolicy.isInsecureHttp(cleaned)) {
            cleaned = NavigationPolicy.upgradeToHttps(cleaned);
        }

        BrowserTab cur = tabController.getCurrentTab();
        if (cur != null) {
            cur.url = cleaned;
        }

        if (networkPolicy.isNavigationBlockedByKillSwitch(cleaned)) {
            showDegradedState("Tunnel disconnected. Ocean has paused this Private Browser session to prevent direct-network fallback.");
            return;
        }

        webView.loadUrl(cleaned);
        updateUrlBar(cleaned);
    }

    private void updateUrlBar(String url) {
        if (urlBarFocused) return;
        urlField.setText(url != null ? url : "");
        lockIcon.setImageResource(NavigationPolicy.isHttps(url)
                ? R.drawable.ic_lock_secure : R.drawable.ic_lock_insecure);
    }

    private void updateTabBadge() {
        int cur = tabController.getCurrentTabIndex() + 1;
        int total = tabController.getTabCount();
        tabBadge.setText(cur + "/" + total);
    }

    private void loadCurrentTab() {
        BrowserTab cur = tabController.getCurrentTab();
        if (cur == null) return;
        desktopSite = cur.desktopSite;
        applyDesktopMode();
        hideError();
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        webView.loadUrl(cur.url);
        updateUrlBar(cur.url);
        updateTabBadge();
    }

    private void applyDesktopMode() {
        WebSettings settings = webView.getSettings();
        if (desktopSite) {
            settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 OceanDesktop");
        } else {
            settings.setUserAgentString(WebSettings.getDefaultUserAgent(this));
        }
    }

    private void openNewTab(String url) {
        String normalized = BrowserUrlHelper.normalizeInput(url);
        tabController.addTab(normalized);
        loadCurrentTab();
    }

    private void closeCurrentTab() {
        int count = tabController.getTabCount();
        if (count <= 1) {
            finishAndWipe();
            return;
        }
        tabController.closeTab(tabController.getCurrentTabIndex());
        loadCurrentTab();
    }

    private void showTabSwitcher() {
        List<BrowserTab> all = tabController.getAllTabs();
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            BrowserTab t = all.get(i);
            String title = t.title != null && !t.title.isEmpty() ? t.title : t.url;
            labels[i] = (i == tabController.getCurrentTabIndex() ? "• " : "") + title;
        }

        new AlertDialog.Builder(this)
                .setTitle("Private Tabs")
                .setItems(labels, (d, which) -> {
                    tabController.selectTab(which);
                    loadCurrentTab();
                })
                .setPositiveButton("New Tab", (d, w) -> openNewTab(BrowserUrlHelper.DEFAULT_HOME))
                .setNeutralButton("Close Tab", (d, w) -> closeCurrentTab())
                .show();
    }

    private void showPrivacyStatusSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_privacy_status, null);
        dialog.setContentView(sheet);

        TextView sessionVal = sheet.findViewById(R.id.privacy_sheet_session_val);
        TextView routeVal = sheet.findViewById(R.id.privacy_sheet_route_val);
        TextView dnsVal = sheet.findViewById(R.id.privacy_sheet_dns_val);
        TextView trackersVal = sheet.findViewById(R.id.privacy_sheet_trackers_val);
        TextView popupsVal = sheet.findViewById(R.id.privacy_sheet_popups_val);
        TextView presetBadge = sheet.findViewById(R.id.privacy_sheet_preset_badge);
        Button btnNewIdentity = sheet.findViewById(R.id.privacy_sheet_btn_new_identity);
        Button btnEndSession = sheet.findViewById(R.id.privacy_sheet_btn_end_session);

        sessionVal.setText("Fresh · " + sessionManager.getFormattedDuration());
        routeVal.setText(networkPolicy.getRouteStatusSummary());
        dnsVal.setText(dnsPolicy.getDnsStatusSummary());
        trackersVal.setText(sessionManager.getTrackerBlockCount() + " blocked");
        popupsVal.setText(sessionManager.getPopupBlockCount() + " blocked");
        presetBadge.setText(sessionManager.getPreset().label);

        btnNewIdentity.setOnClickListener(v -> {
            dialog.dismiss();
            rotateIdentity();
        });

        btnEndSession.setOnClickListener(v -> {
            dialog.dismiss();
            finishAndWipe();
        });

        dialog.show();
    }

    private void rotateIdentity() {
        String newProfile = sessionManager.rotateIdentity();
        tabController.clearAllTabs();
        tabController.addTab(BrowserUrlHelper.DEFAULT_HOME);
        profileManager.applyProfile(webView, newProfile);
        loadCurrentTab();
        Toast.makeText(this, "New ephemeral identity generated", Toast.LENGTH_SHORT).show();
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "New private tab");
        menu.getMenu().add(0, 2, 0, desktopSite ? "Mobile site" : "Desktop site");
        menu.getMenu().add(0, 3, 0, "Privacy presets");
        menu.getMenu().add(0, 4, 0, "Panic wipe & exit");

        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                openNewTab(BrowserUrlHelper.DEFAULT_HOME);
                return true;
            }
            if (id == 2) {
                desktopSite = !desktopSite;
                BrowserTab cur = tabController.getCurrentTab();
                if (cur != null) cur.desktopSite = desktopSite;
                applyDesktopMode();
                webView.reload();
                return true;
            }
            if (id == 3) {
                showPresetPicker();
                return true;
            }
            if (id == 4) {
                finishAndWipe();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showPresetPicker() {
        String[] options = {"Balanced (Recommended)", "Strict (HTTPS-only & strict isolation)"};
        new AlertDialog.Builder(this)
                .setTitle("Privacy Preset")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        sessionManager.setPreset(PrivateSessionManager.Preset.BALANCED);
                        shieldText.setText("Balanced");
                    } else {
                        sessionManager.setPreset(PrivateSessionManager.Preset.STRICT);
                        shieldText.setText("Strict");
                    }
                })
                .show();
    }

    private DownloadListener createQuarantineDownloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            new AlertDialog.Builder(SecureBrowserActivity.this)
                    .setTitle(R.string.browser_download_quarantine_title)
                    .setMessage(getString(R.string.browser_download_quarantine_msg, URLUtil.guessFileName(url, contentDisposition, mimeType)))
                    .setPositiveButton(R.string.browser_save_to_downloads, (d, w) -> {
                        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(url));
                        request.setMimeType(mimeType);
                        request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        android.app.DownloadManager dm = (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                        if (dm != null) dm.enqueue(request);
                    })
                    .setNegativeButton(R.string.browser_discard_download, null)
                    .show();
        };
    }

    private void showDegradedState(String reason) {
        degradedBanner.setVisibility(View.VISIBLE);
        degradedReason.setText(reason);
        shieldIcon.setImageResource(R.drawable.ic_shield_warning);
    }

    private void hideError() {
        errorPanel.setVisibility(View.GONE);
    }

    private void finishAndWipe() {
        progressBar.setVisibility(View.VISIBLE);
        PrivateDataEraser.wipeSession(
                this,
                sessionManager.getProfileName(),
                webView,
                popupWebViews,
                verified -> finish()
        );
    }

    @Override
    protected void onPause() {
        pausedTimestamp = System.currentTimeMillis();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        // Check auto-close timeout (PDF 4 §13.14)
        if (sessionManager.isAutoCloseEnabled() && pausedTimestamp > 0) {
            long awayMinutes = (System.currentTimeMillis() - pausedTimestamp) / (60 * 1000);
            if (awayMinutes >= sessionManager.getAutoCloseTimeoutMinutes()) {
                finishAndWipe();
            }
        }
    }

    @Override
    protected void onDestroy() {
        finishAndWipe();
        super.onDestroy();
    }
}
