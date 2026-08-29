package studio.ocean.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

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

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { if (drawerOpen) closeDrawer(); else finish(); }});
        showLoading(() -> {
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
        findViewById(R.id.menu_button).setOnClickListener(v -> openDrawer()); backdrop.setOnClickListener(v -> closeDrawer()); findViewById(R.id.new_chat_button).setOnClickListener(v -> newChat()); findViewById(R.id.sidebar_new_chat).setOnClickListener(v -> newChat()); findViewById(R.id.model_button).setOnClickListener(this::showModels);
        bindGroup(R.id.group_workspace,R.id.workspace_children,R.id.chevron_workspace); bindGroup(R.id.group_agents,R.id.agents_children,R.id.chevron_agents); bindGroup(R.id.group_tools,R.id.tools_children,R.id.chevron_tools); bindGroup(R.id.group_connections,R.id.connections_children,R.id.chevron_connections);
        bindDestination(R.id.nav_agent,"OceanStudio"); bindDestination(R.id.nav_editor,"Editor"); bindDestination(R.id.nav_files,"Files"); bindDestination(R.id.nav_terminal,"Ocean Terminal"); bindDestination(R.id.nav_preview,"Preview");
        findViewById(R.id.sign_out).setOnClickListener(v -> { developmentSession=false; authState=authClient.configured()?AuthState.CONFIGURED_LOGGED_OUT:AuthState.CONFIGURATION_MISSING; getSharedPreferences(PREFS,MODE_PRIVATE).edit().clear().apply(); showAuth(); });
        sidebar.post(() -> { int width=Math.min((int)(getResources().getDisplayMetrics().widthPixels*.76f),(int)(360*getResources().getDisplayMetrics().density)); ViewGroup.LayoutParams p=sidebar.getLayoutParams(); p.width=width; sidebar.setLayoutParams(p); sidebar.setTranslationX(-width); });
        View skeleton=findViewById(R.id.home_skeleton), content=findViewById(R.id.home_content); content.post(() -> { skeleton.animate().alpha(0f).setDuration(220).withEndAction(() -> skeleton.setVisibility(View.GONE)).start(); content.animate().alpha(1f).translationY(0f).setDuration(260).start(); });
        View recentSkeleton=findViewById(R.id.recent_skeleton), recentEmpty=findViewById(R.id.no_recent_sessions); recentSkeleton.post(() -> { recentSkeleton.animate().alpha(0f).setDuration(180).withEndAction(() -> { recentSkeleton.setVisibility(View.GONE); recentEmpty.setAlpha(0f); recentEmpty.setVisibility(View.VISIBLE); recentEmpty.animate().alpha(1f).setDuration(180).start(); }).start(); });
    }

    private void bindGroup(int parentId,int childId,int chevronId) { findViewById(parentId).setOnClickListener(v -> animateAccordion(findViewById(childId),findViewById(chevronId))); }
    private void animateAccordion(View children,View chevron) {
        boolean expand=children.getVisibility()!=View.VISIBLE; int start=expand?0:children.getHeight();
        if(expand){children.setVisibility(View.VISIBLE); children.measure(View.MeasureSpec.makeMeasureSpec(((View)children.getParent()).getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));}
        int end=expand?children.getMeasuredHeight():0; children.setAlpha(expand?0f:1f); ValueAnimator height=ValueAnimator.ofInt(start,end); height.setDuration(220); height.setInterpolator(new DecelerateInterpolator()); height.addUpdateListener(a -> {ViewGroup.LayoutParams p=children.getLayoutParams();p.height=(int)a.getAnimatedValue();children.setLayoutParams(p);children.setAlpha(expand?a.getAnimatedFraction():1f-a.getAnimatedFraction());}); height.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(!expand)children.setVisibility(View.GONE);ViewGroup.LayoutParams p=children.getLayoutParams();p.height=expand?ViewGroup.LayoutParams.WRAP_CONTENT:0;children.setLayoutParams(p);}}); height.start(); chevron.animate().rotation(expand?90f:0f).setDuration(200).start();
    }
    private void bindDestination(int id,String title) { findViewById(id).setOnClickListener(v -> { closeDrawer(); View content=findViewById(R.id.home_content); content.animate().alpha(0f).translationY(6f).setDuration(120).withEndAction(() -> { TextView heading=findViewById(R.id.empty_title), message=findViewById(R.id.empty_message); ((TextView)findViewById(R.id.screen_title)).setText(title); if(title.equals("OceanStudio")){ heading.setText(R.string.build_question); message.setText(R.string.build_subtitle); } else { heading.setText(title); message.setText(getString(R.string.destination_unavailable,title)); } content.setTranslationY(6f); content.animate().alpha(1f).translationY(0f).setDuration(190).start(); }).start(); }); }
    private void newChat() { if(drawerOpen) closeDrawer(); ((EditText)findViewById(R.id.prompt)).setText(""); ((TextView)findViewById(R.id.screen_title)).setText(R.string.app_name); ((TextView)findViewById(R.id.empty_title)).setText(R.string.build_question); ((TextView)findViewById(R.id.empty_message)).setText(R.string.build_subtitle); }

    private void openDrawer() { if(drawerOpen)return; drawerOpen=true; sidebar.setVisibility(View.VISIBLE); backdrop.setAlpha(0f); backdrop.setVisibility(View.VISIBLE); backdrop.animate().alpha(1f).setDuration(190).start(); sidebar.animate().translationX(0f).setDuration(270).setInterpolator(new DecelerateInterpolator()).start(); findViewById(R.id.main_content).animate().translationX(sidebar.getWidth()*.08f).setDuration(270).start(); }
    private void closeDrawer() { if(!drawerOpen)return; drawerOpen=false; backdrop.animate().alpha(0f).setDuration(180).withEndAction(() -> backdrop.setVisibility(View.GONE)).start(); sidebar.animate().translationX(-sidebar.getWidth()).setDuration(240).setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){sidebar.setVisibility(View.GONE); sidebar.animate().setListener(null);}}).start(); findViewById(R.id.main_content).animate().translationX(0f).setDuration(240).start(); }

    private void showModels(View anchor) {
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(18,16,18,16); content.setBackgroundResource(R.drawable.composer_background);
        for(int i=0;i<3;i++){View row=new View(this); row.setBackgroundResource(R.drawable.skeleton_background); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,(int)(28*getResources().getDisplayMetrics().density)); if(i>0)p.topMargin=(int)(8*getResources().getDisplayMetrics().density); content.addView(row,p);}
        PopupWindow popup=new PopupWindow(content,(int)(270*getResources().getDisplayMetrics().density),ViewGroup.LayoutParams.WRAP_CONTENT,true); popup.setBackgroundDrawable(getDrawable(R.drawable.composer_background)); popup.setElevation(7f); popup.setOutsideTouchable(true); popup.showAsDropDown(anchor,0,-(int)(150*getResources().getDisplayMetrics().density),Gravity.START); content.setAlpha(0f); content.setTranslationY(7f); content.animate().alpha(1f).translationY(0f).setDuration(180).start();
        content.post(() -> { if(!popup.isShowing())return; content.animate().alpha(0f).setDuration(100).withEndAction(() -> { content.removeAllViews(); TextView title=new TextView(this); title.setText(R.string.no_models); title.setTextColor(getColor(R.color.ocean_ink)); title.setTextSize(15); title.setTypeface(null,1); TextView detail=new TextView(this); detail.setText(R.string.connect_models); detail.setTextColor(getColor(R.color.ocean_muted)); detail.setTextSize(13); detail.setPadding(0,8,0,0); content.addView(title); content.addView(detail); content.animate().alpha(1f).setDuration(150).start(); }).start(); });
    }
}
