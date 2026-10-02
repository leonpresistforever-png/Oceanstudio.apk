package studio.ocean.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Reusable Ocean monochrome bottom sheet / modal system (Directive 2026-10-02 §15.1).
 *
 * Visual & Behavioral Invariants:
 * - Anchored as a bottom sheet with rounded top corners.
 * - Subtle drag handle.
 * - Greyscale palette only (#191817 ink, #77736E muted, #D8D8D8 border, #F9F9F8 background).
 * - Primary action: filled black background with white text.
 * - Secondary action: soft grey / outlined background with black text.
 * - Zero default Material teal/green text-only action buttons.
 * - Concise, truthful copy with technical diagnostics tucked under an expandable "Details" toggle.
 * - Inline validation/error banner for failed operations.
 */
public final class OceanModal {

    public static class Builder {
        private final Context context;
        private String title;
        private String explanation;
        private View customContentView;
        private String positiveText;
        private View.OnClickListener onPositiveClick;
        private boolean positiveEnabled = true;
        private String negativeText;
        private View.OnClickListener onNegativeClick;
        private String neutralText;
        private View.OnClickListener onNeutralClick;
        private String detailsText;
        private String inlineError;
        private boolean cancelable = true;

        public Builder(@NonNull Context context) {
            this.context = context;
        }

        public Builder setTitle(@NonNull String title) {
            this.title = title;
            return this;
        }

        public Builder setExplanation(@Nullable String explanation) {
            this.explanation = explanation;
            return this;
        }

        public Builder setCustomView(@Nullable View customContentView) {
            this.customContentView = customContentView;
            return this;
        }

        public Builder setPositiveButton(@NonNull String text, @Nullable View.OnClickListener listener) {
            this.positiveText = text;
            this.onPositiveClick = listener;
            return this;
        }

        public Builder setPositiveButtonEnabled(boolean enabled) {
            this.positiveEnabled = enabled;
            return this;
        }

        public Builder setNegativeButton(@NonNull String text, @Nullable View.OnClickListener listener) {
            this.negativeText = text;
            this.onNegativeClick = listener;
            return this;
        }

        public Builder setNeutralButton(@NonNull String text, @Nullable View.OnClickListener listener) {
            this.neutralText = text;
            this.onNeutralClick = listener;
            return this;
        }

        public Builder setDetailsText(@Nullable String details) {
            this.detailsText = details;
            return this;
        }

        public Builder setInlineError(@Nullable String error) {
            this.inlineError = error;
            return this;
        }

        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        public Dialog show() {
            final Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(cancelable);

            int pad20 = dp(context, 20);
            int pad16 = dp(context, 16);
            int pad12 = dp(context, 12);
            int pad8 = dp(context, 8);
            int pad4 = dp(context, 4);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundResource(R.drawable.bottom_sheet_background);
            root.setPadding(pad20, pad12, pad20, pad20);

            // Drag handle
            ImageView handle = new ImageView(context);
            handle.setImageResource(R.drawable.bottom_sheet_handle);
            LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
            handleLp.gravity = Gravity.CENTER_HORIZONTAL;
            handleLp.bottomMargin = pad16;
            root.addView(handle, handleLp);

            // Title
            if (!TextUtils.isEmpty(title)) {
                TextView titleView = new TextView(context);
                titleView.setText(title);
                titleView.setTextSize(18f);
                titleView.setTypeface(null, Typeface.BOLD);
                titleView.setTextColor(ContextCompat.getColor(context, R.color.ocean_ink));
                root.addView(titleView);
            }

            // Explanation
            if (!TextUtils.isEmpty(explanation)) {
                TextView explView = new TextView(context);
                explView.setText(explanation);
                explView.setTextSize(13f);
                explView.setTextColor(ContextCompat.getColor(context, R.color.ocean_muted));
                explView.setPadding(0, pad4, 0, pad12);
                root.addView(explView);
            } else {
                root.setPadding(pad20, pad12, pad20, pad16);
            }

            // Inline Error banner if present
            if (!TextUtils.isEmpty(inlineError)) {
                LinearLayout errPanel = new LinearLayout(context);
                errPanel.setOrientation(LinearLayout.VERTICAL);
                errPanel.setBackgroundResource(R.drawable.auth_error_panel_background);
                errPanel.setPadding(pad12, pad8, pad12, pad8);
                LinearLayout.LayoutParams errLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                errLp.bottomMargin = pad12;

                TextView errText = new TextView(context);
                errText.setText(inlineError);
                errText.setTextSize(12f);
                errText.setTextColor(ContextCompat.getColor(context, R.color.ocean_ink));
                errPanel.addView(errText);
                root.addView(errPanel, errLp);
            }

            // Custom Content View inside scroll if large
            if (customContentView != null) {
                if (customContentView.getParent() != null) {
                    ((ViewGroup) customContentView.getParent()).removeView(customContentView);
                }
                ScrollView sv = new ScrollView(context);
                sv.addView(customContentView);
                LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
                svLp.bottomMargin = pad12;
                root.addView(sv, svLp);
            }

            // Technical Details expandable section
            if (!TextUtils.isEmpty(detailsText)) {
                TextView toggleDetails = new TextView(context);
                toggleDetails.setText("▸ Details");
                toggleDetails.setTextSize(12f);
                toggleDetails.setTypeface(null, Typeface.BOLD);
                toggleDetails.setTextColor(ContextCompat.getColor(context, R.color.ocean_muted));
                toggleDetails.setPadding(0, pad4, 0, pad8);

                TextView detailsView = new TextView(context);
                detailsView.setText(detailsText);
                detailsView.setTextSize(11f);
                detailsView.setTextColor(ContextCompat.getColor(context, R.color.ocean_muted));
                detailsView.setBackgroundResource(R.drawable.auth_field_background);
                detailsView.setPadding(pad8, pad8, pad8, pad8);
                detailsView.setVisibility(View.GONE);

                toggleDetails.setOnClickListener(v -> {
                    if (detailsView.getVisibility() == View.VISIBLE) {
                        detailsView.setVisibility(View.GONE);
                        toggleDetails.setText("▸ Details");
                    } else {
                        detailsView.setVisibility(View.VISIBLE);
                        toggleDetails.setText("▾ Details");
                    }
                });

                root.addView(toggleDetails);
                root.addView(detailsView);
            }

            // Buttons Container
            LinearLayout buttonBar = new LinearLayout(context);
            buttonBar.setOrientation(LinearLayout.HORIZONTAL);
            buttonBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams btnBarLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            btnBarLp.topMargin = pad8;

            // Neutral button (if any)
            if (!TextUtils.isEmpty(neutralText)) {
                Button neutralBtn = new Button(context);
                neutralBtn.setText(neutralText);
                neutralBtn.setTextSize(13f);
                neutralBtn.setAllCaps(false);
                neutralBtn.setTextColor(ContextCompat.getColor(context, R.color.ocean_muted));
                neutralBtn.setBackgroundResource(R.drawable.button_secondary);
                neutralBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onNeutralClick != null) onNeutralClick.onClick(v);
                });
                LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                        0, dp(context, 44), 1.0f);
                nLp.setMarginEnd(pad8);
                buttonBar.addView(neutralBtn, nLp);
            }

            // Negative button (Secondary)
            if (!TextUtils.isEmpty(negativeText)) {
                Button negBtn = new Button(context);
                negBtn.setText(negativeText);
                negBtn.setTextSize(13f);
                negBtn.setAllCaps(false);
                negBtn.setTextColor(ContextCompat.getColor(context, R.color.ocean_ink));
                negBtn.setBackgroundResource(R.drawable.button_secondary);
                negBtn.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (onNegativeClick != null) onNegativeClick.onClick(v);
                });
                LinearLayout.LayoutParams negLp = new LinearLayout.LayoutParams(
                        0, dp(context, 44), 1.0f);
                negLp.setMarginEnd(TextUtils.isEmpty(positiveText) ? 0 : pad8);
                buttonBar.addView(negBtn, negLp);
            }

            // Positive button (Primary - Black filled)
            if (!TextUtils.isEmpty(positiveText)) {
                Button posBtn = new Button(context);
                posBtn.setText(positiveText);
                posBtn.setTextSize(13f);
                posBtn.setTypeface(null, Typeface.BOLD);
                posBtn.setAllCaps(false);
                posBtn.setTextColor(ContextCompat.getColor(context, R.color.ocean_paper));
                posBtn.setBackgroundResource(R.drawable.primary_button_background);
                posBtn.setEnabled(positiveEnabled);
                if (!positiveEnabled) {
                    posBtn.setAlpha(0.4f);
                }
                posBtn.setOnClickListener(v -> {
                    if (onPositiveClick != null) {
                        onPositiveClick.onClick(v);
                    }
                    dialog.dismiss();
                });
                LinearLayout.LayoutParams posLp = new LinearLayout.LayoutParams(
                        0, dp(context, 44), 1.0f);
                buttonBar.addView(posBtn, posLp);
            }

            if (!TextUtils.isEmpty(positiveText) || !TextUtils.isEmpty(negativeText) || !TextUtils.isEmpty(neutralText)) {
                root.addView(buttonBar, btnBarLp);
            }

            dialog.setContentView(root);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setGravity(Gravity.BOTTOM);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(0.45f);
            }

            dialog.show();
            return dialog;
        }

        private static int dp(Context context, int v) {
            return Math.round(v * context.getResources().getDisplayMetrics().density);
        }
    }

    public static Builder create(@NonNull Context context) {
        return new Builder(context);
    }

    public static Dialog showMessage(@NonNull Context context, @NonNull String title, @Nullable String message) {
        return new Builder(context)
                .setTitle(title)
                .setExplanation(message)
                .setPositiveButton("Dismiss", null)
                .show();
    }

    public static Dialog showError(@NonNull Context context, @NonNull String title, @NonNull String message, @Nullable String details) {
        return new Builder(context)
                .setTitle(title)
                .setExplanation(message)
                .setDetailsText(details)
                .setPositiveButton("Dismiss", null)
                .show();
    }

    public static Dialog showConfirmation(@NonNull Context context, @NonNull String title, @NonNull String message,
                                          @NonNull String positiveLabel, @NonNull Runnable onConfirmed) {
        return new Builder(context)
                .setTitle(title)
                .setExplanation(message)
                .setPositiveButton(positiveLabel, v -> onConfirmed.run())
                .setNegativeButton("Cancel", null)
                .show();
    }
}
