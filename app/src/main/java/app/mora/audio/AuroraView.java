package app.mora.audio;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Dark aurora backdrop; animation pauses when the Activity is not visible. */
final class AuroraView extends View {
    private static final int[] COLORS = {0xFF2FC9B8, 0xFF5B7CFF, 0xFF9B5CFF};
    private static final float[] ALPHA = {0.30f, 0.25f, 0.18f};
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] blobs = new Paint[COLORS.length];
    private final RadialGradient[] shaders = new RadialGradient[COLORS.length];
    private final Matrix matrix = new Matrix();
    private ValueAnimator animator;
    private float phase;

    AuroraView(Context context) {
        super(context);
        for (int i = 0; i < blobs.length; i++) blobs[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (w <= 0 || h <= 0) return;
        base.setShader(new LinearGradient(0, 0, 0, h,
            0xFF05080D, 0xFF0A1220, Shader.TileMode.CLAMP));
        float radius = Math.max(w, h) * 0.55f;
        for (int i = 0; i < COLORS.length; i++) {
            int solid = (Math.round(ALPHA[i] * 255f) << 24) | (COLORS[i] & 0x00FFFFFF);
            shaders[i] = new RadialGradient(0, 0, radius, solid,
                COLORS[i] & 0x00FFFFFF, Shader.TileMode.CLAMP);
            blobs[i].setShader(shaders[i]);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, h, base);
        double t = phase * 2.0 * Math.PI;
        for (int i = 0; i < blobs.length; i++) {
            if (shaders[i] == null) continue;
            double angle = t * (1 + i) + i * 2.1;
            float x = (float) (w * (0.20 + 0.30 * i + 0.14 * Math.cos(angle)));
            float y = (float) (h * (0.18 + 0.30 * i + 0.12 * Math.sin(angle * 0.8)));
            matrix.setTranslate(x, y);
            shaders[i].setLocalMatrix(matrix);
            canvas.drawRect(0, 0, w, h, blobs[i]);
        }
    }

    void setRunning(boolean run) {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (!run || !ValueAnimator.areAnimatorsEnabled()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(90000);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }
}
