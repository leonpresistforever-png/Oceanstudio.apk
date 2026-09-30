package studio.ocean.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Right drawer: live agent sliders, HTTP function builder, tools, and hub shortcuts. */
final class AgentControlsPanel {
    interface Host {
        void close();
        void openPlugins();
        void openByok();
        void openDevice();
        void openRuntime();
        OceanByokManager byok();
    }

    private final MainActivity activity;
    private final Host host;
    private final OceanAgentSettings settings;
    private final OceanAgentHubStore hub;
    private final LinearLayout tabs;
    private final FrameLayout body;
    private String activeTab = "model";

    AgentControlsPanel(MainActivity activity, View root, Host host) {
        this.activity = activity;
        this.host = host;
        settings = new OceanAgentSettings(activity);
        hub = new OceanAgentHubStore(activity);
        tabs = root.findViewById(R.id.agent_hub_tabs);
        body = root.findViewById(R.id.agent_controls_body);
        View close = root.findViewById(R.id.agent_controls_close);
        if (close != null) close.setOnClickListener(v -> host.close());
        buildTabs();
        showTab("model");
    }

    void refreshSummary() {
        if ("model".equals(activeTab)) showTab("model");
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

    private void showTab(String id) {
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

    private void renderModel() {
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

        Switch keep = switchRow(col, "Keep session context", settings.keepSessionAlive());
        Switch anti = switchRow(col, "Extended response timeout", settings.antiTimeout());

        label(col, "INSTRUCTIONS");
        EditText instructions = new EditText(activity);
        instructions.setMinLines(5);
        instructions.setGravity(Gravity.TOP | Gravity.START);
        instructions.setBackgroundResource(R.drawable.auth_field_background);
        instructions.setPadding(dp(12), dp(12), dp(12), dp(12));
        instructions.setText(settings.userInstructions());
        col.addView(instructions, matchWidth());

        TextView modelLine = muted(col, "Model · " + (host.byok() != null && host.byok().isVerified()
                ? host.byok().getModel() : "Not configured"));
        modelLine.setOnClickListener(v -> host.openByok());

        Button save = primary(col, "Save agent configuration");
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

    private void renderFunctions() {
        LinearLayout col = column();
        body.addView(col);
        muted(col, "HTTP functions the agent can call via terminal curl workflows.");
        Button create = primary(col, "Create function");
        create.setOnClickListener(v -> showHttpBuilder("function", name -> {
            try {
                hub.addFunction(name, lastHttpConfig);
                showTab("functions");
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }));
        listJson(col, hub.functions(), "No functions yet.");
    }

    private void renderTools() {
        LinearLayout col = column();
        body.addView(col);
        muted(col, "OpenAI-style tool schemas stored locally for documentation and future wiring.");
        Button create = primary(col, "Create tool schema");
        create.setOnClickListener(v -> showHttpBuilder("tool", name -> {
            try {
                JSONArray arr = hub.tools();
                arr.put(new JSONObject()
                        .put("name", name)
                        .put("description", "Custom HTTP tool " + name)
                        .put("http", lastHttpConfig));
                hub.saveTools(arr);
                showTab("tools");
            } catch (Exception e) {
                Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }));
        listJson(col, hub.tools(), "No custom tools.");
    }

    private void renderSkills() {
        LinearLayout col = column();
        body.addView(col);
        muted(col, "Connected skills append premium context to the agent system prompt.");
        Button create = primary(col, "Create skill");
        create.setOnClickListener(v -> {
            EditText title = new EditText(activity);
            title.setHint("Skill title");
            EditText desc = new EditText(activity);
            desc.setMinLines(4);
            desc.setHint("Full skill instructions…");
            LinearLayout box = column();
            box.addView(title);
            box.addView(desc);
            new AlertDialog.Builder(activity).setTitle("New skill").setView(box)
                    .setPositiveButton("Save", (d, w) -> {
                        try {
                            hub.addSkill(title.getText().toString().trim(), desc.getText().toString().trim(), "manual");
                            showTab("skills");
                        } catch (Exception e) {
                            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }).setNegativeButton("Cancel", null).show();
        });
        JSONArray skills = hub.skills();
        for (int i = 0; i < skills.length(); i++) {
            JSONObject s = skills.optJSONObject(i);
            if (s == null) continue;
            LinearLayout card = column();
            card.setBackgroundResource(R.drawable.composer_background);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView t = new TextView(activity);
            t.setText(s.optString("title"));
            t.setTextColor(0xFF111111);
            t.setTypeface(null, Typeface.BOLD);
            card.addView(t);
            TextView d = new TextView(activity);
            d.setText(s.optString("description"));
            d.setTextColor(0xFF6B7280);
            d.setTextSize(12f);
            card.addView(d);
            TextView badge = new TextView(activity);
            boolean connected = "connected".equals(s.optString("status"));
            badge.setText(connected ? "Connected · " + s.optString("source", "manual") : "Disconnected");
            badge.setTextColor(connected ? 0xFF111111 : 0xFF9CA3AF);
            badge.setTextSize(11f);
            card.addView(badge);
            card.setOnClickListener(v -> toggleSkill(s.optString("id")));
            LinearLayout.LayoutParams lp = matchWidth();
            lp.topMargin = dp(10);
            col.addView(card, lp);
        }
        body.addView(col);
    }

    private void toggleSkill(String id) {
        try {
            JSONArray arr = hub.skills();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.getJSONObject(i);
                if (!id.equals(s.optString("id"))) continue;
                String status = s.optString("status");
                s.put("status", "connected".equals(status) ? "disconnected" : "connected");
            }
            hub.saveSkills(arr);
            showTab("skills");
        } catch (Exception ignored) {}
    }

    private JSONObject lastHttpConfig;

    private void showHttpBuilder(String kind, java.util.function.Consumer<String> onSave) {
        LinearLayout root = column();
        EditText name = field(root, kind + " name", "my_" + kind);
        HttpRequestEditor editor = new HttpRequestEditor(activity);
        root.addView(editor.view(), matchWidth());
        new AlertDialog.Builder(activity).setTitle("Create " + kind).setView(root)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        lastHttpConfig = editor.toJson();
                        onSave.accept(name.getText().toString().trim());
                    } catch (Exception e) {
                        Toast.makeText(activity, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }).setNegativeButton("Cancel", null).show();
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

    private void label(LinearLayout parent, String text) {
        TextView t = new TextView(activity);
        t.setText(text);
        t.setTextColor(0xFF9CA3AF);
        t.setTextSize(11f);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, dp(14), 0, dp(6));
        parent.addView(t);
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
        parent.addView(e, matchWidth());
        return e;
    }

    private SeekBar slider(LinearLayout parent, String title, int progress, int min, int max) {
        TextView label = new TextView(activity);
        label.setText(title);
        label.setTextColor(0xFF111111);
        label.setPadding(0, dp(8), 0, dp(4));
        parent.addView(label);
        SeekBar bar = new SeekBar(activity);
        bar.setMax(max);
        bar.setProgress(Math.max(min, Math.min(max, progress)));
        label.setText(title + " · " + bar.getProgress());
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                label.setText(title + " · " + (title.contains("token") ? value * 128 : value / (title.contains("Top") ? 100f : 100f)));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        parent.addView(bar, matchWidth());
        return bar;
    }

    private Switch switchRow(LinearLayout parent, String text, boolean on) {
        Switch s = new Switch(activity);
        s.setText(text);
        s.setChecked(on);
        s.setTextColor(0xFF111111);
        parent.addView(s);
        return s;
    }

    private Button primary(LinearLayout parent, String text) {
        Button b = new Button(activity);
        b.setText(text);
        b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.primary_button_background);
        b.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp = matchWidth();
        lp.topMargin = dp(16);
        parent.addView(b, lp);
        return b;
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
