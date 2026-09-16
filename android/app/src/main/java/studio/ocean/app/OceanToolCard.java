package studio.ocean.app;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One durable, expandable row for each real tool invocation. */
final class OceanToolCard extends LinearLayout {
    private final TextView state, output;
    private final ImageView chevron;
    private boolean expanded = true;

    OceanToolCard(Context context, String tool, String command) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(0, dp(10), 0, dp(10));
        View top = new View(context); top.setBackgroundColor(0xFFE5E7EB);
        addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, dp(1)));
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        ImageView icon = new ImageView(context); icon.setImageResource(R.drawable.ic_terminal);
        row.addView(icon, new LayoutParams(dp(20), dp(20)));
        TextView title = new TextView(context); title.setText(tool); title.setTextSize(12);
        title.setTextColor(0xFF52565C); title.setTypeface(null, Typeface.BOLD); title.setPadding(dp(8), 0, dp(8), 0);
        row.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        state = new TextView(context); state.setText("Running"); state.setTextSize(11); state.setTextColor(0xFF73777D);
        row.addView(state);
        chevron = new ImageView(context); chevron.setImageResource(R.drawable.ic_chevron); chevron.setRotation(90);
        LayoutParams arrow = new LayoutParams(dp(18), dp(18)); arrow.leftMargin = dp(8); row.addView(chevron, arrow);
        row.setContentDescription("Toggle " + tool + " output"); row.setFocusable(true);
        row.setBackgroundResource(android.R.drawable.list_selector_background);
        addView(row);
        TextView code = new TextView(context); code.setText(command); code.setTextSize(12);
        code.setTextColor(0xFF34383D); code.setTypeface(Typeface.MONOSPACE); code.setTextIsSelectable(true);
        code.setPadding(0, 0, 0, dp(8)); addView(code);
        output = new TextView(context); output.setTextColor(0xFF60646B); output.setTextSize(12);
        output.setTypeface(Typeface.MONOSPACE); output.setTextIsSelectable(true);
        output.setPadding(dp(12), dp(10), dp(12), dp(10)); output.setBackgroundColor(0xFFF7F7F6);
        output.setVisibility(GONE); addView(output);
        row.setOnClickListener(v -> {
            expanded = !expanded;
            output.setVisibility(expanded && output.length() > 0 ? VISIBLE : GONE);
            chevron.animate().rotation(expanded ? 90 : 0).setDuration(140).start();
        });
    }
    void append(String chunk) {
        // Escape sequences are presentation control bytes, never chat markup or executable text.
        String text = chunk.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "").replace("\r", "");
        int keep = Math.min(text.length(), Math.max(0, 32768 - output.length()));
        if (keep > 0) output.append(text.substring(0, keep));
        if (expanded && output.length() > 0) output.setVisibility(VISIBLE);
    }
    void complete(int exitCode) {
        state.setText(exitCode == 0 ? "Done · exit 0" : exitCode == 130 ? "Stopped" : "Exit " + exitCode);
        state.setTextColor(exitCode == 0 ? 0xFF60646B : 0xFF9A3D32);
        if (exitCode != 0 && output.length() > 0) { expanded = true; output.setVisibility(VISIBLE); chevron.setRotation(90); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
