package studio.ocean.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.widget.SwitchCompat;

/** Greyscale control styling for agent hub surfaces. */
final class OceanUi {
    private OceanUi() {}

    static void styleSeekBar(SeekBar bar) {
        int trackOff = 0xFFE5E7EB;
        int trackOn = 0xFF9CA3AF;
        int thumb = 0xFF111111;
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

    static void styleSwitch(SwitchCompat switchView) {
        switchView.setTextColor(0xFF111111);
        switchView.setShowText(false);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        switchView.setThumbTintList(new ColorStateList(states,
                new int[]{0xFF111111, 0xFFFFFFFF}));
        switchView.setTrackTintList(new ColorStateList(states,
                new int[]{0xFF111111, 0xFFE5E7EB}));
    }

    static TextView outlinedPill(Context context, String label) {
        TextView pill = new TextView(context);
        pill.setText(label);
        pill.setTextColor(0xFF111111);
        pill.setTextSize(14f);
        pill.setTypeface(null, Typeface.BOLD);
        pill.setPadding(dp(context, 16), dp(context, 10), dp(context, 16), dp(context, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(context, 999));
        bg.setStroke(dp(context, 1), 0xFF111111);
        pill.setBackground(bg);
        return pill;
    }

    static LinearLayout.LayoutParams matchWidth(Context context) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static int dp(Context context, int v) {
        return Math.round(v * context.getResources().getDisplayMetrics().density);
    }
}
