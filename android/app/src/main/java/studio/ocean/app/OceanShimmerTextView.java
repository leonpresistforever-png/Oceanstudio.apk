package studio.ocean.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;
import androidx.appcompat.widget.AppCompatTextView;

/** A moving light band across the current thought while agent work is active. */
public final class OceanShimmerTextView extends AppCompatTextView {
    private ValueAnimator shimmer;
    private LinearGradient gradient;
    private final Matrix matrix = new Matrix();

    public OceanShimmerTextView(Context context) { super(context); }
    public OceanShimmerTextView(Context context, AttributeSet attributes) { super(context, attributes); }

    public void setShimmering(boolean active) {
        if (!active) {
            if (shimmer != null) { shimmer.cancel(); shimmer = null; }
            gradient = null;
            getPaint().setShader(null);
            invalidate();
            return;
        }
        if (shimmer != null) return;
        int width = Math.max(getWidth(), Math.round(180 * getResources().getDisplayMetrics().density));
        gradient = new LinearGradient(-width, 0, width, 0,
                new int[] {0xff68717c, 0xfff2bd60, 0xffffffff, 0xff68717c},
                new float[] {0f, 0.42f, 0.52f, 1f}, Shader.TileMode.CLAMP);
        getPaint().setShader(gradient);
        shimmer = ValueAnimator.ofFloat(-width, width * 2f);
        shimmer.setDuration(1700);
        shimmer.setRepeatCount(ValueAnimator.INFINITE);
        shimmer.setInterpolator(new LinearInterpolator());
        shimmer.addUpdateListener(animation -> {
            matrix.setTranslate((float) animation.getAnimatedValue(), 0);
            gradient.setLocalMatrix(matrix);
            invalidate();
        });
        shimmer.start();
    }

    @Override protected void onDetachedFromWindow() {
        setShimmering(false);
        super.onDetachedFromWindow();
    }
}
