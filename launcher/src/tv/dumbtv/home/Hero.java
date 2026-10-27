package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * The top shelf picture: a full-width image that cross-fades to the next one and fades out at the
 * bottom into whatever colour is behind it (the focused app's tint), with a caption at the lower left.
 * The top edge is darkened a little so the clock stays readable over a bright picture.
 */
final class Hero extends View {
    private static final long FADE_MS = 700;

    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mask = new Paint();
    private final Paint top = new Paint();
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint line = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private Bitmap current, previous;
    private CharSequence heading = "", detail = "";
    private float fade = 1f;
    private ValueAnimator animator;

    Hero(Context c) {
        super(c);
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        title.setColor(Ui.TEXT);
        title.setTypeface(Ui.MEDIUM);
        title.setTextSize(Ui.dp(c, 22));
        title.setShadowLayer(Ui.dp(c, 8), 0, Ui.dp(c, 1), 0xA0000000);
        line.setColor(0xCCFFFFFF);
        line.setTypeface(Ui.MEDIUM);
        line.setTextSize(Ui.dp(c, 13));
        line.setShadowLayer(Ui.dp(c, 6), 0, Ui.dp(c, 1), 0xA0000000);
    }

    /** Fades {@code b} in over the current picture; a null bitmap fades to nothing. */
    void show(Bitmap b, CharSequence heading, CharSequence detail) {
        if (animator != null) animator.cancel();
        previous = current;
        current = b;
        this.heading = heading == null ? "" : heading;
        this.detail = detail == null ? "" : detail;
        animator = ValueAnimator.ofFloat(0f, 1f).setDuration(FADE_MS);
        animator.addUpdateListener(a -> {
            fade = (Float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        mask.setShader(new LinearGradient(0, 0, 0, h, new int[] {Color.BLACK, Color.BLACK, Color.TRANSPARENT},
                new float[] {0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        top.setShader(new LinearGradient(0, 0, 0, h * 0.3f, 0x8C000000, Color.TRANSPARENT, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        int layer = canvas.saveLayer(0, 0, w, h, null);
        if (previous != null && fade < 1f) draw(canvas, previous, Math.round(255 * (1f - fade)));
        if (current != null) draw(canvas, current, Math.round(255 * fade));
        canvas.drawRect(0, 0, w, h, top);
        canvas.drawRect(0, 0, w, h, mask);
        canvas.restoreToCount(layer);

        float left = Ui.dp(getContext(), 42), room = w * 0.6f;
        float base = h * 0.80f;
        if (detail.length() > 0) {
            CharSequence d = TextUtils.ellipsize(detail, line, room, TextUtils.TruncateAt.END);
            canvas.drawText(d, 0, d.length(), left, base + Ui.dp(getContext(), 20), line);
        }
        if (heading.length() > 0) {
            CharSequence t = TextUtils.ellipsize(heading, title, room, TextUtils.TruncateAt.END);
            canvas.drawText(t, 0, t.length(), left, base, title);
        }
    }

    /** {@code b} scaled to cover the view, centred, at {@code alpha}. */
    private void draw(Canvas canvas, Bitmap b, int alpha) {
        int w = getWidth(), h = getHeight();
        float scale = Math.max(w / (float) b.getWidth(), h / (float) b.getHeight());
        int sw = Math.round(w / scale), sh = Math.round(h / scale);
        int sx = (b.getWidth() - sw) / 2, sy = Math.max(0, (b.getHeight() - sh) * 2 / 5);
        src.set(sx, sy, sx + sw, sy + sh);
        dst.set(0, 0, w, h);
        image.setAlpha(alpha);
        canvas.drawBitmap(b, src, dst, image);
    }
}
