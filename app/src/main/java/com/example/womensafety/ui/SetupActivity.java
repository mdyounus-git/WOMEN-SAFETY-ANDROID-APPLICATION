package com.example.womensafety.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.womensafety.R;
import com.example.womensafety.core.Permissions;
import com.example.womensafety.core.Ui;
import com.example.womensafety.databinding.ActivitySetupBinding;
import com.example.womensafety.databinding.ItemSetupBinding;
import com.example.womensafety.guardian.GuardianWatchService;
import com.example.womensafety.location.LocationShareService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Step-by-step permission checklist, each with a plain-language reason. */
public class SetupActivity extends AppCompatActivity {

    public static final String EXTRA_FIRST_RUN = "first_run";

    private ActivitySetupBinding b;
    private boolean firstRun;
    /** Steps whose permission was denied with "don't ask again": open app settings instead. */
    private final EnumSet<SetupChecklist.Key> blocked = EnumSet.noneOf(SetupChecklist.Key.class);
    private SetupChecklist.Key pending;

    private final ActivityResultLauncher<String[]> request =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), this::onResult);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_WomenSafety);
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        b = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.padForSystemBars(b.root, true, true);
        firstRun = getIntent().getBooleanExtra(EXTRA_FIRST_RUN, false);
        b.back.setVisibility(firstRun ? View.INVISIBLE : View.VISIBLE);
        b.back.setOnClickListener(v -> finishSetup());
        b.done.setOnClickListener(v -> finishSetup());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishSetup();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        List<SetupChecklist.Item> items = SetupChecklist.items(this);
        int done = SetupChecklist.doneCount(items);
        b.progress.setMax(items.size());
        b.progress.setProgress(done);
        b.progressText.setText(getString(R.string.setup_progress, done, items.size()));
        b.done.setText(done == items.size() ? R.string.setup_done : R.string.setup_later);

        b.list.removeAllViews();
        for (SetupChecklist.Item item : items) {
            ItemSetupBinding row = ItemSetupBinding.inflate(getLayoutInflater(), b.list, false);
            row.icon.setImageResource(item.icon);
            row.title.setText(item.title);
            row.description.setText(item.description);
            row.check.setVisibility(item.done ? View.VISIBLE : View.GONE);
            row.allow.setVisibility(item.done ? View.GONE : View.VISIBLE);
            row.allow.setText(blocked.contains(item.key) ? R.string.setup_open_settings : R.string.setup_allow);
            row.allow.setOnClickListener(v -> act(item.key));
            row.getRoot().setOnClickListener(item.done ? null : v -> act(item.key));
            b.list.addView(row.getRoot());
        }
    }

    private void act(SetupChecklist.Key key) {
        if (blocked.contains(key)) {
            openAppSettings();
            return;
        }
        pending = key;
        switch (key) {
            case LOCATION:
                request.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
                break;
            case BACKGROUND_LOCATION:
                askBackgroundLocation();
                break;
            case NOTIFICATIONS:
                if (Build.VERSION.SDK_INT >= 33) {
                    request.launch(new String[]{Manifest.permission.POST_NOTIFICATIONS});
                }
                break;
            case CAMERA_MIC:
                request.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
                break;
            case SMS:
                request.launch(new String[]{Manifest.permission.SEND_SMS});
                break;
            case VOLUME:
                explainVolumeTrigger();
                break;
            case FULL_SCREEN:
                openFullScreenSettings();
                break;
            case BATTERY:
                askBatteryExemption();
                break;
        }
    }

    private void askBackgroundLocation() {
        if (!Permissions.hasLocation(this)) {
            pending = SetupChecklist.Key.LOCATION;
            request.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        if (Build.VERSION.SDK_INT < 29) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setup_bg_location)
                .setMessage(R.string.setup_bg_location_dialog)
                .setPositiveButton(R.string.action_continue, (d, w) ->
                        request.launch(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}))
                .setNegativeButton(R.string.action_not_now, null)
                .show();
    }

    private void explainVolumeTrigger() {
        String message = getString(R.string.setup_volume_dialog);
        if (Build.VERSION.SDK_INT >= 33) message += "\n\n" + getString(R.string.setup_volume_restricted);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setup_volume)
                .setMessage(message)
                .setPositiveButton(R.string.setup_open_settings, (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (ActivityNotFoundException e) {
                        openAppSettings();
                    }
                })
                .setNeutralButton(R.string.setup_app_info, (d, w) -> openAppSettings())
                .setNegativeButton(R.string.action_not_now, null)
                .show();
    }

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            openAppSettings();
        }
    }

    @SuppressLint("BatteryLife") // A safety app must keep running in the background.
    private void askBatteryExemption() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void onResult(Map<String, Boolean> results) {
        boolean anyDenied = false;
        for (Map.Entry<String, Boolean> e : results.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) {
                anyDenied = true;
                if (!shouldShowRequestPermissionRationale(e.getKey()) && pending != null) {
                    blocked.add(pending);
                }
            }
        }
        if (!anyDenied && pending == SetupChecklist.Key.LOCATION) {
            LocationShareService.sync(this);
        }
        pending = null;
        render();
    }

    private void finishSetup() {
        LocationShareService.sync(this);
        GuardianWatchService.sync(this);
        if (firstRun) startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
