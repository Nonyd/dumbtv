package tv.dumbtv.home;

/** Host-side checks for the wallpaper blur's pixel path, without a device or test framework. */
public final class BlurTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        int w = 9, h = 5;
        int[] flat = new int[w * h];
        for (int i = 0; i < flat.length; i++) flat[i] = 0xFF336699;
        Blur.box(flat, w, h, 2);
        for (int p : flat) check(p == 0xFF336699, "a flat image stays flat");

        int[] dot = new int[w * h];
        for (int i = 0; i < dot.length; i++) dot[i] = 0xFF000000;
        dot[2 * w + 4] = 0xFFFFFFFF;
        Blur.box(dot, w, h, 1);
        int center = dot[2 * w + 4] & 255, beside = dot[2 * w + 5] & 255, far = dot[2 * w + 8] & 255;
        check(center > 0 && center < 255, "a bright dot spreads out");
        check(beside > 0, "its neighbours light up");
        check(far == 0, "pixels outside the radius stay dark");
        for (int p : dot) check(p >>> 24 == 255, "output stays opaque");

        int[] one = {0xFF102030};
        Blur.box(one, 1, 1, 3);
        check(one[0] == 0xFF102030, "a one-pixel image survives a large radius");

        check("off".equals(Config.BLUR.fallback), "blur is off unless asked for");
        check("ring".equals(Config.FOCUS.fallback), "upstream focus ring stays the default");
        check("auto".equals(Config.COLUMNS.fallback), "grid columns follow tile size by default");
        System.out.println("BlurTest passed");
    }
}
