package studio.ocean.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.widget.SwitchCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import studio.ocean.app.models.local.LocalModel;
import studio.ocean.app.models.local.LocalModelManager;
import studio.ocean.app.models.local.LocalGenerationSettings;

/** Right drawer: live agent sliders, HTTP function builder, tools, and hub shortcuts. */
final class AgentControlsPanel {
    interface Host {
        void close();
        void openPlugins();
        void openByok();
        void openDevice();
        void openRuntime();
        OceanByokManager byok();
        boolean agentRunning();
    }

    private final MainActivity activity;
    private final Host host;
    private final OceanAgentSettings settings;
    private final OceanAgentHubStore hub;
    private final LinearLayout tabs;
    private final FrameLayout body;
    private final TextView subtitle;
    private String activeTab = "model";
    private boolean inlineFunctionEditor;
    private boolean inlineToolEditor;
    private boolean inlineSkillEditor;

    AgentControlsPanel(MainActivity activity, View root, Host host) {
        this.activity = activity;
        this.host = host;
        settings = new OceanAgentSettings(activity);
        hub = new OceanAgentHubStore(activity);
        tabs = root.findViewById(R.id.agent_hub_tabs);
        body = root.findViewById(R.id.agent_controls_body);
        subtitle = root.findViewById(R.id.agent_controls_subtitle);
        View close = root.findViewById(R.id.agent_controls_close);
        if (close != null) close.setOnClickListener(v -> host.close());
        buildTabs();
        showTab("model");
    }

    void refreshSummary() {
        if ("model".equals(activeTab)) showTab("model");
    }

    void showTab(String id) {
        activeTab = id;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            TextView t = (TextView) tabs.getChildAt(i);
            boolean on = id.equals(t.getTag());
            t.setTextColor(on ? 0xFF111111 : 0xFF8A8F96);
            t.setBackgroundResource(on ? R.drawable.tab_background : android.R.color.transparent);
        }
        body.removeAllViews();
        switch (id) {
            case "functions": renderFunctions(); break;
            case "tools": renderTools(); break;
            case "skills": renderSkills(); break;
            default: renderModel(); break;
        }
    }

    private void buildTabs() {
        tabs.removeAllViews();
        addTabButton("model", "Model");
        addTabButton("functions", "Functions");
        addTabButton("tools", "Tools");
        addTabButton("skills", "Skills");
    }

    private void addTabButton(String id, String label) {
        TextView tab = new TextView(activity);
        tab.setText(label);
        tab.setTextSize(13f);
        tab.setTypeface(null, Typeface.BOLD);
        tab.setPadding(dp(14), dp(10), dp(14), dp(10));
        tab.setOnClickListener(v -> showTab(id));
        tab.setTag(id);
        tabs.addView(tab);
    }

    private void renderModel() {
        LocalModelManager manager = LocalModelManager.getInstance(activity);
        LocalModel connected = manager.isLocalOverrideEnabled() ? manager.getConnectedModel() : null;
        if (connected != null) { renderLocalModel(manager, connected); return; }
        if (subtitle != null) subtitle.setText("Raw agent configuration");
        LinearLayout col = column();
        body.addView(col);

        label(col, "GENERATION");
        SeekBar temp = slider(col, "Temperature", (int) (settings.temperature() * 100), 0, 200);
        SeekBar topP = slider(col, "Top P", (int) (settings.topP() * 100), 1, 100);
        SeekBar tokens = slider(col, "Max output tokens", settings.maxTokens() / 128, 1, 256);

        label(col, "REASONING EFFORT");
        Spinner effort = new Spinner(activity);
        effort.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Default", "Low", "Medium", "High"}));
        String e = settings.reasoningEffort();
        effort.setSelection("low".equals(e) ? 1 : "medium".equals(e) ? 2 : "high".equals(e) ? 3 : 0);
        col.addView(effort);

        label(col, "LIMITS");
        EditText rounds = field(col, "Max agent rounds", String.valueOf(settings.maxRounds()));
        EditText toolCalls = field(col, "Max tool calls", String.valueOf(settings.maxToolCalls()));
        EditText cmdTimeout = field(col, "Command timeout (s)", String.valueOf(settings.commandTimeoutSeconds()));

        SwitchCompat keep = switchRow(col, "Keep session context", settings.keepSessionAlive());
        SwitchCompat anti = switchRow(col, "Extended response timeout", settings.antiTimeout());

        label(col, "INSTRUCTIONS");
        EditText instructions = new EditText(activity);
        instructions.setMinLines(5);
        instructions.setGravity(Gravity.TOP | Gravity.START);
        instructions.setBackgroundResource(R.drawable.auth_field_background);
        instructions.setPadding(dp(12), dp(12), dp(12), dp(12));
        instructions.setText(settings.userInstructions());
        col.addView(instructions, matchWidth());

        TextView modelLine = muted(col, "AI Providers · " + (host.byok() != null && host.byok().isVerified()
                ? host.byok().getModel() : "Manage providers & Direct Connect"));
        modelLine.setOnClickListener(v -> host.openByok());

        TextView localLine = muted(col, "Local Models · On-device GGUF inference");
        localLine.setOnClickListener(v -> {
            host.close();
            activity.startActivity(new Intent(activity, studio.ocean.app.models.local.LocalModelsActivity.class));
        });

        TextView save = outlinedAction(col, "Save agent configuration");
        save.setOnClickListener(v -> {
            try {
                settings.save(temp.getProgress() / 100f, topP.getProgress() / 100f,
                        tokens.getProgress() * 128, settings.connectTimeoutMs(), settings.readTimeoutMs(),
                        Integer.parseInt(cmdTimeout.getText().toString()),
                        Integer.parseInt(rounds.getText().toString()),
                        Integer.parseInt(toolCalls.getText().toString()),
                        keep.isChecked(), anti.isChecked(), effortValue(effort),
                        instructions.getText().toString());
                Toast.makeText(activity, "Agent configuration saved", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(activity, "Check numeric fields", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void renderLocalModel(LocalModelManager manager, LocalModel model) {
        if (subtitle != null) subtitle.setText("Local model · " + model.displayName);
        LinearLayout col = column(); body.addView(col);
        LocalGenerationSettings current = manager.generationSettings(model);
        label(col, "CONNECTED LOCAL RUNTIME");
        muted(col, model.displayName + "\n" + manager.backend() + " · " + model.endpoint
                + "\nActive context: " + model.verifiedContext + " tokens");
        label(col, "GENERATION");
        SeekBar temp = slider(col, "Temperature", (int) (current.temperature * 100), 0, 200);
        SeekBar topP = slider(col, "Top P", (int) (current.topP * 100), 1, 100);
        SeekBar tokens = slider(col, "Max output tokens", current.maxTokens / 128, 1, Math.max(1, (current.context - 128) / 128));
        SeekBar context = slider(col, "Context tokens", current.context / 128, 4, Math.min(32768, model.context) / 128);
        context.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                ((TextView) bar.getTag()).setText(formatSliderValue("Context tokens", progress));
                tokens.setMax(Math.max(1, progress - 1));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        EditText topK = field(col, "Top K (0 disables)", String.valueOf(current.topK));
        EditText repeat = field(col, "Repetition penalty", String.valueOf(current.repeatPenalty));
        EditText threads = field(col, "CPU threads (1–" + Runtime.getRuntime().availableProcessors() + ")", String.valueOf(current.threads));
        topK.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        repeat.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        threads.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        SwitchCompat keep = switchRow(col, "Keep local conversation context", current.keepContext);
        SwitchCompat tools = switchRow(col, "Terminal and runtime tools", current.tools);
        tools.setEnabled(model.supportsTools);
        muted(col, model.supportsTools ? "Tool support verified from the runtime."
                : "This model's runtime reports no tool calling. Chat requests omit tool schemas.");
        muted(col, "Context and thread changes reload the model. Settings apply only to this model; output is bounded by the available context.");
        TextView save = outlinedAction(col, "Apply local model settings");
        save.setOnClickListener(v -> {
            if (host.agentRunning()) { Toast.makeText(activity, "Stop the current response before reloading the local model", Toast.LENGTH_SHORT).show(); return; }
            final LocalGenerationSettings value;
            try {
                value = new LocalGenerationSettings(context.getProgress() * 128, tokens.getProgress() * 128,
                        temp.getProgress() / 100f, topP.getProgress() / 100f, Integer.parseInt(topK.getText().toString()),
                        Float.parseFloat(repeat.getText().toString()), Integer.parseInt(threads.getText().toString()),
                        keep.isChecked(), tools.isChecked() && model.supportsTools, model.context);
            } catch (Exception invalid) { Toast.makeText(activity, "Check Top K, repetition penalty and thread values", Toast.LENGTH_LONG).show(); return; }
            save.setEnabled(false); save.setText("Applying to the local runtime…");
            new Thread(() -> {
                String failure = null;
                try { manager.applyGenerationSettings(model.id, value); }
                catch (Exception error) { failure = OceanAgentConversation.safeMessage(error); }
                final String detail = failure;
                activity.runOnUiThread(() -> {
                    if (activity.isDestroyed()) return;
                    Toast.makeText(activity, detail == null ? "Local settings applied" : detail, Toast.LENGTH_LONG).show();
                    if ("model".equals(activeTab)) showTab("model");
                });
            }, "ocean-local-settings").start();
        });
        TextView models = outlinedAction(col, "Manage local models");
        models.setOnClickListener(v -> { host.close(); activity.startActivity(new Intent(activity, studio.ocean.app.models.local.LocalModelsActivity.class)); });
    }

    private void renderFunctions() {
        LinearLayout col = column();
        body.addView(col);
        muted(col, "HTTP functions the agent can call via terminal curl workflows.");

        TextView newBtn = outlinedAction(col, "+ New function");
        newBtn.setOnClickListener(v -> {
            inlineFunctionEditor = true;
            showTab("functions");
        });

        if (inlineFunctionEditor) {
            divider(col);
            label(col, "NEW FUNCTION");
            EditText name = field(col, "Function name", "my_function");
            HttpRequestEditor editor = new HttpRequestEditor(activity);
            col.addView(editor.view(), matchWidth());
            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView cancel = outlinedAction(actions, "Cancel");
            TextView save = outlinedAction(actions, "Save");
            LinearLayout.LayoutParams lp = matchWidth();
            lp.topMargin = dp(12);
            col.addView(actions, lp);
            cancel.setOnClickListener(v -> {
                inlineFunctionEditor = false;
                showTab("functions");
            });
            save.setOnClickListener(v -> {
                try {
                    hub.addFunction(name.getText().toString().trim(), editor.toJson());
                    inlineFunctionEditor = false;
                    showTab("functions");
                } catch (Exception e) {
                    Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            listJson(col, hub.functions(), "No functions yet.");
        }
    }

    private void renderTools() {
        LinearLayout col = column();
        body.addView(col);
        muted(col, "OpenAI-style tool schemas stored locally for documentation and future wiring.");

        TextView newBtn = outlinedAction(col, "+ New tool schema");
        newBtn.setOnClickListener(v -> {
            inlineToolEditor = true;
            showTab("tools");
        });

        if (inlineToolEditor) {
            divider(col);
            label(col, "NEW TOOL");
            EditText name = field(col, "Tool name", "my_tool");
            HttpRequestEditor editor = new HttpRequestEditor(activity);
            col.addView(editor.view(), matchWidth());
            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView cancel = outlinedAction(actions, "Cancel");
            TextView save = outlinedAction(actions, "Save");
            LinearLayout.LayoutParams lp = matchWidth();
            lp.topMargin = dp(12);
            col.addView(actions, lp);
            cancel.setOnClickListener(v -> {
                inlineToolEditor = false;
                showTab("tools");
            });
            save.setOnClickListener(v -> {
                try {
                    JSONArray arr = hub.tools();
                    arr.put(new JSONObject()
                            .put("name", name.getText().toString().trim())
                            .put("description", "Custom HTTP tool " + name.getText().toString().trim())
                            .put("http", editor.toJson()));
                    hub.saveTools(arr);
                    inlineToolEditor = false;
                    showTab("tools");
                } catch (Exception e) {
                    Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            listJson(col, hub.tools(), "No custom tools.");
        }
    }

    private void renderSkills() {
        LinearLayout col = column();
        body.addView(col);
        muted(col, "Connected skills append premium context to the agent system prompt.");

        TextView hubLink = outlinedAction(col, "Open skills hub");
        hubLink.setOnClickListener(v -> {
            host.close();
            activity.startActivity(new Intent(activity, PluginCenterActivity.class)
                    .putExtra("hub_section", "skills"));
        });

        TextView newBtn = outlinedAction(col, "+ New skill");
        newBtn.setOnClickListener(v -> {
            inlineSkillEditor = true;
            showTab("skills");
        });

        if (inlineSkillEditor) {
            divider(col);
            label(col, "NEW SKILL");
            EditText title = field(col, "Skill title", "");
            EditText desc = new EditText(activity);
            desc.setMinLines(8);
            desc.setGravity(Gravity.TOP | Gravity.START);
            desc.setHint("Full skill instructions (markdown)…");
            desc.setBackgroundResource(R.drawable.auth_field_background);
            desc.setPadding(dp(12), dp(12), dp(12), dp(12));
            col.addView(desc, matchWidth());
            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            TextView cancel = outlinedAction(actions, "Cancel");
            TextView save = outlinedAction(actions, "Save SKILL.md");
            LinearLayout.LayoutParams actionLp = matchWidth();
            actionLp.topMargin = dp(12);
            col.addView(actions, actionLp);
            cancel.setOnClickListener(v -> {
                inlineSkillEditor = false;
                showTab("skills");
            });
            save.setOnClickListener(v -> {
                try {
                    hub.addSkill(title.getText().toString().trim(), desc.getText().toString().trim(), "manual");
                    inlineSkillEditor = false;
                    showTab("skills");
                } catch (Exception e) {
                    Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
        JSONArray skills = hub.skills();
        for (int i = 0; i < skills.length(); i++) {
            JSONObject s = skills.optJSONObject(i);
            if (s == null) continue;
            String id = s.optString("id");
            LinearLayout card = column();
            card.setBackgroundResource(R.drawable.composer_background);
            card.setPadding(dp(16), dp(16), dp(16), dp(16));
            card.setMinimumHeight(dp(88));

            TextView t = new TextView(activity);
            t.setText(s.optString("title"));
            t.setTextColor(0xFF111111);
            t.setTextSize(15f);
            t.setTypeface(null, Typeface.BOLD);
            card.addView(t);

            TextView d = new TextView(activity);
            d.setText(s.optString("description"));
            d.setTextColor(0xFF6B7280);
            d.setTextSize(12f);
            d.setMaxLines(2);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = dp(4);
            card.addView(d, dlp);

            boolean connected = "connected".equals(s.optString("status"));
            TextView toggle = new TextView(activity);
            toggle.setText(connected ? "Connected" : "Connect");
            toggle.setTextColor(0xFF111111);
            toggle.setTextSize(11f);
            toggle.setTypeface(null, Typeface.BOLD);
            toggle.setPadding(dp(10), dp(6), dp(10), dp(6));
            toggle.setBackgroundResource(R.drawable.button_secondary);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(8);
            card.addView(toggle, tlp);

            toggle.setOnClickListener(v -> {
                hub.setSkillConnected(id, !connected);
                showTab("skills");
            });
            card.setOnClickListener(v -> {
                host.close();
                activity.startActivity(new Intent(activity, PluginCenterActivity.class)
                        .putExtra("hub_section", "skills")
                        .putExtra("skill_id", id));
            });

            LinearLayout.LayoutParams lp = matchWidth();
            lp.topMargin = dp(10);
            col.addView(card, lp);
        }
        }
    }

    private void listJson(LinearLayout col, JSONArray arr, String empty) {
        if (arr.length() == 0) {
            muted(col, empty);
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            TextView row = new TextView(activity);
            row.setText("• " + o.optString("name", o.optString("title", "item")));
            row.setTextColor(0xFF374151);
            row.setTextSize(14f);
            row.setPadding(0, dp(6), 0, dp(6));
            col.addView(row);
        }
    }

    private LinearLayout column() {
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(16), 0, 0);
        return col;
    }

    private void divider(LinearLayout parent) {
        View v = new View(activity);
        v.setBackgroundColor(0xFFE5E3E0);
        LinearLayout.LayoutParams lp = matchWidth();
        lp.height = 1;
        lp.topMargin = dp(16);
        lp.bottomMargin = dp(8);
        parent.addView(v, lp);
    }

    private void label(LinearLayout parent, String text) {
        parent.addView(OceanUi.sectionHeader(activity, text));
    }

    private TextView muted(LinearLayout parent, String text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextColor(0xFF6B7280);
        t.setTextSize(12f);
        t.setPadding(0, 0, 0, dp(10));
        parent.addView(t);
        return t;
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText e = new EditText(activity);
        e.setHint(hint);
        e.setText(value);
        e.setBackgroundResource(R.drawable.auth_field_background);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.bottomMargin = dp(10);
        parent.addView(e, lp);
        return e;
    }

    private SeekBar slider(LinearLayout parent, String title, int progress, int min, int max) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextColor(0xFF111111);
        label.setTextSize(14f);
        TextView value = new TextView(activity);
        value.setTextColor(0xFF6B7280);
        value.setTextSize(13f);
        value.setGravity(Gravity.END);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams rowLp = matchWidth();
        rowLp.topMargin = dp(10);
        rowLp.bottomMargin = dp(4);
        parent.addView(row, rowLp);
        SeekBar bar = new SeekBar(activity);
        bar.setTag(value);
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(Math.max(min, Math.min(max, progress)));
        OceanUi.styleSeekBar(activity, bar);
        value.setText(formatSliderValue(title, bar.getProgress()));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int prog, boolean fromUser) {
                value.setText(formatSliderValue(title, prog));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams barLp = matchWidth();
        barLp.bottomMargin = dp(12);
        parent.addView(bar, barLp);
        return bar;
    }

    private static String formatSliderValue(String title, int progress) {
        if (title.contains("token")) return String.valueOf(progress * 128);
        if (title.contains("Top")) return String.format(java.util.Locale.US, "%.2f", progress / 100f);
        return String.format(java.util.Locale.US, "%.2f", progress / 100f);
    }

    private SwitchCompat switchRow(LinearLayout parent, String text, boolean on) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(0xFF111111);
        label.setTextSize(14f);
        SwitchCompat s = new SwitchCompat(activity);
        s.setChecked(on);
        OceanUi.styleSwitch(activity, s);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = matchWidth();
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(4);
        parent.addView(row, lp);
        return s;
    }

    private TextView outlinedAction(ViewGroup parent, String text) {
        TextView pill = OceanUi.outlinedPill(activity, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        lp.rightMargin = dp(8);
        parent.addView(pill, lp);
        return pill;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    private static String effortValue(Spinner spinner) {
        int p = spinner.getSelectedItemPosition();
        return p == 1 ? "low" : p == 2 ? "medium" : p == 3 ? "high" : "default";
    }
}
