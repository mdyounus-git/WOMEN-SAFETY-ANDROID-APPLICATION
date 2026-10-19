package com.example.womensafety.guardian;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.example.womensafety.R;
import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Notifications;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.ui.MainActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runs on a guardian's phone (e.g. Mom's). Keeps a light connection to the database and rings
 * a loud alarm the moment someone she looks after starts an SOS. No server or paid push
 * service is needed — this is what keeps the app free to run.
 */
public class GuardianWatchService extends Service {

    private static final String TAG = "GuardianWatch";

    private String myUid;
    private DatabaseReference watchingRef;
    private ValueEventListener watchingListener;
    private final Map<String, ValueEventListener> sosListeners = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    /** Session ids we have already alerted for, so an alarm rings once per SOS. */
    private final Set<String> alerted = new HashSet<>();
    private boolean foreground;

    public static void sync(@NonNull Context context) {
        Intent i = new Intent(context, GuardianWatchService.class);
        if (Prefs.get(context).getWatchingCount() > 0 && Backend.isConfigured(context)) {
            try {
                ContextCompat.startForegroundService(context, i);
            } catch (Exception e) {
                Log.w(TAG, "Could not start guardian alerts", e);
            }
        } else {
            context.stopService(i);
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (!foreground) {
            try {
                int type = Build.VERSION.SDK_INT >= 34
                        ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0;
                ServiceCompat.startForeground(this, Notifications.ID_WATCH, buildNotification(0), type);
                foreground = true;
            } catch (Exception e) {
                Log.w(TAG, "Not allowed to run guardian alerts now", e);
                stopSelf();
                return START_NOT_STICKY;
            }
            myUid = Backend.uid(this);
            if (myUid == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            watch();
        }
        return START_STICKY;
    }

    private void watch() {
        watchingRef = Backend.watching(myUid);
        watchingListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> current = new HashSet<>();
                for (DataSnapshot w : snapshot.getChildren()) {
                    String wardUid = w.getKey();
                    if (wardUid == null) continue;
                    current.add(wardUid);
                    String name = w.child("name").getValue(String.class);
                    names.put(wardUid, name == null ? "" : name);
                    if (!sosListeners.containsKey(wardUid)) listenSos(wardUid);
                }
                for (String id : new HashSet<>(sosListeners.keySet())) {
                    if (!current.contains(id)) {
                        Backend.sos(id).removeEventListener(sosListeners.remove(id));
                    }
                }
                Prefs.get(GuardianWatchService.this).setWatchingCount(current.size());
                if (current.isEmpty()) {
                    stopSelf();
                } else {
                    Notifications.notifySafely(GuardianWatchService.this, Notifications.ID_WATCH,
                            buildNotification(current.size()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Watching list unavailable: " + error.getMessage());
            }
        };
        watchingRef.addValueEventListener(watchingListener);
    }

    private void listenSos(@NonNull String wardUid) {
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot s) {
                Boolean active = s.child("active").getValue(Boolean.class);
                String session = s.child("sessionId").getValue(String.class);
                Long startedAt = s.child("startedAt").getValue(Long.class);
                // An SOS older than 3 hours is stale (her phone probably died mid-SOS).
                boolean fresh = startedAt == null
                        || System.currentTimeMillis() - startedAt < 3 * 60 * 60_000L;
                if (Boolean.TRUE.equals(active) && fresh && session != null && alerted.add(session)) {
                    raiseAlarm(wardUid);
                } else if (!Boolean.TRUE.equals(active)) {
                    Notifications.cancel(GuardianWatchService.this, Notifications.alertIdFor(wardUid));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // She removed us from her circle.
                Log.i(TAG, "No longer allowed to see a ward: " + error.getMessage());
                Backend.watching(myUid).child(wardUid).removeValue();
            }
        };
        sosListeners.put(wardUid, l);
        Backend.sos(wardUid).addValueEventListener(l);
    }

    private void raiseAlarm(@NonNull String wardUid) {
        String name = names.get(wardUid);
        if (name == null || name.isEmpty()) name = getString(R.string.someone_in_circle);
        Intent view = LiveViewActivity.intent(this, wardUid, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, wardUid.hashCode(), view,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new NotificationCompat.Builder(this, Notifications.CH_ALERTS)
                .setSmallIcon(R.drawable.ic_warning)
                .setColor(ContextCompat.getColor(this, R.color.ws_sos))
                .setContentTitle(getString(R.string.alert_title, name))
                .setContentText(getString(R.string.alert_text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .addAction(R.drawable.ic_videocam, getString(R.string.alert_action_view), pi)
                .build();
        n.flags |= Notification.FLAG_INSISTENT; // keep ringing until opened
        Notifications.notifySafely(this, Notifications.alertIdFor(wardUid), n);
        Log.i(TAG, "SOS alert raised");
    }

    private Notification buildNotification(int count) {
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = count <= 0
                ? getString(R.string.watch_notif_text_generic)
                : getResources().getQuantityString(R.plurals.watch_notif_text, count, count);
        return new NotificationCompat.Builder(this, Notifications.CH_SHARING)
                .setSmallIcon(R.drawable.ic_shield)
                .setColor(ContextCompat.getColor(this, R.color.ws_plum))
                .setContentTitle(getString(R.string.watch_notif_title))
                .setContentText(text)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(open)
                .build();
    }

    @Override
    public void onDestroy() {
        if (watchingRef != null && watchingListener != null) {
            watchingRef.removeEventListener(watchingListener);
        }
        for (Map.Entry<String, ValueEventListener> e : sosListeners.entrySet()) {
            Backend.sos(e.getKey()).removeEventListener(e.getValue());
        }
        sosListeners.clear();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
