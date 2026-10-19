package com.example.womensafety.map;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.womensafety.R;
import com.example.womensafety.core.Permissions;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.FragmentMapBinding;
import com.example.womensafety.databinding.ItemNewsBinding;
import com.example.womensafety.news.NewsItem;
import com.example.womensafety.news.NewsRepository;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Google Map of crimes against women by city (official NCRB figures), coloured relative to the
 * average, with the latest local news for the selected city underneath.
 */
public class CrimeMapFragment extends Fragment implements GoogleMap.OnMarkerClickListener {

    private static final LatLng INDIA = new LatLng(22.5, 79.0);
    private static final double NEAR_KM = 60;

    private FragmentMapBinding b;
    private GoogleMap map;
    private final Map<CityStat.Level, BitmapDescriptor> icons = new EnumMap<>(CityStat.Level.class);
    @Nullable
    private String selectedCity;
    private String newsUrl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentMapBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.padForSystemBars(b.top, true, false);
        BottomSheetBehavior<View> sheet = BottomSheetBehavior.from(b.sheet);
        sheet.setPeekHeight(Ui.dp(requireContext(), 232) + Ui.dp(requireContext(), 96));
        b.myLocation.setOnClickListener(v -> goToMyLocation(true));

        if (!MapsUtil.isKeyConfigured(requireContext())) {
            b.mapMissing.setVisibility(View.VISIBLE);
        } else {
            SupportMapFragment f = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
            if (f != null) f.getMapAsync(this::onMapReady);
        }
        showInitialCity();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
        map = null;
    }

    @SuppressLint("MissingPermission")
    private void onMapReady(@NonNull GoogleMap m) {
        if (b == null) return;
        map = m;
        MapsUtil.applyStyle(requireContext(), m, false);
        m.setPadding(0, Ui.dp(requireContext(), 150), 0, Ui.dp(requireContext(), 330));
        if (Permissions.hasLocation(requireContext())) {
            m.setMyLocationEnabled(true);
            m.getUiSettings().setMyLocationButtonEnabled(false);
        }
        m.setOnMarkerClickListener(this);

        for (CityStat c : CrimeStatsRepository.all(requireContext())) {
            LatLng pos = new LatLng(c.lat, c.lng);
            int color = ContextCompat.getColor(requireContext(), colorFor(c.level()));
            Marker marker = m.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(c.name)
                    .anchor(0.5f, 0.5f)
                    .icon(iconFor(c.level())));
            if (marker != null) marker.setTag(c);
            if (c.rate != null) {
                m.addCircle(new CircleOptions().center(pos).radius(9_000 + 40 * c.rate)
                        .strokeWidth(0f).fillColor((color & 0x00FFFFFF) | 0x33000000));
            }
        }

        Prefs prefs = Prefs.get(requireContext());
        if (prefs.hasLastPlace()) {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(prefs.getLastLat(), prefs.getLastLng()), 9.5f));
        } else {
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(INDIA, 4.4f));
        }
        goToMyLocation(false);
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Object tag = marker.getTag();
        if (tag instanceof CityStat) {
            showCity((CityStat) tag);
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 10f));
            BottomSheetBehavior.from(b.sheet).setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
        return true;
    }

    // ------------------------------------------------------------------ city panel

    private void showInitialCity() {
        Prefs prefs = Prefs.get(requireContext());
        if (prefs.hasLastPlace()) {
            CityStat near = CrimeStatsRepository.nearest(requireContext(), prefs.getLastLat(),
                    prefs.getLastLng(), NEAR_KM);
            if (near != null) {
                showCity(near);
                return;
            }
            if (!prefs.getLastCity().isEmpty()) {
                showUnlistedCity(prefs.getLastCity());
                return;
            }
        }
        showUnlistedCity("");
    }

    @SuppressLint("MissingPermission")
    private void goToMyLocation(boolean animate) {
        if (!Permissions.hasLocation(requireContext())) return;
        LocationServices.getFusedLocationProviderClient(requireContext()).getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc == null || b == null) return;
                    LatLng me = new LatLng(loc.getLatitude(), loc.getLongitude());
                    if (map != null) {
                        if (animate) map.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 11f));
                        else map.moveCamera(CameraUpdateFactory.newLatLngZoom(me, 10f));
                    }
                    CityStat near = CrimeStatsRepository.nearest(requireContext(), me.latitude, me.longitude, NEAR_KM);
                    MapsUtil.describePlace(requireContext(), me.latitude, me.longitude, place -> {
                        if (b == null) return;
                        if (place != null && place.city != null) {
                            Prefs.get(requireContext()).setLastPlace(place.city, me.latitude, me.longitude);
                        }
                        if (near != null) showCity(near);
                        else showUnlistedCity(place == null || place.city == null ? "" : place.city);
                    });
                });
    }

    private void showCity(@NonNull CityStat c) {
        if (b == null) return;
        Context ctx = requireContext();
        b.cityName.setText(c.name);
        b.cityMeta.setText(getString(R.string.map_city_meta, c.state, c.year));
        CityStat.Level level = c.level();
        b.levelPill.setVisibility(View.VISIBLE);
        b.levelPill.setText(labelFor(level));
        b.levelPill.setBackgroundTintList(ContextCompat.getColorStateList(ctx, colorFor(level)));

        NumberFormat nf = NumberFormat.getIntegerInstance(new Locale("en", "IN"));
        if (c.rate != null) {
            b.rate.setText(String.format(Locale.US, "%.1f", c.rate));
            b.rateUnit.setText(getString(R.string.map_rate_unit, c.unit));
            String cmp = getString(R.string.map_comparison, String.format(Locale.US, "%.1f", c.ratio()),
                    c.baselineLabel);
            if (c.cases != null) cmp += " · " + getString(R.string.map_cases, nf.format(c.cases));
            b.comparison.setText(cmp);
        } else {
            b.rate.setText("—");
            b.rateUnit.setText(R.string.map_rate_missing);
            b.comparison.setText(c.cases == null ? ""
                    : getString(R.string.map_cases_year, nf.format(c.cases), c.year));
        }
        b.source.setText(getString(R.string.map_source, c.sourceTitle));
        loadNews(c.name);
    }

    private void showUnlistedCity(@NonNull String city) {
        if (b == null) return;
        b.cityName.setText(city.isEmpty() ? getString(R.string.map_pick_city) : city);
        b.cityMeta.setText(city.isEmpty() ? getString(R.string.map_pick_city_hint) : getString(R.string.map_not_listed));
        b.levelPill.setVisibility(View.GONE);
        b.rate.setText("—");
        b.rateUnit.setText(R.string.map_rate_missing);
        b.comparison.setText("");
        b.source.setText("");
        loadNews(city.isEmpty() ? "India" : city);
    }

    private void loadNews(@NonNull String city) {
        if (city.equals(selectedCity)) return;
        selectedCity = city;
        b.newsLabel.setText(getString(R.string.map_news_label, city));
        b.newsList.removeAllViews();
        b.newsEmpty.setVisibility(View.VISIBLE);
        b.newsEmpty.setText(R.string.map_news_loading);
        final String url = NewsRepository.searchUrl(city + " (crime OR safety OR police) women", 30);
        newsUrl = url;
        NewsRepository.load(url, false, new NewsRepository.Callback() {
            @Override
            public void onResult(@NonNull List<NewsItem> items) {
                if (b == null || !url.equals(newsUrl)) return;
                b.newsList.removeAllViews();
                int n = Math.min(items.size(), 5);
                b.newsEmpty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
                b.newsEmpty.setText(R.string.map_news_none);
                for (int i = 0; i < n; i++) addNewsRow(items.get(i), i == n - 1);
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (b == null || !url.equals(newsUrl)) return;
                b.newsEmpty.setText(R.string.news_error);
            }
        });
    }

    private void addNewsRow(NewsItem item, boolean last) {
        ItemNewsBinding row = ItemNewsBinding.inflate(getLayoutInflater(), b.newsList, false);
        row.source.setText(item.source);
        row.title.setText(item.title);
        row.time.setText(Ui.relativeTime(requireContext(), item.publishedAt));
        row.divider.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
        row.getRoot().setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.link)));
            } catch (ActivityNotFoundException ignored) {
            }
        });
        b.newsList.addView(row.getRoot());
    }

    // ------------------------------------------------------------------ styling

    @ColorRes
    static int colorFor(CityStat.Level level) {
        switch (level) {
            case LOW:
                return R.color.ws_risk_low;
            case MODERATE:
                return R.color.ws_risk_moderate;
            case HIGH:
                return R.color.ws_risk_high;
            case VERY_HIGH:
                return R.color.ws_risk_very_high;
            default:
                return R.color.ws_risk_unknown;
        }
    }

    private String labelFor(CityStat.Level level) {
        switch (level) {
            case LOW:
                return getString(R.string.level_low);
            case MODERATE:
                return getString(R.string.level_moderate);
            case HIGH:
                return getString(R.string.level_high);
            case VERY_HIGH:
                return getString(R.string.level_very_high);
            default:
                return getString(R.string.level_unknown);
        }
    }

    /** A coloured dot with a white ring, sized by severity. */
    private BitmapDescriptor iconFor(CityStat.Level level) {
        BitmapDescriptor cached = icons.get(level);
        if (cached != null) return cached;
        int dp = level == CityStat.Level.VERY_HIGH ? 22 : level == CityStat.Level.HIGH ? 19
                : level == CityStat.Level.UNKNOWN ? 13 : 16;
        int size = Ui.dp(requireContext(), dp);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(ContextCompat.getColor(requireContext(), colorFor(level)));
        c.drawCircle(size / 2f, size / 2f, size / 2f - Ui.dp(requireContext(), 2.5f), p);
        BitmapDescriptor d = BitmapDescriptorFactory.fromBitmap(bmp);
        icons.put(level, d);
        return d;
    }
}
