package studio.ocean.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.util.Patterns;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.UnderlineSpan;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.graphics.Typeface;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native auth and chat-first workspace. No WebView or browser bridge is involved. */
public class MainActivity extends AppCompatActivity {
    private enum AuthMode { SIGN_IN, SIGN_UP, FORGOT }
    private enum AuthState { LOADING, CONFIGURED_LOGGED_OUT, CONFIGURED_LOGGED_IN, DEV_BYPASS_LOGGED_IN, CONFIGURATION_MISSING, ERROR }
    private static final String PREFS="ocean_auth", TOKEN="id_token", EMAIL="email";
    private static final String RECENT_PREFS = "ocean_recent_chats";
    private static final String RECENT_KEY = "titles";
    private AuthClient authClient;
    private AuthOAuth authOAuth;
    private AuthMode authMode = AuthMode.SIGN_IN;
    private AuthState authState = AuthState.LOADING;
    private boolean developmentSession;
    private LinearLayout sidebar; private View agentControlsDrawer, backdrop; private boolean drawerOpen, agentControlsOpen;
    private AgentControlsPanel agentControlsPanel;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private int loadingGeneration;
    private OceanByokManager byokManager;
    private OceanAgentRunner agentRunner;
    private FrameLayout contentFrame;
    private ScrollView chatScrollView;
    private LinearLayout chatMessagesLayout;
    private String selectedModelPreset = "auto";
    private static final String[][] SLASH_COMMANDS = {
            {"/providers", "Manage AI providers & Direct Connect"},
            {"/local", "Manage on-device GGUF local models"},
            {"/browser", "Open Secure Private Browser"},
            {"/run", "Run a terminal command"},
            {"/terminal", "Open Ocean Terminal"},
            {"/new", "Start a fresh conversation"},
            {"/model", "Configure the active model & providers"},
            {"/mode", "Choose agent work or cost mode"},
            {"/cost", "Use fewer tokens and tool calls"},
            {"/work", "Use full agent capacity"},
            {"/plugins", "Manage connected tools"},
            {"/settings", "Open raw agent configuration drawer"},
            {"/skills", "Open skills in agent drawer"},
            {"/mcp", "Open MCP hub"},
            {"/function", "Create an HTTP function"},
            {"/forge", "Open Ocean Forge workspace"},
            {"/screen", "Ask the agent to inspect this screen"},
            {"/files", "Ask the agent to work with files"}
    };

    private void safeClick(int id, View.OnClickListener l) {
        View v = findViewById(id);
        if (v != null) v.setOnClickListener(l);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            studio.ocean.app.terminal.PreviousProcessExit.capture(this);
        } catch (Throwable t) {
            android.util.Log.w("MainActivity", "PreviousProcessExit capture failed", t);
        }
        try {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { if (agentControlsOpen) closeAgentControls(); else if (drawerOpen) closeDrawer(); else finish(); }});
        } catch (Throwable t) {
            android.util.Log.w("MainActivity", "Back dispatcher callback failed", t);
        }
        authClient = new AuthClient(this);
        authOAuth = new AuthOAuth(this, authClient);
        showLoading(() -> {
            try {
                byokManager = new OceanByokManager(this);
                agentRunner = new OceanAgentRunner(this);
                if (getSharedPreferences(PREFS,MODE_PRIVATE).contains(TOKEN)) { authState=AuthState.CONFIGURED_LOGGED_IN; showMain(); }
                else { authState=authClient.configured()?AuthState.CONFIGURED_LOGGED_OUT:AuthState.CONFIGURATION_MISSING; showAuth(); }
            } catch (Throwable t) {
                android.util.Log.e("MainActivity", "Startup initialization failed", t);
                try { showAuth(); } catch (Throwable ignored) {}
            }
        });
    }

    @Override protected void onPostResume() {
        super.onPostResume();
        try {
            CrashSurvival.showPending(this);
            if (agentControlsOpen) refreshAgentControlsSummary();
            updateTopModelChip();
        } catch (Throwable t) {
            android.util.Log.w("MainActivity", "onPostResume error", t);
        }
    }

    private void showLoading(Runnable destination) {
        final int generation=++loadingGeneration;
        try {
            setContentView(R.layout.activity_loading);
            View logo=findViewById(R.id.loading_logo), wordmark=findViewById(R.id.loading_wordmark);
            if (logo != null) logo.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
            if (wordmark != null) wordmark.animate().alpha(1f).setStartDelay(90).setDuration(260).start();
            int[] dots={R.id.loading_dot_one,R.id.loading_dot_two,R.id.loading_dot_three,R.id.loading_dot_four};
            Runnable pulse=new Runnable(){int index; public void run(){if(generation!=loadingGeneration)return; for(int i=0;i<dots.length;i++){View d=findViewById(dots[i]); if(d!=null) d.animate().alpha(i==index?0.9f:0.28f).scaleX(i==index?1.25f:1f).scaleY(i==index?1.25f:1f).setDuration(140).start();} index=(index+1)%dots.length; uiHandler.postDelayed(this,170);}};
            uiHandler.post(pulse);
            uiHandler.postDelayed(() -> {
                if(generation!=loadingGeneration)return;
                if (logo != null) logo.animate().alpha(0f).translationY(-4f).setDuration(180).start();
                if (wordmark != null) {
                    wordmark.animate().alpha(0f).setDuration(160).withEndAction(() -> { loadingGeneration++; destination.run(); }).start();
                } else {
                    loadingGeneration++; destination.run();
                }
            }, 550);
        } catch (Throwable t) {
            android.util.Log.w("MainActivity", "showLoading layout failed, proceeding immediately", t);
            destination.run();
        }
    }

    private void showAuth() {
        setContentView(R.layout.activity_auth);
        safeClick(R.id.auth_primary, v -> submitAuth());
        safeClick(R.id.forgot_password, v -> setAuthMode(AuthMode.FORGOT));
        safeClick(R.id.auth_switch, v -> setAuthMode(authMode == AuthMode.SIGN_IN ? AuthMode.SIGN_UP : AuthMode.SIGN_IN));
        safeClick(R.id.google_auth, v -> authOAuth.signInWithGoogle(oauthListener()));
        safeClick(R.id.github_auth, v -> authOAuth.signInWithGithub(oauthListener()));
        authOAuth.resumePending(oauthListener());
        View devIndicator = findViewById(R.id.dev_auth_indicator);
        if (devIndicator != null) devIndicator.setVisibility(devBypassAvailable()?View.VISIBLE:View.GONE);
        View devContinue = findViewById(R.id.dev_auth_continue);
        if (devContinue != null) {
            devContinue.setVisibility(devBypassAvailable()?View.VISIBLE:View.GONE);
            devContinue.setOnClickListener(v -> startDevelopmentSession());
        }
        setAuthMode(AuthMode.SIGN_IN);
    }

    private void setAuthMode(AuthMode mode) {
        authMode=mode; TextView title=findViewById(R.id.auth_title), subtitle=findViewById(R.id.auth_subtitle), toggle=findViewById(R.id.auth_switch), forgot=findViewById(R.id.forgot_password); Button primary=findViewById(R.id.auth_primary); View password=findViewById(R.id.auth_password), passwordLabel=findViewById(R.id.password_label), confirm=findViewById(R.id.auth_confirm), confirmLabel=findViewById(R.id.confirm_label), social=findViewById(R.id.social_buttons), divider=findViewById(R.id.auth_divider);
        boolean signup=mode==AuthMode.SIGN_UP, reset=mode==AuthMode.FORGOT;
        if (title != null) title.setText(reset?R.string.reset_password:signup?R.string.create_account:R.string.welcome_back);
        if (subtitle != null) subtitle.setText(reset?R.string.reset_subtitle:signup?R.string.signup_subtitle:R.string.auth_subtitle);
        if (primary != null) primary.setText(reset?R.string.send_reset:signup?R.string.sign_up:R.string.sign_in);
        if (toggle != null) toggle.setText(reset?R.string.back_to_signin:signup?R.string.have_account_signin:R.string.no_account_signup);
        if (password != null) password.setVisibility(reset?View.GONE:View.VISIBLE);
        if (passwordLabel != null) passwordLabel.setVisibility(reset?View.GONE:View.VISIBLE);
        if (confirm != null) confirm.setVisibility(signup?View.VISIBLE:View.GONE);
        if (confirmLabel != null) confirmLabel.setVisibility(signup?View.VISIBLE:View.GONE);
        if (forgot != null) forgot.setVisibility(mode==AuthMode.SIGN_IN?View.VISIBLE:View.GONE);
        if (social != null) social.setVisibility(reset?View.GONE:View.VISIBLE);
        if (divider != null) divider.setVisibility(reset?View.GONE:View.VISIBLE);
        View authStatus = findViewById(R.id.auth_status);
        if (authStatus != null) authStatus.setVisibility(View.GONE);
        View authErrorPanel = findViewById(R.id.auth_error_panel);
        if (authErrorPanel != null) authErrorPanel.setVisibility(View.GONE);
        String footer=getString(reset?R.string.back_to_signin:signup?R.string.have_account_signin:R.string.no_account_signup); String action=reset?footer:(signup?"Sign in":"Sign up"); SpannableString footerText=new SpannableString(footer); int actionStart=footer.lastIndexOf(action);
        if (actionStart >= 0 && toggle != null) {
            footerText.setSpan(new UnderlineSpan(),actionStart,footer.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            toggle.setText(footerText);
        }
        View p1 = findViewById(R.id.progress_one), p2 = findViewById(R.id.progress_two), p3 = findViewById(R.id.progress_three);
        if (p1 != null) p1.setBackgroundResource(R.drawable.progress_active);
        if (p2 != null) p2.setBackgroundResource(signup?R.drawable.progress_active:R.drawable.progress_inactive);
        if (p3 != null) p3.setBackgroundResource(R.drawable.progress_inactive);
        if (title != null) {
            title.setAlpha(0f); title.setTranslationY(10f); title.animate().alpha(1f).translationY(0f).setDuration(240).start();
        }
    }

    private void submitAuth() {
        EditText emailView=findViewById(R.id.auth_email), passwordView=findViewById(R.id.auth_password), confirmView=findViewById(R.id.auth_confirm);
        String email = emailView != null ? emailView.getText().toString().trim() : "";
        String password = passwordView != null ? passwordView.getText().toString() : "";
        if (!authClient.configured() && devBypassAvailable() && authMode==AuthMode.SIGN_IN && !email.isEmpty() && !password.isEmpty()) { startDevelopmentSession(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { showAuthStatus(getString(R.string.invalid_email),false); return; }
        if (!authClient.configured()) { authState=AuthState.CONFIGURATION_MISSING; showAuthStatus(getString(R.string.auth_unavailable),false); return; }
        if (authMode==AuthMode.SIGN_UP && confirmView != null && !password.equals(confirmView.getText().toString())) { showAuthStatus(getString(R.string.password_mismatch),false); return; }
        if (authMode!=AuthMode.FORGOT && password.length()<6) { showAuthStatus(getString(R.string.password_short),false); return; }
        setAuthBusy(true);
        AuthClient.Callback callback=result -> runOnUiThread(() -> { setAuthBusy(false); if (!result.success) { authState=AuthState.ERROR; showAuthStatus(result.message==null?getString(R.string.request_failed):result.message,false); return; } if (authMode==AuthMode.FORGOT) { showAuthStatus(getString(R.string.reset_sent),true); return; } authState=AuthState.CONFIGURED_LOGGED_IN; getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(TOKEN,result.token).putString(EMAIL,result.email).apply(); showLoading(this::showMain); });
        if (authMode==AuthMode.FORGOT) authClient.reset(email,callback); else authClient.signIn(email,password,authMode==AuthMode.SIGN_UP,callback);
    }

    private AuthOAuth.Listener oauthListener() {
        return new AuthOAuth.Listener() {
            @Override public void onSuccess(String token, String email) {
                authState = AuthState.CONFIGURED_LOGGED_IN;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(TOKEN, token).putString(EMAIL, email).apply();
                showLoading(MainActivity.this::showMain);
            }
            @Override public void onFailure(String message) {
                authState = AuthState.ERROR;
                showAuthStatus(getString(R.string.auth_error_title), message, null, false);
            }
            @Override public void onFailure(String title, String message, String details) {
                authState = AuthState.ERROR;
                showAuthStatus(title, message, details, false);
            }
        };
    }

    private void setAuthBusy(boolean busy) { AuthOAuth.ViewHelper.setAuthBusy(this, busy); }
    private void showAuthStatus(String message, boolean success) {
        showAuthStatus(success ? null : getString(R.string.auth_error_title), message, null, success);
    }

    private void showAuthStatus(String title, String message, String details, boolean success) {
        View panel = findViewById(R.id.auth_error_panel);
        TextView titleView = findViewById(R.id.auth_error_title);
        TextView msgView = findViewById(R.id.auth_error_message);
        TextView toggleView = findViewById(R.id.auth_error_toggle_details);
        TextView detailsView = findViewById(R.id.auth_error_details);

        if (panel != null && titleView != null && msgView != null) {
            titleView.setText(title == null ? "" : title);
            titleView.setVisibility(title != null && !title.isEmpty() ? View.VISIBLE : View.GONE);
            msgView.setText(message == null ? "" : message);
            if (details != null && !details.isEmpty() && BuildConfig.DEBUG) {
                detailsView.setText(details);
                toggleView.setVisibility(View.VISIBLE);
                toggleView.setOnClickListener(v -> {
                    boolean visible = detailsView.getVisibility() == View.VISIBLE;
                    detailsView.setVisibility(visible ? View.GONE : View.VISIBLE);
                });
            } else {
                if (toggleView != null) toggleView.setVisibility(View.GONE);
                if (detailsView != null) detailsView.setVisibility(View.GONE);
            }
            panel.setVisibility(View.VISIBLE);
        } else {
            TextView status = findViewById(R.id.auth_status);
            if (status != null) {
                status.setText(message);
                status.setTextColor(getColor(success ? R.color.ocean_ink_100 : R.color.ocean_ink_75));
                status.setVisibility(View.VISIBLE);
            }
        }
    }
    private boolean devBypassAvailable() { return BuildConfig.DEBUG && BuildConfig.OCEAN_DEV_AUTH_BYPASS; }
    private void startDevelopmentSession() { if (!devBypassAvailable()) return; developmentSession=true; authState=AuthState.DEV_BYPASS_LOGGED_IN; showLoading(this::showMain); }

    private void showMain() {
        setContentView(R.layout.activity_main);
        EditText eyePrompt = findViewById(R.id.prompt);
        studio.ocean.app.render.RoboticEyeView roboticEye = findViewById(R.id.robotic_eye);
        if (eyePrompt != null && roboticEye != null) {
            eyePrompt.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                    roboticEye.setTyping(eyePrompt.hasFocus() && text.length() > 0);
                }
                @Override public void afterTextChanged(Editable text) {}
            });
            eyePrompt.setOnFocusChangeListener((view, focused) -> roboticEye.setTyping(focused && eyePrompt.length() > 0));
            View mainRoot = findViewById(R.id.main_root);
            if (mainRoot != null) {
                mainRoot.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                        float nx = event.getX() / Math.max(1f, v.getWidth());
                        float ny = event.getY() / Math.max(1f, v.getHeight());
                        roboticEye.setGaze(nx, ny);
                    }
                    return false;
                });
            }
        }
        if (eyePrompt != null) eyePrompt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                showSlashOptions(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        sidebar=findViewById(R.id.sidebar);
        agentControlsDrawer=findViewById(R.id.agent_controls_drawer);
        backdrop=findViewById(R.id.drawer_backdrop);
        agentControlsPanel = new AgentControlsPanel(this, agentControlsDrawer, new AgentControlsPanel.Host() {
            @Override public void close() { closeAgentControls(); }
            @Override public void openPlugins() { startActivity(new Intent(MainActivity.this, PluginCenterActivity.class)); }
            @Override public void openByok() { closeAgentControls(); showByokPage(); }
            @Override public void openDevice() { startActivity(new Intent(MainActivity.this, studio.ocean.app.device.DeviceAccessActivity.class)); }
            @Override public void openRuntime() { startActivity(new Intent(MainActivity.this, studio.ocean.app.runtime.RuntimePortsActivity.class)); }
            @Override public OceanByokManager byok() { return byokManager; }
        });
        safeClick(R.id.menu_button, v -> openDrawer());
        safeClick(R.id.agent_controls_button, v -> openAgentControls());
        if (backdrop != null) backdrop.setOnClickListener(v -> { if (agentControlsOpen) closeAgentControls(); else closeDrawer(); });
        safeClick(R.id.sidebar_new_chat, v -> newChat());
        
        TextView modelBtn = findViewById(R.id.model_button);
        if (modelBtn != null) {
            modelBtn.setOnClickListener(v -> showModelHubChooser());
        }
        View headerBlock = findViewById(R.id.header_title_block);
        if (headerBlock != null) headerBlock.setOnClickListener(v -> showModelHubChooser());
        safeClick(R.id.history_button, v -> refreshAgentSession());
        safeClick(R.id.new_chat_button, v -> newChat());
        safeClick(R.id.suggest_card_providers, v -> startActivity(new Intent(this, studio.ocean.app.providers.ProvidersConnectActivity.class)));
        safeClick(R.id.suggest_card_local, v -> startActivity(new Intent(this, studio.ocean.app.models.local.LocalModelsActivity.class)));
        safeClick(R.id.suggest_card_browser, v -> startActivity(new Intent(this, studio.ocean.app.browser.secure.SecureBrowserActivity.class)));
        safeClick(R.id.suggest_card_plan, v -> setStarterPrompt("Plan and execute this task: "));
        safeClick(R.id.suggest_card_terminal, v -> setStarterPrompt("Use my terminal to "));
        safeClick(R.id.suggest_card_build, v -> setStarterPrompt("Build or improve "));
        updateTopModelChip();
        safeClick(R.id.send_button, v -> { if (agentRunner != null && agentRunner.isRunning()) agentRunner.cancel(); else submitAgentPrompt(); });
        safeClick(R.id.starter_providers, v -> startActivity(new Intent(this, studio.ocean.app.providers.ProvidersConnectActivity.class)));
        safeClick(R.id.starter_local_models, v -> startActivity(new Intent(this, studio.ocean.app.models.local.LocalModelsActivity.class)));
        safeClick(R.id.starter_private_browser, v -> startActivity(new Intent(this, studio.ocean.app.browser.secure.SecureBrowserActivity.class)));
        safeClick(R.id.starter_plan, v -> setStarterPrompt("Plan and execute this task: "));
        safeClick(R.id.starter_terminal, v -> setStarterPrompt("Use my terminal to "));
        safeClick(R.id.starter_build, v -> setStarterPrompt("Build or improve "));
        safeClick(R.id.quick_providers, v -> startActivity(new Intent(this, studio.ocean.app.providers.ProvidersConnectActivity.class)));
        safeClick(R.id.quick_local, v -> startActivity(new Intent(this, studio.ocean.app.models.local.LocalModelsActivity.class)));
        safeClick(R.id.quick_browser, v -> startActivity(new Intent(this, studio.ocean.app.browser.secure.SecureBrowserActivity.class)));
        safeClick(R.id.quick_search, v -> openAgentSearch());
        safeClick(R.id.quick_files, v -> { closeDrawer(); bindQuickDestination("Files"); });
        safeClick(R.id.quick_screenshot, v -> setStarterPrompt("Take a screenshot and inspect it"));
        safeClick(R.id.quick_screen, v -> setStarterPrompt("Inspect my current screen and "));
        safeClick(R.id.quick_voice, v -> Toast.makeText(this, "Voice input ready", Toast.LENGTH_SHORT).show());
        safeClick(R.id.quick_more, v -> openAgentControls());

        bindGroup(R.id.group_workspace,R.id.workspace_children,R.id.chevron_workspace);
        bindGroup(R.id.group_agents,R.id.agents_children,R.id.chevron_agents);
        bindGroup(R.id.group_tools,R.id.tools_children,R.id.chevron_tools);
        bindGroup(R.id.group_connections,R.id.connections_children,R.id.chevron_connections);
        bindDestination(R.id.nav_agent,"OceanStudio");
        bindDestination(R.id.nav_editor,"Editor");
        bindDestination(R.id.nav_files,"Files");
        bindDestination(R.id.nav_preview,"Preview");
        safeClick(R.id.nav_terminal, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.terminal.OceanTerminalActivity.class)); });
        safeClick(R.id.nav_x11, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.render.OceanX11Activity.class)); });
        safeClick(R.id.nav_runtime_ports, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.runtime.RuntimePortsActivity.class)); });
        safeClick(R.id.nav_browser, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.browser.BrowserActivity.class)); });
        safeClick(R.id.nav_private_browser, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.browser.secure.SecureBrowserActivity.class)); });
        safeClick(R.id.nav_agent_settings, v -> { closeDrawer(); openAgentControls(); });
        safeClick(R.id.nav_hub_plugins, v -> { closeDrawer(); startActivity(new Intent(this, PluginCenterActivity.class)); });
        safeClick(R.id.nav_hub_device, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.device.DeviceAccessActivity.class)); });
        safeClick(R.id.nav_hub_runtime, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.runtime.RuntimePortsActivity.class)); });
        safeClick(R.id.nav_hub_models, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.providers.ProvidersConnectActivity.class)); });
        safeClick(R.id.nav_crash_diagnostics, v -> { closeDrawer(); startActivity(new Intent(this, CrashDiagnosticsActivity.class)); });
        safeClick(R.id.nav_plugins, v -> { closeDrawer(); startActivity(new Intent(this, PluginCenterActivity.class)); });
        
        // Add Playground + Providers Hub + Local Models + BYOK into sidebar Tools children.
        LinearLayout toolsChildren = findViewById(R.id.tools_children);
        if (toolsChildren != null) {
            TextView playgroundNav = new TextView(this);
            playgroundNav.setText("Playground");
            playgroundNav.setTextColor(getColor(R.color.ocean_ink));
            playgroundNav.setTextSize(14f);
            playgroundNav.setPadding(dp(24),dp(16),dp(24),dp(16));
            playgroundNav.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_agent), null, null, null);
            playgroundNav.setCompoundDrawablePadding(dp(12));
            playgroundNav.setOnClickListener(v -> { closeDrawer(); startActivity(new Intent(this, PlaygroundActivity.class)); });
            toolsChildren.addView(playgroundNav, 0);

            TextView deviceNav = new TextView(this);
            deviceNav.setText("Device Access");
            deviceNav.setTextSize(14);
            deviceNav.setTextColor(0xFF44464B);
            deviceNav.setPadding(dp(24),dp(16),dp(24),dp(16));
            deviceNav.setOnClickListener(v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.device.DeviceAccessActivity.class)); });
            toolsChildren.addView(deviceNav);

            TextView byokNav = new TextView(this);
            byokNav.setText("BYOK Custom Endpoint");
            byokNav.setTextColor(getColor(R.color.ocean_ink));
            byokNav.setTextSize(14f);
            byokNav.setPadding((int)(16 * getResources().getDisplayMetrics().density), (int)(10 * getResources().getDisplayMetrics().density), (int)(16 * getResources().getDisplayMetrics().density), (int)(10 * getResources().getDisplayMetrics().density));
            byokNav.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_tools), null, null, null);
            byokNav.setCompoundDrawablePadding((int)(12 * getResources().getDisplayMetrics().density));
            byokNav.setOnClickListener(v -> { closeDrawer(); showByokPage(); });
            toolsChildren.addView(byokNav, 1);
        }

        safeClick(R.id.nav_models, v -> { closeDrawer(); showModelHubChooser(); });
        safeClick(R.id.nav_providers, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.providers.ProvidersConnectActivity.class)); });
        safeClick(R.id.nav_local_models, v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.models.local.LocalModelsActivity.class)); });
        safeClick(R.id.nav_plugins, v -> { closeDrawer(); startActivity(new Intent(this, PluginCenterActivity.class)); });

        findViewById(R.id.sign_out).setOnClickListener(v -> { developmentSession=false; authState=authClient.configured()?AuthState.CONFIGURED_LOGGED_OUT:AuthState.CONFIGURATION_MISSING; getSharedPreferences(PREFS,MODE_PRIVATE).edit().clear().apply(); showAuth(); });
        TextView versionView = findViewById(R.id.app_version_provenance);
        if (versionView != null) {
            String commit = BuildConfig.OCEAN_BUILD_COMMIT;
            if (commit != null && commit.length() > 8) commit = commit.substring(0, 8);
            versionView.setText("v" + BuildConfig.VERSION_NAME + " (" + commit + ")");
            versionView.setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(this, CrashDiagnosticsActivity.class));
            });
        }
        sidebar.post(() -> { int width=Math.min((int)(getResources().getDisplayMetrics().widthPixels*.76f),(int)(360*getResources().getDisplayMetrics().density)); ViewGroup.LayoutParams p=sidebar.getLayoutParams(); p.width=width; sidebar.setLayoutParams(p); sidebar.setTranslationX(-width); });
        View skeleton=findViewById(R.id.home_skeleton), content=findViewById(R.id.home_content); content.post(() -> { skeleton.animate().alpha(0f).setDuration(220).withEndAction(() -> skeleton.setVisibility(View.GONE)).start(); content.animate().alpha(1f).translationY(0f).setDuration(260).start(); });
        View recentSkeleton=findViewById(R.id.recent_skeleton); recentSkeleton.post(() -> { recentSkeleton.animate().alpha(0f).setDuration(180).withEndAction(() -> { recentSkeleton.setVisibility(View.GONE); refreshRecentSessions(); }).start(); });

        initChatContainer();
    }

    private void initChatContainer() {
        View homeContent = findViewById(R.id.home_content);
        if (homeContent != null && homeContent.getParent() instanceof FrameLayout) {
            contentFrame = (FrameLayout) homeContent.getParent();
            chatScrollView = new ScrollView(this);
            chatScrollView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            chatScrollView.setBackgroundColor(0x00000000);
            chatScrollView.setClipToPadding(false);
            chatScrollView.setVisibility(View.GONE);

            chatMessagesLayout = new LinearLayout(this);
            chatMessagesLayout.setOrientation(LinearLayout.VERTICAL);
            int pad = (int)(16 * getResources().getDisplayMetrics().density);
            int eyeClearance = (int)(168 * getResources().getDisplayMetrics().density);
            chatMessagesLayout.setPadding(pad, eyeClearance, pad, pad);
            chatScrollView.addView(chatMessagesLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            contentFrame.addView(chatScrollView);
        }
    }

    private void showByokPage() {
        if (contentFrame == null) initChatContainer();
        findViewById(R.id.home_content).setVisibility(View.GONE);
        if (chatScrollView != null) chatScrollView.setVisibility(View.GONE);

        View oldByok = contentFrame.findViewWithTag("BYOK_VIEW");
        if (oldByok != null) contentFrame.removeView(oldByok);

        TextView screenTitle = findViewById(R.id.screen_title);
        if (screenTitle != null) screenTitle.setText("Providers");

        ScrollView scroll = new ScrollView(this);
        scroll.setTag("BYOK_VIEW");
        scroll.setBackgroundColor(0xFFFFFFFF);
        scroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Models & connections");
        title.setTextSize(24f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF191817);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Connect your model to chat and run commands in Ocean. Terminal results appear here, without opening the terminal screen.");
        sub.setTextSize(14f);
        sub.setLineSpacing(dp(3),1f);
        sub.setTextColor(0xFF7B7873);
        sub.setPadding(0, dp(10), 0, dp(22));
        layout.addView(sub);
        addDivider(layout, 8);

        TextView pLabel = new TextView(this);
        pLabel.setText("SERVICE PROVIDER");
        pLabel.setTextSize(12f);
        pLabel.setTypeface(null, Typeface.BOLD);
        pLabel.setTextColor(0xFF191817);
        layout.addView(pLabel);

        Spinner providerSpinner = new Spinner(this);
        String[] providers = {"Google AI (Gemini)", "Anthropic (Claude)", "OpenAI", "Custom Endpoint"};
        ArrayAdapter<String> pAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, providers);
        providerSpinner.setAdapter(pAdapter);
        providerSpinner.setBackgroundResource(R.drawable.composer_background);
        layout.addView(providerSpinner,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView mLabel = new TextView(this);
        mLabel.setText("ACTIVE MODEL");
        mLabel.setTextSize(12f);
        mLabel.setTypeface(null, Typeface.BOLD);
        mLabel.setTextColor(0xFF191817);
        mLabel.setPadding(0, dp(20), 0, dp(8));
        layout.addView(mLabel);

        EditText modelInput = new EditText(this);
        modelInput.setHint("Model ID, e.g. gemini-2.5-flash");
        modelInput.setSingleLine(true);
        modelInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        modelInput.setText(byokManager.getModel());
        modelInput.setBackgroundResource(R.drawable.composer_background);
        modelInput.setPadding(dp(14),0,dp(14),0);
        modelInput.setTextSize(15f); modelInput.setSingleLine(true);
        layout.addView(modelInput,new LinearLayout.LayoutParams(-1,dp(52)));
        TextView modelHelp = new TextView(this);
        modelHelp.setText("Use the exact model ID from your provider. The provider name (for example, google) is not a model ID.");
        modelHelp.setTextSize(12f); modelHelp.setTextColor(0xFF73777D); modelHelp.setPadding(0, dp(8), 0, dp(16));
        layout.addView(modelHelp);
        addDivider(layout, 4);

        String curProv = byokManager.getProvider();
        if (OceanByokManager.PROVIDER_ANTHROPIC.equals(curProv)) providerSpinner.setSelection(1);
        else if (OceanByokManager.PROVIDER_OPENAI.equals(curProv)) providerSpinner.setSelection(2);
        else if (OceanByokManager.PROVIDER_CUSTOM.equals(curProv)) providerSpinner.setSelection(3);
        else providerSpinner.setSelection(0);

        TextView kLabel = new TextView(this);
        kLabel.setText("API KEY");
        kLabel.setTextSize(12f);
        kLabel.setTypeface(null, Typeface.BOLD);
        kLabel.setTextColor(0xFF191817);
        kLabel.setPadding(0, dp(20), 0, dp(8));
        layout.addView(kLabel);

        EditText keyInput = new EditText(this);
        keyInput.setHint("AIzaSy... / sk-ant-... / sk-...");
        keyInput.setText(byokManager.getApiKey());
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setBackgroundResource(R.drawable.composer_background);
        keyInput.setPadding(dp(14),0,dp(14),0);
        keyInput.setTextSize(14f);
        keyInput.setTextSize(15f); keyInput.setSingleLine(true);
        layout.addView(keyInput,new LinearLayout.LayoutParams(-1,dp(52)));

        TextView bLabel = new TextView(this);
        bLabel.setText("API BASE URL");
        bLabel.setTextSize(12f);
        bLabel.setTypeface(null, Typeface.BOLD);
        bLabel.setTextColor(0xFF191817);
        bLabel.setPadding(0, dp(20), 0, dp(8));
        layout.addView(bLabel);

        EditText urlInput = new EditText(this);
        urlInput.setText(byokManager.getBaseUrl());
        urlInput.setBackgroundResource(R.drawable.composer_background);
        urlInput.setPadding(dp(14),0,dp(14),0);
        urlInput.setTextSize(14f);
        urlInput.setTextSize(15f); urlInput.setSingleLine(true);
        layout.addView(urlInput,new LinearLayout.LayoutParams(-1,dp(52)));
        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                modelInput.setHint(position == 0 ? "gemini-2.5-flash" : position == 1 ? "claude model ID" : "Provider model ID");
                String old=urlInput.getText().toString();
                if(old.isEmpty()||old.contains("generativelanguage.googleapis.com")||old.contains("api.anthropic.com")||old.contains("api.openai.com")){
                    if(position==0)urlInput.setText("https://generativelanguage.googleapis.com");
                    else if(position==1)urlInput.setText("https://api.anthropic.com/v1");
                    else if(position==2)urlInput.setText("https://api.openai.com/v1");
                    else urlInput.setText("");
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });

        TextView connectionStatus = new TextView(this);
        connectionStatus.setText(byokManager.isVerified()?"Connected · last configuration verified":"Not connected");
        connectionStatus.setTextColor(byokManager.isVerified()?0xFF18794E:0xFF7B7873);
        connectionStatus.setTextSize(13f);
        connectionStatus.setPadding(0,dp(20),0,0);
        layout.addView(connectionStatus);

        Button saveBtn = new Button(this);
        saveBtn.setText("Save & Test Connection");
        saveBtn.setAllCaps(false);
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setBackgroundColor(0xFF191817);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)(48 * getResources().getDisplayMetrics().density));
        btnParams.topMargin = (int)(28 * getResources().getDisplayMetrics().density);
        layout.addView(saveBtn, btnParams);

        saveBtn.setOnClickListener(v -> {
            int pPos = providerSpinner.getSelectedItemPosition();
            String prov = pPos == 0 ? OceanByokManager.PROVIDER_GOOGLE :
                          pPos == 1 ? OceanByokManager.PROVIDER_ANTHROPIC :
                          pPos == 2 ? OceanByokManager.PROVIDER_OPENAI : OceanByokManager.PROVIDER_CUSTOM;
            String mod = modelInput.getText().toString().trim();
            String key = keyInput.getText().toString().trim();
            String burl = urlInput.getText().toString().trim();
            if (mod.isEmpty()) {
                connectionStatus.setText(getString(R.string.provider_model_required));
                connectionStatus.setTextColor(0xFFB3261E);
                return;
            }
            if (key.isEmpty()) {
                connectionStatus.setText(getString(R.string.provider_key_required));
                connectionStatus.setTextColor(0xFFB3261E);
                return;
            }

            try { byokManager.saveConfig(prov,mod,key,burl); }
            catch(Exception error){ connectionStatus.setText(error.getMessage());connectionStatus.setTextColor(0xFFB3261E);return; }
            saveBtn.setEnabled(false); providerSpinner.setEnabled(false); modelInput.setEnabled(false); keyInput.setEnabled(false); urlInput.setEnabled(false);
            connectionStatus.setText("Testing connection…");
            agentRunner.testConnection(new OceanAgentRunner.ConnectionCallback(){
                @Override public void onSuccess(){runOnUiThread(()->{saveBtn.setEnabled(true);providerSpinner.setEnabled(true);modelInput.setEnabled(true);keyInput.setEnabled(true);urlInput.setEnabled(true);modelInput.setText(byokManager.getModel());connectionStatus.setText("Connected · authenticated request succeeded");connectionStatus.setTextColor(0xFF18794E);((TextView)findViewById(R.id.model_button)).setText(byokManager.getModel());updateTopModelChip();});}
                @Override public void onFailure(String error){runOnUiThread(()->{saveBtn.setEnabled(true);providerSpinner.setEnabled(true);modelInput.setEnabled(true);keyInput.setEnabled(true);urlInput.setEnabled(true);connectionStatus.setText("Not connected · "+error);connectionStatus.setTextColor(0xFFB3261E);});}
            });
        });

        scroll.addView(layout);
        contentFrame.addView(scroll);
    }

    private void setStarterPrompt(String text) {
        EditText prompt=findViewById(R.id.prompt);
        if(prompt==null)return;
        prompt.setText(text);
        prompt.setSelection(prompt.length());
        prompt.requestFocus();
    }

    private void showSlashOptions(String input) {
        LinearLayout menu = findViewById(R.id.slash_menu);
        LinearLayout options = findViewById(R.id.slash_options);
        if (menu == null || options == null) return;
        String query = input.trim().toLowerCase(java.util.Locale.ROOT);
        if (!query.startsWith("/") || query.contains(" ")) {
            menu.setVisibility(View.GONE);
            return;
        }
        options.removeAllViews();
        java.util.ArrayList<String[]> commands = new java.util.ArrayList<>();
        for (String[] command : SLASH_COMMANDS) commands.add(command);
        try {
            JSONArray skills = new OceanAgentHubStore(this).skills();
            for (int i = 0; i < skills.length(); i++) {
                JSONObject s = skills.optJSONObject(i);
                if (s == null) continue;
                String id = s.optString("id");
                commands.add(new String[]{"/" + id, s.optString("description", s.optString("title"))});
            }
        } catch (Exception ignored) {}
        String lastSection = "";
        for (String[] command : commands) {
            if (!command[0].startsWith(query)) continue;
            boolean skillEntry = new OceanAgentHubStore(this).findSkillBySlash(command[0]) != null;
            String section = command[0].equals("/run") || command[0].equals("/terminal") ? "Actions"
                    : skillEntry ? "Skills" : "Commands";
            if (!section.equals(lastSection)) {
                lastSection = section;
                TextView header = new TextView(this);
                header.setText(section);
                header.setTextSize(11f);
                header.setTypeface(null, Typeface.BOLD);
                header.setTextColor(0xFF9CA3AF);
                header.setPadding(dp(16), dp(10), dp(12), dp(4));
                options.addView(header);
            }
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setMinimumHeight(dp(48));
            item.setPadding(dp(16), dp(8), dp(12), dp(8));
            item.setBackgroundResource(R.drawable.nav_item_background);
            TextView title = new TextView(this);
            title.setText(command[0]);
            title.setTextSize(14f);
            title.setTypeface(null, Typeface.BOLD);
            title.setTextColor(0xFF111111);
            TextView desc = new TextView(this);
            desc.setText(command[1]);
            desc.setTextSize(12f);
            desc.setTextColor(0xFF6B7280);
            item.addView(title);
            item.addView(desc);
            item.setOnClickListener(view -> {
                menu.setVisibility(View.GONE);
                executeSlashCommand(command[0]);
                if (!command[0].equals("/run")) {
                    EditText prompt = findViewById(R.id.prompt);
                    if (prompt != null && prompt.getText().toString().trim().startsWith("/")) prompt.setText("");
                }
            });
            options.addView(item);
        }
        menu.setVisibility(options.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        if (menu.getVisibility() == View.VISIBLE) menu.animate().alpha(1f).translationY(0f).setDuration(160).start();
    }

    private boolean executeSlashCommand(String input) {
        String[] split = input.trim().split("\\s+", 2);
        String command = split[0].toLowerCase(java.util.Locale.ROOT);
        switch (command) {
            case "/run":
                if (split.length == 1 || split[1].trim().isEmpty()) {
                    setStarterPrompt("$ ");
                } else {
                    setStarterPrompt("$ " + split[1].trim());
                    submitAgentPrompt();
                }
                return true;
            case "/terminal":
                startActivity(new Intent(this, studio.ocean.app.terminal.OceanTerminalActivity.class));
                return true;
            case "/new": newChat(); return true;
            case "/providers":
                startActivity(new Intent(this, studio.ocean.app.providers.ProvidersConnectActivity.class));
                return true;
            case "/local":
                startActivity(new Intent(this, studio.ocean.app.models.local.LocalModelsActivity.class));
                return true;
            case "/browser":
                startActivity(new Intent(this, studio.ocean.app.browser.secure.SecureBrowserActivity.class));
                return true;
            case "/model": showModelHubChooser(); return true;
            case "/settings": openAgentControls("model"); return true;
            case "/skills": openAgentControls("skills"); return true;
            case "/mcp": startActivity(new Intent(this, PluginCenterActivity.class).putExtra("hub_section", "mcps")); return true;
            case "/function": openAgentControls("functions"); return true;
            case "/forge": startActivity(new Intent(this, OceanForgeActivity.class)); return true;
            case "/plugins": startActivity(new Intent(this, PluginCenterActivity.class)); return true;
            case "/screen": setStarterPrompt("Inspect my current screen and "); return true;
            case "/files": setStarterPrompt("Work with my files to "); return true;
            case "/mode":
                LinearLayout modeList = new LinearLayout(this);
                modeList.setOrientation(LinearLayout.VERTICAL);
                String[] modes = {"Cost efficient · shorter runs", "Work efficient · full capacity"};
                String[] modeKeys = {"cost", "work"};
                Dialog[] modeDialog = new Dialog[1];
                for (int mi = 0; mi < modes.length; mi++) {
                    final String k = modeKeys[mi];
                    TextView mItem = new TextView(this);
                    mItem.setText(modes[mi]);
                    mItem.setTextSize(13f);
                    mItem.setTextColor(getColor(R.color.ocean_ink));
                    mItem.setBackgroundResource(R.drawable.settings_row_background);
                    mItem.setPadding(dp(12), dp(12), dp(12), dp(12));
                    LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    mlp.bottomMargin = dp(6);
                    mItem.setLayoutParams(mlp);
                    mItem.setOnClickListener(v -> {
                        if (modeDialog[0] != null) modeDialog[0].dismiss();
                        changeAgentMode(k);
                    });
                    modeList.addView(mItem);
                }
                modeDialog[0] = OceanModal.create(this)
                        .setTitle("Agent Mode")
                        .setExplanation("Select agent resource optimization strategy.")
                        .setCustomView(modeList)
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            case "/cost": changeAgentMode("cost"); return true;
            case "/work": changeAgentMode("work"); return true;
            default:
                JSONObject skill = new OceanAgentHubStore(this).findSkillBySlash(command);
                if (skill != null) {
                    new OceanAgentHubStore(this).setSkillConnected(skill.optString("id"), true);
                    setStarterPrompt("Apply the " + skill.optString("title") + " skill to ");
                    openAgentControls("skills");
                    return true;
                }
                return false;
        }
    }

    private void changeAgentMode(String mode) {
        new OceanAgentSettings(this).setAgentMode(mode);
        Toast.makeText(this, "Agent mode: " + ("cost".equals(mode) ? "Cost efficient" : "Work efficient"),
                Toast.LENGTH_SHORT).show();
    }

    private void bindQuickDestination(String title) {
        Toast.makeText(this, title + " · choose what Ocean should use", Toast.LENGTH_SHORT).show();
        setStarterPrompt("Use my " + title.toLowerCase(java.util.Locale.ROOT) + " to ");
    }

    private void submitAgentPrompt() {
        EditText promptInput = findViewById(R.id.prompt);
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        if (prompt.startsWith("/") && executeSlashCommand(prompt)) {
            promptInput.setText("");
            return;
        }
        CrashSurvival.begin();
        recordRecentChat(prompt);
        promptInput.setText("");

        findViewById(R.id.home_content).setVisibility(View.GONE);
        View byok = contentFrame != null ? contentFrame.findViewWithTag("BYOK_VIEW") : null;
        if (byok != null) contentFrame.removeView(byok);
        if (chatScrollView != null) chatScrollView.setVisibility(View.VISIBLE);

        addUserMessageCard(prompt);

        LinearLayout agentCard = createAgentResponseCard();
        chatMessagesLayout.addView(agentCard);

        OceanShimmerTextView thoughtView = agentCard.findViewWithTag("THOUGHT_VIEW");
        LinearLayout toolBox = agentCard.findViewWithTag("TOOL_BOX");
        OceanToolCard[] currentTool = {null};
        TextView responseView = agentCard.findViewWithTag("RESPONSE_VIEW");

        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));

        CrashSurvival.mark("CHAT_UI_READY");
        setAgentBusy(true);
        agentRunner.processPrompt(prompt, new OceanAgentRunner.AgentCallback() {
            @Override public void onThought(String thought) {
                CrashSurvival.mark("RENDER_AGENT_STATUS");
                thoughtView.setVisibility(View.VISIBLE);
                thoughtView.setText(thought);
                thoughtView.setShimmering(true);
            }

            @Override public void onToolStart(String toolName, String command) {
                CrashSurvival.mark("RENDER_TOOL_CARD");
                thoughtView.setText("Running " + toolName.toLowerCase(java.util.Locale.ROOT) + "…");
                thoughtView.setShimmering(true);
                toolBox.setVisibility(View.VISIBLE);
                currentTool[0] = new OceanToolCard(MainActivity.this, toolName, command);
                toolBox.addView(currentTool[0]);
            }
            @Override public void onToolOutput(String chunk) {
                if (currentTool[0] != null) currentTool[0].append(chunk);
            }
            @Override public void onToolComplete(int exitCode) {
                if (currentTool[0] != null) currentTool[0].complete(exitCode);
            }

            @Override public void onResponse(String response) {
                CrashSurvival.mark("RENDER_AGENT_RESPONSE");
                responseView.setVisibility(View.VISIBLE);
                String safeResponse = response == null ? "Agent completed without a text response." : response;
                try {
                    responseView.setText(OceanMessageText.render(safeResponse));
                } catch (Throwable t) {
                    responseView.setText(safeResponse);
                }
                thoughtView.setVisibility(View.GONE);
                thoughtView.setShimmering(false);
                setAgentBusy(false);
                CrashSurvival.finished();
                chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }

            @Override public void onError(String error) {
                CrashSurvival.mark("RENDER_AGENT_ERROR");
                responseView.setVisibility(View.VISIBLE);
                responseView.setText(error);
                thoughtView.setVisibility(View.GONE);
                thoughtView.setShimmering(false);
                setAgentBusy(false);
                CrashSurvival.finished();
                responseView.setTextColor(0xFFDC2626);
            }
        });
    }

    private void addUserMessageCard(String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.END);
        int pad = (int)(12 * getResources().getDisplayMetrics().density);
        row.setPadding(0, pad, 0, pad);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(getColor(R.color.ocean_user_bubble_text));
        bubble.setTextSize(15f);
        bubble.setBackgroundResource(R.drawable.chat_bubble_user);
        int bubblePadH = (int)(14 * getResources().getDisplayMetrics().density);
        int bubblePadV = (int)(10 * getResources().getDisplayMetrics().density);
        bubble.setPadding(bubblePadH, bubblePadV, bubblePadH, bubblePadV);
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bubbleLp.setMarginStart((int)(48 * getResources().getDisplayMetrics().density));
        row.addView(bubble, bubbleLp);

        chatMessagesLayout.addView(row);
    }

    private LinearLayout createAgentResponseCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFFFFFFF);
        int pad = (int)(14 * getResources().getDisplayMetrics().density);
        card.setPadding(pad, pad, pad, pad);

        TextView head = new TextView(this);
        head.setText("Ocean Agent");
        head.setTextSize(13f);
        head.setTypeface(null, Typeface.BOLD);
        head.setTextColor(0xFF62666D);
        head.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_agent), null, null, null);
        head.setCompoundDrawablePadding(dp(10));
        card.addView(head);
        addDivider(card, 12);

        OceanShimmerTextView thought = new OceanShimmerTextView(this);
        thought.setTag("THOUGHT_VIEW");
        thought.setTextColor(0xFF6B7280);
        thought.setTextSize(14f);
        thought.setPadding(0, 10, 0, 10);
        thought.setVisibility(View.GONE);
        card.addView(thought);

        LinearLayout toolBox = new LinearLayout(this);
        toolBox.setTag("TOOL_BOX");
        toolBox.setOrientation(LinearLayout.VERTICAL);
        toolBox.setPadding(0, 0, 0, dp(8));
        toolBox.setVisibility(View.GONE);

        card.addView(toolBox);

        TextView response = new TextView(this);
        response.setTag("RESPONSE_VIEW");
        response.setTextColor(0xFF191817);
        response.setTextSize(15f);
        response.setTextIsSelectable(true);
        response.setLineSpacing(dp(3), 1f);
        response.setPadding(0, 10, 0, 0);
        response.setVisibility(View.GONE);
        card.addView(response);

        return card;
    }

    private void bindGroup(int parentId,int childId,int chevronId) { findViewById(parentId).setOnClickListener(v -> animateAccordion(findViewById(childId),findViewById(chevronId))); }
    private void animateAccordion(View children,View chevron) {
        boolean expand=children.getVisibility()!=View.VISIBLE; int start=expand?0:children.getHeight();
        if(expand){children.setVisibility(View.VISIBLE); children.measure(View.MeasureSpec.makeMeasureSpec(((View)children.getParent()).getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));}
        int end=expand?children.getMeasuredHeight():0; children.setAlpha(expand?0f:1f); ValueAnimator height=ValueAnimator.ofInt(start,end); height.setDuration(220); height.setInterpolator(new DecelerateInterpolator()); height.addUpdateListener(a -> {ViewGroup.LayoutParams p=children.getLayoutParams();p.height=(int)a.getAnimatedValue();children.setLayoutParams(p);children.setAlpha(expand?a.getAnimatedFraction():1f-a.getAnimatedFraction());}); height.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(!expand)children.setVisibility(View.GONE);ViewGroup.LayoutParams p=children.getLayoutParams();p.height=expand?ViewGroup.LayoutParams.WRAP_CONTENT:0;children.setLayoutParams(p);}}); height.start(); chevron.animate().rotation(expand?90f:0f).setDuration(200).start();
    }
    private void bindDestination(int id,String title) { findViewById(id).setOnClickListener(v -> { closeDrawer(); View content=findViewById(R.id.home_content); content.animate().alpha(0f).translationY(6f).setDuration(120).withEndAction(() -> { TextView heading=findViewById(R.id.empty_title), message=findViewById(R.id.empty_message); TextView st=findViewById(R.id.screen_title); if (st != null) st.setText(title); if(title.equals("OceanStudio")){ heading.setText(R.string.build_question); message.setText(R.string.build_subtitle); } else { heading.setText(title); message.setText(getString(R.string.destination_unavailable,title)); } content.setTranslationY(6f); content.animate().alpha(1f).translationY(0f).setDuration(190).start(); }).start(); }); }
    private void newChat() {
        if (agentRunner.isRunning()) { Toast.makeText(this, "Stop the current request before starting a new chat", Toast.LENGTH_SHORT).show(); return; }
        if (drawerOpen) closeDrawer();
        agentRunner.resetConversation();
        if (chatMessagesLayout != null) chatMessagesLayout.removeAllViews();
        if (chatScrollView != null) chatScrollView.setVisibility(View.GONE);
        if (contentFrame != null) { View byok = contentFrame.findViewWithTag("BYOK_VIEW"); if (byok != null) contentFrame.removeView(byok); }
        findViewById(R.id.home_content).setVisibility(View.VISIBLE);
        ((EditText)findViewById(R.id.prompt)).setText("");
        TextView st = findViewById(R.id.screen_title);
        if (st != null) st.setText(R.string.app_name);
        ((TextView)findViewById(R.id.empty_title)).setText(R.string.build_question);
        ((TextView)findViewById(R.id.empty_message)).setText(R.string.build_subtitle);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void selectModelPreset(String presetKey, String label) {
        selectedModelPreset = presetKey;
        updateTopModelChip();
        if (!"auto".equals(presetKey)) {
            Toast.makeText(this, label + " preset · configure API in Models", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout createHubChooserOption(int iconRes, String titleText, String subtitleText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackgroundResource(R.drawable.settings_row_background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.ocean_ink_100)));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.rightMargin = dp(14);
        row.addView(icon, iconLp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textCol.setLayoutParams(textLp);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextColor(getColor(R.color.ocean_text_primary));
        title.setTextSize(14f);
        title.setTypeface(null, Typeface.BOLD);
        textCol.addView(title);

        TextView sub = new TextView(this);
        sub.setText(subtitleText);
        sub.setTextColor(getColor(R.color.ocean_text_secondary));
        sub.setTextSize(12f);
        sub.setPadding(0, dp(2), 0, 0);
        textCol.addView(sub);

        row.addView(textCol);

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.ocean_text_tertiary)));
        LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(dp(16), dp(16));
        row.addView(chevron, chevLp);

        return row;
    }

    private void showModelHubChooser() {
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(12), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("AI Models & Providers");
        title.setTextColor(getColor(R.color.ocean_text_primary));
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        sheet.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Select how you want OceanStudio to run inference.");
        subtitle.setTextColor(getColor(R.color.ocean_text_secondary));
        subtitle.setTextSize(13f);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        sheet.addView(subtitle);

        Dialog[] dialogRef = new Dialog[1];

        LinearLayout optProviders = createHubChooserOption(
                R.drawable.ic_connections,
                "AI Providers & Direct Connect",
                "17+ providers: OpenAI, Claude, Gemini, Antigravity, Kimi, DeepSeek, CLI bridges, OAuth"
        );
        optProviders.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            startActivity(new Intent(this, studio.ocean.app.providers.ProvidersConnectActivity.class));
        });
        sheet.addView(optProviders);

        LinearLayout optLocal = createHubChooserOption(
                R.drawable.ic_models,
                "On-Device Local Models (GGUF)",
                "Run SmolLM2, Qwen2.5, Llama 3.2 offline on your device RAM"
        );
        optLocal.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            startActivity(new Intent(this, studio.ocean.app.models.local.LocalModelsActivity.class));
        });
        sheet.addView(optLocal);

        LinearLayout optByok = createHubChooserOption(
                R.drawable.ic_tools,
                "Configure BYOK / Custom Endpoint",
                "Manual API key and custom base URL endpoint configuration"
        );
        optByok.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            showByokPage();
        });
        sheet.addView(optByok);

        TextView presetsHeader = new TextView(this);
        presetsHeader.setText("MODEL PRESETS");
        presetsHeader.setTextSize(11f);
        presetsHeader.setTypeface(null, Typeface.BOLD);
        presetsHeader.setTextColor(getColor(R.color.ocean_text_tertiary));
        presetsHeader.setPadding(0, dp(12), 0, dp(4));
        sheet.addView(presetsHeader);

        String[][] presets = {
                {"auto", "Auto"},
                {"gemini", "Gemini Flash"},
                {"claude", "Claude Sonnet"},
                {"gpt", "GPT-4o"}
        };
        for (String[] preset : presets) {
            boolean on = preset[0].equals(selectedModelPreset);
            TextView row = new TextView(this);
            row.setText(preset[1] + (on ? "  ✓" : ""));
            row.setTextColor(getColor(R.color.ocean_text_primary));
            row.setTextSize(14);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackgroundResource(R.drawable.settings_row_background);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                selectModelPreset(preset[0], preset[1]);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });
            sheet.addView(row);
        }

        dialogRef[0] = OceanModal.create(this)
                .setTitle("AI Models & Inference")
                .setCustomView(sheet)
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHeaderModelPicker() {
        showModelHubChooser();
    }

    private void refreshAgentSession() {
        if (agentRunner != null && agentRunner.isRunning()) {
            Toast.makeText(this, "Stop the current request before refreshing", Toast.LENGTH_SHORT).show();
            return;
        }
        if (agentRunner != null) agentRunner.resetConversation();
        refreshAgentControlsSummary();
        Toast.makeText(this, "Agent session refreshed", Toast.LENGTH_SHORT).show();
    }

    private void updateTopModelChip() {
        TextView sub = findViewById(R.id.top_model_chip);
        TextView modelBtn = findViewById(R.id.model_button);

        // Check on-device local model first
        try {
            studio.ocean.app.models.local.LocalModel local = studio.ocean.app.models.local.LocalModelManager.getInstance(this).getConnectedModel();
            if (local != null) {
                String text = "Local · " + local.displayName;
                if (sub != null) sub.setText(text);
                if (modelBtn != null) modelBtn.setText(local.displayName);
                return;
            }
        } catch (Throwable ignored) {}

        // Check Provider Hub connections
        try {
            java.util.List<studio.ocean.app.providers.model.ProviderConnection> conns = new studio.ocean.app.providers.state.ProviderConnectionStore(this).listAll();
            if (conns != null && !conns.isEmpty()) {
                studio.ocean.app.providers.model.ProviderConnection active = conns.get(0);
                String title = active.name != null ? active.name : active.providerId;
                String modelStr = active.activeModel != null && !active.activeModel.isEmpty() ? active.activeModel : title;
                if (sub != null) sub.setText("Cloud · " + modelStr);
                if (modelBtn != null) modelBtn.setText(modelStr);
                return;
            }
        } catch (Throwable ignored) {}

        // Check legacy BYOK
        if (byokManager != null && byokManager.isVerified()) {
            String presetLabel = "auto".equals(selectedModelPreset) ? "Auto" :
                    "gemini".equals(selectedModelPreset) ? "Gemini Flash" :
                    "claude".equals(selectedModelPreset) ? "Claude Sonnet" : "GPT-4o";
            String model = byokManager.getModel();
            if (sub != null) sub.setText(presetLabel + " · " + model);
            if (modelBtn != null) modelBtn.setText(model);
            return;
        }

        // Default: Not configured
        if (sub != null) sub.setText("Auto · Connect Provider");
        if (modelBtn != null) modelBtn.setText("Providers");
    }

    private void recordRecentChat(String prompt) {
        if (prompt == null || prompt.isEmpty()) return;
        String title = prompt.length() > 48 ? prompt.substring(0, 45) + "…" : prompt;
        try {
            JSONArray arr = new JSONArray(getSharedPreferences(RECENT_PREFS, MODE_PRIVATE).getString(RECENT_KEY, "[]"));
            JSONArray next = new JSONArray();
            next.put(title);
            for (int i = 0; i < arr.length() && next.length() < 6; i++) {
                String existing = arr.optString(i, "");
                if (!title.equals(existing)) next.put(existing);
            }
            getSharedPreferences(RECENT_PREFS, MODE_PRIVATE).edit().putString(RECENT_KEY, next.toString()).apply();
            refreshRecentSessions();
        } catch (Exception ignored) {}
    }

    private void refreshRecentSessions() {
        LinearLayout list = findViewById(R.id.recent_sessions_list);
        TextView empty = findViewById(R.id.no_recent_sessions);
        if (list == null || empty == null) return;
        list.removeAllViews();
        JSONArray arr;
        try {
            arr = new JSONArray(getSharedPreferences(RECENT_PREFS, MODE_PRIVATE).getString(RECENT_KEY, "[]"));
        } catch (Exception e) {
            arr = new JSONArray();
        }
        if (arr.length() == 0) {
            list.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            empty.setAlpha(1f);
            return;
        }
        empty.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        for (int i = 0; i < arr.length(); i++) {
            String title = arr.optString(i, "");
            if (title.isEmpty()) continue;
            View row = OceanUi.conversationRowView(this, title, "Tap to reuse in composer");
            row.setOnClickListener(v -> {
                closeDrawer();
                setStarterPrompt(title);
            });
            list.addView(row);
        }
    }
    private void addDivider(LinearLayout parent, int margin) {
        View divider = new View(this); divider.setBackgroundColor(0xFFE5E7EB);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.topMargin = dp(margin); params.bottomMargin = dp(margin);
        parent.addView(divider, params);
    }
    private void setAgentBusy(boolean busy) {
        ImageButton button = findViewById(R.id.send_button);
        if (button == null) return;
        button.setImageResource(busy ? R.drawable.ic_stop : R.drawable.ic_send);
        button.setContentDescription(busy ? "Stop request" : getString(R.string.send));
    }
    @Override protected void onDestroy() {
        if (agentRunner != null) agentRunner.cancel();
        super.onDestroy();
    }

    private void openDrawer() { if(drawerOpen)return; drawerOpen=true; sidebar.setVisibility(View.VISIBLE); backdrop.setAlpha(0f); backdrop.setVisibility(View.VISIBLE); backdrop.animate().alpha(1f).setDuration(190).start(); sidebar.animate().translationX(0f).setDuration(270).setInterpolator(new DecelerateInterpolator()).start(); findViewById(R.id.main_content).animate().translationX(sidebar.getWidth()*.08f).setDuration(270).start(); }
    private void closeDrawer() { if(!drawerOpen)return; drawerOpen=false; backdrop.animate().alpha(0f).setDuration(180).withEndAction(() -> backdrop.setVisibility(View.GONE)).start(); sidebar.animate().translationX(-sidebar.getWidth()).setDuration(240).setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){sidebar.setVisibility(View.GONE); sidebar.animate().setListener(null);}}).start(); findViewById(R.id.main_content).animate().translationX(0f).setDuration(240).start(); }

    private void openAgentSearch() {
        final EditText query = new EditText(this);
        query.setHint("Search tools and past conversations");
        query.setSingleLine(true);
        query.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_search, 0, 0, 0);
        query.setCompoundDrawablePadding(dp(10));
        query.setPadding(dp(16), 0, dp(16), 0);
        query.setBackgroundResource(R.drawable.composer_background);
        OceanModal.create(this)
                .setTitle("Search OceanStudio")
                .setExplanation("Search tools, files, and conversation history.")
                .setCustomView(query)
                .setPositiveButton("Search", v -> {
                    String term = query.getText().toString().trim();
                    if (!term.isEmpty()) Toast.makeText(this, "Search ready · " + term, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openAgentControls() {
        openAgentControls("model");
    }

    private void openAgentControls(String tab) {
        if (agentControlsOpen) {
            if (agentControlsPanel != null && tab != null) agentControlsPanel.showTab(tab);
            return;
        }
        if (drawerOpen) closeDrawer();
        agentControlsOpen=true;
        refreshAgentControlsSummary();
        if (agentControlsPanel != null && tab != null) agentControlsPanel.showTab(tab);
        agentControlsDrawer.setVisibility(View.VISIBLE);
        backdrop.setAlpha(0f); backdrop.setVisibility(View.VISIBLE); backdrop.animate().alpha(1f).setDuration(160).start();
        agentControlsDrawer.post(() -> {
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int width = Math.min((int)(screenWidth * 0.86f), dp(400));
            ViewGroup.LayoutParams p = agentControlsDrawer.getLayoutParams();
            p.width = width;
            agentControlsDrawer.setLayoutParams(p);
            agentControlsDrawer.setTranslationX(width);
            agentControlsDrawer.animate().translationX(0f).setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
        });
    }

    private void closeAgentControls() {
        if(!agentControlsOpen)return;
        agentControlsOpen=false;
        backdrop.animate().alpha(0f).setDuration(160).withEndAction(() -> { if(!drawerOpen) backdrop.setVisibility(View.GONE); }).start();
        agentControlsDrawer.animate().translationX(agentControlsDrawer.getWidth()).setDuration(220).setInterpolator(new DecelerateInterpolator())
                .setListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){agentControlsDrawer.setVisibility(View.GONE);agentControlsDrawer.animate().setListener(null);}}).start();
    }

    private void refreshAgentControlsSummary() {
        if (agentControlsPanel != null) agentControlsPanel.refreshSummary();
    }

    private void showModels(View anchor) {
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(18,16,18,16); content.setBackgroundResource(R.drawable.composer_background);
        for(int i=0;i<3;i++){View row=new View(this); row.setBackgroundResource(R.drawable.skeleton_background); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,(int)(28*getResources().getDisplayMetrics().density)); if(i>0)p.topMargin=(int)(8*getResources().getDisplayMetrics().density); content.addView(row,p);}
        PopupWindow popup=new PopupWindow(content,(int)(270*getResources().getDisplayMetrics().density),ViewGroup.LayoutParams.WRAP_CONTENT,true); popup.setBackgroundDrawable(getDrawable(R.drawable.composer_background)); popup.setElevation(7f); popup.setOutsideTouchable(true); popup.showAsDropDown(anchor,0,-(int)(150*getResources().getDisplayMetrics().density),Gravity.START); content.setAlpha(0f); content.setTranslationY(7f); content.animate().alpha(1f).translationY(0f).setDuration(180).start();
        content.post(() -> { if(!popup.isShowing())return; content.animate().alpha(0f).setDuration(100).withEndAction(() -> { content.removeAllViews(); TextView title=new TextView(this); title.setText(R.string.no_models); title.setTextColor(getColor(R.color.ocean_ink)); title.setTextSize(15); title.setTypeface(null, Typeface.BOLD); TextView detail=new TextView(this); detail.setText(R.string.connect_models); detail.setTextColor(getColor(R.color.ocean_muted)); detail.setTextSize(13); detail.setPadding(0,8,0,0); content.addView(title); content.addView(detail); content.animate().alpha(1f).setDuration(150).start(); }).start(); });
    }
}
