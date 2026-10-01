package studio.ocean.app.browser;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import studio.ocean.app.R;
import studio.ocean.app.browser.normal.BrowserHistoryStore;
import studio.ocean.app.browser.secure.SecureBrowserActivity;
import studio.ocean.app.browser.storage.SessionVault;

/** Full-featured in-app browser with tabs, cookies, and JavaScript (System WebView + AndroidX WebKit). */
public final class BrowserActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "browser_initial_url";

    private final List<BrowserTab> tabs = new ArrayList<>();
    private final LinkedList<BrowserTab> recentlyClosedTabs = new LinkedList<>();
    private BrowserHistoryStore historyStore;
    private SessionVault sessionVault;
    private int currentTabIndex;
    private boolean urlBarFocused;
    private boolean desktopSite;

    private WebView webView;
    private EditText urlField;
    private ImageView secureIcon;
    private TextView tabBadge;
    private ProgressBar progressBar;
    private LinearLayout errorPanel;
    private TextView errorTitle;
    private TextView errorMessage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        historyStore = new BrowserHistoryStore(this);
        sessionVault = new SessionVault(this);
        bindViews();
        configureWebView();
        wireChrome();
        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            String initial = getIntent().getStringExtra(EXTRA_URL);
            tabs.add(new BrowserTab(BrowserUrlHelper.normalizeInput(initial)));
            currentTabIndex = 0;
            loadCurrentTab(false);
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else finish();
            }
        });
    }

    private void bindViews() {
        webView = findViewById(R.id.browser_webview);
        urlField = findViewById(R.id.browser_url);
        secureIcon = findViewById(R.id.browser_secure_icon);
        tabBadge = findViewById(R.id.browser_tab_badge);
        progressBar = findViewById(R.id.browser_progress);
        errorPanel = findViewById(R.id.browser_error_panel);
        errorTitle = findViewById(R.id.browser_error_title);
        errorMessage = findViewById(R.id.browser_error_message);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
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
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);
        webView.setDownloadListener(downloadListener());
    }

    private void wireChrome() {
        findViewById(R.id.browser_close).setOnClickListener(v -> finish());
        findViewById(R.id.browser_nav_back).setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        findViewById(R.id.browser_nav_forward).setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        findViewById(R.id.browser_nav_refresh).setOnClickListener(v -> webView.reload());
        findViewById(R.id.browser_nav_home).setOnClickListener(v -> navigateTo(BrowserUrlHelper.DEFAULT_HOME));
        findViewById(R.id.browser_menu).setOnClickListener(this::showBrowserMenu);
        tabBadge.setOnClickListener(v -> showTabSwitcher());
        findViewById(R.id.browser_error_retry).setOnClickListener(v -> {
            hideError();
            webView.reload();
        });
        urlField.setOnFocusChangeListener((v, hasFocus) -> urlBarFocused = hasFocus);
        urlField.setOnEditorActionListener((v, actionId, event) -> {
            navigateTo(BrowserUrlHelper.normalizeInput(urlField.getText().toString()));
            urlField.clearFocus();
            return true;
        });
        webView.setWebChromeClient(new OceanBrowserChromeClient(new OceanBrowserChromeClient.Callback() {
            @Override
            public void onProgressChanged(int progress) {
                if (progress >= 100) {
                    progressBar.setVisibility(View.GONE);
                } else {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress);
                }
            }

            @Override
            public void onReceivedTitle(String title) {
                if (title == null || title.trim().isEmpty()) return;
                currentTab().title = title.trim();
            }

            @Override
            public void onCreateWindow(WebView view, String url) {
                addTab(url);
            }
        }));
        webView.setWebViewClient(new OceanBrowserWebViewClient(new OceanBrowserWebViewClient.Callback() {
            @Override
            public void onPageStarted(String url) {
                hideError();
                updateUrlBar(url);
            }

            @Override
            public void onPageFinished(String url, boolean failed) {
                progressBar.setVisibility(View.GONE);
                if (!failed) {
                    currentTab().url = url;
                    updateUrlBar(url);
                    saveWebState();
                    if (historyStore != null) {
                        historyStore.recordVisit(url, currentTab().title);
                    }
                }
                updateTabBadge();
            }

            @Override
            public void onReceivedError(String url, CharSequence description) {
                showError(getString(R.string.browser_error_title),
                        getString(R.string.browser_error_message,
                                getString(R.string.browser_load_failed),
                                description == null ? url : description));
            }

            @Override
            public boolean onExternalNavigation(Uri uri) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    return true;
                } catch (Exception error) {
                    Toast.makeText(BrowserActivity.this, R.string.browser_no_handler, Toast.LENGTH_SHORT).show();
                    return true;
                }
            }

            @Override
            public void onSslError(SslErrorHandler handler, android.net.http.SslError error) {
                new AlertDialog.Builder(BrowserActivity.this)
                        .setTitle(R.string.browser_ssl_title)
                        .setMessage(getString(R.string.browser_ssl_message, error.getPrimaryError()))
                        .setNegativeButton(android.R.string.cancel, (d, w) -> handler.cancel())
                        .setPositiveButton(R.string.browser_ssl_continue, (d, w) -> handler.proceed())
                        .show();
            }
        }));
    }

    private DownloadListener downloadListener() {
        return (url, userAgent, contentDisposition, mimeType, contentLength) -> {
            try {
                String file = URLUtil.guessFileName(url, contentDisposition, mimeType);
                android.app.DownloadManager.Request request =
                        new android.app.DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.setDescription(getString(R.string.browser_download_description));
                request.setTitle(file);
                request.setNotificationVisibility(
                        android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                android.app.DownloadManager manager =
                        (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                if (manager != null) {
                    manager.enqueue(request);
                    Toast.makeText(this, R.string.browser_download_started, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception error) {
                Toast.makeText(this, R.string.browser_download_failed, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void showBrowserMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.inflate(R.menu.menu_browser);
        menu.getMenu().findItem(R.id.browser_menu_desktop).setChecked(desktopSite);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.browser_menu_desktop) {
                desktopSite = !desktopSite;
                currentTab().desktopSite = desktopSite;
                applyDesktopMode();
                webView.reload();
                return true;
            }
            if (id == R.id.browser_menu_share) {
                String url = currentTab().url;
                if (url == null || url.isEmpty()) return true;
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, url), getString(R.string.browser_share)));
                return true;
            }
            if (id == R.id.browser_menu_external) {
                String url = currentTab().url;
                if (url != null && !url.isEmpty()) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception error) {
                        Toast.makeText(this, R.string.browser_no_handler, Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
            if (id == R.id.browser_menu_new_tab) {
                addTab(BrowserUrlHelper.DEFAULT_HOME);
                return true;
            }
            if (id == R.id.browser_menu_history) {
                showHistoryDialog();
                return true;
            }
            if (id == R.id.browser_menu_saved_sessions) {
                showSavedSessionsDialog();
                return true;
            }
            if (id == R.id.browser_menu_save_session) {
                promptSaveSession();
                return true;
            }
            if (id == R.id.browser_menu_open_private) {
                startActivity(new Intent(this, SecureBrowserActivity.class));
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showHistoryDialog() {
        if (historyStore == null) return;
        List<BrowserHistoryStore.HistoryGroup> groups = historyStore.getHistoryGrouped();
        List<String> display = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (BrowserHistoryStore.HistoryGroup g : groups) {
            display.add("─── " + g.title + " ───");
            urls.add(null);
            for (BrowserHistoryStore.HistoryItem item : g.items) {
                display.add(item.title + "\n" + item.url);
                urls.add(item.url);
            }
        }

        if (display.isEmpty()) {
            Toast.makeText(this, "History is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_history)
                .setItems(display.toArray(new String[0]), (d, which) -> {
                    String selectedUrl = urls.get(which);
                    if (selectedUrl != null) {
                        navigateTo(selectedUrl);
                    }
                })
                .setNegativeButton(R.string.browser_clear_history, (d, w) -> {
                    historyStore.clearAll();
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showSavedSessionsDialog() {
        if (sessionVault == null) return;
        List<SessionVault.SavedSession> sessions = sessionVault.listSessions();
        if (sessions.isEmpty()) {
            Toast.makeText(this, "No saved sessions found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[sessions.size()];
        for (int i = 0; i < sessions.size(); i++) {
            names[i] = sessions.get(i).name + " (" + sessions.get(i).tabs.size() + " tabs)";
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_saved_sessions)
                .setItems(names, (d, which) -> {
                    SessionVault.SavedSession chosen = sessions.get(which);
                    for (SessionVault.SavedTab st : chosen.tabs) {
                        tabs.add(st.toBrowserTab());
                    }
                    currentTabIndex = tabs.size() - 1;
                    loadCurrentTab(false);
                    Toast.makeText(this, "Restored session " + chosen.name, Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void promptSaveSession() {
        if (sessionVault == null) return;
        EditText input = new EditText(this);
        input.setHint("Session name");
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_save_session)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        boolean ok = sessionVault.saveSession(name, tabs, "direct");
                        Toast.makeText(this, ok ? "Session saved" : "Failed to save session", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyDesktopMode() {
        WebSettings settings = webView.getSettings();
        if (desktopSite) {
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUserAgentString(
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/120.0.0.0 Safari/537.36 OceanDesktop");
        } else {
            settings.setUserAgentString(WebSettings.getDefaultUserAgent(this));
        }
    }

    private void navigateTo(String url) {
        hideError();
        currentTab().url = url;
        webView.loadUrl(url);
        updateUrlBar(url);
    }

    private void updateUrlBar(String url) {
        if (urlBarFocused) return;
        urlField.setText(url == null ? "" : url);
        secureIcon.setImageResource(BrowserUrlHelper.isSecureUrl(url)
                ? R.drawable.ic_lock_secure : R.drawable.ic_lock_insecure);
    }

    private void showError(CharSequence title, CharSequence message) {
        errorTitle.setText(title);
        errorMessage.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorPanel.setVisibility(View.GONE);
    }

    private BrowserTab currentTab() {
        return tabs.get(currentTabIndex);
    }

    private void addTab(String rawUrl) {
        saveWebState();
        String url = BrowserUrlHelper.normalizeInput(rawUrl);
        tabs.add(new BrowserTab(url));
        currentTabIndex = tabs.size() - 1;
        loadCurrentTab(false);
    }

    private void showTabSwitcher() {
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            String title = tab.title == null || tab.title.isEmpty() ? tab.url : tab.title;
            String prefix = (i == currentTabIndex ? "• " : "") + (tab.pinned ? "[Pinned] " : "") + (tab.locked ? "[Locked] " : "");
            labels[i] = prefix + title;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.browser_tabs_title)
                .setItems(labels, (dialog, which) -> {
                    BrowserTab tab = tabs.get(which);
                    if (tab.locked) {
                        new AlertDialog.Builder(this)
                                .setTitle(R.string.browser_lock_tab)
                                .setMessage(R.string.browser_tab_locked_msg)
                                .setPositiveButton(android.R.string.ok, (d, w) -> switchToTab(which))
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    } else {
                        switchToTab(which);
                    }
                })
                .setPositiveButton(R.string.browser_new_tab, (d, w) -> addTab(BrowserUrlHelper.DEFAULT_HOME))
                .setNeutralButton(R.string.browser_close_tab, (d, w) -> closeCurrentTab());

        if (!recentlyClosedTabs.isEmpty()) {
            builder.setNegativeButton(R.string.browser_reopen_tab, (d, w) -> reopenLastClosedTab());
        }
        builder.show();
    }

    private void reopenLastClosedTab() {
        if (recentlyClosedTabs.isEmpty()) return;
        BrowserTab restored = recentlyClosedTabs.removeFirst();
        tabs.add(restored);
        currentTabIndex = tabs.size() - 1;
        loadCurrentTab(true);
    }

    private void closeCurrentTab() {
        if (tabs.size() <= 1) {
            finish();
            return;
        }
        BrowserTab removed = tabs.remove(currentTabIndex);
        recentlyClosedTabs.addFirst(removed.copy());
        while (recentlyClosedTabs.size() > 15) {
            recentlyClosedTabs.removeLast();
        }
        if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
        loadCurrentTab(true);
    }

    private void switchToTab(int index) {
        if (index == currentTabIndex || index < 0 || index >= tabs.size()) return;
        saveWebState();
        currentTabIndex = index;
        loadCurrentTab(true);
    }

    private void loadCurrentTab(boolean restoreState) {
        BrowserTab tab = currentTab();
        desktopSite = tab.desktopSite;
        applyDesktopMode();
        hideError();
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        if (restoreState && tab.webState != null) {
            webView.restoreState(tab.webState);
        } else {
            webView.loadUrl(tab.url);
        }
        updateUrlBar(tab.url);
        updateTabBadge();
    }

    private void saveWebState() {
        if (tabs.isEmpty()) return;
        Bundle state = new Bundle();
        webView.saveState(state);
        currentTab().webState = state;
        String url = webView.getUrl();
        if (url != null && !url.isEmpty()) currentTab().url = url;
    }

    private void updateTabBadge() {
        tabBadge.setText(String.valueOf(currentTabIndex + 1) + "/" + tabs.size());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveWebState();
        outState.putInt("tab_index", currentTabIndex);
        outState.putInt("tab_count", tabs.size());
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            outState.putString("tab_url_" + i, tab.url);
            outState.putString("tab_title_" + i, tab.title);
            outState.putBoolean("tab_desktop_" + i, tab.desktopSite);
            if (tab.webState != null) outState.putBundle("tab_state_" + i, tab.webState);
        }
        super.onSaveInstanceState(outState);
    }

    private void restoreInstanceState(Bundle state) {
        int count = state.getInt("tab_count", 1);
        tabs.clear();
        for (int i = 0; i < count; i++) {
            BrowserTab tab = new BrowserTab(state.getString("tab_url_" + i, BrowserUrlHelper.DEFAULT_HOME));
            tab.title = state.getString("tab_title_" + i, "");
            tab.desktopSite = state.getBoolean("tab_desktop_" + i, false);
            tab.webState = state.getBundle("tab_state_" + i);
            tabs.add(tab);
        }
        currentTabIndex = Math.min(state.getInt("tab_index", 0), tabs.size() - 1);
        loadCurrentTab(true);
    }

    @Override
    protected void onPause() {
        saveWebState();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        super.onDestroy();
    }
}
