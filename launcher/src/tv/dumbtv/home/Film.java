package tv.dumbtv.home;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.text.TextPaint;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import java.io.File;
import java.io.IOException;

/**
 * The top shelf film: {@code film.mp4} pushed next to home.conf, playing on a loop across the top of
 * the home screen, cropped to fill the width from the top and fading at the bottom into the focused
 * app's colour, with "Now playing" and its title at the lower left.
 *
 * Its sound fades in to {@code film.volume} (40 %) over two seconds every time the home screen shows,
 * and stops at once when the home screen goes (an app opening, the slideshow, the screen sleeping).
 * It never takes the speakers from anything else: it stays silent while other music is playing, and
 * goes quiet for voice search and anything else that asks for audio focus.
 */
final class Film extends FrameLayout implements TextureView.SurfaceTextureListener, AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "dumbtv.Film";
    static final String FILE = "film.mp4";
    private static final long FADE_IN_MS = 2000;
    private static final long DUCK_MS = 300;

    private final TextureView video;
    private final Overlay overlay;
    private final AudioManager audio;
    private MediaPlayer player;
    private Surface surface;
    private File file;
    private int position;
    /** Whether the home screen wants the film playing (between {@link #play} and {@link #stop}). */
    private boolean wanted;
    private boolean focused;
    private float volume;
    private ValueAnimator ramp;

    Film(Context c) {
        super(c);
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        video = new TextureView(c);
        video.setSurfaceTextureListener(this);
        video.setAlpha(0f);
        addView(video, new LayoutParams(-1, -1));
        overlay = new Overlay(c);
        addView(overlay, new LayoutParams(-1, -1));
    }

    /** Looks for the film again (it may have been pushed or removed since); true when there is one. */
    boolean available() {
        file = Config.get(getContext()).media(FILE);
        return file != null;
    }

    /** The colour under the shelf, which the bottom of the film fades into. */
    void tint(int color) {
        overlay.tint(color);
    }

    /** Starts (or resumes) the film, its sound fading in. */
    void play() {
        wanted = true;
        overlay.title(Config.get(getContext()).get("film.title", "You are for Me"));
        if (surface != null && file != null) open();
    }

    /** Silences and releases the film at once, remembering where it was. */
    void stop() {
        wanted = false;
        release();
    }

    private void open() {
        if (player != null || !wanted) return;
        MediaPlayer p = new MediaPlayer();
        try {
            p.setDataSource(file.getPath());
        } catch (IOException e) {
            Log.w(TAG, "cannot open " + file + ": " + e);
            p.release();
            return;
        }
        p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build());
        p.setSurface(surface);
        p.setLooping(true);
        p.setVolume(0f, 0f);
        volume = 0f;
        p.setOnVideoSizeChangedListener((mp, w, h) -> fit(w, h));
        p.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) video.animate().alpha(1f).setDuration(600).start();
            return false;
        });
        p.setOnPreparedListener(mp -> {
            if (mp != player) return;
            // Checked before our own playback starts, which would otherwise count as music too.
            boolean otherMusic = audio.isMusicActive();
            if (position > 0) mp.seekTo(position);
            mp.start();
            if (otherMusic) {
                Log.i(TAG, "other audio is playing: the film stays silent");
            } else {
                focused = audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                        == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                if (focused) fadeTo(level(), FADE_IN_MS);
            }
            Log.i(TAG, "playing from " + position + " ms; other audio " + otherMusic + ", focus " + focused);
        });
        p.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "playback error " + what + "/" + extra);
            release();
            return true;
        });
        player = p;
        video.setAlpha(0f);
        p.prepareAsync();
    }

    private void release() {
        if (ramp != null) ramp.cancel();
        if (player != null) {
            Log.i(TAG, "stopped" + (focused ? ", focus given back" : ""));
            try {
                position = player.getCurrentPosition();
            } catch (IllegalStateException ignored) {
                // not prepared yet: keep the last position
            }
            player.setVolume(0f, 0f);
            player.release();
            player = null;
        }
        if (focused) {
            audio.abandonAudioFocus(this);
            focused = false;
        }
    }

    /** Options' film.volume, in percent; 40 when unset. */
    private float level() {
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(Config.get(getContext()).get("film.volume", "40")))) / 100f;
        } catch (NumberFormatException e) {
            return 0.4f;
        }
    }

    private void fadeTo(float target, long ms) {
        if (ramp != null) ramp.cancel();
        ramp = ValueAnimator.ofFloat(volume, target).setDuration(ms);
        ramp.addUpdateListener(a -> setVolume((Float) a.getAnimatedValue()));
        ramp.start();
    }

    private void setVolume(float v) {
        volume = v;
        if (player != null) player.setVolume(v, v);
    }

    @Override
    public void onAudioFocusChange(int change) {
        Log.i(TAG, "audio focus " + change);
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (player != null) fadeTo(level(), FADE_IN_MS);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                fadeTo(level() * 0.25f, DUCK_MS);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (ramp != null) ramp.cancel();
                setVolume(0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Something else is playing now: stay silent until the next return home.
                if (ramp != null) ramp.cancel();
                setVolume(0f);
                audio.abandonAudioFocus(this);
                focused = false;
                break;
            default:
                break;
        }
    }

    /** Scales the video to cover the view, centred across and anchored at the top. */
    private void fit(int vw, int vh) {
        int w = getWidth(), h = getHeight();
        if (vw <= 0 || vh <= 0 || w <= 0 || h <= 0) return;
        float s = Math.max(w / (float) vw, h / (float) vh);
        Matrix m = new Matrix();
        m.setScale(s * vw / w, s * vh / h);
        m.postTranslate((w - s * vw) / 2f, 0);
        video.setTransform(m);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        if (player != null) fit(player.getVideoWidth(), player.getVideoHeight());
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = new Surface(st);
        if (wanted && file != null) open();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        release();
        if (surface != null) surface.release();
        surface = null;
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }

    /** The darkened top (for the clock), the fade into the shelf colour, and the caption. */
    private static final class Overlay extends View {
        private final Paint top = new Paint(), bottom = new Paint();
        private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint song = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private int color = Color.BLACK;
        private String title = "";

        Overlay(Context c) {
            super(c);
            label.setColor(0x99FFFFFF);
            label.setTypeface(Ui.MEDIUM);
            label.setTextSize(Ui.dp(c, 11));
            label.setLetterSpacing(0.25f);
            label.setShadowLayer(Ui.dp(c, 6), 0, Ui.dp(c, 1), 0xA0000000);
            song.setColor(0xFFF3EDE4);
            song.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            song.setTextSize(Ui.dp(c, 26));
            song.setShadowLayer(Ui.dp(c, 10), 0, Ui.dp(c, 1), 0xB0000000);
        }

        void tint(int c) {
            if (c == color) return;
            color = c;
            shade(getWidth(), getHeight());
            invalidate();
        }

        void title(String t) {
            title = t == null ? "" : t;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            shade(w, h);
        }

        private void shade(int w, int h) {
            if (h <= 0) return;
            top.setShader(new LinearGradient(0, 0, 0, h * 0.3f, 0x8C000000, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            int clear = color & 0x00FFFFFF;
            bottom.setShader(new LinearGradient(0, 0, 0, h, new int[] {clear, clear, color},
                    new float[] {0f, 0.55f, 0.97f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            canvas.drawRect(0, 0, w, h, top);
            canvas.drawRect(0, 0, w, h, bottom);
            if (title.isEmpty()) return;
            float left = Ui.dp(getContext(), 42), base = h * 0.80f;
            canvas.drawText("NOW PLAYING", left, base - Ui.dp(getContext(), 34), label);
            canvas.drawText(title, left, base, song);
        }
    }
}
