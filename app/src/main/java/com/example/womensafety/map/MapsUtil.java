package com.example.womensafety.map;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.womensafety.R;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.MapStyleOptions;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Google Maps helpers: key check, the app's map look, and reverse geocoding. */
public final class MapsUtil {

    private static final ExecutorService GEO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MapsUtil() {
    }

    /** False when MAPS_API_KEY hasn't been put in local.properties yet. */
    public static boolean isKeyConfigured(@NonNull Context c) {
        try {
            ApplicationInfo ai = c.getPackageManager()
                    .getApplicationInfo(c.getPackageName(), PackageManager.GET_META_DATA);
            Bundle md = ai.metaData;
            String key = md == null ? null : md.getString("com.google.android.geo.API_KEY");
            return !TextUtils.isEmpty(key);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isNight(@NonNull Context c) {
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void applyStyle(@NonNull Context c, @NonNull GoogleMap map, boolean forceDark) {
        int res = forceDark || isNight(c) ? R.raw.map_style_dark : R.raw.map_style_light;
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(c, res));
        } catch (Exception ignored) {
            // Falls back to the default Google style.
        }
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setCompassEnabled(false);
    }

    /** A short place name such as "Koramangala, Bengaluru". Callback on the main thread. */
    public static void describePlace(@NonNull Context c, double lat, double lng,
                                     @NonNull Consumer<Place> callback) {
        if (!Geocoder.isPresent()) {
            callback.accept(null);
            return;
        }
        Geocoder geocoder = new Geocoder(c, Locale.getDefault());
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(lat, lng, 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(@NonNull List<Address> addresses) {
                    Place p = addresses.isEmpty() ? null : Place.from(addresses.get(0));
                    MAIN.post(() -> callback.accept(p));
                }

                @Override
                public void onError(@Nullable String errorMessage) {
                    MAIN.post(() -> callback.accept(null));
                }
            });
        } else {
            GEO.execute(() -> {
                Place p = null;
                try {
                    @SuppressWarnings("deprecation")
                    List<Address> list = geocoder.getFromLocation(lat, lng, 1);
                    if (list != null && !list.isEmpty()) p = Place.from(list.get(0));
                } catch (Exception ignored) {
                }
                final Place result = p;
                MAIN.post(() -> callback.accept(result));
            });
        }
    }

    /** A reverse-geocoded place. */
    public static final class Place {
        @Nullable
        public final String area;
        @Nullable
        public final String city;
        @Nullable
        public final String state;

        Place(@Nullable String area, @Nullable String city, @Nullable String state) {
            this.area = area;
            this.city = city;
            this.state = state;
        }

        static Place from(Address a) {
            String city = a.getLocality();
            if (TextUtils.isEmpty(city)) city = a.getSubAdminArea();
            return new Place(a.getSubLocality(), city, a.getAdminArea());
        }

        @NonNull
        public String shortName() {
            if (!TextUtils.isEmpty(area) && !TextUtils.isEmpty(city)) return area + ", " + city;
            if (!TextUtils.isEmpty(city)) return city;
            if (!TextUtils.isEmpty(state)) return state;
            return "";
        }
    }
}
