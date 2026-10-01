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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
                    new AlertDialog.Builder(SecureBrowserActivity.this)
                            .setTitle("Location request")
                            .setMessage(orig + " is requesting location access. Allow for this private session?")
                            .setPositiveButton("Allow for session", (d, w) -> cb.onGranted(true))
                            .setNegativeButton("Deny", (d, w) -> cb.onGranted(false))
                            .show();
                });
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
                new AlertDialog.Builder(SecureBrowserActivity.this)
                        .setTitle(R.string.browser_ssl_title)
                        .setMessage("Private mode blocked untrusted certificate (Error " + error.getPrimaryError() + "). Navigation cancelled for security.")
                        .setNegativeButton(android.R.string.cancel, (d, w) -> handler.cancel())
                        .setPositiveButton("Bypass (Advanced)", (d, w) -> handler.proceed())
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
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            BrowserTab t = all.get(i);
            String title = t.title != null && !t.title.isEmpty() ? t.title : t.url;
            labels[i] = (i == tabController.getCurrentTabIndex() ? "• " : "") + title;
        }

        new AlertDialog.Builder(this)
                .setTitle("Private Tabs")
                .setItems(labels, (d, which) -> switchToTab(which))
                .setPositiveButton("New Tab", (d, w) -> openNewTab(BrowserUrlHelper.DEFAULT_HOME))
                .setNeutralButton("Close Tab", (d, w) -> closeCurrentTab())
                .show();
    }

    private void showPrivacyStatusSheet() {
        View sheet = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_privacy_status, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(sheet)
                .create();

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
        menu.getMenu().add(0, 5, 0, "Panic wipe & exit");

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
                finishAndWipe();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showNetworkProxyDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        TextView statusView = new TextView(this);
        statusView.setText("Current Route: " + networkPolicy.getRouteStatusSummary());
        statusView.setTextColor(getColor(R.color.ocean_ink));
        statusView.setPadding(0, 0, 0, 16);
        layout.addView(statusView);

        EditText hostField = new EditText(this);
        hostField.setHint("Proxy Host (e.g. 127.0.0.1)");
        if (networkPolicy.getProxyHost() != null) hostField.setText(networkPolicy.getProxyHost());
        layout.addView(hostField);

        EditText portField = new EditText(this);
        portField.setHint("Proxy Port (e.g. 8080)");
        if (networkPolicy.getProxyPort() > 0) portField.setText(String.valueOf(networkPolicy.getProxyPort()));
        layout.addView(portField);

        new AlertDialog.Builder(this)
                .setTitle("Network Route & Proxy")
                .setView(layout)
                .setPositiveButton("Set Proxy", (d, w) -> {
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
                .setNeutralButton("Direct Mode", (d, w) -> {
                    networkPolicy.setDirect();
                    ProxyControllerAdapter.clearProxy(null);
                    Toast.makeText(this, "Direct routing restored", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
