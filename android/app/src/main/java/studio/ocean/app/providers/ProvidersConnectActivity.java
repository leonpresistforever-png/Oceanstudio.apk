package studio.ocean.app.providers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.app.Dialog;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import studio.ocean.app.OceanModal;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import studio.ocean.app.OceanAgentRunner;
import studio.ocean.app.OceanByokManager;
import studio.ocean.app.R;
import studio.ocean.app.providers.auth.AuthErrorNormalizer;
import studio.ocean.app.providers.auth.AuthOrchestrator;
import studio.ocean.app.providers.auth.AuthPreflight;
import studio.ocean.app.providers.cli.AntigravityCliAdapter;
import studio.ocean.app.providers.cli.ClaudeCodeCliAdapter;
import studio.ocean.app.providers.cli.CodexCliAdapter;
import studio.ocean.app.providers.cli.KimiCliAdapter;
import studio.ocean.app.providers.cli.OfficialCliAdapter;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ConnectionStatus;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.ProviderConnection;
import studio.ocean.app.providers.model.ProviderDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;
import studio.ocean.app.providers.state.CredentialVault;
import studio.ocean.app.providers.state.ModelCatalogService;
import studio.ocean.app.providers.state.ProviderConnectionStore;
import studio.ocean.app.providers.state.QuotaService;

/**
 * OceanStudio Provider Hub:
 * Unified multi-strategy management for subscription accounts, official CLI bridges,
 * direct API keys, and local runtimes.
 *
 * Implements compact monochrome UI/UX, non-deceptive quota inspection, and strict preflight validation.
 */
public final class ProvidersConnectActivity extends AppCompatActivity {

    public enum FilterCategory {
        ALL("All"),
        CONNECTED("Connected"),
        DIRECT_CONNECT("Direct Connect"),
        CLI_SUBSCRIPTION("CLI Bridge"),
        API_KEY("API Key");

        public final String label;
        FilterCategory(String label) { this.label = label; }
    }

    public enum SortMode {
        RECOMMENDED("Recommended"),
        NAME("Name (A-Z)"),
        STATUS("Connection Status");

        public final String label;
        SortMode(String label) { this.label = label; }
    }

    private OceanByokManager byokManager;
    private OceanAgentRunner agentRunner;
    private ProviderConnectionStore connectionStore;
    private CredentialVault credentialVault;
    private QuotaService quotaService;
    private ModelCatalogService modelCatalogService;
    private AuthOrchestrator authOrchestrator;

    // CLI Adapters
    private AntigravityCliAdapter antigravityCli;
    private KimiCliAdapter kimiCli;
    private ClaudeCodeCliAdapter claudeCli;
    private CodexCliAdapter codexCli;

    // UI state
    private FilterCategory activeFilter = FilterCategory.ALL;
    private SortMode currentSort = SortMode.RECOMMENDED;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_providers_connect);

        byokManager = new OceanByokManager(this);
        agentRunner = new OceanAgentRunner(this);
        connectionStore = new ProviderConnectionStore(this);
        credentialVault = new CredentialVault(this);
        quotaService = new QuotaService();
        modelCatalogService = new ModelCatalogService();
        authOrchestrator = new AuthOrchestrator(this);

        // Target application dedicated tools prefix (/data/data/studio.ocean.app/files/usr/bin)
        File toolsDir = new File(getFilesDir(), "usr/bin");
        antigravityCli = new AntigravityCliAdapter(toolsDir);
        kimiCli = new KimiCliAdapter(toolsDir);
        claudeCli = new ClaudeCodeCliAdapter(toolsDir);
        codexCli = new CodexCliAdapter(toolsDir);

        seedFromLegacyByokIfNeeded();

        initHeaderActions();
        renderFilterPills();
        renderProviders();

        handleIncomingOAuthIntent(getIntent());
    }

    private void seedFromLegacyByokIfNeeded() {
        if (!connectionStore.listAll().isEmpty()) return;
        if (byokManager == null || !byokManager.hasApiKey()) return;

        String raw = byokManager.getProvider();
        String providerId;
        if (OceanByokManager.PROVIDER_GOOGLE.equals(raw)) providerId = ProviderRegistry.ID_GOOGLE;
        else if (OceanByokManager.PROVIDER_ANTHROPIC.equals(raw)) providerId = ProviderRegistry.ID_ANTHROPIC;
        else if (OceanByokManager.PROVIDER_OPENAI.equals(raw)) providerId = ProviderRegistry.ID_OPENAI;
        else providerId = ProviderRegistry.ID_CUSTOM;

        String apiKey = byokManager.getApiKey();
        String model = byokManager.getModel();
        String baseUrl = byokManager.getBaseUrl();
        String ref = credentialVault.store(apiKey);

        ProviderConnection seed = new ProviderConnection(
                UUID.randomUUID().toString(),
                providerId,
                maskKey(apiKey),
                AuthStrategy.API_KEY,
                ConnectionStatus.CONNECTED,
                baseUrl,
                model,
                ref,
                null,
                null,
                null,
                QuotaSnapshot.unknown("Pay-as-you-go API", "byok-migration"),
                null,
                System.currentTimeMillis()
        );
        connectionStore.save(seed);
    }

    private void initHeaderActions() {
        findViewById(R.id.providers_back).setOnClickListener(v -> finish());

        LinearLayout searchContainer = findViewById(R.id.providers_search_container);
        EditText searchInput = findViewById(R.id.providers_search_input);
        ImageButton searchClear = findViewById(R.id.providers_search_clear);
        ImageButton searchBtn = findViewById(R.id.providers_search_btn);
        ImageButton sortBtn = findViewById(R.id.providers_sort_btn);
        Button resetFilterBtn = findViewById(R.id.providers_reset_filter_btn);

        searchBtn.setOnClickListener(v -> {
            if (searchContainer.getVisibility() == View.VISIBLE) {
                searchContainer.setVisibility(View.GONE);
                searchQuery = "";
                searchInput.setText("");
                renderProviders();
            } else {
                searchContainer.setVisibility(View.VISIBLE);
                searchInput.requestFocus();
            }
        });

        searchClear.setOnClickListener(v -> {
            searchInput.setText("");
            searchQuery = "";
            renderProviders();
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s != null ? s.toString().trim() : "";
                renderProviders();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        sortBtn.setOnClickListener(v -> showSortDialog());

        resetFilterBtn.setOnClickListener(v -> {
            activeFilter = FilterCategory.ALL;
            searchQuery = "";
            searchInput.setText("");
            searchContainer.setVisibility(View.GONE);
            renderFilterPills();
            renderProviders();
        });
    }

    private void showSortDialog() {
        SortMode[] modes = SortMode.values();
        float density = getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        Dialog[] dialogHolder = new Dialog[1];
        for (SortMode mode : modes) {
            boolean isSelected = (mode == currentSort);
            TextView item = new TextView(this);
            item.setText((isSelected ? "● " : "○ ") + mode.label);
            item.setTextSize(14f);
            item.setTypeface(null, isSelected ? Typeface.BOLD : Typeface.NORMAL);
            item.setTextColor(isSelected ? getColor(R.color.ocean_paper) : getColor(R.color.ocean_ink));
            item.setBackgroundResource(isSelected ? R.drawable.model_chip_background_selected : R.drawable.settings_row_background);
            int pad = (int) (12 * density);
            item.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (6 * density);
            item.setLayoutParams(lp);

            item.setOnClickListener(v -> {
                currentSort = mode;
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                renderProviders();
            });
            list.addView(item);
        }

        dialogHolder[0] = OceanModal.create(this)
                .setTitle("Sort Providers")
                .setExplanation("Choose how providers are ordered in the catalog.")
                .setCustomView(list)
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderFilterPills() {
        LinearLayout container = findViewById(R.id.providers_filters_container);
        container.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (FilterCategory category : FilterCategory.values()) {
            TextView pill = new TextView(this);
            pill.setText(category.label);
            pill.setTextSize(13f);
            pill.setGravity(Gravity.CENTER);
            int padH = (int) (14 * density);
            int padV = (int) (6 * density);
            pill.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, (int) (32 * density));
            lp.setMarginEnd((int) (8 * density));
            pill.setLayoutParams(lp);

            boolean isActive = category == activeFilter;
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(16 * density);
            if (isActive) {
                gd.setColor(getColor(R.color.ocean_ink));
                pill.setTextColor(getColor(R.color.ocean_background));
            } else {
                gd.setColor(getColor(R.color.ocean_surface_2));
                gd.setStroke((int) (1 * density), getColor(R.color.ocean_border));
                pill.setTextColor(getColor(R.color.ocean_ink));
            }
            pill.setBackground(gd);

            pill.setOnClickListener(v -> {
                activeFilter = category;
                renderFilterPills();
                renderProviders();
            });

            container.addView(pill);
        }
    }

    private void renderProviders() {
        LinearLayout list = findViewById(R.id.providers_list);
        LinearLayout emptyState = findViewById(R.id.providers_empty_state);
        list.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        List<ProviderDescriptor> filtered = new ArrayList<>();
        String queryLower = searchQuery.toLowerCase();

        for (ProviderDescriptor desc : ProviderRegistry.all()) {
            if (ProviderRegistry.ID_LOCAL.equals(desc.id)) continue;
            List<ProviderConnection> conns = connectionStore.listByProvider(desc.id);
            boolean isConnected = !conns.isEmpty() && conns.get(0).status == ConnectionStatus.CONNECTED;

            // Apply filter category
            if (activeFilter == FilterCategory.CONNECTED && !isConnected) continue;
            if (activeFilter == FilterCategory.DIRECT_CONNECT && !desc.supports(AuthStrategy.DIRECT_OAUTH) && !desc.supports(AuthStrategy.OFFICIAL_OAUTH) && !desc.supports(AuthStrategy.DEVICE_CODE)) continue;
            if (activeFilter == FilterCategory.CLI_SUBSCRIPTION && !desc.supports(AuthStrategy.OFFICIAL_CLI)) continue;
            if (activeFilter == FilterCategory.API_KEY && !desc.supports(AuthStrategy.API_KEY)) continue;

            // Apply search query
            if (!queryLower.isEmpty()) {
                boolean matchesTitle = desc.title.toLowerCase().contains(queryLower);
                boolean matchesSubtitle = desc.subtitle.toLowerCase().contains(queryLower);
                boolean matchesModel = desc.defaultModel != null && desc.defaultModel.toLowerCase().contains(queryLower);
                if (!matchesTitle && !matchesSubtitle && !matchesModel) continue;
            }

            filtered.add(desc);
        }

        // Apply sorting
        if (currentSort == SortMode.NAME) {
            Collections.sort(filtered, (a, b) -> a.title.compareToIgnoreCase(b.title));
        } else if (currentSort == SortMode.STATUS) {
            Collections.sort(filtered, (a, b) -> {
                boolean aConn = !connectionStore.listByProvider(a.id).isEmpty();
                boolean bConn = !connectionStore.listByProvider(b.id).isEmpty();
                if (aConn == bConn) return a.title.compareToIgnoreCase(b.title);
                return aConn ? -1 : 1;
            });
        }

        TextView titleView = findViewById(R.id.providers_title);
        TextView introView = findViewById(R.id.providers_intro);
        if (titleView != null) {
            if (activeFilter != FilterCategory.ALL || !searchQuery.isEmpty()) {
                titleView.setText("Providers (" + filtered.size() + ")");
            } else {
                titleView.setText(R.string.providers);
            }
        }
        if (introView != null) {
            if (activeFilter != FilterCategory.ALL || !searchQuery.isEmpty()) {
                introView.setText("Showing " + filtered.size() + " matching provider" + (filtered.size() == 1 ? "" : "s"));
            } else {
                introView.setText("Connect subscription accounts via official CLI bridges, direct API keys, or local runtimes.");
            }
        }

        if (filtered.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            list.setVisibility(View.GONE);
            return;
        }

        emptyState.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);

        for (int i = 0; i < filtered.size(); i++) {
            ProviderDescriptor desc = filtered.get(i);
            list.addView(buildProviderRow(desc, density));

            if (i < filtered.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(getColor(R.color.ocean_border));
                LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density));
                dLp.setMarginStart((int) (52 * density));
                list.addView(divider, dLp);
            }
        }
    }

    private View buildProviderRow(ProviderDescriptor desc, float density) {
        List<ProviderConnection> connections = connectionStore.listByProvider(desc.id);
        ProviderConnection activeConn = connections.isEmpty() ? null : connections.get(0);
        boolean isConnected = activeConn != null && activeConn.status == ConnectionStatus.CONNECTED;

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (8 * density);
        int padV = (int) (12 * density);
        row.setPadding(padH, padV, padH, padV);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        row.setMinimumHeight((int) (56 * density));

        // Monochrome icon container
        FrameLayout iconFrame = new FrameLayout(this);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.RECTANGLE);
        iconBg.setCornerRadius(8 * density);
        iconBg.setColor(getColor(R.color.ocean_surface_2));
        iconFrame.setBackground(iconBg);
        int frameSize = (int) (40 * density);
        LinearLayout.LayoutParams fLp = new LinearLayout.LayoutParams(frameSize, frameSize);
        fLp.setMarginEnd((int) (12 * density));
        iconFrame.setLayoutParams(fLp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(desc.iconRes);
        icon.setColorFilter(getColor(R.color.ocean_ink));
        FrameLayout.LayoutParams iLp = new FrameLayout.LayoutParams((int) (22 * density), (int) (22 * density));
        iLp.gravity = Gravity.CENTER;
        iconFrame.addView(icon, iLp);
        row.addView(iconFrame);

        // Center content
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        details.setLayoutParams(dLp);

        // Top line: Name + Status badge
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(desc.title);
        title.setTextColor(getColor(R.color.ocean_ink));
        title.setTextSize(15f);
        title.setTypeface(null, Typeface.BOLD);
        titleRow.addView(title);

        View badge = createStatusBadge(desc, activeConn, density);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMarginStart((int) (8 * density));
        titleRow.addView(badge, bLp);
        details.addView(titleRow);

        // Middle line: Strategy + Subtitle
        TextView subtitle = new TextView(this);
        String strategyTag = activeConn != null ? activeConn.strategy.displayName : defaultStrategyLabel(desc);
        subtitle.setText(strategyTag + " · " + desc.subtitle);
        subtitle.setTextColor(getColor(R.color.ocean_muted));
        subtitle.setTextSize(12f);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle.setPadding(0, (int) (2 * density), 0, 0);
        details.addView(subtitle);

        // Bottom line: Quota indicator
        TextView quotaView = new TextView(this);
        if (isConnected) {
            QuotaSnapshot qs = activeConn.quota != null ? activeConn.quota : quotaService.inspect(activeConn);
            quotaView.setText(qs.formatSummary());
        } else {
            quotaView.setText("Not connected · Tap to setup");
        }
        quotaView.setTextColor(getColor(R.color.ocean_ink_55));
        quotaView.setTextSize(11f);
        quotaView.setPadding(0, (int) (2 * density), 0, 0);
        details.addView(quotaView);

        row.addView(details);

        // Chevron
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setColorFilter(getColor(R.color.ocean_muted));
        int cSize = (int) (20 * density);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(cSize, cSize);
        cLp.setMarginStart((int) (8 * density));
        row.addView(chevron, cLp);

        row.setOnClickListener(v -> showProviderBottomSheet(desc));
        return row;
    }

    private View createStatusBadge(ProviderDescriptor desc, ProviderConnection conn, float density) {
        TextView badge = new TextView(this);
        badge.setTextSize(9f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        int padH = (int) (6 * density);
        int padV = (int) (2 * density);
        badge.setPadding(padH, padV, padH, padV);

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(4 * density);

        if (conn != null && conn.status == ConnectionStatus.CONNECTED) {
            badge.setText("CONNECTED");
            badge.setTextColor(getColor(R.color.ocean_paper));
            gd.setColor(getColor(R.color.ocean_ink));
        } else if (conn != null && conn.status == ConnectionStatus.VERIFYING) {
            badge.setText("VERIFYING");
            badge.setTextColor(getColor(R.color.ocean_paper));
            gd.setColor(0xFF2563EB);
        } else if (conn != null && (conn.status == ConnectionStatus.REAUTH_REQUIRED || conn.status == ConnectionStatus.NEEDS_REAUTH)) {
            badge.setText("RE-AUTH");
            badge.setTextColor(getColor(R.color.ocean_paper));
            gd.setColor(getColor(R.color.ocean_error));
        } else if (conn != null && conn.status == ConnectionStatus.RATE_LIMITED) {
            badge.setText("RATE LIMITED");
            badge.setTextColor(getColor(R.color.ocean_paper));
            gd.setColor(0xFFD97706);
        } else if (conn != null && conn.status == ConnectionStatus.CONFIG_ERROR) {
            badge.setText("CONFIG ERROR");
            badge.setTextColor(getColor(R.color.ocean_paper));
            gd.setColor(getColor(R.color.ocean_error));
        } else if (conn != null && conn.status == ConnectionStatus.ERROR) {
            badge.setText("ERROR");
            badge.setTextColor(getColor(R.color.ocean_paper));
            gd.setColor(getColor(R.color.ocean_error));
        } else if (conn != null && conn.status == ConnectionStatus.OFFLINE) {
            badge.setText("OFFLINE");
            badge.setTextColor(getColor(R.color.ocean_muted));
            gd.setColor(getColor(R.color.ocean_surface_2));
            gd.setStroke((int) (1 * density), getColor(R.color.ocean_border));
        } else {
            // Unconfigured or disconnected - never display OFFLINE merely because not configured
            OfficialCliAdapter adapter = desc != null ? resolveCliAdapter(desc.id) : null;
            if (adapter != null) {
                if (!adapter.isInstalled()) {
                    badge.setText("NOT INSTALLED");
                } else if (!adapter.isSessionAuthenticated()) {
                    badge.setText("NEEDS LOGIN");
                } else {
                    badge.setText("NOT CONNECTED");
                }
            } else {
                badge.setText("NOT CONNECTED");
            }
            badge.setTextColor(getColor(R.color.ocean_muted));
            gd.setColor(getColor(R.color.ocean_surface_2));
            gd.setStroke((int) (1 * density), getColor(R.color.ocean_border));
        }
        badge.setBackground(gd);
        return badge;
    }

    private String defaultStrategyLabel(ProviderDescriptor desc) {
        if (desc.supports(AuthStrategy.OFFICIAL_CLI)) return "CLI Bridge";
        if (desc.supports(AuthStrategy.LOCAL)) return "Local Runtime";
        return "API Key";
    }

    // ==========================================
    // Bottom Sheet: Provider Detail & Actions
    // ==========================================

    private void showProviderBottomSheet(ProviderDescriptor desc) {
        float density = getResources().getDisplayMetrics().density;
        List<ProviderConnection> connections = connectionStore.listByProvider(desc.id);
        ProviderConnection activeConn = connections.isEmpty() ? null : connections.get(0);
        boolean isConnected = activeConn != null && activeConn.status == ConnectionStatus.CONNECTED;

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundResource(R.drawable.bottom_sheet_background);
        int pad = (int) (20 * density);
        sheet.setPadding(pad, (int) (10 * density), pad, (int) (28 * density));

        // Drag handle
        View handle = new View(this);
        handle.setBackgroundResource(R.drawable.bottom_sheet_handle);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams((int) (36 * density), (int) (4 * density));
        hLp.gravity = Gravity.CENTER_HORIZONTAL;
        hLp.bottomMargin = (int) (16 * density);
        sheet.addView(handle, hLp);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(desc.iconRes);
        icon.setColorFilter(getColor(R.color.ocean_ink));
        header.addView(icon, new LinearLayout.LayoutParams((int) (28 * density), (int) (28 * density)));

        TextView title = new TextView(this);
        title.setText(desc.title);
        title.setTextColor(getColor(R.color.ocean_ink));
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding((int) (10 * density), 0, (int) (8 * density), 0);
        header.addView(title);

        header.addView(createStatusBadge(desc, activeConn, density));
        sheet.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText(desc.subtitle);
        subtitle.setTextColor(getColor(R.color.ocean_muted));
        subtitle.setTextSize(13f);
        subtitle.setPadding(0, (int) (4 * density), 0, (int) (16 * density));
        sheet.addView(subtitle);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(sheet);

        if (isConnected) {
            // Active connection details
            sheet.addView(buildConnectionInfoBlock(desc, activeConn, density));
            sheet.addView(buildQuotaBlock(activeConn, density));
            sheet.addView(buildModelsBlock(desc, activeConn, density));

            // Actions
            Button testBtn = createPrimaryButton("Test & Verify Connection", density);
            testBtn.setOnClickListener(v -> testConnection(desc, activeConn));
            sheet.addView(testBtn);

            Button editBtn = createSecondaryButton("Configure Settings", density);
            editBtn.setOnClickListener(v -> {
                dialog.dismiss();
                if (activeConn.strategy == AuthStrategy.API_KEY) {
                    showApiKeyDialog(desc, activeConn);
                } else {
                    Toast.makeText(this, "Managed by official account / CLI bridge", Toast.LENGTH_SHORT).show();
                }
            });
            sheet.addView(editBtn);

            Button disconnectBtn = createSecondaryButton("Disconnect & Erase Credentials", density);
            disconnectBtn.setOnClickListener(v -> {
                dialog.dismiss();
                confirmDisconnect(desc, activeConn);
            });
            sheet.addView(disconnectBtn);

        } else {
            // Connect choices
            TextView chooseLabel = new TextView(this);
            chooseLabel.setText("CHOOSE CONNECTION STRATEGY");
            chooseLabel.setTextColor(getColor(R.color.ocean_muted));
            chooseLabel.setTextSize(11f);
            chooseLabel.setTypeface(null, Typeface.BOLD);
            chooseLabel.setPadding(0, 0, 0, (int) (10 * density));
            sheet.addView(chooseLabel);

            Button directBtn = createPrimaryButton("Direct Connect", density);
            directBtn.setOnClickListener(v -> {
                dialog.dismiss();
                showDirectConnectFlow(desc);
            });
            sheet.addView(directBtn);

            if (desc.supports(AuthStrategy.OFFICIAL_CLI)) {
                Button cliBtn = createSecondaryButton("Connect via " + desc.officialCliName + " CLI Bridge", density);
                cliBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    connectViaOfficialCli(desc);
                });
                sheet.addView(cliBtn);
            }

            if (desc.supports(AuthStrategy.API_KEY)) {
                Button apiBtn = createSecondaryButton("Connect with API Key", density);
                apiBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    showApiKeyDialog(desc, null);
                });
                sheet.addView(apiBtn);
            }
        }

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams wlp = window.getAttributes();
            wlp.windowAnimations = android.R.style.Animation_InputMethod;
            window.setAttributes(wlp);
        }
        dialog.show();
    }

    private View buildConnectionInfoBlock(ProviderDescriptor desc, ProviderConnection conn, float density) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundResource(R.drawable.auth_field_background);
        int pad = (int) (12 * density);
        block.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * density);
        block.setLayoutParams(lp);

        block.addView(createMetaRow("Account / Session", conn.displayAccount, density));
        block.addView(createMetaRow("Auth Strategy", conn.strategy.displayName, density));
        block.addView(createMetaRow("Active Model", conn.selectedModel, density));
        if (conn.strategy == AuthStrategy.OFFICIAL_CLI) {
            OfficialCliAdapter adapter = resolveCliAdapter(desc.id);
            if (adapter != null) {
                block.addView(createMetaRow("CLI Executable", desc.officialCliName, density));
                block.addView(createMetaRow("CLI Version", adapter.getVersion(), density));
            }
        }
        if (conn.baseUrl != null && !conn.baseUrl.isEmpty()) {
            block.addView(createMetaRow("Endpoint URL", conn.baseUrl, density));
        }
        String lastVerified = conn.lastValidatedAtEpochMs > 0
                ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(new java.util.Date(conn.lastValidatedAtEpochMs))
                : "Not yet verified";
        block.addView(createMetaRow("Last Verified", lastVerified, density));
        return block;
    }

    private View buildQuotaBlock(ProviderConnection conn, float density) {
        QuotaSnapshot qs = conn.quota != null ? conn.quota : quotaService.inspect(conn);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundResource(R.drawable.auth_field_background);
        int pad = (int) (12 * density);
        block.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (12 * density);
        block.setLayoutParams(lp);

        TextView label = new TextView(this);
        label.setText("QUOTA & ENTITLEMENT");
        label.setTextColor(getColor(R.color.ocean_muted));
        label.setTextSize(11f);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, 0, 0, (int) (4 * density));
        block.addView(label);

        block.addView(createMetaRow("Plan Tier", qs.planTier, density));
        block.addView(createMetaRow("Confidence Level", qs.confidence.name(), density));
        block.addView(createMetaRow("Usage Details", qs.formatSummary(), density));
        block.addView(createMetaRow("Source", qs.source, density));
        return block;
    }

    private View buildModelsBlock(ProviderDescriptor desc, ProviderConnection conn, float density) {
        List<ModelDescriptor> models = modelCatalogService.discoverModels(conn);
        if (models.isEmpty()) return new View(this);

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setBackgroundResource(R.drawable.auth_field_background);
        int pad = (int) (12 * density);
        block.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (16 * density);
        block.setLayoutParams(lp);

        TextView label = new TextView(this);
        label.setText("DISCOVERED MODELS (" + models.size() + ")");
        label.setTextColor(getColor(R.color.ocean_muted));
        label.setTextSize(11f);
        label.setTypeface(null, Typeface.BOLD);
        label.setPadding(0, 0, 0, (int) (6 * density));
        block.addView(label);

        for (ModelDescriptor m : models) {
            LinearLayout mRow = new LinearLayout(this);
            mRow.setOrientation(LinearLayout.HORIZONTAL);
            mRow.setGravity(Gravity.CENTER_VERTICAL);
            mRow.setPadding(0, (int) (4 * density), 0, (int) (4 * density));

            TextView mTitle = new TextView(this);
            mTitle.setText(m.displayName);
            mTitle.setTextColor(getColor(R.color.ocean_ink));
            mTitle.setTextSize(13f);
            mTitle.setTypeface(null, m.modelId.equals(conn.selectedModel) ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mRow.addView(mTitle, tLp);

            if (m.modelId.equals(conn.selectedModel)) {
                TextView activeTag = new TextView(this);
                activeTag.setText("ACTIVE");
                activeTag.setTextColor(getColor(R.color.ocean_ink));
                activeTag.setTextSize(10f);
                activeTag.setTypeface(null, Typeface.BOLD);
                mRow.addView(activeTag);
            }
            block.addView(mRow);
        }
        return block;
    }

    private View createMetaRow(String caption, String value, float density) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, (int) (2 * density), 0, (int) (2 * density));

        TextView cap = new TextView(this);
        cap.setText(caption + ":");
        cap.setTextColor(getColor(R.color.ocean_muted));
        cap.setTextSize(12f);
        cap.setWidth((int) (130 * density));
        row.addView(cap);

        TextView val = new TextView(this);
        val.setText(value != null && !value.isEmpty() ? value : "—");
        val.setTextColor(getColor(R.color.ocean_ink));
        val.setTextSize(12f);
        val.setTypeface(null, Typeface.BOLD);
        row.addView(val);

        return row;
    }

    private Button createPrimaryButton(String text, float density) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(getColor(R.color.ocean_background));
        b.setBackgroundResource(R.drawable.primary_button_background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * density));
        lp.bottomMargin = (int) (8 * density);
        b.setLayoutParams(lp);
        return b;
    }

    private Button createSecondaryButton(String text, float density) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(getColor(R.color.ocean_ink));
        b.setBackgroundResource(R.drawable.button_secondary);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (48 * density));
        lp.bottomMargin = (int) (8 * density);
        b.setLayoutParams(lp);
        return b;
    }

    // ==========================================
    // Strategy Execution: CLI Bridge
    // ==========================================

    private void connectViaOfficialCli(ProviderDescriptor desc) {
        OfficialCliAdapter adapter = resolveCliAdapter(desc.id);
        if (adapter == null) {
            Toast.makeText(this, "No CLI adapter available for " + desc.title, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!adapter.isInstalled()) {
            OceanModal.create(this)
                    .setTitle("CLI Not Found")
                    .setExplanation("The official CLI ('" + desc.officialCliName + "') was not detected in PATH or the app tools prefix.")
                    .setDetailsText("Install via Ocean Packages:\n  ocean-pkg install " + desc.officialCliName)
                    .setPositiveButton("Use API Key", v -> showApiKeyDialog(desc, null))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        boolean authenticated = false;
        if (adapter instanceof AntigravityCliAdapter) authenticated = ((AntigravityCliAdapter) adapter).isSessionAuthenticated();
        else if (adapter instanceof KimiCliAdapter) authenticated = ((KimiCliAdapter) adapter).isSessionAuthenticated();
        else if (adapter instanceof ClaudeCodeCliAdapter) authenticated = ((ClaudeCodeCliAdapter) adapter).isSessionAuthenticated();
        else if (adapter instanceof CodexCliAdapter) authenticated = ((CodexCliAdapter) adapter).isSessionAuthenticated();

        if (!authenticated) {
            String loginCmd = desc.officialCliName + " auth login";
            if ("kimi".equals(desc.id)) loginCmd = "kimi login";
            else if ("claude".equals(desc.id)) loginCmd = "claude login";
            else if ("codex".equals(desc.id)) loginCmd = "codex login";

            OceanModal.create(this)
                    .setTitle("Authentication Required")
                    .setExplanation("The official '" + desc.officialCliName + "' CLI is installed, but no active account session was detected.")
                    .setDetailsText("Open Ocean Terminal and run:\n  " + loginCmd + "\nThen tap Verify once login succeeds.")
                    .setPositiveButton("Verify & Connect", v -> connectViaOfficialCli(desc))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Create provisional connection with VERIFYING state (Directive 2 §3.1, §3.2)
        ProviderConnection provisional = new ProviderConnection(
                UUID.randomUUID().toString(),
                desc.id,
                desc.officialCliName + " CLI session",
                AuthStrategy.OFFICIAL_CLI,
                ConnectionStatus.VERIFYING,
                desc.defaultBaseUrl,
                desc.defaultModel,
                null,
                desc.officialCliName + "_active",
                null,
                null,
                null,
                null,
                System.currentTimeMillis()
        );
        QuotaSnapshot qs = quotaService.inspect(provisional);
        ProviderConnection connWithQuota = provisional.withQuota(qs);
        connectionStore.save(connWithQuota);
        renderProviders();

        Toast.makeText(this, "Verifying " + desc.title + "…", Toast.LENGTH_SHORT).show();
        agentRunner.testProviderConnection(connWithQuota, new OceanAgentRunner.ConnectionCallback() {
            @Override public void onSuccess() {
                ProviderConnection verified = new ProviderConnection(
                        connWithQuota.id, connWithQuota.providerId, connWithQuota.displayAccount,
                        connWithQuota.strategy, ConnectionStatus.CONNECTED,
                        connWithQuota.baseUrl, connWithQuota.selectedModel,
                        connWithQuota.credentialRef, connWithQuota.cliSessionRef,
                        connWithQuota.scopes, connWithQuota.expiresAtEpochMs,
                        connWithQuota.quota, connWithQuota.models,
                        System.currentTimeMillis()
                );
                connectionStore.save(verified);
                runOnUiThread(() -> {
                    Toast.makeText(ProvidersConnectActivity.this, desc.title + " verified and connected", Toast.LENGTH_SHORT).show();
                    renderProviders();
                });
            }

            @Override public void onFailure(String error) {
                ProviderConnection failed = new ProviderConnection(
                        connWithQuota.id, connWithQuota.providerId, connWithQuota.displayAccount,
                        connWithQuota.strategy, ConnectionStatus.NEEDS_REAUTH,
                        connWithQuota.baseUrl, connWithQuota.selectedModel,
                        connWithQuota.credentialRef, connWithQuota.cliSessionRef,
                        connWithQuota.scopes, connWithQuota.expiresAtEpochMs,
                        connWithQuota.quota, connWithQuota.models,
                        System.currentTimeMillis()
                );
                connectionStore.save(failed);
                runOnUiThread(() -> {
                    Toast.makeText(ProvidersConnectActivity.this, "Verification failed: " + error, Toast.LENGTH_LONG).show();
                    renderProviders();
                });
            }
        });
    }

    private OfficialCliAdapter resolveCliAdapter(String providerId) {
        if (ProviderRegistry.ID_ANTIGRAVITY.equals(providerId)) return antigravityCli;
        if (ProviderRegistry.ID_KIMI.equals(providerId)) return kimiCli;
        if (ProviderRegistry.ID_ANTHROPIC.equals(providerId)) return claudeCli;
        if (ProviderRegistry.ID_OPENAI.equals(providerId)) return codexCli;
        return null;
    }

    // ==========================================
    // Strategy Execution: API Key & Local
    // ==========================================

    private void showApiKeyDialog(ProviderDescriptor desc, ProviderConnection existing) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * density);
        layout.setPadding(pad, pad, pad, pad);

        String currentModel = existing != null ? existing.selectedModel : desc.defaultModel;
        String currentBase = existing != null ? existing.baseUrl : desc.defaultBaseUrl;

        Field modelField = field("Model ID", currentModel);
        layout.addView(modelField.container);

        Field keyField = field("API Key", "");
        keyField.input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.input.setHint(desc.credentialHint);
        layout.addView(keyField.container);

        Field baseField = field("API Base URL", currentBase);
        layout.addView(baseField.container);

        OceanModal.create(this)
                .setTitle(desc.title + " Configuration")
                .setExplanation("Enter your API key and configuration settings. Secrets are encrypted locally in CredentialVault.")
                .setCustomView(layout)
                .setPositiveButton("Save & Connect", v -> {
                    String model = modelField.input.getText().toString().trim();
                    String key = keyField.input.getText().toString().trim();
                    String base = baseField.input.getText().toString().trim();

                    // Preflight validation
                    AuthPreflight.PreflightResult preflight = AuthPreflight.validateApiKeyConfig(desc.id, model, key, base);
                    if (!preflight.passed) {
                        Toast.makeText(this, preflight.errorMessage, Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Save secret into CredentialVault
                    String credentialRef = credentialVault.store(key);
                    String connId = existing != null ? existing.id : UUID.randomUUID().toString();

                    ProviderConnection conn = new ProviderConnection(
                            connId,
                            desc.id,
                            maskKey(key),
                            AuthStrategy.API_KEY,
                            ConnectionStatus.CONNECTED,
                            base,
                            model,
                            credentialRef,
                            null,
                            null,
                            null,
                            QuotaSnapshot.unknown("Pay-as-you-go API", "api-header"),
                            null,
                            System.currentTimeMillis()
                    );
                    connectionStore.save(conn);

                    // Sync to BYOK manager for backward compatibility
                    try {
                        String byokId = desc.id;
                        if (!OceanByokManager.PROVIDER_GOOGLE.equals(byokId)
                                && !OceanByokManager.PROVIDER_ANTHROPIC.equals(byokId)
                                && !OceanByokManager.PROVIDER_OPENAI.equals(byokId)) {
                            byokId = OceanByokManager.PROVIDER_CUSTOM;
                        }
                        byokManager.saveConfig(byokId, model, key, base);
                    } catch (Exception ignored) {}

                    Toast.makeText(this, desc.title + " configured", Toast.LENGTH_SHORT).show();
                    renderProviders();

                    // Background test
                    testConnection(desc, conn);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDirectConnectFlow(ProviderDescriptor desc) {
        authOrchestrator.startDirectConnect(desc, new AuthOrchestrator.AuthFlowCallback() {
            @Override
            public void onRiskWarningRequired(String title, String message, Runnable onProceed) {
                runOnUiThread(() -> OceanModal.create(ProvidersConnectActivity.this)
                        .setTitle(title)
                        .setExplanation(message)
                        .setPositiveButton("Proceed Anyway", v -> onProceed.run())
                        .setNegativeButton("Cancel", null)
                        .show());
            }

            @Override
            public void onDeviceCodeReceived(String userCode, String verificationUrl, int expiresInSeconds) {
                runOnUiThread(() -> showDeviceCodeDialog(desc, userCode, verificationUrl, expiresInSeconds));
            }

            @Override
            public void onBrowserLaunchRequired(String authUrl) {
                runOnUiThread(() -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
                    } catch (Exception e) {
                        Toast.makeText(ProvidersConnectActivity.this, "Could not open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onSuccess(ProviderConnection connection) {
                runOnUiThread(() -> {
                    Toast.makeText(ProvidersConnectActivity.this, desc.title + " connected successfully", Toast.LENGTH_SHORT).show();
                    renderProviders();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    OceanModal.Builder builder = OceanModal.create(ProvidersConnectActivity.this)
                            .setTitle(desc.title + " · Direct Connect")
                            .setExplanation("Direct connect could not be completed.")
                            .setInlineError(error)
                            .setDetailsText(error)
                            .setNegativeButton("Dismiss", null);
                    if (error != null && (error.contains("Client ID") || error.contains("OAuth 2.0"))) {
                        builder.setPositiveButton("Set Client ID", v -> showConfigureClientIdDialog(desc));
                    }
                    builder.show();
                });
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingOAuthIntent(intent);
    }

    private void handleIncomingOAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        boolean isOceanCallback = "ocean".equalsIgnoreCase(data.getScheme()) && "auth".equalsIgnoreCase(data.getHost());
        boolean isStudioCallback = "studio.ocean.app".equalsIgnoreCase(data.getScheme()) && "oauth".equalsIgnoreCase(data.getHost());
        if (!isOceanCallback && !isStudioCallback) return;

        Toast.makeText(this, "Verifying account authorization…", Toast.LENGTH_SHORT).show();
        authOrchestrator.handleCallback(data, new AuthOrchestrator.AuthFlowCallback() {
            @Override public void onRiskWarningRequired(String title, String message, Runnable onProceed) {}
            @Override public void onDeviceCodeReceived(String userCode, String verificationUrl, int expiresInSeconds) {}
            @Override public void onBrowserLaunchRequired(String authUrl) {}
            @Override public void onSuccess(ProviderConnection connection) {
                runOnUiThread(() -> {
                    Toast.makeText(ProvidersConnectActivity.this, "Connected account successfully verified", Toast.LENGTH_LONG).show();
                    renderProviders();
                });
            }
            @Override public void onFailure(String error) {
                runOnUiThread(() -> {
                    OceanModal.create(ProvidersConnectActivity.this)
                            .setTitle("Authentication Failed")
                            .setExplanation("Could not complete authorization handshake.")
                            .setInlineError(error)
                            .setPositiveButton("Dismiss", null)
                            .show();
                });
            }
        });
    }

    private void showConfigureClientIdDialog(ProviderDescriptor desc) {
        EditText input = new EditText(this);
        input.setHint("Enter " + desc.title + " OAuth Client ID");
        String existing = credentialVault.retrieve("oauth_client_id_" + desc.id);
        if (existing != null) input.setText(existing);
        input.setSingleLine(true);
        input.setTextColor(getColor(R.color.ocean_ink));
        input.setBackgroundResource(R.drawable.auth_field_background);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        OceanModal.create(this)
                .setTitle("Configure OAuth Client ID")
                .setExplanation("Provide your registered OAuth 2.0 Client ID for " + desc.title + " (RFC 8252 native client). Secrets are never required or embedded.")
                .setCustomView(input)
                .setPositiveButton("Save & Connect", v -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        credentialVault.store("oauth_client_id_" + desc.id, val);
                        showDirectConnectFlow(desc);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeviceCodeDialog(ProviderDescriptor desc, String userCode, String verificationUrl, int expiresInSeconds) {
        Dialog[] dialogHolder = new Dialog[1];
        AtomicBoolean canceled = new AtomicBoolean(false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView info = new TextView(this);
        info.setText("1. Open the verification page in your external browser.\n2. Confirm the authorization code shown below:\n");
        info.setTextColor(getColor(R.color.ocean_text_primary));
        info.setTextSize(14f);
        layout.addView(info);

        TextView codeView = new TextView(this);
        codeView.setText(userCode);
        codeView.setTextSize(24f);
        codeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        codeView.setTextColor(getColor(R.color.ocean_ink));
        codeView.setGravity(Gravity.CENTER);
        codeView.setPadding(0, pad / 2, 0, pad);
        layout.addView(codeView);

        ProgressBar spinner = new ProgressBar(this);
        layout.addView(spinner);

        TextView pollingText = new TextView(this);
        pollingText.setText("Waiting for provider authorization…");
        pollingText.setTextColor(getColor(R.color.ocean_muted));
        pollingText.setTextSize(12f);
        pollingText.setGravity(Gravity.CENTER);
        layout.addView(pollingText);

        dialogHolder[0] = OceanModal.create(this)
                .setTitle(desc.title + " · Device Authorization")
                .setExplanation("Confirm authorization in external browser.")
                .setCustomView(layout)
                .setPositiveButton("Open Browser", v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(verificationUrl)));
                    } catch (Exception e) {
                        Toast.makeText(this, "Could not open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", v -> {
                    canceled.set(true);
                })
                .show();

        dialogHolder[0].setOnDismissListener(d -> canceled.set(true));

        new Thread(() -> {
            int maxAttempts = Math.max(1, expiresInSeconds / 5);
            for (int i = 0; i < maxAttempts; i++) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    return;
                }
                if (canceled.get()) return;
            }
            if (!canceled.get()) {
                runOnUiThread(() -> {
                    if (dialogHolder[0] != null && dialogHolder[0].isShowing()) {
                        dialogHolder[0].dismiss();
                        Toast.makeText(ProvidersConnectActivity.this, "Device code expired. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void confirmDisconnect(ProviderDescriptor desc, ProviderConnection conn) {
        OceanModal.create(this)
                .setTitle("Disconnect " + desc.title)
                .setExplanation("Are you sure you want to disconnect? All local credentials and tokens will be permanently erased.")
                .setPositiveButton("Disconnect", v -> {
                    connectionStore.delete(conn.id);
                    if (conn.credentialRef != null) {
                        credentialVault.delete(conn.credentialRef);
                    }
                    Toast.makeText(this, desc.title + " disconnected", Toast.LENGTH_SHORT).show();
                    renderProviders();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void testConnection(ProviderDescriptor desc, ProviderConnection conn) {
        Toast.makeText(this, "Testing connection to " + desc.title + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            if (conn != null) {
                agentRunner.testProviderConnection(conn, new OceanAgentRunner.ConnectionCallback() {
                    @Override public void onSuccess() {
                        runOnUiThread(() -> Toast.makeText(ProvidersConnectActivity.this,
                                desc.title + " connection verified", Toast.LENGTH_SHORT).show());
                    }
                    @Override public void onFailure(String error) {
                        runOnUiThread(() -> Toast.makeText(ProvidersConnectActivity.this,
                                "Connection failed: " + error, Toast.LENGTH_LONG).show());
                    }
                });
                return;
            }

            // Fallback for legacy BYOK connections
            agentRunner.testConnection(new OceanAgentRunner.ConnectionCallback() {
                @Override public void onSuccess() {
                    runOnUiThread(() -> Toast.makeText(ProvidersConnectActivity.this,
                            desc.title + " connection verified", Toast.LENGTH_SHORT).show());
                }
                @Override public void onFailure(String error) {
                    runOnUiThread(() -> Toast.makeText(ProvidersConnectActivity.this,
                            "Connection failed: " + error, Toast.LENGTH_LONG).show());
                }
            });
        }).start();
    }

    // ==========================================
    // UI Helpers
    // ==========================================

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
        caption.setPadding(0, (int) (10 * density), 0, (int) (4 * density));

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

    private static String maskKey(String key) {
        if (key == null || key.length() <= 8) return "••••••••";
        return key.substring(0, 4) + "••••" + key.substring(key.length() - 4);
    }
}
