package studio.ocean.app.models.local;

import android.content.DialogInterface;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import studio.ocean.app.OceanModal;
import studio.ocean.app.R;

/**
 * Dedicated Product Page for On-Device Local Models (Directive 3 §8).
 * Completely decoupled from remote Provider Hub quota / BYOK models.
 */
public final class LocalModelsActivity extends AppCompatActivity {

    public enum ModelFilter {
        ALL("All"),
        INSTALLED("Installed"),
        AVAILABLE("Available"),
        DOWNLOADING("Downloading");

        public final String label;
        ModelFilter(String label) { this.label = label; }
    }

    private LocalModelManager manager;
    private ModelFilter activeFilter = ModelFilter.ALL;
    private TextView ramStatusView;
    private LinearLayout filtersContainer;
    private LinearLayout listContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_models);

        manager = LocalModelManager.getInstance(this);

        ramStatusView = findViewById(R.id.local_models_ram_status);
        filtersContainer = findViewById(R.id.local_models_filters);
        listContainer = findViewById(R.id.local_models_list);

        findViewById(R.id.local_models_back).setOnClickListener(v -> finish());
        findViewById(R.id.local_models_refresh).setOnClickListener(v -> refresh());

        updateRamStatus();
        renderFilters();
        renderModels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        manager.syncFromDisk();
        updateRamStatus();
        renderModels();
    }

    private void refresh() {
        manager.syncFromDisk();
        updateRamStatus();
        renderModels();
        Toast.makeText(this, "Refreshed local models", Toast.LENGTH_SHORT).show();
    }

    private void updateRamStatus() {
        long avail = manager.getAvailableDeviceRamMb();
        long total = manager.getTotalDeviceRamMb();
        String loaded = manager.getLoadedModelId();
        String loadedStr = loaded != null ? " · Loaded: " + loaded : " · No model loaded";
        ramStatusView.setText("Device RAM: " + avail + " MB free / " + total + " MB total" + loadedStr);
    }

    private void renderFilters() {
        filtersContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        for (ModelFilter filter : ModelFilter.values()) {
            TextView pill = new TextView(this);
            pill.setText(filter.label);
            pill.setTextSize(13f);
            pill.setGravity(Gravity.CENTER);
            int padH = (int) (14 * density);
            int padV = (int) (6 * density);
            pill.setPadding(padH, padV, padH, padV);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, (int) (32 * density));
            lp.setMarginEnd((int) (8 * density));
            pill.setLayoutParams(lp);

            boolean isActive = filter == activeFilter;
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
                activeFilter = filter;
                renderFilters();
                renderModels();
            });

            filtersContainer.addView(pill);
        }
    }

    private void renderModels() {
        listContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;

        List<LocalModel> models = manager.listModels();
        int shownCount = 0;

        for (LocalModel model : models) {
            // Apply filter
            if (activeFilter == ModelFilter.INSTALLED && model.state != LocalModel.State.INSTALLED && !model.isConnected()) {
                continue;
            }
            if (activeFilter == ModelFilter.AVAILABLE && model.state != LocalModel.State.AVAILABLE) {
                continue;
            }
            if (activeFilter == ModelFilter.DOWNLOADING && model.state != LocalModel.State.DOWNLOADING) {
                continue;
            }

            shownCount++;
            listContainer.addView(buildModelCard(model, density));
        }

        if (shownCount == 0) {
            TextView empty = new TextView(this);
            empty.setText("No local models match this filter.");
            empty.setTextColor(getColor(R.color.ocean_text_tertiary));
            empty.setTextSize(14f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, (int) (40 * density), 0, (int) (40 * density));
            listContainer.addView(empty);
        }
    }

    private View buildModelCard(LocalModel model, float density) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.composer_background);
        int pad = (int) (16 * density);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = (int) (12 * density);
        card.setLayoutParams(clp);

        // Header row: Title + Status Badge
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText(model.displayName);
        title.setTextColor(getColor(R.color.ocean_text_primary));
        title.setTextSize(15f);
        title.setTypeface(null, Typeface.BOLD);
        topRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setText(model.state.label);
        badge.setTextSize(11f);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setPadding((int) (8 * density), (int) (3 * density), (int) (8 * density), (int) (3 * density));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * density);
        if (model.isConnected()) {
            bg.setColor(getColor(R.color.ocean_ink));
            badge.setTextColor(getColor(R.color.ocean_background));
        } else if (model.state == LocalModel.State.INSTALLED) {
            bg.setColor(0xFFE5E7EB);
            badge.setTextColor(0xFF111827);
        } else if (model.state == LocalModel.State.DOWNLOADING) {
            bg.setColor(0xFFE0E7FF);
            badge.setTextColor(0xFF3730A3);
            badge.setText("Downloading " + model.downloadProgress + "%");
        } else if (model.state == LocalModel.State.INCOMPATIBLE) {
            bg.setColor(0xFFFEE2E2);
            badge.setTextColor(0xFF991B1B);
        } else {
            bg.setColor(getColor(R.color.ocean_surface));
            bg.setStroke((int) (1 * density), getColor(R.color.ocean_border));
            badge.setTextColor(getColor(R.color.ocean_text_secondary));
        }
        badge.setBackground(bg);
        topRow.addView(badge);
        card.addView(topRow);

        // Metadata row
        long sizeMb = model.sizeBytes / (1024 * 1024);
        String metaText = model.quantization + " · " + sizeMb + " MB · Min RAM: " + model.minRamMb + " MB · " + model.license;
        TextView meta = new TextView(this);
        meta.setText(metaText);
        meta.setTextColor(getColor(R.color.ocean_text_secondary));
        meta.setTextSize(12f);
        meta.setPadding(0, (int) (4 * density), 0, (int) (8 * density));
        card.addView(meta);

        // Download progress bar if downloading
        if (model.state == LocalModel.State.DOWNLOADING) {
            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(model.downloadProgress);
            LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (6 * density));
            pblp.topMargin = (int) (4 * density);
            pblp.bottomMargin = (int) (8 * density);
            card.addView(pb, pblp);
        }

        // Actions row
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, (int) (4 * density), 0, 0);

        if (model.state == LocalModel.State.AVAILABLE || model.state == LocalModel.State.ERROR) {
            Button installBtn = createButton("Install (" + sizeMb + " MB)", true, density);
            installBtn.setOnClickListener(v -> startDownload(model));
            actions.addView(installBtn);
        } else if (model.state == LocalModel.State.DOWNLOADING) {
            Button cancelBtn = createButton("Cancel", false, density);
            cancelBtn.setOnClickListener(v -> {
                manager.cancelDownload(model.id);
                renderModels();
            });
            actions.addView(cancelBtn);
        } else if (model.state == LocalModel.State.INSTALLED) {
            Button connectBtn = createButton("CONNECT", true, density);
            connectBtn.setOnClickListener(v -> {
                connectBtn.setEnabled(false);
                connectBtn.setText("CONNECTING…");
                Toast.makeText(this, "Starting and verifying local inference…", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    boolean connected = manager.connectModel(model.id);
                    runOnUiThread(() -> {
                        connectBtn.setEnabled(true);
                        connectBtn.setText("CONNECT");
                        if (connected) {
                            manager.setLocalOverrideEnabled(true);
                            Toast.makeText(this,
                                    "Verified local inference. " + model.displayName + " is now connected to Ocean Agent.",
                                    Toast.LENGTH_LONG).show();
                        } else {
                            String err = model.errorMessage != null ? model.errorMessage : "Failed to connect local inference runtime";
                            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                        }
                        updateRamStatus();
                        renderModels();
                    });
                }, "ocean-local-connect-" + model.id).start();
            });
            actions.addView(connectBtn);

            Button deleteBtn = createButton("DELETE", false, density);
            deleteBtn.setOnClickListener(v -> confirmDelete(model));
            actions.addView(deleteBtn);
        } else if (model.isConnected()) {
            Button useBtn = createButton("USE FOR OCEAN", true, density);
            useBtn.setOnClickListener(v -> {
                manager.setLocalOverrideEnabled(true);
                Toast.makeText(this, "Local model override enabled: Ocean Agent will route prompts to " + model.displayName, Toast.LENGTH_LONG).show();
                renderModels();
            });
            actions.addView(useBtn);

            Button disconnectBtn = createButton("DISCONNECT", false, density);
            disconnectBtn.setOnClickListener(v -> {
                manager.disconnectModel(model.id);
                Toast.makeText(this, "Disconnected " + model.displayName, Toast.LENGTH_SHORT).show();
                updateRamStatus();
                renderModels();
            });
            actions.addView(disconnectBtn);
        }

        Button detailsBtn = createButton("DETAILS", false, density);
        detailsBtn.setOnClickListener(v -> showModelDetails(model));
        actions.addView(detailsBtn);

        card.addView(actions);
        card.setOnClickListener(v -> showModelDetails(model));
        return card;
    }

    private Button createButton(String label, boolean primary, float density) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12f);
        b.setTypeface(null, Typeface.BOLD);
        int padH = (int) (14 * density);
        b.setPadding(padH, 0, padH, 0);
        b.setMinHeight((int) (36 * density));
        b.setMinimumHeight((int) (36 * density));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(8 * density);
        if (primary) {
            bg.setColor(getColor(R.color.ocean_ink));
            b.setTextColor(getColor(R.color.ocean_background));
        } else {
            bg.setColor(getColor(R.color.ocean_surface));
            bg.setStroke((int) (1 * density), getColor(R.color.ocean_border));
            b.setTextColor(getColor(R.color.ocean_text_primary));
        }
        b.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, (int) (36 * density));
        lp.setMarginStart((int) (8 * density));
        b.setLayoutParams(lp);
        return b;
    }

    private void startDownload(LocalModel model) {
        Toast.makeText(this, "Starting download of " + model.displayName + "…", Toast.LENGTH_SHORT).show();
        renderModels();

        manager.installModel(model.id, new LocalModelManager.ProgressCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                runOnUiThread(() -> renderModels());
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(LocalModelsActivity.this, model.displayName + " installed successfully", Toast.LENGTH_LONG).show();
                    renderModels();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(LocalModelsActivity.this, "Download failed: " + error, Toast.LENGTH_LONG).show();
                    renderModels();
                });
            }
        });
    }

    private void confirmDelete(LocalModel model) {
        OceanModal.create(this)
                .setTitle("Delete Model")
                .setExplanation("Delete local model file for " + model.displayName + "?")
                .setPositiveButton("Delete", v -> {
                    manager.deleteModel(model.id);
                    Toast.makeText(this, "Model deleted", Toast.LENGTH_SHORT).show();
                    renderModels();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showModelDetails(LocalModel model) {
        long sizeMb = model.sizeBytes / (1024 * 1024);
        String msg = "Model ID: " + model.id + "\n"
                + "Family: " + model.family + "\n"
                + "Format: " + model.format + " (" + model.quantization + ")\n"
                + "File Size: " + sizeMb + " MB (" + model.sizeBytes + " bytes)\n"
                + "Context Window: " + model.context + " tokens\n"
                + "Min RAM Required: " + model.minRamMb + " MB\n"
                + "Runtime Backend: " + model.backend + "\n"
                + "Target Architecture: " + model.architecture + "\n"
                + "Open-Source License: " + model.license + "\n"
                + "Lifecycle State: " + model.state.label + "\n"
                + "SHA-256 Checksum: " + (model.sha256 != null && !model.sha256.isEmpty() ? model.sha256 : "Not provided") + "\n\n"
                + "Verified Upstream Source:\n" + model.sourceUrl;

        OceanModal.create(this)
                .setTitle(model.displayName)
                .setExplanation(model.family + " · " + model.quantization + " · " + sizeMb + " MB · " + model.license)
                .setDetailsText(msg)
                .setPositiveButton("Done", null)
                .show();
    }
}
