package com.example.womensafety.sos;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/** Entry points for starting and stopping SOS from anywhere in the app. */
public final class SosTrigger {

    private static final String TAG = "SosTrigger";

    public static final String SOURCE_BUTTON = "button";
    public static final String SOURCE_VOLUME = "volume";
    public static final String SOURCE_RESUME = "resume";

    private SosTrigger() {
    }

    /** Use when the app is on screen (e.g. the big SOS button). */
    public static void start(@NonNull Context context, @NonNull String source) {
        Intent i = new Intent(context, SosService.class)
                .setAction(SosService.ACTION_START)
                .putExtra(SosService.EXTRA_SOURCE, source);
        ContextCompat.startForegroundService(context, i);
    }

    /**
     * Use from the volume-button service. Android only lets camera and microphone run in a
     * foreground service that was started while the app is visible, so we first open an
     * invisible activity (allowed for accessibility services, even over the lock screen),
     * and that activity starts the SOS service.
     */
    public static void startFromBackground(@NonNull Context context, @NonNull String source) {
        try {
            Intent i = new Intent(context, SosLaunchActivity.class)
                    .putExtra(SosService.EXTRA_SOURCE, source)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            context.startActivity(i);
        } catch (Exception e) {
            // Last resort: start directly. Location and SMS still work even if camera/mic can't.
            Log.w(TAG, "Trampoline failed, starting service directly", e);
            try {
                start(context, source);
            } catch (Exception e2) {
                Log.e(TAG, "Could not start SOS", e2);
            }
        }
    }

    public static void stop(@NonNull Context context) {
        Intent i = new Intent(context, SosService.class).setAction(SosService.ACTION_STOP);
        try {
            context.startService(i);
        } catch (Exception e) {
            // Service not running in foreground any more: just make sure state is cleared.
            context.stopService(new Intent(context, SosService.class));
        }
    }
}
