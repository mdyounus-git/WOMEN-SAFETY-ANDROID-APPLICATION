package com.example.womensafety.news;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Daily news from Google News RSS feeds: free, no API key, India edition in English.
 * Results are cached in memory for a few minutes.
 */
public final class NewsRepository {

    private static final String TAG = "News";
    private static final String BASE = "https://news.google.com/rss";
    private static final String EDITION = "hl=en-IN&gl=IN&ceid=IN:en";
    private static final long CACHE_MS = 10 * 60_000L;
    private static final int MAX_ITEMS = 40;

    private static final ExecutorService IO = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Cached> CACHE = new HashMap<>();

    private NewsRepository() {
    }

    public interface Callback {
        void onResult(@NonNull List<NewsItem> items);

        void onError(@NonNull Exception e);
    }

    private static final class Cached {
        final long at;
        final List<NewsItem> items;

        Cached(long at, List<NewsItem> items) {
            this.at = at;
            this.items = items;
        }
    }

    @NonNull
    public static String topStoriesUrl() {
        return BASE + "?" + EDITION;
    }

    /** A search feed limited to the last {@code days} days. */
    @NonNull
    public static String searchUrl(@NonNull String query, int days) {
        return BASE + "/search?q=" + Uri.encode(query + " when:" + days + "d") + "&" + EDITION;
    }

    public static void load(@NonNull String url, boolean force, @NonNull Callback callback) {
        synchronized (CACHE) {
            Cached c = CACHE.get(url);
            if (!force && c != null && System.currentTimeMillis() - c.at < CACHE_MS) {
                List<NewsItem> items = c.items;
                MAIN.post(() -> callback.onResult(items));
                return;
            }
        }
        IO.execute(() -> {
            try {
                List<NewsItem> items = fetch(url);
                synchronized (CACHE) {
                    CACHE.put(url, new Cached(System.currentTimeMillis(), items));
                }
                MAIN.post(() -> callback.onResult(items));
            } catch (Exception e) {
                Log.w(TAG, "Feed failed", e);
                MAIN.post(() -> callback.onError(e));
            }
        });
    }

    private static List<NewsItem> fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) WomenSafety/6");
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IllegalStateException("HTTP " + code);
            try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
                return parse(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    /** Parses RSS 2.0 &lt;item&gt; elements. Package-private for tests. */
    static List<NewsItem> parse(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(in, null);
        List<NewsItem> items = new ArrayList<>();
        String title = null, link = null, source = null, date = null;
        boolean inItem = false;
        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT && items.size() < MAX_ITEMS) {
            if (event == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("item".equals(tag)) {
                    inItem = true;
                    title = link = source = date = null;
                } else if (inItem) {
                    switch (tag) {
                        case "title":
                            title = p.nextText();
                            break;
                        case "link":
                            link = p.nextText();
                            break;
                        case "pubDate":
                            date = p.nextText();
                            break;
                        case "source":
                            source = p.nextText();
                            break;
                        default:
                            break;
                    }
                }
            } else if (event == XmlPullParser.END_TAG && "item".equals(p.getName())) {
                inItem = false;
                NewsItem item = build(title, link, source, date);
                if (item != null) items.add(item);
            }
            event = p.next();
        }
        Collections.sort(items, (a, b) -> Long.compare(b.publishedAt, a.publishedAt));
        return items;
    }

    private static NewsItem build(String title, String link, String source, String date) {
        if (title == null || link == null) return null;
        String t = title.trim();
        String s = source == null ? "" : source.trim();
        // Google News titles end with " - Source"; drop it since we show the source separately.
        if (!s.isEmpty() && t.endsWith(" - " + s)) t = t.substring(0, t.length() - s.length() - 3);
        return new NewsItem(t, s, link.trim(), parseDate(date));
    }

    static long parseDate(String rfc822) {
        if (rfc822 == null) return 0;
        SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
        try {
            java.util.Date d = f.parse(rfc822.trim());
            return d == null ? 0 : d.getTime();
        } catch (ParseException e) {
            return 0;
        }
    }
}
