package com.example.womensafety.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.womensafety.R;
import com.example.womensafety.circle.CircleFragment;
import com.example.womensafety.circle.CircleRepository;
import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.ActivityMainBinding;
import com.example.womensafety.guardian.GuardianWatchService;
import com.example.womensafety.location.LocationShareService;
import com.example.womensafety.map.CrimeMapFragment;
import com.example.womensafety.news.NewsFragment;

/** Hosts the four tabs. Fragments are kept alive (shown/hidden) so the map isn't rebuilt. */
public class MainActivity extends AppCompatActivity {

    private static final String STATE_TAB = "tab";
    private ActivityMainBinding b;
    @IdRes
    private int currentTab = R.id.nav_home;
    /** Join code from a scanned link (wsafety://join?c=...) waiting for the Circle tab. */
    @Nullable
    private String pendingJoinCode;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_WomenSafety);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        if (!Prefs.get(this).isOnboarded()) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.marginForNavBar(b.navHolder);

        b.nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() != currentTab) {
                b.nav.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            }
            show(item.getItemId());
            return true;
        });
        b.nav.setOnItemReselectedListener(item -> {
            // no-op: keep scroll position
        });

        if (savedInstanceState != null) currentTab = savedInstanceState.getInt(STATE_TAB, R.id.nav_home);
        b.nav.setSelectedItemId(currentTab);
        show(currentTab);
        handleIntent(getIntent());
        warmUpBackend();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
    }

    /** Lets the Home tiles jump to another tab. */
    public void selectTab(@IdRes int tabId) {
        b.nav.setSelectedItemId(tabId);
    }

    /** Called by the Circle tab to pick up a join code that opened the app. */
    @Nullable
    public String consumePendingJoinCode() {
        String c = pendingJoinCode;
        pendingJoinCode = null;
        return c;
    }

    private void handleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        if (data != null && "wsafety".equals(data.getScheme())) {
            pendingJoinCode = CircleRepository.parseCode(data.toString());
            if (pendingJoinCode != null) {
                selectTab(R.id.nav_circle);
                Fragment f = getSupportFragmentManager().findFragmentByTag(tag(R.id.nav_circle));
                if (f instanceof CircleFragment && f.isResumed()) ((CircleFragment) f).checkPendingJoin();
            }
        }
    }

    private void show(@IdRes int tabId) {
        boolean switching = tabId != currentTab
                && getSupportFragmentManager().findFragmentByTag(tag(currentTab)) != null;
        currentTab = tabId;
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        if (switching) {
            // Material "fade through": the old tab fades out, the new one fades and grows in.
            tx.setCustomAnimations(R.anim.ws_fade_through_in, R.anim.ws_fade_through_out);
        }
        for (int id : new int[]{R.id.nav_home, R.id.nav_map, R.id.nav_news, R.id.nav_circle}) {
            Fragment f = fm.findFragmentByTag(tag(id));
            if (id == tabId) {
                if (f == null) tx.add(R.id.content, create(id), tag(id));
                else tx.show(f);
            } else if (f != null) {
                tx.hide(f);
            }
        }
        tx.commitNow();
    }

    private static String tag(int id) {
        return "tab_" + id;
    }

    @NonNull
    private static Fragment create(int id) {
        if (id == R.id.nav_map) return new CrimeMapFragment();
        if (id == R.id.nav_news) return new NewsFragment();
        if (id == R.id.nav_circle) return new CircleFragment();
        return new HomeFragment();
    }

    /** Signs in quietly and (re)starts background sharing/alerts to match the settings. */
    private void warmUpBackend() {
        if (!Backend.isConfigured(this)) return;
        Backend.ensureSignedIn(this, new Backend.UidCallback() {
            @Override
            public void onReady(@NonNull String uid) {
                CircleRepository.publishProfile(MainActivity.this, uid);
                LocationShareService.sync(MainActivity.this);
                GuardianWatchService.sync(MainActivity.this);
            }

            @Override
            public void onError(@NonNull Exception e) {
                // Offline: everything that matters (SOS, SMS, evidence) still works.
            }
        });
    }
}
