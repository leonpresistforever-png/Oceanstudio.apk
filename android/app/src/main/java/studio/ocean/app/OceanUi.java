package studio.ocean.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

/** Centralized greyscale chat-app styling (Gemini / ChatGPT / Claude community patterns). */
final class OceanUi {
    private OceanUi() {}

    static int color(Context context, int resId) {
        return ContextCompat.getColor(context, resId);
    }

    static void styleSeekBar(Context context, SeekBar bar) {
        int trackOff = color(context, R.color.ocean_track_off);
        int trackOn = color(context, R.color.ocean_track_on);
        int thumb = color(context, R.color.ocean_switch_thumb);
        if (bar.getProgressDrawable() != null) {
            bar.getProgressDrawable().setColorFilter(trackOn, PorterDuff.Mode.SRC_IN);
        }
        if (bar.getThumb() != null) {
            bar.getThumb().setColorFilter(thumb, PorterDuff.Mode.SRC_IN);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bar.setSplitTrack(false);
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(trackOff));
            bar.setProgressTintList(ColorStateList.valueOf(trackOn));
            bar.setThumbTintList(ColorStateList.valueOf(thumb));
        }
    }

    static void styleSwitch(Context context, SwitchCompat switchView) {
        int ink = color(context, R.color.ocean_switch_thumb);
        int trackOff = color(context, R.color.ocean_track_off);
        switchView.setTextColor(color(context, R.color.ocean_text_primary));
        switchView.setShowText(false);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        switchView.setThumbTintList(new ColorStateList(states,
                new int[]{ink, color(context, R.color.ocean_background)}));
        switchView.setTrackTintList(new ColorStateList(states,
                new int[]{ink, trackOff}));
    }

    static TextView sectionHeader(Context context, String label) {
        TextView t = new TextView(context);
        t.setText(label);
        t.setTextColor(color(context, R.color.ocean_section_label));
        t.setTextSize(11f);
        t.setTypeface(null, Typeface.BOLD);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        int top = dp(context, 16);
        int bottom = dp(context, 6);
        t.setPadding(0, top, 0, bottom);
        return t;
    }

    static LinearLayout settingsRowLayout(Context context, String title, String subtitle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.settings_row_background);
        int pad = dp(context, 14);
        row.setPadding(pad, pad, pad, pad);
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(color(context, R.color.ocean_text_primary));
        titleView.setTextSize(15f);
        titleView.setTypeface(null, Typeface.BOLD);
        row.addView(titleView);
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(context);
            sub.setText(subtitle);
            sub.setTextColor(color(context, R.color.ocean_text_secondary));
            sub.setTextSize(12f);
            sub.setPadding(0, dp(context, 4), 0, 0);
            row.addView(sub);
        }
        return row;
    }

    static View conversationRowView(Context context, String title, String preview) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundResource(android.R.drawable.list_selector_background);
        int h = dp(context, 12);
        wrap.setPadding(dp(context, 20), h, dp(context, 20), h);
        TextView t = new TextView(context);
        t.setText(title);
        t.setTextColor(color(context, R.color.ocean_text_primary));
        t.setTextSize(14f);
        t.setTypeface(null, Typeface.BOLD);
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        wrap.addView(t);
        if (preview != null && !preview.isEmpty()) {
            TextView p = new TextView(context);
            p.setText(preview);
            p.setTextColor(color(context, R.color.ocean_text_tertiary));
            p.setTextSize(12f);
            p.setMaxLines(1);
            p.setEllipsize(android.text.TextUtils.TruncateAt.END);
            p.setPadding(0, dp(context, 2), 0, 0);
            wrap.addView(p);
        }
        return wrap;
    }

    static TextView modelChip(Context context, String label, boolean selected) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        chip.setBackgroundResource(selected ? R.drawable.model_chip_background_selected : R.drawable.model_chip_background);
        chip.setTextColor(selected ? color(context, R.color.ocean_background) : color(context, R.color.ocean_text_secondary));
        return chip;
    }

    static TextView outlinedPill(Context context, String label) {
        TextView pill = new TextView(context);
        pill.setText(label);
        pill.setTextColor(color(context, R.color.ocean_text_primary));
        pill.setTextSize(14f);
        pill.setTypeface(null, Typeface.BOLD);
        pill.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(context, R.color.ocean_background));
        bg.setCornerRadius(dp(context, 999));
        bg.setStroke(dp(context, 1), color(context, R.color.ocean_text_primary));
        pill.setBackground(bg);
        return pill;
    }

    static LinearLayout.LayoutParams matchWidth(Context context) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable roundRect(Context context, int fill, int radiusDp, int strokeArgb) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(context, radiusDp));
        if ((strokeArgb >>> 24) != 0) d.setStroke(dp(context, 1), strokeArgb);
        return d;
    }

    static GradientDrawable topSheetBackground(Context context) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color(context, R.color.ocean_background));
        float r = dp(context, 28);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }
}
