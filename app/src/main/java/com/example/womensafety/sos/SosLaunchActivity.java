package com.example.womensafety.sos;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.womensafety.core.Ui;

/**
 * Invisible, lock-screen-capable trampoline. Being briefly "visible" is what allows the SOS
 * foreground service to use the camera and microphone when SOS is triggered from the
 * volume button. It finishes immediately.
 */
public class SosLaunchActivity extends Activity {

    private boolean started;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.showOverLockScreen(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Started from onResume, when the activity is definitely in the foreground.
        if (started) return;
        started = true;
        String source = getIntent().getStringExtra(SosService.EXTRA_SOURCE);
        try {
            SosTrigger.start(this, source == null ? SosTrigger.SOURCE_VOLUME : source);
        } catch (Exception e) {
            Log.e("SosLaunch", "Failed to start SOS service", e);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
