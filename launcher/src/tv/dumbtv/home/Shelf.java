package tv.dumbtv.home;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the top shelf ({@link Hero}) shows. In the net build, with a TMDB API key in the
 * {@code tmdb.key} file next to home.conf: backdrops of what is popular on the focused app's service
 * in {@code tmdb.region}, or what is trending this week for apps TMDB does not list. Otherwise, and
 * whenever that fails, the wallpaper photos.
 *
 * It talks to two hosts only, both fixed here: api.themoviedb.org for lists and image.tmdb.org for
 * pictures. It sends the key, the region and the service, nothing about the viewer. Lists are cached
 * for six hours and pictures until the cache passes {@link #MAX_IMAGES} files.
 */
final class Shelf {
    private static final String TAG = "dumbtv.Shelf";
    private static final String API = "https://api.themoviedb.org/3/";
    private static final String IMAGES = "https://image.tmdb.org/t/p/w1280";
    private static final String TRENDING = "trending";
    private static final long LIST_TTL_MS = 6 * 3_600_000L;
    private static final long HOLD_MS = 8_000;
    private static final long SETTLE_MS = 350;
    private static final int MAX_IMAGES = 80;

    /** TMDB watch-provider ids for the apps it knows, by package; several ids are OR-ed. */
    private static final Map<String, String> PROVIDERS = new HashMap<>();
    static {
        PROVIDERS.put("com.netflix.ninja", "8");
        PROVIDERS.put("com.amazon.firebat", "9|119");
        PROVIDERS.put("com.disney.disneyplus", "337");
        PROVIDERS.put("com.hbo.hbonow", "1899|384");
        PROVIDERS.put("com.tubitv.ott", "73");
        PROVIDERS.put("tv.pluto.android", "300");
    }

    private static final class Pick {
        /** {@code region} is where the list came from: tmdb.region, or US when that had none. */
        final String title, year, backdrop, region;

        Pick(String title, String year, String backdrop, String region) {
            this.title = title;
            this.year = year;
            this.backdrop = backdrop;
            this.region = region;
        }
    }

    private final Context context;
    private final Hero hero;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable next = this::next;
    private final Runnable settle = this::load;
    private String wanted, showing, label = "";
    private List<Pick> picks = Collections.emptyList();
    private List<File> photos = Collections.emptyList();
    private int index, generation;
    private boolean running;
    /** Whether the shown list was loaded with a key in place; a key added later forces a reload. */
    private volatile boolean onlineAtLoad;

    Shelf(Context context, Hero hero) {
        this.context = context;
        this.hero = hero;
    }

    /** True in the net build (INTERNET granted) with a key in place. */
    static boolean online(Context c) {
        return c.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
                && Config.get(c).secret("tmdb.key") != null;
    }

    /** Follows the focused tile: {@code pkg} is its package, or null for anything that is not an app. */
    void focus(String pkg, CharSequence appLabel) {
        String key = pkg != null && PROVIDERS.containsKey(pkg) ? pkg : TRENDING;
        if (key.equals(wanted)) return;
        wanted = key;
        label = appLabel == null ? "" : appLabel.toString();
        ui.removeCallbacks(settle);
        ui.postDelayed(settle, SETTLE_MS);
    }

    /** Starts cycling; a no-op while already running, so focus changes can call it freely. */
    void resume() {
        if (running) return;
        running = true;
        if (wanted == null) {
            focus(null, null);
        } else if (!picks.isEmpty() || !photos.isEmpty()) {
            ui.removeCallbacks(next);
            ui.postDelayed(next, HOLD_MS);
        }
    }

    /** On a return home: reloads the current list if a key was added or removed since it loaded. */
    void recheck() {
        if (showing != null && online(context) != onlineAtLoad) {
            ui.removeCallbacks(settle);
            ui.post(settle);
        }
    }

    void pause() {
        running = false;
        ui.removeCallbacks(next);
        ui.removeCallbacks(settle);
    }

    /** Fetches (or reads from cache) the list for {@link #wanted}, then shows its first picture. */
    private void load() {
        final String key = wanted, appLabel = label;
        // Focus often passes through another tile and straight back (a re-render, a row refresh):
        // returning to what is already up keeps it, instead of starting the list over.
        if (key.equals(showing) && (!picks.isEmpty() || !photos.isEmpty()) && online(context) == onlineAtLoad) {
            if (running) {
                ui.removeCallbacks(next);
                ui.postDelayed(next, HOLD_MS);
            }
            return;
        }
        final int gen = ++generation;
        ui.removeCallbacks(next);
        io.execute(() -> {
            List<Pick> list = Collections.emptyList();
            onlineAtLoad = online(context);
            if (onlineAtLoad) {
                try {
                    list = list(key);
                } catch (Exception e) {
                    Log.w(TAG, "TMDB list for " + key + " failed: " + e);
                }
            }
            List<File> pool = list.isEmpty() ? new ArrayList<>(Config.get(context).wallpapers()) : Collections.<File>emptyList();
            Collections.shuffle(pool);
            final List<Pick> got = list;
            Log.i(TAG, "shelf " + key + ": " + list.size() + " TMDB picks, " + pool.size() + " photos");
            ui.post(() -> {
                if (gen != generation) return;
                showing = key;
                picks = got;
                photos = pool;
                index = 0;
                label = appLabel;
                next();
            });
        });
    }

    private String caption(Pick p) {
        if (TRENDING.equals(showing)) return "Trending this week";
        String country = new Locale("", p.region).getDisplayCountry(Locale.ENGLISH);
        return "Popular on " + label + " in " + ("US".equals(p.region) ? "the " + country : country);
    }

    /** Shows the next picture of the current list and re-arms. */
    private void next() {
        ui.removeCallbacks(next);
        final int gen = generation;
        if (!picks.isEmpty()) {
            final Pick p = picks.get(index++ % picks.size());
            io.execute(() -> {
                Bitmap b = null;
                try {
                    b = backdrop(p.backdrop);
                } catch (Exception e) {
                    Log.w(TAG, "backdrop " + p.backdrop + " failed: " + e);
                }
                final Bitmap shown = b;
                ui.post(() -> {
                    if (gen != generation) return;
                    if (shown != null) hero.show(shown, p.year.isEmpty() ? p.title : p.title + "  (" + p.year + ")", caption(p));
                    if (running && picks.size() > 1) ui.postDelayed(next, HOLD_MS);
                });
            });
        } else if (!photos.isEmpty()) {
            final File f = photos.get(index++ % photos.size());
            int w = context.getResources().getDisplayMetrics().widthPixels;
            io.execute(() -> {
                Bitmap b = Home.decode(f, w, w * 9 / 16);
                Log.i(TAG, "photo " + f.getName() + " -> " + (b == null ? "null" : b.getWidth() + "x" + b.getHeight()));
                ui.post(() -> {
                    if (gen != generation) return;
                    if (b != null) hero.show(b, "", "");
                    if (running && photos.size() > 1) ui.postDelayed(next, HOLD_MS * 2);
                });
            });
        }
    }

    private String region() {
        String r = Config.get(context).get("tmdb.region", Locale.getDefault().getCountry());
        return r == null || r.isEmpty() ? "US" : r.toUpperCase(Locale.ROOT);
    }

    /** The list for {@code key}, from a cache younger than {@link #LIST_TTL_MS} or from TMDB. */
    private List<Pick> list(String key) throws IOException, org.json.JSONException {
        String region = region();
        File cache = new File(context.getCacheDir(), "shelf2-" + key.replace('.', '_') + "-" + region + ".json");
        String body = null;
        if (cache.isFile() && System.currentTimeMillis() - cache.lastModified() < LIST_TTL_MS) {
            body = read(new java.io.FileInputStream(cache));
        }
        if (body == null) {
            String apiKey = URLEncoder.encode(Config.get(context).secret("tmdb.key"), "UTF-8");
            JSONArray merged = new JSONArray();
            String used = region;
            if (TRENDING.equals(key)) {
                append(merged, get(API + "trending/all/week?language=en-US&api_key=" + apiKey));
            } else {
                discover(merged, key, region, apiKey);
                // Services not offered in tmdb.region (Disney+, Max, Tubi, Pluto TV in Nigeria, used
                // through a VPN) have no list there: show the US one, and say so in the caption.
                if (merged.length() == 0 && !"US".equals(region)) {
                    used = "US";
                    discover(merged, key, used, apiKey);
                }
            }
            body = new JSONObject().put("region", used).put("results", merged).toString();
            try (OutputStream out = new FileOutputStream(cache)) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        JSONObject cached = new JSONObject(body);
        String from = cached.optString("region", region);
        JSONArray all = cached.getJSONArray("results");
        List<Pick> out = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.getJSONObject(i);
            String backdrop = o.optString("backdrop_path", "");
            if (backdrop.isEmpty() || "null".equals(backdrop)) continue;
            String title = o.optString("title", o.optString("name", ""));
            String date = o.optString("release_date", o.optString("first_air_date", ""));
            out.add(new Pick(title, date.length() >= 4 ? date.substring(0, 4) : "", backdrop, from));
        }
        Collections.shuffle(out);
        return out.size() > 20 ? new ArrayList<>(out.subList(0, 20)) : out;
    }

    /** Popular movies, then TV, on {@code key}'s service in {@code region}. */
    private static void discover(JSONArray into, String key, String region, String apiKey)
            throws IOException, org.json.JSONException {
        String providers = URLEncoder.encode(PROVIDERS.get(key), "UTF-8");
        for (String type : new String[] {"movie", "tv"}) {
            append(into, get(API + "discover/" + type + "?language=en-US&include_adult=false"
                    + "&sort_by=popularity.desc&watch_region=" + region
                    + "&with_watch_providers=" + providers + "&api_key=" + apiKey));
        }
    }

    private static void append(JSONArray into, String json) throws org.json.JSONException {
        JSONArray results = new JSONObject(json).optJSONArray("results");
        if (results == null) return;
        for (int i = 0; i < results.length(); i++) into.put(results.get(i));
    }

    /** The picture for {@code path}, downloaded once into the cache. */
    private Bitmap backdrop(String path) throws IOException {
        File dir = new File(context.getCacheDir(), "backdrops");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no cache dir");
        File f = new File(dir, path.replace("/", ""));
        if (!f.isFile()) {
            HttpURLConnection c = open(IMAGES + path);
            File tmp = new File(dir, f.getName() + ".part");
            try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            } finally {
                c.disconnect();
            }
            if (!tmp.renameTo(f)) throw new IOException("cache write failed");
            trim(dir);
        }
        int w = context.getResources().getDisplayMetrics().widthPixels;
        return Home.decode(f, w, w * 9 / 16);
    }

    /** Keeps the newest {@link #MAX_IMAGES} pictures. */
    private static void trim(File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length <= MAX_IMAGES) return;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = MAX_IMAGES; i < files.length; i++) {
            if (!files[i].delete()) Log.w(TAG, "could not trim " + files[i]);
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            return read(c.getInputStream());
        } finally {
            c.disconnect();
        }
    }

    /** Opens {@code url}, refusing anything but the two TMDB hosts over HTTPS. */
    private static HttpURLConnection open(String url) throws IOException {
        URL u = new URL(url);
        String host = u.getHost();
        if (!"https".equals(u.getProtocol())
                || !("api.themoviedb.org".equals(host) || "image.tmdb.org".equals(host))) {
            throw new IOException("host not allowed: " + host);
        }
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        return c;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = s.read(buf)) > 0; ) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
