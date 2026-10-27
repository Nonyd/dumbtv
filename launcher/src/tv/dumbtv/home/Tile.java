package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * A card drawn in one pass: banner, or icon plus name, or an input name; focus ring on top. With
 * Options › Focus style › Lift and glow the card instead rises and grows over a soft accent glow
 * drawn past its edges, which the parents' clipChildren(false) lets through. Lift, glow and tilt adds
 * a small 3D tilt, a light sweep across the card as it takes focus, and a glow in the app's own colour.
 */
final class Tile extends View {
    private static final float FOCUS_SCALE = 1.08f;
    private static final float GLOW_SCALE = 1.1f;
    private static final float TILT_SCALE = 1.14f;

    final Item item;
    final String kind;
    private final CharSequence title;
    private final int cardW, cardH, labelH, labelRoom;
    private final boolean labelAlways, labelOnFocus, glow, tilt;
    private final int accent;
    private final float radius, stroke, lift;
    private final Paint sweep = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** Position of the light sweep across the card, -1 when none is running. */
    private float sweepAt = -1f;
    private ValueAnimator sweeper;
    private final RectF card = new RectF();
    private final Path clip = new Path();
    private final Path arrow = new Path();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint inner = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint under = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private boolean moving;

    Tile(Context c, Item item, String kind, CharSequence title, int cardW, int cardH,
            String labels, int accent, boolean glow, boolean tilt, boolean nameInGap) {
        super(c);
        this.item = item;
        this.kind = kind;
        this.title = title;
        this.cardW = cardW;
        this.cardH = cardH;
        this.glow = glow || tilt;
        this.tilt = tilt;
        this.accent = accent;
        if (tilt) setCameraDistance(c.getResources().getDisplayMetrics().density * 6000);
        boolean app = Catalog.APPS.equals(kind);
        // With nameInGap the name is drawn past the bottom edge, in the row gap the grid leaves for it.
        labelRoom = app ? Ui.dp(c, this.glow ? 32 : 26) : 0;
        labelH = nameInGap ? 0 : labelRoom;
        labelAlways = "always".equals(labels);
        labelOnFocus = "focus".equals(labels);
        radius = this.glow ? Math.max(Ui.dp(c, 12), cardW / 14f) : Ui.dp(c, 10);
        stroke = Ui.dp(c, 2.5f);
        lift = Ui.dp(c, 8);

        fill.setColor(app ? Ui.SURFACE : 0xFF17181B);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(stroke);
        ring.setColor(accent);
        // Shadow layers on shapes draw with hardware acceleration from Android 9; Fire OS 6 shows no glow.
        halo.setColor(0xFF000000);
        halo.setShadowLayer(Ui.dp(c, 28), 0, Ui.dp(c, 10), 0x99000000 | accent & 0xFFFFFF);
        sheen.setStyle(Paint.Style.STROKE);
        sheen.setStrokeWidth(Ui.dp(c, 1));
        sheen.setColor(0x40FFFFFF);
        dot.setColor(0xFF34C759);
        inner.setColor(Ui.TEXT);
        inner.setTypeface(Ui.MEDIUM);
        inner.setTextAlign(Paint.Align.CENTER);
        inner.setTextSize(Ui.dp(c, app ? 14 : 17));
        under.setColor(Ui.TEXT);
        under.setTypeface(Ui.MEDIUM);
        under.setTextAlign(Paint.Align.CENTER);
        under.setTextSize(Ui.dp(c, 13));

        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(title);
    }

    void setMoving(boolean on) {
        moving = on;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(cardW, cardH + labelH);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        card.set(0, 0, cardW, cardH);
        clip.reset();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        setPivotX(cardW / 2f);
        setPivotY(cardH / 2f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        animate().cancel();
        if (tilt) {
            float s = focused ? TILT_SCALE : 1f;
            animate().scaleX(s).scaleY(s).translationY(focused ? -lift : 0f)
                    .rotationX(focused ? 5f : 0f).rotationY(focused ? -9f : 0f)
                    .setDuration(focused ? 240 : 180).setInterpolator(new DecelerateInterpolator()).start();
            if (sweeper != null) sweeper.cancel();
            sweepAt = -1f;
            if (focused) {
                sweeper = ValueAnimator.ofFloat(-0.5f, 1.5f).setDuration(900);
                sweeper.setStartDelay(120);
                sweeper.addUpdateListener(a -> {
                    sweepAt = (Float) a.getAnimatedValue();
                    invalidate();
                });
                sweeper.start();
            }
        } else if (glow) {
            float s = focused ? GLOW_SCALE : 1f;
            animate().scaleX(s).scaleY(s).translationY(focused ? -lift : 0f)
                    .setDuration(focused ? 200 : 160).setInterpolator(new DecelerateInterpolator()).start();
        } else if (focused) {
            animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).setDuration(110).start();
        } else {
            setScaleX(1f);
            setScaleY(1f);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Drawable banner = item.banner, icon = item.icon;
        boolean lit = glow && isFocused() && !moving;
        if (lit) {
            if (tilt) halo.setShadowLayer(Ui.dp(getContext(), 30), 0, Ui.dp(getContext(), 12), glowColor());
            canvas.drawRoundRect(card, radius, radius, halo);
        }
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(card, fill);
        if (banner != null) {
            banner.setBounds(0, 0, cardW, cardH);
            banner.draw(canvas);
        } else if (icon != null) {
            int size = cardH * 46 / 100;
            int left = (cardW - size) / 2, top = cardH * 12 / 100;
            icon.setBounds(left, top, left + size, top + size);
            icon.draw(canvas);
            drawLine(canvas, inner, cardW / 2f, cardH - cardH * 13 / 100f);
        } else if (!Catalog.APPS.equals(kind)) {
            drawLine(canvas, inner, cardW / 2f, cardH / 2f - (inner.ascent() + inner.descent()) / 2);
        }
        if (moving) canvas.drawColor(0x99000000);
        if (lit && sweepAt > -0.5f) {
            // A diagonal band of light, a third of the card wide, crossing it once.
            float band = cardW * 0.35f, x = sweepAt * cardW;
            sweep.setShader(new LinearGradient(x - band, 0, x + band, cardH * 0.7f,
                    new int[] {0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(card, sweep);
        }
        canvas.restore();

        if (item.state == TvInputManager.INPUT_STATE_CONNECTED) {
            float r = Ui.dp(getContext(), 3.5f);
            canvas.drawCircle(cardW - r * 3, r * 3, r, dot);
        }
        if (lit) {
            float half = sheen.getStrokeWidth() / 2;
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, sheen);
        } else if (isFocused() || moving) {
            float half = stroke / 2;
            ring.setPathEffect(moving ? new DashPathEffect(new float[] {stroke * 3, stroke * 2}, 0) : null);
            canvas.drawRoundRect(half, half, cardW - half, cardH - half, radius - half, radius - half, ring);
        }
        if (moving) drawArrows(canvas);
        boolean label = banner != null && (labelAlways || labelOnFocus && isFocused());
        if (labelRoom > 0 && label) {
            // A name drawn in the gap below may run wider than a narrow card.
            float room = labelH == 0 ? cardW * 1.5f : cardW - Ui.dp(getContext(), 16);
            drawLine(canvas, under, cardW / 2f, cardH + labelRoom - Ui.dp(getContext(), 6), room);
        }
    }

    /** The app's own colour (as worked out for the adaptive background), brightened for a glow. */
    private int glowColor() {
        int c = item.backgroundColor;
        if (c == 0) return 0x99000000 | accent & 0xFFFFFF;
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.15f);
        hsv[2] = Math.max(hsv[2], 0.9f);
        return 0xA6000000 | Color.HSVToColor(hsv) & 0xFFFFFF;
    }

    private void drawLine(Canvas canvas, TextPaint paint, float x, float baseline) {
        drawLine(canvas, paint, x, baseline, cardW - Ui.dp(getContext(), 16));
    }

    private void drawLine(Canvas canvas, TextPaint paint, float x, float baseline, float room) {
        CharSequence s = TextUtils.ellipsize(title, paint, room, TextUtils.TruncateAt.END);
        canvas.drawText(s, 0, s.length(), x, baseline, paint);
    }

    private void drawArrows(Canvas canvas) {
        float h = cardH / 6f, w = h * 0.7f, mid = cardH / 2f, pad = h * 0.8f;
        inner.setStyle(Paint.Style.FILL);
        arrow.reset();
        arrow.moveTo(pad, mid);
        arrow.lineTo(pad + w, mid - h / 2);
        arrow.lineTo(pad + w, mid + h / 2);
        arrow.close();
        arrow.moveTo(cardW - pad, mid);
        arrow.lineTo(cardW - pad - w, mid - h / 2);
        arrow.lineTo(cardW - pad - w, mid + h / 2);
        arrow.close();
        canvas.drawPath(arrow, inner);
    }
}
