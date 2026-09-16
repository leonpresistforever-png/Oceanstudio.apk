package studio.ocean.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.util.Patterns;
import android.text.SpannableString;
import android.text.Spanned;
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
import androidx.appcompat.app.AlertDialog;

/** Native auth and chat-first workspace. No WebView or browser bridge is involved. */
public class MainActivity extends AppCompatActivity {
    private enum AuthMode { SIGN_IN, SIGN_UP, FORGOT }
    private enum AuthState { LOADING, CONFIGURED_LOGGED_OUT, CONFIGURED_LOGGED_IN, DEV_BYPASS_LOGGED_IN, CONFIGURATION_MISSING, ERROR }
    private static final String PREFS="ocean_auth", TOKEN="id_token", EMAIL="email";
    private final AuthClient authClient = new AuthClient();
    private AuthMode authMode = AuthMode.SIGN_IN;
    private AuthState authState = AuthState.LOADING;
    private boolean developmentSession;
    private LinearLayout sidebar; private View backdrop; private boolean drawerOpen;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private int loadingGeneration;
    private OceanByokManager byokManager;
    private OceanAgentRunner agentRunner;
    private FrameLayout contentFrame;
    private ScrollView chatScrollView;
    private LinearLayout chatMessagesLayout;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        studio.ocean.app.terminal.PreviousProcessExit.capture(this);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { if (drawerOpen) closeDrawer(); else finish(); }});
        showLoading(() -> {
            byokManager = new OceanByokManager(this);
        agentRunner = new OceanAgentRunner(this);
        if (getSharedPreferences(PREFS,MODE_PRIVATE).contains(TOKEN)) { authState=AuthState.CONFIGURED_LOGGED_IN; showMain(); }
            else { authState=authClient.configured()?AuthState.CONFIGURED_LOGGED_OUT:AuthState.CONFIGURATION_MISSING; showAuth(); }
        });
    }

    private void showLoading(Runnable destination) {
        final int generation=++loadingGeneration;
        setContentView(R.layout.activity_loading);
        View logo=findViewById(R.id.loading_logo), wordmark=findViewById(R.id.loading_wordmark);
        logo.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
        wordmark.animate().alpha(1f).setStartDelay(90).setDuration(260).start();
        int[] dots={R.id.loading_dot_one,R.id.loading_dot_two,R.id.loading_dot_three,R.id.loading_dot_four};
        Runnable pulse=new Runnable(){int index; public void run(){if(generation!=loadingGeneration)return; for(int i=0;i<dots.length;i++)findViewById(dots[i]).animate().alpha(i==index?0.9f:0.28f).scaleX(i==index?1.25f:1f).scaleY(i==index?1.25f:1f).setDuration(140).start(); index=(index+1)%dots.length; uiHandler.postDelayed(this,170);}};
        uiHandler.post(pulse);
        uiHandler.postDelayed(() -> { if(generation!=loadingGeneration)return; logo.animate().alpha(0f).translationY(-4f).setDuration(180).start(); wordmark.animate().alpha(0f).setDuration(160).withEndAction(() -> { loadingGeneration++; destination.run(); }).start(); },550);
    }

    private void showAuth() {
        setContentView(R.layout.activity_auth);
        findViewById(R.id.auth_primary).setOnClickListener(v -> submitAuth());
        findViewById(R.id.forgot_password).setOnClickListener(v -> setAuthMode(AuthMode.FORGOT));
        findViewById(R.id.auth_switch).setOnClickListener(v -> setAuthMode(authMode == AuthMode.SIGN_IN ? AuthMode.SIGN_UP : AuthMode.SIGN_IN));
        findViewById(R.id.google_auth).setOnClickListener(v -> Toast.makeText(this,getString(R.string.oauth_not_configured,"Google"),Toast.LENGTH_SHORT).show());
        findViewById(R.id.github_auth).setOnClickListener(v -> Toast.makeText(this,getString(R.string.oauth_not_configured,"GitHub"),Toast.LENGTH_SHORT).show());
        findViewById(R.id.dev_auth_indicator).setVisibility(devBypassAvailable()?View.VISIBLE:View.GONE);
        findViewById(R.id.dev_auth_continue).setVisibility(devBypassAvailable()?View.VISIBLE:View.GONE);
        findViewById(R.id.dev_auth_continue).setOnClickListener(v -> startDevelopmentSession());
        setAuthMode(AuthMode.SIGN_IN);
    }

    private void setAuthMode(AuthMode mode) {
        authMode=mode; TextView title=findViewById(R.id.auth_title), subtitle=findViewById(R.id.auth_subtitle), toggle=findViewById(R.id.auth_switch), forgot=findViewById(R.id.forgot_password); Button primary=findViewById(R.id.auth_primary); View password=findViewById(R.id.auth_password), passwordLabel=findViewById(R.id.password_label), confirm=findViewById(R.id.auth_confirm), confirmLabel=findViewById(R.id.confirm_label), social=findViewById(R.id.social_buttons), divider=findViewById(R.id.auth_divider);
        boolean signup=mode==AuthMode.SIGN_UP, reset=mode==AuthMode.FORGOT;
        title.setText(reset?R.string.reset_password:signup?R.string.create_account:R.string.welcome_back); subtitle.setText(reset?R.string.reset_subtitle:signup?R.string.signup_subtitle:R.string.auth_subtitle); primary.setText(reset?R.string.send_reset:signup?R.string.sign_up:R.string.sign_in); toggle.setText(reset?R.string.back_to_signin:signup?R.string.have_account_signin:R.string.no_account_signup);
        password.setVisibility(reset?View.GONE:View.VISIBLE); passwordLabel.setVisibility(reset?View.GONE:View.VISIBLE); confirm.setVisibility(signup?View.VISIBLE:View.GONE); confirmLabel.setVisibility(signup?View.VISIBLE:View.GONE); forgot.setVisibility(mode==AuthMode.SIGN_IN?View.VISIBLE:View.GONE); social.setVisibility(reset?View.GONE:View.VISIBLE); divider.setVisibility(reset?View.GONE:View.VISIBLE); findViewById(R.id.auth_status).setVisibility(View.GONE);
        String footer=getString(reset?R.string.back_to_signin:signup?R.string.have_account_signin:R.string.no_account_signup); String action=reset?footer:(signup?"Sign in":"Sign up"); SpannableString footerText=new SpannableString(footer); int actionStart=footer.lastIndexOf(action); footerText.setSpan(new UnderlineSpan(),actionStart,footer.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); toggle.setText(footerText);
        findViewById(R.id.progress_one).setBackgroundResource(R.drawable.progress_active); findViewById(R.id.progress_two).setBackgroundResource(signup?R.drawable.progress_active:R.drawable.progress_inactive); findViewById(R.id.progress_three).setBackgroundResource(R.drawable.progress_inactive);
        title.setAlpha(0f); title.setTranslationY(10f); title.animate().alpha(1f).translationY(0f).setDuration(240).start();
    }

    private void submitAuth() {
        EditText emailView=findViewById(R.id.auth_email), passwordView=findViewById(R.id.auth_password), confirmView=findViewById(R.id.auth_confirm); String email=emailView.getText().toString().trim(), password=passwordView.getText().toString();
        if (!authClient.configured() && devBypassAvailable() && authMode==AuthMode.SIGN_IN && !email.isEmpty() && !password.isEmpty()) { startDevelopmentSession(); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { showAuthStatus(getString(R.string.invalid_email),false); return; }
        if (!authClient.configured()) { authState=AuthState.CONFIGURATION_MISSING; showAuthStatus(getString(R.string.auth_unavailable),false); return; }
        if (authMode==AuthMode.SIGN_UP && !password.equals(confirmView.getText().toString())) { showAuthStatus(getString(R.string.password_mismatch),false); return; }
        if (authMode!=AuthMode.FORGOT && password.length()<6) { showAuthStatus(getString(R.string.password_short),false); return; }
        setAuthBusy(true);
        AuthClient.Callback callback=result -> runOnUiThread(() -> { setAuthBusy(false); if (!result.success) { authState=AuthState.ERROR; showAuthStatus(result.message==null?getString(R.string.request_failed):result.message,false); return; } if (authMode==AuthMode.FORGOT) { showAuthStatus(getString(R.string.reset_sent),true); return; } authState=AuthState.CONFIGURED_LOGGED_IN; getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(TOKEN,result.token).putString(EMAIL,result.email).apply(); showLoading(this::showMain); });
        if (authMode==AuthMode.FORGOT) authClient.reset(email,callback); else authClient.signIn(email,password,authMode==AuthMode.SIGN_UP,callback);
    }

    private void setAuthBusy(boolean busy) { findViewById(R.id.auth_primary).setEnabled(!busy); findViewById(R.id.google_auth).setEnabled(!busy); findViewById(R.id.github_auth).setEnabled(!busy); }
    private void showAuthStatus(String message, boolean success) { TextView status=findViewById(R.id.auth_status); status.setText(message); status.setTextColor(getColor(success?R.color.ocean_ink:R.color.ocean_error)); status.setVisibility(View.VISIBLE); }
    private boolean devBypassAvailable() { return BuildConfig.DEBUG && BuildConfig.OCEAN_DEV_AUTH_BYPASS; }
    private void startDevelopmentSession() { if (!devBypassAvailable()) return; developmentSession=true; authState=AuthState.DEV_BYPASS_LOGGED_IN; showLoading(this::showMain); }

    private void showMain() {
        setContentView(R.layout.activity_main); sidebar=findViewById(R.id.sidebar); backdrop=findViewById(R.id.drawer_backdrop);
        findViewById(R.id.menu_button).setOnClickListener(v -> openDrawer()); backdrop.setOnClickListener(v -> closeDrawer()); findViewById(R.id.new_chat_button).setOnClickListener(v -> newChat()); findViewById(R.id.sidebar_new_chat).setOnClickListener(v -> newChat());
        
        TextView modelBtn = findViewById(R.id.model_button);
        if (byokManager != null) modelBtn.setText(byokManager.isVerified()?byokManager.getModel():"Configure model");
        modelBtn.setOnClickListener(v -> showByokPage());
        findViewById(R.id.send_button).setOnClickListener(v -> { if (agentRunner.isRunning()) agentRunner.cancel(); else submitAgentPrompt(); });

        bindGroup(R.id.group_workspace,R.id.workspace_children,R.id.chevron_workspace); bindGroup(R.id.group_agents,R.id.agents_children,R.id.chevron_agents); bindGroup(R.id.group_tools,R.id.tools_children,R.id.chevron_tools); bindGroup(R.id.group_connections,R.id.connections_children,R.id.chevron_connections);
        bindDestination(R.id.nav_agent,"OceanStudio"); bindDestination(R.id.nav_editor,"Editor"); bindDestination(R.id.nav_files,"Files"); bindDestination(R.id.nav_preview,"Preview");
        findViewById(R.id.nav_terminal).setOnClickListener(v -> { closeDrawer(); startActivity(new Intent(this, studio.ocean.app.terminal.OceanTerminalActivity.class)); });
        
        // Add BYOK Models link into sidebar Tools children
        LinearLayout toolsChildren = findViewById(R.id.tools_children);
        if (toolsChildren != null) {
            TextView byokNav = new TextView(this);
            byokNav.setText("BYOK Models & APIs");
            byokNav.setTextColor(getColor(R.color.ocean_ink));
            byokNav.setTextSize(14f);
            byokNav.setPadding((int)(16 * getResources().getDisplayMetrics().density), (int)(10 * getResources().getDisplayMetrics().density), (int)(16 * getResources().getDisplayMetrics().density), (int)(10 * getResources().getDisplayMetrics().density));
            byokNav.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_models), null, null, null);
            byokNav.setCompoundDrawablePadding((int)(12 * getResources().getDisplayMetrics().density));
            byokNav.setOnClickListener(v -> { closeDrawer(); showByokPage(); });
            toolsChildren.addView(byokNav, 0);
        }

        // Also bind Models and Providers under Connections to showByokPage
        LinearLayout connectionsChildren = findViewById(R.id.connections_children);
        if (connectionsChildren != null) {
            for (int i = 0; i < connectionsChildren.getChildCount(); i++) {
                View child = connectionsChildren.getChildAt(i);
                if (child instanceof TextView) {
                    child.setOnClickListener(v -> { closeDrawer(); showByokPage(); });
                }
            }
        }

        findViewById(R.id.sign_out).setOnClickListener(v -> { developmentSession=false; authState=authClient.configured()?AuthState.CONFIGURED_LOGGED_OUT:AuthState.CONFIGURATION_MISSING; getSharedPreferences(PREFS,MODE_PRIVATE).edit().clear().apply(); showAuth(); });
        sidebar.post(() -> { int width=Math.min((int)(getResources().getDisplayMetrics().widthPixels*.76f),(int)(360*getResources().getDisplayMetrics().density)); ViewGroup.LayoutParams p=sidebar.getLayoutParams(); p.width=width; sidebar.setLayoutParams(p); sidebar.setTranslationX(-width); });
        View skeleton=findViewById(R.id.home_skeleton), content=findViewById(R.id.home_content); content.post(() -> { skeleton.animate().alpha(0f).setDuration(220).withEndAction(() -> skeleton.setVisibility(View.GONE)).start(); content.animate().alpha(1f).translationY(0f).setDuration(260).start(); });
        View recentSkeleton=findViewById(R.id.recent_skeleton), recentEmpty=findViewById(R.id.no_recent_sessions); recentSkeleton.post(() -> { recentSkeleton.animate().alpha(0f).setDuration(180).withEndAction(() -> { recentSkeleton.setVisibility(View.GONE); recentEmpty.setAlpha(0f); recentEmpty.setVisibility(View.VISIBLE); recentEmpty.animate().alpha(1f).setDuration(180).start(); }).start(); });

        initChatContainer();
    }

    private void initChatContainer() {
        View homeContent = findViewById(R.id.home_content);
        if (homeContent != null && homeContent.getParent() instanceof FrameLayout) {
            contentFrame = (FrameLayout) homeContent.getParent();
            chatScrollView = new ScrollView(this);
            chatScrollView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            chatScrollView.setBackgroundColor(0xFFFFFFFF);
            chatScrollView.setVisibility(View.GONE);

            chatMessagesLayout = new LinearLayout(this);
            chatMessagesLayout.setOrientation(LinearLayout.VERTICAL);
            int pad = (int)(16 * getResources().getDisplayMetrics().density);
            chatMessagesLayout.setPadding(pad, pad, pad, pad);
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

        ((TextView)findViewById(R.id.screen_title)).setText("BYOK Services");

        ScrollView scroll = new ScrollView(this);
        scroll.setTag("BYOK_VIEW");
        scroll.setBackgroundColor(0xFFFFFFFF);
        scroll.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("BYOK Models & Endpoints");
        title.setTextSize(20f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF191817);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Connect your model to chat and run commands in Ocean. Terminal results appear here, without opening the terminal screen.");
        sub.setTextSize(13f);
        sub.setTextColor(0xFF7B7873);
        sub.setPadding(0, 8, 0, 24);
        layout.addView(sub);

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
        layout.addView(providerSpinner);

        TextView mLabel = new TextView(this);
        mLabel.setText("ACTIVE MODEL");
        mLabel.setTextSize(12f);
        mLabel.setTypeface(null, Typeface.BOLD);
        mLabel.setTextColor(0xFF191817);
        mLabel.setPadding(0, 20, 0, 0);
        layout.addView(mLabel);

        EditText modelInput = new EditText(this);
        modelInput.setHint("Model ID, e.g. gemini-2.5-flash");
        modelInput.setSingleLine(true);
        modelInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        modelInput.setText(byokManager.getModel());
        modelInput.setBackgroundResource(R.drawable.composer_background);
        modelInput.setPadding(20,20,20,20);
        layout.addView(modelInput);
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
        kLabel.setPadding(0, 20, 0, 4);
        layout.addView(kLabel);

        EditText keyInput = new EditText(this);
        keyInput.setHint("AIzaSy... / sk-ant-... / sk-...");
        keyInput.setText(byokManager.getApiKey());
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setBackgroundResource(R.drawable.composer_background);
        keyInput.setPadding(20, 20, 20, 20);
        keyInput.setTextSize(14f);
        layout.addView(keyInput);

        TextView bLabel = new TextView(this);
        bLabel.setText("API BASE URL");
        bLabel.setTextSize(12f);
        bLabel.setTypeface(null, Typeface.BOLD);
        bLabel.setTextColor(0xFF191817);
        bLabel.setPadding(0, 20, 0, 4);
        layout.addView(bLabel);

        EditText urlInput = new EditText(this);
        urlInput.setText(byokManager.getBaseUrl());
        urlInput.setBackgroundResource(R.drawable.composer_background);
        urlInput.setPadding(20, 20, 20, 20);
        urlInput.setTextSize(14f);
        layout.addView(urlInput);
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
        connectionStatus.setPadding(0,20,0,0);
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

            try { byokManager.saveConfig(prov,mod,key,burl); }
            catch(Exception error){ connectionStatus.setText(error.getMessage());connectionStatus.setTextColor(0xFFB3261E);return; }
            saveBtn.setEnabled(false); providerSpinner.setEnabled(false); modelInput.setEnabled(false); keyInput.setEnabled(false); urlInput.setEnabled(false);
            connectionStatus.setText("Testing connection…");
            agentRunner.testConnection(new OceanAgentRunner.ConnectionCallback(){
                @Override public void onSuccess(){runOnUiThread(()->{saveBtn.setEnabled(true);providerSpinner.setEnabled(true);modelInput.setEnabled(true);keyInput.setEnabled(true);urlInput.setEnabled(true);modelInput.setText(byokManager.getModel());connectionStatus.setText("Connected · authenticated request succeeded");connectionStatus.setTextColor(0xFF18794E);((TextView)findViewById(R.id.model_button)).setText(byokManager.getModel());});}
                @Override public void onFailure(String error){runOnUiThread(()->{saveBtn.setEnabled(true);providerSpinner.setEnabled(true);modelInput.setEnabled(true);keyInput.setEnabled(true);urlInput.setEnabled(true);connectionStatus.setText("Not connected · "+error);connectionStatus.setTextColor(0xFFB3261E);});}
            });
        });

        scroll.addView(layout);
        contentFrame.addView(scroll);
    }

    private void submitAgentPrompt() {
        EditText promptInput = findViewById(R.id.prompt);
        String prompt = promptInput.getText().toString().trim();
        if (prompt.isEmpty()) return;
        promptInput.setText("");

        findViewById(R.id.home_content).setVisibility(View.GONE);
        View byok = contentFrame != null ? contentFrame.findViewWithTag("BYOK_VIEW") : null;
        if (byok != null) contentFrame.removeView(byok);
        if (chatScrollView != null) chatScrollView.setVisibility(View.VISIBLE);

        addUserMessageCard(prompt);

        LinearLayout agentCard = createAgentResponseCard();
        chatMessagesLayout.addView(agentCard);

        TextView thoughtView = agentCard.findViewWithTag("THOUGHT_VIEW");
        LinearLayout toolBox = agentCard.findViewWithTag("TOOL_BOX");
        OceanToolCard[] currentTool = {null};
        TextView responseView = agentCard.findViewWithTag("RESPONSE_VIEW");

        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));

        setAgentBusy(true);
        agentRunner.processPrompt(prompt, new OceanAgentRunner.AgentCallback() {
            @Override public void onThought(String thought) {
                thoughtView.setVisibility(View.VISIBLE);
                thoughtView.setText(thought);
            }

            @Override public void onToolStart(String toolName, String command) {
                thoughtView.setText("Running " + toolName.toLowerCase(java.util.Locale.ROOT) + "…");
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
                responseView.setVisibility(View.VISIBLE);
                responseView.setText(OceanMessageText.render(response));
                thoughtView.setVisibility(View.GONE);
                setAgentBusy(false);
                chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }

            @Override public void onError(String error) {
                responseView.setVisibility(View.VISIBLE);
                responseView.setText(error);
                thoughtView.setVisibility(View.GONE);
                setAgentBusy(false);
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
        bubble.setTextColor(0xFF191817);
        bubble.setTextSize(15f);
        bubble.setBackgroundResource(R.drawable.composer_background);
        bubble.setPadding(pad, pad, pad, pad);
        row.addView(bubble);

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

        TextView thought = new TextView(this);
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
    private void bindDestination(int id,String title) { findViewById(id).setOnClickListener(v -> { closeDrawer(); View content=findViewById(R.id.home_content); content.animate().alpha(0f).translationY(6f).setDuration(120).withEndAction(() -> { TextView heading=findViewById(R.id.empty_title), message=findViewById(R.id.empty_message); ((TextView)findViewById(R.id.screen_title)).setText(title); if(title.equals("OceanStudio")){ heading.setText(R.string.build_question); message.setText(R.string.build_subtitle); } else { heading.setText(title); message.setText(getString(R.string.destination_unavailable,title)); } content.setTranslationY(6f); content.animate().alpha(1f).translationY(0f).setDuration(190).start(); }).start(); }); }
    private void newChat() {
        if (agentRunner.isRunning()) { Toast.makeText(this, "Stop the current request before starting a new chat", Toast.LENGTH_SHORT).show(); return; }
        if (drawerOpen) closeDrawer();
        agentRunner.resetConversation();
        if (chatMessagesLayout != null) chatMessagesLayout.removeAllViews();
        if (chatScrollView != null) chatScrollView.setVisibility(View.GONE);
        if (contentFrame != null) { View byok = contentFrame.findViewWithTag("BYOK_VIEW"); if (byok != null) contentFrame.removeView(byok); }
        findViewById(R.id.home_content).setVisibility(View.VISIBLE);
        ((EditText)findViewById(R.id.prompt)).setText("");
        ((TextView)findViewById(R.id.screen_title)).setText(R.string.app_name);
        ((TextView)findViewById(R.id.empty_title)).setText(R.string.build_question);
        ((TextView)findViewById(R.id.empty_message)).setText(R.string.build_subtitle);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
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

    private void showModels(View anchor) {
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(18,16,18,16); content.setBackgroundResource(R.drawable.composer_background);
        for(int i=0;i<3;i++){View row=new View(this); row.setBackgroundResource(R.drawable.skeleton_background); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,(int)(28*getResources().getDisplayMetrics().density)); if(i>0)p.topMargin=(int)(8*getResources().getDisplayMetrics().density); content.addView(row,p);}
        PopupWindow popup=new PopupWindow(content,(int)(270*getResources().getDisplayMetrics().density),ViewGroup.LayoutParams.WRAP_CONTENT,true); popup.setBackgroundDrawable(getDrawable(R.drawable.composer_background)); popup.setElevation(7f); popup.setOutsideTouchable(true); popup.showAsDropDown(anchor,0,-(int)(150*getResources().getDisplayMetrics().density),Gravity.START); content.setAlpha(0f); content.setTranslationY(7f); content.animate().alpha(1f).translationY(0f).setDuration(180).start();
        content.post(() -> { if(!popup.isShowing())return; content.animate().alpha(0f).setDuration(100).withEndAction(() -> { content.removeAllViews(); TextView title=new TextView(this); title.setText(R.string.no_models); title.setTextColor(getColor(R.color.ocean_ink)); title.setTextSize(15); title.setTypeface(null,1); TextView detail=new TextView(this); detail.setText(R.string.connect_models); detail.setTextColor(getColor(R.color.ocean_muted)); detail.setTextSize(13); detail.setPadding(0,8,0,0); content.addView(title); content.addView(detail); content.animate().alpha(1f).setDuration(150).start(); }).start(); });
    }
}
