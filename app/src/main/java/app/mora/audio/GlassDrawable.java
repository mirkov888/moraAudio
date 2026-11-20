package app.mora.audio;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Lightweight translucent glass surface with a subtle rim and top highlight. */
final class GlassDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final RectF inner = new RectF();
    private final float radius;
    private final float stroke;
    private final int tint;
    private final float strength;
    private final int under;
    private int alpha = 255;

    GlassDrawable(float radius, float stroke, int tint, float strength, int under) {
        this.radius = radius;
        this.stroke = stroke;
        this.tint = tint;
        this.strength = strength;
        this.under = under;
        fill.setStyle(Paint.Style.FILL);
        tintPaint.setStyle(Paint.Style.FILL);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
        sheen.setStyle(Paint.Style.STROKE);
        sheen.setStrokeWidth(stroke);
        tintPaint.setColor(tint);
    }

    private static int withAlpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }

    @Override protected void onBoundsChange(Rect rect) {
        float h = rect.height();
        fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
            0x26FFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            new int[]{tint == 0 ? 0x99FFFFFF : withAlpha(tint, 0xB0),
                0x1AFFFFFF, 0x55FFFFFF},
            new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, rect.top, 0,
            rect.top + Math.min(h, stroke * 40f), 0x29FFFFFF, 0x00FFFFFF,
            Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        Rect rect = getBounds();
        float half = stroke / 2f;
        bounds.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half);
        float r = Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2f);
        if (Color.alpha(under) > 0) {
            fill.setShader(null);
            fill.setColor(under);
            fill.setAlpha(under >>> 24);
            canvas.drawRoundRect(bounds, r, r, fill);
            fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                0x26FFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP));
        }
        if (strength > 0f) {
            fill.setColor(Color.WHITE);
            fill.setAlpha(alpha);
            canvas.drawRoundRect(bounds, r, r, fill);
            if (tint != 0) {
                tintPaint.setAlpha(Math.round(0x3A * alpha / 255f));
                canvas.drawRoundRect(bounds, r, r, tintPaint);
            }
        }
        rim.setAlpha(alpha);
        canvas.drawRoundRect(bounds, r, r, rim);
        if (strength > 0f) {
            inner.set(bounds.left + stroke, bounds.top + stroke,
                bounds.right - stroke, bounds.bottom - stroke);
            sheen.setAlpha(alpha);
            canvas.drawRoundRect(inner, Math.max(0f, r - stroke), Math.max(0f, r - stroke), sheen);
        }
    }

    @Override public void getOutline(Outline outline) {
        Rect b = getBounds();
        outline.setRoundRect(b, Math.min(radius, b.height() / 2f));
        outline.setAlpha(0f);
    }
    @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
