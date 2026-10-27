package tv.dumbtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.Gravity;
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
 * The wallpaper photos full screen and unblurred, shuffled, each cross-fading into the next after
 * Options › Screensaver photo time, with a small clock at the bottom right. Shown by the home screen
 * when it sits idle ({@link Home}) and by the {@link Saver} dream. Reads the launcher's own folders
 * only: no permission, no network.
 */
final class Slideshow extends FrameLayout {
    private static final long FADE_MS = 2000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ImageView[] photos = new ImageView[2];
    private final TextView clock;
    private final Runnable advance = this::advance;
    private ExecutorService io;
    private List<File> pool = new ArrayList<>();
    private int next, front;
    private boolean running;

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    Slideshow(Context c) {
        super(c);
        setBackgroundColor(Color.BLACK);
        for (int i = 0; i < photos.length; i++) {
            ImageView v = new ImageView(c);
            v.setScaleType(ImageView.ScaleType.CENTER_CROP);
            v.setAlpha(0f);
            addView(v, new LayoutParams(-1, -1));
            photos[i] = v;
        }
        clock = Ui.text(c, 26, Ui.TEXT, Ui.LIGHT);
        // A soft shadow keeps the clock readable over a bright photo.
        clock.setShadowLayer(Ui.dp(c, 6), 0, Ui.dp(c, 1), 0xB0000000);
        LayoutParams corner = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        corner.setMargins(0, 0, Ui.dp(c, 40), Ui.dp(c, 28));
        addView(clock, corner);
    }

    /** Starts from a fresh shuffle of the photos; a no-op while already running. */
    void start() {
        if (running) return;
        running = true;
        io = Executors.newSingleThreadExecutor();
        Config cfg = Config.get(getContext());
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
        getContext().registerReceiver(time, f);
        tick();
        advance();
    }

    /** Stops cycling and drops the decoded photos. */
    void stop() {
        if (!running) return;
        running = false;
        ui.removeCallbacks(advance);
        try {
            getContext().unregisterReceiver(time);
        } catch (IllegalArgumentException ignored) {
        }
        io.shutdownNow();
        for (ImageView v : photos) {
            v.animate().cancel();
            v.setAlpha(0f);
            v.setImageDrawable(null);
        }
    }

    /** Decodes the next photo off the main thread, fades it in over the current one, and re-arms. */
    private void advance() {
        if (!running || pool.isEmpty()) return;
        File file = pool.get(next++ % pool.size());
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        io.execute(() -> {
            Bitmap b = Home.decode(file, w, h);
            ui.post(() -> {
                if (!running) return;
                if (b != null) crossfade(b);
                long hold = Long.parseLong(Config.get(getContext()).get(Config.SAVER)) * 1000L;
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
        String mode = Config.get(getContext()).get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(getContext());
        clock.setText(DateFormat.format(h24 ? "HH:mm" : "h:mm", new Date()));
    }
}
