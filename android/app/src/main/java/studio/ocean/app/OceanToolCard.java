package studio.ocean.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Calm timeline row for one real Ocean tool invocation. */
final class OceanToolCard extends LinearLayout {
    private final TextView state, output;
    private final ImageView chevron;
    private boolean expanded = false;

    OceanToolCard(Context context, String tool, String command) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(2));

        LinearLayout timeline = new LinearLayout(context);
        timeline.setOrientation(HORIZONTAL);
        timeline.setGravity(Gravity.TOP);

        LinearLayout rail = new LinearLayout(context);
        rail.setOrientation(VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView node = new ImageView(context);
        node.setImageResource(iconFor(tool));
        node.setAlpha(0.62f);
        rail.addView(node, new LayoutParams(dp(18), dp(18)));
        View line = new View(context);
        line.setBackgroundColor(0x335B6168);
        LayoutParams lineParams = new LayoutParams(dp(1), dp(54));
        lineParams.topMargin = dp(4);
        rail.addView(line, lineParams);
        timeline.addView(rail, new LayoutParams(dp(34), LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        body.setPadding(dp(5), 0, 0, dp(12));

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(28));
        TextView title = new TextView(context);
        title.setText(tool);
        title.setTextSize(14);
        title.setTextColor(0xFF35383D);
        title.setTypeface(null, Typeface.BOLD);
        row.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        state = new TextView(context);
        state.setText("Running");
        state.setTextSize(12);
        state.setTextColor(0xFF73777D);
        row.addView(state);

        chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setAlpha(0.62f);
        LayoutParams arrow = new LayoutParams(dp(18), dp(18));
        arrow.leftMargin = dp(8);
        row.addView(chevron, arrow);
        body.addView(row);

        TextView code = new TextView(context);
        code.setText(command);
        code.setTextSize(12);
        code.setTextColor(0xFF777B81);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextIsSelectable(true);
        code.setPadding(0, dp(2), 0, dp(7));
        body.addView(code);

        output = new TextView(context);
        output.setTextColor(0xFF666A70);
        output.setTextSize(11.5f);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setLineSpacing(dp(2), 1f);
        output.setPadding(0, dp(4), dp(6), dp(6));
        output.setVisibility(GONE);
        body.addView(output);

        timeline.addView(body, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        addView(timeline);

        row.setContentDescription("Toggle " + tool + " output");
        row.setFocusable(true);
        row.setOnClickListener(v -> setExpanded(!expanded));
    }

    void setQueued() {
        state.setText("Queued");
        state.setTextColor(0xFF8A8E94);
        setAlpha(0.72f);
    }

    void setRunning() {
        state.setText("Running");
        state.setTextColor(0xFF73777D);
        animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    void append(String chunk) {
        String text = chunk.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "").replace("\r", "");
        int keep = Math.min(text.length(), Math.max(0, 32768 - output.length()));
        if (keep > 0) output.append(text.substring(0, keep));
        // Keep noisy runtime output collapsed by default; user expands on demand.
    }

    void complete(int exitCode) {
        state.setText(exitCode == 0 ? "Done · exit 0" : exitCode == 130 ? "Stopped" : "Exit " + exitCode);
        state.setTextColor(exitCode == 0 ? 0xFF676B71 : 0xFF9A3D32);
        if (exitCode != 0 && output.length() > 0) setExpanded(true);
    }

    private void setExpanded(boolean value) {
        expanded = value;
        output.setVisibility(expanded && output.length() > 0 ? VISIBLE : GONE);
        chevron.animate().rotation(expanded ? 90f : 0f).setDuration(140).start();
    }

    private int iconFor(String tool) {
        String t = tool == null ? "" : tool.toLowerCase(java.util.Locale.ROOT);
        if (t.contains("terminal")) return R.drawable.ic_terminal;
        if (t.contains("runtime") || t.contains("port")) return R.drawable.ic_ports;
        if (t.contains("device")) return R.drawable.ic_agent;
        if (t.contains("file")) return R.drawable.ic_files;
        return R.drawable.ic_tools;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
