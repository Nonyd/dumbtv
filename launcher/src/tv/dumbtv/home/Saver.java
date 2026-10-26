package tv.dumbtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The screensaver: the same wallpaper photos as the home screen, full screen and unblurred, each
 * cross-fading into the next after Options › Screensaver photo time, with a small clock at the
 * bottom right. It reads the launcher's own folders, so it needs no permission and no network.
 * Selected with {@code settings put secure screensaver_components tv.dumbtv.home/.Saver}.
 */
public final class Saver extends DreamService {
    private static final long FADE_MS = 2000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ImageView[] photos = new ImageView[2];
    private final Runnable advance = this::advance;
    private ExecutorService io;
    private List<File> pool = new ArrayList<>();
    private int next, front;
    private TextView clock;

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        for (int i = 0; i < photos.length; i++) {
            ImageView v = new ImageView(this);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setAlpha(0f);
            root.addView(v, new FrameLayout.LayoutParams(-1, -1));
            photos[i] = v;
        }
        clock = Ui.text(this, 26, Ui.TEXT, Ui.LIGHT);
        // A soft shadow keeps the clock readable over a bright photo.
        clock.setShadowLayer(Ui.dp(this, 6), 0, Ui.dp(this, 1), 0xB0000000);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        corner.setMargins(0, 0, Ui.dp(this, 40), Ui.dp(this, 28));
        root.addView(clock, corner);
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        io = Executors.newSingleThreadExecutor();
        Config cfg = Config.get(this);
        cfg.refresh();
        pool = new ArrayList<>(cfg.wallpapers());
        if (pool.isEmpty()) {
            File single = cfg.wallpaper();
            if (single != null) pool.add(single);
        }
        Collections.shuffle(pool);
        next = 0;
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        tick();
        advance();
    }

    @Override
    public void onDreamingStopped() {
        ui.removeCallbacks(advance);
        try {
            unregisterReceiver(time);
        } catch (IllegalArgumentException ignored) {
        }
        if (io != null) io.shutdownNow();
        for (ImageView v : photos) {
            v.animate().cancel();
            v.setImageDrawable(null);
        }
        super.onDreamingStopped();
    }

    /** Decodes the next photo off the main thread, fades it in over the current one, and re-arms. */
    private void advance() {
        if (pool.isEmpty()) return;
        File file = pool.get(next++ % pool.size());
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        io.execute(() -> {
            Bitmap b = Home.decode(file, w, h);
            ui.post(() -> {
                if (b != null) crossfade(b);
                long hold = Long.parseLong(Config.get(this).get(Config.SAVER)) * 1000L;
                if (pool.size() > 1) ui.postDelayed(advance, hold);
            });
        });
    }

    private void crossfade(Bitmap b) {
        ImageView in = photos[1 - front], out = photos[front];
        in.animate().cancel();
        out.animate().cancel();
        in.setImageBitmap(b);
        in.bringToFront();
        clock.bringToFront();
        in.animate().alpha(1f).setDuration(FADE_MS).withEndAction(() -> {
            out.setAlpha(0f);
            out.setImageDrawable(null);
        }).start();
        front = 1 - front;
    }

    private void tick() {
        String mode = Config.get(this).get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", new Date()));
        clock.setVisibility(View.VISIBLE);
    }
}
