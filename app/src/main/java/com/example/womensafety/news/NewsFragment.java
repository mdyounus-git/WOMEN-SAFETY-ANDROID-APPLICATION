package com.example.womensafety.news;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.womensafety.R;
import com.example.womensafety.core.Permissions;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.FragmentNewsBinding;
import com.example.womensafety.map.MapsUtil;
import com.google.android.gms.location.LocationServices;

import java.util.List;

/** Daily news: top stories, women's safety, the user's city, and crime alerts. */
public class NewsFragment extends Fragment {

    private FragmentNewsBinding b;
    private NewsAdapter adapter;
    private String currentUrl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentNewsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Ui.padForSystemBars(b.root, true, true);
        b.date.setText(DateUtils.formatDateTime(requireContext(), System.currentTimeMillis(),
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_YEAR));
        adapter = new NewsAdapter(this::open);
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.refresh.setColorSchemeResources(R.color.ws_plum, R.color.ws_sos);
        b.refresh.setProgressBackgroundColorSchemeResource(R.color.ws_surface);
        b.refresh.setOnRefreshListener(() -> load(true));
        b.retry.setOnClickListener(v -> load(true));
        b.chips.setOnCheckedStateChangeListener((group, ids) -> load(false));
        updateCityLabel();
        refreshCity();
        load(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private String urlForSelection() {
        int id = b.chips.getCheckedChipId();
        String city = Prefs.get(requireContext()).getLastCity();
        if (id == R.id.chipSafety) {
            return NewsRepository.searchUrl("women safety India", 7);
        } else if (id == R.id.chipLocal) {
            String where = TextUtils.isEmpty(city) ? "India" : city;
            return NewsRepository.searchUrl(where + " (police OR safety OR crime OR traffic)", 3);
        } else if (id == R.id.chipCrime) {
            String where = TextUtils.isEmpty(city) ? "India" : city;
            return NewsRepository.searchUrl("crime against women " + where, 14);
        }
        return NewsRepository.topStoriesUrl();
    }

    private void load(boolean force) {
        if (b == null) return;
        final String url = urlForSelection();
        currentUrl = url;
        b.empty.setVisibility(View.GONE);
        b.refresh.setRefreshing(true);
        NewsRepository.load(url, force, new NewsRepository.Callback() {
            @Override
            public void onResult(@NonNull List<NewsItem> items) {
                if (b == null || !url.equals(currentUrl)) return;
                b.refresh.setRefreshing(false);
                adapter.submit(items);
                b.list.scrollToPosition(0);
                if (items.isEmpty()) showEmpty(getString(R.string.news_empty));
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (b == null || !url.equals(currentUrl)) return;
                b.refresh.setRefreshing(false);
                if (adapter.getItemCount() == 0) showEmpty(getString(R.string.news_error));
            }
        });
    }

    private void showEmpty(String text) {
        b.empty.setVisibility(View.VISIBLE);
        b.emptyText.setText(text);
    }

    private void open(NewsItem item) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.link)));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private void updateCityLabel() {
        String city = Prefs.get(requireContext()).getLastCity();
        b.chipLocal.setText(TextUtils.isEmpty(city) ? getString(R.string.news_chip_local) : city);
    }

    /** Learns the user's city (for the local chip) from the last known location. */
    @SuppressLint("MissingPermission")
    private void refreshCity() {
        if (!Permissions.hasLocation(requireContext())) return;
        LocationServices.getFusedLocationProviderClient(requireContext()).getLastLocation()
                .addOnSuccessListener(loc -> {
                    if (loc == null || b == null) return;
                    MapsUtil.describePlace(requireContext(), loc.getLatitude(), loc.getLongitude(), place -> {
                        if (b == null || place == null || TextUtils.isEmpty(place.city)) return;
                        Prefs.get(requireContext()).setLastPlace(place.city, loc.getLatitude(), loc.getLongitude());
                        updateCityLabel();
                    });
                });
    }
}
