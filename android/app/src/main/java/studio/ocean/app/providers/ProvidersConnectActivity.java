package studio.ocean.app.providers;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import studio.ocean.app.OceanAgentRunner;
import studio.ocean.app.OceanByokManager;
import studio.ocean.app.R;

/** Sidebar Providers page: connect real LLM backends with API keys or native OAuth. */
public final class ProvidersConnectActivity extends AppCompatActivity {
    private OceanByokManager byokManager;
    private OceanAgentRunner agentRunner;
    private ProviderOAuthSession oauthSession;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_providers_connect);
        byokManager = new OceanByokManager(this);
        agentRunner = new OceanAgentRunner(this);
        findViewById(R.id.providers_back).setOnClickListener(v -> finish());
        renderProviders();
    }

    @Override protected void onDestroy() {
        if (oauthSession != null) oauthSession.cancel();
        super.onDestroy();
    }

    private void renderProviders() {
        LinearLayout list = findViewById(R.id.providers_list);
        list.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (ProviderCatalog.Entry entry : ProviderCatalog.all()) {
            list.addView(buildCard(entry, density));
            View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.ocean_border));
            LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
            d.topMargin = (int) (10 * density);
            d.bottomMargin = (int) (10 * density);
            list.addView(divider, d);
        }
    }

    private View buildCard(ProviderCatalog.Entry entry, float density) {
        int pad = (int) (16 * density);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(pad, pad, pad, pad);
        card.setBackgroundResource(R.drawable.auth_field_background);

        TextView title = new TextView(this);
        title.setText(entry.title);
        title.setTextColor(getColor(R.color.ocean_ink));
        title.setTextSize(16f);
        title.setTypeface(null, Typeface.BOLD);
        card.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(entry.subtitle);
        subtitle.setTextColor(getColor(R.color.ocean_muted));
        subtitle.setTextSize(13f);
        subtitle.setPadding(0, (int) (4 * density), 0, (int) (12 * density));
        card.addView(subtitle);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        if (entry.authMode == ProviderCatalog.AuthMode.OAUTH_PKCE) {
            Button oauth = new Button(this);
            oauth.setText("Connect with Google");
            oauth.setAllCaps(false);
            oauth.setTextColor(getColor(R.color.ocean_background));
            oauth.setBackgroundColor(getColor(R.color.ocean_ink));
            oauth.setOnClickListener(v -> startOAuth(entry));
            actions.addView(oauth, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (44 * density)));
        }

        Button connect = new Button(this);
        connect.setText("API key");
        connect.setAllCaps(false);
        connect.setTextColor(getColor(R.color.ocean_ink));
        connect.setBackgroundResource(android.R.drawable.list_selector_background);
        LinearLayout.LayoutParams connectLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (44 * density));
        connectLp.setMarginStart((int) (8 * density));
        connect.setOnClickListener(v -> showApiKeyDialog(entry));
        actions.addView(connect, connectLp);

        card.addView(actions);
        return card;
    }

    private void showApiKeyDialog(ProviderCatalog.Entry entry) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        layout.setPadding(pad, pad, pad, pad);

        Field model = field("Model ID", entry.defaultModel);
        layout.addView(model.container);
        Field key = field("API key", "");
        key.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(key.container);
        Field base = field("API base URL", entry.defaultBaseUrl);
        layout.addView(base.container);

        TextView hint = new TextView(this);
        hint.setText(entry.credentialHint);
        hint.setTextColor(getColor(R.color.ocean_muted));
        hint.setTextSize(12f);
        hint.setPadding(0, (int) (8 * density), 0, 0);
        layout.addView(hint);

        new AlertDialog.Builder(this)
                .setTitle(entry.title)
                .setView(layout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Save & test", (dialog, which) -> saveAndTest(entry, model.input.getText().toString().trim(),
                        key.input.getText().toString().trim(), base.input.getText().toString().trim()))
                .show();
    }

    private static final class Field {
        final LinearLayout container;
        final EditText input;
        Field(LinearLayout container, EditText input) { this.container = container; this.input = input; }
    }

    private Field field(String label, String value) {
        float density = getResources().getDisplayMetrics().density;
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(getColor(R.color.ocean_ink));
        caption.setTypeface(null, Typeface.BOLD);
        caption.setTextSize(12f);
        caption.setPadding(0, (int) (12 * density), 0, (int) (6 * density));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(caption);

        EditText input = new EditText(this);
        input.setText(value);
        input.setSingleLine(true);
        input.setBackgroundResource(R.drawable.composer_background);
        input.setPadding((int) (12 * density), (int) (10 * density), (int) (12 * density), (int) (10 * density));
        wrap.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new Field(wrap, input);
    }

    private void saveAndTest(ProviderCatalog.Entry entry, String model, String key, String baseUrl) {
        try {
            byokManager.saveConfig(entry.byokProviderId(), model, key, baseUrl);
        } catch (Exception error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Testing connection…", Toast.LENGTH_SHORT).show();
        agentRunner.testConnection(new OceanAgentRunner.ConnectionCallback() {
            @Override public void onSuccess() {
                runOnUiThread(() -> Toast.makeText(ProvidersConnectActivity.this,
                        entry.title + " connected", Toast.LENGTH_LONG).show());
            }
            @Override public void onFailure(String error) {
                runOnUiThread(() -> Toast.makeText(ProvidersConnectActivity.this,
                        "Not connected · " + error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void startOAuth(ProviderCatalog.Entry entry) {
        if (!OceanByokManager.PROVIDER_GOOGLE.equals(entry.id)) {
            Toast.makeText(this, "OAuth is not available for this provider yet", Toast.LENGTH_SHORT).show();
            return;
        }
        oauthSession = new ProviderOAuthSession(this);
        AlertDialog waiting = new AlertDialog.Builder(this)
                .setTitle("Google sign-in")
                .setMessage("Opening a secure browser tab…")
                .setCancelable(true)
                .setNegativeButton("Paste redirect URL", (d, w) -> showManualOAuthFallback(entry))
                .create();
        waiting.show();
        oauthSession.startGoogle(new ProviderOAuthSession.Callback() {
            @Override public void onWaiting(String detail) {
                runOnUiThread(() -> waiting.setMessage(detail));
            }
            @Override public void onSuccess(String accessToken, String refreshToken) {
                runOnUiThread(() -> {
                    waiting.dismiss();
                    try {
                        byokManager.saveConfig(OceanByokManager.PROVIDER_GOOGLE,
                                entry.defaultModel, accessToken, entry.defaultBaseUrl);
                        byokManager.markVerified(byokManager.configurationDigest());
                        Toast.makeText(ProvidersConnectActivity.this, "Google connected", Toast.LENGTH_LONG).show();
                    } catch (Exception error) {
                        Toast.makeText(ProvidersConnectActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
            @Override public void onFailure(String message) {
                runOnUiThread(() -> {
                    waiting.dismiss();
                    Toast.makeText(ProvidersConnectActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showManualOAuthFallback(ProviderCatalog.Entry entry) {
        EditText url = new EditText(this);
        url.setHint("https://127.0.0.1:…/oauth/callback?code=…");
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle("Paste redirect URL")
                .setMessage("If the browser tab cannot return automatically, paste the full redirect URL from the address bar.")
                .setView(url)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Complete", (d, w) -> {
                    if (oauthSession == null) oauthSession = new ProviderOAuthSession(this);
                    oauthSession.completeWithRedirectUrl(url.getText().toString(), new ProviderOAuthSession.Callback() {
                        @Override public void onWaiting(String detail) { }
                        @Override public void onSuccess(String accessToken, String refreshToken) {
                            runOnUiThread(() -> {
                                try {
                                    byokManager.saveConfig(entry.byokProviderId(), entry.defaultModel, accessToken, entry.defaultBaseUrl);
                                    Toast.makeText(ProvidersConnectActivity.this, "Connected", Toast.LENGTH_LONG).show();
                                } catch (Exception error) {
                                    Toast.makeText(ProvidersConnectActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        @Override public void onFailure(String message) {
                            runOnUiThread(() -> Toast.makeText(ProvidersConnectActivity.this, message, Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .show();
    }
}
