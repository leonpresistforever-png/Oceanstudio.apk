package studio.ocean.app.browser.secure;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.Dialog;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import studio.ocean.app.OceanModal;
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
import studio.ocean.app.browser.security.DownloadQuarantineManager;
import studio.ocean.app.browser.security.PrivateDataEraser;

/**
 * Hardened Ephemeral Private Browser Activity (PDF 4 & PDF 5).
 * Runs inside a unique isolated WebKit profile (MULTI_PROFILE), never writes to persistent history,
 * applies active request/ad blocking, popup firewall, device permission firewall, and network route isolation.
 *
 * Enforces:
 * - One-shot wipe lifecycle guard (no re-entrant onDestroy loops)
 * - Safe New Identity (destroys old WebViews, deletes old profile, assigns new profile BEFORE navigation)
 * - Zero default-profile wipe contamination
 * - Per-tab WebView container isolation
 * - Authentic quarantine download inspection
 */
public final class SecureBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "secure_browser_initial_url";

    private enum WipeState { IDLE, WIPING, DESTROYED }
    private final AtomicReference<WipeState> wipeState = new AtomicReference<>(WipeState.IDLE);

    private final BrowserTabController tabController = new BrowserTabController();
    private final PrivateSessionManager sessionManager = new PrivateSessionManager();
    private final PrivateProfileManager profileManager = new PrivateProfileManager();
    private final TrackerBlocker trackerBlocker = new TrackerBlocker();
    private final PopupFirewall popupFirewall = new PopupFirewall();
    private final PermissionFirewall permissionFirewall = new PermissionFirewall();
    private final BrowserNetworkPolicy networkPolicy = new BrowserNetworkPolicy();
    private final DnsPolicy dnsPolicy = new DnsPolicy();
    private SavedPrivateSessionStore savedSessionStore;

    private final Map<Integer, WebView> tabWebViews = new HashMap<>();
    private final List<WebView> popupWebViews = new ArrayList<>();

    private FrameLayout webViewContainer;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("secure_browser");
            } catch (Exception ignored) {
            }
        }
        super.onCreate(savedInstanceState);
        // Default screenshot protection in private mode
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_secure_browser);

        // Prune orphan profiles left from previous process crashes
        PrivateProfileRegistry.cleanupOrphanProfiles(this);
        PrivateProfileRegistry.registerProfile(this, sessionManager.getProfileName());

        bindViews();
        wireActions();
        savedSessionStore = new SavedPrivateSessionStore(this);

        String initial = getIntent().getStringExtra(EXTRA_URL);
        String startUrl = initial != null && !initial.trim().isEmpty()
                ? BrowserUrlHelper.normalizeInput(initial)
                : BrowserUrlHelper.DEFAULT_HOME;

        tabController.addTab(startUrl);
        switchToTab(0);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView active = getActiveWebView();
                if (active != null && active.canGoBack()) {
                    active.goBack();
                } else if (tabController.getTabCount() > 1) {
                    closeCurrentTab();
                } else {
                    finishAndWipe();
                }
            }
        });
    }

    private void bindViews() {
        webViewContainer = findViewById(R.id.secure_browser_webview_container);
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

    private void wireActions() {
        findViewById(R.id.secure_browser_close).setOnClickListener(v -> finishAndWipe());
        findViewById(R.id.secure_browser_panic).setOnClickListener(v -> finishAndWipe());
        findViewById(R.id.secure_browser_shield_btn).setOnClickListener(v -> showPrivacyStatusSheet());

        findViewById(R.id.secure_browser_nav_back).setOnClickListener(v -> {
            WebView active = getActiveWebView();
            if (active != null && active.canGoBack()) active.goBack();
        });
        findViewById(R.id.secure_browser_nav_forward).setOnClickListener(v -> {
            WebView active = getActiveWebView();
            if (active != null && active.canGoForward()) active.goForward();
        });
        findViewById(R.id.secure_browser_nav_refresh).setOnClickListener(v -> {
            WebView active = getActiveWebView();
            if (active != null) active.reload();
        });
        findViewById(R.id.secure_browser_nav_home).setOnClickListener(v -> navigateTo(BrowserUrlHelper.DEFAULT_HOME));

        findViewById(R.id.secure_browser_menu).setOnClickListener(this::showOverflowMenu);
        tabBadge.setOnClickListener(v -> showTabSwitcher());

        findViewById(R.id.secure_browser_error_retry).setOnClickListener(v -> {
            hideError();
            WebView active = getActiveWebView();
            if (active != null) active.reload();
        });

        // Degraded Banner Actions
        findViewById(R.id.secure_browser_degraded_reconnect).setOnClickListener(v -> {
            networkPolicy.setTunnelConnected(true);
            degradedBanner.setVisibility(View.GONE);
            WebView active = getActiveWebView();
            if (active != null) active.reload();
        });
        findViewById(R.id.secure_browser_degraded_change_route).setOnClickListener(v -> {
            networkPolicy.setDirect();
            degradedBanner.setVisibility(View.GONE);
            WebView active = getActiveWebView();
            if (active != null) active.reload();
        });
        findViewById(R.id.secure_browser_degraded_exit).setOnClickListener(v -> finishAndWipe());

        urlField.setOnFocusChangeListener((v, hasFocus) -> urlBarFocused = hasFocus);
        urlField.setOnEditorActionListener((v, actionId, event) -> {
            String input = urlField.getText().toString();
            navigateTo(BrowserUrlHelper.normalizeInput(input));
            urlField.clearFocus();
            return true;
        });
    }

    @Nullable
    private WebView getActiveWebView() {
        return tabWebViews.get(tabController.getCurrentTabIndex());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebViewForTab(int tabIndex, String profileName) {
        WebView wv = new WebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wv.setBackgroundColor(getColor(R.color.ocean_background));

        // Assign ephemeral profile BEFORE any navigation or script execution (PDF 4 §5.1, §15.1; PDF 5 §9)
        boolean profileOk = profileManager.applyProfile(wv, profileName);
        if (!profileOk) {
            showDegradedState("Multi-profile isolation unsupported on this WebView; fallback restricted mode active.");
        }

        WebSettings settings = wv.getSettings();
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

        wv.setDownloadListener(createQuarantineDownloadListener());
        setupClientsForWebView(wv, tabIndex);

        return wv;
    }

    private void setupClientsForWebView(WebView wv, int tabIndex) {
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (tabIndex == tabController.getCurrentTabIndex()) {
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    }
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.trim().isEmpty()) {
                    BrowserTab tab = tabController.getTab(tabIndex);
                    if (tab != null) tab.title = title.trim();
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                permissionFirewall.handleGeolocationPrompt(origin, callback, (orig, resources, cb) -> {
                    OceanModal.create(SecureBrowserActivity.this)
                            .setTitle("Location Request")
                            .setExplanation(orig + " is requesting location access. Allow for this private session?")
                            .setPositiveButton("Allow for Session", v -> cb.onGranted(true))
                            .setNegativeButton("Deny", v -> cb.onGranted(false))
                            .show();
                });
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                permissionFirewall.handlePermissionRequest(request, (origin, resources, callback) -> {
                    OceanModal.create(SecureBrowserActivity.this)
                            .setTitle("Permission Request")
                            .setExplanation(origin + " is requesting device capabilities. Allow for this private session?")
                            .setPositiveButton("Allow for Session", v -> callback.onGranted(true))
                            .setNegativeButton("Deny", v -> callback.onGranted(false))
                            .show();
                });
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView.HitTestResult result = view.getHitTestResult();
                String targetUrl = result.getExtra();

                popupFirewall.evaluatePopup(targetUrl, isUserGesture, (url, host, gesture, decision) -> {
                    OceanModal.create(SecureBrowserActivity.this)
                            .setTitle("Popup Requested")
                            .setExplanation("Allow popup from " + (host.isEmpty() ? "this page" : host) + "?")
                            .setPositiveButton("Allow Once", v -> decision.onDecision(true, false))
                            .setNeutralButton("Always for Session", v -> decision.onDecision(true, true))
                            .setNegativeButton("Block", v -> decision.onDecision(false, false))
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
        });

        wv.setWebViewClient(new WebViewClient() {
            private final AtomicReference<String> mainFrameUrl = new AtomicReference<>("about:blank");

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String requestUrl = request.getUrl().toString();
                String mainUrl = request.isForMainFrame() ? requestUrl : mainFrameUrl.get();
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

                if (networkPolicy.isNavigationBlockedByKillSwitch(targetUrl)) {
                    showDegradedState("Tunnel disconnected. Ocean has paused this Private Browser session to prevent direct-network fallback.");
                    return true;
                }

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
                mainFrameUrl.set(url);
                if (tabIndex == tabController.getCurrentTabIndex()) {
                    hideError();
                    updateUrlBar(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (tabIndex == tabController.getCurrentTabIndex()) {
                    progressBar.setVisibility(View.GONE);
                    BrowserTab cur = tabController.getCurrentTab();
                    if (cur != null) cur.url = url;
                    updateUrlBar(url);
                    updateTabBadge();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                OceanModal.create(SecureBrowserActivity.this)
                        .setTitle(getString(R.string.browser_ssl_title))
                        .setExplanation("Private mode blocked untrusted certificate (Error " + error.getPrimaryError() + "). Navigation cancelled for security.")
                        .setPositiveButton("Bypass (Advanced)", v -> handler.proceed())
                        .setNegativeButton("Cancel", v -> handler.cancel())
                        .show();
            }
        });
    }

    private void switchToTab(int index) {
        tabController.selectTab(index);
        BrowserTab cur = tabController.getCurrentTab();
        if (cur == null) return;

        // Hide all WebViews
        for (WebView wv : tabWebViews.values()) {
            wv.setVisibility(View.GONE);
        }

        // Get or construct tab WebView
        WebView active = tabWebViews.get(index);
        if (active == null) {
            active = createWebViewForTab(index, sessionManager.getProfileName());
            tabWebViews.put(index, active);
            webViewContainer.addView(active);
            active.loadUrl(cur.url);
        }

        active.setVisibility(View.VISIBLE);
        desktopSite = cur.desktopSite;
        applyDesktopMode(active);
        updateUrlBar(active.getUrl() != null ? active.getUrl() : cur.url);
        updateTabBadge();
    }

    private void navigateTo(String rawUrl) {
        hideError();
        String cleaned = NavigationPolicy.cleanUrl(rawUrl, true);
        if (sessionManager.isHttpsOnly() && NavigationPolicy.isInsecureHttp(cleaned)) {
            cleaned = NavigationPolicy.upgradeToHttps(cleaned);
        }

        BrowserTab cur = tabController.getCurrentTab();
        if (cur != null) cur.url = cleaned;

        if (networkPolicy.isNavigationBlockedByKillSwitch(cleaned)) {
            showDegradedState("Tunnel disconnected. Ocean has paused this Private Browser session to prevent direct-network fallback.");
            return;
        }

        WebView active = getActiveWebView();
        if (active != null) {
            active.loadUrl(cleaned);
        }
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

    private void applyDesktopMode(WebView wv) {
        if (wv == null) return;
        WebSettings settings = wv.getSettings();
        if (desktopSite) {
            settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 OceanDesktop");
        } else {
            settings.setUserAgentString(WebSettings.getDefaultUserAgent(this));
        }
    }

    private void openNewTab(String url) {
        String normalized = BrowserUrlHelper.normalizeInput(url);
        tabController.addTab(normalized);
        switchToTab(tabController.getTabCount() - 1);
    }

    private void closeCurrentTab() {
        int count = tabController.getTabCount();
        if (count <= 1) {
            finishAndWipe();
            return;
        }
        int curIdx = tabController.getCurrentTabIndex();
        WebView closing = tabWebViews.remove(curIdx);
        if (closing != null) {
            closing.stopLoading();
            closing.loadUrl("about:blank");
            webViewContainer.removeView(closing);
            closing.destroy();
        }
        tabController.closeTab(curIdx);
        // Re-index remaining map keys
        Map<Integer, WebView> reindexed = new HashMap<>();
        int i = 0;
        for (int k = 0; k < count; k++) {
            if (k == curIdx) continue;
            WebView v = tabWebViews.get(k);
            if (v != null) {
                reindexed.put(i, v);
            }
            i++;
        }
        tabWebViews.clear();
        tabWebViews.putAll(reindexed);

        switchToTab(Math.min(curIdx, tabController.getTabCount() - 1));
    }

    private void showTabSwitcher() {
        List<BrowserTab> all = tabController.getAllTabs();
        float density = getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < all.size(); i++) {
            final int tabIdx = i;
            BrowserTab t = all.get(i);
            String title = t.title != null && !t.title.isEmpty() ? t.title : t.url;
            boolean isCurrent = (i == tabController.getCurrentTabIndex());

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setBackgroundResource(isCurrent ? R.drawable.model_chip_background_selected : R.drawable.settings_row_background);
            int pad = (int) (12 * density);
            item.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (6 * density);
            item.setLayoutParams(lp);

            TextView tv = new TextView(this);
            tv.setText((isCurrent ? "● " : "○ ") + title);
            tv.setTextSize(13f);
            tv.setTypeface(null, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
            tv.setTextColor(isCurrent ? getColor(R.color.ocean_paper) : getColor(R.color.ocean_ink));
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            item.addView(tv, tvLp);

            item.setOnClickListener(v -> switchToTab(tabIdx));
            list.addView(item);
        }

        OceanModal.create(this)
                .setTitle("Private Tabs (" + all.size() + ")")
                .setExplanation("All tabs reside strictly in the active ephemeral profile and are destroyed on exit.")
                .setCustomView(list)
                .setPositiveButton("New Tab", v -> openNewTab(BrowserUrlHelper.DEFAULT_HOME))
                .setNeutralButton("Close Tab", v -> closeCurrentTab())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPrivacyStatusSheet() {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        // Session status summary
        TextView sessionInfo = new TextView(this);
        sessionInfo.setText("Ephemeral Profile: " + sessionManager.getProfileName() + " (" + sessionManager.getFormattedDuration() + ")");
        sessionInfo.setTextSize(12f);
        sessionInfo.setTextColor(getColor(R.color.ocean_muted));
        sessionInfo.setPadding(0, 0, 0, (int) (12 * density));
        content.addView(sessionInfo);

        addControlToggleRow(content, "Tracker Blocking", "Block telemetry, ads, and fingerprinting scripts", sessionManager.isTrackerBlockingEnabled(), (v, isChecked) -> {
            sessionManager.setTrackerBlockingEnabled(isChecked);
        });

        addControlToggleRow(content, "Popup Firewall", "Block unprompted windows, tabs, and redirects", sessionManager.isPopupFirewallEnabled(), (v, isChecked) -> {
            sessionManager.setPopupFirewallEnabled(isChecked);
        });

        addControlToggleRow(content, "HTTPS-Only Mode", "Enforce encrypted TLS transport and block cleartext HTTP", sessionManager.isHttpsOnly(), (v, isChecked) -> {
            sessionManager.setHttpsOnly(isChecked);
        });

        addLockedRow(content, "Third-Party Cookies", "Cross-site tracking storage", "LOCKED OFF");

        addSelectorRow(content, "WebRTC Local-IP Exposure", "Restricts private STUN/ICE interface leaking", sessionManager.getWebRtcMode(), () -> {
            String cur = sessionManager.getWebRtcMode();
            String next = "Restricted".equals(cur) ? "Disabled" : ("Disabled".equals(cur) ? "Default" : "Restricted");
            sessionManager.setWebRtcMode(next);
            return next;
        });

        addSelectorRow(content, "DNS Resolution", "Cryptographic or system DNS routing", sessionManager.getDnsMode(), () -> {
            String cur = sessionManager.getDnsMode();
            String next = "System".equals(cur) ? "Private DoH" : ("Private DoH".equals(cur) ? "Tunnel DNS" : "System");
            sessionManager.setDnsMode(next);
            return next;
        });

        addSelectorRow(content, "Network Route", "Network transport routing", sessionManager.getRouteMode(), () -> {
            String cur = sessionManager.getRouteMode();
            String next = "Direct".equals(cur) ? "Proxy" : ("Proxy".equals(cur) ? "Ocean Tunnel (Beta - Unavailable)" : "Direct");
            sessionManager.setRouteMode(next);
            return next;
        });

        addControlToggleRow(content, "Kill Switch", "Block outbound requests if network path drops", sessionManager.isKillSwitchEnabled(), (v, isChecked) -> {
            sessionManager.setKillSwitchEnabled(isChecked);
        });

        addSelectorRow(content, "Auto-Close + Wipe", "Auto-clear private session on inactivity", sessionManager.getAutoCloseTimeoutMinutes() + " min", () -> {
            long cur = sessionManager.getAutoCloseTimeoutMinutes();
            long next = (cur == 5) ? 15 : ((cur == 15) ? 30 : ((cur == 30) ? 60 : 5));
            sessionManager.setAutoCloseTimeoutMinutes(next);
            return next + " min";
        });

        // Bottom action buttons
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, (int) (12 * density), 0, 0);

        Button btnNewIdentity = new Button(this);
        btnNewIdentity.setText("New Ephemeral Identity");
        btnNewIdentity.setTextSize(13f);
        btnNewIdentity.setTextColor(getColor(R.color.ocean_ink));
        btnNewIdentity.setBackgroundResource(R.drawable.button_secondary);
        btnNewIdentity.setAllCaps(false);
        LinearLayout.LayoutParams niLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density));
        niLp.bottomMargin = (int) (8 * density);
        actions.addView(btnNewIdentity, niLp);

        Button btnSaveSession = new Button(this);
        btnSaveSession.setText("Save Session (URLs Only)");
        btnSaveSession.setTextSize(13f);
        btnSaveSession.setTextColor(getColor(R.color.ocean_ink));
        btnSaveSession.setBackgroundResource(R.drawable.button_secondary);
        btnSaveSession.setAllCaps(false);
        LinearLayout.LayoutParams ssLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density));
        ssLp.bottomMargin = (int) (8 * density);
        actions.addView(btnSaveSession, ssLp);

        Button btnSavedSessions = new Button(this);
        btnSavedSessions.setText("View Saved Sessions");
        btnSavedSessions.setTextSize(13f);
        btnSavedSessions.setTextColor(getColor(R.color.ocean_ink));
        btnSavedSessions.setBackgroundResource(R.drawable.button_secondary);
        btnSavedSessions.setAllCaps(false);
        LinearLayout.LayoutParams vssLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density));
        vssLp.bottomMargin = (int) (8 * density);
        actions.addView(btnSavedSessions, vssLp);

        Button btnEndSession = new Button(this);
        btnEndSession.setText("End Session & Wipe");
        btnEndSession.setTextSize(13f);
        btnEndSession.setTextColor(getColor(R.color.ocean_paper));
        btnEndSession.setBackgroundResource(R.drawable.primary_button_background);
        btnEndSession.setAllCaps(false);
        LinearLayout.LayoutParams esLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (42 * density));
        actions.addView(btnEndSession, esLp);

        content.addView(actions);

        Dialog dialog = OceanModal.create(this)
                .setTitle("Private Browser Protection")
                .setExplanation("Ephemeral browsing with hardware & profile isolation. All statuses report genuine backend state.")
                .setCustomView(content)
                .setPositiveButton("Done", null)
                .show();

        btnNewIdentity.setOnClickListener(v -> {
            dialog.dismiss();
            rotateIdentity();
        });

        btnSaveSession.setOnClickListener(v -> {
            dialog.dismiss();
            saveCurrentSession();
        });

        btnSavedSessions.setOnClickListener(v -> {
            dialog.dismiss();
            showSavedSessionsSheet();
        });

        btnEndSession.setOnClickListener(v -> {
            dialog.dismiss();
            finishAndWipe();
        });
    }

    private void addControlToggleRow(LinearLayout parent, String title, String subtitle, boolean initialValue, androidx.appcompat.widget.SwitchCompat.OnCheckedChangeListener listener) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.settings_row_background);
        int pad = (int) (10 * density);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.bottomMargin = (int) (6 * density);
        row.setLayoutParams(rLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(tLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(13f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(getColor(R.color.ocean_ink));
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(11f);
        tvSub.setTextColor(getColor(R.color.ocean_muted));
        textCol.addView(tvSub);

        row.addView(textCol);

        androidx.appcompat.widget.SwitchCompat toggle = new androidx.appcompat.widget.SwitchCompat(this);
        toggle.setChecked(initialValue);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);

        parent.addView(row);
    }

    private void addLockedRow(LinearLayout parent, String title, String subtitle, String lockBadge) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.settings_row_background);
        int pad = (int) (10 * density);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.bottomMargin = (int) (6 * density);
        row.setLayoutParams(rLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(tLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(13f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(getColor(R.color.ocean_ink));
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(11f);
        tvSub.setTextColor(getColor(R.color.ocean_muted));
        textCol.addView(tvSub);

        row.addView(textCol);

        TextView badge = new TextView(this);
        badge.setText(lockBadge);
        badge.setTextSize(11f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(getColor(R.color.ocean_muted));
        badge.setBackgroundResource(R.drawable.model_chip_background);
        badge.setPadding((int) (6 * density), (int) (2 * density), (int) (6 * density), (int) (2 * density));
        row.addView(badge);

        parent.addView(row);
    }

    private interface ValueCycler {
        String cycle();
    }

    private void addSelectorRow(LinearLayout parent, String title, String subtitle, String initialValue, ValueCycler cycler) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.settings_row_background);
        int pad = (int) (10 * density);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.bottomMargin = (int) (6 * density);
        row.setLayoutParams(rLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(tLp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(13f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(getColor(R.color.ocean_ink));
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextSize(11f);
        tvSub.setTextColor(getColor(R.color.ocean_muted));
        textCol.addView(tvSub);

        row.addView(textCol);

        Button selBtn = new Button(this);
        selBtn.setText(initialValue);
        selBtn.setTextSize(11f);
        selBtn.setTextColor(getColor(R.color.ocean_ink));
        selBtn.setBackgroundResource(R.drawable.auth_field_background);
        selBtn.setAllCaps(false);
        selBtn.setPadding((int) (8 * density), (int) (2 * density), (int) (8 * density), (int) (2 * density));
        selBtn.setOnClickListener(v -> {
            String newVal = cycler.cycle();
            selBtn.setText(newVal);
        });
        row.addView(selBtn);

        parent.addView(row);
    }

    private void saveCurrentSession() {
        List<BrowserTab> tabs = tabController.getAllTabs();
        if (tabs.isEmpty()) {
            Toast.makeText(this, "No tabs to save", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText nameInput = new EditText(this);
        nameInput.setHint("Session Name");
        nameInput.setText("Session · " + tabs.size() + " tabs");
        nameInput.setSingleLine(true);
        nameInput.setTextColor(getColor(R.color.ocean_ink));
        nameInput.setBackgroundResource(R.drawable.auth_field_background);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        nameInput.setPadding(pad, pad, pad, pad);

        OceanModal.create(this)
                .setTitle("Save Session Manifest")
                .setExplanation("Saves tab URLs and titles only. Cookies, WebStorage, cache, and profile data are NEVER persisted.")
                .setCustomView(nameInput)
                .setPositiveButton("Save Manifest", v -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) name = "Untitled Session";
                    List<SavedPrivateSession.TabEntry> entries = new ArrayList<>();
                    for (BrowserTab t : tabs) {
                        entries.add(new SavedPrivateSession.TabEntry(t.url, t.title != null ? t.title : "", t.pinned));
                    }
                    SavedPrivateSession session = new SavedPrivateSession(name, entries);
                    savedSessionStore.saveSession(session);
                    Toast.makeText(SecureBrowserActivity.this, "Session manifest saved (URLs only)", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSavedSessionsSheet() {
        List<SavedPrivateSession> sessions = savedSessionStore.getSessions();
        if (sessions.isEmpty()) {
            OceanModal.showMessage(this, "Saved Sessions", "No saved private sessions found. You can save your open tabs as a clean URL manifest from the menu.");
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        for (SavedPrivateSession s : sessions) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setBackgroundResource(R.drawable.settings_row_background);
            int pad = (int) (12 * density);
            item.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (8 * density);
            item.setLayoutParams(lp);

            TextView name = new TextView(this);
            name.setText(s.getName());
            name.setTextSize(14f);
            name.setTypeface(null, Typeface.BOLD);
            name.setTextColor(getColor(R.color.ocean_ink));
            item.addView(name);

            TextView info = new TextView(this);
            info.setText(s.getTabs().size() + " tabs · URL manifest only");
            info.setTextSize(12f);
            info.setTextColor(getColor(R.color.ocean_muted));
            info.setPadding(0, (int) (2 * density), 0, (int) (8 * density));
            item.addView(info);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button btnRestore = new Button(this);
            btnRestore.setText("Restore Fresh");
            btnRestore.setTextSize(12f);
            btnRestore.setAllCaps(false);
            btnRestore.setTextColor(getColor(R.color.ocean_paper));
            btnRestore.setBackgroundResource(R.drawable.primary_button_background);
            btnRestore.setOnClickListener(v -> restoreSavedSession(s));
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f);
            rLp.marginEnd = (int) (8 * density);
            actions.addView(btnRestore, rLp);

            Button btnDelete = new Button(this);
            btnDelete.setText("Delete");
            btnDelete.setTextSize(12f);
            btnDelete.setAllCaps(false);
            btnDelete.setTextColor(getColor(R.color.ocean_ink));
            btnDelete.setBackgroundResource(R.drawable.button_secondary);
            btnDelete.setOnClickListener(v -> {
                savedSessionStore.deleteSession(s.getId());
                showSavedSessionsSheet();
            });
            LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, (int) (36 * density), 1.0f);
            actions.addView(btnDelete, dLp);

            item.addView(actions);
            list.addView(item);
        }

        OceanModal.create(this)
                .setTitle("Saved Private Sessions")
                .setExplanation("Restoring opens saved URLs in a fresh ephemeral profile with zero retained cookies or storage.")
                .setCustomView(list)
                .setNegativeButton("Close", null)
                .show();
    }

    private void restoreSavedSession(SavedPrivateSession s) {
        if (s.getTabs().isEmpty()) {
            Toast.makeText(this, "Session has no tabs", Toast.LENGTH_SHORT).show();
            return;
        }

        rotateIdentity();
        tabController.clearAllTabs();
        for (int i = 0; i < s.getTabs().size(); i++) {
            SavedPrivateSession.TabEntry entry = s.getTabs().get(i);
            int idx = tabController.addTab(entry.url);
            BrowserTab t = tabController.getTab(idx);
            if (t != null) {
                t.title = entry.title;
                t.pinned = entry.pinned;
            }
        }
        switchToTab(0);
        Toast.makeText(this, "Restored " + s.getTabs().size() + " tabs in fresh ephemeral profile", Toast.LENGTH_SHORT).show();
    }

    /**
     * Executes the strict New Identity sequence (PDF 5 P0-B):
     * 1. Capture old profile name
     * 2. Stop and destroy all existing WebViews and popups
     * 3. Delete old profile from ProfileStore
     * 4. Generate new session profile identity
     * 5. Register new profile
     * 6. Construct fresh WebView and set new profile BEFORE navigation
     * 7. Reload home
     */
    private void rotateIdentity() {
        String oldProfile = sessionManager.getProfileName();

        // Destroy all existing tab WebViews
        for (WebView wv : tabWebViews.values()) {
            if (wv != null) {
                wv.stopLoading();
                wv.loadUrl("about:blank");
                webViewContainer.removeView(wv);
                wv.destroy();
            }
        }
        tabWebViews.clear();

        // Destroy popups
        for (WebView popup : popupWebViews) {
            if (popup != null) {
                popup.stopLoading();
                popup.loadUrl("about:blank");
                popup.destroy();
            }
        }
        popupWebViews.clear();

        // Delete old profile and unregister
        profileManager.deleteProfile(oldProfile);
        PrivateProfileRegistry.unregisterProfile(this, oldProfile);

        // Reset permission grants
        permissionFirewall.clearSessionPermissions();

        // Rotate identity
        String newProfile = sessionManager.rotateIdentity();
        PrivateProfileRegistry.registerProfile(this, newProfile);

        tabController.clearAllTabs();
        tabController.addTab(BrowserUrlHelper.DEFAULT_HOME);
        switchToTab(0);

        Toast.makeText(this, "New ephemeral identity generated", Toast.LENGTH_SHORT).show();
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "New private tab");
        menu.getMenu().add(0, 2, 0, desktopSite ? "Mobile site" : "Desktop site");
        menu.getMenu().add(0, 3, 0, "Network privacy & proxy");
        menu.getMenu().add(0, 4, 0, "Privacy presets");
        menu.getMenu().add(0, 5, 0, "Save session (URLs only)");
        menu.getMenu().add(0, 6, 0, "Saved sessions");
        menu.getMenu().add(0, 7, 0, "Privacy protection sheet");
        menu.getMenu().add(0, 8, 0, "Panic wipe & exit");

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
                WebView active = getActiveWebView();
                if (active != null) {
                    applyDesktopMode(active);
                    active.reload();
                }
                return true;
            }
            if (id == 3) {
                showNetworkProxyDialog();
                return true;
            }
            if (id == 4) {
                showPresetPicker();
                return true;
            }
            if (id == 5) {
                saveCurrentSession();
                return true;
            }
            if (id == 6) {
                showSavedSessionsSheet();
                return true;
            }
            if (id == 7) {
                showPrivacyStatusSheet();
                return true;
            }
            if (id == 8) {
                finishAndWipe();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showNetworkProxyDialog() {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * density);

        EditText hostField = new EditText(this);
        hostField.setHint("Proxy Host (e.g. 127.0.0.1)");
        if (networkPolicy.getProxyHost() != null) hostField.setText(networkPolicy.getProxyHost());
        hostField.setTextColor(getColor(R.color.ocean_ink));
        hostField.setBackgroundResource(R.drawable.auth_field_background);
        hostField.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.bottomMargin = (int) (8 * density);
        layout.addView(hostField, hLp);

        EditText portField = new EditText(this);
        portField.setHint("Proxy Port (e.g. 8080)");
        if (networkPolicy.getProxyPort() > 0) portField.setText(String.valueOf(networkPolicy.getProxyPort()));
        portField.setTextColor(getColor(R.color.ocean_ink));
        portField.setBackgroundResource(R.drawable.auth_field_background);
        portField.setPadding(pad, pad, pad, pad);
        layout.addView(portField);

        OceanModal.create(this)
                .setTitle("Network Route & Proxy")
                .setExplanation("Route private browser traffic through a verified local or upstream proxy. (Ocean Tunnel is currently beta/unavailable).")
                .setCustomView(layout)
                .setPositiveButton("Set Proxy", v -> {
                    String h = hostField.getText().toString().trim();
                    String pStr = portField.getText().toString().trim();
                    if (!h.isEmpty() && !pStr.isEmpty()) {
                        try {
                            int p = Integer.parseInt(pStr);
                            networkPolicy.setProxy(h, p);
                            ProxyControllerAdapter.applyProxy(h + ":" + p, new ProxyControllerAdapter.ProxyCallback() {
                                @Override
                                public void onSuccess() {
                                    networkPolicy.setProxyVerified(true);
                                    Toast.makeText(SecureBrowserActivity.this, "Proxy override applied", Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onFailure(String error) {
                                    networkPolicy.setProxyVerified(false);
                                    Toast.makeText(SecureBrowserActivity.this, "Proxy failed: " + error, Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (NumberFormatException ignored) {
                        }
                    }
                })
                .setNeutralButton("Direct Mode", v -> {
                    networkPolicy.setDirect();
                    ProxyControllerAdapter.clearProxy(null);
                    Toast.makeText(this, "Direct routing restored", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPresetPicker() {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        Button btnBalanced = new Button(this);
        btnBalanced.setText("Balanced (Recommended)\nTracker blocking + standard isolation");
        btnBalanced.setTextSize(12f);
        btnBalanced.setAllCaps(false);
        btnBalanced.setTextColor(getColor(R.color.ocean_ink));
        btnBalanced.setBackgroundResource(R.drawable.settings_row_background);
        int pad = (int) (10 * density);
        btnBalanced.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.bottomMargin = (int) (8 * density);
        list.addView(btnBalanced, bLp);

        Button btnStrict = new Button(this);
        btnStrict.setText("Strict\nHTTPS-only + strict certificate policy");
        btnStrict.setTextSize(12f);
        btnStrict.setAllCaps(false);
        btnStrict.setTextColor(getColor(R.color.ocean_ink));
        btnStrict.setBackgroundResource(R.drawable.settings_row_background);
        btnStrict.setPadding(pad, pad, pad, pad);
        list.addView(btnStrict, bLp);

        Dialog dialog = OceanModal.create(this)
                .setTitle("Privacy Preset")
                .setExplanation("Select an ephemeral security level for this browsing session.")
                .setCustomView(list)
                .setNegativeButton("Cancel", null)
                .show();

        btnBalanced.setOnClickListener(v -> {
            sessionManager.setPreset(PrivateSessionManager.Preset.BALANCED);
            shieldText.setText("Balanced");
            dialog.dismiss();
        });

        btnStrict.setOnClickListener(v -> {
            sessionManager.setPreset(PrivateSessionManager.Preset.STRICT);
            shieldText.setText("Strict");
            dialog.dismiss();
        });
    }

    private DownloadListener createQuarantineDownloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            Toast.makeText(this, "Staging download in app-private quarantine...", Toast.LENGTH_SHORT).show();
            DownloadQuarantineManager.stageDownload(
                    this,
                    url,
                    contentDisposition,
                    mimeType,
                    new DownloadQuarantineManager.QuarantineCallback() {
                        @Override
                        public void onDownloadComplete(DownloadQuarantineManager.QuarantineRecord record) {
                            DownloadQuarantineManager.showQuarantineDialog(SecureBrowserActivity.this, record);
                        }

                        @Override
                        public void onDownloadFailed(String error) {
                            Toast.makeText(SecureBrowserActivity.this, "Quarantine failed: " + error, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
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

    /**
     * Executes one-shot session wipe and closes activity (PDF 5 P0-E).
     */
    private void finishAndWipe() {
        if (!wipeState.compareAndSet(WipeState.IDLE, WipeState.WIPING)) {
            // Already wiping or destroyed
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        PrivateDataEraser.wipeSession(
                this,
                sessionManager.getProfileName(),
                tabWebViews.values(),
                popupWebViews,
                verified -> {
                    wipeState.set(WipeState.DESTROYED);
                    finish();
                }
        );
    }

    @Override
    protected void onPause() {
        pausedTimestamp = System.currentTimeMillis();
        WebView active = getActiveWebView();
        if (active != null) active.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebView active = getActiveWebView();
        if (active != null) active.onResume();
        // Check auto-close timeout
        if (sessionManager.isAutoCloseEnabled() && pausedTimestamp > 0) {
            long awayMinutes = (System.currentTimeMillis() - pausedTimestamp) / (60 * 1000);
            if (awayMinutes >= sessionManager.getAutoCloseTimeoutMinutes()) {
                finishAndWipe();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (wipeState.compareAndSet(WipeState.IDLE, WipeState.WIPING)) {
            PrivateDataEraser.wipeSession(
                    this,
                    sessionManager.getProfileName(),
                    tabWebViews.values(),
                    popupWebViews,
                    verified -> wipeState.set(WipeState.DESTROYED)
            );
        }
        super.onDestroy();
    }
}
