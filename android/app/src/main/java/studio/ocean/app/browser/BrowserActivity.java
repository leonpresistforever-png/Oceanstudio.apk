package studio.ocean.app.browser;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import studio.ocean.app.R;
import studio.ocean.app.browser.core.NavigationPolicy;
import studio.ocean.app.browser.normal.BrowserHistoryStore;
import studio.ocean.app.browser.secure.SecureBrowserActivity;
import studio.ocean.app.browser.storage.SessionVault;

/**
 * Normal persistent browser activity (PDF 4 & PDF 5).
 * Preserves persistent history in SQLite (BrowserHistoryStore), multi-tab state with PIN/biometric locking,
 * saved sessions in SessionVault, and standard browsing state.
 *
 * CRITICAL RULE: Never wipes or collapses into ephemeral private mode.
 */
public final class BrowserActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_URL = "browser_initial_url";
    private static final String PREF_LOCK = "ocean_browser_security_pref";
    private static final String KEY_PIN = "browser_tab_lock_pin";

    private final List<BrowserTab> tabs = new ArrayList<>();
    private final Deque<BrowserTab> recentlyClosedTabs = new ArrayDeque<>();
    private int currentTabIndex = 0;

    private WebView webView;
    private EditText urlField;
    private ImageView secureIcon;
    private TextView tabBadge;
    private ProgressBar progressBar;
    private LinearLayout errorPanel;
    private TextView errorTitle;
    private TextView errorMessage;
    private boolean urlBarFocused;
    private boolean desktopSite;

    @Nullable private BrowserHistoryStore historyStore;
    @Nullable private SessionVault sessionVault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);

        historyStore = new BrowserHistoryStore(this);
        sessionVault = new SessionVault(this);

        bindViews();
        configureWebView();
        wireActions();

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState);
        } else {
            String initial = getIntent().getStringExtra(EXTRA_INITIAL_URL);
            String startUrl = initial != null && !initial.trim().isEmpty()
                    ? BrowserUrlHelper.normalizeInput(initial)
                    : BrowserUrlHelper.DEFAULT_HOME;
            tabs.add(new BrowserTab(startUrl));
            currentTabIndex = 0;
            loadCurrentTab(false);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else if (tabs.size() > 1) {
                    closeCurrentTab();
                } else {
                    finish();
                }
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

    private void wireActions() {
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
            String text = urlField.getText().toString();
            navigateTo(BrowserUrlHelper.normalizeInput(text));
            urlField.clearFocus();
            return true;
        });
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setDownloadListener(downloadListener());
        webView.setWebChromeClient(new OceanBrowserWebChromeClient(new OceanBrowserWebChromeClient.Callback() {
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
                if (title != null && !title.trim().isEmpty()) {
                    currentTab().title = title.trim();
                }
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
                showHistoryScreen();
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

    /**
     * Proper History screen with search filter, grouped timeline, domain deletion, and scoped clearing (PDF 5 §8, §15).
     */
    private void showHistoryScreen() {
        if (historyStore == null) return;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        EditText searchBox = new EditText(this);
        searchBox.setHint("Search browsing history...");
        layout.addView(searchBox);

        ListView listView = new ListView(this);
        layout.addView(listView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 800));

        final List<BrowserHistoryStore.HistoryItem> activeItems = new ArrayList<>();
        final List<String> displayStrings = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, displayStrings);
        listView.setAdapter(adapter);

        Runnable refreshList = () -> {
            displayStrings.clear();
            activeItems.clear();
            String q = searchBox.getText().toString().trim();
            if (q.isEmpty()) {
                List<BrowserHistoryStore.HistoryGroup> groups = historyStore.getHistoryGrouped();
                for (BrowserHistoryStore.HistoryGroup g : groups) {
                    displayStrings.add("── " + g.title + " ──");
                    activeItems.add(null);
                    for (BrowserHistoryStore.HistoryItem it : g.items) {
                        displayStrings.add(it.title + "\n" + it.url);
                        activeItems.add(it);
                    }
                }
            } else {
                List<BrowserHistoryStore.HistoryItem> results = historyStore.search(q);
                for (BrowserHistoryStore.HistoryItem it : results) {
                    displayStrings.add(it.title + "\n" + it.url);
                    activeItems.add(it);
                }
            }
            adapter.notifyDataSetChanged();
        };

        refreshList.run();

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshList.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.browser_history)
                .setView(layout)
                .setNegativeButton(R.string.browser_clear_history, (d, w) -> {
                    showClearHistoryScopeDialog(refreshList);
                })
                .setPositiveButton(android.R.string.ok, null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            BrowserHistoryStore.HistoryItem item = activeItems.get(position);
            if (item != null) {
                dialog.dismiss();
                navigateTo(item.url);
            }
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            BrowserHistoryStore.HistoryItem item = activeItems.get(position);
            if (item == null) return false;

            String domain = extractDomain(item.url);
            String[] options = {"Open URL", "Delete this item", "Delete all from " + domain};
            new AlertDialog.Builder(this)
                    .setTitle(domain)
                    .setItems(options, (d, which) -> {
                        if (which == 0) {
                            dialog.dismiss();
                            navigateTo(item.url);
                        } else if (which == 1) {
                            historyStore.deleteItem(item.id);
                            refreshList.run();
                        } else if (which == 2) {
                            historyStore.clearDomain(domain);
                            refreshList.run();
                            Toast.makeText(this, "Cleared history for " + domain, Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
            return true;
        });

        dialog.show();
    }

    private void showClearHistoryScopeDialog(Runnable onCleared) {
        String[] options = {"Clear Today", "Clear Past 7 Days", "Clear All History"};
        new AlertDialog.Builder(this)
                .setTitle("Clear History Range")
                .setItems(options, (d, which) -> {
                    if (historyStore == null) return;
                    long now = System.currentTimeMillis();
                    if (which == 0) {
                        long dayAgo = now - (24 * 3600 * 1000);
                        historyStore.clearTimeRange(dayAgo, now);
                        Toast.makeText(this, "Cleared today's history", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        long weekAgo = now - (7 * 24 * 3600 * 1000);
                        historyStore.clearTimeRange(weekAgo, now);
                        Toast.makeText(this, "Cleared past 7 days history", Toast.LENGTH_SHORT).show();
                    } else {
                        historyStore.clearAll();
                        Toast.makeText(this, "All history cleared", Toast.LENGTH_SHORT).show();
                    }
                    if (onCleared != null) onCleared.run();
                })
                .show();
    }

    private String extractDomain(String url) {
        try {
            String host = NavigationPolicy.extractHost(url);
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
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
                    promptRestoreChoice(chosen);
                })
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void promptRestoreChoice(SessionVault.SavedSession session) {
        new AlertDialog.Builder(this)
                .setTitle("Restore: " + session.name)
                .setMessage("Choose how to restore this session (" + session.tabs.size() + " tabs):")
                .setPositiveButton("Replace Current Tabs", (d, w) -> {
                    // Close unpinned tabs
                    tabs.removeIf(t -> !t.pinned);
                    for (SessionVault.SavedTab st : session.tabs) {
                        tabs.add(st.toBrowserTab());
                    }
                    if (tabs.isEmpty()) tabs.add(new BrowserTab(BrowserUrlHelper.DEFAULT_HOME));
                    currentTabIndex = tabs.size() - 1;
                    loadCurrentTab(false);
                    Toast.makeText(this, "Replaced session with " + session.name, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Add to Current Tabs", (d, w) -> {
                    for (SessionVault.SavedTab st : session.tabs) {
                        tabs.add(st.toBrowserTab());
                    }
                    currentTabIndex = tabs.size() - 1;
                    loadCurrentTab(false);
                    Toast.makeText(this, "Appended session " + session.name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
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

    /**
     * Enhanced Tab Switcher with real Pin/Unpin, Lock/Unlock, and authentication gating (PDF 5 §8).
     */
    private void showTabSwitcher() {
        String[] labels = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            String title = tab.title == null || tab.title.isEmpty() ? tab.url : tab.title;
            String prefix = (i == currentTabIndex ? "• " : "")
                    + (tab.pinned ? "[Pinned 📌] " : "")
                    + (tab.locked ? "[Locked 🔒] " : "");
            labels[i] = prefix + title;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.browser_tabs_title)
                .setItems(labels, (dialog, which) -> {
                    BrowserTab tab = tabs.get(which);
                    if (tab.locked) {
                        challengeTabUnlock(which);
                    } else {
                        switchToTab(which);
                    }
                })
                .setPositiveButton(R.string.browser_new_tab, (d, w) -> addTab(BrowserUrlHelper.DEFAULT_HOME))
                .setNeutralButton("Tab Options", (d, w) -> showTabOptionsDialog());

        if (!recentlyClosedTabs.isEmpty()) {
            builder.setNegativeButton(R.string.browser_reopen_tab, (d, w) -> reopenLastClosedTab());
        }
        builder.show();
    }

    private void showTabOptionsDialog() {
        String[] tabNames = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab t = tabs.get(i);
            tabNames[i] = (t.pinned ? "📌 " : "") + (t.locked ? "🔒 " : "") + (t.title.isEmpty() ? t.url : t.title);
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Tab to Configure")
                .setItems(tabNames, (d, which) -> {
                    BrowserTab target = tabs.get(which);
                    String pinAction = target.pinned ? "Unpin Tab" : "Pin Tab";
                    String lockAction = target.locked ? "Unlock Tab" : "Lock Tab (PIN Protected)";
                    String closeAction = "Close Tab";

                    new AlertDialog.Builder(this)
                            .setTitle("Configure Tab " + (which + 1))
                            .setItems(new String[]{pinAction, lockAction, closeAction}, (d2, act) -> {
                                if (act == 0) {
                                    target.pinned = !target.pinned;
                                    Toast.makeText(this, target.pinned ? "Tab pinned" : "Tab unpinned", Toast.LENGTH_SHORT).show();
                                    updateTabBadge();
                                } else if (act == 1) {
                                    if (target.locked) {
                                        // Require unlock to remove lock
                                        challengePin(entered -> {
                                            if (entered) {
                                                target.locked = false;
                                                Toast.makeText(this, "Tab unlocked", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    } else {
                                        ensurePinSet(set -> {
                                            if (set) {
                                                target.locked = true;
                                                Toast.makeText(this, "Tab locked with PIN", Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                } else if (act == 2) {
                                    if (target.pinned) {
                                        Toast.makeText(this, "Unpin tab before closing", Toast.LENGTH_SHORT).show();
                                    } else {
                                        closeTabAtIndex(which);
                                    }
                                }
                            })
                            .show();
                })
                .show();
    }

    private void challengeTabUnlock(int tabIndex) {
        challengePin(granted -> {
            if (granted) {
                switchToTab(tabIndex);
            } else {
                Toast.makeText(this, "Authentication failed. Tab remains locked.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private interface AuthCallback { void onResult(boolean success); }

    private void challengePin(AuthCallback callback) {
        SharedPreferences sp = getSharedPreferences(PREF_LOCK, Context.MODE_PRIVATE);
        String savedPin = sp.getString(KEY_PIN, null);
        if (savedPin == null) {
            // No PIN yet configured; grant access
            callback.onResult(true);
            return;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Enter 4-digit PIN");

        new AlertDialog.Builder(this)
                .setTitle("Unlock Tab")
                .setMessage("Enter PIN to access this protected tab:")
                .setView(input)
                .setPositiveButton("Unlock", (d, w) -> {
                    String entered = input.getText().toString().trim();
                    if (savedPin.equals(entered)) {
                        callback.onResult(true);
                    } else {
                        callback.onResult(false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> callback.onResult(false))
                .show();
    }

    private void ensurePinSet(AuthCallback callback) {
        SharedPreferences sp = getSharedPreferences(PREF_LOCK, Context.MODE_PRIVATE);
        String savedPin = sp.getString(KEY_PIN, null);
        if (savedPin != null && !savedPin.isEmpty()) {
            callback.onResult(true);
            return;
        }

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Set 4-digit PIN");

        new AlertDialog.Builder(this)
                .setTitle("Set Security PIN")
                .setMessage("Create a 4-digit PIN to protect locked tabs:")
                .setView(input)
                .setPositiveButton("Save PIN", (d, w) -> {
                    String pin = input.getText().toString().trim();
                    if (pin.length() >= 4) {
                        sp.edit().putString(KEY_PIN, pin).apply();
                        callback.onResult(true);
                    } else {
                        Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show();
                        callback.onResult(false);
                    }
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> callback.onResult(false))
                .show();
    }

    private void reopenLastClosedTab() {
        if (recentlyClosedTabs.isEmpty()) return;
        BrowserTab restored = recentlyClosedTabs.removeFirst();
        tabs.add(restored);
        currentTabIndex = tabs.size() - 1;
        loadCurrentTab(true);
    }

    private void closeCurrentTab() {
        closeTabAtIndex(currentTabIndex);
    }

    private void closeTabAtIndex(int index) {
        if (tabs.size() <= 1) {
            finish();
            return;
        }
        BrowserTab target = tabs.get(index);
        if (target.pinned) {
            Toast.makeText(this, "Tab is pinned", Toast.LENGTH_SHORT).show();
            return;
        }
        BrowserTab removed = tabs.remove(index);
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
        if (webView == null || tabs.isEmpty()) return;
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
            outState.putBoolean("tab_pinned_" + i, tab.pinned);
            outState.putBoolean("tab_locked_" + i, tab.locked);
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
            tab.pinned = state.getBoolean("tab_pinned_" + i, false);
            tab.locked = state.getBoolean("tab_locked_" + i, false);
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
