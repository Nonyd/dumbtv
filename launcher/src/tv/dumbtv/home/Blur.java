package tv.dumbtv.home;

import android.graphics.Bitmap;

/**
 * Wallpaper blur for Android 7.1 to 11, which have no RenderEffect: the image is shrunk, box-blurred
 * three times (close to a gaussian) and left small. The ImageView's filtered upscale smooths it further,
 * so the cost is paid once per wallpaper on the io thread and nothing is blurred per frame.
 */
final class Blur {
    private Blur() {}

    /** A blurred copy of {@code src} for Options › Image blur, or {@code src} itself when it is off. */
    static Bitmap of(Bitmap src, String mode) {
        if (src == null || "off".equals(mode)) return src;
        boolean strong = "strong".equals(mode);
        int shrink = strong ? 12 : 8, radius = strong ? 4 : 3;
        int w = Math.max(1, src.getWidth() / shrink), h = Math.max(1, src.getHeight() / shrink);
        Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) box(px, w, h, radius);
        Bitmap out = small.isMutable() ? small : small.copy(Bitmap.Config.ARGB_8888, true);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }

    /** One horizontal and one vertical box blur of {@code radius} over opaque ARGB pixels, in place. */
    static void box(int[] px, int w, int h, int radius) {
        if (radius < 1 || w < 1 || h < 1) return;
        int[] tmp = new int[px.length];
        pass(px, tmp, w, h, radius, 1, w);
        pass(tmp, px, h, w, radius, w, 1);
    }

    /**
     * Blurs {@code lines} lines of {@code len} pixels from {@code in} to {@code out}; {@code step} moves
     * along a line and {@code next} to the next line. Edges repeat the end pixel.
     */
    private static void pass(int[] in, int[] out, int len, int lines, int radius, int step, int next) {
        int span = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int base = line * next, r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int p = in[base + clamp(i, len) * step];
                r += p >> 16 & 255;
                g += p >> 8 & 255;
                b += p & 255;
            }
            for (int i = 0; i < len; i++) {
                out[base + i * step] = 0xFF000000 | (r / span) << 16 | (g / span) << 8 | b / span;
                int add = in[base + clamp(i + radius + 1, len) * step];
                int drop = in[base + clamp(i - radius, len) * step];
                r += (add >> 16 & 255) - (drop >> 16 & 255);
                g += (add >> 8 & 255) - (drop >> 8 & 255);
                b += (add & 255) - (drop & 255);
            }
        }
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : i >= len ? len - 1 : i;
    }
}
