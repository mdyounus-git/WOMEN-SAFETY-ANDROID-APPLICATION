package com.example.womensafety.map;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads the bundled NCRB city figures from assets/crime_stats.json. */
public final class CrimeStatsRepository {

    private static final String TAG = "CrimeStats";
    private static List<CityStat> cache;

    private CrimeStatsRepository() {
    }

    @NonNull
    public static synchronized List<CityStat> all(@NonNull Context context) {
        if (cache != null) return cache;
        List<CityStat> list = new ArrayList<>();
        try (InputStream in = context.getAssets().open("crime_stats.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            JSONObject root = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            JSONObject sources = root.getJSONObject("sources");
            JSONArray cities = root.getJSONArray("cities");
            for (int i = 0; i < cities.length(); i++) {
                JSONObject c = cities.getJSONObject(i);
                JSONObject src = sources.getJSONObject(c.getString("source"));
                list.add(new CityStat(
                        c.getString("name"),
                        c.optString("state", ""),
                        c.getDouble("lat"),
                        c.getDouble("lng"),
                        c.has("rate") ? c.getDouble("rate") : null,
                        c.has("cases") ? c.getInt("cases") : null,
                        src.getInt("year"),
                        src.getString("unit"),
                        src.getString("title"),
                        src.getDouble("baseline"),
                        src.getString("baselineLabel")));
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not read crime_stats.json", e);
        }
        cache = Collections.unmodifiableList(list);
        return cache;
    }

    /** The closest city within {@code maxKm}, or null. */
    @Nullable
    public static CityStat nearest(@NonNull Context context, double lat, double lng, double maxKm) {
        CityStat best = null;
        double bestKm = maxKm;
        for (CityStat c : all(context)) {
            double km = distanceKm(lat, lng, c.lat, c.lng);
            if (km <= bestKm) {
                bestKm = km;
                best = c;
            }
        }
        return best;
    }

    /** Haversine distance. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
