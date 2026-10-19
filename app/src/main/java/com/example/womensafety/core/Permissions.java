package com.example.womensafety.core;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.womensafety.sos.VolumeSosService;

/** Read-only checks for every permission the safety features rely on. */
public final class Permissions {

    private Permissions() {
    }

    public static boolean granted(@NonNull Context c, @NonNull String permission) {
        return ContextCompat.checkSelfPermission(c, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasLocation(@NonNull Context c) {
        return granted(c, Manifest.permission.ACCESS_FINE_LOCATION)
                || granted(c, Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    public static boolean hasPreciseLocation(@NonNull Context c) {
        return granted(c, Manifest.permission.ACCESS_FINE_LOCATION);
    }

    /** "Allow all the time": lets sharing resume after a reboot without opening the app. */
    public static boolean hasBackgroundLocation(@NonNull Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocation(c);
        return granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    public static boolean hasCamera(@NonNull Context c) {
        return granted(c, Manifest.permission.CAMERA);
    }

    public static boolean hasMicrophone(@NonNull Context c) {
        return granted(c, Manifest.permission.RECORD_AUDIO);
    }

    public static boolean hasSms(@NonNull Context c) {
        return granted(c, Manifest.permission.SEND_SMS);
    }

    public static boolean hasNotifications(@NonNull Context c) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return granted(c, Manifest.permission.POST_NOTIFICATIONS);
    }

    /** The "special permission" that lets a long Volume Up press trigger SOS anywhere. */
    public static boolean isVolumeTriggerEnabled(@NonNull Context c) {
        String enabled = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName mine = new ComponentName(c, VolumeSosService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName cn = ComponentName.unflattenFromString(splitter.next());
            if (mine.equals(cn)) return true;
        }
        return false;
    }

    /** Lets a guardian's phone show the SOS alarm over the lock screen (Android 14+ asks). */
    public static boolean canShowFullScreenAlerts(@NonNull Context c) {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm == null || nm.canUseFullScreenIntent();
    }

    /** True while one of the app's screens is visible. */
    public static boolean isAppInForeground() {
        android.app.ActivityManager.RunningAppProcessInfo info =
                new android.app.ActivityManager.RunningAppProcessInfo();
        android.app.ActivityManager.getMyMemoryState(info);
        return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    /**
     * Whether a location foreground service may start right now. Android crashes an app whose
     * service is started with startForegroundService() but can't then go to the foreground, so
     * we check first: either a screen is visible, or "Allow all the time" location was granted.
     */
    public static boolean canStartLocationServiceNow(@NonNull Context c) {
        return hasLocation(c) && (isAppInForeground() || hasBackgroundLocation(c));
    }

    public static boolean isIgnoringBatteryOptimizations(@NonNull Context c) {
        PowerManager pm = c.getSystemService(PowerManager.class);
        return pm == null || pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }
}
