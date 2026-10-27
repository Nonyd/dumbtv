package tv.dumbtv.home;

import android.service.dreams.DreamService;

/**
 * The {@link Slideshow} as an Android screensaver ("dream"), for TVs that run third-party dreams.
 * Selected with {@code settings put secure screensaver_components tv.dumbtv.home/.Saver}.
 *
 * Fire OS 8 does not run it: at the screen timeout its AmbientManagerService looks for an
 * AmbientService (only Amazon's com.amazon.tv.ftvambient has one) and, finding none enabled, puts
 * the device to sleep without starting any dream. There, Options › Photo slideshow after shows the
 * same slideshow from the home screen instead.
 */
public final class Saver extends DreamService {
    private Slideshow show;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        show = new Slideshow(this);
        setContentView(show);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        show.start();
    }

    @Override
    public void onDreamingStopped() {
        show.stop();
        super.onDreamingStopped();
    }
}
