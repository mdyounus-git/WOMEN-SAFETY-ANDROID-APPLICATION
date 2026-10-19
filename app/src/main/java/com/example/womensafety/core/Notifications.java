package com.example.womensafety.core;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.womensafety.R;

/** Notification channels and ids used across the app. */
public final class Notifications {

    public static final String CH_SHARING = "sharing";
    public static final String CH_SOS = "sos_active";
    public static final String CH_ALERTS = "sos_alerts";
    public static final String CH_CIRCLE = "circle";

    public static final int ID_SHARING = 10;
    public static final int ID_SOS = 11;
    public static final int ID_WATCH = 12;
    public static final int ID_CIRCLE = 20;
    private static final int ID_ALERT_BASE = 1000;

    private Notifications() {
    }

    public static void createChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel sharing = new NotificationChannel(CH_SHARING,
                context.getString(R.string.channel_sharing), NotificationManager.IMPORTANCE_LOW);
        sharing.setDescription(context.getString(R.string.channel_sharing_desc));
        sharing.setShowBadge(false);

        NotificationChannel sos = new NotificationChannel(CH_SOS,
                context.getString(R.string.channel_sos), NotificationManager.IMPORTANCE_HIGH);
        sos.setDescription(context.getString(R.string.channel_sos_desc));

        NotificationChannel alerts = new NotificationChannel(CH_ALERTS,
                context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription(context.getString(R.string.channel_alerts_desc));
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0, 800, 400, 800, 400, 800});
        alerts.setBypassDnd(true);
        alerts.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm != null) {
            alerts.setSound(alarm, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        }

        NotificationChannel circle = new NotificationChannel(CH_CIRCLE,
                context.getString(R.string.channel_circle), NotificationManager.IMPORTANCE_DEFAULT);
        circle.setDescription(context.getString(R.string.channel_circle_desc));

        nm.createNotificationChannel(sharing);
        nm.createNotificationChannel(sos);
        nm.createNotificationChannel(alerts);
        nm.createNotificationChannel(circle);
    }

    public static int alertIdFor(@NonNull String wardUid) {
        return ID_ALERT_BASE + (wardUid.hashCode() & 0xFFFF);
    }

    /** Posts a notification if the user allowed notifications; silently skips otherwise. */
    public static void notifySafely(@NonNull Context context, int id, @NonNull android.app.Notification n) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(context).notify(id, n);
    }

    public static void cancel(@NonNull Context context, int id) {
        NotificationManagerCompat.from(context).cancel(id);
    }
}
