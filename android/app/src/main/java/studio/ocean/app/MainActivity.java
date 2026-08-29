package studio.ocean.app;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
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
import android.view.animation.AccelerateDecelerateInterpolator;
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
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AnimatorSet loadingAnimator;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { if (drawerOpen) closeDrawer(); else finish(); }});
        showLoadingScreen();
        boolean hasSession=getSharedPreferences(PREFS,MODE_PRIVATE).contains(TOKEN);
        handler.postDelayed(() -> exitLoading(hasSession),550);
    }

    private void showLoadingScreen() {
        setContentView(R.layout.activity_loading); View logo=findViewById(R.id.loading_logo); logo.setAlpha(0f); logo.setTranslationY(9f); logo.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();
        loadingAnimator=new AnimatorSet(); ObjectAnimator one=dotAnimator(findViewById(R.id.loading_dot_one),0), two=dotAnimator(findViewById(R.id.loading_dot_two),130), three=dotAnimator(findViewById(R.id.loading_dot_three),260); loadingAnimator.playTogether(one,two,three); loadingAnimator.start();
    }
    private ObjectAnimator dotAnimator(View dot,long delay) { ObjectAnimator animator=ObjectAnimator.ofFloat(dot,"alpha",0.25f,1f,0.25f); animator.setDuration(780); animator.setStartDelay(delay); animator.setRepeatCount(ValueAnimator.INFINITE); animator.setInterpolator(new AccelerateDecelerateInterpolator()); return animator; }
    private void exitLoading(boolean hasSession) { View root=findViewById(R.id.loading_root); root.animate().alpha(0f).translationY(-5f).setDuration(180).withEndAction(() -> { if(loadingAnimator!=null) loadingAnimator.cancel(); if(hasSession){authState=AuthState.CONFIGURED_LOGGED_IN;showMain();}else{authState=authClient.configured()?AuthState.CONFIGURED_LOGGED_OUT:AuthState.CONFIGURATION_MISSING;showAuth();} }).start(); }

    private void showAuth() {
        setContentView(R.layout.activity_auth);
        View authRoot=findViewById(android.R.id.content); authRoot.setAlpha(0f); authRoot.animate().alpha(1f).setDuration(240).start();
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
        AuthClient.Callback callback=result -> runOnUiThread(() -> { setAuthBusy(false); if (!result.success) { authState=AuthState.ERROR; showAuthStatus(result.message==null?getString(R.string.request_failed):result.message,false); return; } if (authMode==AuthMode.FORGOT) { showAuthStatus(getString(R.string.reset_sent),true); return; } authState=AuthState.CONFIGURED_LOGGED_IN; getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(TOKEN,result.token).putString(EMAIL,result.email).apply(); showMain(); });
        if (authMode==AuthMode.FORGOT) authClient.reset(email,callback); else authClient.signIn(email,password,authMode==AuthMode.SIGN_UP,callback);
    }

    private void setAuthBusy(boolean busy) { findViewById(R.id.auth_primary).setEnabled(!busy); findViewById(R.id.google_auth).setEnabled(!busy); findViewById(R.id.github_auth).setEnabled(!busy); }
    private void showAuthStatus(String message, boolean success) { TextView status=findViewById(R.id.auth_status); status.setText(message); status.setTextColor(getColor(success?R.color.ocean_ink:R.color.ocean_error)); status.setVisibility(View.VISIBLE); }
    private boolean devBypassAvailable() { return BuildConfig.DEBUG && BuildConfig.OCEAN_DEV_AUTH_BYPASS; }
    private void startDevelopmentSession() { if (!devBypassAvailable()) return; developmentSession=true; authState=AuthState.DEV_BYPASS_LOGGED_IN; showMain(); }

    private void showMain() {
        setContentView(R.layout.activity_main); sidebar=findViewById(R.id.sidebar); backdrop=findViewById(R.id.drawer_backdrop);
        View main=findViewById(R.id.main_content); main.setAlpha(0f); main.setTranslationY(7f); main.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(new DecelerateInterpolator()).start();
        findViewById(R.id.menu_button).setOnClickListener(v -> openDrawer()); backdrop.setOnClickListener(v -> closeDrawer()); findViewById(R.id.new_chat_button).setOnClickListener(v -> newChat()); findViewById(R.id.sidebar_new_chat).setOnClickListener(v -> newChat()); findViewById(R.id.model_button).setOnClickListener(this::showModels);
        bindGroup(R.id.group_workspace,R.id.workspace_children,R.id.chevron_workspace); bindGroup(R.id.group_agents,R.id.agents_children,R.id.chevron_agents); bindGroup(R.id.group_tools,R.id.tools_children,R.id.chevron_tools); bindGroup(R.id.group_connections,R.id.connections_children,R.id.chevron_connections);
        bindDestination(R.id.nav_agent,"OceanStudio"); bindDestination(R.id.nav_editor,"Editor"); bindDestination(R.id.nav_files,"Files"); bindDestination(R.id.nav_terminal,"Ocean Terminal"); bindDestination(R.id.nav_preview,"Preview");
        findViewById(R.id.sign_out).setOnClickListener(v -> { developmentSession=false; authState=authClient.configured()?AuthState.CONFIGURED_LOGGED_OUT:AuthState.CONFIGURATION_MISSING; getSharedPreferences(PREFS,MODE_PRIVATE).edit().clear().apply(); showAuth(); });
        sidebar.post(() -> { int width=Math.min((int)(getResources().getDisplayMetrics().widthPixels*.76f),(int)(360*getResources().getDisplayMetrics().density)); ViewGroup.LayoutParams p=sidebar.getLayoutParams(); p.width=width; sidebar.setLayoutParams(p); sidebar.setTranslationX(-width); });
        startSkeletonPulse(findViewById(R.id.home_loading)); startSkeletonPulse(findViewById(R.id.recent_skeletons)); handler.postDelayed(() -> revealHome(),420); handler.postDelayed(() -> revealRecents(),520);
    }

    private void bindGroup(int parentId,int childId,int chevronId) { findViewById(parentId).setOnClickListener(v -> animateGroup(findViewById(childId),findViewById(chevronId))); }
    private void animateGroup(View children,View chevron) { boolean opening=children.getVisibility()!=View.VISIBLE; chevron.animate().rotation(opening?90f:0f).setDuration(190).setInterpolator(new DecelerateInterpolator()).start(); if(opening){children.setVisibility(View.VISIBLE); children.measure(View.MeasureSpec.makeMeasureSpec(((View)children.getParent()).getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.UNSPECIFIED); int target=children.getMeasuredHeight(); children.getLayoutParams().height=0; children.setAlpha(0f); animateHeight(children,0,target,true);}else{animateHeight(children,children.getHeight(),0,false);} }
    private void animateHeight(View view,int from,int to,boolean opening) { ValueAnimator animator=ValueAnimator.ofInt(from,to); animator.setDuration(210); animator.setInterpolator(new DecelerateInterpolator()); animator.addUpdateListener(a -> { view.getLayoutParams().height=(int)a.getAnimatedValue(); view.requestLayout(); view.setAlpha(opening?a.getAnimatedFraction():1f-a.getAnimatedFraction()); }); animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(opening)view.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;else view.setVisibility(View.GONE);view.requestLayout();}}); animator.start(); }
    private void startSkeletonPulse(View view) { ObjectAnimator pulse=ObjectAnimator.ofFloat(view,"alpha",0.45f,1f,0.45f); pulse.setDuration(1100); pulse.setRepeatCount(ValueAnimator.INFINITE); pulse.start(); view.setTag(pulse); }
    private void stopSkeletonPulse(View view) { Object tag=view.getTag(); if(tag instanceof Animator)((Animator)tag).cancel(); }
    private void revealHome() { View loading=findViewById(R.id.home_loading); if(loading==null)return; stopSkeletonPulse(loading); loading.animate().alpha(0f).setDuration(150).withEndAction(() -> {loading.setVisibility(View.GONE);View ready=findViewById(R.id.home_ready);ready.setAlpha(0f);ready.setTranslationY(6f);ready.setVisibility(View.VISIBLE);ready.animate().alpha(1f).translationY(0f).setDuration(230).start();}).start(); }
    private void revealRecents() { View loading=findViewById(R.id.recent_skeletons); if(loading==null)return; stopSkeletonPulse(loading); loading.animate().alpha(0f).setDuration(130).withEndAction(() -> {loading.setVisibility(View.GONE);View empty=findViewById(R.id.no_recent_sessions);empty.setAlpha(0f);empty.setVisibility(View.VISIBLE);empty.animate().alpha(1f).setDuration(180).start();}).start(); }
    private void bindDestination(int id,String title) { findViewById(id).setOnClickListener(v -> { closeDrawer(); showDestination(title); }); }
    private void showDestination(String title) {
        TextView heading=findViewById(R.id.empty_title), message=findViewById(R.id.empty_message); View ready=findViewById(R.id.home_ready);
        ((TextView)findViewById(R.id.screen_title)).setText(title);
        ready.animate().cancel(); ready.animate().alpha(0f).translationY(4f).setDuration(110).withEndAction(() -> {
            if(title.equals("OceanStudio")){ heading.setText(R.string.build_question); message.setText(R.string.build_subtitle); }
            else { heading.setText(title); message.setText(getString(R.string.destination_unavailable,title)); }
            ready.setTranslationY(7f); ready.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }
    private void newChat() { if(drawerOpen) closeDrawer(); ((EditText)findViewById(R.id.prompt)).setText(""); ((TextView)findViewById(R.id.screen_title)).setText(R.string.app_name); ((TextView)findViewById(R.id.empty_title)).setText(R.string.build_question); ((TextView)findViewById(R.id.empty_message)).setText(R.string.build_subtitle); }

    private void openDrawer() { if(drawerOpen)return; drawerOpen=true; sidebar.setVisibility(View.VISIBLE); backdrop.setAlpha(0f); backdrop.setVisibility(View.VISIBLE); backdrop.animate().alpha(1f).setDuration(190).start(); sidebar.animate().translationX(0f).setDuration(270).setInterpolator(new DecelerateInterpolator()).start(); findViewById(R.id.main_content).animate().translationX(sidebar.getWidth()*.08f).setDuration(270).start(); }
    private void closeDrawer() { if(!drawerOpen)return; drawerOpen=false; backdrop.animate().alpha(0f).setDuration(180).withEndAction(() -> backdrop.setVisibility(View.GONE)).start(); sidebar.animate().translationX(-sidebar.getWidth()).setDuration(240).setInterpolator(new DecelerateInterpolator()).setListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){sidebar.setVisibility(View.GONE); sidebar.animate().setListener(null);}}).start(); findViewById(R.id.main_content).animate().translationX(0f).setDuration(240).start(); }

    private void showModels(View anchor) {
        float density=getResources().getDisplayMetrics().density;
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding((int)(18*density),(int)(14*density),(int)(18*density),(int)(14*density)); content.setBackgroundResource(R.drawable.composer_background);
        for(int i=0;i<3;i++){ View row=new View(this); row.setBackgroundResource(R.drawable.skeleton_background); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(i==0?(int)(90*density):(int)((170-i*15)*density),(int)(12*density)); p.setMargins(0,0,0,(int)(10*density)); content.addView(row,p); }
        PopupWindow popup=new PopupWindow(content,(int)(260*density),ViewGroup.LayoutParams.WRAP_CONTENT,true); popup.setBackgroundDrawable(getDrawable(R.drawable.composer_background)); popup.setElevation(7f); popup.setOutsideTouchable(true); popup.showAsDropDown(anchor,0,-(int)(150*density),Gravity.START);
        content.setAlpha(0f); content.setTranslationY(7f); content.animate().alpha(1f).translationY(0f).setDuration(180).start(); startSkeletonPulse(content);
        handler.postDelayed(() -> { if(!popup.isShowing())return; stopSkeletonPulse(content); content.animate().alpha(0f).setDuration(100).withEndAction(() -> { content.removeAllViews(); TextView title=new TextView(this); title.setText(R.string.no_models); title.setTextColor(getColor(R.color.ocean_ink)); title.setTextSize(15); title.setTypeface(null,1); TextView detail=new TextView(this); detail.setText(R.string.connect_models); detail.setTextColor(getColor(R.color.ocean_muted)); detail.setTextSize(13); detail.setPadding(0,(int)(8*density),0,0); content.addView(title); content.addView(detail); content.animate().alpha(1f).setDuration(170).start(); }).start(); },360);
    }
}
